# CloudFormation Remediation Plan
_Created: 2026-06-21_

## Background

A full audit of `infra/cloudformation.yml` against the live application revealed **12 discrepancies** — 5 critical (will cause startup failure or broken auth), 4 significant (silent feature breakage), and 3 minor (operational quality).

**Stack**: Quarkus 3.17.5 + Kotlin + Java 21, `fast-jar` package, deployed as ECS Fargate.  
**Region**: `af-south-1`  
**Key config files**: `src/main/resources/application.properties`, `src/main/resources/application-prod.properties`

---

## Items to Fix

### CRITICAL — Fix before first deploy

---

#### C1 — Spring Boot profile var must be Quarkus profile var
**File**: `infra/cloudformation.yml`  
**Lines**: 314 (comment), 323 (env var)

**Current:**
```yaml
# ── Spring Boot app ──
- Name: SPRING_PROFILES_ACTIVE
  Value: prod
```

**Fix:**
```yaml
# ── Quarkus app ──
- Name: QUARKUS_PROFILE
  Value: prod
```

`SPRING_PROFILES_ACTIVE` is silently ignored by Quarkus. Without `QUARKUS_PROFILE=prod`, `application-prod.properties` is never loaded — the app runs with local defaults (wrong DB URL, wrong CORS origin, SQL logging on, etc.).

---

#### C2 — Wrong health check endpoint (`/actuator/health` → `/q/health`)
**Files**: `infra/cloudformation.yml` (lines 348, 398–399), `pom.xml`

`/actuator/health` is Spring Boot Actuator. Quarkus uses `/q/health` via `quarkus-smallrye-health`, which is **not currently in pom.xml**.

**Step 1** — Add health extension to `pom.xml`:
```xml
<dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-smallrye-health</artifactId>
</dependency>
```

**Step 2** — Fix container health check (CFN line 348):
```yaml
Command:
  - CMD-SHELL
  - !Sub "curl -f http://localhost:${AppPort}/q/health || exit 1"
```

**Step 3** — Fix ALB target group health check (CFN line 398):
```yaml
HealthCheckPath: /q/health
```

Without this, ECS will never mark the task healthy — the service will loop in deployment forever.

---

#### C3 — Redis env vars use wrong key names
**File**: `infra/cloudformation.yml`  
**Lines**: 325–327

**Current:**
```yaml
- Name: REDIS_HOST
  Value: localhost
- Name: REDIS_PORT
  Value: "6379"
```

**Fix:**
```yaml
- Name: REDIS_HOSTS
  Value: redis://localhost:6379
```

`application.properties` line 267 reads `${REDIS_HOSTS}`. The two vars the CFN passes (`REDIS_HOST`, `REDIS_PORT`) are never read. The app falls through to the hardcoded default `redis://localhost:6379`, which only works coincidentally because the sidecar is on localhost — this will silently break when switching to ElastiCache.

---

#### C4 — Database JDBC URL hardcoded; CFN env vars never consumed
**Files**: `infra/cloudformation.yml` (lines 329–332), `src/main/resources/application-prod.properties` (line 18)

`application-prod.properties` has:
```properties
quarkus.datasource.jdbc.url=jdbc:postgresql://prod-db-host:5432/doc-hyphen-db
```
CFN passes `DB_HOST`, `DB_PORT`, `DB_NAME` — none of which are referenced anywhere in the properties files. The app will attempt to connect to `prod-db-host` (unreachable).

**Fix option A** (preferred) — Replace the three separate env vars in CFN with a single Quarkus-compatible override. Quarkus maps env vars to config properties using `QUARKUS_` prefix with dots replaced by underscores:

```yaml
# Remove DB_HOST, DB_PORT, DB_NAME. Add:
- Name: QUARKUS_DATASOURCE_JDBC_URL
  Value: !Sub "jdbc:postgresql://${RDSInstance.Endpoint.Address}:${RDSInstance.Endpoint.Port}/docuhyphen"
```

Also remove the hardcoded line from `application-prod.properties` and replace with:
```properties
quarkus.datasource.jdbc.url=jdbc:postgresql://${DB_HOST:localhost}:${DB_PORT:5432}/${DB_NAME:docuhyphen}
```

**Fix option B** — Keep DB_HOST/PORT/NAME but update `application-prod.properties` to use them:
```properties
quarkus.datasource.jdbc.url=jdbc:postgresql://${DB_HOST}:${DB_PORT}/${DB_NAME}
```
Then remove the hardcoded `prod-db-host` line.

---

#### C5 — Missing `JWT_SECRET_ID` env var (auth will fail in production)
**File**: `infra/cloudformation.yml`  
**Related**: `src/main/resources/application-prod.properties` lines 27–29

`application-prod.properties` sets `app.security.jwt.secret-provider=aws` and reads `${JWT_SECRET_ID}`. CFN never passes this. JWT signing/verification will throw at startup or first auth call.

**Fix:**
1. Add a Secrets Manager secret for the JWT key to CFN:
```yaml
JWTSecret:
  Type: AWS::SecretsManager::Secret
  Properties:
    Name: !Sub ${AppName}/jwt-secret
    Description: JWT signing secret for DocuHyphen
    GenerateSecretString:
      PasswordLength: 64
      ExcludePunctuation: true
```

2. Grant the **execution role** access to it (for injection at container start):
```yaml
# In ECSTaskExecutionRole SecretsAccess policy, add:
- !Ref JWTSecret
```

3. Inject it as an env var:
```yaml
- Name: JWT_SECRET_ID
  Value: !Sub "${AppName}/jwt-secret"
```

---

### SIGNIFICANT — Fix before production traffic

---

#### S1 — Missing `AWS_REGION` env var
**File**: `infra/cloudformation.yml`

`application.properties` line 72 and `application-prod.properties` line 28 both read `${AWS_REGION}`. The AWS SDK v2 can infer region via the Fargate metadata endpoint, but the explicit property binding will be empty string.

```yaml
- Name: AWS_REGION
  Value: af-south-1
```

---

#### S2 — ECSTaskRole missing Secrets Manager permissions
**File**: `infra/cloudformation.yml` (ECSTaskRole, line 276)

The app calls `secretsmanager:GetSecretValue` at runtime to fetch:
- The JWT secret (via `app.security.jwt.aws-secret-id`)
- Org IdP secrets (via `app.secrets.org-idp.prefix`, default `docuhyphen/org/*`)

The task role only has S3 permissions. These calls will return `AccessDeniedException`.

Add policy to `ECSTaskRole`:
```yaml
- PolicyName: SecretsManagerAccess
  PolicyDocument:
    Version: "2012-10-17"
    Statement:
      - Effect: Allow
        Action:
          - secretsmanager:GetSecretValue
          - secretsmanager:DescribeSecret
          - secretsmanager:PutSecretValue
          - secretsmanager:RotateSecret
          - secretsmanager:UpdateSecret
        Resource:
          - !Sub "arn:aws:secretsmanager:af-south-1:${AWS::AccountId}:secret:${AppName}/*"
```

Note: `PutSecretValue`/`RotateSecret`/`UpdateSecret` are needed because `app.secrets.rotation.enabled=true` by default — the app rotates org IdP secrets itself.

---

#### S3 — ECSTaskRole missing SES permissions
**File**: `infra/cloudformation.yml` (ECSTaskRole, line 276)

`pom.xml` includes `software.amazon.awssdk:ses`. All transactional emails (sign-up, exchange notifications, etc.) are sent from application code via the AWS SES SDK. The task role has no SES permissions — all emails will silently fail.

Add policy to `ECSTaskRole`:
```yaml
- PolicyName: SESAccess
  PolicyDocument:
    Version: "2012-10-17"
    Statement:
      - Effect: Allow
        Action:
          - ses:SendEmail
          - ses:SendRawEmail
        Resource: "*"
```

Also verify the sending domain (`no-reply@docuhyphen.com` / `admin@securedocumentshare.co.za`) is verified in SES in `af-south-1`.

---

#### S4 — Kafka dependency with no broker infrastructure
**File**: `pom.xml` (line 167), `infra/cloudformation.yml`

`quarkus-messaging-kafka` is a compile dependency. If any `@Incoming`/`@Outgoing` channels are configured without a reachable broker, Quarkus will fail to start.

**Option A** — Add MSK to the stack (more infra, needed for production scale).  
**Option B** — For MVP, disable Kafka channels in `application-prod.properties`:
```properties
# Disable until MSK is provisioned
mp.messaging.incoming.<channel-name>.connector=disabled
mp.messaging.outgoing.<channel-name>.connector=disabled
```
**Action needed**: Grep the codebase for `@Incoming`/`@Outgoing` annotations to enumerate all channel names, then decide A or B.

---

### MINOR — Operational quality

---

#### M1 — `MinimumHealthyPercent: 0` causes downtime on every deploy
**File**: `infra/cloudformation.yml` line 457

With `DesiredCount: 1`, setting `MinimumHealthyPercent: 0` / `MaximumPercent: 100` avoids a "can't launch replacement because only 1 slot" deadlock — but it kills the running task before the new one is healthy. Every deploy = downtime.

For MVP this is acceptable. When `DesiredCount` is raised to 2+, change to:
```yaml
MinimumHealthyPercent: 100
MaximumPercent: 200
```

---

#### M2 — ECS tasks in public subnets with public IPs
**File**: `infra/cloudformation.yml` line 447–449

Fargate tasks have `AssignPublicIp: ENABLED` in `PublicSubnetA`. The security group restricts inbound to the ALB, so it's not exposed directly — but the ENI does get a public IP. Standard production posture is private subnets + NAT Gateway.

For MVP: acceptable. Track for hardening post-launch.

---

#### M3 — RDS password in plaintext CFN parameter
**File**: `infra/cloudformation.yml` lines 13–14, 197

`DBPassword` is a `NoEcho` parameter, but it's still passed as a string and appears in stack events. Consider using RDS-managed credentials (`ManageMasterUserPassword: true`) which auto-rotates via Secrets Manager and removes the password parameter entirely.

---

## Execution Order

```
C2-part1  Add quarkus-smallrye-health to pom.xml
C1        Fix QUARKUS_PROFILE env var + comment
C2-part2  Fix health check paths in CFN
C3        Fix REDIS_HOSTS env var
C4        Fix JDBC URL injection (update prod properties + CFN)
C5        Add JWTSecret resource + execution role grant + JWT_SECRET_ID env var
S1        Add AWS_REGION env var
S2        Add Secrets Manager policy to ECSTaskRole
S3        Add SES policy to ECSTaskRole
S4        Audit Kafka channels, disable or provision MSK
M1–M3     Post-MVP hardening
```

---

## Files That Need Changes

| File | Changes needed |
|------|---------------|
| `infra/cloudformation.yml` | C1, C2, C3, C4, C5, S1, S2, S3 |
| `pom.xml` | C2 (add quarkus-smallrye-health) |
| `src/main/resources/application-prod.properties` | C4 (parameterise JDBC URL) |
| Kotlin source (grep needed) | S4 (audit Kafka channels) |
