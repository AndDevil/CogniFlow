# GCP Always Free Deployment for CogniFlow

This guide walks through deploying CogniFlow to Google Cloud Run and setting up a scheduler to run the data ingestion job every 3 hours. All of this stays inside GCP's Always Free tier – so it costs $0/month if you don't exceed the quotas.

---

## Free tier limits (quick check)

GCP gives you these freebies per month:

- **Cloud Scheduler**: 3 jobs per billing account. We'll use 1 job.
- **Cloud Run**: 2 million requests, 180,000 vCPU‑seconds, 360,000 GiB‑seconds.

Our scheduler runs 8 times/day (every 3 hours) = ~240 executions/month.

**Estimated usage per month** (assuming 1 vCPU, 512MB RAM, ~30 seconds per run):

| Resource | Usage | Free limit | % used |
|----------|-------|------------|--------|
| Requests | 240 | 2,000,000 | ~0.01% |
| vCPU‑seconds | 7,200 | 180,000 | 4% |
| GiB‑seconds | 3,600 | 360,000 | 1% |

We also set `--min-instances=0` so Cloud Run scales to zero when idle – no wasted resources.

---

## Dockerfile (simple & small)

This builds your Spring Boot app into a small container (~150MB) and runs it as a non‑root user.

```dockerfile
# Build stage
FROM maven:3.9.6-eclipse-temurin-17-alpine AS builder
WORKDIR /app

# Download dependencies first (caches them)
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source and build the jar
COPY src ./src
RUN mvn clean package -DskipTests

# Runtime stage
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Create a non‑root user (good practice)
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

# Copy the jar from builder
COPY --from=builder /app/target/*.jar app.jar

# Cloud Run injects PORT env var, Spring Boot picks it up automatically
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

*Note: No need to manually set `server.port` – Spring Boot respects the `PORT` environment variable injected by Cloud Run.*

---

## Service account (for secure scheduler auth)

We don't use API keys. Instead, we create a service account that Cloud Scheduler uses to authenticate to Cloud Run.

```bash
# Create the service account
gcloud iam service-accounts create cogniflow-scheduler-sa \
    --display-name="CogniFlow Scheduler SA"

# Give it permission to invoke your Cloud Run service
gcloud run services add-iam-policy-binding cogniflowservice \
    --member="serviceAccount:cogniflow-scheduler-sa@YOUR_PROJECT_ID.iam.gserviceaccount.com" \
    --role="roles/run.invoker" \
    --region="europe-west1"
```
*(Replace `YOUR_PROJECT_ID` with your actual GCP project ID).*

---

## Deploy to Cloud Run

First, build and push your container. You can use GCR or Artifact Registry (Artifact Registry is modern, but either works):

```bash
# Build the image using Cloud Build
gcloud builds submit --tag gcr.io/YOUR_PROJECT_ID/cogniflowservice

# Deploy to Cloud Run (specify Weaviate host if running externally)
gcloud run deploy cogniflowservice \
    --image="gcr.io/YOUR_PROJECT_ID/cogniflowservice" \
    --region="europe-west1" \
    --platform="managed" \
    --no-allow-unauthenticated \
    --min-instances=0 \
    --max-instances=2 \
    --cpu=1 \
    --memory="512Mi" \
    --set-env-vars="COGNIFLOW_JOB_SECRET=YOUR_SCHEDULER_SECRET,COGNIFLOW_WEAVIATE_HOST=your-weaviate-service.run.app"
```

Important flags explained:
- `--no-allow-unauthenticated` – your service won't be public. Only authenticated requests (like the scheduler) can reach it.
- `--min-instances=0` – scales to zero when idle, saving cost.
- `--cpu=1` and `--memory="512Mi"` – enough for this workload.
- The environment variable `COGNIFLOW_JOB_SECRET` must match the value you set in `application.properties` for `cogniflow.job-secret`.

---

## Create the Cloud Scheduler job

This job hits the internal endpoint `POST /api/internal/run-scan` every 3 hours.

```bash
gcloud scheduler jobs create http cogniflow-scan-job \
    --schedule="0 */3 * * *" \
    --uri="https://YOUR_SERVICE_URL/api/internal/run-scan" \
    --http-method="POST" \
    --headers="X-CloudScheduler-JobSecret=YOUR_SCHEDULER_SECRET" \
    --oidc-service-account-email="cogniflow-scheduler-sa@YOUR_PROJECT_ID.iam.gserviceaccount.com" \
    --oidc-token-audience="https://YOUR_SERVICE_URL" \
    --location="europe-west1" \
    --time-zone="UTC" \
    --max-retry-attempts=3 \
    --min-backoff=5m
```
Replace:
- `YOUR_SERVICE_URL` – the URL of your deployed Cloud Run service (looks like `https://cogniflowservice-...-ew.a.run.app`)
- `YOUR_SCHEDULER_SECRET` – same secret you used in the deploy command and in `application.properties`

---

## Avoiding health check costs (important)

By default, Cloud Run only checks that your container starts listening on the expected port. That's fine and doesn't cost extra requests.

But if you add custom liveness or readiness probes (e.g., in a custom `cloudrun.yaml`), Cloud Run will ping those endpoints periodically. That keeps your container alive unnecessarily and burns through the free request quota.

**Just don't define any probes** – the default behaviour is safe and cheap.

Also, if you put a load balancer in front of Cloud Run (you probably don't need one), configure its health check interval as high as possible (e.g., 300 seconds) and point it to a static route like `/health` that does nothing. But honestly, for this project, skip the load balancer entirely – use the Cloud Run URL directly.

---

## Testing locally and remotely

### Local test (before deploying)
```bash
curl -i -X POST http://localhost:8080/api/internal/run-scan \
  -H "X-CloudScheduler-JobSecret: YOUR_SCHEDULER_SECRET"
```

### Remote test (after deployment)
You'll need to pass an identity token from your Google account (if you have permission to invoke the service):
```bash
curl -i -X POST https://YOUR_SERVICE_URL/api/internal/run-scan \
  -H "X-CloudScheduler-JobSecret: YOUR_SCHEDULER_SECRET" \
  -H "Authorization: Bearer $(gcloud auth print-identity-token)"
```
If this returns 401 or 403, check that:
- The remote test uses your personal account via the `gcloud auth` print token. If it fails, ensure your personal Google account has the `run.invoker` role on the service.
- For the scheduled job, ensure the `cogniflow-scheduler-sa` service account (not your personal account) has the `run.invoker` role on the service.

---

## Common issues & honest notes

- **The job secret is not a password** – it's a shared secret between the scheduler and your app. Keep it in a safe place (e.g., Secret Manager) but don't over‑engineer it. A random string like `s3cr3t-abc123` is fine.
- **Cloud Scheduler free tier** – you get 3 jobs per month for free. We use 1. No surprises.
- **Cloud Run request quota** – even if you accidentally exceed it, GCP doesn't automatically start billing unless you've enabled billing. But to use Cloud Run at all, you must have a billing account attached (required). Just set budget alerts if you're worried.
- **The scheduler will fail if your app is not ready** – make sure your API keys (Alpha Vantage, Gemini) are valid and Weaviate is reachable. The scheduler doesn't retry by default – you can configure retries with `--max-retry-attempts` if needed.
- **Weaviate in the cloud** – This guide assumes Weaviate runs somewhere reachable (e.g., a separate Cloud Run service or a managed Weaviate instance). Make sure your `cogniflow.weaviate.host` is set correctly for production.

That's it. Once the scheduler runs, it will call `/api/internal/run-scan` every 3 hours, and your Cloud Run service will wake up, ingest fresh data, and go back to sleep.
