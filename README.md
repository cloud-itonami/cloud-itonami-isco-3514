# cloud-itonami-isco-3514

Open Business Blueprint for **ISCO-08 3514**: Web Technicians — an ISCO
**Wave 0 (cognitive substrate)** occupation per ADR-2607121000:
pure-cognitive work, the LLM-first wave, **no robotics gate** —
eligible for actor implementation now.

**Maturity: `:implemented`** — WebTechniciansAdvisor ⊣
WebTechniciansGovernor as a langgraph StateGraph
(`intake → advise → govern → decide → commit/hold`, human-approval
interrupt), modeled on cloud-itonami-isco-4311's bookkeeping actor.
26 tests / 57 assertions green.

## The operations this actor offers

`webtech.operations/registry` is the closed set. Each entry declares
`:requires-site-basis?` and `:always-escalates?`; there is no default,
and `webtech.operations/underdeclared` fails the suite if an entry omits
one. A proposal carrying any other `:op` is a HARD violation
(`:unoffered-op`) and is held.

| op | site basis | always escalates |
|---|---|---|
| `:approve-deployment` | yes | no |
| `:approve-production-cutover` | yes | **yes** |
| `:schedule-maintenance-window` | yes | no |
| `:report-accessibility-audit` | yes | no |

This closes two gates that were open at `802ca04`, both proven by
mutation before they were fixed:

1. **An op the actor does not offer was approved.** `:op` was checked
   against nothing, so an unrecognised op matched none of the
   site-basis rules, collected zero violations and returned `:ok? true`
   straight through to `:commit`. `webtech.advisor/parse-proposal`
   itself emits `{:op :unknown}` for unparseable LLM output, so this was
   reachable without a hostile caller.
2. **The riskiest op was the least checked.** Site basis, conformance
   floor and domain membership were gated on
   `(= :approve-deployment op)`, so `:approve-production-cutover` — the
   DNS/production traffic switch — skipped all three and merely
   escalated. Escalation is not refusal: `webtech.actor/approve!`
   resumes an escalated run straight into `:commit`, so a human sign-off
   committed a cutover to an unregistered site, an unapproved domain, or
   below the registered conformance floor.

The deployment HARD invariants — ordinal scale and set membership, not
aspirational:

1. **Conformance floor** — the proposed achieved WCAG conformance
   level must be ordinally ≥ the site's registered minimum (A < AA <
   AAA — conformance is ordinal, not aspirational).
2. **Domain membership** — the proposed deployment target domain must
   be a member of the site's registered approved-domains set (no
   unauthorized deployment target).

Both apply to **every** op declaring `:requires-site-basis?`, not just
`:approve-deployment`.

Also HARD: an unoffered op, unregistered/foreign site, unregistered
organization, non-`:propose` effect. Escalations (always human
sign-off): any op declaring `:always-escalates?` (currently
`:approve-production-cutover`, the DNS/production traffic switch), low
confidence (< 0.6).

AGPL-3.0-or-later, forkable by any qualified operator. Part of the
[cloud-itonami](https://itonami.cloud) open business fleet.
