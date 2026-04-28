# Plan: Production Multi-IdP Authentication with Per-Org Credentials

## Overview

Production-grade authentication supporting internal credentials (username/password + email MFA) and external OAuth providers (Microsoft, Google; extensible to others). Organizations onboard their own IdP credentials (client ID, client secret, tenant ID) so their users authenticate via the org's own identity provider. Domain-based discovery routes users to the correct org IdP; when no org match exists, fallback shows all platform-supported providers. JIT (Just-In-Time) user provisioning auto-creates org members on first OAuth sign-in, with an optional per-org user cap. Org client secrets are stored via AWS Secrets Manager.

---

## Decisions

| Concern | Decision |
|---|---|
| Org user admission after OAuth | **JIT provision** — auto-create as `ORG_MEMBER` on first sign-in via org IdP |
| User cap guard rail | **Optional `maxUsers`** on org IdP config; block JIT when cap reached |
| Secret storage for org client secrets | **AWS Secrets Manager** — store secret ARN/ID in DB, resolve at runtime |
| Domain-to-org conflicts | **Interactive org selection** — prompt user when email domain maps to multiple orgs |
| User deactivation on IdP removal | **Passive detection** now (deactivate on failed sign-in); **periodic Graph/Directory sync** later |
| Account linking model | Multiple linked methods per user (internal + one or more external providers) |
| MFA for internal auth | Email OTP now; architecture supports adding SMS, TOTP, passkey later |

---

## Architecture Summary

### Sign-In Flow

- When `discoveryStatus` is `NO_ORG`, the frontend shows the password field AND "Sign in with Microsoft" / "Sign in with Google" buttons using the **platform's own default multi-tenant app registration** as fallback.
- When `discoveryStatus` is `ORG_FOUND`, the frontend can auto-redirect or show the recommended provider button prominently, plus internal auth as fallback.
- When `discoveryStatus` is `MULTIPLE_ORGS`, the frontend shows an org picker; after selection, redirects to that org's IdP.

### Sign-Up Flow

- OAuth sign-up uses the **platform's default credentials** (your own client ID).
- After sign-up and org registration, the ORG_ADMIN can configure their own IdP credentials so future org users sign in via the org's IdP.

### OAuth Callback Flow (Unified)
GET /auth/oauth/{provider}/callback?code=...&state=... │ ▼
Validate signed state (HMAC): extract flow, orgIdpConfigId, nonce
Determine credentials: if orgIdpConfigId present → load org credentials from AWS SM else → use platform default credentials
Exchange code for tokens using resolved credentials
Parse ID token → extract email, sub, tid, name
If org IdP config was used: a. Validate tid matches config's expected tenant ID b. Validate email domain is in org's verified domains c. If validation fails → reject with error
Resolve user: a. Find existing IdentityProviderLink by (provider, externalSubjectId) → existing user b. Else find AppUser by email: - If exists and has different external provider → link confirmation flow - If exists with no external link → link confirmation flow c. Else (no user exists): - If org IdP config present → JIT provision as ORG_MEMBER (check maxUsers cap) - If no org context → create standalone user (flow=signup) or reject (flow=signin)
Issue token triple (accessToken, idToken, refreshToken)
Redirect to frontend /oauth/callback?accessToken=...&idToken=...&isNewUser=...

### Org IdP Configuration Flow
ORG_ADMIN registers organization (existing flow) │ ▼ ORG_ADMIN navigates to Settings → "Your Organization" → "Identity Provider" section │ ▼
Select provider: Microsoft / Google
Enter: client ID, client secret, tenant ID (Microsoft) or workspace domain (Google)
Enter verified email domains (e.g., contoso.com, contoso.co.uk)
Set optional maxUsers cap
Submit → backend encrypts client secret → stores ARN in org_identity_provider_config │ ▼ System validates by attempting a test authorization URL build │ ▼ Config is active → future users with matching domains route through org's IdP

---

## Step-by-Step Implementation

### Step 1: New Database Entities

#### `OrganizationIdentityProviderConfig` entity
Create [OrganizationIdentityProviderConfig.kt](src/main/kotlin/com/docuhyphen/app/api/model/entity/OrganizationIdentityProviderConfig.kt):

| Column | Type | Description |
|---|---|---|
| `id` | UUID (PK) | |
| `organization_id` | UUID (FK → organization) | Owning org |
| `provider` | ENUM (IdentityProviderType) | MICROSOFT, GOOGLE |
| `client_id` | String | OAuth client ID |
| `client_secret_ref` | String | AWS Secrets Manager ARN or secret ID |
| `tenant_id` | String (nullable) | Azure AD tenant ID (Microsoft only) |
| `is_active` | Boolean | Enable/disable this config |
| `max_users` | Integer (nullable) | Optional user cap for JIT provisioning |
| `created_date` | Timestamp | |
| `updated_date` | Timestamp | |

#### `OrganizationVerifiedDomain` entity
Create [OrganizationVerifiedDomain.kt](src/main/kotlin/com/docuhyphen/app/api/model/entity/OrganizationVerifiedDomain.kt):

| Column | Type | Description |
|---|---|---|
| `id` | UUID (PK) | |
| `organization_id` | UUID (FK → organization) | |
| `domain` | String (unique) | e.g., "contoso.com" |
| `verified` | Boolean | Domain ownership verified |
| `created_date` | Timestamp | |

Add relationships to [Organization.kt](src/main/kotlin/com/docuhyphen/app/api/model/entity/Organization.kt):
- `@OneToMany` → `identityProviderConfigs: MutableList<OrganizationIdentityProviderConfig>`
- `@OneToMany` → `verifiedDomains: MutableList<OrganizationVerifiedDomain>`

#### Repository classes
- Create `OrganizationIdentityProviderConfigRepository` with `findByOrganizationId(orgId)`, `findActiveByDomain(domain)` (joins through verified domains).
- Create `OrganizationVerifiedDomainRepository` with `findByDomain(domain)`, `findAllByOrganizationId(orgId)`.

### Step 2: AWS Secrets Manager Integration for Org Secrets

Extend [AwsSecretsManagerService.kt](src/main/kotlin/com/docuhyphen/app/api/service/config/AwsSecretsManagerService.kt):
- Add `storeSecret(name: String, value: String, region: String): String` → returns the secret ARN.
- Add `updateSecret(secretId: String, value: String, region: String)`.
- Add `deleteSecret(secretId: String, region: String)`.

Create `OrgIdpSecretService`:
- `storeClientSecret(orgId: UUID, provider: IdentityProviderType, clientSecret: String): String` → stores in AWS SM with naming convention `docuhyphen/org/{orgId}/{provider}/client-secret`, returns the ARN.
- `resolveClientSecret(secretRef: String): String` → fetches plaintext from AWS SM.
- `deleteClientSecret(secretRef: String)`.

### Step 3: Refactor IdentityProviderStrategy for Runtime Credentials

Modify [IdentityProviderStrategy.kt](src/main/kotlin/com/docuhyphen/app/api/service/auth/idp/IdentityProviderStrategy.kt):

Add a credentials data class:

Change the interface methods to accept optional credentials:
- `buildAuthorizationUrl(state, redirectUri, credentials: OAuthCredentials? = null): String`
- `exchangeCodeForTokens(code, redirectUri, credentials: OAuthCredentials? = null): OAuthTokenResponse`

When `credentials` is null, fall back to `ConfigurationService` platform defaults (current behavior). When provided, use the org-specific credentials.

Update [MicrosoftIdentityProvider.kt](src/main/kotlin/com/docuhyphen/app/api/service/auth/idp/MicrosoftIdentityProvider.kt) and [GoogleIdentityProvider.kt](src/main/kotlin/com/docuhyphen/app/api/service/auth/idp/GoogleIdentityProvider.kt) to resolve credentials from parameter or ConfigurationService fallback.

Add `tid` claim extraction to `OAuthUserInfo`:
- Add `tenantId: String?` field to `OAuthUserInfo`
- Parse `tid` claim from Microsoft ID tokens in `parseIdTokenPayload`
- Parse `hd` (hosted domain) claim from Google ID tokens

### Step 4: Signed OAuth State Parameter

Create `OAuthStateService`:
- `generateState(flow: String, orgIdpConfigId: UUID?, email: String?, nonce: String): String` → builds a JSON payload `{ flow, orgIdpConfigId, email, nonce, exp }`, HMAC-signs it with JWT secret, Base64-encodes.
- `validateAndParseState(state: String): OAuthStatePayload?` → verifies HMAC, checks expiry (5 min), returns parsed payload or null.
- Store nonce in Redis with 5-min TTL to prevent replay.

### Step 5: Rewrite Sign-In Lookup

Replace the `lookupSignInMethod` in [SignInResource.kt](src/main/kotlin/com/docuhyphen/app/api/resource/SignInResource.kt):

New `SignInLookupResponse` in [RequestsResponses.kt](src/main/kotlin/com/docuhyphen/app/api/resource/model/RequestsResponses.kt):

Logic:
1. Extract domain from email.
2. Query `OrganizationVerifiedDomain` → find matching org(s) with active IdP configs.
3. If single org: build authorize URL using org's credentials, return `ORG_FOUND` with that as recommended + INTERNAL as fallback.
4. If multiple orgs: return `MULTIPLE_ORGS` with org list; frontend calls a follow-up endpoint with selected org ID to get the redirect URL.
5. If no org: check if user exists with external IDP link → return that provider as recommended. Otherwise return `NO_ORG` with platform-default OAuth URLs + INTERNAL.

Add `POST /auth/sign-in/lookup/org-select` endpoint for the multi-org case: accepts `{ email, orgId }`, returns the specific redirect URL for that org's IdP.

### Step 6: Rewrite OAuth Resource

Replace [OAuthResource.kt](src/main/kotlin/com/docuhyphen/app/api/resource/OAuthResource.kt):

**`GET /auth/oauth/{provider}/authorize`**:
- Accept query params: `flow` (signin/signup/link), `orgIdpConfigId` (optional).
- If `orgIdpConfigId` provided: load org config, resolve secret from AWS SM, build authorize URL with org credentials.
- If not: use platform default credentials.
- Generate signed state via `OAuthStateService`.
- Redirect to IdP.

**`GET /auth/oauth/{provider}/callback`**:
- Validate and parse signed state.
- Determine credentials source from state's `orgIdpConfigId`.
- Exchange code for tokens.
- Parse and validate ID token claims.
- If org context: validate `tid` (Microsoft) or `hd` (Google) matches expected org config. Reject on mismatch.
- Resolve or create user (see callback flow above).
- JIT provisioning: if org context and user doesn't exist:
  - Check `maxUsers` cap: count current org members vs cap. If exceeded → redirect to error page.
  - Create `AppUser` with `role = ORG_MEMBER`, attach to org, create `IdentityProviderLink`.
- Passive deactivation: if token exchange fails with user-not-found from IdP, and the user exists in our DB with an active link → set `isActive = false`.
- Issue token triple, set refresh cookie, redirect to frontend.

**`POST /auth/oauth/link-confirm`**: Keep existing logic, ensure it works with the new state model.

### Step 7: Org IdP Admin Management Endpoints

Add to [OrganizationResource.kt](src/main/kotlin/com/docuhyphen/app/api/resource/OrganizationResource.kt) (or a new `OrganizationIdpResource`):

- `POST /organizations/{orgId}/identity-provider` — Create IdP config. Accepts `{ provider, clientId, clientSecret, tenantId, domains[], maxUsers }`. Stores secret in AWS SM. Creates verified domain records. Requires `ORG_ADMIN`.
- `PUT /organizations/{orgId}/identity-provider/{configId}` — Update config (rotate secret, change domains, update cap). Requires `ORG_ADMIN`.
- `GET /organizations/{orgId}/identity-provider` — List configs for the org. Returns configs without secrets (show masked client ID). Requires `ORG_ADMIN`.
- `DELETE /organizations/{orgId}/identity-provider/{configId}` — Deactivate/remove config. Deletes secret from AWS SM. Requires `ORG_ADMIN`.
- `POST /organizations/{orgId}/identity-provider/{configId}/test` — Validates the config by building a test authorize URL and checking if the IdP responds. Requires `ORG_ADMIN`.

Add these paths to `excludedEndpoints` in [EndpointAuthorizationFilter.kt](src/main/kotlin/com/docuhyphen/app/api/interceptor/EndpointAuthorizationFilter.kt) — actually no, these require auth. They should NOT be excluded. Only the OAuth callback/authorize paths remain excluded.

### Step 8: Frontend — Sign-In Page

Rewrite [SignIn.tsx](web-app/src/app/authorization/sign-in/SignIn.tsx):

**EMAIL_ENTRY step:**
- User enters email, clicks "Continue".
- Calls `lookupSignInMethod({ email })`.
- Based on `discoveryStatus`:
  - `ORG_FOUND`: Show org name, prominent provider button ("Sign in with Microsoft via Contoso"), plus "Use password instead" link.
  - `MULTIPLE_ORGS`: Show org picker (radio/select list), user picks one, then show that org's provider button.
  - `NO_ORG`: Show password field AND provider buttons ("Sign in with Microsoft", "Sign in with Google").

**PASSWORD_ENTRY step** (internal auth): Same as current — email + password → MFA.

**Provider button click**: `window.location.href = redirectUrl` from the lookup response.

### Step 9: Frontend — Sign-Up Page

Update [SignUp.tsx](web-app/src/app/authorization/sign-up/SignUp.tsx):

- Keep existing internal sign-up flow (email → OTP → password).
- Show "Sign up with Microsoft" / "Sign up with Google" buttons that redirect to `/auth/oauth/{provider}/authorize?flow=signup` using platform default credentials.
- After OAuth sign-up callback → onboarding (person details → org registration).

### Step 10: Frontend — Org IdP Setup

Add an "Identity Provider" section to [OrganizationTab.tsx](web-app/src/app/settings/organization-tab/OrganizationTab.tsx) (visible to `ORG_ADMIN` only):

- Show current IdP config (if any): provider, masked client ID, domains, user cap, active status.
- "Configure Identity Provider" button → dialog/form:
  - Provider dropdown (Microsoft / Google).
  - Client ID input.
  - Client Secret input (masked after save).
  - Tenant ID input (shown only for Microsoft).
  - Domains multi-input (add/remove verified domains).
  - Max users input (optional).
- Save calls `POST /organizations/{orgId}/identity-provider`.
- "Test Configuration" button calls the test endpoint.
- "Remove" button calls DELETE.

Add API functions to [organizationApi.ts](web-app/src/services/organizationApi.ts):
- `createOrgIdpConfig(orgId, data)`
- `updateOrgIdpConfig(orgId, configId, data)`
- `fetchOrgIdpConfigs(orgId)`
- `deleteOrgIdpConfig(orgId, configId)`
- `testOrgIdpConfig(orgId, configId)`

### Step 11: Frontend — Models and API Types

Update [models.tsx](web-app/src/app/models/models.tsx):

- Update `SignInLookupResponse` to match new backend response shape.
- Add `DiscoveredOrg`, `AuthMethodOption` interfaces.
- Add `OrgIdentityProviderConfigDto` interface.
- Add `CreateOrgIdpConfigRequest`, `UpdateOrgIdpConfigRequest` interfaces.

### Step 12: "Your People" Tab Adjustments

Update [OrganizationPeopleTab.tsx](web-app/src/app/settings/organization-people-tab/OrganizationPeopleTab.tsx):

- For orgs with active IdP config: show a banner "Users are automatically provisioned via your identity provider."
- Show JIT-provisioned users with a badge/label indicating "Provisioned via Microsoft" or similar.
- Allow manual deactivation override (org admin can still disable a user from the app).
- Show `lastSignIn` timestamp to help admins identify stale users.
- Show current user count vs `maxUsers` cap if configured.

---
## Implementation notes
- Follow my coding practice (curley brace on new line, code organization using logical groupings and new lines, etc) 
- Keep files short, 400 lines max. If max reached use OOP and SOLID principles to keep the code short
--- 
## Configuration Changes

### application.properties additions
```properties
# Platform default OAuth credentials (fallback when no org config exists)
# Existing properties — no changes needed:
# app.oauth.microsoft.client-id, client-secret, tenant-id, redirect-uri
# app.oauth.google.client-id, client-secret, redirect-uri

# AWS Secrets Manager for org IdP secrets
app.secrets.org-idp.region=${AWS_REGION:af-south-1}
app.secrets.org-idp.prefix=docuhyphen/org

---