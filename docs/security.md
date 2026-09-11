# Security review

Uncertain mailbox outcomes can be reconciled explicitly in Outreach with user-provided mailbox evidence. Reconciliation never queues or sends. A confirmed non-delivery invalidates the old approval; any later attempt requires a newly reviewed draft and approval.

Review scope: API/MCP identity, cross-user access, sessions/CSRF/CORS, file and URL ingestion, source grounding, immutable email approval, worker restart/retry behavior, credentials, extension permissions, Compose exposure and logs. Evidence belongs in [implementation status](IMPLEMENTATION_STATUS.md); this is not a penetration-test certification.

| Boundary | Implemented control | Verification / residual |
|---|---|---|
| Authentication | Supabase asymmetric JWT issuer/audience/expiry; opaque server BFF session; encrypted refresh | API test auth is explicitly local-only; live social login needs registered provider |
| IDOR | Authenticated owner ID in every service/repository read/write; composite owner foreign keys | API/MCP foreign-ID tests; no direct Supabase Data API access to app schema |
| CSRF/CORS | Stored random CSRF token on unsafe cookie requests; fixed allowed dashboard/extension origins | API 403 test; bearer clients do not depend on cookies |
| XSS | React text rendering; no captured HTML insertion; strict Nginx CSP; no inline script/CDN dependency | Malicious capture E2E; third-party pages remain untrusted |
| SQL injection | Parameterized queries; fixed server-owned column/sort choices | Search/validation and owner tests |
| SSRF | Public-host URLs, DNS validation at actual connection, private/reserved ranges rejected, no redirects, bounded response/time | Connector fixture/address tests; administrators must still protect provider endpoint configuration |
| Uploads | Actual PDF/DOCX parsing, max sizes/pages/ZIP expansion, no macro execution, UUID object keys | Corrupt/spoof/round-trip tests; no antivirus or OCR; native parser vulnerabilities remain patching responsibility |
| Prompt injection | Source normalization, trusted/untrusted separation, schema outputs and fact-ID allowlist; no action executor | Malicious page cannot queue mail/change profile; exact grounding tests |
| Contact safety | Provenance and verification; inferred/unverified email prohibited | Domain/approval tests; publication is not independent identity verification |
| Email | Immutable version+recipient+resume+bytes approval; exact preview; worker revalidation; no arbitrary sender API/MCP | Edit invalidation/idempotency tests; ambiguous delivery requires manual reconciliation |
| Secrets | AES-256-GCM with random nonce/context; server-only keys/tokens; redacted sensitive records; ignored config | No key recovery without backed-up master; host/admin compromise remains privileged |
| Logs | JSON logs, safe errors, sanitized stacks, Nginx logs URI without OAuth query strings | Never enable verbose request-body/header logging in production |
| Abuse | Per-user API limit, Nginx ingress limits, output/attempt/timeout caps, per-user AI budget/cache | Single-host in-memory API limiting is not distributed; no Redis required for V1 |
| Containers | Non-root, read-only root filesystem, dropped capabilities, no-new-privileges, memory/PID limits, private worker ports | Docker daemon/EC2 administrator is privileged; patch images/OS regularly |

## Operational requirements

Keep Supabase `app` schema private and Storage bucket nonpublic. Restrict EC2 SSH ingress to administrators; expose only 80/443. Use real hostname-valid TLS. Protect `.env`, TLS private keys, GitHub state and backup encryption keys; no secrets in images, frontend bundles or commits. Never mount the Docker socket into production application containers.

Review source/public-contact legality and provider terms before enabling a connector. User-triggered capture is not permission for bulk scraping or automated social messaging. Respect recipients, provider quotas and applicable privacy/outreach rules; the product does not provide legal advice.

Residuals: no antivirus sandbox, no formal content-verification oracle, no automatic mailbox-delivery reconciliation, no distributed rate limiter, no multi-instance HA, manual external-account setup, explicit operator-managed encryption-key rotation and data-retention/deletion procedures. Do not describe these as implemented capabilities.
