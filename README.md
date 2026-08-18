# OpsVision Backend

AI-powered **Deployment Intelligence & Recovery** platform backend (hackathon / MVP).

Deterministic pipelines drive scores, policy, incidents, RCA, and recovery. Optional LLM integration only **explains** structured results — it never owns the numerical confidence score or policy decision.

| Stack | |
| --- | --- |
| Language | Java 21 |
| Framework | Spring Boot 3.3 |
| Build | Maven |
| DB | PostgreSQL + Flyway + Spring Data JPA |
| API docs | springdoc OpenAPI (`/swagger-ui.html`) |
| Tests | JUnit 5, Mockito, embedded Postgres (Zonky) |

---

### Prerequisites

- **JDK 21+** (project targets 21; newer JDKs often work for local runs)
- **Maven 3.9+**
- **Docker** (optional): PostgreSQL via Compose, or full backend image
- Optional for live demos:
  - GitHub personal access token (repo + issues)
  - Ollama (preferred local LLM) or OpenAI-compatible API (explanations only)
  - Kubernetes API + Prometheus (live telemetry)

---

### Environment variables

Copy the example file and fill secrets locally (never commit real tokens):

```bash
cp .env.example .env
```

| Variable | Purpose | Default |
| --- | --- | --- |
| `SERVER_PORT` | HTTP port | `8080` |
| `DB_URL` | JDBC URL | `jdbc:postgresql://localhost:5432/opsvision` |
| `DB_USERNAME` / `DB_PASSWORD` | DB credentials | `opsvision` / `opsvision` |
| `GITHUB_API_TOKEN` | GitHub REST API | empty |
| `GITHUB_OWNER` / `GITHUB_REPOSITORY` | Default repo context | empty |
| `OPSVISION_AI_ENABLED` | Enable LLM client | `false` |
| `OPSVISION_AI_PROVIDER` | `none`, `ollama`, or `openai-compatible` | `none` |
| `OPSVISION_OLLAMA_BASE_URL` / `OPSVISION_OLLAMA_MODEL` | Native Ollama (`/api/chat`) | `http://localhost:11434` / `llama3.2` |
| `OPSVISION_AI_BASE_URL` / `OPSVISION_AI_API_KEY` / `OPSVISION_AI_MODEL` | OpenAI-compatible endpoint | see `.env.example` |
| `OPSVISION_OBSERVABILITY_ENABLED` | Live K8s/Prom collection | `false` |
| `OPSVISION_K8S_*` / `OPSVISION_PROMETHEUS_*` | Telemetry clients | see `.env.example` |
| `OPSVISION_INCIDENT_*` | Detection thresholds | see `application.yml` |

Scoring weights and policy bands are centralized under `opsvision.scoring` and `opsvision.policy` in `src/main/resources/application.yml`.

---

### PostgreSQL

**Option A — Docker Compose (recommended for demo)**

```bash
docker compose up -d postgres
```

Creates database/user `opsvision` / `opsvision` on port **5432**.

**Option B — local install**

```sql
CREATE USER opsvision WITH PASSWORD 'opsvision';
CREATE DATABASE opsvision OWNER opsvision;
```

Flyway runs on startup (`ddl-auto=validate`). Migrations live under `src/main/resources/db/migration/`.

---

### Backend startup

From the repository root (with Postgres reachable):

```bash
# Windows PowerShell example — load env then run
# Get-Content .env | ForEach-Object { if ($_ -match '^([^#=]+)=(.*)$') { Set-Item env:$($matches[1]) $matches[2] } }

mvn spring-boot:run
```

Or package and run the JAR:

```bash
mvn -DskipTests package
java -jar target/opsvision-backend-0.0.1-SNAPSHOT.jar
```

**Docker image** (expects Postgres hostname `postgres` by default):

```bash
docker compose up -d
# or: docker build -t opsvision-backend . && docker run --env-file .env -p 8080:8080 opsvision-backend
```

Smoke check:

```bash
curl -s http://localhost:8080/
# OpsVision Backend OK

curl -s http://localhost:8080/actuator/health
```

---

### Tests

```bash
mvn clean test
```

Tests use an **embedded PostgreSQL** (Zonky) and the `test` profile — no external DB required.

Critical workflow coverage includes:

- CI evidence → analyze → confidence score → policy (`DeploymentAnalysisWorkflowIntegrationTest`)
- Deployment → telemetry → incident → RCA → recovery → postmortem (`IncidentRecoveryWorkflowIntegrationTest`)

---

### API documentation

With the app running:

| Resource | URL |
| --- | --- |
| Swagger UI | [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html) |
| OpenAPI JSON | [http://localhost:8080/api-docs](http://localhost:8080/api-docs) |
| Health | [http://localhost:8080/actuator/health](http://localhost:8080/actuator/health) |

#### Main REST surfaces

| Area | Base path |
| --- | --- |
| Deployments (analyze, score, policy, explanation) | `/api/v1/deployments` |
| Observability snapshot | `/api/v1/observability/telemetry` |
| Incidents (detect, RCA, recovery, postmortem, GitHub issue) | `/api/v1/incidents` |

Errors use **RFC 9457** `ProblemDetail` JSON where applicable.

---

### Demo instructions

End-to-end narrative and sample `curl` payloads:

- **[docs/DEMO.md](docs/DEMO.md)** — full hackathon demo script  
- **[docs/demo/](docs/demo/)** — sample JSON bodies  

**Happy path (no live K8s/AI required):**

1. Start Postgres + backend.
2. `POST /api/v1/deployments` with ideal CI evidence → expect high score and `DEPLOY`.
3. `GET .../score`, `.../policy`, optional `.../explanation` (deterministic fallback if AI off).
4. For incident path without cluster: use unit/integration tests, **or** enable observability and call `POST /api/v1/incidents/detect`.
5. `GET /api/v1/incidents/{id}/rca`, `.../recovery`, `.../postmortem`.
6. Optional: `POST .../github-issue` with a real `GITHUB_API_TOKEN`.

See `docs/DEMO.md` for BLOCK scenario (failed build + critical CVE) and safety notes.

---

### Architecture (package-by-feature)

```text
com.opsvision
├── common          # shared exceptions / ProblemDetail handler
├── config          # OpenAPI, property bindings
├── deployment      # analysis API orchestration
├── evidence        # normalized evidence + Semgrep/Trivy/JaCoCo parsers
├── github          # REST client + incident issue automation
├── scoring         # deterministic confidence engine (0–100)
├── policy          # DEPLOY / REVIEW / BLOCK
├── ai              # AiProvider abstraction + deployment explanations
├── observability   # K8s + Prometheus clients → TelemetrySnapshot
├── incident        # detection, timeline, RCA
├── recovery        # recommendation only (no auto-execute)
└── postmortem      # structured blameless draft from evidence
```

**Design rules (MVP):**

- Controllers stay thin; services own business logic.
- JPA entities are not exposed as API models.
- LLM output is never authoritative for scores or policy.
- Recovery **recommends**; it does not roll back or scale automatically.
- Secrets only via env / config — not source control.

---

### Related docs

- [docs/DEMO.md](docs/DEMO.md) — demo walkthrough  
- [docs/TRIVY_ROLLOUT.md](docs/TRIVY_ROLLOUT.md) — scanner rollout notes  
- [.env.example](.env.example) — full env template  

---

### License

Proprietary — OpsVision hackathon project.
