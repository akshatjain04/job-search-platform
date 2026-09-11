# Operations

Readiness: every Java role exposes internal `/actuator/health/readiness`; Nginx exposes `/api-health` and `/healthz`. Compose waits for health. Only intended Nginx ports are public. Structured logs include request/user IDs and worker job IDs; Nginx logs paths without query strings to avoid OAuth-code leakage. Error stacks are sanitized; never enable raw HTTP authorization/body logging.

Inspect dashboard Activity for event type, state, attempts and safe last error. Connector runs and audit events are persisted. Insights shows recorded lifecycle/source/resume associations, outreach counts and AI usage estimates. Monitor disk/log growth, JVM OOM/restarts, DB connection counts, outbox oldest available age and terminal failures. Use Docker/EC2 host metrics initially; a paid monitoring stack is not required.

Outbox defaults: two-second polling, ten-minute lease, maximum five attempts, bounded exponential delay. A late worker cannot complete another claim because each claim has a fencing token. Generation uses deterministic result IDs; unchanged AI requests can use PostgreSQL cache. Email 429 retry exhaustion becomes FAILED; ambiguous send becomes DELIVERY_UNKNOWN. Do not manually override immutable approval bindings.

Secrets: provider/API keys rotate by replacing protected configuration and recreating affected containers. Mailbox reconnect refreshes stored OAuth grants. Encryption-key rotation requires an explicit migration that decrypts with the old key and reencrypts with the new key; **simply replacing the key breaks existing sessions/mailboxes**. V1 does not implement an automatic rotation utility. Plan future KMS/Secrets Manager envelopes at the cipher/composition boundary.

Retention: define privacy/data-retention requirements before broad production use. Immutable evidence/history protects approvals but means deletion needs a deliberate, audited administrative erasure workflow, not arbitrary cascading deletes. Orphan objects and expired auth state can be reviewed through scoped maintenance. No automatic destructive cleanup of business history is performed.

Scaling instrumentation can later use OpenTelemetry/CloudWatch without changing application ports. Add traces/metrics when justified, redact PII, and measure before adding Redis/search/queues. See [scaling triggers](scale-up-triggers.md).
