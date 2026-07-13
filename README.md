# cloud-itonami-isco-3514

Open Business Blueprint for **ISCO-08 3514**: Web Technicians — an ISCO
**Wave 0 (cognitive substrate)** occupation per ADR-2607121000:
pure-cognitive work, the LLM-first wave, **no robotics gate** —
eligible for actor implementation now.

**Maturity: `:implemented`** — WebTechniciansAdvisor ⊣
WebTechniciansGovernor as a langgraph StateGraph
(`intake → advise → govern → decide → commit/hold`, human-approval
interrupt), modeled on cloud-itonami-isco-4311's bookkeeping actor.
13 tests / 27 assertions green.

The deployment HARD invariants — ordinal scale and set membership, not
aspirational:

1. **Conformance floor** — the proposed achieved WCAG conformance
   level must be ordinally ≥ the site's registered minimum (A < AA <
   AAA — conformance is ordinal, not aspirational).
2. **Domain membership** — the proposed deployment target domain must
   be a member of the site's registered approved-domains set (no
   unauthorized deployment target).

Also HARD: unregistered/foreign site, unregistered organization,
non-`:propose` effect. Escalations (always human sign-off):
`:approve-production-cutover` (DNS/production traffic switch), low
confidence (< 0.6).

AGPL-3.0-or-later, forkable by any qualified operator. Part of the
[cloud-itonami](https://itonami.cloud) open business fleet.
