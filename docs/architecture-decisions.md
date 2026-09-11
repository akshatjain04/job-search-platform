# Architecture decisions

1. Preserve the supplied cost-optimized V1 topology. No optional scaling infrastructure is introduced. Production PostgreSQL and storage remain outside EC2.
2. Use a backend-for-frontend session for browser OAuth: PKCE verifier and encrypted refresh tokens stay server-side, with HttpOnly session cookies and CSRF protection. MCP and extension receive bounded access credentials only. This reconciles browser OAuth with the explicit prohibition on exposing refresh tokens to frontend code.
3. Email APIs do not promise transactional exactly-once delivery with our database. A timeout after transmission can mean a delivered email. Mark ambiguous sends for reconciliation instead of retrying blindly; disclose this tradeoff and test it. Database idempotency prevents duplicate queue requests but cannot create an external provider guarantee.
4. Grounding is enforced in deterministic application code. A model's source-ID citation is necessary but not sufficient proof that rewritten claims are true. Unsupported text is rejected or replaced with exact verified source facts; scoring never invents facts to reach a threshold.
5. All services share logical modules. Separate executables select responsibilities; deployment remains one Compose host. Future S3/SQS/search/cache changes replace adapters.
