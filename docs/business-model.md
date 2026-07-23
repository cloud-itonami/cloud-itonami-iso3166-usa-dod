# Business Model: Independent DoD/DFARS Defense-Procurement Compliance Service — United States

Implementation: `src/dodcompliance/` — see README.md's Implementation
section. The Trust Controls below are enforced in code by
`dodcompliance.governor` (spec-basis/no-fabrication HARD check,
SAM.gov-registration-missing HARD check, CMMC-level-missing HARD check,
DCSA/NISP-facility-clearance-missing HARD check, engagement-fee-mismatch
check, confidence-floor/actuation gate, double-draft/double-submit
guards) and `dodcompliance.phase` (`:filing/submit` absent from every
phase's `:auto` set).

## Classification

- Repository: `cloud-itonami-iso3166-usa-dod`
- ISO 3166 (agency-level): `USA-DOD`, parent `USA`
- Ooyake cross-reference: `gov.usa.dod` (U.S. Department of Defense)
- Activity: DFARS (Defense Federal Acquisition Regulation Supplement)
  compliance-review filings, CMMC (Cybersecurity Maturity Model
  Certification) evidence for a contract's declared level, and
  DCSA (Defense Counterintelligence and Security Agency)/NISP facility
  security clearance filings
- Social impact: [:public-spend-transparency :sme-market-access :cross-border-friction-reduction]

## Customer

- an operator already using `cloud-itonami-iso3166-usa` whose contract
  touches DoD-specific rules or buying channels
- a foreign SME entering a DoD-specific public program for the first
  time, needing DFARS/CMMC/facility-clearance readiness before bidding

## Offer

- DFARS clause-applicability walkthrough for the specific
  solicitation/contract
- CMMC evidence checklist matching the contract's own declared level
  (L1/L2/L3) — never an independent claim about which level applies or
  whether CMMC enforcement is currently live
- DCSA/NISP facility security clearance (FCL) prerequisite checklist
- ongoing regulatory-change monitoring for DFARS/CMMC/DCSA public
  sources
- compliance-audit export package for the operator's own records

## Revenue

- per-engagement compliance-review fee
- recurring regulatory-change monitoring subscription
- compliance-audit export package

## Trust Controls

- any actual filing, registration, or compliance-program submission
  requires DoD Compliance Governor clearance and always escalates to
  human sign-off (`:filing/submit` is never automated at any phase)
- a false or fabricated regulatory-requirement claim is a HARD hold that
  cannot be overridden by human approval alone — it must be corrected
  against a cited official source first
- CMMC-related checks are always CONDITIONAL on the engagement's own
  declared requirement (`:requires-cmmc-level?`) — this service never
  independently asserts CMMC applies to every DoD contract, or that
  enforcement is live as of a specific date
- 'Defense Security Service (DSS)' is never cited as DCSA's current
  name — DSS was retired in the July 2019 DCSA reorganization
- this service does **not** provide legal or tax advice; characterization
  and filing on the client's behalf beyond checklist/draft assistance
  routes to U.S.-licensed counsel or a registered agent
- every requirement cites the official source (acquisition.gov for
  DFARS; the CMMC/DCSA entries cite Wikipedia because primary DoD
  sources 403'd during this repo's research pass, disclosed honestly in
  `src/dodcompliance/facts.cljc`), never invented — and no specific DFARS
  clause number is cited unless independently verified live

## Boundary with adjacent actors (read before forking)

- **`cloud-itonami-iso3166-usa`**: the COUNTRY-level coordinator (general
  U.S. public-sector market entry). This repo is a narrower, deeper
  AGENCY-level leaf — most operators need the country-level blueprint
  plus only the agency-level blueprints that actually apply to their
  contract.
- **`com-etzhayyim-ooyake`** (etzhayyim/root): read-only civic-wayfinding
  mirror of government structure, non-commercial, barred from acting as
  or for the government (G3 impersonation ban). This blueprint is
  commercial and never claims to be the Department of Defense, DCSA, GSA,
  or an official channel.
- **`matsurigoto`** (etzhayyim/root): sovereign e-government statecraft —
  literally the government. This blueprint is an independent operator
  that engages with DoD under its public rules — never the agency
  itself.
- **`com-etzhayyim-toritsugi`** (etzhayyim/root): guides a consenting
  INDIVIDUAL citizen through their OWN procedure, non-profit,
  donation-only. This blueprint's client is a business operator, not an
  individual citizen, and it is commercial.
- **`cloud-itonami-iso3166-usa-dol`/`cloud-itonami-iso3166-usa-gsa`**:
  sibling agency-level leaves for the Department of Labor and General
  Services Administration respectively — different regulatory domains,
  built independently in parallel with this repo. This blueprint stays
  scoped to DoD-issued/DoD-linked requirements (DFARS, CMMC, DCSA/NISP,
  SAM.gov linkage) only; labor-standards and general-services-specific
  requirements belong to those sibling actors, not here.
