# cloud-itonami-iso3166-usa-dod

Open ISO 3166 **agency-level** Blueprint for **USA-DOD**: U.S. Department
of Defense (DoD), under the `cloud-itonami-iso3166-usa` country-level
coordinator.

This repository designs a forkable OSS business for an independent
compliance consultant: an already-incorporated operator (typically one
already using `cloud-itonami-iso3166-usa` for general U.S. market entry)
gets a Compliance Advisor + independent **DoD Compliance Governor** to
navigate DFARS (Defense Federal Acquisition Regulation Supplement)
compliance for a DoD contract, CMMC (Cybersecurity Maturity Model
Certification) evidence for the contract's declared level, and
DCSA (Defense Counterintelligence and Security Agency)/NISP facility
security clearance prerequisites.

## No robotics premise — digital/data service exemption

Agency-specific compliance navigation is a pure data/software service
with no physical-domain work — the same exemption class as
`cloud-itonami-6310` and `cloud-itonami-gtin-*`. `blueprint.edn` sets
`:itonami.blueprint/robotics false` and `:required-technologies` lists
only real capabilities (`:identity`, `:forms`, `:dmn`, `:bpmn`,
`:audit-ledger`), no `:robotics`.

## Core Contract

```text
operator intake + prior filing/compliance history
        |
        v
Compliance Advisor -> DoD Compliance Governor -> compliance draft, or human sign-off
        |
        v
gated filing / registration / compliance-program submission + audit ledger
```

No automated proposal can submit a filing or registration the governor
refuses, suppress a compliance record, or claim a legal conclusion the
governor has not cleared. `:filing/submit` is never in any phase's
`:auto` set — it always requires human sign-off (mirrors
`cloud-itonami-iso3166-jpn-mod`'s
`filing-submit-never-auto-at-any-phase` invariant).

## Implementation

`src/dodcompliance/` — a langgraph-clj StateGraph actor, same
containment shape as `cloud-itonami-iso3166-jpn-mod`'s
`defensecompliance.*` (advisor sealed to proposals-only, independent
governor, append-only ledger, `Store` protocol swap, phase gate):

- `facts.cljc` — the DFARS + CMMC + DCSA/NISP facility-clearance +
  SAM.gov/DFARS-clause-linkage catalog, the ONLY source of
  regulatory-requirement facts the actor may cite. Four entries:
  `:dfars`, `:cmmc`, `:facility-clearance` (all three drafted/submitted
  as filing tracks), and `:sam-registration` (citation-only, the same
  role `:procurement-qualification` plays in the JPN-MOD sibling
  catalog — gates `:filing/submit` on any track, but is never itself a
  track a filing is drafted/submitted for). The CMMC entry carries an
  explicit `:enforcement-status-caveat` — this catalog never asserts a
  specific date on which CMMC contract-clause enforcement became (or
  becomes) fully live; it only cites the independently-confirmed, DATED
  rulemaking-status facts (OMB review clearance 2025-08-25, Federal
  Register publication 2025-09-10). The facility-clearance entry carries
  an explicit `:stale-name-trap` — 'Defense Security Service (DSS)' is
  the RETIRED pre-2019 name; only 'Defense Counterintelligence and
  Security Agency (DCSA)' is current and cited.
- `governor.cljc` — the DoD Compliance Governor: a spec-basis/
  no-fabrication HARD check, an evidence-incomplete check, a
  **SAM.gov-registration-missing** HARD check (`:filing/submit`, any
  track, conditional on the engagement's own declared
  `:requires-sam-registration?`), a **CMMC-level-missing** HARD check
  (`:filing/submit`, `:cmmc` track only, conditional on the engagement's
  own declared `:requires-cmmc-level?` — this governor never
  independently decides CMMC applies to every DoD contract, it only
  verifies the engagement's own declared requirement has evidence on
  file), a **DCSA/NISP-facility-clearance-missing** HARD check
  (`:filing/submit`, `:facility-clearance` track only, conditional on
  the engagement's own declared `:requires-facility-clearance?`), an
  independently-recomputed engagement-fee-mismatch check (three revenue
  lines: base fee + monitoring subscription + optional audit-export
  package), a confidence-floor/actuation gate, and double-draft/
  double-submit guards, per track.
- `store.cljc` — `MemStore`/`DatomicStore` (via
  `kotoba-lang/langchain-store`, not a hand-rolled `enc`/`dec*`) for the
  `engagement` entity, which tracks the `:dfars`, `:cmmc`, and
  `:facility-clearance` tracks' filing state independently, plus the
  engagement-level `:sam-registration-verified?`,
  `:cmmc-level-verified?`, and `:facility-clearance-verified?` gates.
- `registry.cljc` — pure-function filing-draft/filing-submit record
  construction, one sequence per track.
- `dodcompliancellm.cljc` — the Compliance Advisor (mock LLM, proposals
  only).
- `operation.cljc` — the StateGraph: intake → advise → govern → decide
  → [request-approval →] commit/hold, `interrupt-before` on human
  approval.
- `phase.cljc` — phase 0→3 rollout; `:filing/draft`/`:filing/submit` are
  permanently absent from every phase's `:auto` set.

Ops: `:engagement/intake`, `:compliance/assess` (per-track evidence
checklist), `:filing/draft`, `:filing/submit` — the latter three take a
`:track` (`:dfars`, `:cmmc`, or `:facility-clearance`) in the request,
since one engagement runs all three filing tracks independently.
`:sam-registration` is a citation-only spec-basis entry for the
SAM.gov-registration-missing check, not a track this actor drafts/
submits filings for.

This is a security-adjacent compliance domain — every module stays
strictly at the compliance-PROCESS level (paperwork / eligibility /
clearance-status tracking) — nothing here models, stores, or reasons
about classified or Controlled Unclassified Information (CUI) content.

## What this is NOT

- **Not the U.S. Department of Defense, DCSA, or GSA, and not the
  government of the United States.** Commercial compliance navigation
  only.
- **Not legal or tax advice.** Every regulatory claim must cite the
  official source (acquisition.gov for DFARS; the CMMC/DCSA entries cite
  Wikipedia because primary DoD sources 403'd during this repo's
  research pass — see facts.cljc for the honest caveat on that), and
  route final filings to U.S.-licensed counsel or a registered agent
  where the law requires licensed representation.

## Capability layer

Resolves via [`kotoba-lang/iso3166`](https://github.com/kotoba-lang/iso3166)
(code `USA-DOD`, `:parent "USA"`, cross-referenced to ooyake's
`gov.usa.dod`). Required capabilities:

- :identity
- :forms
- :dmn
- :bpmn
- :audit-ledger

See [`docs/business-model.md`](docs/business-model.md) and
[`docs/operator-guide.md`](docs/operator-guide.md).

## License

AGPL-3.0-or-later.
