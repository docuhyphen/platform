#!/usr/bin/env bash

set -euo pipefail

APP_NAME="${APP_NAME:-docuhyphen}"
REGION="${AWS_REGION:-af-south-1}"
ECS_CLUSTER="${ECS_CLUSTER:-${APP_NAME}-cluster}"
ECS_SERVICE="${ECS_SERVICE:-${APP_NAME}-service}"
RDS_INSTANCE_ID="${RDS_INSTANCE_ID:-${APP_NAME}-postgres}"
STARTUP_WAIT="${STARTUP_WAIT:-true}"
STARTUP_PAUSE_ON_ERROR="${STARTUP_PAUSE_ON_ERROR:-auto}"
PROJECT_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
LOGO_FILE="${PROJECT_ROOT}/src/main/resources/logo.txt"

log() { printf '%s\n' "$*"; }
log_section() { printf '\n[%s] %s\n' "$(date '+%Y-%m-%d %H:%M:%S')" "$*"; }
fail() { printf 'Error: %s\n' "$*" >&2; exit 1; }

should_pause_on_error() {
  if [[ "$STARTUP_PAUSE_ON_ERROR" == "true" ]]; then
    return 0
  fi

  [[ "$STARTUP_PAUSE_ON_ERROR" == "auto" && -t 0 && -t 2 ]]
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
    printf '\nStartup failed with exit code %s.\n' "$status" >&2
    read -r -p "Press Enter to close this window..." _
  fi

  exit "$status"
}
trap finish EXIT

require_command() {
  command -v "$1" >/dev/null 2>&1 || fail "Required command not found: $1"
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

require_command aws

AWS_ACCOUNT_ID="$(aws sts get-caller-identity --query Account --output text)"
AWS_CALLER_ARN="$(aws sts get-caller-identity --query Arn --output text)"

log_section "Starting platform startup"
log "AWS account: ${AWS_ACCOUNT_ID}"
log "AWS caller: ${AWS_CALLER_ARN}"
log "AWS region: ${REGION}"
log "ECS cluster: ${ECS_CLUSTER}"
log "ECS service: ${ECS_SERVICE}"
log "RDS instance: ${RDS_INSTANCE_ID}"

rds_status="$(rds_instance_status)"
if [[ -z "$rds_status" || "$rds_status" == "None" ]]; then
  log "RDS instance was not found; skipping RDS startup"
elif [[ "$rds_status" == "available" ]]; then
  log "RDS instance is already available"
elif [[ "$rds_status" == "starting" ]]; then
  log "RDS instance is already starting"
else
  log_section "Starting RDS"
  log "Current RDS status: ${rds_status}"
  aws rds start-db-instance \
    --db-instance-identifier "$RDS_INSTANCE_ID" \
    --region "$REGION" \
    --output text \
    --query "DBInstance.DBInstanceIdentifier"
fi

if [[ "$STARTUP_WAIT" == "true" ]]; then
  log "Waiting for RDS instance to become available"
  aws rds wait db-instance-available \
    --db-instance-identifier "$RDS_INSTANCE_ID" \
    --region "$REGION"
fi

if ecs_service_exists; then
  log_section "Starting backend ECS tasks"
  aws ecs update-service \
    --cluster "$ECS_CLUSTER" \
    --service "$ECS_SERVICE" \
    --desired-count 1 \
    --region "$REGION" \
    --output text \
    --query "service.serviceName"

  if [[ "$STARTUP_WAIT" == "true" ]]; then
    log "Waiting for ECS service to become stable"
    aws ecs wait services-stable \
      --cluster "$ECS_CLUSTER" \
      --services "$ECS_SERVICE" \
      --region "$REGION"
  fi
else
  log "ECS service was not found or is inactive; run ./deploy.sh --backend if it was removed from the stack"
fi

log_section "Platform startup requested"
log "Website hosting resources were not changed."
log "If the backend service is inactive because of shutdown-cost-save.sh, redeploy it with ./deploy.sh --backend."
print_completion_logo
