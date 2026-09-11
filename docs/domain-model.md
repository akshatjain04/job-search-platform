# Domain model

`Candidate.Profile` is canonical: identity, employment, projects, education, skills, certifications, achievements and preferences. A resume upload is separate evidence. `Candidate.Fact` records a claim, employer/context, skills, metrics, dates, provenance and explicit verification. Unverified facts never contribute generated claims or verified-skill matching.

`Opportunity.Job` is a user-owned canonical opportunity with kind JOB_POSTING, HIRING_POST or REFERRAL_POST. `Opportunity.Source` preserves each observed connector/external ID/URL/content hash/kind. Ranking uses the strongest available source kind without discarding career-page provenance. `Match` stores dimensions, relevant evidence, missing skills and explanations.

`Resume.Base` stores extraction evidence. `Resume.Version` stores exact structured content, immutable object paths/hashes and the complete bounded scoring/repair history. `Contact` stores actual value, type, source, confidence, verification, observation time and method. Recruiter records and job links are normalized.

`Outreach.Message` is a mutable workflow pointer to immutable `Version` records. An `Approval` binds message version, recipient, resume version and fingerprint. An edit creates a new version and invalidates prior approvals; it never overwrites approved evidence.

Applications have legal transitions in `ApplicationLifecycle`; every transition appends an immutable event. Common path: DISCOVERED → SAVED → PREPARING → READY_TO_APPLY → APPLIED → INTERVIEW → OFFER. Outreach branches through PREPARING → OUTREACH_PREPARED → OUTREACH_SENT → REPLIED. Invalid jumps return 409. Rejected/withdrawn records are terminal.

Analytics distinguish current state from states ever reached in history. Multiple sources may be associated with the same outcome; counts are not causal attribution. Domain records are independent of database entities and vendor DTOs.
