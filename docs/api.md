# REST API

Prefix `/api/v1`. Live, authenticated OpenAPI is generated from actual Spring controllers at `/v3/api-docs`. Import it into your API client after login or with a short-lived bearer token. Backend DTOs are domain/application records, not JPA entities.

| Resources | Operations |
|---|---|
| `/auth` | config, login/callback, session, logout, access-token, extension PKCE exchange |
| `/profile`, `/profile/facts` | Read/update profile; add/read verified evidence |
| `/jobs`, `/jobs/{id}` | Owner-scoped search/detail; sources, match, analyze, recruiters/research |
| `/jobs/import`, `/page-captures` | Structured import and explicitly captured page data |
| `/resumes` | Multipart PDF/DOCX upload and base evidence listing |
| `/resumes/tailor`, `/resumes/versions` | Enqueue generation; immutable versions and PDF/DOCX downloads |
| `/outreach` | Generate/list/preview/revise/version history; request-approval, approve, reject |
| `/approvals/{id}/queue` | Queue only an existing valid immutable approval |
| `/applications` | Create/list; PATCH legal transition; `/events` history |
| `/connectors` | Configure/list sources; `/{id}/run` |
| `/tasks` | Async state/attempt/error visibility |
| `/mailboxes` | Connection status, provider connect/callback, disconnect |
| `/analytics`, `/audit-events` | Owner-scoped read projections |

Cookie requests use opaque HttpOnly SameSite=Lax sessions. All unsafe cookie-authenticated requests require `X-CSRF-Token` from `/auth/session`. Bearer requests use `Authorization: Bearer …` and do not rely on cookies. The server ignores client-supplied ownership and derives it from authentication.

Use a stable `Idempotency-Key` for retrying the same async mutation (tailor, outreach generation, research, connector run). A reused key with a different payload returns 409. Job ingestion uses canonical source identity/deduplication; application creation is unique per user/job. Other edits are not globally idempotent.

Search supports role/full-text, company, location, minimum salary, maximum experience, remote, since, source, referral, recruiter availability, minimum match, page and size (1–100). The response is `{items,page,size,total}`. Some small owner-scoped history/list endpoints are bounded by product volume rather than generic pagination.

Errors use `{code,message,requestId}` where application handling applies. Typical status: 400 validation, 401 authentication, 403 CSRF/access, 404 missing or foreign resource, 409 stale approval/conflicting transition, 422 integration/configuration, 429 rate limit, 503 retryable integration. Unknown implementation failures return a safe 500 and sanitized stack trace. A public health response never reveals database credentials or detailed dependencies.
