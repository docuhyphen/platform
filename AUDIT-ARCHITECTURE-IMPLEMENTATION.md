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

Status at time of writing: **planning complete, no code written yet.**

Start here:
1. Read `AGENTS.md` (mandatory), then re-read `AUDIT-ARCHITECTURE.md` sections
   "Delivery Sequence" and "Required Tests".
2. Confirm the Prerequisite Decisions checklist above with compliance owners. If unanswered,
   implement Phase 0 fully and Phase 1 scaffolding in `degraded/log-only` mode behind config.
3. Begin **Phase 0** (event catalog, `AuditEventDraft`, prohibited-field validator, correlation
   context). It has no behavior change and unblocks everything else.
4. Then **Phase 1** (recorder + outbox + first fail-closed capture on Exchange lifecycle).
5. New Flyway migrations start at `V41`. Follow the `AccessAuditLog` denormalized pattern; never
   the `DocumentAuditLog` FK pattern.
6. Remember the two known bugs to fix during migration: the `AuthAuditEvent.sessionId -> exchange_id`
   column mismap (Phase 2) and the hash coverage gaps in `AuthAuditService` (Phase 3).

Each phase is independently shippable and gated by its own test-validation block above. Do not
advance to the next phase until the current phase's gate passes.
