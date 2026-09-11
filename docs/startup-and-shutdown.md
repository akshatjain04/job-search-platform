# Startup and shutdown

Primary entry points: `./scripts/bootstrap-and-run.sh` or `.\scripts\bootstrap-and-run.ps1`. Add `--demo` / `-Demo` for explicitly isolated acceptance. Production defaults are fail-closed and require configured remote Supabase, active provider and HTTPS.

Bootstrap order: verify repository root/origin/local Git identity → parse selected configuration without printing secrets → check OS/architecture/JDK/Maven/Node/Docker/Compose → backend clean build and fresh-DB tests → web/extension tests and builds → image builds → Compose health wait → public smoke checks → URLs and next steps.

Manual prerequisites are checked early. For missing `.env`, create it from `.env.example`, provision the accounts in the relevant setup guides, and rerun. `--skip-tests` / `-SkipTests` explicitly skips the test gate; this does not constitute final acceptance.

```bash
node scripts/platform.mjs start --demo
node scripts/platform.mjs stop --demo
node scripts/platform.mjs smoke --demo
node scripts/platform.mjs logs --demo
```

Stop uses `docker compose stop`, **not** volume deletion. PostgreSQL and objects survive local restart. Production persistence is remote. A worker can recover a lease after restart; email ambiguity is treated separately and never blindly retried. Resume workers may leave an immutable orphan object if a DB transaction fails after upload; retain it for diagnosis and remove only through an explicit owner-scoped maintenance procedure.

On startup failure, inspect `docker compose ps` and `logs --tail 100` with the same `--env-file` and Compose overlays. Never paste full configuration, access tokens or OAuth callback URLs into tickets. Container health may take 1–3 minutes on small hosts; investigate OOM/restart loops instead of repeatedly recreating services.
