# Browser, Infrastructure, and Secrets Security Remediation

Date: 2026-10-01

Status: Review complete. Remediation pending. No application or infrastructure changes applied.

## Scope and interpretation

Reviewed the current working tree: browser entry points, token storage, cookies, CSRF and CORS, evidence previews, production configuration, Docker packaging, CloudFormation, deployment scripts, IAM permissions, database credentials, and secret providers. Also queried npm advisories for both frontend production dependency graphs.

This is a source and local verification review. No authenticated AWS inventory, secret retrieval, production requests, deployment, or exploitation was performed. A repository configuration gap does not establish that an externally managed live resource has the same gap. Severity describes the stated risk and prerequisites, not proof that an attacker has exploited it.

Thirteen remediation findings: **2 high and 11 medium**. Several are hardening or operational security weaknesses that require another compromise, configuration choice, or memory pressure to cause harm. Additional lower-priority observations are listed separately.

This document updates the assessment in [the earlier infrastructure review](C:/Users/Black/IdeaProjects/doc-hyphen/plans/INFRA-SECURITY-ANALYSIS.md). Source locations below refer to the current working tree.

## Findings summary

| ID | Severity | Finding | Evidence confidence |
|---|---|---|---|
| BROWSER-1 | Medium | Browser security headers are not managed in repository configuration | Source gap; live CloudFront policy unverified |
| BROWSER-2 | Medium | Host-only CSRF cookie cannot be read by the separately hosted app | Source confirmed; synthetic cookie-scope reproduction |
| BROWSER-3 | Medium | Marketing site dependency graph still contains reported vulnerabilities | npm confirmed; application exploitability not demonstrated |
| INFRA-1 | High | API deployment permits plaintext HTTP and inherits a legacy TLS policy | Template confirmed; live listeners unverified |
| INFRA-2 | High | Application connects using the RDS master account | Template and datasource configuration confirmed |
| INFRA-3 | Medium | Runtime role can modify and delete every organization's identity-provider secret | IAM confirmed; application organization guards are present |
| INFRA-4 | Medium | Database connection does not require server identity verification | JDBC and template confirmed; live encryption unverified |
| INFRA-5 | Medium | Deploy script places the audit private signing key in process arguments | Source confirmed |
| INFRA-6 | Medium | Secret rotation does not update running credentials and cached signing keys | Source confirmed; rotation failure inferred |
| INFRA-7 | Medium | Deployment artifacts use mutable tags and dependency installations | Source confirmed |
| INFRA-8 | Medium | Deployment does not verify the expected account and static-site destinations | Source confirmed |
| INFRA-9 | Medium | Data buckets lack a repository-managed deny for insecure transport | Template confirmed; external policies unverified |
| INFRA-10 | Medium | Redis eviction can discard security counters and refresh state | Policy and consumers confirmed; pressure attack not reproduced |

## Browser findings

### BROWSER-1: Browser security headers are not managed

**Evidence:** [web app entry page](C:/Users/Black/IdeaProjects/doc-hyphen/web-app/index.html:14), [CloudFront configuration helper](C:/Users/Black/IdeaProjects/doc-hyphen/infra/configure-cloudfront-spa-fallback.mjs:18), and [deployment configuration](C:/Users/Black/IdeaProjects/doc-hyphen/infra/deploy.sh:282). No global CSP, framing restriction, HSTS, Referrer-Policy, Permissions-Policy, or CloudFront response headers policy was found. The helper updates error responses only. Evidence downloads have their own sandbox policy, which does not protect the main application.

**Risk:** A future script injection or compromised dependency has fewer browser restrictions. If the live app lacks a framing policy, it also lacks an explicit clickjacking defense. Access and ID tokens in sessionStorage increase the consequence of script execution. No production raw HTML or eval sink was identified in the searched frontend code, and no XSS exploit was demonstrated.

**Remediation:**

- Inspect the existing distributions, then manage their response header policies in the deployment configuration.
- Introduce CSP in report-only mode and enforce it after exercising login, document previews, PDF workers, realtime connections, and Fluent UI styles. Restrict script sources, API connections, object sources, base URI, form actions, and frame ancestors.
- Hash or externalize the inline theme script. Account for runtime style injection without allowing arbitrary scripts.
- Add HSTS after HTTPS is mandatory, nosniff, an explicit Referrer-Policy, and a narrowly scoped Permissions-Policy. Choose includeSubDomains only after checking all affected hosts.
- Set `frame-ancestors 'none'` unless embedding is a documented product requirement. This directive must be delivered as an HTTP header. [MDN framing policy reference](https://developer.mozilla.org/en-US/docs/Web/HTTP/Reference/Headers/Content-Security-Policy/frame-ancestors).

**Acceptance:** Capture actual response headers on the app entry page, a deep link, an error response, and relevant API responses. An untrusted iframe cannot embed the app. An unauthorized script is blocked. Supported previews, styles, and OAuth flows still work.

### BROWSER-2: CSRF cookie does not cross the app/API host boundary

**Evidence:** [production API URL](C:/Users/Black/IdeaProjects/doc-hyphen/web-app/.env.production:1), [deployment app origin](C:/Users/Black/IdeaProjects/doc-hyphen/infra/cloudformation.yml:554), [cookie builder](C:/Users/Black/IdeaProjects/doc-hyphen/src/main/kotlin/com/docuhyphen/app/api/service/auth/TokenIssuanceService.kt:118), [cookie reader](C:/Users/Black/IdeaProjects/doc-hyphen/web-app/src/services/csrfToken.ts:21), [request interceptor](C:/Users/Black/IdeaProjects/doc-hyphen/web-app/src/services/apiClient.ts:63), and [production CSRF setting](C:/Users/Black/IdeaProjects/doc-hyphen/src/main/resources/application-prod.properties:34).

The API is on `api.docuhyphen.com`, while the frontend is on `app.docuhyphen.com`. The CSRF cookie has no Domain attribute. The frontend exclusively obtains the header value from document.cookie. The token responses inspected do not provide an alternative CSRF value.

**Reproduction:** A synthetic cookie jar storing the current cookie attributes on the API host produced `apiHasCsrfCookie=true` and `appCanReadCsrfCookie=false`. This models cookie scope, not a production browser session. Exposing Set-Cookie through CORS cannot solve this because browsers hide that response header. [MDN cookie behavior](https://developer.mozilla.org/en-US/docs/Web/HTTP/Reference/Headers/Set-Cookie).

**Impact:** With production CSRF protection enabled, refresh and sign-out requests cannot supply the required matching header in the declared split-host deployment. This is an availability and session-management defect. It is not evidence that production CSRF checks are disabled.

**Remediation:** Provide the CSRF value through a response body or dedicated token resource accessible only to the approved app origin, including a bootstrap path for a new tab. Alternatively, serve API requests under the app origin using the existing CloudFront infrastructure. Keep the refresh cookie host-only, HttpOnly, Secure, and Strict. Keep CSRF and origin validation enabled. Avoid broadening cookie Domain to every sibling host as a shortcut.

**Acceptance:** A real browser with the declared app/API origins can sign in, refresh after access-token expiry, bootstrap a new tab, and sign out. Missing or incorrect CSRF headers and unapproved origins remain rejected. Test cookie scope as part of the deployment configuration.

### BROWSER-3: Marketing site dependencies were not included in the earlier refresh

**Evidence:** [marketing dependencies](C:/Users/Black/IdeaProjects/doc-hyphen/website/package.json:20) and [lockfile](C:/Users/Black/IdeaProjects/doc-hyphen/website/package-lock.json). Locked versions include Axios 1.13.2, React Router and React Router DOM 7.12.0, form-data 4.0.5, and follow-redirects 1.15.11.

`npm audit --omit=dev` reported five affected package entries: four high and one moderate. These are package severity counts, not five demonstrated application exploits. The web app's corresponding audit reported zero vulnerabilities.

**Applicability:** The marketing site uses declarative routes and build-time StaticRouter rendering. Framework Mode and RSC server advisories do not establish a deployed server vulnerability here. Node HTTP adapter issues likewise do not automatically affect browser Axios. The routing package is in the affected range for a conditional open-redirect/XSS advisory, but this review did not find an attacker-controlled redirect path. [Maintainer advisory](https://github.com/remix-run/react-router/security/advisories/GHSA-jjmj-jmhj-qwj2), [Framework Mode applicability](https://github.com/remix-run/react-router/security/advisories/GHSA-chx6-hx7r-mcp5).

**Remediation:** Upgrade the affected direct dependencies and regenerate the marketing lockfile. Remove Axios if a full usage check confirms it is unused. Audit both frontend projects in the same security gate and document any context-specific exclusions.

**Acceptance:** Clean `npm ci`, build and prerender succeed. Navigation and static routes still work. Both production dependency audits have no unresolved applicable advisories. Audit results are reviewed for actual runtime applicability.

## Infrastructure and secrets findings

### INFRA-1: API transport protection is optional

**Evidence:** [optional certificate parameter](C:/Users/Black/IdeaProjects/doc-hyphen/infra/cloudformation.yml:17), [HTTP listener](C:/Users/Black/IdeaProjects/doc-hyphen/infra/cloudformation.yml:726), [HTTPS listener](C:/Users/Black/IdeaProjects/doc-hyphen/infra/cloudformation.yml:744), and [deploy parameter](C:/Users/Black/IdeaProjects/doc-hyphen/infra/deploy.sh:138).

With no certificate, the listener forwards public HTTP traffic to the application. The HTTPS listener omits SslPolicy. CloudFormation defaults to ELBSecurityPolicy-2016-08, which supports TLS 1.0 and 1.1. [AWS ALB policy reference](https://docs.aws.amazon.com/elasticloadbalancing/latest/application/describe-ssl-policies.html).

**Impact:** A permitted deployment can expose credentials, bearer tokens, and documents to interception in transit. The app's configured HTTPS links and Secure cookies do not disable the HTTP listener. Legacy TLS acceptance is a separate medium-strength hardening concern within this finding.

**Remediation:** Reject application-enabled deployments without a valid certificate. Port 80 should redirect to HTTPS or refuse traffic, never forward to the application. Explicitly select a supported TLS 1.2/1.3 policy. Add deployment validation and the browser HSTS control in BROWSER-1.

**Acceptance:** Deployment validation rejects an enabled application without a certificate. Public HTTP never returns application content. TLS 1.0/1.1 negotiation is rejected on actual listeners.

### INFRA-2: Runtime database principal is the RDS master user

**Evidence:** [master-user configuration](C:/Users/Black/IdeaProjects/doc-hyphen/infra/cloudformation.yml:293), [ECS database secrets](C:/Users/Black/IdeaProjects/doc-hyphen/infra/cloudformation.yml:635), and [production datasource](C:/Users/Black/IdeaProjects/doc-hyphen/src/main/resources/application-prod.properties:19). Both DB_USERNAME and DB_PASSWORD come from RDSInstance.MasterUserSecret.

**Impact:** SQL injection or backend credential compromise would gain database administration privileges beyond normal application data access. The application and migration processes are not separated by database principal in the reviewed configuration.

**Remediation:** Provision a dedicated application role with only required table, sequence, and function privileges. Use a separate migration principal for schema changes. Keep the master credential out of the runtime container. Store application credentials in the existing Secrets Manager service and design rotation together with INFRA-6. [AWS PostgreSQL privileged-role reference](https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/Appendix.PostgreSQL.CommonDBATasks.Roles.rds_superuser.html).

**Acceptance:** The application role cannot create roles, grant administrative membership, alter unrelated schemas, or perform migration DDL. Application operations still succeed. The migration principal is unavailable to the running application.

### INFRA-3: Tenant secret lifecycle has no IAM recovery boundary

**Evidence:** [runtime secret policy](C:/Users/Black/IdeaProjects/doc-hyphen/infra/cloudformation.yml:469) permits read, write, stage updates, tagging, and deletion for the entire organization-secret prefix. [Application retirement validation](C:/Users/Black/IdeaProjects/doc-hyphen/src/main/kotlin/com/docuhyphen/app/api/service/identity/OrganizationIdpSecretLifecycleService.kt:303) requires a 7 to 30 day recovery window, but the IAM statement does not enforce it.

**Impact:** A compromised runtime role can replace or delete multiple organizations' identity-provider credentials and bypass the application's recoverable-retirement rule. Organization authorization checks exist in the application, so this is a compromise-containment issue, not a demonstrated tenant authorization bypass.

**Remediation:** Define required actions individually. Enforce recoverable deletion and required ownership tags with supported IAM conditions. Limit unneeded lifecycle actions in the normal request-serving role. If stronger separation is needed, use a dedicated ECS task and IAM role within existing service types. Preserve legitimate configured lifecycle operations.

**Acceptance:** IAM policy simulation permits the documented lifecycle, rejects permanent deletion and out-of-prefix resources, and verifies creation/tagging behavior. Organization authorization tests still reject a caller acting on another organization.

### INFRA-4: Database connection does not authenticate the server

**Evidence:** [JDBC URL](C:/Users/Black/IdeaProjects/doc-hyphen/infra/cloudformation.yml:525) has no sslmode or trust configuration. [RDS configuration](C:/Users/Black/IdeaProjects/doc-hyphen/infra/cloudformation.yml:292) does not pin the engine major version or explicitly set a TLS-enforcement parameter group.

**Impact:** The client does not require certificate and hostname verification. Do not infer that current live traffic is plaintext: RDS PostgreSQL 15 and later require SSL by default. Client verification remains a separate missing control. [AWS PostgreSQL SSL behavior](https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/PostgreSQL.Concepts.General.SSL.html).

**Remediation:** Use sslmode=verify-full with the applicable RDS CA trust bundle. Manage the supported engine version and explicitly require encrypted connections in the database parameter configuration. Include CA renewal in operations.

**Acceptance:** Application connections use TLS and verify hostname and trust. Connections with an untrusted CA, wrong hostname, or no encryption fail. Database upgrades preserve these checks.

### INFRA-5: Private signing key enters the deployment process argument list

**Evidence:** [signing-secret upload](C:/Users/Black/IdeaProjects/doc-hyphen/infra/deploy.sh:200) reads the generated secret into a variable and passes it with `--secret-string`. The shell expands the complete JSON, including privateKeyPem, into the AWS CLI process arguments.

**Impact:** Local process inspection or command telemetry on the deployment host can capture the private audit signing key. Redirecting output does not conceal process arguments. The temporary directory and cleanup are positive controls but do not address this channel.

**Remediation:** Pass the secret through AWS CLI file input, using a restrictive temporary file and the correct path conversion on Windows. Ensure secret material is absent from arguments and shell tracing. Preserve cleanup on all exit paths. Check whether existing deployment-host telemetry retained key material before deciding whether rotation is necessary.

**Acceptance:** With synthetic key material, a stub or process inspection sees only the file path in argv. Temp files are restricted and removed after success and failure. Logs contain no key content.

### INFRA-6: Rotation does not update running consumers

**Evidence:** [RDS managed password](C:/Users/Black/IdeaProjects/doc-hyphen/infra/cloudformation.yml:294) and [startup-injected database credentials](C:/Users/Black/IdeaProjects/doc-hyphen/infra/cloudformation.yml:635). [JWT root caching](C:/Users/Black/IdeaProjects/doc-hyphen/src/main/kotlin/com/docuhyphen/app/api/service/config/ConfigurationService.kt:139) and [derived signing-key caching](C:/Users/Black/IdeaProjects/doc-hyphen/src/main/kotlin/com/docuhyphen/app/api/service/auth/TokenSigningKeyProvider.kt:49) also have no refresh path. Audit signing and vault providers cache their loaded material.

**Impact:** RDS rotates managed master passwords every seven days by default, while ECS environment secret values stay fixed until a new task starts. New database connections can consequently fail after rotation. Changing a JWT secret in Secrets Manager does not make running tasks stop accepting the old signing key. These outcomes follow from the configuration; no live failure was observed. [RDS rotation behavior](https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/rds-secrets-manager.html), [ECS injection behavior](https://docs.aws.amazon.com/AmazonECS/latest/developerguide/secrets-envvar-secrets-manager.html).

**Remediation:** Define and implement a rotation process for each secret type. Database rotation must update consumers and connection pools. JWT rotation must update both caches consistently, define overlap, and support emergency rejection. Archive signing must retain historical verification keys. Vault rotation needs key versioning and a migration or rewrapping strategy before changing its master key; a simple cache refresh can make existing ciphertext unreadable. Use existing ECS, Secrets Manager, and deployment mechanisms.

**Acceptance:** In a disposable environment, rotate synthetic database and JWT credentials while exercising new connections and token validation. Verify the intended overlap and emergency cutoff. Confirm old archives remain verifiable and vault data remains decryptable. Document task restart requirements and failure recovery.

### INFRA-7: Deployment inputs are mutable

**Evidence:** [latest application tag](C:/Users/Black/IdeaProjects/doc-hyphen/infra/deploy.sh:87), [Redis image tag](C:/Users/Black/IdeaProjects/doc-hyphen/infra/cloudformation.yml:666), [base image](C:/Users/Black/IdeaProjects/doc-hyphen/Dockerfile:22), [ECR repository](C:/Users/Black/IdeaProjects/doc-hyphen/infra/cloudformation.yml:314), and [frontend install choices](C:/Users/Black/IdeaProjects/doc-hyphen/infra/deploy.sh:440).

**Impact:** The same deployment reference can resolve to different container contents. Frontend deployment can reuse an arbitrary installed dependency tree or repair it with npm install rather than installing the lockfile. ScanOnPush exists, but this script does not check scan findings before deployment. Rollback and security evidence are less reliable.

**Remediation:** Build once with a unique release tag and deploy the resolved digest. Make release tags immutable, pin reviewed base/sidecar digests, and retain required rollback images. Use clean npm ci for both frontends. Gate deployment on applicable dependency and ECR scan results with an explicit exception process.

**Acceptance:** Repeating a release uses identical digests and lockfile dependencies. Release tags cannot be overwritten. An applicable blocking scan result prevents deployment. Previous release digests remain available.

### INFRA-8: Deployment destinations are not verified before destructive sync

**Evidence:** [default bucket/distribution identifiers](C:/Users/Black/IdeaProjects/doc-hyphen/infra/deploy.sh:14), [caller lookup](C:/Users/Black/IdeaProjects/doc-hyphen/infra/deploy.sh:84), [website sync](C:/Users/Black/IdeaProjects/doc-hyphen/infra/deploy.sh:392), and [app sync](C:/Users/Black/IdeaProjects/doc-hyphen/infra/deploy.sh:460).

**Impact:** The script reports the active account but does not compare it to a required expected account. Site buckets and distribution IDs can remain configured independently of the stack. A sufficiently privileged operator using the wrong configuration can overwrite content and delete files from an unintended authorized bucket.

**Remediation:** Require an expected account for shared-environment deployment. Resolve or validate all destinations, bucket ownership, distribution origin, and app identity before uploads or updates. Use S3 expected-owner checks where supported and verify the target prefix. Fail before mutations on any mismatch.

**Acceptance:** Stubbed wrong-account, wrong-owner, and wrong-origin inputs result in no upload, delete, or distribution update. Correct destinations pass. Validate destructive-sync scope in a disposable bucket.

### INFRA-9: Data buckets do not explicitly require HTTPS

**Evidence:** [document bucket](C:/Users/Black/IdeaProjects/doc-hyphen/infra/cloudformation.yml:913), [preview bucket](C:/Users/Black/IdeaProjects/doc-hyphen/infra/cloudformation.yml:937), [profile bucket](C:/Users/Black/IdeaProjects/doc-hyphen/infra/cloudformation.yml:962), and [audit bucket](C:/Users/Black/IdeaProjects/doc-hyphen/infra/cloudformation.yml:990). No bucket policies denying insecure transport are declared for them.

**Impact:** An otherwise authorized client can use plaintext HTTP. Public-access blocking and server-side encryption do not enforce encryption during transport. The application SDK normally uses HTTPS, so this is an enforcement gap rather than proof of observed plaintext requests. [AWS S3 transport guidance](https://docs.aws.amazon.com/AmazonS3/latest/userguide/security-best-practices.html).

**Remediation:** Add explicit insecure-transport denies for bucket and object resources. Account for AWS service-to-service access as appropriate. Inspect the separately managed site buckets without assuming their policies are absent.

**Acceptance:** Authorized HTTPS operations still work; equivalent HTTP operations are rejected. Application, audit, and deployment integrations continue to work. Verify effective policies on the actual buckets.

### INFRA-10: Redis eviction can remove security state early

**Evidence:** [Redis memory and eviction policy](C:/Users/Black/IdeaProjects/doc-hyphen/infra/cloudformation.yml:675) configures 384 MB with allkeys-lru. [Rate-limit counters](C:/Users/Black/IdeaProjects/doc-hyphen/src/main/kotlin/com/docuhyphen/app/api/service/auth/RedisRateLimiter.kt:88) and [backoff blocks](C:/Users/Black/IdeaProjects/doc-hyphen/src/main/kotlin/com/docuhyphen/app/api/service/auth/RedisRateLimiter.kt:133) rely on those keys. Refresh token lookups use Redis, and missing records are rejected.

**Impact:** Under memory pressure, counters and blocks can disappear before their expiry, resetting abuse budgets. Refresh state can disappear and force users to sign in again. AOF is enabled but no persistent task volume is declared, so task replacement does not preserve it. This is not proof that evicting Redis entries makes revoked user sessions valid; database session validation remains present.

**Remediation:** Protect security state from eviction and define explicit, restrictive behavior when Redis writes fail. Bound key growth and monitor memory/rejections with existing logging and monitoring. If ordinary cache entries need eviction, separate their Redis process from security state within existing infrastructure. Document the deliberate session behavior on task replacement. Do not introduce ElastiCache or another service without approval.

**Acceptance:** A bounded local memory-pressure test cannot silently reset protected rate-limit blocks. Failed counter writes cannot allow requests without a budget check. Refresh lookup loss fails closed, session revocation remains effective, and task replacement behavior is documented.

## Other observations and verification boundaries

- **Token storage:** Access and ID tokens remain readable by same-origin JavaScript in [AuthContext](C:/Users/Black/IdeaProjects/doc-hyphen/web-app/src/context/AuthContext.tsx:108). Prefer memory-only access tokens with a working refresh bootstrap. A larger cookie-session architecture needs separate design and CSRF coverage. Storage changes reduce exfiltration opportunities but cannot make an XSS-compromised page safe to use.
- **Credentialed CORS:** [The deployment](C:/Users/Black/IdeaProjects/doc-hyphen/infra/cloudformation.yml:556) trusts both the marketing and app origins. Narrow credentialed API access to origins that actually need it. Current CSRF origin validation checks the app base URL, so this review does not claim the marketing origin can bypass it.
- **Containers and egress:** The app runs as a non-root user. Tasks still have public IPs, unrestricted default egress, and writable root filesystems. Ingress is restricted to the ALB, so this is not an internet-open task finding. Review readonly roots with explicit writable conversion/temp paths and required egress. Any network redesign must preserve the existing service-cost constraints.
- **Sensitive caching:** Several token responses lack explicit no-store; document previews/downloads vary in cache handling. Review actual CloudFront cache behaviors and set no-store for token-bearing responses. No shared-cache disclosure was demonstrated.
- **Microsoft policy:** [Deployment flags](C:/Users/Black/IdeaProjects/doc-hyphen/infra/cloudformation.yml:560) explicitly allow multiple tenants and disable the EDOV requirement. Verify that this is the intended identity policy and preserve the existing validation of issuer, signature, audience, and subject. These flags alone do not establish account takeover.
- **Audit retention:** Governance Object Lock is bypassable by suitably privileged principals. The app role lacks retention-bypass/delete permission. Confirm the administrative threat model before selecting irreversible Compliance retention; do not change it automatically.
- **Secret scan:** Current tracked environment files contain development or placeholder-looking settings. The targeted current-tree scan found no AWS access-key or actual PEM private-key pattern; the only PEM marker hit was the deployment script's detection string. Secret values were withheld from review output. Git history, live credential validity, developer credential stores, and deployment-host logs were not audited. This is not a complete secret-leak clearance.
- **Rendering:** No production dangerous raw HTML/eval sink was found by the frontend searches. Evidence preview types are detected server-side and limited to PDF and raster images; HTML and SVG are excluded. Original response security headers do not travel with frontend-created blob URLs, so keep this allowlist and verify preview behavior whenever it changes.

## Verification performed

| Check | Result |
|---|---|
| Source/configuration review | Completed against current working tree |
| Synthetic host-only CSRF cookie scope | API can receive cookie; app cannot read it |
| Existing CSRF helper and evidence service tests | 2 files, 12 tests passed |
| Web app production npm audit | 0 reported vulnerabilities |
| Marketing production npm audit | 5 affected package entries, 4 high and 1 moderate; applicability reviewed above |
| Live AWS, production response headers, live secret rotation | Not performed |

Local audit/test output is in [web app audit](C:/Users/Black/IdeaProjects/doc-hyphen/target/browser-security-npm-audit.json), [marketing audit](C:/Users/Black/IdeaProjects/doc-hyphen/target/website-security-npm-audit.json), and [test log](C:/Users/Black/IdeaProjects/doc-hyphen/target/browser-security-tests.log). These files are ignored build artifacts and may be removed by a clean build.

## Remediation order

1. **Transport and privilege:** INFRA-1 and INFRA-2. Validate actual listeners and database roles before rollout.
2. **Working browser boundary:** BROWSER-2, BROWSER-1, and BROWSER-3. Keep CSRF enabled while correcting token delivery; upgrade both frontend dependency graphs.
3. **Secret handling and rotation:** INFRA-5, INFRA-6, and INFRA-3. Define recovery and key compatibility before rotating archival/vault material.
4. **Repeatable deployments:** INFRA-7 and INFRA-8.
5. **Storage and security-state enforcement:** INFRA-4, INFRA-9, and INFRA-10.

Use existing AWS services. No new service type is required by this plan. Each item remains open until its acceptance checks pass. Re-evaluate severity if live configuration proves an external control already closes a gap. No commits, pushes, secret rotations, or deployments were performed.
