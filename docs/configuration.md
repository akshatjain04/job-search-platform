# Configuration reference

Copy `.env.example` to `.env`, protect permissions, edit locally. Do not commit it. The scripts parse `KEY=value` as data, not executable shell. Avoid multiline values, shell substitutions and inline comments. The example groups all settings; the server reads environment variables directly.

Required production groups:

- APP/AUTH: HTTPS `APP_PUBLIC_URL`, `APP_MODE=production`, Supabase social provider, optional `EXTENSION_IDS` (32-character Chrome IDs).
- DATABASE: remote `SUPABASE_DB_URL` with `sslmode=verify-full`, user/password; default pool size 3 per JVM.
- SUPABASE/STORAGE: HTTPS project URL, anon/publishable-compatible auth key, confidential service-role key, private `STORAGE_BUCKET`.
- ENCRYPTION: base64 32-byte random `ENCRYPTION_MASTER_KEY`, backed up separately.
- LLM: active selector (default Gemini), its key and three model IDs. Other providers optional; fallback empty.

Optional groups: Brave search key; complete Gmail client ID/secret pair; complete Microsoft client ID/secret pair + tenant; ranking weights; request budget, retries, output cap; EC2 SSH metadata; TLS/ACME directories. No AWS access key is needed inside application containers. SSH deployment uses your existing secure SSH agent/key.

Generate an encryption key locally and put the result directly in protected configuration (not chat/logs): `openssl rand -base64 32`. On Windows, use a cryptographic random-number generator in a trusted local secret-management workflow. Never use the all-zero test key for production.

`node scripts/platform.mjs preflight` checks repository-local Git, tools and selected integrations **before** building. Missing values are reported by variable name only. Production requires provisioned TLS files for startup; Nginx must read `fullchain.pem` and `privkey.pem` in `TLS_DIRECTORY`. Do not use self-signed certificates for public deployment.

`--demo` explicitly selects `.env.test.example` and the test Compose overlay. It binds public access to localhost and activates non-sending deterministic providers. The base/production Compose files force production mode and do not include local PostgreSQL. Never combine test and production overlays.

Integration configuration is restart-driven. `LLM_PROVIDER` does not change halfway through an approved workflow; generated immutable versions remain unchanged. Rotate provider secrets by updating protected configuration and recreating the relevant containers. See [operations](operations.md) for encryption-key rotation and retention.
