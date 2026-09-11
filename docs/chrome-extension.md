# Chrome extension

Build: `cd extension && npm ci && npm run verify`. Load `extension/dist` using **chrome://extensions → Developer mode → Load unpacked**. A Docker-built dashboard also serves `/myjobai-extension.zip`; download and extract it first. Do not upload private configuration with a store package.

1. Copy the installed extension ID into the backend `EXTENSION_IDS` comma-separated allowlist and restart API/MCP as applicable.
2. Sign in to the dashboard in the same Chrome profile.
3. Open the extension, enter the exact HTTPS dashboard origin (HTTP loopback is allowed only for local acceptance), and click **Connect securely**.
4. Grant optional host access to that platform origin. Authorization uses state, S256 PKCE, an allowlisted `chromiumapp.org` redirect, a one-use two-minute code and a one-hour access token stored in `chrome.storage.session`. No refresh token is exposed.
5. On a job/hiring/referral page, click **Analyze current page**, review extracted text/company, then **Save and match**. Nothing is uploaded before that explicit confirmation.
6. Calculate match, choose an uploaded base resume for tailoring, or open job/outreach in the dashboard. Generation is asynchronous; final approval remains in the full dashboard.

Permissions: activeTab, scripting, storage, identity. Optional host patterns permit selecting your deployment, but no broad host access is granted at installation. No background/content scripts, scheduling, crawling, automatic social action or portal password collection. Chrome/internal/file pages are rejected. Capture uses visible text and bounded JSON-LD, not arbitrary script execution.

Tests cover extraction/normalization, malicious text as data, unsupported protocols, origin validation and build. PKCE replay/ownership are tested against the backend. Live extension installation/login is a manual browser acceptance step; changing an unpacked installation path may change its ID.
