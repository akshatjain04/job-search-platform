---
name: job-search-platform-repair
description: Audit, repair and complete the akshatjain04/job-search-platform V1 repository against its architecture and executable acceptance criteria. Use for a requested repository-wide completion or security/acceptance repair pass, not unrelated projects.
---

# Job Search Platform — prove and repair

Operate in the checkout whose origin is exactly `https://github.com/akshatjain04/job-search-platform`. This skill is a repair workflow, not another specification. Do not treat a previous handoff or status file as proof.

## Sources and authority

Before changing code, read completely:

1. `docs/reference/ai_job_search_platform_architecture_cost_optimized.docx` (original architecture; `scripts/read_architecture.py ROOT` extracts every paragraph/table cell in document order).
2. `docs/MASTER_IMPLEMENTATION_SPEC.md`.
3. `docs/ACCEPTANCE_CRITERIA.md`.
4. `docs/IMPLEMENTATION_STATUS.md` and applicable repository instructions.

Use architecture requirements first, then correctness/security, compatible repository conventions and cost-optimized V1 defaults. Explain any unavoidable deviation and migration implication. If a source is missing, search the checkout and user-supplied architecture location; do not invent its contents.

## Inspect → prove gap → repair → verify

1. Inspect Git status/history, actual source, configuration, migrations, tests and deployment scripts. Preserve unrelated edits. Verify local-only Git email `akshatjain0410@gmail.com`, exact origin and authenticated access before implementation commits; never change global/system identity or helper settings. If authentication is absent, follow `docs/git-bootstrap.md` using secure interactive login, never chat secrets.
2. Read [references/review-boundaries.md](references/review-boundaries.md). Create a criterion-by-criterion evidence ledger from the current acceptance document, not a fixed count in this skill. Run `node scripts/validate.mjs --root=ROOT --static` from this skill directory for inexpensive checks.
3. Run the full validation helper with `--full`: all Java deployables, both browser clients, root script tests, empty PostgreSQL/Testcontainers migrations, Docker image/Compose startup, readiness/smoke, and Playwright. Read every failing test/report. Install documented build prerequisites when authorized; never skip failures to obtain green output.
4. Inspect actual REST/OpenAPI/client DTO and MCP schemas. Exercise each MCP tool, invalid IDs, authentication and ownership. Inspect rendered PDF/DOCX and desktop/mobile screenshots, extension manifest/extraction/PKCE, and startup/deployment/TLS configuration. Follow `docs/testing.md`; test secret-dependent live integrations only with explicit authority and controlled recipients. A fixture-backed adapter is not live verification.
5. Audit cost boundaries and security, then search production paths for TODO/FIXME, placeholders, unsupported operations, fake/mock routing, hard-coded provider/model/identity values, dead code, unfinished transitions and duplicated business rules. Trace findings to reachable behavior; a term in a test or documentation is not automatically a defect.
6. Classify each proven gap: **BLOCKER** (unbuildable/startup/data-loss/approval bypass or unusable core path), **HIGH** (missing required workflow, broken provider contract, isolation/security defect or required executable acceptance failing), **MEDIUM** (bounded reliability/UX/coverage shortfall), **LOW** (non-blocking polish). Record affected criterion, reproduction, location, impact and intended repair.
7. Fix directly in coherent vertical slices. Add regression coverage for the demonstrated defect, rebuild and rerun relevant tests, then repeat the full gate after cross-module changes. Keep normal tests deterministic, non-billable and non-sending. Do not weaken approval, grounding, ownership or tests to make the suite pass.
8. Continue until no BLOCKER/HIGH implementation gaps remain and all realistically executable acceptance criteria pass. Missing credentials block only live proof, not real adapters, configuration, mocks or contract tests. New paid infrastructure, real external sends, destructive data changes or missing authority require user direction; persistence does not expand permission.
9. Update `IMPLEMENTATION_STATUS.md` with actual commands, dates, exit/results, report/artifact locations, acceptance IDs and remaining gaps. Distinguish implemented/fixture-verified, live verification credential-blocked, platform limitations, optional future work, and genuinely unimplemented work. Never label the repository complete while unresolved BLOCKER/HIGH implementation gaps exist.
10. Within the requested Git delivery scope, inspect status/staged diff and secrets before each meaningful commit, push tested checkpoints without force/history rewrite, and verify local HEAD equals the intended remote branch. Report exact start/test/stop/deploy commands, evidence, residual gaps and commit delivery.

The helper is an evidence collector, not a semantic auditor. Passing it does not prove every acceptance row, secure OAuth registration, public TLS, live provider access, or suitability of a specific EC2 size.
