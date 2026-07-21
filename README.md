# DocuHyphen

DocuHyphen is a Quarkus and Kotlin backend with a React and Vite frontend. Local development also requires PostgreSQL and Redis. Document storage and application email use AWS S3 and AWS SES.

This guide uses PowerShell on Windows. Run commands from the repository root unless a step says otherwise.

## Production marketing website deploy

The public marketing website is deployed from `website/` to the production S3 bucket `docuhyphen-website` in `us-east-1`, then invalidated through CloudFront distribution `E3NCVYE325OBGH`.

### Recommended production deploy command

From the repository root:

```powershell
bash ./infra/deploy.sh --frontend
```

If `bash` is not available on your PowerShell `PATH`, use Git Bash directly:

```powershell
& "C:\Program Files\Git\bin\bash.exe" ./infra/deploy.sh --frontend
```

The deploy script now defaults to:

| Setting | Default value |
|---|---|
| AWS Region | `us-east-1` |
| Website bucket | `docuhyphen-website` |
| CloudFront distribution | `E3NCVYE325OBGH` |

For the marketing website, `--frontend`:

- Runs `npm ci` and `npm run build` inside `website/`
- Syncs `website/dist` to `s3://docuhyphen-website`
- Invalidates `E3NCVYE325OBGH`

If a matching CloudFormation stack exists, the script still supports stack-managed environments. If not, it falls back to the manual production bucket and CloudFront defaults above.

### Manual production deploy commands

Use this if you want to deploy the marketing site without the helper script:

```powershell
Set-Location C:\Users\Black\IdeaProjects\doc-hyphen\website
npm ci
npm run build
aws s3 sync dist s3://docuhyphen-website --delete --region us-east-1
aws cloudfront create-invalidation --distribution-id E3NCVYE325OBGH --paths "/*"
```

### Required AWS access for production website deploys

The signed-in AWS identity needs permission to:

- Read and write objects in `docuhyphen-website`
- Create CloudFront invalidations for distribution `E3NCVYE325OBGH`

Before a production deploy, confirm the active account and login:

```powershell
aws sts get-caller-identity --output table
aws s3api head-bucket --bucket docuhyphen-website --region us-east-1
```

### Production route handling

The marketing site uses pre-rendered route files such as `/security/index.html` and `/about/index.html`. Directly opening clean paths like `/security` or refreshing a route only works when CloudFront rewrites extensionless paths to the matching `index.html`.

The current infrastructure template includes a CloudFront Function for this behavior. If production CloudFront was created manually, make sure the live distribution is configured with equivalent clean-path rewriting before relying on direct route refreshes.

### Notes

- These commands deploy the public marketing site from `website/`, not the authenticated product frontend from `web-app/`.
- Pricing stays deployable but is intentionally marked `noindex,follow` in the built marketing site.
- Run production deploys from a clean or at least understood working tree so you do not ship unintended frontend changes.

## Local services and ports

| Service | Local address | Purpose |
|---|---|---|
| React frontend | <http://localhost:5173> | Browser application |
| Quarkus backend | <http://localhost:8080> | REST, WebSocket, and health endpoints |
| Quarkus Dev UI | <http://localhost:8080/q/dev/> | Development diagnostics |
| PostgreSQL | `localhost:5432` | Persistent application data |
| Redis | `localhost:6379` | Authentication, sessions, rate limits, and coordination |

## Prerequisites

Install:

- Git
- Java 21
- Node.js 20 or newer and npm
- Docker Desktop with Docker Compose v2
- AWS CLI v2 with support for `aws login`
- LibreOffice on the host if document conversion is tested outside the application container

Verify the tools:

```powershell
java -version
node --version
npm --version
docker --version
docker compose version
aws --version
```

The Maven wrapper downloads the project-compatible Maven version, so a separate Maven installation is not required.

## One-time AWS setup

Do not create or use root access keys. Each developer should use their own restricted AWS console identity and temporary browser-login credentials.

### Required AWS access

Local document storage uses:

```text
Bucket: docuhyphen-demo-documents
Region: us-east-1
```

The minimum S3 policy for normal document operations is:

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Sid": "DocuHyphenDevelopmentBucket",
      "Effect": "Allow",
      "Action": [
        "s3:GetBucketLocation",
        "s3:ListBucket"
      ],
      "Resource": "arn:aws:s3:::docuhyphen-demo-documents"
    },
    {
      "Sid": "DocuHyphenDevelopmentDocuments",
      "Effect": "Allow",
      "Action": [
        "s3:GetObject",
        "s3:PutObject"
      ],
      "Resource": "arn:aws:s3:::docuhyphen-demo-documents/*"
    }
  ]
}
```

Email sign-up, verification, notification, and recovery flows also require `ses:SendEmail` in `us-east-1`. The address configured by `QUARKUS_MAILER_FROM` must be a verified SES identity. If the AWS account is in the SES sandbox, recipient addresses must also be verified.

AWS Secrets Manager access is not required for basic startup. It is needed when testing organization identity-provider secret storage or optional AWS-backed audit key providers.

### Create the browser-login profile

```powershell
aws login --profile docuhyphen-local --region us-east-1
```

Complete the browser flow and wait for PowerShell to confirm that the profile was updated. Verify the identity and bucket:

```powershell
aws sts get-caller-identity --profile docuhyphen-local

aws s3api head-bucket `
  --bucket docuhyphen-demo-documents `
  --profile docuhyphen-local `
  --region us-east-1
```

No output from `head-bucket` means the bucket check succeeded. Do not use an identity whose ARN ends in `:root` for normal development.

Browser-login credentials are temporary. Repeat `aws login` when the session expires. The application uses the AWS SDK default credential provider and does not read access keys from project properties.

## Option 1: Quarkus dev mode on the host

This is the recommended backend development workflow. PostgreSQL and Redis run in containers, while Quarkus and Vite run on the host.

### 1. Configure the PowerShell session

```powershell
$env:AWS_PROFILE = "docuhyphen-local"
$env:AWS_CONFIG_DIRECTORY = "$env:USERPROFILE\.aws"
$env:FILE_STORAGE_AWS_BUCKET = "docuhyphen-demo-documents"
$env:FILE_STORAGE_AWS_REGION = "us-east-1"
$env:APP_EMAIL_SES_REGION = "us-east-1"
```

The bucket and Region variables match `application-local.properties`, but setting them explicitly makes the active configuration clear. `AWS_CONFIG_DIRECTORY` is used by Compose for its backend service and must be defined when Compose parses the file.

### 2. Start PostgreSQL and Redis

```powershell
docker compose up -d postgres redis
docker compose ps
```

Flyway applies database migrations automatically when Quarkus starts.

### 3. Start Quarkus

```powershell
.\mvnw.cmd quarkus:dev "-Dquarkus.profile=local"
```

The host process connects to:

```text
PostgreSQL: jdbc:postgresql://localhost:5432/docu-hyphen-postgres-db
Redis: redis://localhost:6379
S3: docuhyphen-demo-documents in us-east-1
```

### 4. Start the frontend

Open a second PowerShell window:

```powershell
Set-Location C:\path\to\doc-hyphen\web-app
$env:VITE_API_BASE_URL = "http://localhost:8080"
npm ci
npm run dev
```

Open <http://localhost:5173>. Run `npm ci` on the first setup and whenever `package-lock.json` changes. Later starts only require `npm run dev`.

## Option 2: Backend and dependencies in containers

This workflow runs packaged Quarkus, PostgreSQL, and Redis through Docker Compose. The Vite frontend remains on the host because the current application image contains the backend only.

### 1. Refresh AWS login and configure Compose

```powershell
aws login --profile docuhyphen-local --region us-east-1

$env:AWS_PROFILE = "docuhyphen-local"
$env:AWS_CONFIG_DIRECTORY = "$env:USERPROFILE\.aws"
$env:FILE_STORAGE_AWS_BUCKET = "docuhyphen-demo-documents"
$env:FILE_STORAGE_AWS_REGION = "us-east-1"
$env:APP_EMAIL_SES_REGION = "us-east-1"
```

Compose mounts the host `.aws` directory at `/home/appuser/.aws`. This lets the Java SDK inside the backend container use and refresh the browser-login session. Never copy `.aws`, access keys, secret keys, or session tokens into the repository or image.

### 2. Package the backend

The Dockerfile consumes `target/quarkus-app`:

```powershell
.\mvnw.cmd clean package "-DskipTests" "-DskipFrontend=true"
```

Omit `-DskipTests` when a full test run is desired.

### 3. Build and start containers

```powershell
docker compose up --build -d
docker compose ps
docker compose logs -f quarkus-app
```

Compose selects the `local` Quarkus profile and uses the service hostnames `postgres` and `redis`. Their health checks must pass before the backend starts.

### 4. Start the frontend

```powershell
Set-Location C:\path\to\doc-hyphen\web-app
$env:VITE_API_BASE_URL = "http://localhost:8080"
npm ci
npm run dev
```

Open <http://localhost:5173>.

### Stop or reset containers

Stop containers without deleting data:

```powershell
docker compose down
```

Delete containers and local PostgreSQL and Redis data:

```powershell
docker compose down -v
```

Deleting volumes permanently removes local development data.

## Optional local configuration

### Bootstrap the first application administrator

On an empty database, set this before the first backend startup:

```powershell
$env:APP_ADMIN_BOOTSTRAP_EMAIL = "developer@example.com"
```

The account with this email is promoted when no active application administrator exists. Leave the variable unset to disable promotion.

### Google OAuth

```powershell
$env:GOOGLE_CLIENT_ID = "your-google-client-id"
$env:GOOGLE_CLIENT_SECRET = "your-google-client-secret"
$env:GOOGLE_REDIRECT_URI = "http://localhost:8080/auth/oauth/GOOGLE/callback"
```

Register the same redirect URI in the Google OAuth client.

### Microsoft OAuth

```powershell
$env:MICROSOFT_CLIENT_ID = "your-microsoft-client-id"
$env:MICROSOFT_CLIENT_SECRET = "your-microsoft-client-secret"
$env:MICROSOFT_TENANT_ID = "common"
$env:MICROSOFT_REDIRECT_URI = "http://localhost:8080/auth/oauth/MICROSOFT/callback"
```

Register the same redirect URI in the Microsoft application registration. For containers, set optional variables before `docker compose up`; Compose passes them to the backend.

## Common environment variables

| Variable | Local default | Purpose |
|---|---|---|
| `AWS_PROFILE` | AWS SDK default profile | Selects the browser-login profile |
| `AWS_CONFIG_DIRECTORY` | None | Host `.aws` directory mounted by Compose |
| `FILE_STORAGE_AWS_BUCKET` | `docuhyphen-demo-documents` | S3 document bucket |
| `FILE_STORAGE_AWS_REGION` | `us-east-1` | S3 bucket Region |
| `APP_EMAIL_SES_REGION` | `us-east-1` | SES Region |
| `QUARKUS_MAILER_FROM` | `support@docuhyphen.com` | SES sender identity |
| `REDIS_HOSTS` | `redis://localhost:6379` | Redis connection string |
| `QUARKUS_DATASOURCE_JDBC_URL` | Local PostgreSQL URL | PostgreSQL connection string |
| `APP_ADMIN_BOOTSTRAP_EMAIL` | Empty | First application administrator bootstrap |
| `VITE_API_BASE_URL` | Empty | Frontend backend address |

Do not put credentials or production secrets into property files, `.env`, Docker images, or Git.

## Verification and tests

Backend tests:

```powershell
.\mvnw.cmd test "-DskipFrontend=true"
```

Frontend checks:

```powershell
Set-Location web-app
npx tsc --noEmit
npm test
```

Backend health:

```powershell
Invoke-RestMethod http://localhost:8080/q/health
```

## Troubleshooting

### AWS `NoCredentials` or `Unable to locate credentials`

```powershell
aws login --profile docuhyphen-local --region us-east-1
aws sts get-caller-identity --profile docuhyphen-local
$env:AWS_PROFILE = "docuhyphen-local"
```

Restart the backend after changing environment variables. For containers, confirm `AWS_CONFIG_DIRECTORY` contains `config` and `login/cache`.

### S3 `AccessDenied`

Confirm the logged-in identity has the S3 policy shown above and that the bucket policy does not deny the request.

### S3 redirect, wrong Region, or missing bucket

```powershell
$env:FILE_STORAGE_AWS_BUCKET = "docuhyphen-demo-documents"
$env:FILE_STORAGE_AWS_REGION = "us-east-1"
```

### SES rejection

Confirm the sender is verified in SES `us-east-1`, the identity has `ses:SendEmail`, and sandbox recipient restrictions are satisfied.

### PostgreSQL connection failure

```powershell
docker compose ps postgres
docker compose logs postgres
```

The database host is `localhost` for host Quarkus and `postgres` inside Compose.

### Redis connection failure

```powershell
docker compose ps redis
docker compose logs redis
```

Redis is `redis://localhost:6379` for host Quarkus and `redis://redis:6379` inside Compose.

### Reset local state

```powershell
docker compose down -v
docker compose up -d postgres redis
```

This recreates empty PostgreSQL and Redis services. Flyway rebuilds the schema on the next backend startup.
