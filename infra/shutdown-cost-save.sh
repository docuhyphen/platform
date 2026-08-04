#!/usr/bin/env bash

set -euo pipefail

APP_NAME="${APP_NAME:-docuhyphen}"
STACK_NAME="${STACK_NAME:-${APP_NAME}-prod}"
REGION="${AWS_REGION:-af-south-1}"
WEBSITE_DOMAIN="${WEBSITE_DOMAIN:-www.docuhyphen.com}"
WEB_APP_DOMAIN="${WEB_APP_DOMAIN:-app.docuhyphen.com}"
API_DOMAIN="${API_DOMAIN:-api.docuhyphen.com}"
SES_REGION="${SES_REGION:-us-east-1}"
ECS_CLUSTER="${ECS_CLUSTER:-${APP_NAME}-cluster}"
ECS_SERVICE="${ECS_SERVICE:-${APP_NAME}-service}"
RDS_INSTANCE_ID="${RDS_INSTANCE_ID:-${APP_NAME}-postgres}"
SHUTDOWN_WAIT="${SHUTDOWN_WAIT:-false}"
SHUTDOWN_PAUSE_ON_ERROR="${SHUTDOWN_PAUSE_ON_ERROR:-true}"
TEMPLATE_FILE="$(dirname "$0")/cloudformation.yml"
PROJECT_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
LOGO_FILE="${PROJECT_ROOT}/src/main/resources/logo.txt"

log() { printf '%s\n' "$*"; }
log_section() { printf '\n[%s] %s\n' "$(date '+%Y-%m-%d %H:%M:%S')" "$*"; }
fail() { printf 'Error: %s\n' "$*" >&2; exit 1; }

should_pause_on_error() {
  if [[ "$SHUTDOWN_PAUSE_ON_ERROR" == "true" ]]; then
    return 0
  fi

  [[ "$SHUTDOWN_PAUSE_ON_ERROR" == "auto" && -t 0 && -t 2 ]]
}

print_completion_logo() {
  if [[ -f "$LOGO_FILE" ]]; then
    printf '\n'
    cat "$LOGO_FILE"
    printf '\n'
  fi
}

finish() {
  local status="$?"

  if [[ "$status" -ne 0 ]] && should_pause_on_error; then
    print_completion_logo
    printf '\nCost-save shutdown failed with exit code %s.\n' "$status" >&2
    read -r -p "Press Enter to close this window..." _
  fi

  exit "$status"
}
trap finish EXIT

require_command() {
  command -v "$1" >/dev/null 2>&1 || fail "Required command not found: $1"
}

stack_exists() {
  aws cloudformation describe-stacks \
    --stack-name "$STACK_NAME" \
    --region "$REGION" \
    >/dev/null 2>&1
}

ecs_service_exists() {
  aws ecs describe-services \
    --cluster "$ECS_CLUSTER" \
    --services "$ECS_SERVICE" \
    --region "$REGION" \
    --query "services[?status!='INACTIVE'] | length(@)" \
    --output text 2>/dev/null | grep -q '^1$'
}

rds_instance_status() {
  aws rds describe-db-instances \
    --db-instance-identifier "$RDS_INSTANCE_ID" \
    --region "$REGION" \
    --query "DBInstances[0].DBInstanceStatus" \
    --output text 2>/dev/null || true
}

stack_output() {
  local output_key="$1"
  aws cloudformation describe-stacks \
    --stack-name "$STACK_NAME" \
    --region "$REGION" \
    --query "Stacks[0].Outputs[?OutputKey=='${output_key}'].OutputValue" \
    --output text
}

shutdown_stack_application() {
  stack_exists || fail "CloudFormation stack ${STACK_NAME} was not found in ${REGION}"

  local image_uri
  image_uri="$(stack_output ECRRepositoryURI)"
  [[ -n "$image_uri" && "$image_uri" != "None" ]] || fail "Could not resolve ECRRepositoryURI from stack outputs"

  log_section "Disabling application resources in CloudFormation"
  aws cloudformation deploy \
    --template-file "$TEMPLATE_FILE" \
    --stack-name "$STACK_NAME" \
    --region "$REGION" \
    --capabilities CAPABILITY_NAMED_IAM \
    --parameter-overrides \
      AppName="$APP_NAME" \
      ECRImageURI="${image_uri}:latest" \
      DBUsername="docuhyphen" \
      AppPort="8080" \
      CertificateArn="${CERTIFICATE_ARN:-}" \
      CloudFrontCertificateArn="${CLOUDFRONT_CERTIFICATE_ARN:-}" \
      WebsiteDomainName="$WEBSITE_DOMAIN" \
      WebAppDomainName="$WEB_APP_DOMAIN" \
      ApiDomainName="$API_DOMAIN" \
      SesRegion="$SES_REGION" \
      AppAdminBootstrapEmail="${APP_ADMIN_BOOTSTRAP_EMAIL:-}" \
      ApplicationEnabled="false" \
    --no-fail-on-empty-changeset
}

require_command aws

AWS_ACCOUNT_ID="$(aws sts get-caller-identity --query Account --output text)"
AWS_CALLER_ARN="$(aws sts get-caller-identity --query Arn --output text)"

log_section "Starting cost-save shutdown"
log "AWS account: ${AWS_ACCOUNT_ID}"
log "AWS caller: ${AWS_CALLER_ARN}"
log "AWS region: ${REGION}"
log "Stack name: ${STACK_NAME}"
log "ECS cluster: ${ECS_CLUSTER}"
log "ECS service: ${ECS_SERVICE}"
log "RDS instance: ${RDS_INSTANCE_ID}"

if ecs_service_exists; then
  log_section "Stopping backend ECS tasks"
  aws ecs update-service \
    --cluster "$ECS_CLUSTER" \
    --service "$ECS_SERVICE" \
    --desired-count 0 \
    --region "$REGION" \
    --output text \
    --query "service.serviceName"

  if [[ "$SHUTDOWN_WAIT" == "true" ]]; then
    log "Waiting for ECS service to become stable"
    aws ecs wait services-stable \
      --cluster "$ECS_CLUSTER" \
      --services "$ECS_SERVICE" \
      --region "$REGION"
  fi
else
  log "ECS service was not found or is already inactive; skipping ECS shutdown"
fi

shutdown_stack_application

rds_status="$(rds_instance_status)"
if [[ -z "$rds_status" || "$rds_status" == "None" ]]; then
  log "RDS instance was not found; skipping RDS shutdown"
elif [[ "$rds_status" == "stopped" ]]; then
  log "RDS instance is already stopped"
elif [[ "$rds_status" == "stopping" ]]; then
  log "RDS instance is already stopping"
else
  log_section "Stopping RDS"
  log "Current RDS status: ${rds_status}"
  aws rds stop-db-instance \
    --db-instance-identifier "$RDS_INSTANCE_ID" \
    --region "$REGION" \
    --output text \
    --query "DBInstance.DBInstanceIdentifier"

  if [[ "$SHUTDOWN_WAIT" == "true" ]]; then
    log "Waiting for RDS instance to stop"
    aws rds wait db-instance-stopped \
      --db-instance-identifier "$RDS_INSTANCE_ID" \
      --region "$REGION"
  fi
fi

log_section "Cost-save shutdown requested"
log "ApplicationEnabled=false removes the ALB path and ECS service from the stack."
log "Website and web app hosting resources were not changed."
log "RDS may automatically start again after the AWS stop limit expires."
print_completion_logo
