# Acceptance evidence and repair decisions

Read this when creating or updating the acceptance ledger. Derive criterion IDs from the current `docs/ACCEPTANCE_CRITERIA.md`; do not keep a second fixed list here.

For every criterion record: ID, status, source/test location, reproduction or executed command, date and exit/result, artifact/report path, and remaining gap. Use these statuses:

- `VERIFIED`: the stated criterion has direct executed evidence and source review appropriate to its scope.
- `FAILED`: an executed check demonstrated a defect; include severity, reproduction and the repair.
- `UNVERIFIED`: not yet exercised, or evidence covers only part of the criterion.
- `EXTERNAL_BLOCK`: implementation and fixture contracts are present, but a named account, credential, live platform or deployment target prevents live proof.

The validation helper initializes all criteria as `UNVERIFIED` in its command-evidence JSON. Successful builds are never a reason to mechanically mark every row verified. Persist the assessed ledger in `docs/IMPLEMENTATION_STATUS.md`, retaining unresolved details across interruptions.

Inspect these especially carefully:

1. A fixture-backed provider proves request/response handling, not current account permissions or live delivery.
2. Schema-constrained output proves shape, not factual grounding. Trace claims to verified source facts.
3. One cross-user test does not cover every resource family; trace nested foreign IDs, downloads, approval and OAuth state too.
4. A healthy process does not prove durable state. Restart and compare records and object bytes.
5. An E2E fixture that manually supplies contacts does not prove live public-web research. Keep the connector fixture and live research evidence separate.
6. A documented deviation is not automatically acceptable. Reassess it against the original architecture and explicit user requirements; classify missing required behavior as an implementation gap.

Repair BLOCKER and HIGH defects before completion. For each repair retain the failing reproduction, code change and passing verification. Report MEDIUM/LOW gaps and external blocks explicitly; never convert missing implementation into a credential exception.
