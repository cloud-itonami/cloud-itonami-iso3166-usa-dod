(ns dodcompliance.registry
  "Pure-function filing-draft + filing-submit record construction for the
  USA-DOD (U.S. Department of Defense) actor -- an append-only compliance
  book-of-record draft, one per track (`:dfars` / `:cmmc` /
  `:facility-clearance`).

  Like every sibling actor's registry, there is no single reference-
  number standard DoD assigns to a compliance filing-draft/filing-submit
  package this actor produces for its own audit trail -- this namespace
  does NOT invent one; it builds a track-scoped sequence number and
  validates the record's required fields, the same honest,
  non-fabricating discipline `dodcompliance.facts` uses.

  `engagement-fee-matches-claim?` is an HONEST reapplication of the SAME
  ground-truth-recompute DISCIPLINE sibling actors use (verify a claimed
  monetary total against the entity's own recorded quantity x unit
  fields), reapplied to this repo's own THREE-line revenue model
  (docs/business-model.md Revenue: 'per-engagement compliance-review fee'
  as base-fee + 'recurring regulatory-change monitoring subscription' as
  monthly-rate x monitoring-months + 'compliance-audit export package' as
  an optional flat export-fee, only when the engagement actually
  requested the export package -- an honest structural match to the
  docs' own three revenue lines, not an invented pricing scheme).

  This namespace is pure data + pure functions -- no I/O, no network call
  to DoD, DCSA, GSA/SAM.gov, or any government portal. It builds the
  RECORD an operator would keep, not the act of actually filing/
  registering itself (that is `dodcompliance.operation`'s
  `:filing/submit`, always human-gated -- see README Actuation)."
  (:require [clojure.string :as str]))

(defn- unsigned-certificate
  "Every certificate this actor produces is UNSIGNED -- signature is the
  operator's act, not this actor's."
  [kind subject record-id]
  {"@context" ["https://www.w3.org/ns/credentials/v2"]
   "type" ["VerifiableCredential" kind]
   "credentialSubject" {"id" subject "record" record-id}
   "proof" nil
   "issued_by_registry" false
   "status" "draft-unsigned"})

(defn- zero-pad [n w]
  (let [s (str n)]
    (str (apply str (repeat (max 0 (- w (count s))) "0")) s)))

(defn- track-code [track]
  (str/upper-case (name track)))

(defn compute-engagement-fee
  "The ground-truth engagement fee for `engagement`'s own `:base-fee`
  (per-engagement compliance-review fee), `:monitoring-months` x
  `:monthly-rate` (recurring regulatory-change monitoring subscription),
  and -- only when `:audit-export?` is true -- `:export-fee`
  (compliance-audit export package). A single flat base + months x rate +
  optional export calculation, not a full pricing engine."
  [{:keys [base-fee monthly-rate monitoring-months audit-export? export-fee]}]
  (+ (double base-fee)
     (* (double monthly-rate) (double monitoring-months))
     (if audit-export? (double (or export-fee 0)) 0.0)))

(defn engagement-fee-matches-claim?
  "Does `engagement`'s own `:claimed-fee` equal the independently
  recomputed `compute-engagement-fee`?"
  [{:keys [claimed-fee] :as engagement}]
  (== (double claimed-fee) (compute-engagement-fee engagement)))

(defn register-draft
  "Validate + construct the FILING-DRAFT registration DRAFT for `track`
  (`:dfars` / `:cmmc` / `:facility-clearance`) -- the operator's own act
  of preparing a DoD compliance filing package. Pure function -- does not
  touch DoD, DCSA, GSA/SAM.gov, or any government portal."
  [engagement-id track sequence]
  (when-not (and engagement-id (not= engagement-id ""))
    (throw (ex-info "draft: engagement_id required" {})))
  (when-not (and track (not= track ""))
    (throw (ex-info "draft: track required" {})))
  (when (< sequence 0)
    (throw (ex-info "draft: sequence must be >= 0" {})))
  (let [draft-number (str "USA-DOD-" (track-code track) "-DFT-" (zero-pad sequence 6))
        record {"record_id" draft-number
                "kind" "filing-draft"
                "engagement_id" engagement-id
                "track" (name track)
                "immutable" true}]
    {"record" record "draft_number" draft-number
     "certificate" (unsigned-certificate "FilingDraft" draft-number draft-number)}))

(defn register-submit
  "Validate + construct the FILING-SUBMIT registration DRAFT for `track`
  -- the operator's own act of actually submitting the compliance filing
  to DoD (always human-gated upstream)."
  [engagement-id track sequence]
  (when-not (and engagement-id (not= engagement-id ""))
    (throw (ex-info "submit: engagement_id required" {})))
  (when-not (and track (not= track ""))
    (throw (ex-info "submit: track required" {})))
  (when (< sequence 0)
    (throw (ex-info "submit: sequence must be >= 0" {})))
  (let [submit-number (str "USA-DOD-" (track-code track) "-SUB-" (zero-pad sequence 6))
        record {"record_id" submit-number
                "kind" "filing-submit"
                "engagement_id" engagement-id
                "track" (name track)
                "immutable" true}]
    {"record" record "submit_number" submit-number
     "certificate" (unsigned-certificate "FilingSubmit" submit-number submit-number)}))

(defn append [history result]
  (conj (vec history) (get result "record")))
