# Repository-only Git/GitHub setup

This prerequisite is separate from application bootstrap. Never paste passwords, tokens, private keys or refresh tokens into chat or tracked files. Never run `git config --global`, `git config --system`, or a GitHub convenience setup command that changes global Git helpers.

1. Work in the intended project root. For an empty directory, clone `https://github.com/akshatjain04/job-search-platform` into `.` using an existing secure mechanism, or initialize it and attach that exact origin. If files/history already exist, inspect status and refs and reconcile non-destructively. Stop on unrelated history rather than resetting it.
2. Configure repository-local identity:

   ```bash
   git config --local user.email akshatjain0410@gmail.com
   git config --local user.name YOUR_AUTHENTICATED_GITHUB_NAME
   git remote get-url origin
   ```

3. Use an interactive GitHub CLI device/browser login with a process-scoped `GH_CONFIG_DIR` under the resolved Git metadata directory, e.g. `.git/github-auth`. Do not run `gh auth setup-git`; decline automatic Git credential configuration. Configure only the repository's `credential.https://github.com.helper` to invoke `gh auth git-credential` with that isolated configuration directory. Clear an inherited helper for this host locally first. Never echo the returned credential.
4. OS credential stores may be shared even with an isolated CLI configuration directory. If strict isolation cannot be guaranteed, use a non-persistent credential session, or explicitly choose an isolated file store protected by owner-only filesystem permissions. Such a file is not encrypted by GitHub CLI: do not copy it into backups, containers, archives or commits. Remove repository-specific auth securely when decommissioning the checkout. The implemented bootstrap used protected `.git/github-auth` with a local helper and Windows ACLs.
5. Verify authenticated repository access with `gh api repos/akshatjain04/job-search-platform` without printing credentials, then `git fetch origin` and `git ls-remote origin`. Require push permission before implementation checkpoints. Follow existing default history; do not force push.
6. Compare global/system configuration fingerprints before/after without printing credential values. Print resolved root, branch, exact origin and `git config --local --get user.email`.

For a fresh checkout with existing history, use its `main` branch. Before every commit inspect `git status`, staged diff and secret checks. Push meaningful tested checkpoints, then compare `git rev-parse HEAD` with `git ls-remote origin refs/heads/main`. This project has no script that changes global/system Git configuration or silently initializes a different remote.
