(ns dodcompliance.dodcompliancellm
  "DoDCompliance-LLM client -- the *contained intelligence node* for the
  USA-DOD (U.S. Department of Defense) compliance actor.

  It normalizes engagement intake, drafts a per-track (`:dfars` /
  `:cmmc` / `:facility-clearance`) compliance evidence checklist, drafts
  the filing-draft action, and drafts the filing-submit action. CRITICAL:
  it is a smart-but-untrusted advisor. It returns a *proposal* (with a
  rationale + the fields it cited), never a committed record or a real
  DoD filing. Every output is censored downstream by
  `dodcompliance.governor` before anything touches the SSoT, and
  `:filing/draft`/`:filing/submit` proposals NEVER auto-commit at any
  phase -- see README Actuation. This actor stays strictly at the
  compliance-PROCESS level -- it never asserts CMMC enforcement is live
  as of a specific date, only reflects the engagement's own declared
  requirements and verification state.

  Like every sibling actor's advisor, this is a deterministic mock so the
  actor graph runs offline and the governor contract is exercised
  end-to-end."
  (:require [dodcompliance.facts :as facts]
            [dodcompliance.store :as store]))

(defn- normalize-intake
  [_db {:keys [patch]}]
  {:summary    (str "engagement intake record updated: " (pr-str (keys patch)))
   :rationale  "normalization of the input patch only. No new facts generated."
   :cites      (vec (keys patch))
   :effect     :engagement/upsert
   :value      patch
   :stake      nil
   :confidence 0.97})

(defn- assess-track
  "Per-track (`:dfars` / `:cmmc` / `:facility-clearance`) compliance
  evidence checklist draft. `:no-spec?` injects the failure mode we must
  defend against: proposing a checklist for a track with NO official
  spec-basis."
  [_db {:keys [track no-spec?]}]
  (let [track (if no-spec? :unknown-track track)
        sb (facts/spec-basis track)]
    (if (nil? sb)
      {:summary    (str (name track) " has no official spec-basis on file")
       :rationale  "track not registered in dodcompliance.facts. Requirements are not invented by guessing."
       :cites      []
       :effect     :assessment/set
       :value      {:track track :checklist [] :spec-basis nil}
       :stake      nil
       :confidence 0.9}
      {:summary    (str (name track) " (" (:owner-authority sb) ") proposes "
                        (count (:required-evidence sb)) " required evidence item(s)")
       :rationale  (str "official source: " (:provenance sb) " / legal basis: " (:legal-basis sb))
       :cites      [(:legal-basis sb) (:provenance sb)]
       :effect     :assessment/set
       :value      {:track track
                    :checklist (:required-evidence sb)
                    :spec-basis (:provenance sb)
                    :legal-basis (:legal-basis sb)}
       :stake      nil
       :confidence 0.9})))

(defn- propose-draft
  "Draft the actual FILING-DRAFT action for `track`. ALWAYS `:stake
  :actuation/draft-filing`."
  [db {:keys [subject track]}]
  (let [e (store/engagement db subject)]
    {:summary    (str subject "/" (name track) " draft filing proposed"
                      (when e (str " (operator=" (:operator e) ")")))
     :rationale  (if e
                   (str "track=" (name track) " portal=" (:portal e))
                   "engagement not found")
     :cites      (if e [subject (name track)] [])
     :effect     :engagement/mark-drafted
     :value      {:engagement-id subject :track track}
     :stake      :actuation/draft-filing
     :confidence (if e 0.9 0.3)}))

(defn- propose-submit
  "Draft the actual FILING-SUBMIT action for `track`. ALWAYS `:stake
  :actuation/submit-filing` -- real-world DoD filing submission. Reflects
  readiness across the engagement-level/track gates the governor
  independently re-verifies: SAM.gov registration, and (per-track) CMMC
  level verification / DCSA-NISP facility clearance verification."
  [db {:keys [subject track]}]
  (let [e (store/engagement db subject)
        sam-registration-ok? (or (not (:requires-sam-registration? e))
                                  (:sam-registration-verified? e))
        cmmc-level-ok? (or (not= track :cmmc)
                            (not (:requires-cmmc-level? e))
                            (:cmmc-level-verified? e))
        facility-clearance-ok? (or (not= track :facility-clearance)
                                    (not (:requires-facility-clearance? e))
                                    (:facility-clearance-verified? e))]
    {:summary    (str subject "/" (name track) " submit filing proposed"
                      (when e (str " (operator=" (:operator e) ")")))
     :rationale  (if e
                   (str "sam-registration-verified?=" (:sam-registration-verified? e)
                        " cmmc-level-verified?=" (:cmmc-level-verified? e)
                        " facility-clearance-verified?=" (:facility-clearance-verified? e)
                        " claimed-fee=" (:claimed-fee e))
                   "engagement not found")
     :cites      (if e [subject (name track)] [])
     :effect     :engagement/mark-submitted
     :value      {:engagement-id subject :track track}
     :stake      :actuation/submit-filing
     :confidence (if (and e sam-registration-ok? cmmc-level-ok? facility-clearance-ok?)
                   0.9 0.3)}))

(defprotocol Advisor
  (-advise [this db request] "Return a proposal map for `request`."))

(defrecord MockAdvisor []
  Advisor
  (-advise [_ db {:keys [op] :as request}]
    (case op
      :engagement/intake   (normalize-intake db request)
      :compliance/assess   (assess-track db request)
      :filing/draft        (propose-draft db request)
      :filing/submit       (propose-submit db request)
      {:summary "unknown op" :rationale "unsupported" :cites []
       :effect :noop :value {} :stake nil :confidence 0.0})))

(defn mock-advisor [] (->MockAdvisor))

(defn trace [request proposal]
  {:t :advisor-proposal
   :op (:op request)
   :subject (:subject request)
   :track (:track request)
   :summary (:summary proposal)
   :confidence (:confidence proposal)
   :stake (:stake proposal)})
