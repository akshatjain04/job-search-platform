# Troubleshooting

| Symptom | Check and corrective action |
|---|---|
| Bootstrap refuses Git root/origin/email | Complete [repository-only Git setup](git-bootstrap.md); do not disable the guard or change global config |
| Missing `.env` or selected provider values | Copy `.env.example`; configure the selected integration only. For credential-free testing use `--demo` |
| Java 17 despite Java 21 installed | Set process `JAVA_HOME` and PATH to the Java 21 JDK; run `java -version` and `mvn -version` |
| Docker access denied / no engine | Start Docker Desktop Linux engine; authorize the task's Docker execution. Tests must not silently skip database verification |
| Compose rejects `!override` | Upgrade Compose to 2.24.4+; the tag prevents accidentally exposing extra production ports |
| Port 8080 already occupied | Stop the specific conflicting local service or intentionally change the test web binding and test base URL; do not terminate unrelated processes blindly |
| API cannot connect to Supabase | Verify database hostname/port/user/password, IPv4/session-pooler compatibility and TLS `sslmode=verify-full`; do not disable certificate verification |
| Flyway checksum mismatch | Restore the published migration file and create a new migration for changes; do not run repair to conceal drift |
| PDF parsing returns no text | Scanned/image-only resumes require a text PDF/DOCX; V1 does not silently OCR or infer facts |
| Resume task fails grounding | Review verified facts, company/context, supported skills and provenance. Arbitrary LLM paraphrases cannot pass V1's exact-fact validator |
| Low compatibility score | Missing requirements are genuine gaps. Add only real, reviewed experience; the repair loop cannot manufacture skills |
| AI configuration or budget failure | Inspect Activity safe error code; check selected model IDs/key and daily request cap. Failed uncached calls count toward the cap |
| AI 401/403 | Repair credentials/model access. Authentication failures are not retried automatically |
| Draft cannot request approval | Verify public/verified EMAIL recipient, subject and exact job-specific resume attachment; social channels cannot send via email |
| Approval is stale | Reload exact preview and explicitly reapprove the new immutable message version |
| DELIVERY_UNKNOWN | Inspect provider Sent folder/logs and reconcile in Outreach. Never repeatedly click send or edit PostgreSQL to force retry |
| Email FAILED | Inspect safe Activity code, reconnect mailbox if required, then edit/create a new draft and approve again; old failed jobs cannot bypass approval |
| Extension cannot connect | Allowlist exact extension ID; backend redirect must match `https://ID.chromiumapp.org/`; check platform optional permission and reconnect expired access token |
| MCP 401 | Refresh short-lived access token or supported Supabase access token; no arbitrary token embedded in URL |
| TLS startup failure | Verify `TLS_DIRECTORY/fullchain.pem` and `privkey.pem`, renewal, file readability by Nginx UID 101, DNS and intended ports |
| HTTP 429 | Respect rate limits; do not retry in a tight loop. Only authentication-entry routes use the tighter Nginx login limit |

Use `node scripts/platform.mjs logs --demo` for the local test stack, or the same production environment/Compose files used to start it. Logs omit query strings, credentials and provider bodies; preserve request/job IDs in support reports. Redact any user-entered evidence before sharing logs or database exports.
