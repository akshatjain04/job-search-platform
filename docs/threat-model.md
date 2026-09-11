# Threat model

Assets: candidate PII/resumes, verified employment facts, mailbox refresh/access tokens, provider keys, approval evidence, user application history and the ability to contact an external recipient.

Actors: authenticated owner, another tenant, malicious page author, compromised external provider response, unauthenticated attacker, privileged operator. Trust boundaries: browser/extension → API; MCP client → adapter; application → PostgreSQL/private objects; connector → public network; AI output → validated structured data; immutable approval → external mailbox.

| Attack | Expected result |
|---|---|
| Guess another user's job/resume/approval ID | 404/no data; owner-scoped SQL and composite FK reject writes |
| Forge page text instructing email/profile actions | Stored as source text only; no privileged tool executor exists |
| Model returns invented fact ID or extra send command | Schema/allowlist rejection, task fails without action |
| Edit body/recipient/attachment after approval | New immutable version; approval invalidated; old queue/worker dispatch rejected |
| Worker crashes around mailbox send | Durable SENDING intent; lease recovery marks delivery uncertain, no blind resend |
| URL points to EC2 metadata/private address or DNS rebind | Address validation at connection denies fetch; redirects not followed |
| Zip bomb/spoofed resume/path traversal | Size/expansion/type validation and owner-key validation reject |
| Cross-site cookie mutation | CSRF mismatch and restricted CORS; backend never trusts UI authorization |
| Steal database backup | Tokens remain encrypted without separate master key; candidate PII still requires backup encryption/access control |
| Compromise EC2/docker administrator | Can access runtime secrets; mitigate through IAM/SSH/patching and future KMS, not application claims |

Test the negative paths after every security-relevant change. Avoid fixing tests by weakening ownership, CSRF, schema validation or approval checks. A provider outage is not permission to swap providers automatically or send through another mailbox.
