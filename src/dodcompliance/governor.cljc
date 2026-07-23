(ns dodcompliance.governor
  "DoD Compliance Governor -- the independent compliance layer that earns
  the DoDCompliance-LLM the right to commit. The LLM has no notion of
  what DFARS (Defense Federal Acquisition Regulation Supplement) actually
  requires for a given solicitation, what CMMC level (if any) a
  contract's own terms declare and whether that level's assessment is
  actually on file, whether SAM.gov registration is actually verified,
  whether DCSA/NISP facility security clearance is actually complete for
  staff/facilities that need it, whether a claimed engagement fee
  actually equals base + months x rate (+ optional export package), or
  when a draft stops being a draft and becomes a real-world DoD filing,
  so this MUST be a separate system able to *reject* a proposal and fall
  back to HOLD.

  `:itonami.blueprint/governor` is `:dod-compliance-governor`
  (blueprint.edn).

  This blueprint's own text (docs/business-model.md Trust Controls: 'any
  actual filing, registration, or compliance-program submission requires
  DoD Compliance Governor clearance and always escalates to human
  sign-off'; 'a false or fabricated regulatory-requirement claim is a
  HARD hold that cannot be overridden by human approval alone') names
  exactly the checks below.

  IMPORTANT ON CMMC: this governor NEVER asserts that CMMC enforcement is
  live as of any specific date (see `dodcompliance.facts`'
  `:enforcement-status-caveat`). The `cmmc-level-missing-violations` check
  below is CONDITIONAL on the ENGAGEMENT'S OWN declared
  `:requires-cmmc-level?` flag -- i.e. it only fires when THIS
  engagement's own contract terms already say a CMMC level applies, the
  same discipline the JPN-MOD sibling uses for its aptitude-assessment
  check. The governor does not independently decide CMMC applies to
  every DoD contract; it only verifies the engagement's own declared
  requirement has evidence on file.

  Eight checks, in priority order, ALL HARD violations except the
  confidence/actuation gate: a human approver CANNOT override the hard
  ones. The confidence/actuation gate is SOFT: it asks a human to look
  (low confidence / actuation), and the human may approve -- but see
  `dodcompliance.phase`: for `:stake :actuation/draft-filing`/
  `:actuation/submit-filing` NO phase ever allows auto-commit either. Two
  independent layers agree that actuation is always a human call.

    1. Spec-basis                     -- did the compliance-track
                                          proposal cite an OFFICIAL source
                                          (`dodcompliance.facts`), or
                                          invent one?
    2. Evidence incomplete            -- for `:filing/draft`/
                                          `:filing/submit`, has the track
                                          actually been assessed with a
                                          full evidence checklist on file?
    3. SAM-registration missing       -- for `:filing/submit` (ANY
                                          track), when the engagement
                                          declares `:requires-sam-
                                          registration? true`,
                                          INDEPENDENTLY verify
                                          `:sam-registration-verified?`
                                          is true. SAM.gov is the shared
                                          government-wide prerequisite
                                          registration DFARS clauses tie
                                          DoD-specific obligations to
                                          (facts.cljc `:sam-registration`)
                                          -- NOT track-scoped, it gates
                                          any filing/submit.
    4. CMMC-level missing             -- for `:filing/submit` on the
                                          `:cmmc` track, when the
                                          engagement declares
                                          `:requires-cmmc-level? true`,
                                          INDEPENDENTLY verify
                                          `:cmmc-level-verified?` is
                                          true. Conditional on the
                                          engagement's OWN declared
                                          requirement -- see the CMMC
                                          note above.
    5. Facility-clearance missing     -- for `:filing/submit` on the
                                          `:facility-clearance` track,
                                          when the engagement declares
                                          `:requires-facility-clearance?
                                          true`, INDEPENDENTLY verify
                                          `:facility-clearance-verified?`
                                          is true -- DCSA/NISP facility
                                          security clearance
                                          (facts.cljc
                                          `:facility-clearance`).
    6. Engagement fee mismatch        -- for `:filing/submit`,
                                          INDEPENDENTLY recompute whether
                                          the engagement's own `:claimed-
                                          fee` equals `base-fee +
                                          monthly-rate x monitoring-
                                          months` (+ optional export-fee
                                          when `:audit-export?` is true)
                                          -- honest reapplication of the
                                          ground-truth-recompute
                                          discipline sibling actors use,
                                          matched against this repo's own
                                          three revenue lines
                                          (per-engagement compliance-
                                          review fee + recurring
                                          monitoring subscription +
                                          compliance-audit export
                                          package).
    7. Confidence floor / actuation
       gate                             -- LLM confidence below
                                          threshold, OR the op is
                                          `:filing/draft`/`:filing/submit`
                                          (REAL acts) -> escalate.

  Two more guards, double-draft/double-submit prevention, are enforced
  off dedicated per-track `:dfars-drafted?`/`:dfars-submitted?`/
  `:cmmc-drafted?`/`:cmmc-submitted?`/`:facility-clearance-drafted?`/
  `:facility-clearance-submitted?` facts (never a single `:status`
  value)."
  (:require [dodcompliance.facts :as facts]
            [dodcompliance.registry :as registry]
            [dodcompliance.store :as store]))

(def confidence-floor 0.6)

(def high-stakes
  "Stakes grave enough to always require a human, even when clean.
  Drafting a real DoD compliance filing package and submitting a real
  DoD filing are the two real-world actuation events this actor
  performs."
  #{:actuation/draft-filing :actuation/submit-filing})

;; ----------------------------- checks -----------------------------

(defn- spec-basis-violations
  "A `:compliance/assess` (or `:filing/draft`/`:filing/submit`) proposal
  with no spec-basis citation is a HARD violation -- never invent DoD's
  DFARS/CMMC/facility-clearance requirements."
  [{:keys [op]} proposal]
  (when (contains? #{:compliance/assess :filing/draft :filing/submit} op)
    (let [value (:value proposal)]
      (when (or (empty? (:cites proposal))
                (and (contains? value :spec-basis) (nil? (:spec-basis value))))
        [{:rule :no-spec-basis
          :detail "no official spec-basis citation -- cannot be treated as a compliance requirement"}]))))

(defn- evidence-incomplete-violations
  "For `:filing/draft`/`:filing/submit`, the track's required evidence
  checklist must actually be satisfied."
  [{:keys [op subject track]} st]
  (when (contains? #{:filing/draft :filing/submit} op)
    (let [assessment (store/assessment-of st subject track)]
      (when-not (and assessment
                     (facts/required-evidence-satisfied?
                      track (:checklist assessment)))
        [{:rule :evidence-incomplete
          :detail (str subject "/" (name track) " proposed with required evidence checklist not satisfied")}]))))

(defn- sam-registration-missing-violations
  "For `:filing/submit` on ANY track, when the engagement declares
  `:requires-sam-registration? true`, INDEPENDENTLY verify
  `:sam-registration-verified?` is true -- SAM.gov registration is the
  shared government-wide prerequisite DFARS clauses tie DoD-specific
  obligations to, not track-scoped. CONDITIONAL on the engagement's own
  `:requires-sam-registration?` ground truth."
  [{:keys [op subject]} st]
  (when (= op :filing/submit)
    (let [e (store/engagement st subject)]
      (when (and (true? (:requires-sam-registration? e))
                 (not (true? (:sam-registration-verified? e))))
        [{:rule :sam-registration-missing
          :detail (str subject " has unverified SAM.gov registration -- submit proposal cannot proceed")}]))))

(defn- cmmc-level-missing-violations
  "For `:filing/submit` on the `:cmmc` track, when the engagement
  declares `:requires-cmmc-level? true`, INDEPENDENTLY verify
  `:cmmc-level-verified?` is true. Conditional on the engagement's OWN
  declared requirement -- this governor never independently asserts CMMC
  applies to every DoD contract (see `dodcompliance.facts`
  `:enforcement-status-caveat`)."
  [{:keys [op subject track]} st]
  (when (and (= op :filing/submit) (= track :cmmc))
    (let [e (store/engagement st subject)]
      (when (and (true? (:requires-cmmc-level? e))
                 (not (true? (:cmmc-level-verified? e))))
        [{:rule :cmmc-level-missing
          :detail (str subject " has an unverified CMMC self-/third-party-assessment for its declared level -- submit proposal cannot proceed")}]))))

(defn- facility-clearance-missing-violations
  "For `:filing/submit` on the `:facility-clearance` track, when the
  engagement declares `:requires-facility-clearance? true`, INDEPENDENTLY
  verify `:facility-clearance-verified?` is true -- DCSA/NISP facility
  security clearance (facts.cljc `:facility-clearance`)."
  [{:keys [op subject track]} st]
  (when (and (= op :filing/submit) (= track :facility-clearance))
    (let [e (store/engagement st subject)]
      (when (and (true? (:requires-facility-clearance? e))
                 (not (true? (:facility-clearance-verified? e))))
        [{:rule :facility-clearance-missing
          :detail (str subject " has an unverified DCSA/NISP facility security clearance -- submit proposal cannot proceed")}]))))

(defn- engagement-fee-mismatch-violations
  "For `:filing/submit`, INDEPENDENTLY recompute whether the engagement's
  own claimed fee equals base + months x rate (+ optional export-fee)."
  [{:keys [op subject]} st]
  (when (= op :filing/submit)
    (let [e (store/engagement st subject)]
      (when-not (registry/engagement-fee-matches-claim? e)
        [{:rule :engagement-fee-mismatch
          :detail (str subject " claimed fee (" (:claimed-fee e)
                      ") does not match independently recomputed value (" (registry/compute-engagement-fee e) ")")}]))))

(defn- already-drafted-violations
  "For `:filing/draft`, refuses to draft the SAME engagement/track
  twice."
  [{:keys [op subject track]} st]
  (when (= op :filing/draft)
    (when (store/engagement-track-drafted? st subject track)
      [{:rule :already-drafted
        :detail (str subject "/" (name track) " already has a draft on file")}])))

(defn- already-submitted-violations
  "For `:filing/submit`, refuses to submit the SAME engagement/track
  twice."
  [{:keys [op subject track]} st]
  (when (= op :filing/submit)
    (when (store/engagement-track-submitted? st subject track)
      [{:rule :already-submitted
        :detail (str subject "/" (name track) " already has a submission on file")}])))

(defn check
  "Censors a DoDCompliance-LLM proposal against the governor rules.
  Returns {:ok? bool :violations [..] :confidence c :escalate? bool
  :high-stakes? bool :hard? bool}."
  [request _context proposal st]
  (let [hard (into []
                   (concat (spec-basis-violations request proposal)
                           (evidence-incomplete-violations request st)
                           (sam-registration-missing-violations request st)
                           (cmmc-level-missing-violations request st)
                           (facility-clearance-missing-violations request st)
                           (engagement-fee-mismatch-violations request st)
                           (already-drafted-violations request st)
                           (already-submitted-violations request st)))
        conf (:confidence proposal 0.0)
        low? (< conf confidence-floor)
        stakes? (boolean (high-stakes (:stake proposal)))
        hard? (boolean (seq hard))]
    {:ok?          (and (not hard?) (not low?) (not stakes?))
     :violations   hard
     :confidence   conf
     :hard?        hard?
     :escalate?    (and (not hard?) (or low? stakes?))
     :high-stakes? stakes?}))

(defn hold-fact
  "The audit fact written when a proposal is rejected (HOLD)."
  [request context verdict]
  {:t          :governor-hold
   :op         (:op request)
   :actor      (:actor-id context)
   :subject    (:subject request)
   :track      (:track request)
   :disposition :hold
   :basis      (mapv :rule (:violations verdict))
   :violations (:violations verdict)
   :confidence (:confidence verdict)})
