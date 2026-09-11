# Supabase setup

Required for production; not required for isolated acceptance mode.

1. Create a Supabase project in a region near EC2 and choose an appropriate plan/backup policy. Record project URL and database connection details privately.
2. Configure a direct PostgreSQL or session-pooler connection compatible with your EC2 IP stack. Set `SUPABASE_DB_URL`, `SUPABASE_DB_USER`, `SUPABASE_DB_PASSWORD`; require hostname-verifying TLS. Do not use transaction pooling. Confirm the provider CA is trusted by Java/PostgreSQL JDBC.
3. Use a confidential backend database role with migration permissions for the `app` schema. The API runs Flyway migrations. Keep `app` **out of exposed schemas** in Data API settings. Revoke schema/table access for public/anon/authenticated roles; do not offer direct client access to these tables.
4. In Storage create a **private** bucket named by `STORAGE_BUCKET` (default `resumes`). Set 10 MB source-upload policy as applicable; generated objects stay private. The backend uses `SUPABASE_SERVICE_ROLE_KEY`; never put this key in Vite/extension variables.
5. Set `SUPABASE_URL` and `SUPABASE_ANON_KEY` for confidential server auth exchange. Configure asymmetric JWT signing keys (ES256 or RS256) so the backend can validate the project JWKS, issuer and authenticated audience.
6. Enable the chosen social provider and allowlist `https://YOUR_DOMAIN/api/v1/auth/callback`. See [OAuth setup](oauth-setup.md).
7. Run `node scripts/platform.mjs preflight`, then start configured production. Expected: successful Flyway migration, API readiness UP, login creates an owner record, resume upload/download works only for its owner.

Live checks require your project credentials. Automated tests prove migrations/ownership against real isolated PostgreSQL, not your Supabase configuration. After setup explicitly test a second user cannot read another user's version URL. Verify bucket privacy using an unauthenticated storage request (must not return the file).

Supabase backup retention and database/storage quotas depend on your plan. Database backups alone do not back up Storage objects or the application encryption master key; see [backup and recovery](backup-and-recovery.md).
