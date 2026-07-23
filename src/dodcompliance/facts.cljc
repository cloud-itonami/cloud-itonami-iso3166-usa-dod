(ns dodcompliance.facts
  "U.S. Department of Defense (DoD) procurement / cybersecurity / facility-
  clearance compliance catalog -- the ONLY source of regulatory-requirement
  facts this actor is allowed to cite (`dodcompliance.governor`'s
  spec-basis check enforces that every proposal touching
  `:compliance/assess`, `:filing/draft`, or `:filing/submit` cites this
  catalog and nothing invented).

  Every fact below was verified during this repo's research pass
  (2026-07-22/23) against `acquisition.gov` (the DFARS entry, a DoD-issued
  Federal Acquisition Regulation supplement) plus, where DoD primary
  sources returned HTTP 403 during that pass, the Wikipedia articles cited
  in `:provenance` below for the CMMC and DCSA entries. Four tracks, each
  with its own owner authority -- do NOT merge them into one
  undifferentiated 'DoD requirement':

    :dfars               -- Defense Federal Acquisition Regulation
                             Supplement (DFARS). DoD-issued, supplements
                             the FAR, organized in Parts 201-270 plus
                             Appendices A-I.
    :cmmc                -- Cybersecurity Maturity Model Certification
                             (CMMC) 2.0. Three levels (L1/L2/L3); see
                             `:enforcement-status-caveat` below -- this
                             catalog deliberately does NOT assert a
                             specific date on which CMMC contract-clause
                             enforcement became (or becomes) fully live.
    :facility-clearance  -- DCSA (Defense Counterintelligence and Security
                             Agency) administration of the National
                             Industrial Security Program (NISP) facility
                             security clearance. DCSA is a DoD agency; its
                             NISP authority is not DoD-exclusive (see
                             `:cross-agency-note`).
    :sam-registration    -- System for Award Management (SAM.gov)
                             registration + the structural way DoD-specific
                             obligations (e.g. CMMC self-assessment-level
                             posting) attach to that registration via
                             DFARS clauses. Citation-only entry (see
                             `dodcompliance.store` docstring) -- not a
                             track this actor drafts/submits filings for,
                             the same role `:procurement-qualification`
                             plays in the JPN-MOD sibling catalog.

  What this catalog deliberately does NOT claim (kept strictly at the
  compliance-PROCESS level, same discipline as every sibling actor in this
  fleet):
    - no assertion that CMMC contract-clause enforcement is fully live as
      of any specific date -- CMMC has a well-documented history of
      schedule slips, and full enforcement status was NOT independently
      confirmed from an official DoD source during this catalog's
      research pass (primary `dodcio.defense.gov`-family sources 403'd).
      The DATED rulemaking-status facts below (OMB review clearance,
      Federal Register publication) ARE independently confirmed and are
      what this catalog cites -- 'the rule was published on this date' is
      a different, narrower claim than 'the rule's clauses are being
      enforced against contracts today', and this catalog only makes the
      former;
    - no specific DFARS clause numbers of any kind -- clause numbers
      recalled from background knowledge were NOT independently verified
      via a live fetch during this research pass, so none is cited here.
      The SAM.gov/DFARS linkage below is described structurally instead;
    - no numeric CMMC assessment-cost, timeline, or penalty figures (none
      verified);
    - no classified or operational detail of any kind related to facility
      clearances (none verified, and inventing any in a public OSS repo
      would be inappropriate regardless of accuracy).

  STALE-NAME TRAP: 'Defense Security Service (DSS)' is the RETIRED
  pre-2019 name for what is now DCSA. Never use DSS in this catalog or in
  code that cites it.")

(def catalog
  {:dfars
   {:name "DFARS -- Defense Federal Acquisition Regulation Supplement"
    :name-en "Defense Federal Acquisition Regulation Supplement (DFARS)"
    :owner-authority "U.S. Department of Defense (DFARS)"
    :legal-basis
    "48 CFR Chapter 2 -- DoD-issued supplement to the FAR (48 CFR Chapter 1), organized in Parts 201-270 plus Appendices A-I"
    :official-portal "https://www.acquisition.gov/dfars"
    :provenance "https://www.acquisition.gov/dfars"
    :process-description
    "DFARS clauses are inserted into DoD solicitations and contracts on top of the government-wide FAR baseline -- which Parts/Appendices apply is solicitation-specific; this catalog does not assert a universal applicability list."
    :required-evidence
    ["DFARS clause applicability review record for the specific solicitation/contract (which of Parts 201-270 / Appendices A-I apply)"
     "combined FAR+DFARS compliance cross-reference record for the contract"]}

   :cmmc
   {:name "CMMC -- Cybersecurity Maturity Model Certification (CMMC 2.0)"
    :name-en "Cybersecurity Maturity Model Certification (CMMC) 2.0"
    :owner-authority "U.S. Department of Defense (CMMC Program)"
    :legal-basis
    "CMMC 2.0 (streamlined program revision announced November 2021); the 48 CFR CMMC acquisition rule (inserting CMMC clauses into DoD contracts) cleared OMB review 2025-08-25 and was published 2025-09-10 -- these are the independently-confirmed, DATED rulemaking-status facts this catalog cites (see :enforcement-status-caveat for what is deliberately NOT claimed)"
    :levels
    {:l1 {:practices 14 :protects "Federal Contract Information (FCI)" :assessment "annual self-assessment"}
     :l2 {:practices 110 :aligned-to "NIST SP 800-171" :protects "Controlled Unclassified Information (CUI)" :assessment "third-party assessment for most contractors"}
     :l3 {:practices "110+" :aligned-to "NIST SP 800-171 + NIST SP 800-172" :protects "Controlled Unclassified Information (CUI), higher-priority programs" :assessment "government-led assessment"}}
    :rulemaking-cleared-omb "2025-08-25"
    :rulemaking-published "2025-09-10"
    :enforcement-status-caveat
    "Full contract-clause ENFORCEMENT status as of any given 'today' was NOT independently confirmed from an official DoD source during this catalog's research pass (primary sources 403'd) -- CMMC has a well-documented history of schedule slips. This catalog does NOT hardcode a claim that CMMC is fully enforced as of a specific date; treat full-enforcement-status as something to re-verify fresh at time of use, not a permanently settled fact. What IS cited above (OMB clearance date, Federal Register publication date) is independently confirmed."
    :official-portal "https://en.wikipedia.org/wiki/Cybersecurity_Maturity_Model_Certification"
    :provenance "https://en.wikipedia.org/wiki/Cybersecurity_Maturity_Model_Certification"
    :required-evidence
    ["CMMC self-assessment or third-party assessment record matching the contract's declared CMMC level (L1/L2/L3)"
     "NIST SP 800-171 (L2) and, where the contract declares L3, SP 800-172 control-implementation evidence, matching the declared level"]}

   :facility-clearance
   {:name "DCSA / NISP Facility Security Clearance"
    :name-en "Defense Counterintelligence and Security Agency (DCSA) -- National Industrial Security Program (NISP) Facility Security Clearance"
    :owner-authority "Defense Counterintelligence and Security Agency (DCSA)"
    :legal-basis
    "National Industrial Security Program (NISP) -- DCSA administers facility security clearances for approximately 10,000 cleared contractor companies. DCSA formed via a July 2019 reorganization (merger of the former Defense Security Service with OPM's background-investigation function)."
    :formed "2019-07"
    :cross-agency-note
    "DCSA administers NISP on behalf of DoD AND 35 other federal agencies -- its facility-clearance authority is not DoD-exclusive, though DoD is DCSA's parent department."
    :stale-name-trap
    "'Defense Security Service (DSS)' is the RETIRED pre-2019 name -- use 'Defense Counterintelligence and Security Agency (DCSA)' only. Never cite DSS as the current authority."
    :official-portal "https://en.wikipedia.org/wiki/Defense_Counterintelligence_and_Security_Agency"
    :provenance "https://en.wikipedia.org/wiki/Defense_Counterintelligence_and_Security_Agency"
    :required-evidence
    ["Facility Security Clearance (FCL) sponsorship/application record under the National Industrial Security Program (NISP)"
     "DCSA (current name -- not legacy Defense Security Service/DSS) point-of-contact and case-reference confirmation record"]}

   :sam-registration
   {:name "SAM.gov Registration + DFARS-Clause Linkage"
    :name-en "System for Award Management (SAM.gov) Registration -- DFARS-clause linkage"
    :owner-authority "General Services Administration (GSA, operates SAM.gov government-wide) -- DoD-specific obligations attach on top via DFARS clauses, not a separate DoD registration system"
    :legal-basis
    "No DoD-specific SAM.gov registration variant exists beyond the government-wide baseline (GSA-operated). DoD-specific obligations (e.g. CMMC self-assessment-level posting) ride on top of the shared registration via DFARS clauses -- described here structurally only."
    :clause-number-caveat
    "This catalog deliberately does NOT cite any specific DFARS clause number for this linkage -- clause numbers recalled from background knowledge were NOT independently verified via a live fetch during this research pass. No :official-portal/:provenance URL is given for this specific fact for the same reason: none was independently confirmed live this session. Verify a clause number via a live source before citing one in downstream work."
    :official-portal nil
    :provenance nil
    :required-evidence
    ["SAM.gov registration validity confirmation record (the shared government-wide prerequisite registration; no separate DoD-specific registration system exists)"
     "confirmation record of whether the applicable DFARS clause(s) require a CMMC-status-type posting tied to the SAM.gov record (no specific clause number cited by this catalog)"]}})

(def valid-tracks (set (keys catalog)))

(defn spec-basis [track] (get catalog track))

(defn coverage
  ([] (coverage (keys catalog)))
  ([tracks]
   (let [have (filter catalog tracks) missing (remove catalog tracks)]
     {:requested (count tracks) :covered (count have)
      :covered-tracks (vec (sort (map name have)))
      :missing-tracks (vec (sort (map name missing)))
      :note "R0 catalog seed -- DFARS + CMMC + DCSA/NISP facility clearance + SAM.gov/DFARS-clause linkage, USA-DOD agency scope"})))

(defn required-evidence-satisfied? [track submitted]
  (when-let [{:keys [required-evidence]} (spec-basis track)]
    (= (count required-evidence) (count (filter (set submitted) required-evidence)))))

(defn evidence-checklist [track] (:required-evidence (spec-basis track) []))

(defn cmmc-level-of
  "The CMMC level descriptor (`:l1`/`:l2`/`:l3`) catalog entry, or nil."
  [level]
  (get-in (spec-basis :cmmc) [:levels level]))
