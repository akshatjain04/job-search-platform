# Cost posture

Recurring costs are one EC2 instance, EBS/egress/IP charges as applicable, Supabase database/storage/backup plan, selected LLM token usage, optional Brave search and domain/TLS operations. Provider mailbox quotas and account subscriptions may apply. This repository does not create paid infrastructure automatically or claim a fixed monthly price.

Actual controls: deterministic normalization/dedup/ranking before AI; public feed connectors rather than portal crawling; verified-fact selection rather than large RAG prompts; provider-specific model tiers; bounded output/time/retry settings; per-user daily AI request budget; content/routing/user-based PostgreSQL cache; per-user advisory lock to avoid duplicate cache misses; separate committed usage accounting; explicit recruiter research only above an eligible match threshold; no paid phone enrichment or fallback-provider surprise.

The budget counts logical uncached provider requests; bounded transport retries can produce additional vendor requests and unreported charges. Usage estimates use configured prices and returned token metadata, may omit failed-attempt cost or modality-specific charges, and must be reconciled with provider billing. Set provider-side hard budgets/alerts as an independent safeguard.

No required Redis, Kafka, Kubernetes, OpenSearch, vector database, SQS, Step Functions, LocalStack or RAG. All production persistence is remote Supabase; the local PostgreSQL container exists only in the explicit test overlay. A 4 GB-class instance can be a starting sizing hypothesis for five JVMs; measure actual memory/CPU/latency before committing to an instance plan.
