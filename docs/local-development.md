# Local development

Install Java 21+ **JDK**, Maven 3.9+, Node 22+, Git and Docker with Linux containers/Compose 2.24.4+. Testcontainers must reach the Docker daemon. On Windows, ensure the daemon is running and allow required Docker permissions. The script detects the JDK used by `java` and sets JAVA_HOME for child builds only; global environment/Git configuration is not changed.

```bash
./scripts/bootstrap-and-run.sh --demo
# PowerShell: .\scripts\bootstrap-and-run.ps1 -Demo
```

Explicit demo mode builds/tests all components, uses isolated PostgreSQL and private local-test objects, starts all five Java roles plus Nginx, then smoke-tests. Docker volumes preserve test data across stop/start. Do not expose this login mode beyond localhost. No real mailbox or billable AI call is made.

Host iteration commands:

```bash
mvn -f backend/pom.xml clean package
cd web && npm ci && npm run verify
cd ../extension && npm ci && npm run verify
```

For Windows resume tests pass `-Dmyjobai.resume.font=C:/Windows/Fonts/arial.ttf`; on Linux install DejaVu Sans or set `RESUME_FONT_PATH`. Maven tests require Docker even when application runtime uses remote Supabase. Normal tests cannot be made “green” by silently skipping unavailable PostgreSQL.

`web` Vite can proxy `/api` to a separately started API on localhost:8081. Set API `PORT=8081`, a test-only DB/config and `APP_PUBLIC_URL=http://localhost:5173` for same-origin browser development; production requires HTTPS. The verified/recommended complete-stack workflow is Docker on port 8080. Never copy production mailbox tokens into demo storage.

Use IDE Java 21 and TypeScript strict mode. Preserve Flyway migration history and immutable records. Run repository checks, targeted tests, then the complete gate before committing. See [testing](testing.md).
