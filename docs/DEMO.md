# OpsVision backend — end-to-end demo

This guide walks the hackathon narrative **without requiring live Kubernetes or a paid LLM**. Optional steps enable GitHub issues, AI explanations, and live telemetry when credentials are available.

```text
GitHub / CI evidence
        ↓
POST /api/v1/deployments   (ingest + score + policy)
        ↓
Confidence score + DEPLOY | REVIEW | BLOCK
        ↓
GET .../explanation        (AI or deterministic fallback)
        ↓
(optional) deploy to cluster
        ↓
Prometheus + Kubernetes telemetry
        ↓
POST /api/v1/incidents/detect
        ↓
RCA → recovery recommendation → postmortem draft → GitHub issue
```

Base URL: `http://localhost:8080`

---

### 0. Start services

```bash
docker compose up -d postgres
mvn spring-boot:run
```

Confirm:

```bash
curl -s http://localhost:8080/
curl -s http://localhost:8080/actuator/health
```

Open Swagger UI: http://localhost:8080/swagger-ui.html

---

### 1. Ideal deployment → DEPLOY

```bash
curl -s -X POST http://localhost:8080/api/v1/deployments \
  -H "Content-Type: application/json" \
  -d @docs/demo/analyze-ideal.json
```

**Expect:**

- HTTP `201`
- `score.score` ≥ 80
- `policy.decision` = `DEPLOY`
- Factor breakdown under `score.factors`

Save the returned `deployment.id` as `DEPLOYMENT_ID`.

Follow-ups:

```bash
curl -s http://localhost:8080/api/v1/deployments/$DEPLOYMENT_ID/score
curl -s http://localhost:8080/api/v1/deployments/$DEPLOYMENT_ID/policy
curl -s http://localhost:8080/api/v1/deployments/$DEPLOYMENT_ID/evidence
curl -s http://localhost:8080/api/v1/deployments/$DEPLOYMENT_ID/explanation
```

With `OPSVISION_AI_ENABLED=false` (default), explanation still returns structured text from score/policy/findings (no invented CVEs).

**Optional — Ollama explanations (demo LLM):**

```bash
# Terminal: ollama serve && ollama pull llama3.2
export OPSVISION_AI_ENABLED=true
export OPSVISION_AI_PROVIDER=ollama
export OPSVISION_OLLAMA_BASE_URL=http://localhost:11434
export OPSVISION_OLLAMA_MODEL=llama3.2
```

Then restart the backend and call `GET .../explanation` again — `provider` should be `ollama`.

---

### 2. Risky deployment → BLOCK

```bash
curl -s -X POST http://localhost:8080/api/v1/deployments \
  -H "Content-Type: application/json" \
  -d @docs/demo/analyze-block.json
```

**Expect:**

- `policy.decision` = `BLOCK`
- Reasons mention failed build and/or critical finding
- Findings list includes a critical container CVE

This shows **critical security + failed CI override** score bands.

---

### 3. Observability snapshot (optional live)

Requires `OPSVISION_OBSERVABILITY_ENABLED=true` and reachable K8s/Prometheus endpoints (see `.env.example`).

```bash
curl -s "http://localhost:8080/api/v1/observability/telemetry?namespace=default&workload=api"
```

If disabled or clients fail, the API still responds with partial snapshot metadata and collection notes (non-fatal).

---

### 4. Incident detection → RCA → recovery → postmortem

#### 4a. Live path (cluster + metrics unhealthy)

```bash
curl -s -X POST "http://localhost:8080/api/v1/incidents/detect?deploymentId=$DEPLOYMENT_ID&namespace=prod&workload=api"
```

If signals cross thresholds: response contains an incident with timeline entries.

#### 4b. Offline path (tests)

Without a cluster, demonstrate the same pipeline via tests:

```bash
mvn -Dtest=IncidentRecoveryWorkflowIntegrationTest,DeploymentAnalysisWorkflowIntegrationTest test
```

These inject synthetic unhealthy telemetry and assert RCA, recovery ≠ `NO_ACTION`, and a postmortem draft.

#### 4c. Once you have `INCIDENT_ID`

```bash
curl -s http://localhost:8080/api/v1/incidents/$INCIDENT_ID
curl -s http://localhost:8080/api/v1/incidents/$INCIDENT_ID/rca
curl -s http://localhost:8080/api/v1/incidents/$INCIDENT_ID/recovery
curl -s http://localhost:8080/api/v1/incidents/$INCIDENT_ID/postmortem
```

**Recovery is recommendation-only** — no automatic rollback/scale.

Postmortem drafts are built from **structured** incident + RCA + recovery data (unknowns called out when data is missing).

---

### 5. GitHub issue (optional)

```bash
# Requires GITHUB_API_TOKEN, GITHUB_OWNER, GITHUB_REPOSITORY
curl -s -X POST http://localhost:8080/api/v1/incidents/$INCIDENT_ID/github-issue
```

Idempotent: repeat calls return the same issue linkage (no duplicate spam).

---

### Demo talking points

1. **Deterministic score** — same evidence ⇒ same score/policy (reanalyze endpoint).
2. **Explainable factors** — not a black-box LLM score.
3. **Policy overrides** — critical CVE / failed tests can BLOCK even if coverage looks fine.
4. **AI is optional garnish** — explanations must not invent findings.
5. **Safe recovery** — human approval boundary (`requiresHumanApproval`).
6. **Blameless postmortem** — timeline + impact + prevention from known facts only.

---

### Safety

- Do not commit `.env` or real tokens.
- Do not point demo credentials at production clusters without a read-only token.
- GitHub issue creation mutates the configured repository — use a sandbox repo for demos.
