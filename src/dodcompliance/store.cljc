(ns dodcompliance.store
  "SSoT for the USA-DOD (U.S. Department of Defense) compliance actor,
  behind a `Store` protocol so the backend is a swap, not a rewrite --
  the same seam every prior cloud-itonami actor in this fleet uses.

    - `MemStore`     -- atom of EDN. The deterministic default for
                        dev/tests/demo (no deps).
    - `DatomicStore` -- backed by `langchain.db`, a Datomic-API-compatible
                        EAV store, using `langchain-store.core` for the
                        shared EDN-blob codec + event-log helpers instead
                        of a hand-rolled `enc`/`dec*` (ADR-2607141600).

  Both implement the same protocol and pass the same contract
  (test/dodcompliance/store_contract_test.clj).

  The primary entity here is an `engagement` -- one operator's compliance
  engagement carrying THREE independently-tracked filing tracks:

    :dfars               -- Defense Federal Acquisition Regulation
                             Supplement compliance-review filing
    :cmmc                -- Cybersecurity Maturity Model Certification
                             self-/third-party-assessment filing
    :facility-clearance  -- DCSA/NISP facility security clearance filing

  plus three engagement-level (not per-track) gating facts grounded in
  `dodcompliance.facts`:
    - `:sam-registration-verified?`     (`:sam-registration` catalog
                                         entry -- citation-only, gates any
                                         `:filing/submit`)
    - `:cmmc-level-verified?`           (`:cmmc` catalog entry -- gates
                                         `:filing/submit` on the `:cmmc`
                                         track)
    - `:facility-clearance-verified?`   (`:facility-clearance` catalog
                                         entry -- gates `:filing/submit`
                                         on the `:facility-clearance`
                                         track)

  filing-draft and filing-submit actuation events apply per-TRACK to the
  SAME engagement record (draft first, submit later, independently for
  each track). Dedicated double-actuation-guard booleans per track
  (`:dfars-drafted?`/`:dfars-submitted?`/`:cmmc-drafted?`/
  `:cmmc-submitted?`/`:facility-clearance-drafted?`/
  `:facility-clearance-submitted?`, never a single `:status` value).

  The `:sam-registration` catalog entry is citation-only (mirrors
  `:procurement-qualification` in the JPN-MOD sibling) -- it is never a
  track this actor drafts/submits filings FOR, only a fact this actor
  gates `:filing/submit` (any track) against.

  The ledger stays append-only on every backend."
  (:require [dodcompliance.registry :as registry]
            [langchain.db :as d]
            [langchain-store.core :as ls]))

(defprotocol Store
  (engagement [s id])
  (all-engagements [s])
  (assessment-of [s engagement-id track] "committed track assessment, or nil")
  (ledger [s])
  (draft-history [s] "the append-only filing-draft history")
  (submit-history [s] "the append-only filing-submit history")
  (next-draft-sequence [s track])
  (next-submit-sequence [s track])
  (engagement-track-drafted? [s engagement-id track])
  (engagement-track-submitted? [s engagement-id track])
  (commit-record! [s record] "apply a committed op's record to the SSoT")
  (append-ledger! [s fact]   "append one immutable decision fact")
  (with-engagements [s engagements] "replace/seed the engagement directory"))

;; ----------------------- track-scoped field mapping -----------------------
;; No dynamic keyword construction -- each track's drafted?/submitted?/
;; draft-number/submit-number fields are explicit, named keys (mirrors
;; the rest of this fleet's explicit-boolean-field style).

(def ^:private track-fields
  {:dfars              {:drafted? :dfars-drafted?              :draft-number :dfars-draft-number
                         :submitted? :dfars-submitted?          :submit-number :dfars-submit-number}
   :cmmc               {:drafted? :cmmc-drafted?                :draft-number :cmmc-draft-number
                         :submitted? :cmmc-submitted?            :submit-number :cmmc-submit-number}
   :facility-clearance {:drafted? :facility-clearance-drafted?  :draft-number :facility-clearance-draft-number
                         :submitted? :facility-clearance-submitted? :submit-number :facility-clearance-submit-number}})

;; ----------------------------- demo data -----------------------------

(defn demo-data
  "A small, self-contained engagement set covering both actuation
  lifecycles (draft, submit) across all three filing tracks, plus the
  governor's own dossier-grounded checks: a clean case (eng-1, includes
  the compliance-audit export package revenue line), an
  unregistered-track fabrication-defense case (eng-2), a fee-mismatch
  case (eng-3), a missing-facility-clearance case (eng-4,
  `:facility-clearance` track), a missing-SAM-registration case (eng-5),
  and a missing-CMMC-level-verification case (eng-6)."
  []
  {:engagements
   {"eng-1" {:id "eng-1" :operator "Kita Defense Systems Inc" :portal "SAM.gov / DFARS"
             :base-fee 800000 :monthly-rate 50000 :monitoring-months 12
             :audit-export? true :export-fee 150000 :claimed-fee 1550000.0
             :requires-sam-registration? true :sam-registration-verified? true
             :requires-cmmc-level? true :cmmc-level-verified? true
             :requires-facility-clearance? true :facility-clearance-verified? true
             :dfars-drafted? false :dfars-submitted? false
             :cmmc-drafted? false :cmmc-submitted? false
             :facility-clearance-drafted? false :facility-clearance-submitted? false
             :status :intake}
    "eng-2" {:id "eng-2" :operator "Atlantis Defense Partners LLC" :portal "SAM.gov / DFARS"
             :base-fee 800000 :monthly-rate 50000 :monitoring-months 12
             :audit-export? true :export-fee 150000 :claimed-fee 1550000.0
             :requires-sam-registration? true :sam-registration-verified? true
             :requires-cmmc-level? true :cmmc-level-verified? true
             :requires-facility-clearance? true :facility-clearance-verified? true
             :dfars-drafted? false :dfars-submitted? false
             :cmmc-drafted? false :cmmc-submitted? false
             :facility-clearance-drafted? false :facility-clearance-submitted? false
             :status :intake}
    "eng-3" {:id "eng-3" :operator "Minami Defense Systems Inc" :portal "SAM.gov / DFARS"
             :base-fee 800000 :monthly-rate 50000 :monitoring-months 12
             :audit-export? false :export-fee nil :claimed-fee 1800000.0
             :requires-sam-registration? true :sam-registration-verified? true
             :requires-cmmc-level? true :cmmc-level-verified? true
             :requires-facility-clearance? true :facility-clearance-verified? true
             :dfars-drafted? false :dfars-submitted? false
             :cmmc-drafted? false :cmmc-submitted? false
             :facility-clearance-drafted? false :facility-clearance-submitted? false
             :status :intake}
    "eng-4" {:id "eng-4" :operator "Higashi Defense Technologies Inc" :portal "SAM.gov / DFARS"
             :base-fee 800000 :monthly-rate 50000 :monitoring-months 12
             :audit-export? false :export-fee nil :claimed-fee 1400000.0
             :requires-sam-registration? true :sam-registration-verified? true
             :requires-cmmc-level? true :cmmc-level-verified? true
             :requires-facility-clearance? true :facility-clearance-verified? false
             :dfars-drafted? false :dfars-submitted? false
             :cmmc-drafted? false :cmmc-submitted? false
             :facility-clearance-drafted? false :facility-clearance-submitted? false
             :status :intake}
    "eng-5" {:id "eng-5" :operator "Nishi Defense Logistics Inc" :portal "SAM.gov / DFARS"
             :base-fee 800000 :monthly-rate 50000 :monitoring-months 12
             :audit-export? false :export-fee nil :claimed-fee 1400000.0
             :requires-sam-registration? true :sam-registration-verified? false
             :requires-cmmc-level? true :cmmc-level-verified? true
             :requires-facility-clearance? true :facility-clearance-verified? true
             :dfars-drafted? false :dfars-submitted? false
             :cmmc-drafted? false :cmmc-submitted? false
             :facility-clearance-drafted? false :facility-clearance-submitted? false
             :status :intake}
    "eng-6" {:id "eng-6" :operator "Chuo Defense Consulting Inc" :portal "SAM.gov / DFARS"
             :base-fee 800000 :monthly-rate 50000 :monitoring-months 12
             :audit-export? false :export-fee nil :claimed-fee 1400000.0
             :requires-sam-registration? true :sam-registration-verified? true
             :requires-cmmc-level? true :cmmc-level-verified? false
             :requires-facility-clearance? true :facility-clearance-verified? true
             :dfars-drafted? false :dfars-submitted? false
             :cmmc-drafted? false :cmmc-submitted? false
             :facility-clearance-drafted? false :facility-clearance-submitted? false
             :status :intake}}})

;; ----------------------------- shared commit logic -----------------------------

(defn- draft-filing!
  [s engagement-id track]
  (let [seq-n (next-draft-sequence s track)
        result (registry/register-draft engagement-id track seq-n)
        {:keys [drafted? draft-number]} (get track-fields track)]
    {:result result
     :engagement-patch {drafted? true
                        draft-number (get result "draft_number")}}))

(defn- submit-filing!
  [s engagement-id track]
  (let [seq-n (next-submit-sequence s track)
        result (registry/register-submit engagement-id track seq-n)
        {:keys [submitted? submit-number]} (get track-fields track)]
    {:result result
     :engagement-patch {submitted? true
                        submit-number (get result "submit_number")}}))

;; ----------------------------- MemStore (default) -----------------------------

(defrecord MemStore [a]
  Store
  (engagement [_ id] (get-in @a [:engagements id]))
  (all-engagements [_] (sort-by :id (vals (:engagements @a))))
  (assessment-of [_ engagement-id track] (get-in @a [:assessments engagement-id track]))
  (ledger [_] (:ledger @a))
  (draft-history [_] (:draft-records @a))
  (submit-history [_] (:submit-records @a))
  (next-draft-sequence [_ track] (get-in @a [:draft-sequences track] 0))
  (next-submit-sequence [_ track] (get-in @a [:submit-sequences track] 0))
  (engagement-track-drafted? [_ engagement-id track]
    (boolean (get-in @a [:engagements engagement-id (:drafted? (get track-fields track))])))
  (engagement-track-submitted? [_ engagement-id track]
    (boolean (get-in @a [:engagements engagement-id (:submitted? (get track-fields track))])))
  (commit-record! [s {:keys [effect path value payload]}]
    (case effect
      :engagement/upsert
      (swap! a update-in [:engagements (:id value)] merge value)

      :assessment/set
      (let [[engagement-id track] path]
        (swap! a assoc-in [:assessments engagement-id track] payload))

      :engagement/mark-drafted
      (let [[engagement-id track] path
            {:keys [result engagement-patch]} (draft-filing! s engagement-id track)]
        (swap! a (fn [state]
                   (-> state
                       (update-in [:draft-sequences track] (fnil inc 0))
                       (update-in [:engagements engagement-id] merge engagement-patch)
                       (update :draft-records registry/append result))))
        result)

      :engagement/mark-submitted
      (let [[engagement-id track] path
            {:keys [result engagement-patch]} (submit-filing! s engagement-id track)]
        (swap! a (fn [state]
                   (-> state
                       (update-in [:submit-sequences track] (fnil inc 0))
                       (update-in [:engagements engagement-id] merge engagement-patch)
                       (update :submit-records registry/append result))))
        result)
      nil)
    s)
  (append-ledger! [_ fact] (swap! a update :ledger conj fact) fact)
  (with-engagements [s engagements] (when (seq engagements) (swap! a assoc :engagements engagements)) s))

(defn seed-db
  "A MemStore seeded with the demo engagement set."
  []
  (->MemStore (atom (assoc (demo-data)
                           :assessments {}
                           :ledger [] :draft-sequences {} :draft-records []
                           :submit-sequences {} :submit-records []))))

;; ----------------------------- DatomicStore (langchain.db) -----------------------------

(def ^:private schema
  {:engagement/id                   {:db/unique :db.unique/identity}
   :assessment/key                  {:db/unique :db.unique/identity}
   :ledger/seq                      {:db/unique :db.unique/identity}
   :draft-record/seq                {:db/unique :db.unique/identity}
   :submit-record/seq               {:db/unique :db.unique/identity}
   :draft-sequence/track            {:db/unique :db.unique/identity}
   :submit-sequence/track           {:db/unique :db.unique/identity}})

(defn- engagement->tx [{:keys [id operator portal base-fee monthly-rate monitoring-months
                               audit-export? export-fee claimed-fee
                               requires-sam-registration? sam-registration-verified?
                               requires-cmmc-level? cmmc-level-verified?
                               requires-facility-clearance? facility-clearance-verified?
                               dfars-drafted? dfars-draft-number dfars-submitted? dfars-submit-number
                               cmmc-drafted? cmmc-draft-number cmmc-submitted? cmmc-submit-number
                               facility-clearance-drafted? facility-clearance-draft-number
                               facility-clearance-submitted? facility-clearance-submit-number
                               status]}]
  (cond-> {:engagement/id id}
    operator                                (assoc :engagement/operator operator)
    portal                                  (assoc :engagement/portal portal)
    base-fee                                (assoc :engagement/base-fee base-fee)
    monthly-rate                            (assoc :engagement/monthly-rate monthly-rate)
    monitoring-months                       (assoc :engagement/monitoring-months monitoring-months)
    (some? audit-export?)                   (assoc :engagement/audit-export? audit-export?)
    export-fee                              (assoc :engagement/export-fee export-fee)
    claimed-fee                             (assoc :engagement/claimed-fee claimed-fee)
    (some? requires-sam-registration?)      (assoc :engagement/requires-sam-registration? requires-sam-registration?)
    (some? sam-registration-verified?)      (assoc :engagement/sam-registration-verified? sam-registration-verified?)
    (some? requires-cmmc-level?)            (assoc :engagement/requires-cmmc-level? requires-cmmc-level?)
    (some? cmmc-level-verified?)            (assoc :engagement/cmmc-level-verified? cmmc-level-verified?)
    (some? requires-facility-clearance?)    (assoc :engagement/requires-facility-clearance? requires-facility-clearance?)
    (some? facility-clearance-verified?)    (assoc :engagement/facility-clearance-verified? facility-clearance-verified?)
    (some? dfars-drafted?)                  (assoc :engagement/dfars-drafted? dfars-drafted?)
    dfars-draft-number                      (assoc :engagement/dfars-draft-number dfars-draft-number)
    (some? dfars-submitted?)                (assoc :engagement/dfars-submitted? dfars-submitted?)
    dfars-submit-number                     (assoc :engagement/dfars-submit-number dfars-submit-number)
    (some? cmmc-drafted?)                   (assoc :engagement/cmmc-drafted? cmmc-drafted?)
    cmmc-draft-number                       (assoc :engagement/cmmc-draft-number cmmc-draft-number)
    (some? cmmc-submitted?)                 (assoc :engagement/cmmc-submitted? cmmc-submitted?)
    cmmc-submit-number                      (assoc :engagement/cmmc-submit-number cmmc-submit-number)
    (some? facility-clearance-drafted?)     (assoc :engagement/facility-clearance-drafted? facility-clearance-drafted?)
    facility-clearance-draft-number         (assoc :engagement/facility-clearance-draft-number facility-clearance-draft-number)
    (some? facility-clearance-submitted?)   (assoc :engagement/facility-clearance-submitted? facility-clearance-submitted?)
    facility-clearance-submit-number        (assoc :engagement/facility-clearance-submit-number facility-clearance-submit-number)
    status                                  (assoc :engagement/status status)))

(def ^:private engagement-pull
  [:engagement/id :engagement/operator :engagement/portal :engagement/base-fee :engagement/monthly-rate
   :engagement/monitoring-months :engagement/audit-export? :engagement/export-fee :engagement/claimed-fee
   :engagement/requires-sam-registration? :engagement/sam-registration-verified?
   :engagement/requires-cmmc-level? :engagement/cmmc-level-verified?
   :engagement/requires-facility-clearance? :engagement/facility-clearance-verified?
   :engagement/dfars-drafted? :engagement/dfars-draft-number
   :engagement/dfars-submitted? :engagement/dfars-submit-number
   :engagement/cmmc-drafted? :engagement/cmmc-draft-number
   :engagement/cmmc-submitted? :engagement/cmmc-submit-number
   :engagement/facility-clearance-drafted? :engagement/facility-clearance-draft-number
   :engagement/facility-clearance-submitted? :engagement/facility-clearance-submit-number
   :engagement/status])

(defn- pull->engagement [m]
  (when (:engagement/id m)
    {:id (:engagement/id m) :operator (:engagement/operator m) :portal (:engagement/portal m)
     :base-fee (:engagement/base-fee m) :monthly-rate (:engagement/monthly-rate m)
     :monitoring-months (:engagement/monitoring-months m)
     :audit-export? (boolean (:engagement/audit-export? m)) :export-fee (:engagement/export-fee m)
     :claimed-fee (:engagement/claimed-fee m)
     :requires-sam-registration? (boolean (:engagement/requires-sam-registration? m))
     :sam-registration-verified? (boolean (:engagement/sam-registration-verified? m))
     :requires-cmmc-level? (boolean (:engagement/requires-cmmc-level? m))
     :cmmc-level-verified? (boolean (:engagement/cmmc-level-verified? m))
     :requires-facility-clearance? (boolean (:engagement/requires-facility-clearance? m))
     :facility-clearance-verified? (boolean (:engagement/facility-clearance-verified? m))
     :dfars-drafted? (boolean (:engagement/dfars-drafted? m))
     :dfars-draft-number (:engagement/dfars-draft-number m)
     :dfars-submitted? (boolean (:engagement/dfars-submitted? m))
     :dfars-submit-number (:engagement/dfars-submit-number m)
     :cmmc-drafted? (boolean (:engagement/cmmc-drafted? m))
     :cmmc-draft-number (:engagement/cmmc-draft-number m)
     :cmmc-submitted? (boolean (:engagement/cmmc-submitted? m))
     :cmmc-submit-number (:engagement/cmmc-submit-number m)
     :facility-clearance-drafted? (boolean (:engagement/facility-clearance-drafted? m))
     :facility-clearance-draft-number (:engagement/facility-clearance-draft-number m)
     :facility-clearance-submitted? (boolean (:engagement/facility-clearance-submitted? m))
     :facility-clearance-submit-number (:engagement/facility-clearance-submit-number m)
     :status (:engagement/status m)}))

(defn- assessment-key [engagement-id track] (str engagement-id "::" (name track)))

(defrecord DatomicStore [conn]
  Store
  (engagement [_ id]
    (pull->engagement (d/pull (d/db conn) engagement-pull [:engagement/id id])))
  (all-engagements [_]
    (->> (d/q '[:find [?id ...] :where [?e :engagement/id ?id]] (d/db conn))
         (map #(pull->engagement (d/pull (d/db conn) engagement-pull [:engagement/id %])))
         (sort-by :id)))
  (assessment-of [_ engagement-id track]
    (ls/dec* (d/q '[:find ?p . :in $ ?k
                   :where [?a :assessment/key ?k] [?a :assessment/payload ?p]]
                 (d/db conn) (assessment-key engagement-id track))))
  (ledger [_] (ls/read-stream conn :ledger/seq :ledger/fact))
  (draft-history [_] (ls/read-stream conn :draft-record/seq :draft-record/record))
  (submit-history [_] (ls/read-stream conn :submit-record/seq :submit-record/record))
  (next-draft-sequence [_ track]
    (or (d/q '[:find ?n . :in $ ?t
              :where [?e :draft-sequence/track ?t] [?e :draft-sequence/next ?n]]
            (d/db conn) track)
        0))
  (next-submit-sequence [_ track]
    (or (d/q '[:find ?n . :in $ ?t
              :where [?e :submit-sequence/track ?t] [?e :submit-sequence/next ?n]]
            (d/db conn) track)
        0))
  (engagement-track-drafted? [s engagement-id track]
    (boolean (get (engagement s engagement-id) (:drafted? (get track-fields track)))))
  (engagement-track-submitted? [s engagement-id track]
    (boolean (get (engagement s engagement-id) (:submitted? (get track-fields track)))))
  (commit-record! [s {:keys [effect path value payload]}]
    (case effect
      :engagement/upsert
      (d/transact! conn [(engagement->tx value)])

      :assessment/set
      (let [[engagement-id track] path]
        (d/transact! conn [{:assessment/key (assessment-key engagement-id track)
                            :assessment/payload (ls/enc payload)}]))

      :engagement/mark-drafted
      (let [[engagement-id track] path
            {:keys [result engagement-patch]} (draft-filing! s engagement-id track)
            next-n (inc (next-draft-sequence s track))]
        (d/transact! conn
                     [(engagement->tx (assoc engagement-patch :id engagement-id))
                      {:draft-sequence/track track :draft-sequence/next next-n}
                      {:draft-record/seq (count (draft-history s)) :draft-record/record (ls/enc (get result "record"))}])
        result)

      :engagement/mark-submitted
      (let [[engagement-id track] path
            {:keys [result engagement-patch]} (submit-filing! s engagement-id track)
            next-n (inc (next-submit-sequence s track))]
        (d/transact! conn
                     [(engagement->tx (assoc engagement-patch :id engagement-id))
                      {:submit-sequence/track track :submit-sequence/next next-n}
                      {:submit-record/seq (count (submit-history s)) :submit-record/record (ls/enc (get result "record"))}])
        result)
      nil)
    s)
  (append-ledger! [s fact]
    (ls/append-blob! conn :ledger/seq :ledger/fact (count (ledger s)) fact)
    fact)
  (with-engagements [s engagements]
    (when (seq engagements) (d/transact! conn (mapv engagement->tx (vals engagements)))) s))

(defn datomic-store
  ([] (datomic-store {}))
  ([{:keys [engagements]}]
   (let [s (->DatomicStore (d/create-conn schema))]
     (with-engagements s engagements))))

(defn datomic-seed-db
  []
  (datomic-store (demo-data)))
