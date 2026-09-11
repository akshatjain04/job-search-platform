# Testing and acceptance

Use Java 21, Maven, Node 22 and a running Linux Docker engine. Normal suites use deterministic AI, stored connector fixtures and a non-sending mailbox. No API keys or paid provider calls are required. Docker absence is a failure, not permission to silently skip PostgreSQL tests.

## Automated gates

```bash
npm ci
npm test
node scripts/verify-repository.mjs
mvn -f backend/pom.xml clean verify
npm ci --prefix web
npm run verify --prefix web
npm ci --prefix extension
npm run verify --prefix extension
./scripts/bootstrap-and-run.sh --demo
cd web
npx playwright install chromium
npm run e2e
cd ..
node scripts/restart-test.mjs
```

PowerShell uses `mvn.cmd`, `npm.cmd` where execution policy prevents `.ps1` shims, and `./scripts/bootstrap-and-run.ps1 -Demo`. On Windows, set `JAVA_HOME` to your Java 21 JDK in the current process. If no DejaVu font is installed, pass `-Dmyjobai.resume.font=C:/Windows/Fonts/arial.ttf` to Maven; bootstrap detects this automatically. Linux runtime images include DejaVu.

The primary bootstrap command builds every backend deployable, tests migrations in fresh Testcontainers databases, builds/tests both clients, builds images, starts the explicit test overlay, waits for readiness and runs smoke checks. Browser E2E is a separate gate because browser installation is a distinct prerequisite. Never point it at production: it verifies test mode before logging in.

| Suite | Evidence and important boundaries |
|---|---|
| Domain | Ranking/filtering, normalized identities, fact grounding, compatibility gate, legal states, contact rules, immutable approval fingerprint, retry schedule |
| Persistence | Empty PostgreSQL Flyway migrate/validate, schema/indexes/FKs, transaction rollback, dedup/source preservation, concurrent SKIP LOCKED claiming, lease fencing, immutable events, idempotency, usage budget/cache |
| AI | All three actual adapter request/response contracts through stub transports, all model tiers, default/selection/credentials, schema rejection, safe retry/error mapping; deterministic fake |
| Resume | PDF/DOCX text round-trip, byte determinism, corrupt-file rejection; generated visual artifacts under `backend/platform-resume/target/test-artifacts` |
| Storage/mail | Owner-bound immutable objects, Supabase conflict retry, exact Gmail/Graph MIME payloads, provider errors, cancellation and no blind resend |
| API | Real HTTP server and PostgreSQL; session/CSRF, cross-user access, OpenAPI, extension PKCE, grounded resume/approval/test dispatch, analytics and reconciliation |
| MCP | Separate authenticated HTTP deployable; each of the 13 tools, schemas, foreign/missing IDs, absence of arbitrary send |
| Web | Typed API/error/CSRF handling, authentication and exact approval UX; full browser lifecycle against workers |
| Extension | Visible DOM/JSON-LD extraction, bounded normalization and privileged-page rejection |
| Scripts | Selected-provider configuration, inactive-provider optionality, unsafe deployment/fallback rejection |

Reports: each module's `target/surefire-reports`; `web/playwright-report`; `web/test-results`. Inspect reports and exit codes; a successful compile is not a passing test suite. Run `mvn -f backend/pom.xml -Pformat spotless:check` and `npm run format:check` for formatting.

## Manual acceptance

1. Start `--demo`; open `http://localhost:8080`. Enter a unique test account email. Production instead redirects through Supabase OAuth. Expect a private empty workspace.
2. Open **My profile**. Enter identity, work/projects/education, preferences, and skills. Add at least one explicitly reviewed experience fact with company, context, statement, supported skills and provenance. Reload; records persist.
3. Open **Resumes**, upload a text PDF or DOCX. Review extracted text. Transfer accurate facts into the profile and explicitly verify them. Scanned/unreadable files must fail clearly; extraction never silently verifies claims.
4. **Capture an opportunity**: provide an actual source URL, title, company and job text. The opportunity preserves its source and becomes searchable. Re-import the same job from a second source; inspect canonical dedup and both provenance records.
5. Open job detail, calculate match. Inspect matching/missing skills, weighted dimensions, hard-filter result and explanation. No live LLM is necessary for ranking.
6. Select a base resume, **Tailor to this opportunity**, then inspect **Activity** and refresh versions. Download PDF and DOCX, open both and compare text to source facts. The files are single-column; old versions remain downloadable.
7. Inspect internal compatibility and parsing scores and history. The 80/95 recommendation is an internal gate, not an external ATS guarantee. Missing skills must remain gaps, not invented claims.
8. Research contacts for an eligible match (Brave credentials required outside test mode), or add a genuinely source-backed public recruiting contact. Inspect source URL, confidence and verification. Unverified/inferred email cannot enter approval.
9. Generate email, LinkedIn or WhatsApp outreach from job detail. Expect a background task and draft, not an automatic send. Social drafts are copied manually.
10. Open **Outreach**, review recipient, exact subject/body, resume version/hash and downloaded attachment. Prepare approval; both review checkboxes must be checked before the approval button enables.
11. Approve and queue. In demo, Activity reaches COMPLETED and message SENT without network email. Production sends only through the connected mailbox after worker revalidation. Modify an APPROVED draft before queueing: prior approval must become unusable. If a provider outcome is uncertain, use mailbox reconciliation; this action never sends.
12. Save the job in **Applications**; use legal transitions and inspect immutable history. Illegal jumps must fail. Inspect **Insights** for owner-scoped lifecycle/source/resume/outreach/AI usage projections.
13. In **Connections**, issue a short-lived MCP access token. Keep it in the client's secret configuration, not source control. Follow [MCP examples](mcp.md). Unauthenticated calls and foreign IDs must fail.
14. Load `extension/dist` unpacked in Chrome, allowlist its ID in backend configuration, connect via PKCE, then deliberately click Analyze on an HTTP(S) job page. Review capture before submission. Browser-internal pages are unsupported. There must be no background crawler or host-page access before user action.
15. Stop/start without deleting volumes. Re-login with the same test email: profile, applications and immutable files survive. In production, verify remote Supabase persistence independently of the EC2 filesystem.

## Adversarial and live checks

Repeat with two accounts and substitute foreign IDs in every resource family. Test stale approval IDs, changed attachments, expired sessions, CSRF omission, unsupported uploads and private/redirected URLs. Paste hostile instructions into job text; no email, profile change or arbitrary privileged tool should occur.

Live Gemini/OpenAI/Claude, Supabase OAuth/storage, Gmail/Graph OAuth/send, Brave search, unpacked-extension identity flow and EC2/DNS/TLS require their real accounts. Run these deliberately with a controlled mailbox and explicit approval; normal test commands never enable them. Record provider/model/date and outcome without secrets. See `IMPLEMENTATION_STATUS.md` for what was actually exercised.
