#!/usr/bin/env bash

set -euo pipefail

APP_NAME="${APP_NAME:-docuhyphen}"
STACK_NAME="${STACK_NAME:-${APP_NAME}-prod}"
REGION="${AWS_REGION:-af-south-1}"
WEBSITE_DOMAIN="${WEBSITE_DOMAIN:-www.docuhyphen.com}"
API_DOMAIN="${API_DOMAIN:-api.docuhyphen.com}"
SES_REGION="${SES_REGION:-us-east-1}"
TEMPLATE_FILE="$(dirname "$0")/cloudformation.yml"
PROJECT_ROOT="$(cd "$(dirname "$0")/.." && pwd)"

log() { printf '%s\n' "$*"; }
fail() { printf 'Error: %s\n' "$*" >&2; exit 1; }

TEMP_DIR=""
cleanup() {
  if [[ -n "${TEMP_DIR:-}" && -d "$TEMP_DIR" ]]; then
    rm -rf -- "$TEMP_DIR"
  fi
}
trap cleanup EXIT

require_command() {
  command -v "$1" >/dev/null 2>&1 || fail "Required command not found: $1"
}

require_command aws
AWS_ACCOUNT_ID="$(aws sts get-caller-identity --query Account --output text)"
ECR_URI="${AWS_ACCOUNT_ID}.dkr.ecr.${REGION}.amazonaws.com/${APP_NAME}"
IMAGE_TAG="${ECR_URI}:latest"

stack_exists() {
  aws cloudformation describe-stacks \
    --stack-name "$STACK_NAME" \
    --region "$REGION" \
    >/dev/null 2>&1
}

deploy_stack() {
  local application_enabled="$1"

  log "Deploying stack ${STACK_NAME} with ApplicationEnabled=${application_enabled}"
  aws cloudformation deploy \
    --template-file "$TEMPLATE_FILE" \
    --stack-name "$STACK_NAME" \
    --region "$REGION" \
    --capabilities CAPABILITY_NAMED_IAM \
    --parameter-overrides \
      AppName="$APP_NAME" \
      ECRImageURI="$IMAGE_TAG" \
      DBUsername="docuhyphen" \
      AppPort="8080" \
      CertificateArn="${CERTIFICATE_ARN:-}" \
      CloudFrontCertificateArn="${CLOUDFRONT_CERTIFICATE_ARN:-}" \
      WebsiteDomainName="$WEBSITE_DOMAIN" \
      ApiDomainName="$API_DOMAIN" \
      SesRegion="$SES_REGION" \
      AppAdminBootstrapEmail="${APP_ADMIN_BOOTSTRAP_EMAIL:-}" \
      ApplicationEnabled="$application_enabled" \
    --no-fail-on-empty-changeset
}

bootstrap_stack() {
  if stack_exists; then
    return
  fi

  log "Creating infrastructure without the ECS service"
  deploy_stack false
}

populate_audit_signing_secret() {
  local secret_id="${APP_NAME}/audit-archive-signing-key"
  local current_secret
  current_secret="$(aws secretsmanager get-secret-value \
    --secret-id "$secret_id" \
    --region "$REGION" \
    --query SecretString \
    --output text)"

  if [[ "$current_secret" == *'"privateKeyPem":"-----BEGIN PRIVATE KEY-----'* ]] || \
     [[ "$current_secret" == *'"privateKeyPem": "-----BEGIN PRIVATE KEY-----'* ]]; then
    return
  fi

  require_command openssl
  require_command node

  TEMP_DIR="$(mktemp -d)"

  openssl genpkey \
    -algorithm RSA \
    -pkeyopt rsa_keygen_bits:2048 \
    -out "$TEMP_DIR/private.pem"
  openssl pkey \
    -in "$TEMP_DIR/private.pem" \
    -pubout \
    -out "$TEMP_DIR/public.pem"

  node -e '
    const fs = require("fs");
    const privateKeyPem = fs.readFileSync(process.argv[1], "utf8");
    const publicKeyPem = fs.readFileSync(process.argv[2], "utf8");
    fs.writeFileSync(process.argv[3], JSON.stringify({
      keyId: "audit-rsa-1",
      privateKeyPem,
      publicKeyPem,
      historicalPublicKeys: {},
    }));
  ' "$TEMP_DIR/private.pem" "$TEMP_DIR/public.pem" "$TEMP_DIR/signing-secret.json"

  aws secretsmanager put-secret-value \
    --secret-id "$secret_id" \
    --secret-string "file://$TEMP_DIR/signing-secret.json" \
    --region "$REGION" \
    >/dev/null

  log "Populated the audit archive signing secret"
  cleanup
  TEMP_DIR=""
}

push_image() {
  require_command docker

  log "Building the Quarkus application"
  (cd "$PROJECT_ROOT" && ./mvnw package -DskipTests)

  log "Logging in to ECR"
  aws ecr get-login-password --region "$REGION" \
    | docker login --username AWS --password-stdin "${AWS_ACCOUNT_ID}.dkr.ecr.${REGION}.amazonaws.com"

  log "Building and pushing ${IMAGE_TAG}"
  docker build -t "$IMAGE_TAG" "$PROJECT_ROOT"
  docker push "$IMAGE_TAG"
}

restart_ecs() {
  aws ecs update-service \
    --cluster "${APP_NAME}-cluster" \
    --service "${APP_NAME}-service" \
    --force-new-deployment \
    --region "$REGION" \
    --output text \
    --query 'service.serviceName'
}

stack_output() {
  local output_key="$1"
  aws cloudformation describe-stacks \
    --stack-name "$STACK_NAME" \
    --region "$REGION" \
    --query "Stacks[0].Outputs[?OutputKey=='${output_key}'].OutputValue" \
    --output text
}

deploy_frontend() {
  local bucket
  local distribution_id
  bucket="$(stack_output WebsiteBucketName)"
  distribution_id="$(stack_output CloudFrontDistributionId)"

  log "Building the website"
  (cd "$PROJECT_ROOT/website" && npm ci && npm run build)

  aws s3 sync "$PROJECT_ROOT/website/dist" "s3://${bucket}" \
    --delete \
    --region "$REGION"

  aws cloudfront create-invalidation \
    --distribution-id "$distribution_id" \
    --paths "/*" \
    >/dev/null
}

print_outputs() {
  aws cloudformation describe-stacks \
    --stack-name "$STACK_NAME" \
    --region "$REGION" \
    --query "Stacks[0].Outputs" \
    --output table
}

case "${1:-}" in
  --image)
    stack_exists || fail "Create the stack first with --full"
    push_image
    restart_ecs
    ;;
  --frontend)
    stack_exists || fail "Create the stack first with --full"
    deploy_frontend
    ;;
  --full)
    bootstrap_stack
    populate_audit_signing_secret
    push_image
    deploy_stack true
    restart_ecs
    deploy_frontend
    print_outputs
    ;;
  "")
    stack_exists || fail "First deployment must use --full"
    populate_audit_signing_secret
    deploy_stack true
    print_outputs
    ;;
  *)
    fail "Usage: $0 [--image | --frontend | --full]"
    ;;
esac
