# Working in job-search-platform

Read `docs/MASTER_IMPLEMENTATION_SPEC.md`, `docs/ACCEPTANCE_CRITERIA.md` and the current `docs/IMPLEMENTATION_STATUS.md` before repository-wide work. The original architecture is preserved under `docs/reference/`. Status reports are evidence pointers, not proof of current behavior.

For a requested completion, audit or repair pass, use `skills/job-search-platform-repair/SKILL.md` and its supporting resources. For a focused change, inspect the affected module and run relevant checks; avoid turning a small task into an unrelated repository-wide rewrite.

- Domain/application rules live in `backend/platform-domain` and `platform-application`. REST, MCP and workers share these use cases; vendor implementations remain in adapters.
- Production is one EC2/Compose host with remote Supabase PostgreSQL/storage and a PostgreSQL outbox. Local database and fake AI/mail belong only to explicit test mode.
- Preserve owner isolation, immutable resume/message versions, fact provenance and approval binding. Changing approved content invalidates approval; ambiguous delivery must not blindly retry.
- Use repository-local Git identity and the required origin. Do not change global/system Git configuration or commit credential state. Preserve unrelated changes and inspect staged diffs before commits.
- `README.md` contains current build/start/test/stop commands. Normal tests use fixtures and non-sending providers. Record actual command outcomes and remaining live-credential limits when updating status.

The portable Skill is the detailed repair workflow; keep project requirements in the source-of-truth documents rather than duplicating them here.
