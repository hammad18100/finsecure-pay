# FinSecure Pay

Transaction validation API with a full CI/CD pipeline: Jenkins builds, tests, scans and deploys it to Kubernetes, with Prometheus, Grafana, Alertmanager and Loki for monitoring and logs.

## API

| Method | Path | Result |
|---|---|---|
| GET | `/health` | `{"status":"UP","version":"1.2.0-11"}` |
| POST | `/validate` | 200 if valid, 422 with every reason if not |

```bash
curl -X POST localhost:30080/validate -H 'Content-Type: application/json' -d '{
  "transactionId": "TX-1", "amount": 150.75, "currency": "USD",
  "sourceAccount": "AE070331234567890123456", "destinationAccount": "GB29NWBK60161331926819"}'
```

Rules: amount above 0 and within the limit, max 2 decimals, allowed currency, valid IBAN accounts, source and destination different. Limit and currencies come from the ConfigMap.

## Build and test

```bash
mvn clean verify                      # 15 JUnit 5 tests
docker build --build-arg APP_VERSION=dev -t finsecure-pay:dev .
docker run -p 8080:8080 finsecure-pay:dev
```

## Layout

| Path | What |
|---|---|
| `src/` | Spring Boot 4.1 app and tests |
| `Dockerfile` | Alpine JRE 21 image, runs as user 10001 |
| `Jenkinsfile` | Checkout → Build → Test → Package → Security Checks → Container Build → Image Scan → Push → Deploy → Smoke Test |
| `k8s/` | Namespace, ConfigMap, Deployment (2 replicas, probes), Service |
| `monitoring/` | Prometheus, Alertmanager, Grafana, Loki, Fluent Bit, exporters (`kubectl apply -k monitoring/`) |
| `scripts/traffic.sh` | Sends normal or attack traffic to trigger the invalid-rate alert |

## Pipeline

- Security gates: SonarQube quality gate, Trivy dependency scan and image scan (fail on HIGH/CRITICAL), Trivy secret scan.
- Credentials come from the Jenkins credentials store (`registry-creds`, `kubeconfig-deployer`, `sonar-token`). No secrets in this repo.
- Rollback: run the job with `ROLLBACK_VERSION=<tag>` to redeploy a known-good image. A failed rollout is undone automatically.

## Logs

```bash
kubectl -n finsecure logs -l app=finsecure-pay --tail=50
```

In Grafana Explore (Loki): `{namespace="finsecure"} |= "rejected"`
