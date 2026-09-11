# AI provider setup

Only one active provider is required. The default is Gemini. Do not paste keys into chat, Git, browser storage or frontend build variables. Put them in a protected server `.env` or deployment secret injection.

| Provider | Selector | Required key | Required model variables |
|---|---|---|---|
| Google Gemini | `LLM_PROVIDER=gemini` | `GEMINI_API_KEY` | `GEMINI_MODEL_CHEAP`, `GEMINI_MODEL_MEDIUM`, `GEMINI_MODEL_HIGH` |
| OpenAI API | `LLM_PROVIDER=openai` | `OPENAI_API_KEY` | `OPENAI_MODEL_CHEAP`, `OPENAI_MODEL_MEDIUM`, `OPENAI_MODEL_HIGH` |
| Anthropic | `LLM_PROVIDER=claude` | `ANTHROPIC_API_KEY` | `CLAUDE_MODEL_CHEAP`, `CLAUDE_MODEL_MEDIUM`, `CLAUDE_MODEL_HIGH` |

Create the selected provider account/project, enable API billing/access as appropriate, create a restricted API key, and select currently supported structured-output model IDs available to that account. IDs are intentionally not frozen in source: configure cheap/medium/high tiers using the provider's current catalog and budget. A ChatGPT or Claude consumer subscription is not a substitute for API billing/access.

Adapters use Gemini `generateContent` with JSON schema, OpenAI Responses with strict `text.format` and `store=false`, and Anthropic Messages with `output_config.format`. References: [Gemini structured output](https://ai.google.dev/gemini-api/docs/structured-output), [OpenAI structured output](https://developers.openai.com/api/docs/guides/structured-outputs), [Claude structured output](https://platform.claude.com/docs/en/build-with-claude/structured-outputs).

Validation: `node scripts/platform.mjs preflight` must identify only missing **active** provider fields. Unknown providers and nonempty `LLM_FALLBACK_PROVIDERS` fail fast. Inactive keys can remain empty. `LLM_TIMEOUT_SECONDS`, `LLM_MAX_ATTEMPTS`, `LLM_MAX_OUTPUT_TOKENS` and `LLM_DAILY_REQUEST_LIMIT` bound work. Optional per-provider `INPUT_COST_PER_MILLION` / `OUTPUT_COST_PER_MILLION` produce estimates; use units and current pricing appropriate to configured models.

Automated verification: `mvn -f backend/pom.xml -pl platform-ai -am test`. Tests cover all three strategies and tiers, default/explicit selection, missing active credentials, optional inactive credentials, malformed schemas/outputs, invented action properties, HTTP auth and transient failures. No normal test incurs charges.

Live verification is separate: after consent to incur provider charges, configure an active provider, generate one grounded resume/outreach task, inspect safe usage metadata and provider billing. Repeat by switching environment and restarting for the other providers. Never treat fake-provider acceptance as proof of live account/model access.
