# V1 acceptance criteria

Each row requires executable evidence or an explicit unimplemented/externally blocked status. A filename or mock alone never proves a production capability. `IMPLEMENTATION_STATUS.md` records actual execution results.

| ID | Requirement and concrete acceptance | Verification target |
|---|---|---|
| GIT-01 | Exact origin, local email, secure auth completed before app work; no global/system edits | Git bootstrap fingerprints and authenticated repository query |
| GIT-02 | Multiple meaningful tested commits pushed; final local HEAD equals origin/main | git log; git rev-parse HEAD; git ls-remote origin refs/heads/main |
| BUILD-01 | All Java modules clean-build on Java 21 | mvn -f backend/pom.xml clean verify |
| BUILD-02 | Web and extension typecheck, tests, production bundles | npm run verify --prefix web; npm run verify --prefix extension |
| DB-01 | Complete Flyway migration on empty PostgreSQL; constraints/indexes present | PostgreSQL integration suite |
| DB-02 | Transactions preserve aggregate + outbox consistency; restart preserves data | integration and restart smoke tests |
| SEC-01 | Unauthenticated requests rejected; invalid/expired tokens rejected | API and MCP security tests |
| SEC-02 | User B cannot read/update/download/approve/queue user A resources or attach A foreign IDs | cross-user integration suite |
| SEC-03 | CSRF, CORS, SSRF, uploads, paths, XSS and rate limits enforced | security tests and manual threat review |
| SEC-04 | No tracked secrets, frontend refresh tokens or token/prompt logs | repository audit + token encryption tests |
| PROFILE-01 | Full structured profile/preferences and explicitly verified provenance facts persist | profile API + UI tests |
| JOB-01 | Greenhouse/Lever/Ashby/structured career/import adapters parse fixtures | connector tests |
| JOB-02 | Canonical dedup preserves multiple sources; posting windows distinguish reposts | normalization/dedup tests |
| JOB-03 | Hard filters and configurable weighted ranking persist explanations; all search filters work | domain + API tests |
| JOB-04 | Hiring/referral classification, source provenance, alerts and scheduling work | capture/worker tests |
| AI-01 | Gemini default; all three real adapters selectable; only selected credentials required | provider registry tests |
| AI-02 | Every provider routes cheap/medium/high configured IDs; unknown/missing config actionable | provider configuration tests |
| AI-03 | Local schema/grounding validation rejects malformed/invented data | AI tests |
| AI-04 | Bounded retry/timeouts/cancellation/error normalization, metadata, caching and no silent fallback | adapter tests |
| AI-05 | Normal suites make zero billable calls | fixture HTTP/fake ports only; integration live flags absent |
| RESUME-01 | PDF/DOCX ingestion extracts real text, explicit failure on unreadable input | parser fixtures/tests |
| RESUME-02 | Immutable structured versions and deterministic downloadable PDF/DOCX retain provenance | render/extract/storage tests |
| RESUME-03 | Six score dimensions, 80/95 recommendation gate, bounded repair and scoring history | scorer/pipeline tests |
| CONTACT-01 | Research adapter stores actual source-supported contacts with confidence/status/method | research fixture tests |
| CONTACT-02 | Inferred/unverified email is never sendable; no fabricated URL or identity | contact/approval tests |
| OUTREACH-01 | Three draft channels, templates/tone/length/custom instructions, grounded facts | generation + UI tests |
| APPROVAL-01 | Exact recipient/message version/resume version/attachment shown before explicit approval | API + Playwright approval path |
| APPROVAL-02 | Any approved-field change invalidates approval; stale queue cannot send | concurrency/invalidation tests |
| MAIL-01 | Real Gmail/Graph adapters with encrypted OAuth, safe test sink, bounded failures | mailbox adapter fixtures/tests |
| MAIL-02 | No send without current immutable approval; ambiguous delivery does not blindly retry | communication worker tests |
| ASYNC-01 | SKIP LOCKED concurrent claim, lease recovery, backoff, max attempts, terminal visibility, idempotency | PostgreSQL concurrency tests |
| APP-01 | Legal lifecycle transitions only; immutable history and analytics | state machine/API tests |
| WEB-01 | Accessible responsive complete profile/jobs/resumes/recruiter/tracker/approval/analytics workflows | component + E2E + visual review |
| EXT-01 | MV3 minimal permissions, user-triggered extraction/auth/errors/actions, no crawl | extension fixture tests + build review |
| MCP-01 | Every required tool validates schema/auth/ownership and shares use cases | MCP tests |
| MCP-02 | No arbitrary send; invalid IDs and privileged page instructions produce controlled failures | MCP/security E2E tests |
| E2E-01 | Login → profile → upload/review → capture → match → tailor → score → research → draft → preview/approve → test send → history | Playwright golden path |
| E2E-02 | Edited approved message cannot send; injected page instructions cannot mutate privileged state | Playwright negative paths |
| OPS-01 | All images build, production Compose validates, only intended ports exposed | docker compose config; docker compose build |
| OPS-02 | Clean startup, migration, readiness, smoke, restart and graceful stop succeed | bootstrap scripts + health/smoke |
| OPS-03 | TLS/Nginx routes, correlation IDs, JSON logs, safe diagnostics and backup recovery documented | config tests + operational runbook |
| DEPLOY-01 | Bash/PowerShell preflight fails before build for missing selected credentials; inactive optional | script tests |
| DEPLOY-02 | EC2 script builds/tests/transfers/validates/starts/checks without erasing persistent state | shell validation + deployment test when target available |
| ARCH-01 | Production DB remote; Supabase storage port; one-EC2 Compose; no prohibited infrastructure | architecture dependency/config audit |
| COST-01 | Filter/cache before costly AI, usage metadata, opt-in enrichment, no phone credits default | cost-control tests/config review |
| DOC-01 | Complete quick start, setup, operations, test and security docs with honest evidence | docs/link audit |
| SKILL-01 | Reusable audit/repair skill reads sources, proves/fixes gaps and reruns deterministic checks | skill validation + packaged script checks |

Live production provider calls and EC2/TLS deployment require external accounts/credentials. Fixture-backed adapter verification is required regardless. These exceptions do not excuse missing code or allow claiming live verification.
