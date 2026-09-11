# Database and migrations

Production uses a remote Supabase PostgreSQL direct or **session** connection. Do not use transaction pooling for long-lived advisory locks/transactions. Require `sslmode=verify-full`; install/configure the appropriate CA if your runtime cannot verify the server certificate. Never weaken SSL to fix a certificate error.

Flyway creates private schema `app` from an empty database. API startup migrates; the other Compose roles wait for API health and disable migration. Do not expose `app` through Supabase PostgREST. Revoke anonymous/authenticated direct schema access and use a dedicated confidential backend role. Ownership checks are performed on every backend operation and reinforced with composite `(id,user_id)` foreign keys.

Schema groups: users/profile/facts; base/versioned resumes; jobs/sources/requirements/matches; recruiters/contacts/job links; applications/events; messages/versions/approvals; connectors/runs; outbox/audit; encrypted mailbox and BFF sessions; OAuth state/extension codes/access credentials; AI cache/usage.

Indexes cover normalized jobs/full text, user matches, source identities, application history, approvals, due jobs, user audits and session expiry. Immutable triggers protect resume versions, outreach versions, application/audit events and approval bindings. Only `invalidated_at` may invalidate an approval; bindings cannot be rewritten.

Never edit an applied migration. Add the next `V<N>__description.sql`. Tests migrate a fresh PostgreSQL 16 container and validate checksums, indexes, constraints, concurrent claims, rollback and owner isolation:

```bash
mvn -f backend/pom.xml -pl platform-persistence -am test
```

Use a Java 21 JDK and running Docker. Production migrations are forward-only; take a verified backup before deployment and use expand/contract changes when compatibility matters. Do not run Flyway clean against production.
