# Recruiter intelligence

Public research is separate from job discovery and runs only on explicit request for an eligible match (minimum score 40). `Ports.WebResearch` abstracts search; Brave Web Search is the production search adapter. `PublicRecruiterResearch` fetches a bounded set of public result pages through the SSRF-safe client and extracts actual mailto/profile links. Previously stored public contacts avoid repeating work.

Configure optional `BRAVE_SEARCH_API_KEY` from a Brave Search API account. Without it, the research task returns an actionable configuration failure; manual source-backed contacts still work. Contract tests use fixtures, not live pages. See [Brave API](https://api-dashboard.search.brave.com/api-reference/web/search/get).

Every contact records value/type/source URL/source type/confidence/verification/discovery time/method. Publicly published email is PUBLIC; a user-confirmed exact source value is USER_VERIFIED; INFERRED/UNVERIFIED contacts cannot pass email approval. A publication label is not proof of a person's identity; inspect provenance. LinkedIn URLs must be actual discovered profile URLs. Phone enrichment and guessed corporate email generation are absent by design.

Only public HTTP(S) pages are fetched, with bounded response size, timeouts, no redirects and DNS/address validation at connection time. No continuous LinkedIn/Indeed/Naukri scraping, credentialed portal automation or fabricated contact values. Licensed enrichment can later implement the research port, but V1 introduces no paid enrichment dependency.
