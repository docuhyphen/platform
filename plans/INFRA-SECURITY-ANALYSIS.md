# Infrastructure Security Analysis

Date: 2026-08-04

Scope: Static review of the files under `infra/`.

## Executive summary

The infrastructure configuration is not fully security-hardened. The review identified four high-risk and seven medium-risk findings. No obvious hardcoded passwords, AWS access keys, API tokens, or private keys were found.

This was a source-level review. Deployed AWS resources were not queried, so configuration drift and controls configured outside CloudFormation remain outside the scope of this report.

## High-risk findings

### 1. Production can run over plain HTTP

`CertificateArn` defaults to an empty value. When it is empty, the port 80 listener forwards traffic directly to the application instead of redirecting to HTTPS.

Evidence:

- `infra/cloudformation.yml:17`
- `infra/cloudformation.yml:608`

Impact: Authentication tokens, document data, and other sensitive traffic could be transmitted without transport encryption.

Recommendation: Require a certificate whenever `ApplicationEnabled=true`. Configure the port 80 listener to redirect unconditionally and reject a production deployment without a valid certificate ARN.

### 2. Container images are mutable and not pinned

The application uses the `latest` tag, Redis uses `redis:7-alpine`, and the ECR repository does not enable immutable tags. A tag can therefore resolve to a different image without a CloudFormation change.

Evidence:

- `infra/deploy.sh:84`
- `infra/cloudformation.yml:297`
- `infra/cloudformation.yml:548`

Impact: An overwritten or compromised tag could cause an unreviewed image to be deployed. Rollback and incident attribution are also unreliable when artifact identity is mutable.

Recommendation:

- Set `ImageTagMutability: IMMUTABLE` on the ECR repository.
- Publish a unique image tag for each commit or release.
- Deploy application images by digest where possible.
- Pin the Redis image to a reviewed digest.

Reference: [Amazon ECS task and container security best practices](https://docs.aws.amazon.com/AmazonECS/latest/developerguide/security-tasks-containers.html)

### 3. The application role controls all organization identity-provider secrets

The ECS task role can retrieve, create, replace, retag, alter version stages, and delete every secret below the organization secret prefix.

Evidence:

- `infra/cloudformation.yml:417`

Impact: A compromised application could retrieve every organization identity-provider credential, replace credentials to redirect authentication, or delete the credentials.

Recommendation:

- Remove permissions that are not required during normal request processing, particularly `DeleteSecret`, `UntagResource`, and version-stage manipulation.
- Add resource-tag and request-tag conditions that prevent access outside application-managed organization secrets.
- Separate provisioning or administrative secret operations from the normal application runtime role.
- Consider using recovery windows and explicit controls around secret deletion.

### 4. Deployment destinations are hardcoded without an AWS account safety check

Production bucket names and CloudFront distribution IDs are defaulted in the deployment script. The script obtains and logs the active account, but it does not verify that account before executing `aws s3 sync --delete` or invalidating CloudFront distributions.

Evidence:

- `infra/deploy.sh:12`
- `infra/deploy.sh:16`
- `infra/deploy.sh:278`
- `infra/deploy.sh:342`

Impact: An operator using unintended credentials could overwrite or delete frontend content in the wrong environment if those credentials have access.

Recommendation:

- Require deployment destinations explicitly or resolve them from CloudFormation outputs.
- Require an `EXPECTED_AWS_ACCOUNT_ID` and stop before mutation when the caller account does not match.
- Apply the same account guard to startup and shutdown scripts.
- Avoid production identifiers as implicit defaults for destructive synchronization operations.

## Medium-risk findings

### 5. The HTTPS listener uses the legacy CloudFormation default TLS policy

No `SslPolicy` is specified. AWS documents that an HTTPS listener created through CloudFormation defaults to `ELBSecurityPolicy-2016-08`.

Evidence:

- `infra/cloudformation.yml:626`

Impact: The listener can support older protocol and cipher combinations that are inappropriate for a modern production service.

Recommendation: Select an explicit current TLS policy that meets application compatibility requirements, such as a TLS 1.2 and TLS 1.3 policy.

Reference: [Security policies for an Application Load Balancer](https://docs.aws.amazon.com/elasticloadbalancing/latest/application/describe-ssl-policies.html)

### 6. Database TLS is not explicitly enforced

The JDBC URL does not specify an SSL mode, the PostgreSQL major version is not pinned, and no database parameter group explicitly enables `rds.force_ssl`.

Evidence:

- `infra/cloudformation.yml:268`
- `infra/cloudformation.yml:472`

Impact: The template does not guarantee encrypted, certificate-validated database connections across PostgreSQL engine versions and client behavior. The PostgreSQL JDBC default is `prefer`, which can fall back to an unencrypted connection.

Recommendation:

- Pin the supported PostgreSQL major version.
- Set `rds.force_ssl=1` through a database parameter group.
- Configure certificate validation, preferably `sslmode=verify-full`, and manage the RDS CA trust material.

Reference: [Using SSL with an Amazon RDS for PostgreSQL DB instance](https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/PostgreSQL.Concepts.General.SSL.html)

### 7. Private signing-key material is passed through process arguments

The deployment script retrieves the full signing secret into a shell variable and supplies the replacement secret through the `--secret-string` command-line argument.

Evidence:

- `infra/deploy.sh:127`
- `infra/deploy.sh:167`

Impact: Private-key material can be exposed through process inspection, command diagnostics, crash reporting, or deployment-host monitoring.

Recommendation:

- Do not retrieve the complete secret merely to determine whether initialization is required.
- Upload the protected temporary file using an AWS CLI file reference instead of placing secret content in the argument value.
- Apply restrictive file permissions explicitly and clear sensitive variables as soon as possible.

### 8. ECS tasks have public IP addresses and unrestricted outbound access

The ECS service runs tasks in public subnets with `AssignPublicIp: ENABLED`. The security groups omit explicit egress rules, which means the AWS default permits all outbound traffic.

Evidence:

- `infra/cloudformation.yml:185`
- `infra/cloudformation.yml:652`

Impact: Although inbound application access is limited to the ALB security group, a compromised task has broad outbound connectivity for command-and-control or data exfiltration.

Recommendation:

- Define the minimum necessary ECS security-group egress rules.
- Evaluate moving tasks into private subnets.
- Obtain explicit user approval before introducing any new paid AWS service or resource type required by a private networking design.

### 9. S3 does not explicitly reject insecure transport

The document and audit buckets block public access and enable encryption at rest, but neither has a bucket policy that denies requests made without TLS.

Evidence:

- `infra/cloudformation.yml:678`
- `infra/cloudformation.yml:708`

Impact: An authorized principal is not prevented at the resource-policy layer from using an insecure HTTP request.

Recommendation: Add bucket policies that deny requests when `aws:SecureTransport=false`, with appropriate handling for AWS service principals.

Reference: [Protecting Amazon S3 data in transit](https://docs.aws.amazon.com/AmazonS3/latest/userguide/UsingEncryptionInTransit.html)

### 10. Containers do not enforce a read-only root filesystem or capability reduction

The application Dockerfile uses a non-root user, which is a positive control. The ECS task definition does not, however, enforce a read-only root filesystem or explicitly remove unnecessary Linux capabilities.

Evidence:

- `infra/cloudformation.yml:453`

Impact: A successful application exploit has more opportunity to modify the container filesystem or misuse available operating-system capabilities.

Recommendation:

- Enable `ReadonlyRootFilesystem` after compatibility testing.
- Add explicit writable mounts only where necessary.
- Enable the container init process where appropriate.
- Drop unnecessary Linux capabilities.

Reference: [AWS Security Hub controls for Amazon ECS](https://docs.aws.amazon.com/securityhub/latest/userguide/ecs-controls.html)

### 11. Audit Object Lock uses Governance mode

The audit archive uses Object Lock Governance mode. The application task role cannot bypass the retention, but another sufficiently privileged AWS principal can.

Evidence:

- `infra/cloudformation.yml:725`

Impact: The archive does not provide the strongest AWS immutability guarantee against compromised administrative credentials or misuse by a privileged principal.

Recommendation: If regulatory-grade immutability is required, evaluate Compliance mode and tightly control retention changes. This decision must account for the fact that Compliance-mode objects cannot be removed before retention expires.

## Hardcoded configuration inventory

The following values are not secrets, but they are embedded as production defaults and should be reviewed for parameterization or environment-specific configuration:

- AWS regions
- Production domain names
- Website and web-app bucket names
- CloudFront distribution IDs
- Database name and username
- Application and database ports
- VPC and subnet CIDR ranges
- Database instance class and storage size
- ECS CPU and memory allocations
- Audit retention period
- Signing-key identifier `audit-rsa-1`

The fixed CloudFront distribution IDs and deployment buckets are the most concerning hardcoded items because deployment operations mutate those resources.

## Positive controls observed

The configuration already includes several useful security controls:

- RDS is not publicly accessible.
- RDS credentials are managed by Secrets Manager.
- RDS storage is encrypted.
- RDS deletion protection and snapshot retention are enabled.
- RDS ingress is restricted to the ECS security group.
- ECS application ingress is restricted to the ALB security group.
- S3 public access blocking is enabled.
- S3 encryption and versioning are enabled.
- The audit bucket uses Object Lock and omits delete permissions from the application role.
- ECR scan-on-push is enabled.
- Secrets Manager access for the primary application secrets is resource-scoped.
- The application image runs as a non-root user.
- Deployment temporary directories are cleaned through an exit trap.

## Coverage limitations

The CloudFormation template declares CloudFront-related parameters but does not define the website or web-app buckets, CloudFront distributions, origin access controls, DNS records, or CDN response-header policies. Their TLS posture, public access controls, logging, security headers, and origin restrictions therefore cannot be assessed from `infra/`.

The following dedicated scanners were not available in the local environment:

- Checkov
- cfn-lint
- Trivy
- Gitleaks
- ShellCheck
- Semgrep

Regex checks of the current infrastructure files and Git patch history found no committed credential or private-key material. This is useful evidence but is not equivalent to a complete secret scan or a review of deployed AWS state.

## Recommended remediation order

1. Remove the HTTP forwarding fallback and require a valid production certificate.
2. Replace mutable image tags with immutable release identifiers and digests.
3. Reduce runtime access to organization identity-provider secrets.
4. Add AWS account guards and remove hardcoded destructive deployment destinations.
5. Set an explicit modern ALB TLS policy and enforce verified database TLS.
6. Fix signing-key handling so private material is not passed through process arguments.
7. Restrict ECS egress and add S3 secure-transport policies.
8. Apply container hardening and decide whether audit retention requires Compliance mode.
