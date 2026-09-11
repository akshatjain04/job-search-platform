# MCP

One stateless Streamable HTTP endpoint: `POST https://YOUR_DOMAIN/mcp`. Protocol baseline `2025-06-18`; initialize, ping, tools/list and tools/call. Responses are JSON; notifications return 202. There is no long-lived SSE subscription or portal-specific MCP server.

Tools: `jobs.search`, `jobs.get`, `jobs.analyze`, `resume.tailor`, `resume.evaluate`, `resume.render`, `recruiter.find`, `outreach.generate_email`, `outreach.generate_linkedin`, `outreach.generate_whatsapp`, `applications.create`, `applications.update`, `applications.list`.

Generate an opaque one-hour token in dashboard Connections. Configure a compatible client with:

```json
{"mcpServers":{"myjobai":{"type":"http","url":"https://YOUR_DOMAIN/mcp","headers":{"Authorization":"Bearer <SHORT_LIVED_PLATFORM_TOKEN>"}}}}
```

Client configuration syntax varies; use its Streamable HTTP/server-header settings. Keep the real token in the client's protected local configuration, never repository files. Refresh by signing in and issuing a new token; logout revokes platform-issued tokens. Mailbox/Supabase refresh tokens never reach MCP clients.

Example request body:

```json
{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"jobs.search","arguments":{"role":"Java"}}}
```

Get exact schemas through tools/list. Mutation idempotency keys are explicit. `resume.render` returns authenticated paths for a previously generated immutable version; `resume.tailor` performs actual queued rendering. `resume.evaluate` returns recorded internal score history. This adapter delegates to the same application services as REST.

There are **no email approval or send tools**. An MCP client can prepare a draft, but the user must inspect and approve it in the dashboard. Authorization is required for every request; foreign IDs are reported as not found. Tool schema violations produce controlled JSON-RPC errors, application failures produce `isError: true`.

`McpAcceptanceTest` exercises all 13 tools with a real isolated PostgreSQL database, checks missing/foreign IDs, invalid schema and unauthenticated requests, and verifies that arbitrary sending is unavailable.
