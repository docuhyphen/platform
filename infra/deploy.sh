#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────────────
# DocuHyphen, Production deployment script
# Region: af-south-1 (Cape Town)
#
# Usage:
#   First deploy:   ./infra/deploy.sh
#   Update stack:   ./infra/deploy.sh          (same command, CloudFormation
#                                               detects it's an update)
#   Deploy image:   ./infra/deploy.sh --image  (build + push Docker only)
#   Full deploy:    ./infra/deploy.sh --full   (build + push + update stack + invalidate)
# ─────────────────────────────────────────────────────────────────────────────
set -euo pipefail

# ── Config ────────────────────────────────────────────────────────────────────
APP_NAME="docuhyphen"
STACK_NAME="${APP_NAME}-prod"
REGION="af-south-1"
AWS_ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
ECR_URI="${AWS_ACCOUNT_ID}.dkr.ecr.${REGION}.amazonaws.com/${APP_NAME}"
IMAGE_TAG="${ECR_URI}:latest"
TEMPLATE_FILE="$(dirname "$0")/cloudformation.yml"

# ── Helpers ───────────────────────────────────────────────────────────────────
log()  { echo "▶ $*"; }
ok()   { echo "✅ $*"; }
fail() { echo "❌ $*" >&2; exit 1; }

# ── Step 1: Build & push Docker image to ECR ─────────────────────────────────
push_image() {
  log "Logging in to ECR..."
  aws ecr get-login-password --region "$REGION" \
    | docker login --username AWS --password-stdin "${AWS_ACCOUNT_ID}.dkr.ecr.${REGION}.amazonaws.com"

  log "Building Docker image..."
  docker build -t "$IMAGE_TAG" "$(dirname "$0")/.."

  log "Pushing image to ECR: $IMAGE_TAG"
  docker push "$IMAGE_TAG"
  ok "Image pushed."
}

# ── Step 2: Deploy / update CloudFormation stack ─────────────────────────────
deploy_stack() {
  # Prompt for DB password if not set as env var
  if [[ -z "${DB_PASSWORD:-}" ]]; then
    read -rsp "Enter RDS master password: " DB_PASSWORD
    echo
  fi

  log "Deploying CloudFormation stack: $STACK_NAME"
  aws cloudformation deploy \
    --template-file "$TEMPLATE_FILE" \
    --stack-name "$STACK_NAME" \
    --region "$REGION" \
    --capabilities CAPABILITY_NAMED_IAM \
    --parameter-overrides \
      AppName="$APP_NAME" \
      ECRImageURI="$IMAGE_TAG" \
      DBPassword="$DB_PASSWORD" \
      DBUsername="docuhyphen" \
      AppPort="8080" \
      CertificateArn="${CERTIFICATE_ARN:-}" \
    --no-fail-on-empty-changeset

  ok "Stack deployed."
}

# ── Step 3: Force ECS to pull the new image ───────────────────────────────────
restart_ecs() {
  CLUSTER="${APP_NAME}-cluster"
  SERVICE="${APP_NAME}-service"
  log "Forcing ECS service redeployment..."
  aws ecs update-service \
    --cluster "$CLUSTER" \
    --service "$SERVICE" \
    --force-new-deployment \
    --region "$REGION" \
    --output text --query 'service.serviceName'
  ok "ECS redeployment triggered."
}

# ── Step 4: Deploy frontend to S3 + invalidate CloudFront ────────────────────
deploy_frontend() {
  BUCKET="${APP_NAME}-website-prod"
  DIST_ID=$(aws cloudformation describe-stacks \
    --stack-name "$STACK_NAME" \
    --region "$REGION" \
    --query "Stacks[0].Outputs[?OutputKey=='CloudFrontURL'].OutputValue" \
    --output text | grep -oP '(?<=https://)([^.]+)')

  log "Building frontend..."
  (cd "$(dirname "$0")/../website" && npm ci && npm run build)

  log "Syncing to S3: s3://${BUCKET}"
  aws s3 sync "$(dirname "$0")/../website/dist" "s3://${BUCKET}" \
    --delete \
    --region "$REGION"

  log "Invalidating CloudFront cache..."
  CF_DIST_ID=$(aws cloudformation describe-stacks \
    --stack-name "$STACK_NAME" \
    --region "$REGION" \
    --query "Stacks[0].Outputs[?OutputKey=='CloudFrontURL'].OutputValue" \
    --output text | sed 's|https://||' | cut -d. -f1)

  # Get actual distribution ID
  ACTUAL_DIST_ID=$(aws cloudfront list-distributions \
    --query "DistributionList.Items[?contains(Origins.Items[].DomainName, '${APP_NAME}-website-prod')].Id" \
    --output text)

  if [[ -n "$ACTUAL_DIST_ID" ]]; then
    aws cloudfront create-invalidation \
      --distribution-id "$ACTUAL_DIST_ID" \
      --paths "/*"
    ok "CloudFront cache invalidated."
  else
    log "Warning: Could not find CloudFront distribution ID. Skipping invalidation."
  fi

  ok "Frontend deployed."
}

# ── Print stack outputs ───────────────────────────────────────────────────────
print_outputs() {
  log "Stack outputs:"
  aws cloudformation describe-stacks \
    --stack-name "$STACK_NAME" \
    --region "$REGION" \
    --query "Stacks[0].Outputs" \
    --output table
}

# ── Entrypoint ────────────────────────────────────────────────────────────────
MODE="${1:-}"

case "$MODE" in
  --image)
    push_image
    restart_ecs
    ;;
  --frontend)
    deploy_frontend
    ;;
  --full)
    push_image
    deploy_stack
    restart_ecs
    deploy_frontend
    print_outputs
    ;;
  "")
    deploy_stack
    print_outputs
    ;;
  *)
    echo "Usage: $0 [--image | --frontend | --full]"
    echo ""
    echo "  (no flag)    Deploy / update CloudFormation stack only"
    echo "  --image      Build + push Docker image, restart ECS"
    echo "  --frontend   Build + deploy website to S3, invalidate CloudFront"
    echo "  --full       Do everything: image + stack + frontend"
    exit 1
    ;;
esac

