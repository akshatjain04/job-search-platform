# Measured scaling triggers

| Change | Trigger | Migration boundary |
|---|---|---|
| Split EC2 roles | Sustained CPU/memory pressure, queue lag, availability/failure-isolation needs | Existing executable roles; preserve shared services/contracts |
| Supabase upgrade/migration | Connection/storage/throughput limits, backup retention or SLA requirements | JDBC/Flyway and private object abstraction |
| S3 | Storage lifecycle/scale or AWS consolidation justifies it | Implement ObjectStorage adapter; preserve owner/version keys and hashes |
| SQS + DLQ | Worker concurrency/throughput/retry isolation outgrows DB polling | Keep transactional outbox and immutable approval in PostgreSQL; add relay/consumer |
| Redis | Measured distributed rate-limit/cache/ephemeral coordination need | Replace specific coordination port; not the source of truth |
| OpenSearch | Millions of jobs or sophisticated facets/autocomplete exceed indexed PostgreSQL | Search adapter and source-of-truth reindex pipeline |
| pgvector | Proven ranking quality improvement from semantic similarity | Optional match/dedup stage after cheap filters |
| RAG | Candidate/private context cannot fit normal prompts or retrieval is itself valuable | Retrieval port; retain fact-grounding provenance |
| Kubernetes/Kafka | Operating scale/organizational requirements demonstrably justify them | Deliberate architecture decision, not default V1 additions |

Before changing infrastructure, record baseline traffic, p95 latency, CPU/RSS, DB connections/query time, backlog age, failure rate and cost. Prefer improving queries/limits and tuning current roles before adding another service.
