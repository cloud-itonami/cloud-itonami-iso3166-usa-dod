# Operator Guide

1. Confirm country-level USA readiness (`cloud-itonami-iso3166-usa`)
   first.
2. Intake: which DoD program/contract applies, and which of the three
   filing tracks (`:dfars`, `:cmmc`, `:facility-clearance`) it needs.
3. Advisor reads only against `src/dodcompliance/facts.cljc` (DFARS via
   acquisition.gov/dfars; CMMC and DCSA/NISP facility clearance via the
   Wikipedia sources cited in that file, since primary DoD sources
   403'd during this repo's research pass).
4. For the `:cmmc` track, confirm whether the CONTRACT ITSELF declares a
   CMMC level requirement before treating `:requires-cmmc-level?` as
   true for that engagement — this actor never assumes CMMC applies
   universally.
5. For the `:facility-clearance` track, confirm DCSA (not the retired
   'Defense Security Service (DSS)' name) is the correct current
   point of contact for any FCL sponsorship/status question.
6. Confirm SAM.gov registration is current before any `:filing/submit`
   — DFARS clauses tie DoD-specific obligations to that shared
   registration; there is no separate DoD-specific registration system.
7. Human-gated filing drafts only; never auto-submit, on any track, at
   any phase.
