# Plan 05 — B2C external-customer sharing default

**Severity:** 🟠 (headline B2C topology is "denied" by default)
**Depends on:** nothing.
**Goal:** make org → external customer (a recipient with **no org**, or an unpaired org)
a first-class, ergonomic flow — without weakening the B2B safety the pairing gate
provides.

---

## STATUS — BACKEND DONE (2026-06-01), boot-validated; UI PENDING

**Backend complete, compiles green** (`./mvnw -o -q compile` → EXIT 0) **and boot-validated
from an empty DB on a throwaway DB** (Flyway applied V1→V3, Hibernate `validate` matched the new
column, `Listening on: http://localhost:8099`). What landed:
- **`OrganizationSettings.kt`**: new `allowExternalCustomerSharing: Boolean = true`
  (`@Column(name = "allow_external_customer_sharing", nullable = false)`).
- **`V3__org_settings_external_customer.sql`** (NEW — first migration past the V1/V2 baseline):
  `ALTER TABLE organization_settings ADD COLUMN allow_external_customer_sharing boolean NOT NULL
  DEFAULT true;` + a backfill `UPDATE` (defensive; the `DEFAULT` already covers existing rows).
- **`OrganizationSharingPolicyService.assertCanShareWithUser`** refactored to branch by **recipient
  nature** instead of one blanket pairing gate:
  - **Internal** (recipient in initiator's own org) → always allowed.
  - **B2C** (recipient has *no* org, incl. `recipientAppUserId == null` brand-new email) → allowed
    **by default**, gated only on `allowExternalCustomerSharing` (default `true`); the allow is
    **audit-logged** (`ORG_SHARE_EXTERNAL_CUSTOMER`/`ALLOWED`, best-effort) so admins retain
    visibility into shares that bypass pairing.
  - **B2B** (recipient belongs to another org) → unchanged: allowed only if
    `allowShareWithoutPairing == true` **or** the two orgs are ACCEPTED-paired; else the existing
    `IllegalArgumentException` (pairing-required message).
  - Injected `AuthAuditService`.

**Net effect:** a fresh org can share to an individual with no org **without changing any setting**
(default flips from denied→allowed for B2C only); org→unpaired-**org** stays blocked as before.

**Remaining / deferred:**
- **UI** → Plan 06 (split the single `allowShareWithoutPairing` toggle into two: "Allow sharing with
  external customers (individuals)" default ON + "Allow sharing with unpaired organizations" default
  OFF; soften the initiation hard-error to an "external recipient" badge).
- **Tests deferred** per standing "do not write tests" instruction.
- **Migration numbering:** V3 is now **consumed**. The next migration is **V4** (Plan 02's note
  about an optional `V3__…` index is stale — use V4).

---

## 1. What the user wanted

> Support small B2C and large B2C — an org sharing with an individual customer who may
> have no organization at all. This is a headline topology, not an edge case.

## 2. Current state (verified)

- `OrganizationSharingPolicyService.assertCanShareWithUser` **blocks** an org→recipient
  share when the recipient has no org / is an unpaired org **and**
  `OrganizationSettings.allowShareWithoutPairing == false`.
- `allowShareWithoutPairing` **defaults to `false`** → out of the box, the headline B2C
  share is denied. Called from `SharingSessionInitiationService` for EMAIL/APP_USER
  recipients.
- The pairing gate is the right control for **B2B** (don't silently share into another
  managed org). It is wrong as a blanket default for **B2C** (sharing to an individual).

Files:
- `…/service/organization/OrganizationSharingPolicyService.kt`
- `…/service/sharingsession/SharingSessionInitiationService.kt`
- `…/model/entity/OrganizationSettings.kt`
- `…/model/entity/Organization.kt` (org "type"/"profile" if one exists — check)

## 3. Design — distinguish "individual recipient" from "another org"

The fix is **not** to flip the default blindly (that would also auto-allow org→unpaired-
**org**, the B2B case we want gated). Instead, split the policy by recipient nature:

1. **Recipient is an individual / has no org** (B2C):
   - **Allow by default.** This is sharing to a person, not federating into a managed
     tenant. Gate only on: recipient identity is verified-by-email and the org has not
     explicitly disabled external sharing (`allowExternalCustomerSharing`, **default
     true**).
2. **Recipient belongs to another org and the two orgs are not paired** (B2B):
   - **Keep the current block** — require pairing or explicit
     `allowShareWithoutPairing = true`.

Implementation:
- In `assertCanShareWithUser`, branch on whether the resolved recipient has an
  `OrganizationMembership` / owning org:
  - no org → B2C path → allow unless `allowExternalCustomerSharing == false`.
  - has org, unpaired → existing pairing gate.
- Add `OrganizationSettings.allowExternalCustomerSharing: Boolean = true` (new column,
  migration `V3__org_settings_external_customer.sql`, default `true`, backfill existing
  rows to `true`).
- Keep `allowShareWithoutPairing` for the B2B-unpaired case (semantics unchanged).

### 3.1 Optional org "profile" nuance
If `Organization` has a B2B-vs-B2C/profile flag, you could make the default depend on it
(a strict-B2B org might keep external-customer sharing off). If no such flag exists,
**do not** add one in this plan — the per-recipient branch above is sufficient. Note it
as a future refinement.

### 3.2 Abuse / safety guardrails (don't lose them)
- Still enforce per-share constraints (Plan 01) and any rate/volume limits the directory
  guard imposes.
- Audit-log B2C external shares (they bypass pairing) so admins retain visibility.

## 4. UI (detail in Plan 06)

- **Org settings → Sharing:** two clearly distinct toggles —
  *"Allow sharing with external customers (individuals)"* (default ON) and
  *"Allow sharing with unpaired organizations"* (default OFF) — instead of one opaque
  `allowShareWithoutPairing`.
- Session initiation: when adding an external individual recipient, show an
  "external recipient" badge + (if applicable) the participant-constraints panel from
  Plan 01, not a hard error.

## 5. Step-by-step

1. Add `allowExternalCustomerSharing` to `OrganizationSettings` + `V3__…` migration
   (default true, backfill true).
2. Refactor `assertCanShareWithUser`: branch B2C (individual / no org) vs. B2B-unpaired;
   B2C allowed by default, B2B keeps the pairing gate.
3. Audit-log external-customer shares.
4. UI: split the single toggle into the two-toggle settings model; soften the initiation
   error to a badge.
5. Validate boot on throwaway DB (migration added). Tests: org→no-org user allowed by
   default; org→unpaired-org still blocked; toggling each setting flips the respective
   path.
6. Append DONE note to memory.

## 6. Acceptance

- Fresh org can share to an individual with no org **without** changing any setting.
- Org→unpaired **org** is still blocked unless `allowShareWithoutPairing = true`.
- Disabling `allowExternalCustomerSharing` blocks the B2C path only.
- External-customer shares are audit-logged.
- Boots clean.
