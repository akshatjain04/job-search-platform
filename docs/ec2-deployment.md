# One-EC2 deployment

Required external prerequisites: AWS account, a Linux x86-64/arm64 EC2 host with Docker/Compose 2.24.4+, Node 22+ for validation, SSH access through an existing agent/key, remote Supabase, selected AI provider, a domain/DNS and hostname-valid TLS. No AWS key is required by the application. Terraform is intentionally not mandatory; this repository does not automatically purchase infrastructure.

1. Provision one Ubuntu LTS host. Start with enough measured headroom for five JVMs; configured application limits total roughly 2.6 GB plus OS/build overhead. A 4 GB-class host is a practical starting point, not a tested capacity guarantee. Build locally and transfer images to avoid production build pressure.
2. Permit TCP 80/443 publicly and SSH only from an administrator CIDR. Workers/API/MCP/PostgreSQL ports must not be publicly mapped. Apply OS updates and use least-privilege instance roles (no broad AWS permissions required).
3. Create `/opt/myjobai`, `releases`, `tls`, and `acme`, writable by the deployment user as appropriate. Place protected production configuration at `/opt/myjobai/.env` (mode 600), setting `TLS_DIRECTORY=/opt/myjobai/tls` and `ACME_DIRECTORY=/opt/myjobai/acme`. Do not copy it into Git releases.
4. Point DNS at the host. Provision/renew a real certificate using your ACME/certificate workflow. Copy full chain and private key to `/opt/myjobai/tls/fullchain.pem` and `privkey.pem`; allow read access to Nginx UID/GID 101 without making the key world-readable. Validate certificate chain, expiry and key match. Configure automated renewal plus graceful Nginx reload. ACME HTTP challenge files can be served from `/opt/myjobai/acme`.
5. Verify the host SSH key through a trusted channel and place it in known_hosts. Deployment deliberately uses StrictHostKeyChecking=yes and never copies private SSH keys.
6. Configure a local protected `.env` for production preflight/build. Commit all intended source changes, then:

```bash
./scripts/deploy-ec2.sh ubuntu@YOUR_EC2_HOST /opt/myjobai
```

The script validates local and remote prerequisites before expensive builds, runs tests/builds, tags backend/web images with the Git revision, transfers Docker images and a Git archive, deploys an immutable release directory, waits for health and runs HTTPS smoke checks. It retains older releases/images and never deletes database/object state. Only after success does `current` point at the new release. No force push or Git history rewrite occurs.

Host and build architecture must match unless you deliberately build matching multi-platform images. Validate production Compose without printing secrets: `docker compose --env-file .env -f docker-compose.yml -f docker-compose.prod.yml config --quiet`. Default base ports are loopback-only; production overrides to 80/443 with TLS. Never add `docker-compose.test.yml` to production.

Rollback: inspect migrations for backward compatibility; restore the prior image tag/release Compose configuration only if safe. Forward-compatible schema changes can roll app images back; destructive schema changes require a tested database recovery plan. A remote deployment is not considered verified until real DNS/TLS, Supabase and provider configuration pass. Current live deployment status is recorded separately in implementation status.
