# Backup and recovery

Production state is PostgreSQL + private Storage objects + the **separately backed-up encryption master key**. EC2 should be replaceable. Docker images/Git revisions are reproducible code artifacts, not a data backup.

Configure Supabase backups/PITR according to the chosen plan and your recovery objectives. Verify retention and restore ability; do not assume a free plan includes required recovery guarantees. Periodically create an encrypted PostgreSQL logical backup using provider-supported tools. Keep connection passwords in protected environment/pgpass, not command arguments or shell history. Database backup alone does not include object-storage contents.

Maintain an object inventory and encrypted copy/export of private resume objects with their version keys/hashes. Back up the encryption key through an independent secret-management channel with access controls and recovery testing. Loss of the key makes stored OAuth tokens unusable; users can reconnect mailboxes, but old token ciphertext cannot be recovered. Never store the key inside the same unprotected database backup.

Restore rehearsal: provision an isolated database/storage project → restore DB and objects → supply the matching key → deploy the recorded compatible image revision → migrate only after checking compatibility → verify health and owner-scoped sample downloads/hashes → keep mail sending disabled/disconnected during validation. Do not replay historical SEND_EMAIL events into a live mailbox.

Worker recovery: normal jobs use leases/fencing/idempotent result IDs. In-flight communication with uncertain provider acceptance must be manually reconciled against Sent mail before any new draft/send. Failed events and audit history are evidence; never mass-reset email jobs to PENDING.

Set and record RPO/RTO appropriate to your actual plan. Perform restore drills after migration/storage/provider changes. EC2 termination should not remove production database/files, but TLS/config/key recovery is still an operator responsibility.
