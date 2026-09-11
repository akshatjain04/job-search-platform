#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."
usage() { echo 'Usage: ./scripts/deploy-ec2.sh USER@HOST [/opt/myjobai]'; }
[[ $# -ge 1 && $# -le 2 ]] || { usage; exit 2; }
remote="$1"
destination="${2:-/opt/myjobai}"
[[ "$remote" =~ ^[a-z_][a-z0-9_-]*@[a-zA-Z0-9.-]+$ ]] || { echo 'Invalid SSH destination'; exit 2; }
[[ "$destination" =~ ^/opt/[a-zA-Z0-9_-]+$ ]] || { echo 'Deploy directory must be one application directory directly under /opt'; exit 2; }
[[ -z "$(git status --porcelain)" ]] || { echo 'Commit and verify a clean working tree before deployment.'; exit 1; }
node scripts/platform.mjs preflight --build-host
revision="$(git rev-parse HEAD)"
release="$destination/releases/$revision"
# Strict host-key verification and the existing SSH agent/key are required. Never copy SSH keys.
ssh -o BatchMode=yes -o StrictHostKeyChecking=yes "$remote" "command -v docker >/dev/null && command -v node >/dev/null && test -r '$destination/.env' && test -r '$destination/tls/fullchain.pem' && test -r '$destination/tls/privkey.pem' && mkdir -p '$release/scripts'" || { echo 'Remote prerequisites missing: Docker Compose, Node 22+, readable .env/TLS, or writable release directory. See docs/ec2-deployment.md.'; exit 1; }
scp -q -o BatchMode=yes -o StrictHostKeyChecking=yes scripts/platform.mjs scripts/configuration.mjs "$remote:$release/scripts/"
ssh -o BatchMode=yes -o StrictHostKeyChecking=yes "$remote" "node '$release/scripts/platform.mjs' preflight --runtime --deployment --env='$destination/.env'"
node scripts/platform.mjs build --build-host
export IMAGE_TAG="$revision" APP_ENV_FILE="$(pwd)/.env"
docker compose --env-file .env -f docker-compose.yml -f docker-compose.prod.yml build job-platform-api web
mkdir -p .local/deploy
docker save -o .local/deploy/images.tar "myjobai/backend:$revision" "myjobai/web:$revision"
git archive --format=tar -o .local/deploy/source.tar HEAD
scp -q -o BatchMode=yes -o StrictHostKeyChecking=yes .local/deploy/images.tar .local/deploy/source.tar "$remote:$release/"
ssh -o BatchMode=yes -o StrictHostKeyChecking=yes "$remote" bash -s -- "$destination" "$revision" <<'REMOTE'
set -euo pipefail
destination="$1"; revision="$2"; release="$destination/releases/$revision"
[[ "$destination" =~ ^/opt/[a-zA-Z0-9_-]+$ && "$revision" =~ ^[a-f0-9]{40}$ ]] || exit 2
cd "$release"
tar -xf source.tar
docker load -i images.tar
export APP_ENV_FILE="$destination/.env" IMAGE_TAG="$revision" TLS_DIRECTORY="$destination/tls" ACME_DIRECTORY="$destination/acme"
compose=(docker compose --project-name myjobai --env-file "$destination/.env" -f docker-compose.yml -f docker-compose.prod.yml)
"${compose[@]}" config --quiet
if ! "${compose[@]}" up -d --wait --wait-timeout 300; then
  "${compose[@]}" ps
  "${compose[@]}" logs --tail 100
  echo 'Deployment failed. Previous images/releases are retained; inspect migrations before rollback.' >&2
  exit 1
fi
node scripts/platform.mjs smoke --env="$destination/.env"
ln -sfn "$release" "$destination/current"
echo "Healthy release: $revision"
REMOTE
echo "Deployed $revision to $remote:$release. No persistent data or previous releases deleted."
