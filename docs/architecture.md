# Architecture

MyJobAI is a modular Java 21 / Spring Boot 4.1 application, not a microservice fleet. Five executable roles share application use cases and infrastructure adapters. REST and MCP only translate authenticated requests; all owner IDs originate from backend identity.

| Layer | Modules | Responsibility |
|---|---|---|
| Domain | platform-domain | Candidate facts, opportunities, normalization, ranking, compatibility, lifecycle and immutable approval rules |
| Application | platform-application | Transactional use cases and outbound ports; no provider or persistence SDK imports |
| Infrastructure | persistence, connectors, ai, resume, storage, mail | PostgreSQL, public feeds/research, vendor HTTP, parsing/rendering, objects and mailbox transport |
| Composition | platform-runtime | Configuration, OAuth, Spring Security, worker scheduling, health/logging |
| Adapters | job-platform-api, job-platform-mcp | REST/OpenAPI and stateless Streamable HTTP MCP |
| Workers | job-ingestion-worker, ai-worker, communication-worker | Claim specific PostgreSQL event types and invoke shared services |
| Clients | web, extension | Full dashboard and user-triggered contextual capture |

Production: one EC2 host, Docker Compose, Nginx HTTPS; remote Supabase PostgreSQL/Auth/private Storage. `docker-compose.test.yml` is an explicit isolated acceptance-only alternative. See the original [architecture](reference/ai_job_search_platform_architecture_cost_optimized.docx), [master specification](MASTER_IMPLEMENTATION_SPEC.md) and [decisions](architecture-decisions.md).

## Durable asynchronous work

State mutation and outbox insertion share a PostgreSQL transaction. Workers claim with `FOR UPDATE SKIP LOCKED`, random claim fencing tokens, a ten-minute lease and bounded backoff. Leases recover crashed workers. Deterministic result IDs and immutable storage make generation retries safe. Failed/terminal jobs remain visible. No Redis/SQS dependency.

## Boundaries

External content is data, never an action instruction. Discovery and matching do not automatically send mail. AI only selects verified source fact IDs; renderers compose exact claims. Recruiter contact values come from actual public pages or explicitly reviewed user evidence, not a model. Mail approval binds version/recipient/resume/bytes and is checked again at dispatch.

All V1 search is PostgreSQL relational/full-text search with indexed normalized fields. No RAG, embeddings, dedicated search service or vector database is required. Five small connection pools default to three connections each; account for other Supabase consumers before increasing them.
