# Plan: Production Multi-IdP Authentication with Per-Org Credentials (v4)

## Overview

This v4 plan preserves all v3 goals and expands security hardening for production OAuth/OIDC at scale. The platform remains the sole session authority for all sign-in methods, issues its own access/refresh tokens, and never exposes IdP tokens to browsers. JIT provisioning remains enabled with platform-managed per-organization user caps (including unlimited tiers), and directory lookup remains explicit on-demand only with no bulk import flow.

This version adds strict OIDC token validation, atomic refresh rotation race safety, device identity trust boundaries, explicit IdP/global-logout mismatch handling, CSRF protections for cookie-backed refresh/logout, secrets rotation runbooks, stronger admin safeguards with immutable audit, anti-enumeration controls, JIT role-floor protections, and mandatory rate limiting plus abuse detection.

---

## Decisions

| Concern | Decision |
|---|---|
| Session/token authority | App issues and validates its own access/refresh tokens for all sign-in methods |
| IdP token exposure | IdP tokens remain server-side only; never passed to browser |
| Org client secret storage | AWS Secrets Manager; DB stores ARN/secret reference only |
| Domain-to-org conflicts | Interactive org selection |
| Org user admission | JIT provision with strict minimum role floor (`ORG_MEMBER`) |
| User cap guard rail | Per-organization cap enforced from platform billing tier (platform-managed only) |
| Session expiry policy | Per-org configurable token/session policy with platform min/max guardrails |
| Active-session deprovisioning | Enforce on refresh and request paths; deny access when user/org membership is inactive |
| IdP global logout behavior | Treated as external signal only; local platform revocation remains authoritative |
| Revocation strategy | Per-session family revocation + optional `sessionVersion` global invalidation |
| Directory strategy | Explicit on-demand lookup only (add-to-group, sharing recipient); no bulk import |
| OIDC validation standard | Mandatory provider-agnostic and provider-specific ID-token validation checklist |
| Refresh rotation semantics | Atomic compare-and-swap rotation with bounded grace and replay kill-switch |
| Device trust model | `deviceId` is metadata only, never an auth factor |
| CSRF model | Cookie-backed refresh/logout requires CSRF token + origin checks |
| Admin controls | Dual-control safeguards for sensitive actions and immutable append-only audit |
| Enumeration defense | Uniform responses, query thresholds, and privacy-preserving lookup behavior |
| Abuse controls | Mandatory endpoint rate limits and risk-based abuse detection |

---

## Architecture Summary

### Sign-In Discovery

1. User submits email.
2. Backend resolves verified domains to org(s).
3. Outcomes:
   - `ORG_FOUND`: route to org IdP + show INTERNAL fallback.
   - `MULTIPLE_ORGS`: show org picker before redirect.
   - `NO_ORG`: show INTERNAL + platform external providers.

### OAuth Callback (Unified)

1. Validate signed state (`flow`, `orgIdpConfigId`, nonce, exp).
2. Resolve runtime OAuth credentials (org-specific or platform default).
3. Exchange authorization code server-side.
4. Validate OIDC token checklist (issuer/audience/nonce/exp/nbf/iat/alg/kid/signature/tenant-domain constraints).
5. Resolve identity by provider subject or verified email.
6. Apply JIT with role floor (`ORG_MEMBER`) and platform-managed user-cap enforcement.
7. Create/update `user_session` for device context.
8. Issue platform token set bound to `sessionId`.
9. Store refresh token chain metadata and return platform tokens only.

### Session Lifecycle and Revocation

1. Access tokens remain short-lived.
2. Refresh is the primary revocation and policy gate.
3. Rotation is atomic with race-safe grace semantics.
4. Request-time checks still enforce active user/org/session.
5. IdP global logout is not equivalent to platform logout; platform exposure is bounded by short access TTL + refresh-time revocation.

---

## Step-by-Step Implementation

### Step 1: Extend Data Model

#### `OrganizationIdentityProviderConfig`
Add/ensure:
- `provider`, `clientId`, `clientSecretRef`, `tenantId`, `isActive`
- `scopes`
- `accessTokenExpiryMinutes`, `refreshTokenExpiryDays`, `maxSessionDurationHours`
- `oidcIssuer`, `allowedAudiences`, `allowedAlgs`, `requiredClaims`
- timestamps and audit actor fields

#### `OrganizationSubscriptionPolicy` (platform-managed)
Add/ensure:
- `organizationId`
- `tierCode`
- `maxUsers` (`null` = unlimited)
- timestamps and change reason metadata

#### `AppUser`
Add/ensure:
- `sessionVersion` (optional global invalidation)
- `deprovisionedAt`
- `roleSource` and `roleAssignedAt` for JIT traceability

#### `user_session`
Add/ensure:
- `sessionId`, `userId`, `organizationId`
- `deviceId`, `deviceName`, `ipAddress`, `userAgent`, `lastSeenAt`
- `expiresAt`, `revokedAt`, `revocationReasonCode`, `revokedByUserId`, `isActive`
- optional `riskFlags`

#### `refresh_token` (new/extended)
Add/ensure:
- `userSessionId`, `familyId`, `tokenHash`, `rotatedFromTokenId`
- `issuedAt`, `expiresAt`, `graceUntil`, `consumedAt`
- `revokedAt`, `revocationReasonCode`
- uniqueness and transition constraints for atomic rotation

#### `audit_event` (immutable)
Add/ensure append-only event stream:
- `actorId`, `actorRole`, `action`, `targetType`, `targetId`
- `before`, `after`, `reason`, `requestId`, `timestamp`
- tamper-evidence fields (`eventHash`, `prevEventHash` or external WORM sink reference)

### Step 2: Secrets Management and Rotation Runbook

1. Extend secrets services for create, rotate, resolve, disable, and retire.
2. Enforce versioned secret references and staged activation.
3. Define rotation cadence (periodic + emergency).
4. Define runbook (prepare, validate, activate, monitor, rollback, retire).
5. Audit every secret lifecycle action immutably.

### Step 3: Runtime OAuth/OIDC Credentials and Validation

1. Refactor provider strategy to accept runtime credentials and issuer metadata.
2. Implement strict OIDC ID-token validation checklist:
   - JWKS signature verification with key rotation handling,
   - `iss` exact match,
   - `aud` contains configured client ID,
   - `azp` validation for multi-audience,
   - `exp`/`nbf`/`iat` with clock-skew bounds,
   - nonce correlation,
   - algorithm allowlist,
   - required claim checks,
   - provider-specific tenant/domain checks.
3. Fail closed on validation ambiguity or metadata failure.

### Step 4: Signed OAuth State and Replay Protection

1. Keep signed, expiring state payload.
2. Bind state to provider/org context and flow intent.
3. Enforce single-use nonce with TTL and replay audit events.

### Step 5: Sign-In Lookup API Hardening

1. Preserve discovery outcomes (`ORG_FOUND`, `MULTIPLE_ORGS`, `NO_ORG`).
2. Return minimal metadata and avoid user-existence leakage.
3. Add request throttling and uniform response timing.
4. Track suspicious discovery patterns.

### Step 6: OAuth Callback, JIT, and Token Issuance

1. Validate state and OIDC token checklist before user resolution.
2. Resolve/link/create user with deterministic precedence.
3. Enforce platform-managed org `maxUsers`.
4. Apply JIT role floor safety:
   - default `ORG_MEMBER`,
   - no elevated role grants from IdP claims unless explicit allowlist mapping exists.
5. Create/update `user_session`; treat `deviceId` as metadata only.
6. Issue platform tokens with `sessionId` and optional `sessionVersion`.
7. Never return IdP tokens to frontend.

### Step 6A: Refresh and Request-Time Enforcement

Before refresh issuance, validate:
- active user,
- active org and membership,
- active unrevoked session,
- session version match (if enabled).

On failure:
- revoke relevant sessions/families,
- set revocation reason,
- return `401`.

Request filter must re-check active user/session after JWT verification.

### Step 6B: Atomic Refresh Rotation with Race Safety

1. Use atomic consume-and-rotate transitions.
2. Mint exactly one successor token per successful refresh.
3. Apply short grace to prior token.
4. Detect out-of-grace reuse -> revoke family + session + security alert.

### Step 6C: Logout Controls and CSRF Protection

1. Keep this-device and all-devices logout endpoints.
2. Require CSRF controls for cookie-backed refresh/logout:
   - `SameSite` cookie policy,
   - CSRF token,
   - `Origin`/`Referer` checks,
   - strict CORS credential policy.

### Step 6D: IdP Sign-Out Mismatch and Exposure Bounds

1. Document mismatch clearly.
2. Bound exposure via short access TTL + strict refresh checks + optional `sessionVersion` invalidation.
3. Surface user/admin messaging on propagation behavior.

### Step 6E: Authentication Auditability and Process Logging

1. Emit structured auth-process events for discovery, callback, JIT, token issuance, refresh, logout, and revocation.
2. Include correlation fields: `requestId`, `sessionId`, `organizationId`, `actorId`, outcome, reason code, and timestamp.
3. Redact secrets, tokens, and sensitive identity attributes before persistence or export.
4. Route auth-process events to immutable audit storage and alert on high-risk failure patterns.

### Step 7: Admin APIs and Safeguards

1. Preserve ORG_ADMIN IdP/domain/session-policy APIs.
2. Preserve platform-admin tier/cap APIs.
3. Add high-impact action safeguards:
   - least privilege,
   - step-up authentication,
   - optional dual approval for mass-impact operations.
4. Record immutable audits with reason and before/after snapshots.

### Step 8: On-Demand Directory Resolution (No Bulk Import) with Anti-Enumeration

1. Keep explicit-only lookup paths (group add, recipient resolution).
2. Prohibit bulk import endpoints.
3. Add anti-enumeration controls:
   - minimum query lengths,
   - result caps,
   - normalized responses,
   - per-actor/org throttles,
   - privacy-safe logging.

### Step 9: Frontend and UX Updates

1. Preserve sign-in/sign-up and org selection behavior.
2. Show read-only platform-managed user-cap indicators.
3. Add user/admin session-management views.
4. Show clear messages for deprovision/revocation/security sign-out.

### Step 10: Third-Party Application Integration Readiness

1. Preserve `APPLICATION` token model.
2. Enforce scoped endpoint boundaries.
3. Keep machine-token and user-session separation.

### Step 11: Revocation Reason Code Taxonomy

Define and enforce consistent reason codes including:
- logout (device/all), admin revoke, deprovision, membership inactive,
- session expired/version mismatch,
- refresh rotated/reuse detected,
- CSRF/OIDC validation failures,
- rate-limit/security-policy/system maintenance reasons.

### Step 12: Mandatory Rate Limiting and Abuse Detection

Apply mandatory limits and anomaly detection for:
- login discovery,
- OAuth callback,
- refresh,
- logout,
- directory lookups.

Controls:
- per-IP/per-account/per-org quotas,
- burst + sustained windows,
- progressive backoff/temporary blocks,
- alerting and incident hooks.

---

## Security Controls

1. Signed, expiring, nonce-backed OAuth state.
2. Strict OIDC token validation checklist with fail-closed behavior.
3. Tenant/domain claim checks before linking/provisioning.
4. Secrets isolated in AWS Secrets Manager with versioned rotation.
5. IdP tokens remain backend-only.
6. JIT and on-demand resolve enforce platform-managed `maxUsers`.
7. No bulk import of IdP users.
8. Per-org session TTL policy with platform guardrails.
9. Refresh-time enforcement for inactive/deprovisioned users.
10. Request-time active-user/session rechecks.
11. Atomic refresh rotation + replay detection.
12. `deviceId` metadata-only trust boundary.
13. CSRF defenses on cookie-backed refresh/logout.
14. Explicit IdP sign-out mismatch handling and exposure bounds.
15. Admin safeguards + immutable audit.
16. Anti-enumeration controls.
17. JIT role floor enforcement.
18. Mandatory rate limits and abuse detection.
19. Reason-code standardization for incident response.
20. End-to-end auth-process audit logs with correlation IDs, reason codes, redaction, and immutable retention.

---

## Configuration Changes

### `application.properties` additions/updates

Use inline comments for every property so operators know purpose, security impact, and expected values.

### Config Ownership and Change Control

| Property Group | Primary Owner | Secondary Reviewer | Approval Requirement | Change Window |
|---|---|---|---|---|
| `app.secrets.*` | Platform Security | SRE | Security approval required | Scheduled window only |
| `app.oidc.*` | Platform Security | Identity Engineering | Security approval required | Scheduled window only |
| `app.auth.session-*` / `app.auth.refresh.*` | Identity Engineering | Security | Security + platform approval | Scheduled window only |
| `app.auth.csrf.*` | Security Engineering | Backend Lead | Security approval required | Scheduled window only |
| `app.idp.*` | Identity Engineering | Product Security | Product + security approval | Scheduled window only |
| `app.org-tier.*` | Billing/Platform Ops | Security | Platform admin approval | Business hours + audit trail |
| `app.auth.rate-limit.*` | SRE | Security Operations | SRE + SecOps approval | Can be emergency tuned |
| `app.audit.*` | Security Operations | Compliance | Compliance + security approval | Scheduled window only |

Rules:
- All production config changes require a change ticket and rollback plan.
- Emergency changes are allowed for security incidents and must be reviewed within 24 hours.
- Sensitive config changes (`secrets`, `oidc`, `session`, `csrf`) must emit immutable audit events.

```properties
# Region used by AWS Secrets Manager for organization IdP secrets.
# Set explicitly per environment via env var.
app.secrets.org-idp.region=${APP_SECRETS_ORG_IDP_REGION:af-south-1}

# Prefix/path used to namespace org IdP secrets in Secrets Manager.
app.secrets.org-idp.prefix=${APP_SECRETS_ORG_IDP_PREFIX:docuhyphen/org}

# Enables automatic secret rotation workflows.
app.secrets.rotation.enabled=${APP_SECRETS_ROTATION_ENABLED:true}

# Number of days between standard secret rotations.
app.secrets.rotation.interval-days=${APP_SECRETS_ROTATION_INTERVAL_DAYS:90}

# Overlap window (hours) where old/new secret versions are both accepted.
app.secrets.rotation.overlap-hours=${APP_SECRETS_ROTATION_OVERLAP_HOURS:24}

# Enforces strict OIDC ID token validation checks.
app.oidc.validation.strict=${APP_OIDC_VALIDATION_STRICT:true}

# Allowed JWT clock skew (seconds) for exp/nbf/iat validation.
app.oidc.allowed-clock-skew-seconds=${APP_OIDC_ALLOWED_CLOCK_SKEW_SECONDS:120}

# Default access token lifetime (minutes) when org policy is not set.
app.auth.access-token-default-minutes=${APP_AUTH_ACCESS_TOKEN_DEFAULT_MINUTES:15}

# Default refresh token lifetime (days) when org policy is not set.
app.auth.refresh-token-default-days=${APP_AUTH_REFRESH_TOKEN_DEFAULT_DAYS:7}

# Enables global session invalidation using sessionVersion claim checks.
app.auth.session-version.enabled=${APP_AUTH_SESSION_VERSION_ENABLED:true}

# Enables refresh token rotation on each successful refresh.
app.auth.refresh.rotation.enabled=${APP_AUTH_REFRESH_ROTATION_ENABLED:true}

# Grace period (seconds) where just-rotated refresh token is still tolerated.
app.auth.refresh.rotation.grace-seconds=${APP_AUTH_REFRESH_ROTATION_GRACE_SECONDS:60}

# Detects refresh token reuse and triggers security revocation flow.
app.auth.refresh.reuse-detection.enabled=${APP_AUTH_REFRESH_REUSE_DETECTION_ENABLED:true}

# Enables CSRF defenses for cookie-backed auth endpoints.
app.auth.csrf.enabled=${APP_AUTH_CSRF_ENABLED:false}

# Requires Origin/Referer validation on CSRF-protected endpoints.
app.auth.csrf.require-origin-check=${APP_AUTH_CSRF_REQUIRE_ORIGIN_CHECK:true}

# Enables JIT provisioning for eligible org users after IdP sign-in.
app.idp.jit.enabled=${APP_IDP_JIT_ENABLED:true}

# Lowest role JIT can assign; prevents privilege escalation from external claims.
app.idp.jit.role-floor=${APP_IDP_JIT_ROLE_FLOOR:ORG_MEMBER}

# Enables on-demand IdP directory lookup for explicit add-to-group/recipient actions.
app.idp.directory.on-demand.enabled=${APP_IDP_DIRECTORY_ON_DEMAND_ENABLED:true}

# Hard-disable bulk directory import behavior by policy.
app.idp.directory.bulk-import.enabled=${APP_IDP_DIRECTORY_BULK_IMPORT_ENABLED:false}

# Enables platform tier enforcement for per-org user caps.
app.org-tier.enforcement.enabled=${APP_ORG_TIER_ENFORCEMENT_ENABLED:true}

# Enables auth endpoint rate limiting and abuse controls.
app.auth.rate-limit.enabled=${APP_AUTH_RATE_LIMIT_ENABLED:true}

# Per-minute refresh endpoint budget.
app.auth.rate-limit.refresh.per-minute=${APP_AUTH_RATE_LIMIT_REFRESH_PER_MINUTE:30}

# Per-minute sign-in lookup endpoint budget.
app.auth.rate-limit.lookup.per-minute=${APP_AUTH_RATE_LIMIT_LOOKUP_PER_MINUTE:60}

# Per-minute OAuth callback endpoint budget.
app.auth.rate-limit.callback.per-minute=${APP_AUTH_RATE_LIMIT_CALLBACK_PER_MINUTE:60}

# Enables immutable audit pipeline/storage controls.
app.audit.immutable.enabled=${APP_AUDIT_IMMUTABLE_ENABLED:true}
```

---

## Implementation Notes

- Keep auth-critical transitions transactional and idempotent.
- Prefer focused services for OIDC validation, rotation state machine, CSRF checks, and abuse controls.
- Ensure sensitive logs are redacted and correlation-ready.
- Preserve constraints: no bulk import; platform-managed user caps.
- Standardize a single structured auth-log schema across discovery, callback, refresh, and logout flows for incident correlation and compliance evidence.

---

## Future Enhancements (Not in Scope Now)

1. SCIM 2.0 lifecycle sync for enterprise tenants.
2. IdP back-channel logout/event ingestion.
3. Device posture/risk signals for adaptive policy.
4. Advanced UEBA for insider-threat detection.
5. Hardware-bound session assurance options for high-security tiers.
