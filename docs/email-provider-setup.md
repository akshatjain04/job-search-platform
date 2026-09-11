# Mailbox integrations

Both real Gmail API and Microsoft Graph adapters are implemented. They are optional until a user wants to send approved email. A mailbox must be connected by that user; neither API nor MCP accepts arbitrary sender credentials.

## Gmail

Create a Google Cloud project, enable Gmail API, configure OAuth consent and a **confidential web** client. Set `GMAIL_CLIENT_ID` / `GMAIL_CLIENT_SECRET`; register `https://YOUR_DOMAIN/api/v1/mailboxes/gmail/callback`. Requested scopes are openid, email and gmail.send, with offline access. Add test users while the consent app is in testing; public distribution may require Google's verification process. Connect from dashboard Connections and consent to offline mailbox access. The backend verifies the returned email identity and encrypts tokens.

## Outlook

Register a confidential web application in Microsoft Entra. Set `MICROSOFT_CLIENT_ID`, `MICROSOFT_CLIENT_SECRET`, and tenant (`common` for an appropriate multi-account app, or your tenant ID). Register `https://YOUR_DOMAIN/api/v1/mailboxes/outlook/callback`. Grant delegated openid, offline_access, User.Read and Mail.Send permissions, with administrator consent if your organization's policy requires it. Connect from Connections. The backend identifies the mailbox with Graph `/me` and encrypts tokens.

References: [Google server OAuth](https://developers.google.com/identity/protocols/oauth2/web-server), [Microsoft delegated authorization](https://learn.microsoft.com/en-us/graph/auth-v2-user). API approval and provider-account policies are external prerequisites, not something bootstrap can bypass.

## Sending and failure semantics

Generation → DRAFT → AWAITING_APPROVAL → APPROVED → QUEUED → SENDING → SENT/FAILED/DELIVERY_UNKNOWN. Approval covers the exact recipient ID/value, subject/body, message version, resume version and attachment hash. Editing invalidates approval. Workers check the immutable binding and actual attachment bytes again.

MIME messages carry an approval-derived Message-ID and attachment. Gmail returns a provider ID; Graph 202 is recorded as accepted, not a guarantee of delivery. Refresh tokens are exchanged server-side before send when expired. Authentication failures require reconnection. Explicit 429 rejection can be retried with bounded backoff; ambiguous network/5xx/cancellation is never blindly resent. A crash after dispatch intent leads to DELIVERY_UNKNOWN and manual Sent-folder reconciliation.

Local test mode encodes MIME and records `EMAIL_TEST_SENT` without contacting a mailbox. Normal automated tests never send real mail. Live verification requires explicit user consent for a specific recipient/message/attachment; inspect provider Sent mail and audit history. Disconnect removes locally stored tokens; revoke the application's consent in the provider account when needed.

The encryption master key is required to recover tokens. Back it up securely outside the database. Future KMS/Secrets Manager integration should replace key loading through the existing cipher/composition boundary, not expose tokens to clients.
