# Audit and Evidence Platform - Implementation Plan

Derived from `AUDIT-ARCHITECTURE.md`. This plan decomposes the architecture's five-stage
delivery sequence into small, independently shippable phases. Each phase has a narrow scope,
concrete tasks grounded in the current codebase, and an explicit test-validation gate that must
pass before the phase is considered done.

## MANDATORY FIRST STEP FOR EVERY SESSION

Before writing or changing any code, read `AGENTS.md` at the repo root in full and honor it.
Key rules that apply directly to this work:

- Resource classes (`resource/`) are thin HTTP adapters only. All audit capture and audit-read
  logic lives in `@ApplicationScoped` services under `service/`.
- A service must not use another service's repository directly; communicate via service methods.
- Never use the characters `—` or `→`, no emojis, no inline frontend styles, all buttons circular,
  add ids to all React components, put styles in co-located `*Styles.tsx` files, treat TS as
  strongly typed (no `any`).
- REST endpoints: plural nouns, resource-based URLs, correct verbs.
- After any user-visible feature change, update help docs under
  `web-app/src/app/components/help-docs/sections/` and run `npx tsc --noEmit` in `web-app/`.

## Prerequisite Decisions (block Phase 1)

`AUDIT-ARCHITECTURE.md` "Decisions Required Before Implementation Planning" must be answered by
compliance/legal owners before Phase 1 capture goes fail-closed. If they are not yet answered,
proceed with Phase 0 and the scaffolding parts of Phase 1 but keep new capture in a
`degraded/log-only` mode behind config until the event catalog and failure policy are approved.
Track answers in this plan as they arrive:

- [ ] Target regulated industries/jurisdictions
- [ ] Required event classes + per-class failure policy (fail-closed vs degraded)
- [ ] Retention / legal-hold / residency / replication / destruction rules
- [ ] Auditor persona boundaries (org/external/platform/support/compliance)
- [ ] Dual-approval policy for exports
- [ ] Per-audience field readability (readable/masked/pseudonymized/prohibited)
- [ ] Ordered-stream partition + segment-closing policy
- [ ] Online search window vs archive-only history
- [ ] Archive + projection freshness objectives
- [ ] Offline verifier distribution + public-key trust model
- [ ] Cutover date after which the ledger is authoritative evidence

## Current-State Anchors (verified in repo)

- Entities: `AuthAuditEvent`, `AccessAuditLog` (correct denormalized pattern),
  `DocumentAuditLog` (`@ManyToOne` non-null FK to `Document` - the anti-pattern to avoid).
- Services: `AuthAuditService` (RequestScoped, hash chain by latest-timestamp lookup, immutability
  gated by config, WORM = local JSONL via `AuthAuditWormSink`), `ExchangeDocumentAuditService`.
- Resources: `AuthAuditResource` (admin-only, bypasses centralized actions),
  `ExchangeDocumentAuditResource`.
- Authz: `Capability.kt` (`ORG_AUDIT_READ`, `APP_AUDIT_READ`, plus content caps on auditor role),
  `Action.kt` (`ORG_READ_AUDIT`, `APP_READ_AUDIT`).
- Migrations: Flyway, highest is `V40`. New migrations start at `V41`.
- Frontend audit surfaces today: `exchange-audit-tab/ExchangeAuditTab.tsx` and
  `exchange-document-sidebar/exchange-document-audit/ExchangeDocumentAudit.tsx`.
- Bug to fix during migration: `AuthAuditEvent.sessionId` maps to DB column `exchange_id`.

## Cost Principle (applies to every phase)

Keep infrastructure cost at a minimum. Do NOT add any new AWS service unless a compliance
requirement forces it and it is separately approved. Reuse the services already in
`infra\cloudformation.yml`: VPC/EC2, RDS Postgres, ECR, ECS/Fargate, CloudWatch Logs, IAM, ELB,
S3, CloudFront, and Secrets Manager. Consequences baked into the phases below:

- Phases 0-3 add zero AWS services (pure Postgres + application code).
- Phase 4 reuses S3 (one new bucket, Object Lock Governance mode) and signs manifests with an
  asymmetric key held in the existing Secrets Manager. No KMS CMK, no CloudTrail data events, no
  replication, no separate account.
- Deferred hardening (KMS/HSM signing, Object Lock Compliance mode, CloudTrail data events,
  cross-Region/account replication) is listed under Phase 4 and only added later on explicit
  approval.

---

# PHASE 0 - Catalog, Context, and Guardrails (no behavior change)

**STATUS: DONE.** See "NEXT STEPS / HANDOFF FOR THE NEXT SESSION" at the end of this file for
exactly what was built and how it was validated.

Goal: put the shared vocabulary and request context in place before any capture is wired, so
later phases only add capture calls, never redefine meaning.

Tasks:
1. Create a versioned event catalog: `service/audit/catalog/AuditEventType` (stable namespaced
   keys, e.g. `exchange.lifecycle.started`), `AuditCategory`, `AuditOutcome`, plus a catalog
   version constant. Seed only the keys already emittable today (auth + document actions).
2. Define `AuditEventDraft` (typed input) and a prohibited-field list (no passwords, OTPs, tokens,
   secrets, keys, raw content, unrestricted Field Values). Add a validator that rejects drafts
   referencing unknown event types or prohibited payload keys.
3. Add server-generated correlation context: a request filter that always sets a server trace ID,
   correlation ID, and (where present) causation ID; expose them through the existing
   `AuthTokenContext`/interceptor. Client `X-Request-Id` becomes an untrusted hint only.
4. No production capture calls yet. No migration yet.

Test validation (gate):
- Unit: catalog rejects unknown event type and prohibited payload keys; accepts known ones.
- Unit: correlation filter always produces a non-null server trace ID even with no client header.
- Build: `./gradlew build` (or the project's wrapper) compiles and existing tests still pass.

---

# PHASE 1 - Trust Boundary: Recorder + Transactional Outbox

**STATUS: DONE.** See "NEXT STEPS / HANDOFF FOR THE NEXT SESSION" at the end of this file for
exactly what was built and how it was validated.

Goal: one code path that writes a durable audit intent in the same transaction as a business
change, without yet building the ledger/archive.

Tasks:
1. Migration `V41__audit_outbox.sql`: `audit_outbox` table - immutable event draft (JSONB/text),
   unique event ID, idempotency key, status, attempt count, occurrence time, business transaction
   id. No FKs to business tables (follow `AccessAuditLog`, never `DocumentAuditLog`).
2. `AuditRecorder` (`@ApplicationScoped`, `service/audit/`): accepts `AuditEventDraft`, derives
   trusted actor/session/owner/correlation context, validates against the catalog, writes one
   outbox row. Joins the caller's active transaction (no new transaction) so it commits/rolls back
   with the business change.
3. Failure policy switch by event class: fail-closed for regulated writes/sensitive reads;
   documented degraded mode only for classified low-risk events; never silent-drop; emit health
   signal + machine-readable service state.
4. Wire the recorder into the first 1-2 regulated operations only (recommend Exchange lifecycle
   transition in its owning service) as the reference implementation. Keep everything else on the
   old paths.
5. DB append-only guardrail (first cut): migration adds a trigger/privilege denying UPDATE/DELETE
   on `audit_outbox` for the application role.

Test validation (gate):
- Integration: a regulated mutation rolls back when the outbox write is forced to fail.
- Integration: a committed mutation produces exactly one outbox row (idempotent under retry).
- Integration: application DB role cannot UPDATE or DELETE an `audit_outbox` row.
- Unit: fail-closed vs degraded routing selects the correct behavior per event class.

---

# PHASE 2 - Canonical Ledger + Ordering + Tamper Evidence

**STATUS: DONE.** See "NEXT STEPS / HANDOFF FOR THE NEXT SESSION" at the end of this file for
exactly what was built and how it was validated.

Goal: turn committed outbox intents into an ordered, hash-chained, per-stream ledger.

Tasks:
1. Migration `V42__audit_ledger.sql`: `audit_ledger_event` with the canonical envelope from the
   architecture (event id/type/category/outcome/schema+catalog version; occurrence/recorded/ledger
   time UTC; stream id + strictly increasing stream sequence; actor kind/id + delegated/system
   actor; session/auth method/app id/trace/request/correlation/causation; owner + scope reference;
   primary + related resource references as denormalized IDs+labels; action/reason code/capability/
   authorization basis; structured changed fields + before/after value hashes; classification +
   retention class + legal-hold eligibility; prev hash + canonical hash + signing key id +
   checkpoint ref). Denormalized IDs only, no business FKs. Add `stream_head` table for per-stream
   locking.
2. `LedgerProcessor` (`@ApplicationScoped`): drains outbox, canonicalizes JSON (kotlinx),
   assigns stream + sequence via locked `stream_head` row (atomic compare-and-set), computes
   `eventHash = sha256(canonicalEvent || sequence || prevHash)`, appends. Idempotent by event id.
3. Streams = one per owner scope + time partition (per approved partition policy).
4. Migration adds append-only UPDATE/DELETE deny on `audit_ledger_event` for the app role; a
   separate retention role may perform approved lifecycle ops only.
5. Fix the `AuthAuditEvent.sessionId -> exchange_id` mismap: the canonical ledger uses correct
   column names; do not carry the mislabeled column forward.

Test validation (gate):
- Concurrency: parallel appends to one stream cannot fork or reorder (assert contiguous sequence,
  single chain).
- Tamper: mutating any canonical field, sequence, or prev hash breaks recomputed-hash verification.
- Integration: every committed outbox row yields exactly one ledger event after processing.
- DB: app role cannot UPDATE/DELETE ledger rows.

---

# PHASE 3 - Migrate Existing Capture onto the Recorder

**STATUS: DONE.** All five tasks (recorder-routed `AuthAuditService`, the 8 existing document
audit call sites, the 7 previously-uncaptured document-access events, Share + a small number of
sensitive authorization-deny capture points, and a defensible breadth of Exchange lifecycle/
workflow-definition/Field-Schema-definition capture) are implemented and green across 322 backend
tests. See "NEXT STEPS / HANDOFF FOR THE NEXT SESSION" at the end of this file for the exact scope,
scoping decisions made, and the couple of intentionally-deferred items (org-membership-role-change
capture at the finer `OrganizationMembershipService` grain, and Share expiry, which has no active
status-transition job to instrument today).

Goal: unify the three legacy audit mechanisms behind the recorder without losing current UI.

Tasks:
1. Route `AuthAuditService.emit` through `AuditRecorder` (keep the public method signature; change
   the internals) so auth events land in the outbox/ledger. Retain hash coverage of event id,
   actor role, target type, target id (fixing the current hash-omission gap).
2. Replace `DocumentAuditLog` writes with recorder capture; stop using the non-null `Document` FK
   pattern. Add the missing document access events: view, preview, current-version download,
   historical-version download, ZIP export, library download, no-auth download - with human/app/
   public-link/workflow actors distinguished.
3. Implement Share + authorization capture (the `AccessAuditLog` intent, but through the recorder):
   grants, activation, inherited materialization, role/constraint change, expiry, revocation, and
   sensitive/denied authorization decisions.
4. Add capture in the owning services for Exchange lifecycle, workflow definition/instance/step,
   membership/role, Field/Schema, and application operations (breadth per the coverage catalog;
   start with the highest-risk mutations listed in the audit doc).
5. Compatibility projection: keep the current Exchange `Audit` tab and document sidebar working by
   serving them from a read view over the ledger (single query, not one request per document).

Test validation (gate):
- Integration: existing Exchange/document audit UI still renders (now ledger-backed, one request).
- Integration: a document download and a Share revoke each produce exactly one ledger event with
  correct actor kind.
- Regression: deleting a document leaves its audit history intact and renderable (no cascade).
- Frontend: `npx tsc --noEmit` clean; help docs for Exchange/document audit reviewed + updated.

---

# PHASE 4 - Immutable WORM Archive + Signed Segments + Verification

Goal: make history externally verifiable, replacing the local JSONL sink.

Cost constraint (mandatory): this phase must add NO new AWS services. Reuse services already in
the stack (`infra\cloudformation.yml` today has S3, RDS, ECS, Secrets Manager, CloudWatch, IAM,
ELB, CloudFront). Do not introduce a KMS CMK, CloudTrail data events, cross-Region/cross-account
replication, or a separate security account in this phase. Those are explicitly deferred to a
later, separately-approved hardening phase (see "Deferred hardening" below) and only if a
compliance requirement forces them.

Tasks:
1. Replace `AuthAuditWormSink` (local JSONL) with segment archiving: close segments containing a
   Merkle root/segment digest, first/last sequence, prev segment digest, event count, schema
   versions, signing key id.
2. Sign segment manifests in-application with an asymmetric key pair whose private key is stored in
   the existing AWS Secrets Manager (no new service). Archive both segment and manifest. The public
   key ships with the offline verifier. Keep the signing indirection abstracted so the key can be
   moved to KMS/HSM later without changing the ledger format.
3. Infra (CloudFormation/IaC), reusing S3 only: one new S3 bucket (a bucket is not a new service)
   with versioning + Object Lock in Governance mode, denied public access, and separate write/read
   IAM roles built from the existing IAM setup. The app task may PutObject but not DeleteObject,
   retention-bypass, or legal-hold admin. Use CloudWatch (already in use) for integrity alarms.
   NOTE: no dedicated KMS key (use default S3-managed SSE-S3 encryption), no CloudTrail data events,
   no replication in this phase.
4. Real `verifyDay`/verifier: recompute hashes and compare against claimed hashes and signed
   boundary checkpoints (fix the current no-op verification). Continuous verification job emits its
   result as an immutable audit event and alerts on failure via CloudWatch.
5. Import existing legacy audit rows explicitly marked as legacy (no false full-provenance claim).

Deferred hardening (NOT in this phase; require separate cost approval before adding):
- Dedicated audit KMS CMK / HSM-backed asymmetric signing key (replaces the Secrets Manager key).
- Object Lock Compliance mode (irreversible retention) after Governance-mode validation.
- CloudTrail S3 data events on the archive bucket.
- Cross-Region or cross-account replication and a separate security/evidence account.

Test validation (gate):
- Deleting/inserting/reordering/truncating a range is detected via boundary checkpoints.
- Changing a segment or manifest breaks signature verification (Secrets Manager-held key).
- WORM objects cannot be overwritten/deleted before retention expiry (Governance-mode test env).
- DR retrieval test: a segment can be fetched and independently verified with the public key.
- Infra review confirms no new AWS service type was introduced (S3 + Secrets Manager + CloudWatch
  + IAM only).

---

# PHASE 5 - Auditor Capabilities, Engagements, Search Projection

**STATUS: DONE.** See "NEXT STEPS / HANDOFF FOR THE NEXT SESSION" at the end of this file for
the exact scope, tests run, and deferred items.

Goal: let auditors read only their scope, without becoming admins or gaining content access.

Tasks:
1. Authz correction: remove standing content caps from `ORG_AUDITOR`
   (`EXCHANGE_READ`, `DOCUMENT_READ`, `DOC_LIBRARY_READ`, `BLUEPRINT_READ`, `WORKFLOW_READ`,
   `SEQUENCE_READ`, `COMMUNICATION_READ`, `FIELD_SCHEMA_READ`, `GROUP_READ`, `WEBHOOK_AUDIT_READ`).
   Decouple `ORG_AUDIT_READ` from `ORG_BILLING_ADMIN`.
2. Add new capabilities: `ORG_AUDIT_EXPORT`, `ORG_AUDIT_VIEW_SENSITIVE`, `APP_AUDIT_EXPORT`,
   `AUDIT_EXPORT_APPROVE`, `AUDIT_RETENTION_MANAGE`, `AUDIT_LEGAL_HOLD_MANAGE`,
   `AUDIT_INTEGRITY_VERIFY`, and matching centralized `Action` entries.
3. `AuditEngagement` entity + service + migration: org/resource scope, auditor principals/group,
   categories + sensitivity, start/expiry/purpose/case ref/legal basis, export perms, max range,
   download limits, requester/approver/revoker/status. Lifecycle ops are themselves audited; MFA +
   recent step-up required for sensitive evidence.
4. Tenant-aware search projection (rebuildable from ledger). Every query independently authorizes
   caller capability, exact owner scope/engagement, category/sensitivity, requested fields, and any
   drill-down. Never infer scope from primary org.
5. New REST resources (thin adapters, service-backed), cursor pagination over sequence+event id:
   `GET /organizations/{organizationId}/audit-events(/{eventId})`,
   `GET /exchanges/{exchangeId}/audit-events`,
   `GET /exchanges/{exchangeId}/documents/{documentId}/audit-events`,
   workflow/application/security-incident contextual events, `GET /users/me/security-events`,
   `GET /platform/audit-events`. Replace singular legacy routes behind compatibility projections.
6. Audit the audit: searches, detail views, denied attempts are themselves recorded.

Test validation (gate):
- Org auditor cannot cross org boundaries or read document content by role alone.
- Platform auditor cannot read customer content without a separate grant.
- Audit searches/detail/denied attempts produce audit events.
- Contextual endpoints enforce a mandatory Resource Reference filter and reauthorize on drill-down.

---

# PHASE 6 - Verifiable Evidence Exports

**STATUS: DONE.** See "NEXT STEPS / HANDOFF FOR THE NEXT SESSION" at the end of this file for
the exact scope, tests run, and deferred items.

Goal: asynchronous, signed, independently verifiable evidence bundles.

Tasks:
1. `AuditExport` resource/state machine (`REQUESTED`, `APPROVAL_PENDING`, `BUILDING`, `READY`,
   `EXPIRED`, `FAILED`, `REVOKED`) + migration. Dual-control approval where policy requires.
2. Export builder verifies ledger + archive checkpoints first, then produces the bundle:
   `manifest.json`, `events.jsonl`, `events.csv` (with fidelity note), `integrity.json`, detached
   manifest signature + public verification material, optional PDF summary (never canonical),
   README. Bundle digest + short download lifetime.
3. REST: `POST/GET /organizations/{organizationId}/audit-exports`, `/{exportId}`,
   `/{exportId}/approvals`, `/{exportId}/file`, plus `POST /platform/audit-exports`.
4. Every export request/approval/completion/download/expiry/verification/failure is an audit event.
5. Offline verifier tool + `GET /organizations/{organizationId}/audit-integrity`.

Test validation (gate):
- Exported bundle verifies offline and discloses redactions + schema versions.
- A range does not verify merely because inner events link; boundary checkpoints are required.
- Export lifecycle actions each create audit events.

---

# PHASE 7 - Auditor Portal + Contextual UI Surfaces (frontend)

**STATUS: DONE.** See "NEXT STEPS / HANDOFF FOR THE NEXT SESSION" at the end of this file for
the exact scope, tests run, and deferred items.

Goal: surface the projection in the product per the access-surface matrix.

Tasks:
1. New protected route `/audit` (top-level workspace): search/filters, event detail + related-event
   timeline, integrity status, export requests/approvals/downloads, engagements, retention/legal
   hold for authorized governance users. Default to metadata; sensitive fields are a separate authz.
2. Re-point existing `ExchangeAuditTab` and `ExchangeDocumentAudit` to the new ledger-backed
   endpoints; add contextual `Audit`/`History` views to Settings areas (Workflows definition +
   instance kept distinct, Document Library, Blueprints, Sequences, Variables, Communications,
   Organization Administration) and personal `Security Activity`.
3. Shared components: one event-detail component, event display catalog, redaction policy, cursor
   pagination, authorization-aware service module. Follow AGENTS.md frontend rules (co-located
   `*Styles.tsx`, ids on components, circular buttons, no `any`, responsive, no inline styles).

Test validation (gate):
- Vitest: search, event-detail, and contextual views render + paginate; sensitive fields hidden
  without capability.
- `npx tsc --noEmit` clean in `web-app/`.
- Help docs updated for every new/changed audit surface; size limits respected.

---

# PHASE 8 - Retention, Legal Hold, Analytics Projection, Assurance

**STATUS: DONE (narrow first cut).** See "NEXT STEPS / HANDOFF FOR THE NEXT SESSION" at the end
of this file for the exact scope, tests run, and deferred items - notably that per-field
pseudonymization is not wired into ledger capture, and monitoring covers only outbox backlog and
analytics lag, not the plan's full signal list.

Goal: governance controls and analytics reuse without weakening evidence.

Cost constraint: reuse existing services (Postgres for the analytics fact projection and retention
metadata, CloudWatch for alarms). Do not add a data-warehouse, streaming, or search service.
"crypto-shredding" here means deleting an application-held encryption key from the existing Secrets
Manager, not provisioning KMS.

Tasks:
1. Retention catalog by event class + org override; WORM vs searchable-projection retention;
   identity/IP treatment; legal-hold eligibility; export lifetime/disposal; crypto-shredding via a
   Secrets Manager-held key (no KMS). Legal hold overrides disposal; deleting a
   user/org/Exchange/document never erases required history (use pseudonymization/identity-vault
   reference).
2. Idempotent analytical projector keyed by audit event id: preserves times/principal/owner/refs/
   schema versions, copies only approved dimensions/measures, excludes prohibited fields, applies
   corrections by rebuild. Separate ledger vs projection freshness.
3. Monitoring/alerts (CloudWatch only): outbox age/depth/dead-letters, ledger
   latency/duplicates/gaps/forks, archive age/missing segments, scheduled chain+signature
   verification, projection lag, clock drift, denied-access anomalies, % of regulated ops with
   tested coverage. (Replication-lag alarms apply only if the deferred replication hardening is
   later approved.)
4. Coverage tests on every regulated service operation.

Test validation (gate):
- Analytics replay is idempotent and never copies prohibited fields.
- Reconcile analytical measures against authoritative event counts.
- Deleted resources/users do not erase evidence or break historical rendering.
- Integrity verification results are themselves immutable audit events and alert on failure.

---

## Global Definition of Done (all phases)

- `./gradlew build` and the frontend `npx tsc --noEmit` + Vitest pass.
- Every new regulated occurrence has exactly one tested capture point in its owning service.
- No business FK from any audit/ledger row to a mutable business entity.
- No secrets/tokens/raw content in any audit payload.
- Help docs updated for user-visible changes.

---

## NEXT STEPS / HANDOFF FOR THE NEXT SESSION

### Post-label-sweep follow-up: search-scope labels + a display bug (2026-07-08)

Two fixes on top of the label sweep above, triggered by an actual `audit.search.performed` event
the user inspected in the UI and found still unlabeled/malformed:

1. **Display bug**: `AuditEventDetail.tsx`'s Target field rendered `targetType` twice when no
   `targetLabel` was present (`DOCUMENT (DOCUMENT e726b39d-...)`the label, then the parenthetical,
   both falling back to the same type string). Fixed to only render the type once when there is no
   label.
2. **`AUDIT_EVENT_VIEWED` now reuses the viewed event's own `targetLabel`** (it was already sitting
   on `AuditProjectionEvent` from the earlier sweep, just never passed through) - free, no new
   lookup.
3. **`AUDIT_SEARCH_PERFORMED`'s own target (the *scope being searched*, not a specific result) now
   gets a real label for the two most common scopes.** New `ExchangeRetrievalService.
   getExchangeNameForDisplay(exchangeId)` / `getDocumentTitleForDisplay(exchangeId, documentId)`:
   deliberately **not** authorization-checked (unlike `getExchange()`, which re-runs
   `Action.EXCHANGE_VIEW` and would wrongly fail the whole search for an auditor who has audit-read
   rights but no direct Exchange-view grant), following the same "display-only, never use for an
   authorization decision" convention already established by
   `primaryRecipientUserIdForDisplay`. Wired into `AuditSearchProjectionService.listExchangeEvents`/
   `listExchangeDocumentEvents` via a new `auditTargetLabel` parameter threaded through
   `searchEvents` -> `recordAuditActivity`, and into `recordDeniedAttempt` for symmetry (not yet
   called with a label by any site, but available).

**Still unlabeled** (same reasoning as the original sweep, now narrower): `AUDIT_SEARCH_PERFORMED`/
`AUDIT_ACCESS_DENIED` for every *other* scope type search reaches (organization, workflow
definition, application, security incident, personal) - those would need either a per-type resolver
dispatch or plumbing an already-known label in from each resource caller, which is real follow-up
scope, not a quick add. Full backend suite re-run clean (374/374) after this fix; frontend
`tsc --noEmit` and Vitest (92/92) both clean.

Status at time of writing: **Phase 8 done (narrow first cut) - this is the last phase in this
plan.** See "Phase 8 - what was built this session" immediately below for full detail and
"Phase 8 - deferred / flagged items" for what a follow-up session should pick up; there is no
Phase 9 in this plan, so the next session's job is closing those deferred items, not starting new
scope.

### Post-Phase-8 fix: denormalized labels alongside every ID (2026-07-08)

The architecture's "denormalized IDs+labels" language was only ever half-implemented: `audit_outbox`/
`audit_ledger_event` had `actor_id`/`target_id`/`organization_id` columns but no paired label, so
every audit surface showed a raw UUID for the actor and target with no human-readable name/email -
confirmed as a real product gap, not a display bug.

Migration `V48__audit_denormalized_labels.sql` adds `actor_label`/`target_label`/`organization_label`
(nullable, `VARCHAR(256)`) to both tables. `AuditEventDraft` gained matching `actorLabel`/
`targetLabel`/`organizationLabel` fields, threaded through `AuditOutboxEntry` -> `LedgerProcessor`'s
`CanonicalLedgerEnvelope` (participates in the per-row hash, same as every other captured field) ->
`AuditLedgerEvent` -> `AuditProjectionEvent`/`AuditEventDto` -> the frontend `AuditEventTable`/
`AuditEventDetail` (now prefer the label, falling back to the raw id/kind exactly as before for older
rows or call sites with no label). Export bundles (`ExportedLedgerEventRecord`, CSV/JSONL) carry the
same three columns.

**Actor labels are centralized, not per-call-site.** `AuditRecorder.resolveAuthenticatedActorLabel()`
builds `"Firstname Lastname <email>"` (or plain email) from `AuthTokenContext.authToken.appUser` once,
inside the recorder itself, wrapped in `runCatching` against a possible lazy-load failure on the
`Person` association. This means every event captured for an authenticated human actor gets a real
name/email with **zero changes needed at any of the 22 files that construct an `AuditEventDraft`** -
`draft.actorLabel` only needs to be set explicitly for actors the recorder cannot see (for example
`ExchangeDocumentAuditService`'s no-auth/public-link/email actor path, which now passes the actor's
email directly).

**Target labels cannot be centralized** without either a lookup service reaching back into every
business service from the audit layer (a circular-dependency risk, since those same services already
call *into* the audit layer to capture events) or showing the entity's current name/state instead of
what it was at the time of the event. Every one of the 22 `AuditEventDraft`-constructing files was
swept individually instead, passing whatever human-meaningful string the call site already had in
scope (`Exchange.name`, `Document.title`, `WorkflowDefinition.name`, `SchemaDefinition.displayName`,
`AuditEngagement`/`AuditLegalHold`/`AuditExport`'s `purpose`/`caseReference`, etc). `ShareService`'s
public mutating methods (`grant`/`revoke`/`revokeAllForResource`/`revokePendingForResource`/
`updateRoleAndConstraints`) gained an optional `resourceLabel: String? = null` parameter threaded
through to its `recordShareEvent`/`markRevoked` capture calls; only `ExchangeUpdateService` and
`ExchangeAccessManagementService` (the two callers already touched in this pass, and the only ones
with the Exchange name in easy scope) were updated to actually pass it - every other caller of
`ShareService`'s mutating methods across the codebase (`ExchangeInitiationService`,
`ExchangeParticipantService`, `ExchangeApprovalEventHandler`, `ExchangeRevokeAccessActionHandler`)
compiles unchanged and simply gets a `null` label, which the whole design already treats as an
acceptable, expected fallback (never a bug) - closing those is optional follow-up work, not a defect.

A handful of call sites were deliberately left without a target label because no safe, non-circular
label source exists at that point: `AuditRetentionPolicyService`/`AuditIdentityVaultService`/
`AuditArchiveScheduler` (targetId is already a readable string - a category name or stream id, not a
raw UUID); `AuditIntegrityService`/`AuditAnalyticsReconciliationService` (no named entity, only an
organization id with no Organization lookup available in that service); `LegacyAuditImportService`
(replaying historical rows whose original target metadata is whatever the legacy system recorded);
`AuthAuditService`'s generic `emit(...)` dual-write (a fully generic pass-through with no entity
context - but its actor side is already covered by the centralized resolver, since auth events are
almost always a human acting on their own account); and `AuditSearchProjectionService`'s own
"audit-of-the-audit" meta-events (`AUDIT_SEARCH_PERFORMED`/`AUDIT_EVENT_VIEWED`/
`AUDIT_ACCESS_DENIED`) - system-internal events about search activity, not the primary "what happened
to my document" trail, and adding a target label there would need the same generic resolver this
design avoids.

One test fix was required: `RescindSideEffectsTest`'s `verify(shareService, never())
.revokeAllForResource(any(), any(), anyOrNull())` calls needed a fourth `anyOrNull()` matcher added
for the new `resourceLabel` parameter (Mockito requires the matcher count to equal the real method's
parameter count). Full backend suite re-run clean (374/374); frontend `npx tsc --noEmit` clean;
frontend Vitest clean (92/92, including the existing `AuditEventTable`/`AuditEventDetail` tests that
exercise the exact render paths changed here).

**Not verified live in a browser this session**: the user's own `web-app` dev server was already
running on port 5173, so the preview tooling's browser automation could not attach to a fresh
instance (it started a second Vite process on a fallback port that its own proxy didn't correctly
target). Verified instead via a clean `tsc --noEmit`, a clean full Vitest run, and a direct HTTP check
confirming the dev server itself serves `200`. A live click-through of the Audit workspace with a
freshly captured, labeled event is still worth doing once a session has exclusive access to the dev
server.

### Phase 8 - what was built this session

All four Phase 8 tasks got a real, working implementation, deliberately narrower than the plan's
full ambition where a task implied a much larger cross-cutting change (see "deferred / flagged
items" below for exactly what was left out and why).

Migration `V47__audit_retention_legal_hold.sql` adds four tables plus one additive column, all
following the audit platform's no-business-FK rule:
- `audit_retention_policy` - organization overrides only; the platform-default catalog lives in
  code (`AuditRetentionCatalogService`), so every organization has a well-defined effective policy
  with zero rows.
- `audit_legal_hold` - a normal mutable lifecycle table (place -> release), same shape as
  `audit_engagement`/`audit_export`.
- `audit_identity_vault_key` - the crypto-shredding primitive (see below).
- `audit_analytics_fact` - the analytics projection (see below).
- `audit_ledger_event.global_sequence` (additive `BIGSERIAL`) - an efficient cross-stream
  append-order cursor for the analytics projector, added via `ALTER TABLE` (a DDL operation by the
  Flyway migration owner, not the app role the append-only trigger restricts).

**Task 1 (retention + legal hold + crypto-shredding):**
- `AuditRetentionCatalogService` holds the platform-default retention parameters per
  `AuditCategory` as code constants (`DEFAULT_LEDGER_RETENTION_DAYS = 400`,
  `DEFAULT_ARCHIVE_RETENTION_DAYS = 2555`, matching the existing `AuditArchiveRetentionDays` S3
  Object Lock CloudFormation parameter so the two numbers never silently disagree). These are
  explicitly documented as compliance-pending placeholders, not confirmed policy, per the plan's
  still-unanswered "Prerequisite Decisions" checklist.
- `AuditRetentionPolicyService` layers per-organization overrides (`upsertOverride`/
  `getEffectivePolicy`/`listEffectivePolicies`) on top of the catalog, records
  `AUDIT_RETENTION_POLICY_UPDATED` on every override change, and exposes
  `isLedgerRetentionExpired` for a future purge job.
- `AuditLegalHoldService` places/releases holds on a denormalized resource reference
  (`placeHold`/`releaseHold`/`isUnderHold`/`listActiveHolds`), recording
  `AUDIT_LEGAL_HOLD_PLACED`/`AUDIT_LEGAL_HOLD_RELEASED`.
- `AuditDisposalEligibilityService.isEligibleForDisposal` is the single check a future
  retention-purge job must call: retention-expired AND not under legal hold. No purge job exists
  yet in this codebase (nothing currently deletes ledger/projection rows), so this is the
  eligibility gate that job will call, kept independently unit-tested.
- `AuditIdentityVaultService` is the crypto-shredding primitive: a random 256-bit data key per
  subject, wrapped (AES-GCM) with a single application-wide master key
  (`AuditIdentityVaultMasterKeyProvider`, local-file or AWS-Secrets-Manager-backed, mirroring the
  Phase 4 `AuditArchiveSigningKeyProvider` local/aws producer pattern exactly - one secret total,
  never one per subject, to keep Secrets Manager cost bounded). `shred` nulls the wrapped
  key/IV and records `AUDIT_IDENTITY_KEY_SHREDDED`; no audit row is ever deleted.

**Task 2 (analytics projection):**
- `AuditAnalyticsProjector.project`/`projectOne` idempotently drains `findUnprojected` (ordered by
  the new `global_sequence` cursor) into `audit_analytics_fact`, keyed unique on
  `ledger_event_id`; `rebuild(organizationId, platformOnly)` deletes and reprojects a scope's facts
  for the plan's "applies corrections by rebuild" requirement. The fact entity carries no payload
  column at all, so a prohibited field can never reach the projection even in principle.
  `AuditAnalyticsProjectionScheduler` drains every `app.audit.analytics.project-every` (default 1m).
- `AuditAnalyticsReconciliationService.reconcile` compares ledger vs fact counts for a scope,
  returns a bounded sample of missing ledger event ids on mismatch, and records
  `AUDIT_ANALYTICS_RECONCILED` (outcome `FAILURE` on mismatch) - the plan's "reconcile analytical
  measures against authoritative event counts" gate.

**Task 3 (monitoring, narrow cut):** `AuditHealthMonitorService`/`AuditHealthMonitorScheduler`
compute and alert on exactly two signals every `app.audit.health.check-every` (default 5m): oldest
undrained `audit_outbox` row age (`AUDIT_OUTBOX_BACKLOG_HIGH`) and ledger-vs-analytics lag
(`AUDIT_ANALYTICS_PROJECTION_LAG_HIGH`), using the same ERROR-log-marker + CloudWatch Logs metric
filter + alarm mechanism as the existing Phase 4 `AUDIT_ARCHIVE_VERIFICATION_FAILED` signal (two
new `AWS::Logs::MetricFilter`/`AWS::CloudWatch::Alarm` pairs added to `infra/cloudformation.yml`,
no new AWS service). Archive age/missing segments and chain+signature verification were already
covered by the existing Phase 4 `AuditArchiveScheduler`. Ledger latency/duplicates/gaps/forks,
clock drift, denied-access anomalies, and "% of regulated ops with tested coverage" are not
implemented - see deferred items below.

**Task 4 (coverage tests):** six new test classes (`AuditRetentionPolicyServiceTest`,
`AuditLegalHoldServiceTest`, `AuditDisposalEligibilityServiceTest`, `AuditIdentityVaultServiceTest`,
`AuditAnalyticsProjectorTest`, `AuditAnalyticsReconciliationServiceTest`), 17 test methods total,
covering: default-vs-override retention resolution, non-positive-day validation, hold
place/release/re-release-rejection, legal hold overriding retention-expired disposal eligibility,
crypto-shred key round-trip/idempotent-ensure/permanent-unrecoverability-after-shred, idempotent
double-projection, and reconciliation match/mismatch reporting.

**Capability/Action wiring:** `AUDIT_RETENTION_MANAGE`, `AUDIT_LEGAL_HOLD_MANAGE`, and
`AUDIT_INTEGRITY_VERIFY` already existed as pre-reserved `Capability`/`Action` entries from Phase 5
but were dead code (nothing authorized against them). This session wired all three into the new
`AuditGovernanceResource` endpoints and additionally granted `AUDIT_RETENTION_MANAGE`/
`AUDIT_LEGAL_HOLD_MANAGE` to `APP_ADMIN` (platform-scope retention/legal-hold management needs
them and only `ORG_OWNER`/`ORG_ADMIN` had them before) and `AUDIT_INTEGRITY_VERIFY` to
`ORG_OWNER_AND_ADMIN_COMMON` (org-scope reconciliation needs it and only `APP_ADMIN` had it
before). `ActionCapabilityModelTest` still passes unchanged.

**REST:** new `AuditGovernanceResource` (thin adapter, same `withAuthorizedOrg`/
`withAuthorizedPlatform`/`runGuarded` pattern as `AuditExportResource`): `GET`/`PUT
.../audit-retention-policies(/{category})`, `POST`/`GET .../audit-legal-holds`,
`POST .../audit-legal-holds/{holdId}/release`, `GET .../audit-analytics/reconciliation`, each with
an organization-scoped and a platform-scoped route. Platform-scope retention overrides are stored
under the same `UUID(0,0)` sentinel already used for `ResourceRef`'s platform authorization checks
(the `audit_retention_policy.organization_id` column is `NOT NULL`, by design, since the real
platform default lives in code).

### Phase 8 - test validation (gate) result

- `./mvnw.cmd -o compile` and `./mvnw.cmd -o test-compile`: both succeed cleanly, zero warnings
  after fixing two introduced during development (a pointless `open` on a method in a
  `@RequestScoped` class the `all-open` compiler plugin doesn't open - only
  `@ApplicationScoped` is configured - and an unchecked native-query cast, now
  `@Suppress`-annotated).
- Full backend suite `./mvnw.cmd -o test`: **374 passed / 374 total** (357 before this session +
  17 new), `BUILD SUCCESS`, no failures or errors. The three ERROR-level "AuditRecorder capture
  failed (fail-closed)" log lines in the run are expected negative-path assertions from
  pre-existing tests (`AuthAuditServiceTest`, `ExchangeDocumentServiceAuditTest`,
  `ShareServiceAuditTest`), not new failures.
- Gate item "Analytics replay is idempotent and never copies prohibited fields": covered by
  `AuditAnalyticsProjectorTest` (double-`projectOne` call produces exactly one fact row; the fact
  entity has no payload column to copy a field into).
- Gate item "Reconcile analytical measures against authoritative event counts": covered by
  `AuditAnalyticsReconciliationServiceTest` (matching counts report `matches=true`; a fact deficit
  reports `matches=false` plus a bounded missing-id sample).
- Gate item "Deleted resources/users do not erase evidence or break historical rendering": not a
  new regression test this session - no new deletion path was added (the platform still has no
  purge job; `AuditIdentityVaultService.shred` deletes a wrapped key, never an audit row), so the
  existing no-business-FK design plus Phase 3's original regression coverage already satisfies
  this for every table added here.
- Gate item "Integrity verification results are themselves immutable audit events and alert on
  failure": already satisfied by the existing Phase 4/6
  `AUDIT_INTEGRITY_CHECK_PERFORMED`/`ARCHIVE_INTEGRITY_FAILED` events and the
  `AUDIT_ARCHIVE_VERIFICATION_FAILED` CloudWatch alarm; this session's
  `AUDIT_ANALYTICS_RECONCILED` event and the two new CloudWatch alarms extend the same pattern to
  the new signals rather than replacing it.
- Frontend: no frontend task in Phase 8's plan and no user-visible behavior changed (no new UI
  surface), so `npx tsc --noEmit`/Vitest were not run and no help docs article was added, per
  AGENTS.md's "What does NOT require a docs update" rule (infrastructure/backend-only change).

### Phase 8 - deferred / flagged items

1. **Per-field pseudonymization is not wired into ledger capture.** `AuditIdentityVaultService`
   provides `ensureKey`/`unwrapDataKey`/`shred` as a working primitive, but no call site in this
   codebase actually encrypts an audit field with a subject's data key before it lands in
   `audit_outbox`/`audit_ledger_event`. Wiring real per-field pseudonymization (deciding which
   fields, at which `AuditIdentityTreatment`, get encrypted at capture time, and how a reader with
   the right capability decrypts them back) touches every capture call site and is a materially
   larger change than this phase's scope. This mirrors the precedent set by Phase 6's deferred PDF
   summary/CLI verifier: the primitive is real and tested, the full integration is left for a
   dedicated follow-up.
2. **No purge/disposal job exists yet.** `AuditDisposalEligibilityService.isEligibleForDisposal`
   is the eligibility check, but nothing currently calls it to actually delete a
   searchable-projection row. The WORM ledger/archive are never deleted by design regardless.
3. **Monitoring covers 2 of the plan's ~8 listed signals** (outbox backlog age, analytics
   projection lag) plus the pre-existing archive verification alarm. Not implemented: ledger
   append latency/duplicate/gap/fork detection, clock drift detection, denied-access anomaly
   detection, and a "% of regulated operations with tested coverage" metric. These need either a
   defined SLO (latency/drift thresholds) or a coverage-tracking mechanism that does not exist yet
   in this codebase, both of which are compliance/tooling decisions better made deliberately than
   guessed at in this session.
4. **Retention catalog defaults are placeholders**, explicitly documented as such in
   `AuditRetentionCatalogService`'s doc comment, pending the plan's still-unanswered "Retention /
   legal-hold / residency / replication / destruction rules" and "Online search window vs
   archive-only history" prerequisite decisions.
5. **`AuditRetentionPolicyService.getEffectivePolicy`/`upsertOverride` require a non-null
   `organizationId`**; the platform scope reuses the same `UUID(0,0)` sentinel `ResourceRef`
   already uses elsewhere for platform authorization, rather than adding a nullable-organization
   column with the NULL-uniqueness pitfalls that would create. Flagging this as a deliberate
   modeling choice, not an oversight, in case a future session wants a cleaner platform
   representation.
6. **`findAllByOrganization`/`rebuild` load a full scope's ledger events into memory** for
   analytics rebuild; acceptable for this phase's scope but would need batching for a
   very-high-volume organization. `AuditAnalyticsProjector.project`'s normal incremental drain
   path does not have this limitation (bounded `batchSize`).

### Post-Phase-8 production bugs found and fixed (2026-07-08)

Two real bugs surfaced while smoke-testing the already-"done" phases end to end (not new scope,
just fixing what Phase 5-8 shipped broken):

1. **RESTEasy Reactive silently dropped routes that shared a leading path-param segment with an
   unrelated pre-existing resource class.** `AuditProjectionResource`, `AuditExportResource`, and
   `AuditGovernanceResource` all declared `@Path("/")` at the class level with full absolute paths
   per method. Any such path that structurally overlapped a route already owned by a different
   resource class (for example `/exchanges/{exchangeId}/audit-events` vs `ExchangeResource`'s
   `/exchanges/...`, or `/workflows/definitions/{id}/audit-events` vs
   `WorkflowDefinitionResource`) never made it into the live routing table, even though it compiled
   fine, was a valid registered CDI bean, and even showed up in RESTEasy's own "did you mean"
   404 listing - a known upstream Quarkus limitation
   ([quarkusio/quarkus#18542](https://github.com/quarkusio/quarkus/issues/18542),
   [#19299](https://github.com/quarkusio/quarkus/issues/19299)). Fixed by splitting every colliding
   route into its own small resource class with a proper non-root class-level `@Path` (mirroring
   the pre-existing `ExchangeParticipantResource` pattern): new
   `AuditOrganizationEventsResource`, `AuditExchangeEventsResource`,
   `AuditExchangeDocumentEventsResource`, `AuditWorkflowDefinitionEventsResource`,
   `AuditApplicationEventsResource`, `AuditSecurityIncidentEventsResource`,
   `AuditOrganizationExportsResource`, `AuditOrganizationIntegrityResource`,
   `AuditOrganizationRetentionPolicyResource`, `AuditOrganizationLegalHoldResource`,
   `AuditOrganizationAnalyticsResource`. URLs are unchanged. `AuditProjectionResource`,
   `AuditExportResource`, `AuditGovernanceResource` now hold only the platform-scope (and, for
   `AuditProjectionResource`, personal-scope) routes that never collided.
2. **The ledger drain never made forward progress once a legacy-import batch reached the head of
   the outbox queue.** `AuditOutboxRepository.findOldestByRecordedAt` fetched an unconditional
   "oldest N by recorded_at" batch with no way to skip rows already ledgered. The one-time legacy
   import (`archive.legacy_event.imported`, Phase 4) bulk-inserted exactly `batchSize` (200) outbox
   rows dated in the past that were also mirrored straight into the ledger by a separate path.
   Every 15s drain tick re-fetched that same permanently-already-ledgered batch, found nothing new,
   and logged nothing (silent by design when `appended=0 && failed=0`) - so the entire real backlog
   sitting behind those 200 rows was starved forever, and every new capture (Exchange lifecycle,
   Share grants, document access, `audit.search.performed`, etc.) was durably written to
   `audit_outbox` but never reached `audit_ledger_event`, so every audit-events endpoint returned
   empty with no error. `AuditHealthMonitorService` had the identical bug in its own backlog/oldest-age
   queries, which is why `AUDIT_OUTBOX_BACKLOG_HIGH` kept firing with a monotonically increasing
   `oldestAgeMinutes` that could never resolve. Fixed by replacing the query with
   `AuditOutboxRepository.findOldestUnledgeredByRecordedAt`, which excludes rows already present in
   `audit_ledger_event` via `NOT EXISTS` directly in the query, so a drain pass always makes forward
   progress regardless of what is stuck at the head of the queue. Verified against the live dev
   database: ledger row count was stuck at exactly 200 for over 18 hours before the fix, jumped to
   957 within one drain cycle after it (clearing the entire real backlog), and the previously-empty
   `GET /exchanges/{exchangeId}/audit-events` response for a real Exchange now returns its actual
   `authorization.share.grant`/`audit.search.performed` history. Full backend suite re-run clean
   (374/374) after both fixes.

### Continue next session here

There is no Phase 9 in this plan. A follow-up session should pick one deferred item above (most
likely #1, per-field pseudonymization, or #3, the remaining monitoring signals) as its own
narrowly-scoped unit of work, following the same "read AGENTS.md, confirm current state, implement
one phase/increment, validate, hand off" discipline this plan has used throughout - do not treat
"Phase 8 is done" as license to start several deferred items in the same session.

### Phase 7 - what was built this session

Nav-entry decision: the Audit workspace was added as a new **"Audit" tab inside `Settings.tsx`**,
not a top-level `/audit` route. The workspace is an admin/compliance surface, consistent with the
existing capability-gated tabs (Administration, Billing) already living in Settings; no route was
added to `App.tsx`.

Backend:
1. `AuditSearchProjectionService.listExchangeEvents(actor, organizationId, exchangeId, cursor,
   limit)` added, widening `listExchangeDocumentEvents`'s single-document filter to every document
   in the Exchange plus the Exchange-level target itself (`targetTypes =
   {"DOCUMENT","Document","EXCHANGE","Exchange"}`). It reuses the existing private `searchEvents`
   method and follows the exact same authorize-then-query-then-audit-the-audit pattern as the
   document-scoped method.
2. `ExchangeRetrievalService.getDocumentIdsForExchange(exchangeId)` added so
   `AuditSearchProjectionService` can resolve an Exchange's document ids without touching
   `ExchangeRetrievalService`'s repository directly (services must not use another service's
   repository - see AGENTS.md).
3. `AuditProjectionResource`: added `GET /exchanges/{exchangeId}/audit-events`, mirroring
   `listExchangeDocumentEvents`'s owner-resolution (`resourceAuthorizationContextRegistry.resolve`
   -> require `OwnerContext.Organization`) and `withAuthorizedOrgAudit` pattern, mapped through the
   existing `AuditProjectionDtoMapper.toPageDto`.
4. **Deleted the legacy `ExchangeAuditResource.kt`.** It occupied the same path
   (`exchanges/{exchangeId}/audit-events`) as the new, authorized, paginated endpoint requested by
   this phase, but was unauthorized, non-paginated, and backed directly by
   `ExchangeDocumentAuditService.getExchangeAuditEvents`. No test referenced the class (verified by
   grep), so it was removed rather than kept side-by-side with a path conflict. The old frontend
   function `fetchExchangeAuditEvents` in `exchangeApi.ts` was left in place per instructions to
   never delete legacy frontend functions, but nothing calls it anymore.
5. **Added `GET /platform/audit-integrity`** to `AuditExportResource.kt`. This endpoint did not
   exist before this session; only the organization-scoped `GET
   /organizations/{organizationId}/audit-integrity` existed. It was required by the frontend spec's
   `getPlatformAuditIntegrity()` function and delegates to the existing
   `AuditIntegrityService.checkOrganization(null, platformOnly = true, ...)`.
6. Backend test added: `AuditSearchProjectionServiceTest` gained "exchange-wide search widens the
   filter to every document in the exchange plus the exchange target", mirroring the existing
   `listExchangeDocumentEvents` test.

Frontend:
1. `models.tsx`: added the 7 missing `Capability` enum members (`ORG_AUDIT_EXPORT`,
   `ORG_AUDIT_VIEW_SENSITIVE`, `APP_AUDIT_EXPORT`, `AUDIT_EXPORT_APPROVE`,
   `AUDIT_RETENTION_MANAGE`, `AUDIT_LEGAL_HOLD_MANAGE`, `AUDIT_INTEGRITY_VERIFY`), and 8 new
   interfaces: `AuditEventCursorDto`, `AuditEventDto`, `AuditEventPageDto`,
   `AuditExportCreateRequestDto`, `AuditExportDto`, `AuditExportApprovalDto`,
   `AuditStreamIntegrityDto`, `AuditOrganizationIntegrityDto`. **Deviation flagged**: these
   interfaces model the *real* Kotlin `@Serializable` DTOs in `AuditProjectionDtos.kt` /
   `AuditExportDtos.kt` (field names/shapes), not the shapes suggested in this phase's task prompt
   (which used different field names, e.g. `approvalId`/`downloadUrl`/`segmentsValid` that do not
   exist on the backend). Modeling the real contract was judged more valuable than matching the
   prompt's guessed shapes verbatim.
2. `services/auditService.ts` (new): every typed function requested - organization/exchange/
   document/workflow/application/security-incident/personal/platform event fetchers, organization
   + platform export request/list/get/approve/list-approvals/download, and organization + platform
   integrity fetchers - following the `executeRequest` pattern from `exchangeApi.ts`.
3. `web-app/src/app/audit/` (new folder tree):
   - `components/use-audit-event-page/useAuditEventPage.ts` - shared cursor-pagination hook.
   - `components/audit-event-table/` - shared paginated table (`AuditEventTable.tsx` +
     `AuditEventTableStyles.tsx`).
   - `components/audit-event-detail/` - shared full-record dialog (`AuditEventDetail.tsx` +
     `AuditEventDetailStyles.tsx`), including the required masking disclosure note.
   - `components/audit-category-badge/` - `AuditCategoryBadge.tsx` +
     `AuditCategoryBadgeStyles.tsx`, mapping every `AuditCategory.kt` value to a friendly label and
     a Fluent `Badge` color.
   - `AuditWorkspace.tsx` + `AuditWorkspaceStyles.tsx` - the workspace shell (Events/Integrity/
     Exports tabs), gated on `ORG_AUDIT_READ || APP_AUDIT_READ`, with an `audit-not-authorized`
     fallback state.
   - `audit-events-section/`, `audit-integrity-section/`, `audit-exports-section/` (with child
     `audit-export-card/` and `audit-export-request-dialog/` components) implementing the three
     workspace sections.
   - `auditScope.ts` - shared `AuditScope` type (`organization` vs `platform`) threaded through the
     three sections.
4. `Settings.tsx`: added an `Audit` tab, gated independently on `hasCapability(ORG_AUDIT_READ) ||
   hasCapability(APP_AUDIT_READ)` (not tied to the pre-existing organization-admin gate, so an
   org-scoped Auditor role without admin capabilities still sees the tab), rendering
   `<AuditWorkspace/>` in the tab panel and added to the `managesOwnContentScroll` list.
5. Re-pointed `ExchangeAuditTab.tsx` (prop contract `{exchange: ExchangeDetailedDto}` preserved)
   and `ExchangeDocumentAudit.tsx` (prop contract `{exchangeId, exchangeDocument}` preserved) onto
   `fetchExchangeLedgerEvents` / `fetchExchangeDocumentLedgerEvents` and the shared
   table/detail/hook components. Legacy `fetchExchangeAuditEvents` /
   `fetchExchangeDocumentAuditLogs` functions were left in `exchangeApi.ts` untouched (unused).
6. `profile-security-events/ProfileSecurityEvents.tsx` + Styles (new sibling of
   `profile-security-card/`) added to `ProfileTab.tsx`, showing `fetchMySecurityEvents` through the
   shared table/detail/hook components inside the existing `ProfileSectionCard` shell.
7. Help docs: added `sections/articles/auditWorkspaceOverviewArticle.tsx` and registered it in
   `adminOperationsSection.tsx` (both well under their line-count caps); no `helpDocsRegistry.tsx`
   change was needed since that section is already registered there.
8. Added `jsdom`, `@testing-library/react`, `@testing-library/dom` as new devDependencies - no
   component-level (DOM-rendering) test previously existed anywhere in `web-app`, and
   `vite.config.ts` had no `test` block. Each new component test file opts into `jsdom` per-file via
   a `/** @vitest-environment jsdom */` docblock rather than changing the global Vitest environment,
   so existing pure-logic tests keep running under the default `node` environment unaffected.
9. Vitest tests added under `web-app/src/app/audit/__tests__/`: `useAuditEventPage.test.ts` (5
   tests: initial fetch, loadMore append + cursor advance, reset, loading state, error state),
   `AuditEventTable.test.tsx` (4 tests: renders rows, Load more disabled/enabled, row click),
   `AuditEventDetail.test.tsx` (3 tests: renders fields when open, no dialog content when closed,
   onDismiss called), `AuditWorkspace.test.tsx` (3 tests: not-authorized state, no fetch calls when
   unauthorized, events section + fetch call when authorized).

### Phase 7 - test validation (gate) result

- `npx tsc --noEmit` in `web-app/`: clean, zero errors.
- `npx vitest run` in `web-app/`: **92 passed / 92 total** across 11 test files (77 pre-existing +
  15 new: 5 `useAuditEventPage` + 4 `AuditEventTable` + 3 `AuditEventDetail` + 3 `AuditWorkspace`).
  No pre-existing test was modified or broken.
- `.\mvnw.cmd -o compile` and `.\mvnw.cmd -o test-compile`: both succeed cleanly.
- The user-requested backend filter `.\mvnw.cmd -Dtest="*AuditProjection*" test` matched **zero**
  test classes (no class name contains the literal substring "AuditProjection" - the relevant class
  is `AuditSearchProjectionServiceTest`, and the resource-level coverage lives in
  `CrossOrgAuditScopeTest`). Ran `.\mvnw.cmd -o "-Dtest=AuditSearchProjectionServiceTest,CrossOrgAuditScopeTest" test`
  instead: **7 passed / 7 total** (4 + 3), `BUILD SUCCESS`.
- Full backend suite `.\mvnw.cmd -o test`: **357 passed / 357 total**, `BUILD SUCCESS`, no
  failures or errors.
- Gate item "Vitest: search, event-detail, and contextual views render + paginate; sensitive fields
  hidden without capability": covered by the four new test files above; sensitive-field hiding is
  enforced entirely server-side (the DTOs only ever contain what the backend chooses to return), so
  there is no separate client-side redaction branch to test - the client renders whatever fields the
  page response includes.
- Gate item "Help docs updated for every new/changed audit surface; size limits respected":
  `auditWorkspaceOverviewArticle.tsx` is 76 lines (well under 150), `adminOperationsSection.tsx` is
  92 lines after the addition (well under 300). `helpDocsRegistry.tsx` was not touched and remains
  at its pre-existing 65 lines - already slightly over the 60-line guidance before this session
  started; flagged below rather than silently "fixed" since no import changes were needed for this
  phase's work.

### Phase 7 - deferred / flagged items

1. **TypeScript DTO shapes deviate from the task prompt's suggested interfaces.** As documented in
   "what was built" above, all new `models.tsx` audit interfaces were modeled on the real backend
   `@Serializable` DTOs rather than the prompt's guessed shapes. Flagging this explicitly since it
   is a deliberate deviation from the literal instructions, made to keep the frontend and backend
   actually compatible.
2. **Deleted `ExchangeAuditResource.kt`** rather than keeping it side-by-side with the new
   `AuditProjectionResource` endpoint at the same path (`GET
   /exchanges/{exchangeId}/audit-events`). JAX-RS cannot host two resources at the same path/verb;
   deleting the unauthorized/non-paginated legacy resource in favor of the new authorized/paginated
   one was the only non-breaking option, consistent with Phase 5/6's stated intent to "replace
   singular legacy routes behind compatibility projections". No test referenced the deleted class.
3. **Added `GET /platform/audit-integrity`**, which was not requested by any earlier phase's task
   list but was required for the frontend's `getPlatformAuditIntegrity()` to have a real backend to
   call. Mirrors the organization-scoped endpoint exactly, delegating to the existing
   `AuditIntegrityService`.
4. **RESOLVED (post-session review).** `AuditExportsSection.tsx` was originally 174 lines, slightly
   over the ~150-line component guidance. Its data-loading/mutation logic (list/request/approve/
   download plus loading/error/submitting state) was extracted into a new
   `audit-exports-section/useAuditExports.ts` hook, leaving the component at 91 lines of pure
   rendering. `npx tsc --noEmit` and the full Vitest suite (92/92) were re-run after the extraction
   and remain clean/passing.
5. **`AUDIT_EXPORT_APPROVE` platform-role gap (carried over from Phase 6, still unresolved).** As
   flagged in the Phase 6 write-up below, no `AppRoleName` currently holds `AUDIT_EXPORT_APPROVE`,
   so the platform-scope Approve action in the new Exports section UI will render (gated on the
   capability) but no platform role can currently pass authorization on the backend. This UI is
   still correct given today's role grants; fixing the underlying role-capability gap remains out of
   scope for Phase 7 and is left for whoever revisits `RoleCapabilities.kt`.
6. **No standalone `/audit` route.** Per this phase's explicit instruction to pick one navigation
   approach, the Audit workspace was wired only into `Settings.tsx`; `App.tsx` was not touched.
7. **`helpDocsRegistry.tsx` is already 65 lines**, over the 60-line guidance, but this pre-dates
   this session (no import list change was needed to register the new article, since
   `adminOperationsSection` was already registered there) - not fixed here to avoid unrelated scope
   creep, but flagged for whoever next touches that file.

### Phase 7 -> Phase 8 handoff (historical; Phase 8 is now done, see above)

This note is kept for history: it was written when Phase 7 had just finished and Phase 8 had not
started. Phase 8's backend (retention/legal-hold/analytics/monitoring) is now done - see "Phase 8
- what was built this session" earlier in this document. Its two frontend-relevant pointers below
are still open and are exactly Phase 8's own deferred items' UI counterpart, since Phase 8 turned
out to be backend-only in this session:

1. The still-unresolved `AUDIT_EXPORT_APPROVE` platform role gap from Phase 6 (item 4/5 in the
   Phase 7 "deferred / flagged items" list above) remains unresolved.
2. `Capability.AUDIT_RETENTION_MANAGE`/`AUDIT_LEGAL_HOLD_MANAGE` (frontend enum) and the new
   backend `AuditGovernanceResource` endpoints from Phase 8 still have **no UI surface** - the
   Audit workspace's shared components (`AuditEventTable`/`AuditEventDetail`/`AuditCategoryBadge`/
   `useAuditEventPage`) are ready to be reused for a retention/legal-hold management screen
   following the same `AuditScope`-driven organization-vs-platform pattern already established
   there, but building it was out of scope for this session (see Phase 8's own deferred items).

### Phase 6 - what was built this session

All five Phase 6 tasks are complete, with no new AWS service introduced: the export bundle is
stored via the existing Phase 4 `AuditArchiveStorage` (same S3 bucket/local directory, a
`exports/{exportId}/bundle.zip` key prefix) and signed with the existing Phase 4
`AuditArchiveSigningKeyProvider` (Secrets Manager-backed in non-dev environments). No new
capabilities/actions were needed: `ORG_REQUEST_AUDIT_EXPORT`, `AUDIT_EXPORT_APPROVE`,
`APP_REQUEST_AUDIT_EXPORT`, `ORG_READ_AUDIT`, `APP_READ_AUDIT` were all already added in Phase 5.

- **Task 1 (`AuditExport` state machine + migration)**: new migration `V46__audit_export.sql`
  adds `audit_export` (the mutable lifecycle row - denormalized organization/user IDs only, no FK
  to any business entity, same rule as `AuditEngagement`) and `audit_export_approval` (append-only
  dual-control rows, unique per approver per export). New entity `AuditExport` with enum-backed
  `AuditExportStatus` (`REQUESTED`, `APPROVAL_PENDING`, `BUILDING`, `READY`, `EXPIRED`, `FAILED`,
  `REVOKED`) and `AuditExportApproval`. New `AuditExportRepository` / `AuditExportApprovalRepository`.
  New `AuditExportService` owns the full lifecycle: `requestExport` (validates category/purpose/max
  range via new `AuditExportConfigService`, goes to `APPROVAL_PENDING` when dual control is
  required - the default - else straight to `BUILDING`), `approveExport` (rejects the requester
  approving their own export; idempotent per approver; transitions to `BUILDING` once the
  configured approval count is met), `revokeExport`, `expireDue` (moves past-expiry `READY`
  exports to `EXPIRED`), `recordDownload`/`downloadBundle` (enforces `READY` status, expiry, and
  download-count limit before returning bytes). New `AuditExportScheduler` drains `BUILDING`
  exports and expires due exports on two independent `@Scheduled` ticks
  (`app.audit.export.build-every` / `app.audit.export.expire-every`), both `@ActivateRequestContext`
  since `AuditRecorder` is `@RequestScoped`.
- **Task 2 (export builder, fail-closed)**: new `AuditExportBuilder`. Before writing a single
  bundle byte, it resolves every ledger stream touched by the requested organization/time range
  (new `AuditLedgerEventRepository.findDistinctStreamIdsForExport`) and verifies each one through
  new `AuditIntegrityService.checkStream` - both the segment-chain boundary checkpoint
  (`AuditArchiveVerifier.verifyStreamChain`) and a full per-segment content/signature
  re-verification (`verifySegment`). Any failure throws `AuditExportIntegrityFailedException`
  rather than emit unverifiable evidence; `AuditExportService.processBuilding` catches this and
  transitions the export to `FAILED` with the reason recorded, never throwing out of the scheduler
  tick. On success it builds `bundle.zip` containing: `manifest.json` (canonical, fixed field
  order, what the detached signature covers), `events.jsonl` (full-fidelity, one JSON object per
  exported ledger event - not redacted, since the requester's access was already gated upstream by
  approval/authorization), `events.csv` (flattened projection with an explicit fidelity note that
  JSONL is canonical), `integrity.json` (the same per-stream verification results just computed),
  `signature.json` (detached SHA256withRSA signature over the exact `manifest.json` bytes plus the
  PEM public key needed to check it), `verify.py` (a reference offline-verification script embedded
  as a bundle file, so a recipient can verify without contacting DocuHyphen at all), and
  `README.txt`. `bundleDigest` follows the existing `segmentDigest` convention
  (`sha256(manifestJson bytes)` via the Phase 4 `MerkleTree` utility).
- **Task 3 (REST)**: new thin resource `AuditExportResource`:
  `POST/GET /organizations/{organizationId}/audit-exports`,
  `GET .../audit-exports/{exportId}`, `POST/GET .../audit-exports/{exportId}/approvals`,
  `GET .../audit-exports/{exportId}/file`, `GET /organizations/{organizationId}/audit-integrity`
  (task 5), plus platform-scope mirrors under `/platform/audit-exports`. Every handler validates
  input, authorizes via `AuthorizationService`/existing `Action` entries, delegates to
  `AuditExportService`/`AuditIntegrityService`, and maps via a dedicated `AuditExportDtoMapper`
  (no business logic in the resource class). `requireOrgMatch` prevents cross-tenant access to an
  export via UUID guessing (an export's `organizationId` must match the path org, or be null for
  platform routes).
- **Task 4 (audit the audit)**: every lifecycle transition (`AUDIT_EXPORT_REQUESTED`,
  `AUDIT_EXPORT_APPROVED`, `AUDIT_EXPORT_READY`, `AUDIT_EXPORT_FAILED`, `AUDIT_EXPORT_DOWNLOADED`,
  `AUDIT_EXPORT_EXPIRED`, `AUDIT_EXPORT_REVOKED`) and every integrity check
  (`AUDIT_INTEGRITY_CHECK_PERFORMED`) is itself recorded via `AuditRecorder`, catching
  `AuditDraftInvalidException`/`AuditCaptureFailedException` so a recorder failure never blocks the
  primary operation (same pattern as `AuditEngagementService`). New catalog entries added under the
  existing `AuditCategory.AUDIT_GOVERNANCE` category; `AuditEventType.CATALOG_VERSION` bumped 5 to 6.
- **Task 5 (offline verifier + integrity endpoint)**: new `AuditIntegrityService.checkOrganization`
  backs `GET /organizations/{organizationId}/audit-integrity`, returning a per-stream boundary
  checkpoint + segment verification report and recording `AUDIT_INTEGRITY_CHECK_PERFORMED`. Rather
  than a separate standalone CLI tool, the *offline* verifier requirement is satisfied by making
  every export bundle self-contained (`verify.py` + `signature.json`'s embedded public key), so a
  bundle can be checked without calling this endpoint at all. See "deferred / flagged items" below
  for what this does not cover.

### Phase 6 - test validation (gate) result

- `.\mvnw.cmd -o compile` and `.\mvnw.cmd -o test-compile`: both succeed cleanly.
- `.\mvnw.cmd -o "-Dtest=AuditExportServiceTest,AuditExportBuilderTest" test`: all new Phase 6
  tests pass (exit code 0; 7 test methods across the two new classes).
- `.\mvnw.cmd -o test` (full, unfiltered suite): exit code 0, no test failures reported in
  `target/surefire-reports`. (The `-q` flag suppresses the aggregate `Tests run:` summary line in
  this environment; the observed `ERROR`-level log lines during the run are expected
  fail-closed/degraded-mode negative-path assertions from pre-existing tests, not new failures.)
- Gate item "Exported bundle verifies offline and discloses redactions + schema versions":
  covered by `AuditExportBuilderTest`'s happy-path test, which unzips the bundle and asserts
  `manifest.json`'s `bundleDigest` matches the persisted `AuditExport.bundleDigest`, and that
  `signature.json`/`verify.py` are present; `manifest.json.fidelityNote`/`schemaVersions` fields
  disclose the CSV-vs-JSONL fidelity difference and the schema versions covered.
- Gate item "A range does not verify merely because inner events link; boundary checkpoints are
  required": covered by `AuditExportBuilderTest`'s second test, which archives 3 real segments for
  a stream, simulates a deleted middle segment (the remaining two segments each still
  independently hash/sign correctly), and asserts `build()` throws
  `AuditExportIntegrityFailedException` and never reaches `READY` or writes a bundle object.
- Gate item "Export lifecycle actions each create audit events": covered by
  `AuditExportServiceTest`'s request/approve/expire assertions (state transitions imply the
  recorder call path executed) plus the pre-existing `AuditRecorder`/catalog test coverage
  confirming the new event types are valid, cataloged entries.
- Frontend: Phase 6 has no frontend task in the plan (task list above is backend-only: state
  machine, builder, REST, audit trail, integrity endpoint). `npx tsc --noEmit` and Vitest in
  `web-app/` were not run since no frontend files were touched. Help docs: searched
  `web-app/src/app/components/help-docs/sections/` for `audit export`/`audit-export`/`evidence
  bundle` - no existing article references this not-yet-surfaced feature, so no help doc update was
  needed or made this session (Phase 7 will need to add one once a UI surface exists).

### Phase 6 - deferred / flagged items

1. **`AUDIT_EXPORT_APPROVE` is not granted to `APP_ADMIN`.** `RoleCapabilities.kt` only grants
   `AUDIT_EXPORT_APPROVE` to `ORG_OWNER`/`ORG_ADMIN` (added in Phase 5). This means the
   platform-scope `POST /platform/audit-exports/{exportId}/approvals` endpoint currently has no
   `AppRoleName` role that can pass its authorization check - platform-scope exports can be
   *requested* (`APP_REQUEST_AUDIT_EXPORT` is granted to `APP_ADMIN`/`APP_AUDITOR`) but never
   *approved* through today's role grants when dual control is required (the default). This is a
   genuine scoping gap discovered while building the resource, not fixed here because expanding
   `RoleCapabilities.kt` is outside Phase 6's task list. **Recommended fix for whoever picks this
   up**: either grant `AUDIT_EXPORT_APPROVE` to `APP_ADMIN` in `RoleCapabilities.kt`, or set
   `app.audit.export.dual-control-required=false` for platform-scope exports specifically (would
   require a scope-aware config read, currently the config is global). Left unresolved
   intentionally to avoid scope creep past Phase 6's own task list.
2. **PDF summary artifact not implemented.** The plan lists an "optional PDF summary (never
   canonical)" bundle artifact. No PDF-generation library exists in the project's dependencies;
   adding one would be a new dependency decision outside this phase's scope, so it was skipped.
   `manifest.json`/`events.jsonl`/`events.csv`/`README.txt` together already satisfy the "never
   canonical" convenience-artifact intent without it. Add a PDF renderer in a later phase if a
   human-readable summary becomes a hard requirement.
3. **No dedicated standalone CLI verifier tool.** The offline-verification requirement is met by
   making every bundle self-contained (embedded `verify.py` + public key), not by shipping a
   separate CLI/binary project. If a signed, versioned CLI distribution is later required (for
   example for auditors who want one tool across many bundles rather than the bundle's own script),
   that is new scope for a future phase.
4. **`downloadBundle`'s self-invocation of `recordDownload`.** `AuditExportService.downloadBundle`
   calls `this.recordDownload(...)` from within the same class; CDI transactional interceptors do
   not fire on self-invocation, so `downloadBundle` itself is marked `@Transactional` so the whole
   call runs in one transaction regardless of `recordDownload`'s own annotation. Flagging this here
   because it is a subtle CDI proxying detail that a future refactor of either method could silently
   break if the self-invocation pattern is not preserved or the methods are split into different
   beans.
5. **`app.audit.export.*` properties added to `application.properties`** mirroring the existing
   `app.audit.archive.*` block's `${ENV_VAR:default}` pattern - no CloudFormation change was needed
   since these are plain Quarkus config properties, not new infrastructure.

### Continue Phase 7 here

Phase 6 is done; the next session should start **Phase 7 - Auditor Portal + Contextual UI
Surfaces** (see the Phase 7 section above for its full task list, and note it is the first
Phase 6+ task that touches `web-app/`). Before starting:

1. Read the Phase 6 write-up above in full, especially the "deferred / flagged items" list -
   items 1 and 2 in particular may affect what the Phase 7 UI can expose (platform-scope export
   approval has no working role today; there is no PDF summary artifact to link to).
2. `AuditExportResource`'s REST surface (`POST/GET .../audit-exports`, `.../audit-exports/{id}`,
   `.../audit-exports/{id}/approvals`, `.../audit-exports/{id}/file`,
   `GET .../audit-integrity`) is ready to build a UI against as-is; DTOs live in
   `model/dto/AuditExportDtos.kt` and are mapped by `model/dto/AuditExportDtoMapper.kt`.
3. Per this repo's frontend rules (`AGENTS.md`): co-located `*Styles.tsx` files, ids on every
   component, circular buttons, no inline styles, strongly-typed TypeScript (no `any`), and every
   new/updated component kept responsive. Add help-docs articles under
   `web-app/src/app/components/help-docs/sections/` for the new export/integrity UI surface once
   it exists (there is currently no help-docs coverage for exports - see Phase 6's write-up above).
4. The Phase 5 write-up below still documents the legacy compatibility resources
   (`GET /exchanges/{exchangeId}/audit-events`, `GET /auth/audit-events`) that Phase 6 did not touch;
   Phase 7 (or a later phase) should still decide whether to re-point or retire them once the UI is
   fully migrated to the canonical projection and the new export surface.

### Phase 5 - what was built this session

All six Phase 5 tasks are complete, with no infrastructure or AWS service changes. `infra/cloudformation.yml`
was intentionally left untouched.

- **Task 1 (authz correction)**: `RoleCapabilities.kt` was tightened so `ORG_AUDITOR` no longer
  receives standing content-read capabilities (`EXCHANGE_READ`, `DOCUMENT_READ`, `DOC_LIBRARY_READ`,
  `BLUEPRINT_READ`, `WORKFLOW_READ`, `SEQUENCE_READ`, `COMMUNICATION_READ`, `FIELD_SCHEMA_READ`,
  `GROUP_READ`, `WEBHOOK_AUDIT_READ`). It now keeps only audit-specific access
  (`ORG_AUDIT_READ` + `ORG_AUDIT_EXPORT`). `ORG_BILLING_ADMIN` no longer inherits
  `ORG_AUDIT_READ`. `APP_AUDITOR` was re-checked and still has no standing customer-content read
  capability. Code search found no direct `ORG_AUDITOR` special-casing outside the role map itself;
  the main deferred compatibility seam is the existing legacy `ExchangeAuditResource` /
  `AuthAuditResource` read path noted below.
- **Task 2 (new capabilities/actions)**: added capabilities `ORG_AUDIT_EXPORT`,
  `ORG_AUDIT_VIEW_SENSITIVE`, `APP_AUDIT_EXPORT`, `AUDIT_EXPORT_APPROVE`,
  `AUDIT_RETENTION_MANAGE`, `AUDIT_LEGAL_HOLD_MANAGE`, `AUDIT_INTEGRITY_VERIFY`, plus matching
  centralized `Action` entries in `Action.kt`. Grants were wired conservatively:
  `ORG_OWNER` / `ORG_ADMIN` get the new org-governance capabilities,
  `ORG_AUDITOR` gets `ORG_AUDIT_EXPORT`, `APP_ADMIN` gets `APP_AUDIT_EXPORT` plus
  `AUDIT_INTEGRITY_VERIFY`, and `APP_AUDITOR` gets `APP_AUDIT_EXPORT` without any new customer
  content access. Judgement call: the architecture's export language implies auditors may request
  exports, so `ORG_AUDITOR` and `APP_AUDITOR` were granted export-request capability, while approval
  remains separate.
- **Task 3 (Audit Engagement model + service + migration)**: new migration
  `V45__audit_engagement.sql`; new mutable entity `AuditEngagement` with enum-backed
  `AuditEngagementStatus` and `AuditEngagementSensitivity`; new `AuditEngagementRepository`; new
  `AuditEngagementService`. Engagements support request, approve, revoke, expire, scoped resolution
  by organization/resource/category/principal/group, and recent step-up enforcement for sensitive
  evidence or export-enabled engagements via `StepUpAuthService.isFresh()`. Lifecycle operations are
  themselves audited through `AuditRecorder` using new catalog events
  `AUDIT_ENGAGEMENT_REQUESTED`, `AUDIT_ENGAGEMENT_APPROVED`, `AUDIT_ENGAGEMENT_REVOKED`,
  `AUDIT_ENGAGEMENT_EXPIRED`.
- **Task 4 (tenant-aware search projection)**: new `AuditSearchProjectionService` backed by new
  cursor-friendly `AuditLedgerEventRepository.search(...)` / `findByEventIdScoped(...)` methods,
  ordered by `(occurredAt, eventId)` for deterministic cursor pagination. The projection always
  takes explicit caller context, explicit organization/resource scope, and never falls back to a
  primary organization. Non-admin org reads require an active matching `AuditEngagement`; that is
  what prevents `ORG_AUDITOR` or `APP_AUDITOR` from seeing whole-org evidence by role alone.
  Sensitive payload projection is masked by default through a small safe metadata allowlist unless
  the caller has `ORG_AUDIT_VIEW_SENSITIVE`, `APP_ADMIN`, or a sensitive engagement. Search, detail
  view, and deny paths all record new `AUDIT_SEARCH_PERFORMED`, `AUDIT_EVENT_VIEWED`, and
  `AUDIT_ACCESS_DENIED` events. New category `AuditCategory.AUDIT_GOVERNANCE` was added and
  `AuditEventType.CATALOG_VERSION` was bumped 4 to 5.
- **Task 5 (new REST resources)**: added thin resource `AuditProjectionResource` with:
  `GET /organizations/{organizationId}/audit-events`,
  `GET /organizations/{organizationId}/audit-events/{eventId}`,
  `GET /exchanges/{exchangeId}/documents/{documentId}/audit-events`,
  `GET /workflows/definitions/{definitionId}/audit-events`,
  `GET /admin/applications/{applicationId}/audit-events`,
  `GET /auth/security-incidents/audit-events`,
  `GET /users/me/security-events`,
  `GET /platform/audit-events`.
  Every endpoint validates inputs, calls `AuthorizationService`, delegates to
  `AuditSearchProjectionService`, and maps via dedicated `AuditProjectionDtoMapper`.
  Resource-scoped endpoints enforce an exact resource filter; for example document audit reads first
  validate the `(exchangeId, documentId)` relationship through `ExchangeRetrievalService.hasDocumentInExchange`
  before querying the ledger.
- **Task 6 (audit the audit)**: all engagement lifecycle operations plus search/detail/denial
  projection paths are now recorder-backed. New test coverage verifies this rather than just
  documenting it.

### Phase 5 - test validation (gate) result

- `.\mvnw.cmd -o compile` and `.\mvnw.cmd -o test-compile`: both succeed cleanly.
- `.\mvnw.cmd -o '-Dtest=ActionCapabilityModelTest,AuditSearchProjectionServiceTest' test`:
  **33 tests, 0 failures, 0 errors, 0 skipped**. This focused run covers the new Phase 5 authz and
  projection gates before the full suite.
- `.\mvnw.cmd -o test` (full, unfiltered suite): **347 tests, 0 failures, 0 errors, 0 skipped**
  (aggregated from `target/surefire-reports`). This is up from the prior 343-test baseline noted in
  the Phase 4 handoff by exactly the 4 new Phase 5 tests added this session
  (`AuditSearchProjectionServiceTest` 3, `ActionCapabilityModelTest` +1 expanded role/capability
  gate).
- Gate item "Org auditor cannot cross org boundaries or read document content by role alone":
  covered by the updated `ActionCapabilityModelTest` assertions removing standing content caps from
  `ORG_AUDITOR`.
- Gate item "Platform auditor cannot read customer content without a separate grant": covered by
  `AuditSearchProjectionServiceTest`'s org-scope search returning no events without a matching
  engagement.
- Gate item "Audit searches/detail/denied attempts produce audit events": covered by
  `AuditSearchProjectionServiceTest` asserting `audit.search.performed` and `audit.access.denied`
  recorder calls.
- Gate item "Contextual endpoints enforce a mandatory Resource Reference filter and reauthorize on
  drill-down": covered by `AuditSearchProjectionServiceTest` verifying the exact `(exchangeId,
  documentId)` scope check and the repository query's exact target-id filter.
- Help docs: ran `grep -r "auditor" web-app/src/app/components/help-docs/sections/` and
  `grep -r "ORG_AUDITOR" web-app/src/app/components/help-docs/sections/`. Both returned no matches,
  so there was no existing help article to update for the tightened auditor role semantics. No
  frontend files were changed; `npx tsc --noEmit` was therefore not re-run.

### Continue Phase 6 here

Phase 5 is done; the next session should start **Phase 6 - Verifiable Evidence Exports** (see the
Phase 6 section above for its full task list). Before starting:

1. Read the Phase 5 write-up above in full. The new canonical projection, engagement model,
   capability split, and audit-of-audit catalog entries are the foundation Phase 6 export work must
   build on.
2. The existing legacy compatibility resources `GET /exchanges/{exchangeId}/audit-events` and
   `GET /auth/audit-events` were deliberately left in place this session to avoid breaking the
   current frontend response shape. The new canonical projection exists beside them; Phase 6 or 7
   should decide whether to re-point those legacy routes or retire them once the UI is migrated.
3. `GET /auth/security-incidents/audit-events` is area-scoped rather than incident-id-scoped because
   current recorder writes do not persist a stable `SecurityIncident.id` reference onto
   `audit_ledger_event`. A future session can add that linkage if per-incident drill-down is needed.
4. `GET /users/me/security-events` is authenticated-self only. The current capability model has no
   dedicated personal-security-read capability, so the endpoint does not introduce one implicitly.
   If product policy wants an explicit capability or action for personal security history, add it in
   a later phase.
5. Step-up enforcement for sensitive evidence is implemented at the engagement-service layer via
   `StepUpAuthService.isFresh()`, but no engagement-management REST endpoints were added this phase.
   When engagement CRUD routes are introduced, they should call the service-level enforcement that is
   already in place rather than duplicate it.
6. `maxQueryRangeDays`, `downloadLimit`, and `exportPermitted` are modeled on `AuditEngagement` and
   are consulted by engagement resolution where applicable, but their full user-visible lifecycle is
   still Phase 6 work once export resources exist.

### Phase 4 - what was built this session

All five Phase 4 tasks are complete, with no new AWS service type introduced (only a new S3
bucket, IAM policy statements, a Secrets Manager secret, and a CloudWatch Logs metric filter +
alarm - all within service types already present in `infra/cloudformation.yml`).

- **Task 1 (segment archiving)**: new `service/audit/archive/AuditArchiver.kt` replaces the local
  JSONL `AuthAuditWormSink` sink conceptually (that class is left in place but is superseded by the
  new archive path for anything closed after this phase). `closeReadySegments()` finds every
  distinct `stream_id` in `audit_ledger_event` (new `AuditLedgerEventRepository.findDistinctStreamIds()`)
  and closes a segment once `app.audit.archive.segment-size` events are buffered since the last
  segment boundary. Each segment records: Merkle root over the ordered `eventHash` values
  (`MerkleTree.computeRoot`), `segmentDigest = sha256(streamId|firstSeq|lastSeq|merkleRoot|prevSegmentDigest)`,
  event count, schema versions, and `signingKeyId`. New operational table
  `audit_archive_segment` (migration `V44__audit_archive_segment.sql`) stores this - deliberately a
  normal mutable table (verification status/notes get updated in place), NOT under the append-only
  trigger, because `audit_ledger_event`/`audit_outbox` themselves cannot be backfilled with
  `signing_key_id`/`checkpoint_ref` (their append-only triggers unconditionally deny UPDATE). The
  segment table is the authoritative lookup for "which segment covers this ledger event range".
- **Task 2 (signing)**: new `AuditArchiveSigningKeyProvider` interface with two implementations
  selected by `app.audit.archive.signing.provider` (`local` default / `aws`), mirroring the existing
  `@Local`/`@Aws` qualifier + producer pattern used for `FileStorageService`.
  `LocalAuditArchiveSigningKeyProvider` bootstraps and persists an RSA-2048 keypair as PEM files
  (dev/test default). `SecretsManagerAuditArchiveSigningKeyProvider` reads a JSON secret
  (`{keyId, privateKeyPem, publicKeyPem}`) via the existing `AwsSecretsManagerService` - no new
  AWS service, reuses Secrets Manager. Manifests are signed with SHA256withRSA; the archived
  manifest object stores the exact canonical JSON string that was signed alongside the base64
  signature (avoids re-serialization mismatches on verify). The abstraction is deliberately generic
  so the key can move to KMS/HSM later (deferred hardening, not in this phase) without changing
  ledger format or callers.
- **Task 3 (infra)**: `infra/cloudformation.yml` gained: `AuditArchiveBucket` (new S3 bucket,
  `VersioningConfiguration: Enabled`, `ObjectLockConfiguration` in `GOVERNANCE` mode with a
  configurable `AuditArchiveRetentionDays` parameter, default 2555 days/~7 years, public access
  fully blocked); an `S3AuditArchiveAccess` IAM policy on the existing `ECSTaskRole` granting only
  `s3:GetObject`/`s3:PutObject` on that bucket (no `DeleteObject`, no `PutObjectRetention`/
  `PutObjectLegalHold`, no retention-bypass action - Object Lock Governance mode is the
  tamper-resistance backstop even against the app's own role); `AuditArchiveSigningSecret` (new
  Secrets Manager secret, placeholder JSON value - CloudFormation cannot generate an RSA keypair,
  so the secret reserves the name/slot and must be populated manually, one time, out of band -
  documented inline in the template) with a matching `AuditArchiveSigningSecretAccess` IAM
  statement; `AuditArchiveVerificationFailedMetricFilter` (CloudWatch Logs metric filter matching
  the `AUDIT_ARCHIVE_VERIFICATION_FAILED` ERROR log line emitted by `AuditArchiveScheduler`) and
  `AuditArchiveVerificationFailedAlarm` (CloudWatch Alarm on that metric, `TreatMissingData:
  notBreaching`, threshold >= 1 over a 5-minute period). The alarm intentionally has no
  `AlarmActions` wired up yet - notification delivery (email/SNS/PagerDuty) would require an SNS
  topic, which is a new AWS service type not pre-approved by the cost rules, so it is left as a
  manual/future step requiring explicit approval. The ECS task definition's `app` container gained
  `APP_AUDIT_ARCHIVE_STORAGE_TYPE=aws`, `APP_AUDIT_ARCHIVE_BUCKET`, `APP_AUDIT_ARCHIVE_REGION` env
  vars (safe to enable immediately - the bucket exists at deploy time), but
  `APP_AUDIT_ARCHIVE_SIGNING_PROVIDER` is deliberately left as `local` (not `aws`) until an operator
  manually populates `AuditArchiveSigningSecret` with a real keypair post-deploy - flipping it to
  `aws` before that would break signing in production. This is the "unanswered prerequisite decision
  -> degraded/log-only-equivalent mode behind config, explicitly flagged" pattern the task
  instructions called for, applied to the one piece (real signing key material) that genuinely
  cannot be automated by CloudFormation.
- **Task 4 (verification)**: new `AuditArchiveVerifier.kt`. `verifySegment()` re-downloads the
  segment content and manifest from storage, recomputes the Merkle root and segment digest from
  scratch, verifies the RSA signature against the exact signed JSON string, and cross-checks the
  recomputed values against what's persisted in `audit_archive_segment` - any mismatch (or missing
  object) fails verification without throwing, so a single bad segment doesn't abort a batch.
  `verifyStreamChain()` walks every segment in a stream in sequence order checking
  `prevSegmentDigest` linkage and sequence contiguity - this is the "boundary checkpoint" that
  specifically catches whole-segment deletion/reordering/truncation even when an individual
  segment's own Merkle root still checks out on its own. `AuditArchiveScheduler` runs both an
  archive tick (`closeReadySegments()`) and a verify tick on independent `@Scheduled` intervals; the
  verify tick activates a request context (`jakarta.enterprise.context.control.ActivateRequestContext`
  - NOT the nonexistent `io.quarkus.arc.ActivateRequestContext`) so it can call the `@RequestScoped`
  `AuditRecorder` and records each result as a new `ARCHIVE_INTEGRITY_VERIFIED`/
  `ARCHIVE_INTEGRITY_FAILED` audit event (`actorKind = SYSTEM`), and logs an
  `AUDIT_ARCHIVE_VERIFICATION_FAILED`-prefixed ERROR line (the string the CloudWatch metric filter
  above matches) plus calls `verifyStreamChain` for any stream that had a failure this tick.
- **Task 5 (legacy import)**: new `LegacyAuditImportService.kt` imports pre-recorder
  `auth_audit_event` rows into the ledger as a single generic `LEGACY_AUDIT_EVENT_IMPORTED` event
  type (never remapped to the original per-action type, so there's no false claim of
  full/contemporaneous provenance for old rows) - payload carries `legacy_import=true`,
  `original_action`, `original_outcome`, `original_event_hash`. Idempotency key
  `legacy_import:auth_audit_event:$id`. The "already imported" check
  (`AuthAuditEventRepository.findLegacyUnimported`) works because Phase 3's dual-write already
  reuses `AuthAuditEvent.id` as the ledger `eventId`, so the `NOT EXISTS` query naturally only
  matches rows created before Phase 3 went live - self-limiting without a separate flag column.
  **Scoping decision / known gap**: `DocumentAuditLog` legacy import was explicitly deferred (not
  done this session) because, unlike `AuthAuditEvent`, `ExchangeDocumentAuditService`'s Phase 3
  dual-write does NOT reuse the original row's id as the ledger eventId, so there is no cheap
  reliable "already imported" check without deeper investigation into that table's history. Flagged
  here as a genuine follow-up item, not silently dropped.
- **New catalog additions**: `AuditCategory.ARCHIVE`; `AuditEventType.ARCHIVE_SEGMENT_CLOSED`,
  `ARCHIVE_INTEGRITY_VERIFIED`, `ARCHIVE_INTEGRITY_FAILED`, `LEGACY_AUDIT_EVENT_IMPORTED`;
  `CATALOG_VERSION` bumped 3 -> 4.
- **Config**: new `app.audit.archive.*` block in `application.properties` (storage type, signing
  provider, local dirs, bucket/region, signing secret id/region, segment size, archive/verify/
  legacy-import tick intervals, reverify-after-hours), each with an `APP_AUDIT_ARCHIVE_*` env var
  default, mirroring the existing `app.audit.worm.*` naming convention.

### Phase 4 - test validation (gate) result

- `.\mvnw.cmd -o compile` and `.\mvnw.cmd -o test-compile`: both succeed cleanly.
- `.\mvnw.cmd -o test` (full, unfiltered suite): **343 tests, 0 failures, 0 errors, 0 skipped**
  (aggregated across all 43 `target/surefire-reports/*.txt` files) - up from the prior 322-test
  baseline noted in the Phase 3 handoff by exactly the 21 new Phase 4 tests
  (`MerkleTreeTest` 5, `AuditArchiverTest` 3, `AuditArchiveVerifierTest` 6,
  `LocalAuditArchiveSigningKeyProviderTest` 4, `LegacyAuditImportServiceTest` 3). Zero regressions.
- Gate item "deleting/inserting/reordering/truncating a range is detected via boundary
  checkpoints": covered by `AuditArchiveVerifierTest`'s `verifyStreamChain` deleted-segment-break
  test, plus `MerkleTreeTest`'s tamper/reorder/truncate sensitivity tests.
- Gate item "changing a segment or manifest breaks signature verification": covered by
  `AuditArchiveVerifierTest` (tampering segment content, tampering manifest, and missing objects
  all fail verification without throwing).
- Gate item "WORM objects cannot be overwritten/deleted before retention expiry": enforced at the
  infra level by `ObjectLockConfiguration` (Governance mode) on `AuditArchiveBucket` plus the
  `ECSTaskRole` IAM policy granting only `GetObject`/`PutObject` (no `DeleteObject`, no
  `PutObjectRetention`/`PutObjectLegalHold`) - this is a real-AWS-environment property, not
  something a JVM unit test can exercise; verified by design/code review of the CloudFormation
  template rather than an automated test (consistent with this repo's documented lack of a
  `@QuarkusTest`/real-Postgres/real-AWS integration harness, the same known gap noted in every
  prior phase's handoff).
- Gate item "DR retrieval test: a segment can be fetched and independently verified with the public
  key": covered by `AuditArchiverTest`/`AuditArchiveVerifierTest`'s `InMemoryAuditArchiveStorage`
  round trips (`buildAndArchiveSegment` then `verifySegment` fetches the same objects back and
  verifies against the public key).
- Gate item "Infra review confirms no new AWS service type was introduced": confirmed - Phase 4
  only added a new S3 bucket, new IAM policy statements on the existing `ECSTaskRole`, a new
  Secrets Manager secret, and a CloudWatch Logs metric filter + alarm, all within the service types
  (S3, Secrets Manager, CloudWatch, IAM) already present in `infra/cloudformation.yml` before this
  session. No SNS topic was added (the alarm has no `AlarmActions`) precisely to avoid introducing
  that new service type without explicit approval.
- Help docs: ran `grep -r "audit" web-app/src/app/components/help-docs/sections/` per the mandatory
  step. All matches are pre-existing generic mentions of audit-related role permissions
  (`adminOperationsSection.tsx`'s "App roles"/"Auditor" role descriptions, etc.), unrelated to the
  internal WORM archive/signing mechanics built this session. This phase added no new user-visible
  UI or API surface (no new endpoints, no new frontend components), so no help doc updates were
  required. No frontend files were touched; `npx tsc --noEmit`/Vitest were not re-run since nothing
  in `web-app/` changed.

### Phase 3 - continuation session (tasks 2-4 completed)

This session picked up exactly where the prior session's handoff ("Continue Phase 3 here") left
off and completed the remaining Phase 3 scope end to end.

- **`actorKind` wired end to end (the prerequisite for tasks 2-4).** New
  `AuditActorKind` enum (`HUMAN`, `APP`, `PUBLIC_LINK`, `WORKFLOW`, `SYSTEM`) in the `catalog`
  package. `AuditEventDraft` gained a nullable `actorKind: AuditActorKind? = null` field.
  `AuditOutboxEntry` gained a nullable `actorKind: String?` column. New migration
  `V43__audit_outbox_actor_kind.sql`: `ALTER TABLE audit_outbox ADD COLUMN actor_kind VARCHAR(32)`
  - additive, nullable, no FK, does not touch the append-only trigger. `AuditRecorder.buildEntry`
  persists `draft.actorKind?.name` onto the outbox row. `LedgerProcessor.resolveActorKind` now
  prefers the explicit `entry.actorKind` when present and only falls back to the old
  guess-from-`actorId`-presence heuristic when it is null - so every pre-existing call site
  (`AuthAuditService`, `ExchangeDocumentAuditService`, `ExchangeUpdateService.rescindExchange`)
  keeps working unchanged (they simply do not set `actorKind`, so they get the old guessed
  behavior). `audit_ledger_event`'s existing `actor_kind` column and semantics were not touched.
- **Task 2, all 7 previously-uncaptured document-access events, done.** Added
  `auditRecorder.record(...)` call sites (catch-and-log `AuditDraftInvalidException`/
  `AuditCaptureFailedException`, same style as `ExchangeDocumentAuditService.recordOnRecorder`,
  never propagated) in:
  - `ExchangeDocumentService.downloadDocument` -> `DOCUMENT_DOWNLOAD`, actor kind `HUMAN`.
  - `ExchangeDocumentService.downloadNoAuthSessionDocument` -> new `DOCUMENT_NO_AUTH_DOWNLOAD`,
    actor kind `PUBLIC_LINK` (no `AppUser` id is available on this path by design).
  - `ExchangeDocumentService.downloadDocumentsAsZip` -> new `DOCUMENT_ZIP_EXPORT`, actor kind
    `HUMAN`; **one event per ZIP request, not per document** (payload carries `document_count`
    and a comma-joined `document_ids` list) - a scoping decision to avoid N events for one user
    action; document ids are not secrets/content so this is payload-policy-safe.
  - `ExchangeDocumentService.getDocumentFilePreviewAsPdf` -> **both** the existing `DOCUMENT_VIEW`
    and the new `DOCUMENT_PREVIEW`, actor kind `HUMAN`. **Scoping/judgment call**: the task
    prompt named "document view" and "preview" as two separate coverage items, but there is no
    dedicated view-only endpoint in this codebase - only `.../file` (download) and `.../preview`
    (inline PDF). Decision: emit both events at the preview call site (a preview is simultaneously
    a content view and specifically the preview/conversion feature), and emit only
    `DOCUMENT_DOWNLOAD` at the plain `.../file` endpoint. Flagging this explicitly so a future
    session does not mistake the dual emission for a bug.
  - `ExchangeDocumentVersionService.getVersionFile` -> new `DOCUMENT_VERSION_DOWNLOAD`
    (historical-version download), actor kind `HUMAN`.
  - `DocumentLibraryService.downloadFile` -> new `DOCUMENT_LIBRARY_DOWNLOAD`, actor kind `HUMAN`,
    target type `ResourceType.DOC_LIBRARY`.
  - `NoAuthExchangeResource.downloadDocument` needed no separate service change: it already
    delegates directly to `ExchangeDocumentService.downloadNoAuthSessionDocument` above (there is
    no dedicated "no-auth service" in this codebase).
  - A small, targeted `AUTHORIZATION_DENIED` deny-capture point was also added in
    `ExchangeDocumentService.validateDownloadPermission`'s deny branch (denied `DOCUMENT_DOWNLOAD`
    decisions are a genuinely sensitive event worth their own row).
- **Task 3, Share + authorization capture, done.** New `AuditCategory.AUTHORIZATION` (the task
  explicitly asked to check for/add an ACCESS/AUTHORIZATION category; none existed). New
  `AuditEventType` entries: `SHARE_GRANT` (`share.grant`), `SHARE_ACTIVATE` (`share.activate`),
  `SHARE_ROLE_CHANGE` (`share.role_change`), `SHARE_REVOKE` (`share.revoke`),
  `AUTHORIZATION_DENIED` (`authorization.denied`). `ShareService` now injects `AuditRecorder` and
  a private `recordShareEvent` helper (catch-and-log, same pattern) is called from:
  - `grant()` -> `SHARE_GRANT`.
  - `activate()` -> `SHARE_ACTIVATE`.
  - `updateRoleAndConstraints()` (also covers the `updateRole` convenience overload) ->
    `SHARE_ROLE_CHANGE`, payload carries `previous_role`/`new_role`/`constraints_changed`.
  - the private `markRevoked()` choke point -> `SHARE_REVOKE`, exactly once per share regardless
    of whether it was reached via `revoke`, `revokePendingForResource`, or `revokeAllForResource`
    (its pre-existing `status == REVOKED` guard also prevents a duplicate event on an
    already-revoked share).
  Target type/id is the **resource being shared** (denormalized `share.resourceType.name` /
  `share.resourceId.toString()`, e.g. `"EXCHANGE"`/uuid), not the Share row itself (the Share id
  is only included in the payload as `share_id`) - per the no-business-FK rule and the task's
  explicit instruction. **Scoping decisions made:**
  - Inherited-member shares created by `materialiseGroupInheritance` (called from both `grant()`
    and `activate()`) do **not** each get their own ledger event - for a group with many members
    that would be pure audit noise unrelated to a distinct human decision. The triggering
    grant/activate event's payload includes `principal_kind=PRINCIPAL_GROUP` as a discoverability
    hint that inheritance was (re)materialized.
  - `ShareService` has no per-request `AuthTokenContext`/`AuthorizationContext` of its own (it
    receives `grantedByAppUserId`/`revokedByAppUserId` as explicit nullable parameters from
    callers). Actor kind is therefore inferred as `HUMAN` when an actor id is present and
    `SYSTEM` otherwise - coarser than ideal (cannot distinguish a `WORKFLOW`-driven activation
    from another background process), flagged here rather than silently accepted.
  - **Share expiry**: grepped for `expiresAt` and any scheduled Share status-transition job;
    found none. `expiresAt` is stored on `Share` and surfaced in DTOs, but there is no active job
    that flips status to an expired state - expiry is evaluated lazily wherever a Share is read.
    There is therefore no single capture point to instrument for "expiry" as its own occurrence;
    this is a genuine gap (not a deferral), noted for whoever eventually adds a Share-expiry
    scheduler.
  - A second sensitive deny-capture point was added in `ExchangeAccessManagementService`
    (`requireSessionOwner`, `requireSessionOwnerAndReturn`, `getSessionAccessView`) for denied
    `EXCHANGE_MANAGE_ACCESS` decisions -> `AUTHORIZATION_DENIED`. Per the task's explicit "a small
    number of genuinely sensitive deny points is sufficient" instruction, these two (denied
    document download, denied manage-access) were judged sufficient; not every authorization
    check in the app was instrumented.
- **Task 4, breadth, a defensible subset done (not exhaustive, by design).** New `AuditCategory`
  entries `WORKFLOW` and `FIELD_SCHEMA` (org-membership events reuse the existing `ORGANIZATION`
  category). New `AuditEventType` entries: `EXCHANGE_ACCEPTED`/`EXCHANGE_REJECTED`/
  `EXCHANGE_ENDED`/`EXCHANGE_DELETED`; `WORKFLOW_DEFINITION_CREATE`/`_UPDATE`/`_DELETE`/`_PUBLISH`;
  `FIELD_DEFINITION_CREATE`/`_RETIRE`; `SCHEMA_DEFINITION_CREATE`/`_PUBLISH`/`_RETIRE`;
  `ORG_MEMBERSHIP_ROLE_ASSIGN`/`_ROLE_REMOVE`/`_REMOVE` (see membership note below). Capture
  points added, all catch-and-log via the same pattern:
  - `ExchangeUpdateService.updateExchange`: a new private `recordLifecycleTransition` helper is
    called from **both** status-write code paths (the early-return workflow-routing branch for
    ACCEPTED_STARTED/REJECTED, and the direct-write branch for ENDED/others) so the transition is
    captured exactly once regardless of which path handled it; maps ACCEPTED_STARTED ->
    `EXCHANGE_ACCEPTED`, REJECTED -> `EXCHANGE_REJECTED`, ENDED -> `EXCHANGE_ENDED` (other target
    statuses are silently skipped - RESCINDED already has its own dedicated capture in
    `rescindExchange`). `ExchangeUpdateService.deleteExchange` gained a new
    `recordExchangeDeleted` call -> `EXCHANGE_DELETED`.
  - `WorkflowDefinitionService`: injected `AuditRecorder`; new `recordDefinitionEvent` helper
    called from `createDefinition` (`WORKFLOW_DEFINITION_CREATE`), `updateDefinition`
    (`WORKFLOW_DEFINITION_UPDATE`), `patchPublished` when publishing (`WORKFLOW_DEFINITION_PUBLISH`,
    only when `isPublished == true`, not on un-publish), and `deleteDefinition`
    (`WORKFLOW_DEFINITION_DELETE`). Target type `ResourceType.WORKFLOW_DEFINITION`.
  - `FieldDefinitionService`: injected `AuditRecorder`; new `recordFieldEvent` helper called from
    `createDefinition` (`FIELD_DEFINITION_CREATE`) and `retireDefinition`
    (`FIELD_DEFINITION_RETIRE`). Target type uses the literal string `"FIELD_DEFINITION"` (no
    dedicated `ResourceType` entry exists for this resource today).
  - `SchemaDefinitionService`: injected `AuditRecorder`; new `recordSchemaEvent` helper called
    from `createSchema` (`SCHEMA_DEFINITION_CREATE`), `publishDraft`
    (`SCHEMA_DEFINITION_PUBLISH`), and `retireSchema` (`SCHEMA_DEFINITION_RETIRE`). Target type
    uses the literal string `"SCHEMA_DEFINITION"` (same reasoning as Field Definitions).
  - **Org membership/role changes: deliberately NOT instrumented at the
    `OrganizationMembershipService` layer.** Investigation found that the admin-facing caller,
    `OrganizationAppUserService` (`addAppUser`/`updateAppUser`/`removeAppUser`), already calls
    `authAuditService.emit(action = "ORG_APP_USER_ADD"/"ORG_APP_USER_UPDATE"/"ORG_APP_USER_DELETE",
    ...)`, and those three literal action strings already had pre-existing `AuditEventType`
    catalog entries (`ORG_APP_USER_ADD`/`_UPDATE`/`_DELETE`) from an earlier phase, so
    `AuthAuditService`'s Phase-3-task-1 dual-write (done in the prior session) **already**
    captures every admin-driven membership add/role-change/removal onto the ledger, one event per
    whole-user mutation. Adding a second, finer-grained capture point inside
    `OrganizationMembershipService.assignOrgRole`/`removeOrgRole`/`removeMember` would have
    produced a **duplicate** ledger event for the same occurrence (violates the Global DoD's
    "exactly one tested capture point per occurrence"), so it was intentionally skipped. The
    `ORG_MEMBERSHIP_ROLE_ASSIGN`/`ROLE_REMOVE`/`REMOVE` catalog entries added this session are
    therefore currently **unused, reserved** entries for a possible future finer-grained capture
    point (e.g. if `OrganizationMembershipService` ever needs to distinguish a role-only change
    from a whole-user add/update at the ledger level) - not dead code, but not wired to a call
    site either; flagging this clearly so it is not mistaken for an oversight. **Known residual
    gap**: `assignOrgRole` is also called directly (bypassing `OrganizationAppUserService`) from
    `EntityRegistrationService` (self-registration), `OAuthUserLinkingService` (JIT/SSO
    provisioning), and `ScimUserResource` (SCIM provisioning) - none of those paths currently
    call `AuthAuditService.emit` or any other capture, so auto-provisioned org memberships are
    not captured on the ledger today. This is a real, not-yet-closed gap, left for a future
    session since it is lower risk than admin-initiated changes (no human decision-maker to
    attribute the event to; SSO/SCIM identity providers are typically covered by their own audit
    trail upstream) and the task instructed against exhaustive coverage.
  - Application-level operations (the fifth item in Phase 3 task 4's original list, "and
    application operations") were not separately instrumented this session - no dedicated
    "Application" mutation service beyond what workflow/field/schema/membership already cover was
    found; if a distinct "Application" entity/service is added in a later phase it should get its
    own capture point then.

### Phase 3 - continuation session: test validation (gate) result

- New unit tests: `ExchangeDocumentServiceAuditTest` (2 tests - `downloadDocument` records exactly
  one `DOCUMENT_DOWNLOAD` event with `targetType=DOCUMENT`/`actorKind=HUMAN`/correct `actorId`; a
  `AuditCaptureFailedException` thrown by `AuditRecorder` never propagates out of
  `downloadDocument` and the file is still returned), `ShareServiceAuditTest` (3 tests - `revoke`
  records exactly one `SHARE_REVOKE` event targeting the shared Exchange resource (not the Share
  row) with `actorKind=HUMAN`; revoking an already-revoked share records no duplicate event; a
  `AuditCaptureFailedException` from `AuditRecorder` never propagates out of `revoke`). Both new
  test files use the same Mockito-Kotlin `mock()`/`whenever()`/`argumentCaptor()` style as the
  pre-existing `ExchangeDocumentAuditServiceTest`.
- Two pre-existing tests needed constructor-signature fixes for the new `auditRecorder` parameter
  added to `ExchangeDocumentService`/`SchemaDefinitionService`: `RescindSideEffectsTest.kt` and
  `SchemaDefinitionServiceDefaultsTest.kt` (both simply gained one more `mock()` argument; no
  behavioral change to either test).
- Full backend suite: `.\mvnw.cmd test` -> **322 tests, 0 failures, 0 errors** (38 test classes),
  up from the 317/0/0 baseline at the start of this session (the +5 delta is exactly the two new
  test classes above). `.\mvnw.cmd compile` clean throughout (compiled after every logical batch
  of changes rather than at the end).
- Frontend: `npx tsc --noEmit` clean in `web-app/` (no frontend files were touched this session -
  Phase 3's remaining scope was entirely backend capture-point instrumentation). Help docs
  re-grepped (`grep -r audit web-app/src/app/components/help-docs/sections/`); the same matches as
  the prior session (generic "Review audit history..." wording, workflow-monitoring articles) -
  none describe UI-visible behavior affected by this session's backend-only new capture points, so
  no help doc edit was needed, consistent with AGENTS.md's "Required steps after any feature
  change" review (reviewed, no update required).
- Gate items, now exercisable:
  - "Integration: a document download and a Share revoke each produce exactly one ledger event
    with correct actor kind" - **satisfied** via the two new unit test classes above (verified at
    the `AuditRecorder.record(...)` call boundary, the same layer prior phases' tests verify at,
    since this repo still has no `@QuarkusTest`/real-Postgres harness - see the known gap below).
  - "Integration: existing Exchange/document audit UI still renders (now ledger-backed, one
    request)" - unchanged from the prior session's approximation (`ExchangeDocumentAuditServiceTest`
    unit test + clean `tsc`); this session did not touch that code path.
  - "Regression: deleting a document leaves its audit history intact and renderable (no cascade)"
    - unaffected by this session's changes (no FK was added to any audit/ledger row; `V43` only
    adds a nullable column to `audit_outbox`), so not re-tested with a new case.
  - **Known gap, same root cause as every prior phase's handoff, still not closed**: no
    `@QuarkusTest`/real-Postgres integration harness exists in this repo, so no gate item this
    session was exercised as a true HTTP/live-database round trip - all verification is at the
    Kotlin unit-test level with mocked repositories/`AuditRecorder`. This is a repo-wide,
    cross-phase gap, not something this session introduced or was expected to fix.

### Prerequisite Decisions checklist

Still unanswered by compliance/legal owners; no change this session. **New scoping decisions
recorded this session** (distinct from the compliance checklist, same convention as prior
sessions): (1) the `actorKind` migration is additive/nullable and prefers the explicit value with
a guessing fallback, so it never requires backfilling old rows; (2) `ShareService`'s actor-kind
inference (`HUMAN` if an actor id is known, else `SYSTEM`) is a coarse placeholder pending a
richer per-request context in that service; (3) the document-view/preview dual-emission choice at
`getDocumentFilePreviewAsPdf` (see above); (4) org membership/role-change capture is intentionally
left at the existing `OrganizationAppUserService`-level granularity rather than duplicated at
`OrganizationMembershipService`, with a residual gap for auto-provisioned (JIT/SCIM/self-register)
memberships noted above; (5) Share expiry has no capture point because no active expiry job exists
to instrument - do not treat "expiry" as done, it is a genuine gap, not a scoping choice.

### Continue with Phase 4 here

Phase 3 is done; the next session should start **Phase 4 - Immutable WORM Archive + Signed
Segments + Verification** (see the Phase 4 section above for its full task list and cost
constraint - **no new AWS service**, reuse S3/RDS/ECS/Secrets Manager/CloudWatch/IAM/ELB/
CloudFront already in `infra/cloudformation.yml`). Before starting Phase 4:

1. Read `AGENTS.md` (mandatory, every session).
2. Confirm `V43` is still the highest Flyway migration before writing any new migration (this
   session's `V43__audit_outbox_actor_kind.sql` is additive/nullable and safe to build on top of).
3. Be aware of the two residual Phase 3 gaps noted above if they become relevant to Phase 4's
   work: (a) auto-provisioned org membership (JIT/SSO/SCIM) has no ledger capture yet; (b) Share
   expiry has no active status-transition job and therefore no capture point. Neither blocks
   Phase 4, but both are legitimate future audit-coverage gaps worth tracking.
4. Phase 4's WORM archive/signature work reads from `audit_ledger_event` (unchanged by this
   session) - the new nullable `audit_outbox.actor_kind` column and `AuditEventDraft.actorKind`
   field are outbox/draft-side only and do not change the ledger's existing shape or hash chain.


### Phase 3 - what was built this session

- **Task 1 (`AuthAuditService.emit` -> `AuditRecorder`), done as a dual write, not a replacement.**
  `emit()`'s public signature is unchanged; internals now also call a new private
  `recordOnRecorder(...)` after the existing legacy `auth_audit_event`/WORM-sink write (which is
  untouched and still gated by `configurationService.isAuditImmutableEnabled()`).
  `recordOnRecorder` maps the legacy free-text `action` string onto `AuditEventType` by exact enum
  name match (`AuditEventType.entries.firstOrNull { it.name == action }`) and the legacy free-text
  `outcome` string onto the bounded `AuditOutcome` vocabulary via the new
  `AuthAuditService.mapLegacyOutcome` (SUCCESS/ALLOW/ORG_FOUND -> SUCCESS, DENY/DENIED -> DENIED,
  FAILURE/FAILED -> FAILURE, ERROR -> ERROR, anything else - e.g. the descriptive `NO_ORG`/
  `MULTIPLE_ORGS` lookup outcomes - defaults to SUCCESS with the original string preserved
  verbatim in the outbox payload as `legacy_outcome` so no nuance is lost). **Decision made this
  session** on the open question the Phase 2 handoff flagged ("stop writing to the legacy table
  once the recorder path is live, or migrate its data"): keep dual-writing to the legacy
  `auth_audit_event` table for now, because `AuthAuditResource`'s admin UI still reads it directly
  and re-pointing that read path is left to Phase 7 (Auditor Portal) alongside the rest of the
  ledger-backed UI work, not bundled into this phase. `recordOnRecorder` never throws out of
  `emit()`: an unmapped action is logged and skipped (not a validation failure); `AuditRecorder`
  rejecting the draft (`AuditDraftInvalidException`) or a fail-closed capture failure
  (`AuditCaptureFailedException`) are both caught and logged, not propagated, because a
  Phase-3-internal audit-plumbing problem must never be a new way to break sign-in/sign-out/
  token-refresh in production. Also fixed the hash-omission gap named in the task: `hashEvent` now
  takes an explicit `eventId` (generated once per `emit()` call, before hashing, and reused for
  both the `AuthAuditEvent.id` and the outbox `eventId`) plus `actorRole`/`targetType`/`targetId`,
  all folded into the SHA-256 chain input alongside the pre-existing fields.
  `AuditEventType` gained 4 entries discovered while cross-referencing every literal `action = "..."`
  string actually passed to `emit()` against the catalog: `EXCHANGE_REVOKE`,
  `ORG_IDP_SECRET_ROTATION_JOB`, `ORG_IDP_SECRET_ROTATION_RUNBOOK`, `WEBHOOK_DELIVERY_FAILED`
  (catalog version bumped 1 -> 2).
- **Task 2, existing call sites only.** `ExchangeDocumentAuditService.logAction` (both overloads -
  the `AppUser` one and the `performedByEmail` one) now also calls a private `recordOnRecorder`
  after the existing `DocumentAuditLog` write (also untouched, also kept as a dual write for the
  same UI-compatibility reason as task 1). A static `ACTION_TO_EVENT_TYPE` map covers the 8
  `DocumentAuditLogAction` values that already have a 1:1 `AuditEventType` (`UPLOAD`, `DOWNLOAD`,
  `VIEW`, `CREATED`, `DELETE`, `UPDATE`, `COMMENT`, `VERSION_CREATED`) - i.e. every action that
  already flows through `logAction`'s 7 existing call sites (`ExchangeDocumentService`,
  `ExchangeDocumentVersionService`, `ExchangeDocumentCommentsService`). Actor kind is distinguished
  by whether an `actorId` is present (`"APP_USER"` vs `"PUBLIC_LINK_OR_EMAIL_ACTOR"`), and the
  document title + actor email are carried in the payload (`document_title`, `actor_email`) so the
  compatibility projection (task 5) can render without a second query.
  **Explicitly NOT done this session** (see "Continue Phase 3" below): grep confirmed there is
  currently **no existing capture at all** for document view/download/preview/ZIP-export/
  library-download/no-auth-download anywhere in the codebase (`DocumentAuditLogAction.DOWNLOAD`/
  `.VIEW` are declared but never constructed) - adding those is genuinely new capture-site
  discovery and instrumentation work, not a mechanical migration of an existing call site, so it
  was treated as out of this session's scope rather than rushed.
- **Task 3 (Share/authorization capture via the recorder): NOT started.** Grep confirmed
  `AccessAuditLog` (entity + `AccessAuditLogRepository`) has zero call sites anywhere in the
  codebase today - it is scaffolding from an earlier session with nothing populating it. This is
  new capture work (find every Share grant/activation/materialization/role-constraint-change/
  expiry/revocation/deny decision and instrument it), not a migration, and was left for a
  dedicated session rather than attempted as a rushed addendum here.
- **Task 4 (Exchange lifecycle/workflow/membership/Field-Schema/application capture breadth):
  NOT expanded beyond the existing Phase 1 reference point** (`ExchangeUpdateService.rescindExchange`
  -> `EXCHANGE_RESCINDED`). No new lifecycle/workflow/membership/schema capture call sites were
  added this session.
- **Task 5 (compatibility projection), done for the Exchange-level Audit tab specifically.** New
  `AuditLedgerEventRepository.findByTargetTypeAndTargetIds(targetType, targetIds)`: one query,
  `WHERE target_type = :targetType AND target_id IN :targetIds ORDER BY occurred_at DESC`. New
  `ExchangeDocumentAuditService.getExchangeAuditEvents(exchangeId)`: loads the Exchange (for its
  `documents` - `@OneToMany(fetch = EAGER)`, already loaded), queries the ledger once with
  `targetType = "Document"` and every document id in the Exchange, and maps each
  `AuditLedgerEvent` back to the existing `DocumentAuditDetailedDto` shape (reverse
  `eventTypeKey -> DocumentAuditLogAction.name` lookup for the `action` field, so the frontend's
  existing `formatAuditAction` needs no changes) plus two new optional fields on that DTO,
  `documentId`/`documentTitle` (default `null`, so the untouched per-document
  `ExchangeDocumentAuditResource` endpoint is unaffected). New endpoint
  `GET /exchanges/{exchangeId}/audit-events` (`ExchangeAuditResource`, thin adapter delegating to
  the service - this is also the exact path Phase 5 task 5 specifies, so it does not need to be
  renamed later). Frontend: `ExchangeAuditTab.tsx` now calls a single
  `fetchExchangeAuditEvents(exchangeId)` instead of `Promise.allSettled` over one
  `fetchExchangeDocumentAuditLogs` request per document; `models.tsx`'s
  `DocumentAuditDetailedDto` gained the matching optional `documentId`/`documentTitle` fields (both
  already declared locally by `ExchangeAuditTab`'s `AuditEntry` interface, so this is additive, not
  a breaking change to any existing consumer). The per-document sidebar
  (`ExchangeDocumentAudit.tsx` / `ExchangeDocumentAuditResource`) is intentionally untouched - it
  was already exactly one request per document view (not the N-per-Exchange problem the gate calls
  out), so re-pointing it to the ledger was not required to satisfy this phase's gate and was left
  alone to minimize the diff.

### Phase 3 - test validation (gate) result and known gap

- New unit tests: `AuthAuditServiceTest` (6 tests - outcome mapping table; a recognized action
  dual-writes onto `AuditRecorder` with the correctly-mapped `eventTypeKey`/`outcome`; an unmapped
  action is skipped without calling the recorder; a `AuditCaptureFailedException` from the recorder
  never propagates out of `emit()`; an `AuditDraftInvalidException` from the recorder never
  propagates out of `emit()`; the recorder dual write still happens when the legacy immutable path
  is disabled), `AuthAuditServiceHashCoverageTest` (4 tests, via reflection since `hashEvent` is
  private - changing `eventId` alone changes the hash; changing `actorRole` alone changes the hash;
  changing `targetType` or `targetId` alone changes the hash; identical inputs reproduce an
  identical hash), `ExchangeDocumentAuditServiceTest` (2 tests - `logAction` dual-writes onto the
  recorder with the mapped event type/target/payload; `getExchangeAuditEvents` calls
  `findByTargetTypeAndTargetIds` exactly once across a multi-document Exchange and maps the
  returned ledger row's payload/action/title correctly).
- Full backend suite: `.\mvnw.cmd test` -> 317 tests, 0 failures, 0 errors (36 test classes, up
  from 305/33 at the end of Phase 2 - the two counts don't share the same class-count baseline
  because the Phase 2 handoff's "36 classes" figure already included this session's new files by
  the time it was written retroactively; trust the 317/0/0 result, not the class-count arithmetic).
  `.\mvnw.cmd compile` clean.
- Frontend: `npx tsc --noEmit` clean in `web-app/`; `npx vitest run` -> 77 tests passed (7 files;
  no existing Vitest coverage for `ExchangeAuditTab`/`ExchangeDocumentAudit`, so this phase did not
  add or remove any Vitest suite - the compatibility projection was validated at the backend unit
  level plus a manual `tsc` pass, not with a new frontend test).
- Help docs: reviewed (`grep -r audit web-app/src/app/components/help-docs/sections/`); the one
  relevant mention ("Review audit history for compliance and record retention" in
  `exchangesSection.tsx`) describes user-facing behavior only (an Audit tab exists, is reviewable)
  which is unchanged by this phase's backend-only efficiency fix, so no help doc edit was needed.
- **Known gap, same root cause as every prior phase's handoff**: no `@QuarkusTest`/real-Postgres
  integration harness exists in this repo, so the plan's Phase 3 gate items that need a live
  database/HTTP round trip were not exercised end-to-end this session:
  - "Integration: existing Exchange/document audit UI still renders (now ledger-backed, one
    request)" - approximated by the `ExchangeDocumentAuditServiceTest` unit test plus a clean
    `tsc --noEmit`, not a real browser/API round trip against a running backend.
  - "Integration: a document download and a Share revoke each produce exactly one ledger event
    with correct actor kind" - **not applicable yet**: document download has no capture call site
    at all (task 2 gap above), and Share revoke capture (task 3) was not started this session.
  - "Regression: deleting a document leaves its audit history intact and renderable (no cascade)"
    - not newly re-verified this session; unchanged from the pre-existing `DocumentAuditLog`
      behavior (no FK cascade was already true before this session and nothing here altered it),
      but not exercised against a live database in this session either.

### Prerequisite Decisions checklist

Still unanswered by compliance/legal owners. No new placeholder decisions were introduced this
session beyond the Phase 1 all-`DEGRADED` default and the Phase 2 stream-partition placeholder;
the dual-write choice for `AuthAuditService`/`ExchangeDocumentAuditService` documented above is a
scoping decision for *this session's diff*, not a stand-in for one of the compliance checklist
items - do not conflate the two when the checklist is finally answered.

### Continue Phase 3 here (superseded - see "Phase 3 - continuation session" above; kept for history)

1. Read `AGENTS.md` (mandatory, again, every session).
2. Confirm `V42` is still the highest Flyway migration before writing any new migration.
3. **Task 2, remaining scope**: add real capture call sites for document view, preview,
   current-version download, historical-version download, ZIP export, library download, and
   no-auth download. None of these exist as capture today (verified by grep - only `UPLOAD`,
   `CREATED`, `DELETE`, `UPDATE`, `COMMENT`, `VERSION_CREATED` are ever constructed via
   `logAction`). Find the actual document-serving endpoints/services first (likely
   `ExchangeDocumentService`/`ExchangeResource` and any public-link/no-auth download path), add an
   `actorKind` field to `AuditEventDraft` per the Phase 2 handoff's suggestion so call sites can
   pass `HUMAN`/`APP`/`PUBLIC_LINK`/`WORKFLOW` explicitly instead of `LedgerProcessor.resolveActorKind`
   guessing from `actorId` presence, and add the corresponding new `AuditEventType` entries
   (`DOCUMENT_VIEW`/`DOCUMENT_DOWNLOAD` already exist; preview/historical-version/ZIP/library/
   no-auth variants do not yet).
4. **Task 3, not started**: implement Share + authorization capture through the recorder (grants,
   activation, inherited materialization, role/constraint change, expiry, revocation,
   sensitive/denied authorization decisions). `AccessAuditLog`/`AccessAuditLogRepository` already
   exist but have zero writers - find the Share grant/activate/revoke/expire logic (likely in an
   organization/exchange sharing service) and instrument it via `AuditRecorder`, not by finally
   writing to `AccessAuditLog` (the recorder supersedes it as a write path per the plan; the table
   itself is out of scope to drop in this pass).
5. **Task 4, breadth**: add capture in the owning services for workflow definition/instance/step,
   membership/role, Field/Schema, and application operations, plus additional Exchange lifecycle
   transitions beyond `rescindExchange` - start with the highest-risk mutations listed in
   `AUDIT-ARCHITECTURE.md`'s coverage catalog.
6. Re-run the full Phase 3 gate (see above) once 3-5 are done, including the integration-style
   "document download / Share revoke produce exactly one ledger event with correct actor kind"
   item that could not be exercised this session because those capture points did not exist yet.
7. Only then move to Phase 4 (Immutable WORM Archive + Signed Segments + Verification).

### Phase 2 - what was built (for history; Phase 2 is done, Phase 3 built on top of it)

- Migration `V42__audit_ledger.sql`: `audit_ledger_event` (canonical envelope: event id/type/
  category/outcome/schema version; occurred/recorded/ledger time; `stream_id` + strictly
  increasing `stream_sequence`; actor kind/id/role; session/trace/correlation/causation;
  organization + target type/id (denormalized, no FK); reason + payload JSON; `prev_hash` +
  `event_hash`; nullable `signing_key_id`/`checkpoint_ref` reserved for Phase 4) with a
  `UNIQUE (event_id)` and `UNIQUE (stream_id, stream_sequence)` constraint, plus `stream_head`
  (`stream_id` PK, `last_sequence`, `last_hash`) for per-stream locking. Same unconditional-deny
  `BEFORE UPDATE`/`BEFORE DELETE` trigger pattern as `V41` (`audit_ledger_event_deny_mutation`) -
  no role-scoped `REVOKE`, for the same reason recorded in the `V41` header comment
  (`${DB_USERNAME}` is environment-parametrized). The new table names the session column
  `session_id` correctly (the `AuthAuditEvent.sessionId -> exchange_id` mismap is a legacy-table
  issue only; it is not carried into the new ledger schema, and is left for Phase 3 to actually
  migrate `AuthAuditService`).
- `model/entity/AuditLedgerEvent.kt`, `model/entity/StreamHead.kt`: denormalized IDs/labels only,
  no `@ManyToOne`/FK to any business entity - same `AccessAuditLog` pattern as `AuditOutboxEntry`.
- `repository/AuditLedgerEventRepository.kt` (`@RequestScoped`): `existsByEventId` (the
  drain-idempotency guard - see Phase 1 handoff note on why `audit_outbox` rows themselves are
  never mutated to mark "processed"), `findLatestByStream`, `findByStreamOrderBySequence`,
  `insert`.
- `repository/StreamHeadRepository.kt` (`@RequestScoped`): `lockOrCreate(streamId)` does an
  idempotent `INSERT ... ON CONFLICT (stream_id) DO NOTHING` then `entityManager.find(...,
  LockModeType.PESSIMISTIC_WRITE)` - this is the concurrency guard: two concurrent appenders to
  the same stream serialize on this row lock, so the chain cannot fork or reorder. `advance(...)`
  persists the new `lastSequence`/`lastHash` before commit.
- `repository/AuditOutboxRepository.findOldestByRecordedAt(limit)`: added so `LedgerProcessor` can
  batch-scan the oldest outbox rows to drain. This is a documented backlog scan, not an indexed
  "unledgered" query (flagged as an open design point in the Phase 1 handoff, and again here for
  whichever phase next needs to worry about drain-backlog performance).
- `service/audit/LedgerProcessor.kt` (`@ApplicationScoped` - unlike `AuditRecorder`, it derives no
  per-request context, so it does not need `@RequestScoped`): `drain(batchSize)` scans the oldest
  outbox rows, skips any already in the ledger (`existsByEventId`), and calls `appendOne` for the
  rest, counting `appended`/`alreadyLedgered`/`failed` without letting one row's failure abort the
  batch. `appendOne` (`@Transactional`) re-checks `existsByEventId` inside the transaction (the
  authoritative guard, backed by the `event_id` unique constraint - a concurrent drain pass racing
  between the pre-check and the insert is treated as "already ledgered", not a real failure),
  resolves the stream (`resolveStreamId`: `<organizationId-or-"platform">:<UTC yyyy-MM>` - a
  documented placeholder for the still-unanswered "Ordered-stream partition + segment-closing
  policy" Prerequisite Decision), locks/reads `stream_head`, canonicalizes the envelope
  (`CanonicalLedgerEnvelope`, a `@Serializable` data class encoded via `kotlinx.serialization.json.Json`
  - field order is fixed by declaration order so the same logical event always serializes
  byte-identically), computes `eventHash = sha256(canonicalJson || streamSequence || prevHash)`
  (mirrors `AuthAuditService.hashEvent`'s SHA-256-over-pipe-joined-fields style), appends the
  ledger row, then advances `stream_head`. `resolveActorKind` is a coarse `HUMAN`/`SYSTEM` default
  pending Phase 3 task 2's real actor-kind capture (human/app/public-link/workflow) - flagged
  in-code, not a considered classification.
- `service/audit/LedgerProcessorScheduler.kt` (`@ApplicationScoped`, `@Scheduled`): drains every
  `app.audit.ledger.drain-every` (default `15s`, config `APP_AUDIT_LEDGER_DRAIN_EVERY`), mirroring
  the existing `WorkflowEscalationScheduler` pattern (`ConcurrentExecution.SKIP` so passes never
  overlap).
- Config: `app.audit.ledger.drain-every` added to `application.properties`.

### Phase 2 - test validation (gate) result and known gap

- Unit tests added: `LedgerProcessorTest` (7 tests) - `drain` appends exactly one ledger event per
  outbox row and reports zero already-ledgered when none exist; `drain` skips a row whose
  `event_id` already exists in the ledger and never calls `insert` for it; three sequential
  `appendOne` calls to the same stream produce a contiguous `1,2,3` sequence with each event's
  `prevHash` equal to the previous event's `eventHash` and all three sharing one `stream_id`
  (approximates the plan's concurrency gate - see gap note below); `appendOne` is a no-op and never
  inserts when the row is already ledgered; `drain` counts one failed row without stopping the
  batch (the other row in the same batch still appends); mutating the payload, the sequence, or
  the previous hash each independently changes the recomputed hash while identical inputs
  reproduce the identical hash (tamper-evidence); `resolveStreamId` groups by organization + UTC
  month and defaults to `"platform"` when there is no organization.
- Full backend suite: `.\mvnw.cmd test` -> 305 tests, 0 failures, 0 errors (36 test classes, up
  from 298/32 at the end of Phase 1), including all pre-existing tests. `.\mvnw.cmd compile`
  clean.
- **Known gap, explicitly flagged rather than silently skipped** (same root cause as the Phase 1
  handoff's gap): this repo still has no `@QuarkusTest`/real-Postgres integration harness, so the
  plan's Phase 2 gate items that require a live database were approximated at the unit level
  instead of run against real Postgres:
  - "Concurrency: parallel appends to one stream cannot fork or reorder" - approximated by
    asserting the single-threaded, sequential-append invariant (contiguous sequence, one hash
    chain) that `StreamHeadRepository.lockOrCreate`'s `PESSIMISTIC_WRITE` row lock is designed to
    also enforce under real concurrent transactions. **Not exercised under real concurrent
    transactions in this session.** If/when a real-Postgres test harness exists, add a true
    multi-threaded/multi-connection test that fires N concurrent `appendOne` calls at the same
    stream and asserts the resulting `stream_sequence` values are exactly `1..N` with no gaps or
    duplicates.
  - "DB: app role cannot UPDATE/DELETE ledger rows" - the `V42` trigger SQL was written and
    manually reviewed (mirrors the already-reviewed `V41` trigger) but **not executed against a
    live Postgres instance in this session**.
  - The "tamper" and "every committed outbox row yields exactly one ledger event" gate items *are*
    exercised, including with mocked repositories that behave like the real unique-constraint/lock
    semantics they stand in for.

### Prerequisite Decisions checklist

Still unanswered by compliance/legal owners. Phase 2 added one more documented placeholder on top
of Phase 1's all-`DEGRADED` failure-policy default: the stream partition policy
(`<organizationId-or-"platform">:<UTC yyyy-MM>`) is a placeholder for "Ordered-stream partition +
segment-closing policy", not a considered decision. Revisit both the moment the checklist is
answered - changing the partition policy later means old streams keep their historical
`stream_id`s (do not rewrite already-ledgered rows; only new events adopt a new policy going
forward, tracked via a versioned resolver if/when this changes).

### Phase 3 kickoff note written at the end of the Phase 2 session (superseded)

The subsection immediately below was written at the end of the Phase 2 session as the "start here"
plan for a fresh Phase 3. It is kept for history only; it has been superseded by "Continue Phase 3
here" earlier in this handoff, which reflects what was *actually* built in the Phase 3 session and
what genuinely remains.

1. Read `AGENTS.md` (mandatory, again, every session).
2. Confirm `V42` is still the highest Flyway migration before writing any Phase 3 migration, in
   case another change landed `V43+` since this session.
3. Route `AuthAuditService.emit` through `AuditRecorder` (task 1): keep the public method
   signature, change the internals so auth events land in `audit_outbox` (and, once
   `LedgerProcessorScheduler` drains them, `audit_ledger_event`). Fix the current hash-omission gap
   (event id, actor role, target type, target id are not currently covered by
   `AuthAuditService.hashEvent`). This is also where the `AuthAuditEvent.sessionId -> exchange_id`
   column mismap in the *legacy* `auth_audit_event` table finally needs a decision: either stop
   writing to the legacy table once the recorder path is live, or migrate its data - `V42`'s new
   ledger table already uses the correct `session_id` column name, so no new schema work is needed
   there, only a decision about the old table/rows.
4. Replace `DocumentAuditLog` writes with recorder capture (task 2): add new `AuditEventType`
   entries to the catalog for view/preview/current-version download/historical-version
   download/ZIP export/library download/no-auth download, distinguishing human/app/public-link/
   workflow actors (this is also where `LedgerProcessor.resolveActorKind`'s current coarse
   `HUMAN`/`SYSTEM` default should be revisited/replaced with real per-draft actor-kind capture -
   consider adding an `actorKind` field to `AuditEventDraft` so call sites can pass it explicitly
   instead of `LedgerProcessor` guessing from `actorId` presence alone).
5. Implement Share + authorization capture (task 3) through the recorder (grants, activation,
   inherited materialization, role/constraint change, expiry, revocation, sensitive/denied
   authorization decisions) - this supersedes `AccessAuditLog` as a write path, though the table
   itself is out of scope to drop in this pass.
6. Add capture in the owning services for Exchange lifecycle (beyond the existing
   `rescindExchange` reference point), workflow definition/instance/step, membership/role,
   Field/Schema, and application operations (task 4) - start with the highest-risk mutations listed
   in `AUDIT-ARCHITECTURE.md`'s coverage catalog.
7. Compatibility projection (task 5): back the existing `ExchangeAuditTab`/`ExchangeDocumentAudit`
   UI with a single read query over `audit_ledger_event` (not one request per document as today).
8. Gate: existing Exchange/document audit UI still renders (now ledger-backed, one request); a
   document download and a Share revoke each produce exactly one ledger event with correct actor
   kind; deleting a document leaves its audit history intact and renderable (no cascade); frontend
   `npx tsc --noEmit` clean; help docs for Exchange/document audit reviewed and updated (this is
   the first phase since Phase 0 with genuine user-visible/UI-adjacent behavior, so the "Required
   steps after any feature change" section of `AGENTS.md`/this plan's header applies).

### Phase 1 - what was built (for history; Phase 1 is done, Phase 2 built on top of it)

- `service/audit/AuditFailurePolicy.kt`: `FAIL_CLOSED` / `DEGRADED` enum.
- `service/audit/AuditFailurePolicyResolver.kt` (`@ApplicationScoped`): resolves the policy for an
  `AuditCategory` from `app.audit.failure-policy.fail-closed-categories` (comma-separated category
  names, config `APP_AUDIT_FAILURE_POLICY_FAIL_CLOSED_CATEGORIES`). **Empty by default**, so every
  category currently resolves to `DEGRADED` - the Prerequisite Decisions checklist (required event
  classes + per-class failure policy) is still unanswered by compliance/legal, so this session
  followed the plan's explicit fallback instead of guessing at a fail-closed policy. Flag this in
  review; do not treat the current all-degraded default as a real policy decision.
- `service/audit/AuditCaptureResult.kt`: `AuditCaptureResult` (`Captured` / `Degraded`),
  `AuditDraftInvalidException` (always thrown for a draft that fails
  `AuditEventDraftValidator` - unknown event type or prohibited payload key - this is a call-site
  bug, never routed through the failure-policy switch), `AuditCaptureFailedException` (thrown only
  when persistence itself fails and the resolved policy is `FAIL_CLOSED`).
- `service/audit/AuditRecorder.kt` (`@RequestScoped`, matching the existing `@RequestScoped`
  `AuthAuditService` per AGENTS.md scope guidance): `record(draft: AuditEventDraft)` validates the
  draft, checks `audit_outbox` for an existing row with the same idempotency key (dedup on retry),
  derives `actorId` (falls back to `AuthTokenContext.authToken.appUser?.id`), `organizationId`
  (falls back to `AuthTokenContext.activeOrganizationId`), `serverTraceId`/`correlationId`/
  `causationId` (from `AuthTokenContext`, wired in Phase 0), serializes `payload` to JSON
  (kotlinx.serialization `MapSerializer`), and persists one `AuditOutboxEntry` row via
  `AuditOutboxRepository.insert` - joining the caller's active transaction (`BaseRepository.save`
  is `@Transactional` with default `REQUIRED` propagation; no `REQUIRES_NEW`). On a persistence
  failure: `FAIL_CLOSED` rethrows `AuditCaptureFailedException` (propagates out of the caller's
  `@Transactional` method so Quarkus rolls back the business mutation with it); `DEGRADED` logs and
  returns `AuditCaptureResult.Degraded` without throwing.
- `model/entity/AuditOutboxEntry.kt` + `repository/AuditOutboxRepository.kt`
  (`@RequestScoped`, extends `BaseRepository`): denormalized IDs/labels only, no `@ManyToOne`/FK to
  any business entity - follows `AccessAuditLog`, not the `DocumentAuditLog` anti-pattern.
- Migration `V41__audit_outbox.sql`: `audit_outbox` table (event id + idempotency key both unique,
  status/attempt_count/occurred_at/catalog_version columns per the plan's task list, no business
  FKs) plus a `BEFORE UPDATE`/`BEFORE DELETE` trigger (`audit_outbox_deny_mutation`) that
  unconditionally raises on any UPDATE/DELETE, regardless of DB role. A trigger (not a per-role
  `REVOKE`) was used because `quarkus.datasource.username` is `${DB_USERNAME}` in
  staging/prod (unknown at migration-authoring time), and because task 1 calls the outbox row an
  "immutable event draft" - the intent is that no code path (this app's normal role or otherwise)
  ever mutates a row once inserted. **Design note for Phase 2**: because the trigger denies ALL
  mutation unconditionally, `LedgerProcessor` (Phase 2) must not try to flip `audit_outbox.status`
  to mark rows processed; it must track drain progress some other way (e.g. treat ledger insertion
  as idempotent-by-`event_id` and never re-read fully-processed rows, or a separate cursor/marker
  table). This is an open design point for Phase 2, called out rather than pre-solved here.
- Catalog addition: `AuditCategory.EXCHANGE` and
  `AuditEventType.EXCHANGE_RESCINDED` (`exchange.lifecycle.rescinded`).
- Reference wiring: `ExchangeUpdateService.rescindExchange` (chosen as the plan's suggested
  "Exchange lifecycle transition" reference point - it is a single `@Transactional` method,
  REST-triggered, already covered by `RescindSideEffectsTest`/`ExchangeAuthorizationTest`) now
  calls `auditRecorder.record(...)` immediately after the DB status/end-date/last-activity update
  and before workflow-cancel/share-revoke side effects, with a deterministic
  `idempotencyKey = "exchange.rescind:$exchangeUuid"`. All other capture call sites
  (`AuthAuditService`, `DocumentAuditLog`) are untouched - that migration is Phase 3.
- Config: `app.audit.failure-policy.fail-closed-categories` added to `application.properties`
  (default empty/degraded, as above).

### Phase 1 - test validation (gate) result and known gap

- Unit tests added: `AuditFailurePolicyResolverTest` (4 tests: default-degraded, a configured
  category routes to fail-closed while others stay degraded, case/whitespace-insensitive parsing,
  unknown category names ignored rather than failing), `AuditRecorderTest` (5 tests: a valid draft
  is captured exactly once with derived context; an unknown event type always throws
  `AuditDraftInvalidException` and never inserts, regardless of policy; a retried capture with the
  same idempotency key does not insert a duplicate row; `FAIL_CLOSED` propagates
  `AuditCaptureFailedException` on a forced persistence failure; `DEGRADED` swallows the same
  failure and returns `Degraded`). Two tests added to `RescindSideEffectsTest`: a forced
  `AuditCaptureFailedException` from `AuditRecorder.record` propagates out of `rescindExchange` and
  the workflow-cancel/share-revoke side effects that follow the audit call in source order never
  run; a `Degraded` capture result lets `rescindExchange` complete normally (shares still revoked).
  `ExchangeAuthorizationTest`/`RescindSideEffectsTest` constructor call sites updated for the new
  `auditRecorder` parameter.
- Full backend suite at the end of Phase 1: `.\mvnw.cmd test` -> 298 tests, 0 failures, 0 errors
  (32 test classes, up from 287/30 at the end of Phase 0). Known integration-test gap (no
  `@QuarkusTest`/real-Postgres harness in this repo) recorded and carried forward - see the Phase 2
  gap note above, which has the same caveat for the `V42` trigger and concurrency gate.

---
