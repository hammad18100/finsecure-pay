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
| `infra/` | kind cluster config, Jenkins image and config as code, Docker Compose for Jenkins/SonarQube/Mailpit, deployer RBAC |

## Local setup

Everything runs on one machine: a kind cluster, a registry with login, and Jenkins, SonarQube and Mailpit in Docker Compose. Files are in `infra/`.

1. Put these files in `infra/secrets/` (git-ignored): `jenkins_admin_password`, `registry_username`, `registry_password`, `htpasswd` (from `htpasswd -B`), `kubeconfig` (for the `jenkins-deployer` service account), `sonar_token`.
2. Cluster and registry:
   ```bash
   kind create cluster --config infra/kind-config.yaml
   docker run -d --name kind-registry --network kind -p 127.0.0.1:5001:5000 \
     -v "$PWD/infra/secrets/htpasswd:/auth/htpasswd:ro" -e REGISTRY_AUTH=htpasswd \
     -e REGISTRY_AUTH_HTPASSWD_REALM=FinSecure -e REGISTRY_AUTH_HTPASSWD_PATH=/auth/htpasswd registry:2
   # on the node, map localhost:5001 to http://kind-registry:5000 in /etc/containerd/certs.d/localhost:5001/hosts.toml
   kubectl apply -f k8s/namespace.yaml -f infra/jenkins-rbac.yaml
   ```
3. CI tools: `docker volume create m2 && docker volume create trivy-cache && docker compose -f infra/docker-compose.yml up -d --build`. Jenkins (127.0.0.1:8090) is configured by `infra/jenkins/casc/jenkins.yaml` and polls this repo every minute.
4. Monitoring: `kubectl apply -k monitoring/` (Grafana on :30030, Prometheus on :30090).

## Pipeline

- Security gates: SonarQube quality gate, Trivy dependency scan and image scan (fail on HIGH/CRITICAL), Trivy secret scan.
- Credentials come from the Jenkins credentials store (`registry-creds`, `kubeconfig-deployer`, `sonar-token`). No secrets in this repo.
- Rollback: run the job with `ROLLBACK_VERSION=<tag>` to redeploy a known-good image. A failed rollout is undone automatically.

## Logs

```bash
kubectl -n finsecure logs -l app=finsecure-pay --tail=50
```

In Grafana Explore (Loki): `{namespace="finsecure"} |= "rejected"`
