# Authentication setup

Production login uses Supabase Auth with OAuth social login and S256 PKCE. The backend generates verifier/state, encrypts transient verifier data in PostgreSQL, exchanges the returned auth code, validates asymmetric JWT signature/issuer/audience/expiry, and creates an opaque HttpOnly Secure SameSite=Lax cookie. Supabase refresh tokens are encrypted server-side, never stored in React or the extension.

Register a web OAuth app with the social provider, configure its credentials in Supabase, and follow Supabase's displayed provider callback URI. Allowlist the platform callback `https://YOUR_DOMAIN/api/v1/auth/callback` in Supabase redirect URLs. Configure `AUTH_SOCIAL_PROVIDER=google` or another enabled compatible Supabase provider. Set `APP_PUBLIC_URL` exactly, without a path. The backend uses project JWKS; migrate legacy HS256 signing to an asymmetric key before startup.

Mailbox authorization is separate consent: signing in with Google does not grant Gmail sending. See [email provider setup](email-provider-setup.md). Gmail/Outlook refresh tokens also remain encrypted server-side.

Browser API writes require the stored session's CSRF value in `X-CSRF-Token`. CORS is restricted to the dashboard origin and configured extension IDs. Frontend state cannot establish ownership or approve a changed message. Session expiry/refresh failure returns 401; sign in again. Logout deletes the session and all issued short-lived platform tokens for that user.

Extension authorization requires login in the same Chrome profile, a registered `EXTENSION_IDS` value, an exact `chromiumapp.org` redirect, S256 challenge, random state and one-use code. It receives a one-hour opaque access token, no refresh token. MCP similarly uses a one-hour token generated deliberately in Connections.

Verify: normal login/logout; cookie has HttpOnly/Secure/SameSite; no refresh token in browser storage; missing CSRF gives 403; missing bearer gives 401; foreign IDs give 404. Automated API tests exercise CSRF/ownership/PKCE replay using explicit local test authentication. Social-provider login itself requires live registration.
