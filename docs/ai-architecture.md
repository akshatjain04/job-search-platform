# AI architecture

Application use cases depend on `Ports.Intelligence` / `Ports.LlmProvider`. The startup registry selects exactly one of `GeminiLlmProvider`, `OpenAiLlmProvider`, `ClaudeLlmProvider`. Default key: `gemini`. Domain/application modules import no vendor SDK/DTO. Provider HTTP translation stays in `platform-ai`.

The active provider has configurable CHEAP/MEDIUM/HIGH identifiers. V1 resume evidence selection uses HIGH; outreach evidence selection uses MEDIUM. Deterministic normalization/classification/matching avoid a billable call entirely. CHEAP routing is implemented and contract-tested for extraction/classification extensions. Adding a strategy requires a registered provider factory, not changes to application logic. Automatic fallback is deliberately unsupported/disabled; a nonempty fallback list fails configuration.

All strategies separate trusted instructions from `untrusted_data`, request constrained JSON, reject incomplete/truncated results, and locally validate the same schema. The supported schema vocabulary is intentionally explicit; unsupported schema keywords fail rather than silently weakening validation. Unknown fact IDs and changed claims are rejected outside the model.

V1 grounding is conservative: the model selects and orders verified fact IDs. Exact source claims, employer/context and confirmed skills are rendered deterministically. It does **not** offer unrestricted semantic claim rewriting, invented metrics, invented contacts or provider-controlled actions. Users can improve a fact's wording through a new reviewed source record. This is an intentional safe alternative to unverifiable automated paraphrase, not an external ATS guarantee.

Transport: bounded configured timeout, cancellation, exponential backoff for rate limits/transient provider failures, no auth/validation retries. Usage includes provider/model/task/tokens/latency and optional configured cost estimates. No raw prompts, keys or authorization headers are logged. PostgreSQL cache keys include user, routing and request content; daily request budgets serialize per user. Accounting commits independently so downstream rendering/grounding rollback cannot erase billed usage. Failed attempts may have unreported token cost; reconcile with vendor billing.

Normal tests use deterministic output/HTTP fixtures and never call a paid provider. Real strategies are implemented but live compatibility depends on enabled account/model access and credentials. See [provider setup](ai-provider-setup.md).
