# Webforx SRE On-Call Guideline

---
**Default Customer-Facing Baseline SLO (Tier 1): 99.8% Availability**  
**Default Error Budget: 0.2% (example calculation below; actual budgets are defined per service)**

---

## Table of Contents
1. [SLO & Error Budget Overview](#1-slo--error-budget-overview)
2. [On-Call Responsibilities](#2-on-call-responsibilities)
3. [Incident Severity Levels](#3-incident-severity-levels)
4. [Escalation Matrix](#4-escalation-matrix)
5. [Platform Tools Runbooks](#5-platform-tools-runbooks)
6. [Incident Response Workflow](#6-incident-response-workflow)
7. [Post-Incident Review](#7-post-incident-review)
8. [On-Call Handoff Procedure](#8-on-call-handoff-procedure)

---

## 1. SLO & Error Budget Overview

### What an SLO means at Web Forx
An SLO is a reliability target for a **specific service** (e.g., Bhair checkout API, WebConnect login, EDUSUC course access).
It is used to:
- define what “good” looks like for customers and internal teams
- drive alerting (page only when the user experience is materially at risk)
- create an error budget to balance feature velocity vs stability work

### Service Tiers (initial)
| Tier                               | Description                                                                                           | Examples                                                                                  |
| ---------------------------------- | ----------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------- |
| **Tier 0 (Foundational)**          | Security/access and deployment control. If this is down, engineers cannot operate or recover quickly. | AWS SSO/Identity Center, Vault, VPN, CI/CD control planes                                 |
| **Tier 1 (Customer-Critical)**     | Core customer-facing user journeys and APIs.                                                          | Bhair browsing/cart/checkout, WebConnect login/dashboard/dues, EDUSUC login/course access |
| **Tier 2 (Internal-Productivity)** | Internal tools that slow teams down but do not immediately impact customers.                          | Documentation, project tools, non-critical SaaS                                           |

Internal platform/tool SLOs exist to **support** Tier 1 customer SLOs (they do not replace customer SLOs).

### Initial SLO targets (starter values)
| Metric            | Tier 0   | Tier 1   | Tier 2   | Measurement (starter)                                |
| ----------------- | -------- | -------- | -------- | ---------------------------------------------------- |
| **Availability**  | 99.9%    | 99.8%    | 99.5%    | Uptime Kuma synthetic checks + infrastructure health |
| **Latency (P95)** | < 500ms  | < 500ms  | < 1000ms | API metrics (Prometheus)                             |
| **Latency (P99)** | < 1000ms | < 1000ms | < 2000ms | API metrics (Prometheus)                             |
| **Error Rate**    | < 0.2%   | < 0.5%   | < 1.0%   | 5xx / total requests (Prometheus)                    |

### What we measure (SLIs)
Minimum set to make SLOs real:
- **Availability**: “Can a user complete the critical action?” (synthetic checks against health endpoints and key pages)
- **Request success rate**: 2xx/3xx vs 5xx for critical APIs
- **Latency**: P95/P99 for critical API routes (not averages)
- **Dependency health**: database connectivity, queue depth, third-party payment/notification failures (tracked as contributing signals)


### How to build service-scoped SLOs (we are establishing SLOs now)
Use this lightweight process to create SLOs per service without slowing delivery.

1) **Pick 3–5 critical user journeys per app**
- Bhair: browse → product view → add-to-cart → checkout/payment
- WebConnect: login → dashboard load → dues/payment → notifications/events
- EDUSUC: login → course access → lesson load → assignment submit

2) **Define SLIs for each journey**
- Availability (synthetic check success)
- Error rate (5xx/total requests for critical routes)
- Latency (P95/P99 for critical routes)

3) **Set Tier-based starter SLOs**
Start with Tier 1 defaults and tighten quarterly based on real data.

4) **Implement measurement**
- Uptime Kuma for journey availability checks (externally visible where appropriate)
- Prometheus/Grafana for API error rate and latency (per-route where possible)

5) **Operationalize**
- Alert thresholds must align to the SLO (page when SLO risk is real).
- Review error budget burn weekly/biweekly and gate releases if burn is elevated.

### Error Budget Calculation
```
Monthly Error Budget = 30 days × 24 hours × 0.2% = 1.44 hours (86.4 minutes)
Weekly Error Budget  = 7 days × 24 hours × 0.2%  = 20.16 minutes
```

### Error Budget Policy
| Budget Remaining | Action                                                |
| ---------------- | ----------------------------------------------------- |
| > 50%            | Normal development velocity, feature work prioritized |
| 25-50%           | Slow down risky deployments, prioritize stability     |
| 10-25%           | Freeze non-critical changes, focus on reliability     |
| < 10%            | Full freeze, all hands on reliability improvements    |

---

## 2. On-Call Responsibilities

### Primary On-Call Engineer
- Respond to all alerts within SLA (see severity levels)
- Triage and classify incident severity
- Initiate incident response and communication
- Escalate when necessary
- Document actions in incident timeline
- Participate in handoff briefings

### Secondary On-Call Engineer
- Backup for primary when unreachable (15-minute escalation)
- Assist with complex incidents requiring multiple engineers
- Handle overflow during high-incident periods

### On-Call Readiness Checklist (Start of Rotation + Start of Shift)
**Goal:** ensure the on-call engineer can respond in minutes (not “first 30 minutes are access issues”).

**Start of rotation (before taking the pager)**
- Confirm you have access to:
  - SSO (IdP), VPN
  - AWS console + CLI
  - Git repo(s) and deployment pipelines
  - Runbooks (this SOP) and incident templates
- Review the current service list in-scope (Tier 0 + Tier 1) and verify you know where the dashboards are.
- Review open SEV1/SEV2 incident tickets and active action items.
- Review any scheduled maintenance windows and planned production releases.

**Start of shift (daily)**
- **Paging test:** verify paging works **at least once per week per engineer** (or after phone/app changes).
- Verify access works end-to-end:
  - AWS SSO / CLI access
  - VPN
  - Kubernetes access (`kubectl`)
  - ArgoCD UI/CLI access
  - Logs (Loki) and dashboards (Grafana)
- **Dashboard readiness:** open and pin the standard dashboards for Tier 0 and Tier 1 services (errors, latency, saturation, traffic).
- **Synthetics readiness:** confirm Uptime Kuma checks exist and are green for:
  - Bhair: browse → add-to-cart → checkout (at least a lightweight endpoint check if full journey is not yet automated)
  - WebConnect: login → dashboard load
  - EDUSUC: login → course access
- **Runbook coverage expectation:** top paging alerts must have a linked runbook entry (or a ticket to create one). If a page has no runbook, create a ticket during the rotation.
- Review active silences (must have a reason + owner + expiry + ticket link).

### Proactive Reliability Practices (Proactive, not “dashboard watching”)
On-call is primarily alert-driven. Proactive work is time-boxed and repeatable so it scales.

**Daily (business days)**
- Review dashboards for Tier 0 and Tier 1 services using the **Golden Signals**:
  - **Latency** (P95/P99)
  - **Errors** (5xx/error rate)
  - **Traffic** (request rate / key journey checks)
  - **Saturation** (CPU/memory/disk, DB connections, queue depth)
- Review Uptime Kuma synthetic checks for core user journeys (Bhair/WebConnect/EDUSUC).
- If any metric is trending toward paging thresholds, open a tracking ticket (do not “wait for it to page”).

**Weekly**
- **Alert hygiene:** identify top 5 noisy alerts; tune thresholds, add runbook links, or retire non-actionable alerts.
- **Runbook improvements:** update at least 1 runbook based on real incidents or near-misses.

**Weekly / Biweekly**
- **Error budget review:** review error budget burn by service and flag elevated burn (risk of breaching SLO).
- If burn is elevated for Tier 1, create a reliability work item and consider release gating (see below).

**Monthly**
- **Game day / incident drill (60–90 min):** run one planned scenario (e.g., DB failover, bad deploy rollback, payment provider outage).
- Document outcomes and create action items with owners and due dates.

**Release readiness and deployment gating**
- If a Tier 1 service is **burning error budget quickly** or has repeated SEV2+ incidents:
  - prioritize reliability fixes over new features for that service
  - require IC/Supervisor approval for high-risk changes
  - increase canary/gradual rollout and post-deploy verification requirements

## 3. Incident Severity Levels

| Severity            | Definition                                                                | Acknowledge (Ack) SLA  | Engage SLA (channel + roles) | Stakeholder Update Cadence       | Status Page Policy (Internal + External)                               | Examples                                                                                               |
| ------------------- | ------------------------------------------------------------------------- | ---------------------- | ---------------------------- | -------------------------------- | ---------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------ |
| **SEV1 - Critical** | Complete outage, widespread impact, data loss/security risk               | **≤ 5 min**            | **≤ 10 min**                 | Every 30 min                     | **Immediate** once SEV1 is confirmed                                   | Bhair checkout down, WebConnect down, EDUSUC down, Vault/VPN/AWS SSO outage, ArgoCD control-plane down |
| **SEV2 - Major**    | Partial outage or severe degradation, significant user impact             | **≤ 15 min**           | **≤ 30 min**                 | Every 60 min                     | **After 30 min** *if still unresolved or customer impact is confirmed* | Elevated 5xx/latency on critical APIs, degraded payments/notifications, Prometheus/Grafana unavailable |
| **SEV3 - Minor**    | Limited impact, workaround available; not materially affecting most users | **≤ 4 business hours** | N/A                          | Every 4 business hours           | Internal-only (if user-visible)                                        | Isolated errors, minor feature degradation, non-critical tool issues                                   |
| **SEV4 - Low**      | Minimal impact, cosmetic issues, informational alerts                     | **Next business day**  | N/A                          | Daily business hours (if needed) | None                                                                   | Documentation issues, minor UI defects, low-priority alerts                                            |

### Severity Decision Tree
```
Is a Tier 1 customer-facing service affected? (Bhair / WebConnect / EDUSUC)
├── YES → Is it a complete outage or widespread impact?
│         ├── YES → SEV1
│         └── NO  → SEV2
└── NO  → Is a Tier 0 foundational tool down?
          ├── YES (Vault, VPN, AWS SSO, ArgoCD) → SEV1
          ├── YES (Prometheus, Grafana, AlertManager, Loki) → SEV2
          └── NO  → SEV3 or SEV4
```

---

## 4. Escalation Matrix

### Escalation Timeline (no-ack and no-progress)
**Non-negotiable:** If the Primary On-Call does **not** acknowledge the page, escalate to Secondary at **exactly 10 minutes**.

| Time Elapsed                     | Trigger                                       | Action                                                                           | Owner                       | Artifact                                       |
| -------------------------------- | --------------------------------------------- | -------------------------------------------------------------------------------- | --------------------------- | ---------------------------------------------- |
| 0 min                            | Alert fired                                   | Page Primary (and create alert record)                                           | Alerting system             | Alert record                                   |
| ≤ 5 min (SEV1) / ≤ 15 min (SEV2) | Ack SLA                                       | Primary acknowledges and begins triage                                           | Primary                     | Ack in Pager + note in incident channel/ticket |
| 10 min                           | **No ack**                                    | Escalate page to Secondary                                                       | Alerting system             | Pager escalation record                        |
| 15 min                           | SEV1/SEV2 confirmed                           | Create incident channel + assign roles + create incident ticket + start timeline | Primary (until IC assigned) | Channel + ticket + pinned timeline             |
| 30 min                           | SEV1/SEV2 unresolved **or** unclear ownership | Engage an IC (POC) and notify Supervisor                                         | Primary/Secondary           | Ticket updated with IC + escalation note       |
| 60 min                           | SEV1 still unresolved                         | Escalate to COO / VP Engineering (for prioritization/resources)                  | IC / Supervisor             | Ticket escalation note                         |
| 120 min                          | SEV1 still unresolved                         | Escalate to CTO                                                                  | IC / Supervisor             | Ticket escalation note                         |

### Contact List
| Role                          | Primary Contact                          | Backup Contact       |
| ----------------------------- | ---------------------------------------- | -------------------- |
| On-Call Primary               | PagerDuty/AlertManager                   | Slack #sre-oncall    |
| On-Call Secondary             | PagerDuty/AlertManager                   | Slack #sre-oncall    |
| Incident Commander (IC) / POC | Agada Ikoyi / Chnonso / Valdes / Derrick | Supervisor           |
| Supervisor (POC lead)         | Marjorie Echu                            | COO / VP Engineering |
| COO / VP Engineering          | Tia Merluisa                             | CTO                  |
| CTO                           | Simon Ocheme                             | COO / VP Engineering |

---

## 5. Platform Tools Runbooks

### 5.1 Monitoring & Observability

#### Grafana
**Purpose**: Visualization and dashboards  
**URL**: `https://grafana.edusuc.net/`  
**Impact if down**: No visibility into metrics, SEV2

| Alert                  | Possible Cause       | Resolution                                                        |
| ---------------------- | -------------------- | ----------------------------------------------------------------- |
| Grafana Unreachable    | Pod crash, OOM       | Check pod status: `kubectl get pods -n monitoring -l app=grafana` |
| Dashboard Loading Slow | Database issues      | Check Grafana DB (PostgreSQL/SQLite)                              |
| Datasource Error       | Prometheus/Loki down | Verify datasource connectivity                                    |

```bash
# Restart Grafana
kubectl rollout restart deployment/grafana -n monitoring

# Check logs
kubectl logs -n monitoring -l app=grafana --tail=100
```

#### Prometheus
**Purpose**: Metrics collection and alerting  
**URL**: `https://prometheus.edusuc.net/`  
**Impact if down**: No metrics, no alerts, SEV2

| Alert            | Possible Cause            | Resolution                                |
| ---------------- | ------------------------- | ----------------------------------------- |
| Prometheus Down  | OOM, disk full            | Check resources and PVC usage             |
| Targets Down     | Network issues, app crash | Check target endpoints                    |
| High Cardinality | Bad metric labels         | Identify and fix high-cardinality metrics |

```bash
# Check Prometheus status
kubectl get pods -n monitoring -l app=prometheus

# Check disk usage
kubectl exec -n monitoring prometheus-0 -- df -h /prometheus

# Reload config
curl -X POST http://prometheus:9090/-/reload
```

#### AlertManager
**Purpose**: Alert routing and notification  
**URL**: `https://alertmanager.edusuc.net/`  
**Impact if down**: No alert notifications, SEV2

| Alert                  | Possible Cause       | Resolution                            |
| ---------------------- | -------------------- | ------------------------------------- |
| AlertManager Down      | Pod crash            | Restart deployment                    |
| Alerts Not Firing      | Config error         | Validate alertmanager.yml             |
| Notifications Not Sent | Webhook/email config | Check integrations (Slack, PagerDuty) |

```bash
# Validate config
amtool check-config /etc/alertmanager/alertmanager.yml

# Test notification
amtool alert add test severity=critical
```

#### Loki (Logs - Connect App & Tools)
**Purpose**: Log aggregation  
**URL**: `https://loki.edusuc.net/`  
**Impact if down**: No log visibility, SEV2

| Alert                 | Possible Cause              | Resolution            |
| --------------------- | --------------------------- | --------------------- |
| Loki Ingestion Failed | Rate limit, disk full       | Check ingester status |
| Queries Timeout       | Large time range, bad query | Optimize LogQL query  |

```bash
# Check Loki components
kubectl get pods -n monitoring -l app=loki

# Check ingestion rate
curl -s http://loki:3100/metrics | grep loki_ingester
```

---

### 5.2 Deployment & CI/CD

#### ArgoCD
**Purpose**: GitOps continuous deployment  
**URL**: `https://argocd.edusuc.net/`  
**Impact if down**: No deployments, SEV1

| Alert                | Possible Cause           | Resolution               |
| -------------------- | ------------------------ | ------------------------ |
| ArgoCD Server Down   | Pod crash, OOM           | Restart argocd-server    |
| Sync Failed          | Git auth, manifest error | Check app sync status    |
| Application Degraded | Deployment issues        | Check application health |

```bash
# Check ArgoCD status
kubectl get pods -n argocd

# Check application status
argocd app list
argocd app get <app-name>

# Force sync
argocd app sync <app-name> --force

# Restart ArgoCD
kubectl rollout restart deployment/argocd-server -n argocd
```

#### Forgejo
**Purpose**: Git repository hosting  
**URL**: `https://git.edusuc.net/`  
**Impact if down**: No code access, no CI triggers, SEV1

| Alert            | Possible Cause            | Resolution                  |
| ---------------- | ------------------------- | --------------------------- |
| Forgejo Down     | Database issue, disk full | Check PostgreSQL, disk      |
| Push/Pull Slow   | High load, storage I/O    | Check resources             |
| Webhooks Failing | Network, endpoint down    | Check webhook delivery logs |

```bash
# Check Forgejo status
kubectl get pods -n forgejo

# Check database connection
kubectl exec -n forgejo forgejo-0 -- forgejo admin doctor check
```

#### Connect Deployments (AWS Docker Compose)
**Purpose**: Staging/development Connect app  
**Impact if down**: Development blocked, SEV2

| Alert                      | Possible Cause          | Resolution                |
| -------------------------- | ----------------------- | ------------------------- |
| Container Exited           | App crash, OOM          | Check container logs      |
| Service Unreachable        | Network, security group | Check AWS security groups |
| Database Connection Failed | RDS issues              | Check RDS status          |

```bash
# SSH to EC2 instance
ssh -i <key.pem> ec2-user@<instance-ip>

# Check containers
docker-compose ps
docker-compose logs --tail=100 <service>

# Restart services
docker-compose restart <service>
docker-compose up -d
```

---

### 5.3 Security & Access

#### AWS SSO / AWS Identity Center
**Purpose**: Centralized AWS access management  
**Impact if down**: No AWS console/CLI access, SEV1

| Alert             | Possible Cause       | Resolution                        |
| ----------------- | -------------------- | --------------------------------- |
| SSO Login Failed  | IdP issues, config   | Check AWS SSO console, IdP status |
| Permission Denied | Permission set issue | Verify permission set assignments |

```
Resolution Steps:
1. Check AWS SSO Health Dashboard
2. Verify Identity Provider (IdP) connectivity
3. Check permission set configurations
4. Review CloudTrail for access denied events
```

#### Vault
**Purpose**: Secrets management  
**URL**: `https://vault.edusuc.net/`  
**Impact if down**: No secrets access, deployments fail, SEV1

| Alert             | Possible Cause           | Resolution         |
| ----------------- | ------------------------ | ------------------ |
| Vault Sealed      | Pod restart, manual seal | Unseal Vault       |
| Vault Unreachable | Pod crash, network       | Check pod status   |
| Auth Failed       | Token expired, policy    | Check auth methods |

```bash
# Check Vault status
kubectl exec -n vault vault-0 -- vault status

# Unseal Vault (requires unseal keys)
kubectl exec -n vault vault-0 -- vault operator unseal <key1>
kubectl exec -n vault vault-0 -- vault operator unseal <key2>
kubectl exec -n vault vault-0 -- vault operator unseal <key3>

# Check pod logs
kubectl logs -n vault vault-0
```

#### VPN
**Purpose**: Secure network access  
**Impact if down**: No internal access, SEV1

| Alert              | Possible Cause         | Resolution                     |
| ------------------ | ---------------------- | ------------------------------ |
| VPN Server Down    | Instance crash, config | Check VPN server status        |
| Connection Timeout | Network, firewall      | Check security groups, routing |
| Auth Failed        | Certificate expired    | Renew certificates             |

```bash
# Check VPN server status (if self-hosted)
systemctl status openvpn@server

# Check connections
cat /var/log/openvpn/status.log

# Restart VPN
systemctl restart openvpn@server
```

#### Passbolt
**Purpose**: Team password management  
**URL**: `https://passbolt.edusuc.net/`  
**Impact if down**: No password access, SEV2

| Alert          | Possible Cause      | Resolution            |
| -------------- | ------------------- | --------------------- |
| Passbolt Down  | Container crash     | Restart container     |
| Database Error | MySQL/MariaDB issue | Check database status |

```bash
# Check Passbolt health
docker-compose exec passbolt bin/cake passbolt healthcheck

# Restart Passbolt
docker-compose restart passbolt
```

---

### 5.4 Development & Collaboration Tools

#### DBeaver (Team Server if applicable)
**Purpose**: Database management  
**Impact if down**: Database management inconvenient, SEV4

| Issue              | Resolution                           |
| ------------------ | ------------------------------------ |
| Connection timeout | Check network, VPN status            |
| Auth failed        | Verify credentials in Vault/Passbolt |

#### SonarQube
**Purpose**: Code quality analysis  
**URL**: `https://sonarqube.edusuc.net/`  
**Impact if down**: No code analysis, SEV3

```bash
# Check SonarQube
kubectl get pods -n sonarqube

# Check logs
kubectl logs -n sonarqube -l app=sonarqube --tail=100

# Restart
kubectl rollout restart deployment/sonarqube -n sonarqube
```

#### Uptime Monitoring
**Purpose**: External availability monitoring  
**Impact if down**: No external monitoring, SEV2

| Alert               | Resolution                                   |
| ------------------- | -------------------------------------------- |
| Uptime monitor down | Check uptime service status, verify API keys |
| False positives     | Adjust check intervals, thresholds           |

#### Bookstack
**Purpose**: Documentation wiki  
**URL**: `https://bookstack.edusuc.net/`  
**Impact if down**: No documentation access, SEV3

```bash
# Restart Bookstack
docker-compose restart bookstack

# Check logs
docker-compose logs bookstack --tail=100
```

#### Taiga
**Purpose**: Project management  
**URL**: `https://tree.taiga.io/`  
**Impact if down**: Project tracking unavailable, SEV3

```bash
# Check Taiga services
docker-compose ps

# Restart Taiga
docker-compose restart taiga-back taiga-front taiga-events
```

#### Timesheet
**Purpose**: Time tracking  
**Impact if down**: Time tracking unavailable, SEV4

#### Lucidchart
**Purpose**: Diagramming (SaaS)  
**Impact if down**: Diagrams inaccessible, SEV4  
**Resolution**: Check Lucidchart status page, contact support

#### Figma
**Purpose**: Design collaboration (SaaS)  
**Impact if down**: Design work blocked, SEV3  
**Resolution**: Check Figma status page, use offline mode

#### Expo.dev
**Purpose**: Mobile app development  
**Impact if down**: Mobile builds blocked, SEV3  
**Resolution**: Check Expo status page, use local builds

#### Pusher
**Purpose**: Real-time messaging  
**Impact if down**: Real-time features degraded, SEV2  
**Resolution**: Check Pusher status, verify API keys, check quotas

---

## 6. Incident Response Workflow

### Ticket + Artifact Trail (SEV1/SEV2 mandatory)
For every SEV1/SEV2, create and maintain a complete trail so the incident is auditable and repeatable.

**Required objects (must exist within 15 minutes of SEV1/SEV2 confirmation)**
- **Incident ticket** (system of record):
  - severity, impacted services, customer impact, owners/roles, start time, and current status
- **Incident channel**: `#incident-<YYYYMMDD>-<short-name>`
  - pinned message with roles, ticket link, and current mitigation
- **Timeline**:
  - maintained by Scribe (pinned message and mirrored in ticket or linked doc)

**Linking rule (no orphan artifacts)**
The incident ticket must include links to:
- **Alert** (source page/alert)
- **Incident channel**
- **Dashboards** (Grafana panels relevant to the incident)
- **Logs** (Loki queries or equivalent)
- **Deployments/changes** (ArgoCD revision, commit SHA, pipeline run, feature flag change)
- **Status page updates** (internal + external URLs and timestamps)
- **Postmortem** (SEV1/SEV2 required)

**Postmortem and action items**
- SEV1/SEV2: postmortem is mandatory and must be completed within **48 hours**.
- Action items must be:
  - assigned owners
  - prioritized (P0/P1/P2)
  - tracked to closure with due dates

### Status Page Policy (Internal + External)
**Owner:** Comms Lead (default: Secondary On-Call). IC is accountable for correctness and timing.

**Where to post**
- **Internal status page**: for employees and internal stakeholders (engineering, support, leadership).
- **External status page**: for customers (public).

**When to post**
- **SEV1:** Post **immediately** once SEV1 is confirmed (even if RCA is unknown).
- **SEV2:** Post after **30 minutes** **if still unresolved** or **customer impact is confirmed**.
- **SEV3/SEV4:** External posts are optional; internal-only if user-visible.

**Update cadence**
- **SEV1:** every **30 minutes** until mitigated; then hourly until fully resolved.
- **SEV2:** every **60 minutes** until mitigated; then every 2 hours until fully resolved.

**Each update must include**
- **Impact**: who is affected and what functionality is degraded/unavailable
- **Start time (UTC and local)**: when the incident started or was first detected
- **Current status**: investigating / identified / mitigating / monitoring / resolved
- **Mitigation**: what is being done now (even if partial)
- **Next update time**: exact timestamp for the next update
- **Customer support guidance** (if applicable): workaround or expected behavior

**Linking requirements**
Every status page post must be linked in the incident ticket with:
- timestamp
- URL to the update
- summary of what changed

### Phase 1: Detection & Triage (0-15 minutes)
```
1. Alert received via AlertManager/PagerDuty
2. Acknowledge (Ack) within SLA
3. Classify severity using decision tree
4. Create incident channel: #incident-<YYYYMMDD>-<brief>
5. Assign roles (SEV1/SEV2):
   - Incident Commander (IC)
   - Technical Lead
   - Comms Lead
   - Scribe
6. Create/attach incident ticket and start timeline
7. Post initial status:
   - what’s broken / who is impacted
   - current hypothesis
   - immediate mitigation steps being attempted
8. If SEV1: publish internal + external status page update immediately
```

### Phase 2: Investigation & Mitigation (15-60 minutes)
```
1. Gather data:
   - Check Grafana dashboards
   - Query Loki logs
   - Review Jaeger traces (if applicable)
   - Check recent deployments in ArgoCD

2. Identify root cause or implement temporary mitigation

3. Communicate:
   - Update stakeholders every 30 min (SEV1) or 1 hour (SEV2)
   - Post updates in incident channel
```

### Phase 3: Resolution (Variable)
```
1. Implement fix
2. Verify fix with monitoring
3. Confirm service restored
4. Update status pages (internal + external per severity rules)
5. Close incident channel with summary
```

### Phase 4: Post-Incident (Within 48 hours)
```
1. Schedule post-incident review
2. Write incident report
3. Create action items for prevention
4. Update runbooks if needed
```

---

## 7. Post-Incident Review

### Blameless Post-Mortem Template
```markdown
## Incident: [Title]
**Date**: YYYY-MM-DD
**Duration**: X hours Y minutes
**Severity**: SEVX
**Impact**: [Description of user/business impact]

## Timeline
| Time (UTC) | Event                 |
| ---------- | --------------------- |
| HH:MM      | Alert triggered       |
| HH:MM      | On-call acknowledged  |
| HH:MM      | Root cause identified |
| HH:MM      | Mitigation applied    |
| HH:MM      | Service restored      |

## Root Cause
[Detailed technical explanation]

## Resolution
[What was done to fix the issue]

## Impact on Error Budget
- Downtime: X minutes
- Error budget consumed: X%
- Remaining monthly budget: X%

## Action Items
| Action     | Owner   | Due Date   | Status |
| ---------- | ------- | ---------- | ------ |
| [Action 1] | @person | YYYY-MM-DD | Open   |
| [Action 2] | @person | YYYY-MM-DD | Open   |

## Lessons Learned
- What went well:
- What could be improved:
```

---

## 8. On-Call Handoff Procedure

### End of Rotation Checklist
- [ ] Document any ongoing issues
- [ ] Update incident tickets with current status
- [ ] Note any alerts that were silenced (reason, owner, ticket link, expiration time)
- [ ] List any upcoming maintenance windows
- [ ] Highlight any error budget concerns

### Handoff Meeting Agenda (30 minutes)
1. **Active Incidents** (5 min): Any ongoing issues
2. **Recent Incidents** (10 min): What happened, lessons learned
3. **Alerts Review** (5 min): Noisy alerts, false positives
4. **Upcoming Changes** (5 min): Deployments, maintenance
5. **Error Budget Status** (5 min): Current burn rate, concerns

### Handoff Document Template
```markdown
## On-Call Handoff: [Date Range]

### Active Issues
- [Issue 1]: Status, next steps

### Notable Incidents
- [Incident 1]: Brief summary, resolution

### Silenced Alerts
- [Alert]: Reason, expiration time

### Upcoming Events
- [Date]: Maintenance window for X
- [Date]: Deployment of Y

### Error Budget
- Current month: X% remaining
- Burn rate: Normal/Elevated/Critical

### Notes for Next On-Call
- [Any special considerations]
```

---

## Appendix A: Quick Reference Commands

### Kubernetes
```bash
# Get all pods in bad state
kubectl get pods --all-namespaces | grep -v Running | grep -v Completed

# Get events
kubectl get events --sort-by='.lastTimestamp' -A | tail -20

# Describe problem pod
kubectl describe pod <pod-name> -n <namespace>

# Get logs
kubectl logs <pod-name> -n <namespace> --tail=100 -f

# Restart deployment
kubectl rollout restart deployment/<name> -n <namespace>
```

### Docker Compose (AWS EC2)
```bash
# Status
docker-compose ps

# Logs
docker-compose logs -f --tail=100 <service>

# Restart
docker-compose restart <service>

# Full restart
docker-compose down && docker-compose up -d
```

### ArgoCD CLI
```bash
# Login
argocd login <argocd-server>

# List apps
argocd app list

# Sync app
argocd app sync <app-name>

# Rollback
argocd app rollback <app-name> <revision>
```

---

## Appendix B: Key Metrics to Monitor

### Platform Tools SLIs
| Tool       | SLI                     | Target  |
| ---------- | ----------------------- | ------- |
| ArgoCD     | Sync success rate       | > 99%   |
| Grafana    | Dashboard load time     | < 3s    |
| Prometheus | Scrape success rate     | > 99.5% |
| Vault      | Auth success rate       | > 99.9% |
| VPN        | Connection success rate | > 99.5% |
| WebConnect | Request success rate    | > 99.5% |
| WebConnect | P95 latency             | < 500ms |
| Bhair      | Request success rate    | > 99.5% |
| Bhair      | P95 latency             | < 500ms |
| EDUSUC     | Request success rate    | > 99.5% |
| EDUSUC     | P95 latency             | < 500ms |

### Alert Thresholds (Phase 1 vs Phase 2)
**Phase 1 (Now):** simple, actionable thresholds aligned to Tier 1 SLOs.  
**Phase 2 (Later):** introduce **burn-rate alerting** (multi-window) so we page based on “error budget burn” rather than raw thresholds.

**Phase 1 thresholds**
| Metric                       | Warning    | Critical   | Notes                                     |
| ---------------------------- | ---------- | ---------- | ----------------------------------------- |
| CPU Usage                    | > 70%      | > 90%      | Investigate saturation + scaling limits   |
| Memory Usage                 | > 75%      | > 90%      | Investigate leaks / limits / OOM risk     |
| Disk Usage                   | > 70%      | > 85%      | Prevent node/DB outages                   |
| **Error Rate (5xx / total)** | **> 0.3%** | **> 0.5%** | Aligned to Tier 1 Error Rate SLO (< 0.5%) |
| **Latency P95**              | > 500ms    | > 1000ms   | Aligned to Tier 1 latency targets         |

**Phase 2 burn-rate concept (enterprise standard)**
Burn-rate alerting pages when error budget is being consumed too quickly. A common pattern is:
- **Fast burn**: high severity (e.g., 5–10 minute window) to catch rapid outages
- **Slow burn**: lower severity (e.g., 1–6 hour window) to catch sustained degradation

---

**Document Version**: 1.2  
**Last Updated**: 2026-02-04  
**Owner**: Platform Engineering Team  
**Review Cycle**: Quarterly



