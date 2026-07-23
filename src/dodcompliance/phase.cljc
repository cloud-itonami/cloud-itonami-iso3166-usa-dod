(ns dodcompliance.phase
  "Phase 0->3 staged rollout for the USA-DOD (U.S. Department of Defense)
  compliance actor.

    Phase 0  read-only        -- no writes, still governor-gated.
    Phase 1  assisted-intake  -- engagement intake allowed, every write
                                 needs human approval.
    Phase 2  assisted-assess  -- adds per-track compliance assessment
                                 writes, still approval.
    Phase 3  supervised auto  -- governor-clean, high-confidence
                                 `:engagement/intake` (no filing-facing
                                 risk yet) may auto-commit.
                                 `:filing/draft`/`:filing/submit`
                                 NEVER auto-commit, at any phase.

  `:filing/draft`/`:filing/submit` are deliberately ABSENT from every
  phase's `:auto` set, including phase 3 -- a permanent structural fact,
  not a rollout milestone still to come (mirrors this repo's own
  README: '`:filing/submit` is never in any phase's `:auto` set').
  Drafting a real DoD compliance filing package and submitting a real DoD
  filing (DFARS / CMMC / DCSA-NISP facility-clearance track) are the two
  real-world acts this actor performs; both are always a human
  operator's call. `dodcompliance.governor`'s `:actuation/draft-filing`/
  `:actuation/submit-filing` high-stakes gate enforces the same
  invariant independently -- two layers, not one, agree on this.")

(def read-ops  #{})
(def write-ops #{:engagement/intake :compliance/assess :filing/draft :filing/submit})

;; NOTE the invariant: `:filing/draft`/`:filing/submit` are members of
;; `write-ops` (governor-gated like any write) but are NEVER members of
;; any phase's `:auto` set below. Do not add them there.
(def phases
  "phase -> {:label .. :writes <ops allowed to write> :auto <ops allowed to
  auto-commit when governor-clean>}."
  {0 {:label "read-only"        :writes #{}                                                              :auto #{}}
   1 {:label "assisted-intake"  :writes #{:engagement/intake}                                            :auto #{}}
   2 {:label "assisted-assess"  :writes #{:engagement/intake :compliance/assess}                          :auto #{}}
   3 {:label "supervised-auto"  :writes write-ops
      :auto #{:engagement/intake}}})

(def default-phase 3)

(defn gate
  "Adjust a governor disposition for the rollout phase. Returns
  {:disposition kw :reason kw|nil}."
  [phase {:keys [op]} governor-disposition]
  (let [{:keys [writes auto]} (get phases phase (get phases default-phase))]
    (cond
      (= :hold governor-disposition)       {:disposition :hold :reason nil}
      (contains? read-ops op)              {:disposition governor-disposition :reason nil}
      (not (contains? writes op))          {:disposition :hold :reason :phase-disabled}
      (and (= :commit governor-disposition)
           (not (contains? auto op)))      {:disposition :escalate :reason :phase-approval}
      :else                                {:disposition governor-disposition :reason nil})))

(defn verdict->disposition
  "Map a DoD Compliance Governor verdict to a base disposition before the
  phase gate."
  [verdict]
  (cond (:hard? verdict) :hold
        (:escalate? verdict) :escalate
        :else :commit))
