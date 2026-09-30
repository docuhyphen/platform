# Document-Driven Information Requests Gap Audit

## Status

- Date: 2026-09-30. Audited commit: `23ed5d04` (clean working tree).
- Scope: every task, design decision, exit criterion, gate and acceptance criterion in
  `DOCUMENT-DRIVEN-INFORMATION-REQUESTS-IMPLEMENTATION-PLAN.md`, checked against the source code,
  migrations, tests, frontend and help articles. The completion evidence file and the earlier
  review files were not used as evidence.
- Method: 22 auditors each took one plan slice or cross-cutting sweep. Duplicate claims were merged.
  Every remaining claim was re-checked against both the code and the superseding plan text, and each
  high-severity claim also went to an adversarial skeptic. A completeness critic then looked for
  missed areas.
- Result: 168 verified gaps (13 high, 65 medium, 90 low). GA-001 to GA-166 come from the audit.
  GA-167 and GA-168 were added the same day from a separate Exchange initiation investigation and
  are scheduled as Phase 13 of the implementation plan.
  4 claims were refuted and are listed at the end.
- Test suites at the audited commit: backend `mvnw test` 3,380 tests, 0 failures, 0 errors, 0 skipped.
  web-app `vitest run` 786 of 786 passed. `npm run typecheck:app` reported 0 Information Request
  diagnostics. `npm run lint` reported no problems in Information Request files. Passing suites do
  not cover the gaps below: most of them are behaviours that no test exercises.
- The plan's "Complete" status does not hold until the gaps below are closed or explicitly waived.

## Summary

| ID | Sev | Size | Plan reference | Gap | Decision |
|---|---|---|---|---|---|
| GA-001 | high | M | AD-20, P3-T12, P4-T4 | Delegated-authority grant and revoke write no audit event, domain event or history |  |
| GA-002 | high | M | Baseline: Blueprints, P3-T9b | Placeholder foreign key to blueprint_document_default breaks every later Blueprint save | yes |
| GA-003 | high | M | P5-R25, P5-T4b | Clear-with-confirmation cannot be completed from the UI, which blocks saves |  |
| GA-004 | high | M | P4-T4 | Upgraded participant's App User Share is never revoked on party revocation or reassignment |  |
| GA-005 | high | M | P3-T8 | Request party assignment bypasses the organization B2B/B2C sharing policy |  |
| GA-006 | high | M | P6-T11 | Respondent evidence UI cannot capture evidence attributes, so attribute-based policies are unsatisfiable through the product |  |
| GA-007 | high | S | P8-T11 / P8-T3 | Respondent UI cannot resubmit or re-attest a scope under an open correction |  |
| GA-008 | high | S | P8-T11 / P8-T1 | No UI path for a request manager to open a pending review and assign the first reviewer |  |
| GA-009 | high | M | P5-R13, P5-R30, P5-T2c | Group occurrence receipt replay returns every occurrence without scope or lifecycle checks |  |
| GA-010 | high | M | Baseline: No-auth access, P4-T4 | requireRecipientSignIn is enforced only at first link issuance |  |
| GA-011 | high | L | Completeness critic | Confidentiality compartments are only an MFA-freshness gate, never per-party clearance; no-auth respondents can never answer them and there [truncated] | yes |
| GA-012 | medium | S | P2-T10a, P3-T9a | Ad hoc private Version publication skips the service readiness check |  |
| GA-013 | medium | S | AD-2, P3-T9a | Request-owned ad hoc Template is not actually non-reusable |  |
| GA-014 | medium | M | AD-39, Baseline: Attribution foreign keys | Program-created Information Request tables carry App User-only attribution instead of the canonical principal pair |  |
| GA-015 | medium | M | AD-20, P3-T2 | Runtime request audit events never carry the access-session ID or a safe actor label | yes |
| GA-016 | medium | M | DS-T3, DS-T4, Development-Stage Constraint [truncated] | Leftover compatibility fallbacks and legacy-only state in the Fields foundation |  |
| GA-017 | medium | M | P9-Decision-6, P9-T4 | Completion gate change endpoint has no UI, and creation never sets the gate |  |
| GA-018 | medium | M | P3-T12, Phase 8 decision 4 | Delegated-authority grant and revoke skip the parent lock, transition matrix and entitlement |  |
| GA-019 | medium | M | P9-Decision-6, P9-T4 | cancelRemainingInformationRequests cancels requests when an ending Workflow starts, before ending is confirmed |  |
| GA-020 | medium | M | P6-T2a3 | Production S3 version store (write-once and checksum refusal) has no tests |  |
| GA-021 | medium | M | P1-T2 | Schema editor silently wipes binding defaults, visibility overrides and sections; read-only default has no first-party authoring path |  |
| GA-022 | medium | S | P1-T3 | No Workflow duplicate-binding regression test, and the workflow snapshot still reads orphaned values that V77 kept |  |
| GA-023 | medium | S | P2-T9 | Blueprint Information Request tab offers platform Template Versions that the backend always refuses |  |
| GA-024 | medium | M | P5-T4 | ARCHIVE_OUTSIDE_ACTIVE_RESPONSE behaves exactly like RETAIN_SECURELY | yes |
| GA-025 | medium | M | P5-T3 | Downstream conditions still read Field values kept on a hidden Requirement |  |
| GA-026 | medium | M | P5-T6 | The validation extension point is too thin for cross-row, aggregate, or period-coverage checks |  |
| GA-027 | medium | M | Phase 5 exit criteria | Condition-driven hiding and clearing leaves no audit trail |  |
| GA-028 | medium | S | P5-T1c | A draft PATCH cannot clear a Field the Schema marks required |  |
| GA-029 | medium | M | P5-R06 / P5-R24 | Group commands fall back to aggregate contributor authority when no materialized binding or Requirement is in scope |  |
| GA-030 | medium | S | P5-R26 | Contact-proof session issuance locks ShareLink before the parent Exchange, inverting the order used by party reassignment and revocation |  |
| GA-031 | medium | M | P4-T5 | Participant registration upgrade endpoint has no frontend caller | yes |
| GA-032 | medium | M | P4-T4 | Bootstrap links can be issued to USER-held parties, so a no-auth session acts as an App User principal |  |
| GA-033 | medium | M | P4-T4 | Link command expected revision never advances, so If-Match cannot detect concurrent link changes |  |
| GA-034 | medium | M | P3-T8 | Trusted-organization party selection command is unreachable (no REST endpoint, no UI) | yes |
| GA-035 | medium | M | P3-T4 | Owner history reads after Exchange termination are retained only for personally owned requests |  |
| GA-036 | medium | S | P3-T8 | Party revocation on organization membership removal records no party history, audit, or domain event |  |
| GA-037 | medium | M | CDM-RequestTransition | Request transitions never record their idempotency key or command receipt |  |
| GA-038 | medium | M | REST-StableErrorCodesRequirementIdsFieldPaths | Validation errors carry no Requirement ID or field path, and Field validation errors drop the reason code and field key | yes |
| GA-039 | medium | M | REST-TryCatchAndResourceContractTests | Nine Information Request REST resources have no resource contract test |  |
| GA-040 | medium | M | P6-T6b / P6-T7 | Encrypted and corrupt content is detected only for PDFs; password-protected Office or ZIP files and corrupt images pass as conforming |  |
| GA-041 | medium | S | AD-14 | Expanding amendments are not blocked after a commercial lapse |  |
| GA-042 | medium | M | P11-T12 / Phase 11 decision 9 | Imported-value reconciliation POST requires an Idempotency-Key but the service ignores it |  |
| GA-043 | medium | S | P7-T3 / Phase 7 design decision 2 | If-Match: * skips the reviewed-content check on submission and attestation |  |
| GA-044 | medium | S | P7-T1b / Phase 7 design decision 2 | Submission content hash leaves out governing assessments, conformance, and attestation-item envelopes |  |
| GA-045 | medium | S | P7-T7a / P7-T7b / Phase 7 design decision 1 | Earlier stage can be withdrawn while a later stage depending on it through a condition stays submitted |  |
| GA-046 | medium | M | P7-T6 / P7-T7c / Phase 7 design decision 8 | Carry-forward for a staged source reads only the last stage package |  |
| GA-047 | medium | M | P7-T7c | Expiry-triggered refresh rules are never triggered: lead_days unused, no scheduler, no due check or duplicate guard |  |
| GA-048 | medium | M | P7 Tests to write first / P7-T3 | No save-vs-submit or upload-vs-submit race tests |  |
| GA-049 | medium | S | P9-Decision-1 | Cancel and supersede reasonCode subject field is free text, not a stable code |  |
| GA-050 | medium | M | P8-T2 / P8-T4b | A finding is always attributed to the reviewer's earliest assignment, so a later-stage worksheet cannot return or reject an item |  |
| GA-051 | medium | M | P8-T7 / P8-T11 | Retest of an earlier finding cannot be recorded from the reviewer UI |  |
| GA-052 | medium | M | Tests to write first | No command-level tests for recusal/delegation/revocation, override, outcome rules, or reviewer authorization refusals |  |
| GA-053 | medium | M | P10-T2 | No UI to name an individual platform user as a party; email always creates an External Participant |  |
| GA-054 | medium | M | P10-T3 / P10-T5 | Correction guidance counts returned files but never names which evidence versions a correction reopens |  |
| GA-055 | medium | M | P9-T5 | Clocks of finished requests stop late, at scheduler time, so SLA can say OVERDUE for a request met on time |  |
| GA-056 | medium | M | P9-T9 | Holds on Submission Packages, Evidence, Outbound Notices, or Exports are accepted but ignored by disposal |  |
| GA-057 | medium | S | G191-Baseline: | ShareLink password, domain and MFA constraints are never enforced or rejected |  |
| GA-058 | medium | M | AD-14, P4-T7 | Execution grant is missing entitlement/policy version, trial grant reference, continuation actions and feature snapshot | yes |
| GA-059 | medium | M | P5-R10, P5-T2c | No real concurrent-edit test for group occurrence add, remove, or reorder |  |
| GA-060 | medium | M | P12 | Manual reminders are still sent for requests under operational suspension or with a revoked grant |  |
| GA-061 | medium | S | P9-Decision-9 / P9-Decision-12, P9-T7 / P9-T10 | Record exports and subject exports omit privacy item corrections |  |
| GA-062 | medium | M | P10-T2, P3-T11 | Party reassignment has a frontend service but no UI, while help docs describe it |  |
| GA-063 | medium | M | P9-Decision-11, P9-T9 | Automatic retention disposal can starve behind held or referenced requests and re-audits denials every pass |  |
| GA-064 | medium | L | AGENTS backend rules / Decision 7, AGENTS.md backend rules, [truncated] | Evidence gate and version recorder use other domains' repositories directly |  |
| GA-065 | medium | M | Coding and backend rules, P1-T8, P6-T4 / P6-T5 | toDto logic and request DTOs remain inside the Fields Definition services |  |
| GA-066 | medium | M | AD-31, P2-T8, REST-IdempotencyKeys | Template draft replace and publish have no If-Match or ETag precondition |  |
| GA-067 | medium | M | AD-34, P3-T8 | Request-scoped trusted group Shares expand without trusted-group eligibility reconciliation | yes |
| GA-068 | medium | M | AD-40, Baseline: External participants, P3-T8, P4-T4 | Participant contact verification is never recorded (verifyContact unwired) |  |
| GA-069 | medium | L | P2-T11, P5-T8, P7-T8, P8-T10, Walking-skeleton fixtures | Walking-skeleton fixtures are not extended in Phases 3, 4 and 10 |  |
| GA-070 | medium | M | Completeness critic | A privacy request is committed before it is processed, so any refusal strands it in RECORDED forever; the endpoint takes no Idempotency-Key |  |
| GA-071 | medium | M | Completeness critic | Lifting a subject restriction writes no audit event or history, takes no Idempotency-Key or If-Match, and does not lock the row |  |
| GA-072 | medium | M | Cross-phase gates | Record and subject exports bypass the Requirement confidentiality-compartment step-up that every other read enforces |  |
| GA-073 | medium | M | Cross-phase gates | Record preservation hold and retention schedule mutations have no Idempotency-Key, Command Receipt or If-Match |  |
| GA-074 | medium | M | AGENTS.md rules sweep | Information Request backend, entities and migrations are full of descriptive KDoc and SQL comments |  |
| GA-075 | medium | S | Wiring sweep | Clock policy reminder and overdue Communications cannot be chosen in the UI and are dropped when the screen publishes a new version |  |
| GA-076 | medium | S | Wiring sweep | Request audit history panel shows only the 100 oldest events, with no paging or total |  |
| GA-077 | low | M | P11-T10 / Executable capability proofs (Timed retained [truncated] | The multi-party scenario never has the reviewer review the package, and never checks outsider mutation denial |  |
| GA-078 | low | M | P3-T12, Phase 3 Tests to write first | Delegated authority has no read endpoint, no frontend and no help docs | yes |
| GA-079 | low | M | P9-Decision-5 / P9-T13, P9-T13 / P9-T3 | Workflow designer cannot author requirement conditions for request triggers | yes |
| GA-080 | low | M | CDM-EvidenceVersion, P6-T1 | External typed-reference evidence subtype has no writer, command, endpoint or UI | yes |
| GA-081 | low | S | P6-T3f | Authorization matrix never models an unassigned Exchange participant or the evidence Document on ordinary Document surfaces |  |
| GA-082 | low | M | P6-T2a3 | S3 version reads leave a permanent temp copy of every stored file, evidence included |  |
| GA-083 | low | S | P1-T9 | Help docs claim registered-application access and recipient Public-field visibility that the code does not provide, and describe a schema [truncated] |  |
| GA-084 | low | S | P1-T6 | Blueprint-default value writes and materialized schema defaults produce no value-mutation audit event |  |
| GA-085 | low | S | P1-T7b | A concurrent schema assignment surfaces as 500, and there is no persisted assignment revision |  |
| GA-086 | low | S | P1-T1 | Details tab offers assign and save controls regardless of caller capability, and swallows refusal messages |  |
| GA-087 | low | S | P2-T7a | Template READ granted to every organization member, who can then read drafts and copy them out | yes |
| GA-088 | low | M | P2-T2 | Subject merge and supersession history has storage but no service | yes |
| GA-089 | low | M | P2-T1d2 | No web-app surface for platform-administered personal feature overrides | yes |
| GA-090 | low | S | P2-T7b | Capability rows are written by two services with duplicated publish logic |  |
| GA-091 | low | S | P2-T9 | A retired or non-latest pinned Template Version cannot be identified on the Blueprint |  |
| GA-092 | low | S | P2-T9 | Blueprint help article does not cover the Information Request tab |  |
| GA-093 | low | M | P2-T7a | No production path creates a PLATFORM Template, so the platform list, copy and choice paths are always empty | yes |
| GA-094 | low | S | P3-T11c | Credential cutoff after reassignment or revocation has no transaction test |  |
| GA-095 | low | M | Phase 3 Tests to write first | Missing per-precondition reassignment cases and trusted-matrix cases |  |
| GA-096 | low | S | Phase 3 Tests to write first | Temporary-App-User exclusion tests only partially present |  |
| GA-097 | low | S | Phase 3 Tests to write first | No test of two Information Requests using the same stable Field |  |
| GA-098 | low | S | P5-T1b | No-auth commands return 500 when the owner is operationally suspended |  |
| GA-099 | low | S | P5-T2a | The template validator never checks group min/max occurrence bounds |  |
| GA-100 | low | M | P5-T7 | The REJECTED completeness state is never produced | yes |
| GA-101 | low | S | Phase 5 exit criteria | The progress formula is undocumented and the summary label is wrong |  |
| GA-102 | low | S | P5-T10 | The inactive-condition notice ignores occurrence path |  |
| GA-103 | low | S | P5-T9 | Help docs do not cover adding, removing, or reordering repeated entries |  |
| GA-104 | low | S | P5-R22 | Recipient-safe Template projection keeps reviewStages, including section keys of fully denied sections |  |
| GA-105 | low | S | P5-R15 / P5-R09 | Frontend still has path-parsing and Template-id fallbacks for runtime identity |  |
| GA-106 | low | S | P5-R17 | A hidden REQUIRED requirement that carries a condition rule is counted INCOMPLETE |  |
| GA-107 | low | S | P4-T4 | Access link expiry and use limit are not validated |  |
| GA-108 | low | M | P4 Tests to write first | Several named Phase 4 tests are missing |  |
| GA-109 | low | S | P4-T6 | Request detail projection shows owner and internal identifiers to every party |  |
| GA-110 | low | S | P3-T5 | Request ETags for aggregate, party and response revisions share one namespace and can satisfy each other |  |
| GA-111 | low | S | P3-T1c | share_resource_type_check not widened for the Requirement-occurrence type | yes |
| GA-112 | low | S | P3-T1c | No characterization of Document and Principal Group Share capability derivation, whose behavior changed |  |
| GA-113 | low | S | P3-T6 | Missing Fields coexistence and legacy assignment isolation tests |  |
| GA-114 | low | S | P3-T8 | No tests proving participant-principal parties are refused by resend and acceptance-policy paths |  |
| GA-115 | low | S | P3-T8 | V97 performs the legacy backfill and owner-derivation pass the plan forbade |  |
| GA-116 | low | S | REST-IfMatch428-412-ETag | Several successful mutations return no current ETag |  |
| GA-117 | low | M | CDM-TemplateSection | Template sections have no localized title or help | yes |
| GA-118 | low | S | Phase 6 Tests to write first | No existing-Document linking path and no 'Existing Document linking' tests | yes |
| GA-119 | low | S | P6-T9 | Per-file and no-auth upload limits are enforced only after the whole multipart body is received and hashed |  |
| GA-120 | low | S | AD-9 | Response envelope references the mutable Value Set, not an exact Field Value Revision |  |
| GA-121 | low | S | P11-T2 | Bundle Template Version references need a content hash the platform never produces, and bundles are only validated | yes |
| GA-122 | low | S | P7-T1a | Frozen supporting-link member is not tied to its request by a composite key or guard |  |
| GA-123 | low | M | P7 Tests to write first / P7-T4 | Attestation service refusals and the naming boundary are untested |  |
| GA-124 | low | S | P7 Tests to write first / P7-T5 | No test that an amendment Notice Intent is recovered when its event was consumed before any consumer existed |  |
| GA-125 | low | S | P7-T9 | Respondent workspace shows 'Request more information' to callers who cannot request a supplement |  |
| GA-126 | low | S | P7-T5 / help docs | Help says the requesting party can amend a request, but no screen offers an amendment |  |
| GA-127 | low | M | P9-Decision-12 | Privacy deletion refuses entirely when the subject has any open request | yes |
| GA-128 | low | M | P9-Decision-3 | Only subscription refusals are SKIPPED; other Workflow trigger failures retry forever and block the request's ordered queue | yes |
| GA-129 | low | S | P9-Decision-5 | Empty requirement operand skips the value type check (IS_EMPTY matches across types) |  |
| GA-130 | low | S | P8-T7 / P8-T11 | Reconsider is offered for satisfied or non-reopenable reviews that the backend always refuses; help text is wrong |  |
| GA-131 | low | S | Tests to write first / Exit criteria | No multiple-correction-cycle history test |  |
| GA-132 | low | S | P8-T5 | Separation of duties leaves the SUBJECT role and earlier non-final responders out of 'answering party' |  |
| GA-133 | low | S | Phase 8 decision 7 | Respondent review results leak remediation records for reviewers-only findings and hidden items |  |
| GA-134 | low | S | P8-T1 | Review due instant cannot be set from the UI |  |
| GA-135 | low | S | P10-T4 | Unsafe retry for review findings and comments, which have no ETag and get a new Idempotency-Key per attempt |  |
| GA-136 | low | M | Phase 10 Tests to write first / P10-T8 | No responsive-state tests; respondent evidence replacement and preview flows untested |  |
| GA-137 | low | S | P9-T8 | Reading notice content and endpoints leaves no access history |  |
| GA-138 | low | S | P9-T9 | Disposal retry failures are not audited |  |
| GA-139 | low | S | Tests to write first | No concurrent notice claim-race test and no ordered communication-timeline test |  |
| GA-140 | low | M | P12 | Capability discovery omits the plan quotas and reports new work as available when the open-request allowance is used up |  |
| GA-141 | low | M | P12 | Request detail, operations queue and review queue projections carry no execution standing | yes |
| GA-142 | low | S | P12 | Revocation guidance describes an admin action that does not exist and a recovery path the code refuses | yes |
| GA-143 | low | S | P12 | Contact-code lockout and challenge exhaustion log no abuse marker |  |
| GA-144 | low | S | P12 | No concurrency test for the committed-evidence reservation at issuance |  |
| GA-145 | low | S | P12 | No test that record exports stay available under suspension, revocation or lapse |  |
| GA-146 | low | S | CDM-InformationRequestRequirement, P3-T5 | Runtime Requirement revision effective interval is never closed |  |
| GA-147 | low | S | P1-T7c, REST-TryCatchAndResourceContractTests | ExchangeFieldsResource endpoints share one guard and one generic error log instead of per-method return try/catch |  |
| GA-148 | low | S | AD-24, CDM-RequestAccessContext, P4-T4 | Bootstrap ShareLink does not record last use or verification strength |  |
| GA-149 | low | M | AD-32, P4-T4, REST-DualSurfacesSharedServices | Registration upgrade skips trusted-recipient validation | yes |
| GA-150 | low | M | Completeness critic | Clock policy define and version publish have no Idempotency-Key or If-Match, and a concurrent publish surfaces as 500 |  |
| GA-151 | low | S | Completeness critic | No test changes a hold's scope successfully or covers platform-owned holds |  |
| GA-152 | low | S | Completeness critic | Owner audit search omits classified IR events whose target is not a request (privacy requests, clock policies, Templates) | yes |
| GA-153 | low | S | Cross-phase gates | Help and UI say a platform administrator can stop a request, but grant revocation has no caller or endpoint |  |
| GA-154 | low | S | Cross-phase gates | Flyway allocation ledger omits 14 program migrations and keeps stale statuses |  |
| GA-155 | low | S | Cross-phase gates | 30 program migrations contain descriptive SQL comments, which AGENTS.md forbids |  |
| GA-156 | low | M | AGENTS.md rules sweep | No-auth request-scope binding and the App-User-only upgrade gate are decided in the HTTP layer |  |
| GA-157 | low | S | AGENTS.md rules sweep | Test class and constant names carry plan phase numbers |  |
| GA-158 | low | S | AGENTS.md rules sweep | Delegated-authority tests use a legal-domain 'power-of-attorney' fixture |  |
| GA-159 | low | M | AGENTS.md rules sweep | Styled IR child components borrow a parent's Styles file instead of their own co-located one |  |
| GA-160 | low | M | AGENTS.md rules sweep | About 45 rendered IR elements have no id (Text, Tooltip, Field, Badge, div, li) |  |
| GA-161 | low | L | AGENTS.md rules sweep | Several IR services are oversized multi-responsibility classes |  |
| GA-162 | low | M | Wiring sweep | Personally owned requests never start the owner's personal Workflows; only platform-scope definitions run | yes |
| GA-163 | low | S | Wiring sweep | Access-link replacement and author-set expiry or use limits are REST-only, though help describes them |  |
| GA-164 | low | M | Wiring sweep | Expiry refresh rules have no screen, while help says a follow-up can come from a refresh set up for the request |  |
| GA-165 | low | M | Wiring sweep | Operations queue UI cannot filter by request state, Exchange or exception kind, so finished requests always appear |  |
| GA-166 | low | S | Wiring sweep | Dead and misleading Information Request code: an unused listing endpoint and frontend read, uncalled service methods, an unused Action, and [truncated] |  |
| GA-167 | high | M | New (Phase 13, P13-T3) | Every Exchange requires at least one document, even when its Blueprint's Information Request collects the documents |  |
| GA-168 | high | M | New (Phase 13, P13-T2, P13-T4) | Starting an Exchange from a Blueprint that pins a Template Version never creates the Information Request |  |

## Decisions Needed

The plan leaves these points open. Each one has a recommended default. Remediation proceeds on the
recommended default unless the user chooses otherwise.

- **GA-002** Placeholder foreign key to blueprint_document_default breaks every later Blueprint save
  - Question: The high-severity FK defect needs no product decision: dropping the constraint in V151 is enough. Only the write-only placeholders need one. What should a request's Blueprint document placeholder do? Options: (a) show it on the request page to the author, and optionally to respondents, as an expected document with its title, required flag, allowed type and linked library item; (b) link it to a Document Requirement, or turn it into one, so its required flag is enforced; (c) treat it as internal provenance only and change the help text that says a Blueprint's documents become placeholders. / For the related write-only placeholder gap (not needed for the FK fix): should Blueprint document [truncated]
  - Recommended: Drop the foreign key and the source column in V151 (the placeholder is already a full snapshot). Show placeholders read-only to the author in the management workspace as expected documents.
- **GA-011** Confidentiality compartments are only an MFA-freshness gate, never per-party clearance; no-auth respondents can never answer them and there is no step-up flow
  - Question: What should a confidentiality compartment mean? Option (a): "sensitive, requires a recent step-up", where the key is just a label. Option (b): a named compartment that only parties explicitly cleared for it can reach, plus step-up. And which step-up factor should an email-only no-auth respondent present? Options: a fresh second email OTP within a freshness window, or no bootstrap access at all, forcing sign-in, like requireRecipientSignIn. / Should confidentiality compartments stay a step-up gate, or become per-party clearance?
- If step-up: should no-auth respondents be able to step up with a fresh contact-proof OTP, or should issuing a bootstrap link be refused for a party assigned a [truncated]
  - Recommended: (a) A compartment means "sensitive: requires a recent step-up", with the key as a label. No-auth respondents step up with a fresh contact-proof OTP inside a freshness window. Owners, reviewers and decision makers also need the step-up.
- **GA-015** Runtime request audit events never carry the access-session ID or a safe actor label
  - Question: Should no-auth Information Request audit history use PUBLIC_LINK as Decision 20 requires, which means changing the mapping for no-auth sessions and possibly dropping the new PARTICIPANT audit kind that Fields also uses? Or should the plan be amended to accept the PARTICIPANT audit actor kind that was introduced during implementation?
  - Recommended: Keep the PARTICIPANT audit actor kind, because it names the real canonical principal and Fields already relies on it. Record the access-session id as the safe label, and amend the Decision 20 text that says PUBLIC_LINK.
- **GA-024** ARCHIVE_OUTSIDE_ACTIVE_RESPONSE behaves exactly like RETAIN_SECURELY
  - Question: The plan requires a separate archive-outside-the-active-response policy but never says how it differs from retain securely. Should archive move the hidden answer (envelope plus collected Field values) into a separate append-only archive snapshot and clear the live values? If so, should the snapshot be restored when the condition turns true again, or kept only as history? Or should ARCHIVE_OUTSIDE_ACTIVE_RESPONSE be removed and the policy set cut to retain and clear?
  - Recommended: Implement ARCHIVE_OUTSIDE_ACTIVE_RESPONSE as: snapshot the hidden answer into an append-only archive record, clear the live values, and do not restore automatically when the condition turns true again.
- **GA-031** Participant registration upgrade endpoint has no frontend caller
  - Question: Should the registration upgrade become a self-service respondent step? Options: (a) an explicit "Link to my account" action in the no-auth workspace, shown to a signed-in viewer after contact verification, with a sign-in redirect for others; (b) automatic linking when a signed-in viewer's verified email matches the participant; or (c) leave it API-only and narrow the help article to match.
  - Recommended: (a) An explicit "Link to my account" action in the no-auth workspace after contact verification, with a sign-in redirect.
- **GA-034** Trusted-organization party selection command is unreachable (no REST endpoint, no UI)
  - Question: How should authors assign a party from a trusted organization to an Information Request?
- (a) Pick from the parent Exchange's accepted trusted recipients, bound through `exchangeRecipientId`, and delete the unused selection command.
- (b) Expose the selection-based command, a trusted person or published group, as a REST option and UI picker.

Separately, should adding someone by email be refused or redirected when that person is a verified member of a trusted organization?
  - Recommended: (b) Expose the trusted person or published group selection as a REST option and UI picker. Refuse email entry for a verified member of a trusted organization with guidance to use the trusted picker.
- **GA-038** Validation errors carry no Requirement ID or field path, and Field validation errors drop the reason code and field key
  - Question: The Phase 10 refinements record "A response save refusal names no part" as accepted behavior (line 3608), which conflicts with line 823. Should response-save refusals now name the failing Requirement, occurrence path and Field, so the respondent workspace can focus the item? And should that use a dedicated refusal DTO for Information Requests, or new optional fields on the shared ResponseError?
  - Recommended: Yes. Add requirementId, occurrencePath and fieldKey to a dedicated Information Request refusal DTO and focus the item in the workspace. Line 823 is authoritative over the Phase 10 note.
- **GA-058** Execution grant is missing entitlement/policy version, trial grant reference, continuation actions and feature snapshot
  - Question: The plan asks the execution grant to store an "entitlement and policy version" and "permitted continuation actions", but nothing in the code defines either. Should we add a code-owned plan-policy version constant and a closed continuation-action vocabulary saved per grant, with the Field adapter and amendments checking a saved feature snapshot? Or should the P4-T7 and Decision 14 text be narrowed to what exists today? Today the frozen plan code, status, enforcement mode and allowances stand in for the policy, the continuation rules are the same for every grant, and features are implied by the grant existing.
  - Recommended: Implement it: a code-owned plan-policy version constant, a closed continuation-action vocabulary stored per grant, and a feature snapshot that the Field adapter and amendments check.
- **GA-067** Request-scoped trusted group Shares expand without trusted-group eligibility reconciliation
  - Question: The group-expansion hold-back for request Shares follows from existing trust policy and needs no decision. Two matrix cells are not settled by the plan:
(a) Issuance: when a draft Information Request has a trusted party whose relationship is suspended (or whose group became ineligible), should issue be refused with 409 INFORMATION_REQUEST_TRUST_SUSPENDED, or proceed with that party kept?
(b) Authorization: should the authorization group-mediated path (DefaultAuthorizationService step 3) and Requirement party resolution also exclude members who joined a trusted group while it was ineligible? Fixing step 3 would change Exchange behavior as well.
  - Recommended: (a) Refuse issuance with a stable 409 while any party has a suspended or ineligible trust relationship. (b) Limit the group-expansion fix to request-scoped Shares; leave Exchange authorization unchanged.
- **GA-078** Delegated authority has no read endpoint, no frontend and no help docs
  - Question: When a request party is reassigned to a new principal (or revoked), should delegated authorities granted for that party be revoked automatically (recommended), or should they survive the reassignment? Separately, should delegated authority get a management-workspace UI now, or only a read endpoint plus help docs?
  - Recommended: Revoke delegated authorities automatically on reassignment or revocation. Add a read endpoint, a management-workspace section and help text.
- **GA-079** Workflow designer cannot author requirement conditions for request triggers
  - Question: Should requirement conditions for Information Request workflow triggers be authorable in the workflow designer UI, or is API-only authoring (the current, documented behavior) acceptable?
  - Recommended: Add requirement-condition authoring to the Workflow designer for Information Request triggers.
- **GA-080** External typed-reference evidence subtype has no writer, command, endpoint or UI
  - Question: Should respondents be able to record an external typed reference (type plus value) as evidence for a Requirement, with a command, endpoint and UI and an eligibility rule for such evidence? Or should the unused external-reference subtype be removed from the schema and model?
  - Recommended: Remove the unused external-reference subtype in a forward migration. External evidence is a later configurable extension (plan line 330).
- **GA-087** Template READ granted to every organization member, who can then read drafts and copy them out
  - Question: Organization members need to read published Templates to create requests from them. Should members also be allowed to clone a published organization Template into their personal scope, the way they can clone Blueprints and Workflows, or should cloning require Template write access on the source?
  - Recommended: Members may read and clone published organization Templates only. Drafts are readable only by Template writers.
- **GA-088** Subject merge and supersession history has storage but no service
  - Question: The plan only asks for a merge and supersession foundation, and V85 already provides that at the storage level. Should the application get an operation that records subject merges and supersessions: who may do it, with which permission, and is it exposed in the UI? Should subject resolution and listing follow the successor chain? Or should the storage-only lineage stay as-is and be written down as a follow-up?
  - Recommended: Keep the storage-only lineage and record merge and supersession operations as a documented follow-up.
- **GA-089** No web-app surface for platform-administered personal feature overrides
  - Question: The plan's P2-T1d2 names only the backend parts (service, resource, approval, audit), and those are done. Should platform admins also get a web-app editor for per-user feature overrides in the user-subscriptions editor, matching the organization editor? Or is API-only access acceptable, given that the Personal plan already includes INFORMATION_REQUESTS?
  - Recommended: Add the per-user override editor to the platform user-subscriptions editor, matching the organization editor.
- **GA-093** No production path creates a PLATFORM Template, so the platform list, copy and choice paths are always empty
  - Question: How should platform Information Request Templates come to exist: a platform-admin authoring API and UI, a platform-admin promotion of an organization Template, or no platform Templates for now, with the Platform tab, copy path and Blueprint platform choices hidden until later?
  - Recommended: Let platform administrators author PLATFORM Templates through the existing Template editor.
- **GA-100** The REJECTED completeness state is never produced
  - Question: Should a reviewer's REJECTED or CHANGES_REQUIRED outcome on a returned Requirement show up in the respondent's completeness and progress as REJECTED, counted in the denominator but not the numerator and blocking resubmission until revised? Or should the unused REJECTED completeness state and its readiness branch be removed?
  - Recommended: Implement it: a returned REJECTED or CHANGES_REQUIRED Requirement shows REJECTED, counts in the denominator only, and blocks resubmission until revised.
- **GA-111** share_resource_type_check not widened for the Requirement-occurrence type
  - Question: Should Requirement occurrences be able to hold their own Share rows? If yes, we widen share_resource_type_check in V151 and decide which role keys they accept. If no, we keep inheritance-only authorization and correct the plan text that says the check must admit INFORMATION_REQUEST_REQUIREMENT.
  - Recommended: No per-occurrence Shares. Keep inheritance-only authorization and correct the plan text.
- **GA-117** Template sections have no localized title or help
  - Question: Line 703's "localized title and help" has no locale infrastructure behind it anywhere on the platform. Should it be built now, and if so, how? The open choices are: per-locale variant rows, or dropping the word from the model; which locale is authoritative, and whether every variant must exist before publication; whether the viewer's locale comes from Accept-Language or a stored profile/org preference; and whether requirement prompt and help must be localized in the same way.
  - Recommended: Drop "localized" from the model description. No locale infrastructure exists anywhere on the platform.
- **GA-118** No existing-Document linking path and no 'Existing Document linking' tests
  - Question: Should Information Requests let a party link a Document Version that already exists, such as an Exchange Document, as evidence? Or should the plan's linking test bullet and exit-criterion clause be struck, with evidence always uploaded as new request-owned versions?
  - Recommended: Strike existing-Document linking from the plan. Evidence is always uploaded as new request-owned versions.
- **GA-121** Bundle Template Version references need a content hash the platform never produces, and bundles are only validated
  - Question: Should bundle Template Version references carry a platform-computed content hash that the validator checks against real Template Versions (exposed on the Template Version DTO, or stored via V151), or should the hash field be removed so references pin by templateKey and versionNumber only?
  - Recommended: Expose a platform-computed content hash on the Template Version DTO and validate bundle references against it.
- **GA-127** Privacy deletion refuses entirely when the subject has any open request
  - Question: When a subject has both finished and still-open Information Requests, should a privacy deletion dispose the finished ones and report the open ones as skipped, or keep refusing the whole deletion with RECORD_NOT_FINISHED, as the "all or nothing" Done note and the current tests do?
  - Recommended: Dispose finished requests and report still-open ones as skipped, per Phase 9 Decision 12.
- **GA-128** Only subscription refusals are SKIPPED; other Workflow trigger failures retry forever and block the request's ordered queue
  - Question: When a matching Workflow definition cannot start for a non-subscription, deterministic reason (corrupt spec, unresolvable assignee), should the trigger consumer record a SKIPPED receipt so the request's later events keep flowing? The alternative is to keep the current never-discard retry, which blocks the request's ordered queue until an operator fixes the definition.
  - Recommended: Record a SKIPPED receipt with an operator-visible reason for deterministic non-subscription failures, so the ordered queue keeps flowing.
- **GA-141** Request detail, operations queue and review queue projections carry no execution standing
  - Question: The checked P12-T4a subtask limits the requirement to workspaces and Exchange listing rows, but Decision 5 says every request projection. Should the execution standing also be added to the request detail/list, operations queue and review queue projections, or is the narrower subtask scope the accepted interpretation?
  - Recommended: Carry execution standing on every request projection, including the operations queue, operations detail and review queue.
- **GA-142** Revocation guidance describes an admin action that does not exist and a recovery path the code refuses
  - Question: Should a request whose execution grant is revoked still be cancellable, and possibly supersedable, by its owner so it stops holding an open-request place and evidence allowance and stops blocking Exchange ending? Or should the docs just say a revoked request stays non-terminal and the owner must author a new request?
  - Recommended: Let the owner cancel or supersede a request whose grant is revoked so it reaches a terminal state, and correct the guidance.
- **GA-149** Registration upgrade skips trusted-recipient validation
  - Question: Registration upgrade gives an email-only Participant a new App User Share. What should happen when that App User belongs to an organization whose trust relationship with the request owner's organization is suspended: refuse the upgrade, grant it anyway, or keep the Participant session but grant no user Share? And is an upgrade allowed on an ENDED Exchange as a read-only identity link, or refused outright?
  - Recommended: Refuse the upgrade while the trust relationship is suspended. Refuse upgrades on an ENDED Exchange.
- **GA-152** Owner audit search omits classified IR events whose target is not a request (privacy requests, clock policies, Templates)
  - Question: Should the owner-scope Information Request audit search also include the owner's Template, due date (clock) policy and privacy request events, or stay limited to events that target a request? Personal owners currently cannot see those events anywhere, and organization owners can reach them only through the general organization audit log.
  - Recommended: Include the owner's Template, clock policy and privacy request events in the owner audit search. Personal owners have nowhere else to see them.
- **GA-162** Personally owned requests never start the owner's personal Workflows; only platform-scope definitions run
  - Question: 
  - Recommended: Yes, for request triggers only: PERSONAL definitions replace APP definitions, as ORG definitions do today. Exchange triggers stay unchanged.

## Gap Details

### GA-001: Delegated-authority grant and revoke write no audit event, domain event or history

- Severity: high. Verification: confirmed. Fix size: M. Audit key: `AUDIT-ACCESS`.
- Plan reference: AD-20, P3-T12, P4-T4. Plan basis: Plan: plans/DOCUMENT-DRIVEN-INFORMATION-REQUESTS-IMPLEMENTATION-PLAN.md

Requirements in force:
- Decision 20 (lines 492-498): every state-changing service records its classified audit event and immutable history in the same transaction; Information Request event types go into the closed catalog; no-auth history uses PUBLIC_LINK with a stable non-secret party or participant id.
- Decision 21 (499-509): a versioned event envelope is persisted when the change has downstream meaning.
- P3-T2 [truncated]

Current state:

Several credential and authority mutations on Information Requests change state with no Information Request audit event, no transition-history row and no domain event. They can be reached from REST today.

1. Delegated authority. InformationRequestDelegatedAuthorityService.grant and revoke save or deactivate the authority row and bump request.partyRevision. The service injects no AuditRecorder, InformationRequestTransitionHistoryService or DomainEventPublisher. The row records grantor, instrument, effective and expiry dates, and revoker plus reason (V102), so the P3-T12 "record" wording is met at row level. What is missing is the audit event and immutable history that Decision 20 and P3-T2 require for every mutation.

2. Bootstrap access links. InformationRequestBootstrapShareLinkService issue, rotate, replace and revoke write ShareLink rows and revoke sessions with no audit or history.

3. Contact proof. InformationRequestContactProofService.issueChallenge sets the OTP hash and count. verifyChallenge increments usedCount and mints a RequestAccessSession. Neither is audited.

4. Access sessions. RequestAccessSessionService.issue, revoke and revokeAllForShareLink write nothing to audit.

5. Registration upgrade. InformationRequestParticipantAccountUpgradeService creates a ParticipantAccountLink and revokes bootstrap links and sessions with no Information Request audit or history.

Narrowing, which is why claims 2 and 3 are partial:
- The upgrade's Share grant goes through ShareService.grantRoleKeyWithPrincipalProvenance. That records authorization.share.grant with target [truncated]

Evidence:

Code, under src/main/kotlin/com/docuhyphen/app/api/service/:
- informationrequest/InformationRequestDelegatedAuthorityService.kt
  - 66-73: constructor has only repositories, AuthorizationService and CommandReceiptService.
  - 123-181 (grantMutation): saves the authority, partyRevision += 1, no audit.
  - 183-214 (revokeMutation): sets active=false and revokedAt/By/reason, no audit.
- informationrequest/InformationRequestBootstrapShareLinkService.kt
  - 72-81: constructor has no audit or history.
  - 122-173 issue, 209-232 rotate, 268-297 replace, 339-352 revoke, 330-337 revokeAllForShare. None records audit.
- informationrequest/InformationRequestContactProofService.kt
  - 30-38: constructor has no audit.
  - 41-69 issueChallenge: OTP hash and challenge count updated.
  - 72-120 verifyChallenge: usedCount += 1 at 107, session issued at 114-119.
- informationrequest/RequestAccessSessionService.kt
  - 21-23: only sessionRepository is injected.
  - 26-60 issue, 109-119 revoke, 122-129 [truncated]

Fix outline:

1. Catalog (AuditEventType.kt)
- Add INFORMATION_REQUEST category entries:
  - information_request.delegation.grant and .revoke
  - information_request.access_link.issue, .rotate, .replace, .revoke, .challenge and .verify
  - information_request.access_session.issue and .revoke
  - information_request.account_link.create
- Bump CATALOG_VERSION from 29 to 30 and update the pins in AuditEventTypeTest.

2. Mutations and migration
- Add to InformationRequestMutation (InformationRequestTransitionMatrix.kt): GRANT_DELEGATED_AUTHORITY, REVOKE_DELEGATED_AUTHORITY, ISSUE_ACCESS_LINK, ROTATE_ACCESS_LINK, REPLACE_ACCESS_LINK, REVOKE_ACCESS_LINK, VERIFY_CONTACT_PROOF and LINK_PARTICIPANT_ACCOUNT.
- Allow them in the matrix for the right request states.
- Map them in InformationRequestTransitionHistoryService.auditEventTypeFor.
- Make sure responseStart.startsWith does not treat them as a response start.
- Migration V151__information_request_access_audit_mutations.sql: drop ck_information_request_transition_mutation and re-add it with the new values.

3. Services
- Inject InformationRequestTransitionHistoryService and call record(...) inside each command-receipt mutation lambda, so audit, history and DomainEvent commit atomically and a replay records nothing:
  - InformationRequestDelegatedAuthorityService grant and revoke
  - InformationRequestBootstrapShareLinkService issue, rotate, replace and revoke
  - InformationRequestContactProofService.verifyChallenge
  - InformationRequestParticipantAccountUpgradeService.upgradeMutation (in addition to the existing SHARE_GRANT)
- Actor:
  - [truncated]

### GA-002: Placeholder foreign key to blueprint_document_default breaks every later Blueprint save

- Severity: high. Verification: confirmed. Fix size: M. Audit key: `BLUEPRINT-PLACEHOLDER`.
- Plan reference: Baseline: Blueprints, P3-T9b. Plan basis: Plan line 368 (Blueprints: "Preserve current Blueprint behavior", exact Template Version reference for future instantiations). Line 369 (map existing defaults into document placeholders, no parallel default mechanism). Lines 654-659 (design decision 35). Lines 1560-1574 (P3-T9 and P3-T9b, including "prove no respondent data is copied between Exchanges or treated as submitted"). Line 1731 (Blueprint default mapping tests). Lines 3979-3981 (Phase 12 decision 4). Lines 4067-4075 (P12-T3: mutable [truncated]

Current state:

1) (High, confirmed) V100 gives information_request_document_placeholder.source_blueprint_document_default_id a NOT NULL, non-deferrable foreign key to blueprint_document_default(id) with no ON DELETE action. Nothing after V100 changes it. The Blueprint editor always sends exchangeDocuments on update. BlueprintDefinitionService.updateBlueprint then calls persistDocuments, which bulk-deletes every blueprint_document_default row for the Blueprint and inserts new ones. BlueprintDocumentConfig carries no id, so even a no-op save deletes the rows. After POST /information-requests with blueprintDefinitionId has created a request from a Blueprint that has document defaults, every later editor save of that Blueprint hits a 23503 FK violation. The transaction rolls back and the resource's generic catch returns 500 "Failed to update blueprint". The Blueprint's name, tags, documents, participants and field defaults can then no longer be edited. That breaks "Preserve current Blueprint behavior" and "Existing mutable Blueprint Definitions retain their behavior". Blueprint delete is a soft delete and clone only inserts, so neither is affected. Participant and field defaults have no inbound FKs, so only document defaults cause the failure. The placeholder already copies title, flags, order and library metadata, so the FK adds nothing to the snapshot. No test updates a Blueprint's documents after instantiation: the pinning test only runs a SQL UPDATE of template_version_id, and the V100 contract test never deletes the default row.
2) (Medium, partial) Placeholders are written only by [truncated]

Evidence:

Paths are relative to C:/Users/Black/IdeaProjects/doc-hyphen.
Claim 1:
- src/main/resources/db/migration/V100__information_request_blueprint_document_placeholders.sql:5 declares the column NOT NULL, and :22-23 declares CONSTRAINT information_request_document_placeholder_default_fkey FOREIGN KEY (source_blueprint_document_default_id) REFERENCES blueprint_document_default (id), with no ON DELETE and not DEFERRABLE.
- Later migrations only DELETE placeholders in the disposal functions (V142:1290, V146:189, V147:349, V148:653). None alters the FK. Head is V150.
- src/main/kotlin/com/docuhyphen/app/api/service/blueprint/BlueprintDefinitionService.kt:204 runs request.exchangeDocuments?.let { persistDocuments(bp.id, it) }. Lines :531-547 call persistDocuments, which at :533 calls documentDefaultRepository.deleteAllByBlueprintDefinitionId and then re-inserts new rows.
- src/main/kotlin/com/docuhyphen/app/api/repository/blueprint/BlueprintDocumentDefaultRepository.kt:25-30 is a bulk JPQL [truncated]

Fix outline:

Claim 1 (S):
- Add V151__information_request_placeholder_blueprint_source.sql. Do not edit V100: the local history already includes it.
- In V151, drop information_request_document_placeholder_default_fkey. Then pick one option:
  (a) Drop the source_blueprint_document_default_id column, because the placeholder is a full snapshot. Also remove the field from InformationRequestDocumentPlaceholder.kt and its assignment in InformationRequestBlueprintInstantiationService.materializeDocumentPlaceholders.
  (b) Keep the column as an unconstrained provenance UUID, nullable or not.
  Option (a) is the cleanest under the no-backwards-compat rule.
- Tests:
  - Extend InformationRequestBlueprintDocumentPlaceholderContractTest, or CleanSchemaMigrationContractTest, to delete the blueprint_document_default row after inserting a placeholder and assert the placeholder survives unchanged.
  - Add a PostgreSQL Quarkus test, modeled on InformationRequestBlueprintVersionPinningTest, that creates a Blueprint with a document default, instantiates a request through createFromBlueprint, then calls BlueprintDefinitionService.updateBlueprint with a changed exchangeDocuments list. Assert the update succeeds, the Blueprint's new defaults are stored, and the request's placeholder keeps its original title and flags.
- No help change needed, because this restores documented behavior.
Claim 2 (M, product decision): decide what a placeholder does, then wire a read path:
- Call findForRequest from an application service.
- Add a placeholder DTO with a mapper class to the author request detail projection, [truncated]

Decision needed. Recommended default: Drop the foreign key and the source column in V151 (the placeholder is already a full snapshot). Show placeholders read-only to the author in the management workspace as expected documents.

### GA-003: Clear-with-confirmation cannot be completed from the UI, which blocks saves

- Severity: high. Verification: confirmed. Fix size: M. Audit key: `CLEAR-UI`.
- Plan reference: P5-R25, P5-T4b. Plan basis: Plan lines that put the requirement in force:
- 2162-2168: P5-T4 / P5-T4b, "clear with explicit confirmation" when conditions become false or unknown.
- 2181-2192: P5-T9, the shared UI must handle conditions and sparse save.
- 2213: tests for hidden response retain and clear policies.
- 2259-2262: P5-R03, explicit clear through the Fields engine.
- 2314-2317: P5-R16.
- 2357-2360: P5-R25, "add the explicit hidden-data clear-confirmation flow ... accepted/cancelled clearing in UI interaction [truncated]

Current state:

The server-side CLEAR_WITH_CONFIRMATION policy works only when the PATCH that makes a condition non-TRUE already lists the affected runtime Requirement IDs in confirmedHiddenResponseClearRequirementIds. The UI has no way to learn those IDs.

Server side:
- In the same PATCH, InformationRequestResponseDraftService.enforceHiddenResponsePolicies treats FALSE and UNKNOWN alike as hidden (line 468).
- If a dependent Requirement still holds data and its ID was not confirmed, it throws INFORMATION_REQUEST_HIDDEN_RESPONSE_CLEAR_CONFIRMATION_REQUIRED from clearHiddenFields (598-600) or hideResponse (546-551), and the whole transaction rolls back.
- Both resources turn that into a 409 whose body is only ResponseError(message, reasonCode). It does not say which Requirements are affected.

Frontend side:
- hiddenClearConfirmations (structuredResponseWorkspaceState.ts 223-253) only lists responses whose own server evaluation is already FALSE (line 234). It never looks at UNKNOWN.
- The workspace read never returns a response for an inactive Requirement: InformationRequestResponseWorkspaceService.load filters through InformationRequestActiveResponseProjection.isActive (81-86), which is TRUE-only, and builds responses from that list (101).
- A refused save leaves the evaluations unchanged. So against the real server the list is always empty, the checkbox component never renders, and the controller always sends [] (useStructuredResponseWorkspaceController 51-52, 97).
- No frontend code handles the reason code. The respondent only sees the refusal text through commandErrorMessage.

The [truncated]

Evidence:

Backend (src/main/kotlin/com/docuhyphen/app/api/):
- service/informationrequest/InformationRequestResponseDraftService.kt:
  - 240-255: the hidden-policy loop runs inside every patch.
  - 443-503: enforceHiddenResponsePolicies; line 468 treats only TRUE as visible.
  - 477-479: clearHiddenFields is called under CLEAR_WITH_CONFIRMATION.
  - 546-551 (hideResponse) and 598-600 (clearHiddenFields): throw HIDDEN_RESPONSE_CLEAR_CONFIRMATION_REQUIRED unless the ID is in confirmedClears.
  - 575-578: hasActiveResponseData is true for any disposition other than NOT_ANSWERED, or any narrative or fieldValueSetId.
  - 350-356: NOT_ANSWERED is rejected as a patch disposition.
- resource/informationrequest/InformationRequestResponseResource.kt:125 and InformationRequestNoAuthRequestResource.kt:356: the lifecycle exception becomes a 409 with ResponseError(message, reasonCode) only, with no affected IDs.
- service/informationrequest/InformationRequestResponseWorkspaceService.kt:
  - 81-86: [truncated]

Fix outline:

No migration is needed (V151 stays free).

Backend:
1. Add a subclass such as InformationRequestHiddenResponseClearRequiredException(requirements: List<HiddenResponseClearTarget>) of InformationRequestLifecycleException, with its data class in model/informationrequest.
2. In InformationRequestResponseDraftService.enforceHiddenResponsePolicies / clearHiddenFields / hideResponse:
   - Collect every unconfirmed data-bearing Requirement across all cascade iterations and occurrences instead of throwing on the first one.
   - Then throw once with runtime requirementId, sourceTemplateBindingId and occurrencePath.
   - Include only Requirements the caller may VIEW. If an affected Requirement belongs to another party, refuse with a distinct reason code rather than leaking it. clearHiddenFields already refuses with Forbidden after confirmation. Record this cross-party case in the plan.
3. Add a response DTO such as HiddenResponseClearRequiredErrorDto(message, reasonCode, hiddenResponseClears) in model/dto, with a dedicated mapper class.
4. Map the new exception in both InformationRequestResponseResource.handleException and InformationRequestNoAuthRequestResource.handleException, keeping the 409.
5. Add a way for fact recertification to pass confirmations, or surface the same 409 from InformationRequestFactRecertificationService.

Frontend:
1. models.tsx: add the error DTO interface and the reason-code constant.
2. Make sure informationRequestRuntimeService / structuredResponseCommands keep the 409 body.
3. In useStructuredResponseWorkspaceController:
   - On that reason code, store [truncated]

### GA-004: Upgraded participant's App User Share is never revoked on party revocation or reassignment

- Severity: high. Verification: confirmed. Fix size: M. Audit key: `G070-P4-T4`.
- Plan reference: P4-T4. Plan basis: Plan lines 566-569 (Phase 4 design, resource-scoped Shares) say that party creation, reassignment, revocation, group inheritance and registration upgrade "materialize or revoke their corresponding Share rows in the same transaction". Lines 1829-1835 and 1854-1858 (P4-T4) require granting the App User an equivalent request Share on upgrade. Exit criteria at lines 2040-2042 require the upgrade to extend access to future work and both API surfaces to enforce identical request behavior. Lines [truncated]

Current state:

When a Participant-held party goes through a verified registration upgrade, the App User gets its own DIRECT USER Share on the Information Request with the party's role key. Nothing links that Share back to the party: party.shareId still points at the Participant's Share, the Share has sourceShareId = null, and the party has no column for it. Revoking the party (revokeMutation, and revokeForOwnershipChange through revokeParty) and reassigning it (reassignMutation) revoke only party.shareId and its bootstrap links. ShareService.revoke then cascades only to rows whose sourceShareId matches, so the App User's Share stays ACTIVE.

Requirement-level actions are safe. Requirement view, evidence view, respond, attest, upload and withdraw require an active assigned party, and a linked user only counts through equivalentPrincipalsFor on active parties. Reviewer actions also check for an active reviewer party.

Everything checked only by capability stays open to the revoked person:
- INFORMATION_REQUEST_VIEW: request findById and listForExchange, the party list, amendment, review-comment, business-decision, submission and successor reads.
- For an upgraded DECISION_MAKER party, the Share also keeps INFORMATION_REQUEST_ADMIN, WRITE, ISSUE, CANCEL and EXPORT. That includes INFORMATION_REQUEST_MANAGE_PARTIES, so a revoked party could reassign parties.

The same leak happens on reassignment. No code anywhere reads ParticipantAccountLink to find and revoke the linked user's Share, and no test covers revoking or reassigning a party after an upgrade.

There is a related risk. grantInternal [truncated]

Evidence:

Upgrade grant:
- InformationRequestParticipantAccountUpgradeService.kt:148-156 calls shareService.grantRoleKeyWithPrincipalProvenance with PrincipalKind.USER, appUser.id and roleName = party.roleKey.name. The result is returned but never stored on the party.
- The same file does not change party.shareId. Its test at InformationRequestParticipantAccountUpgradeServiceTest.kt:110-118 asserts the original Share is never revoked.

Party revoke and reassign:
- InformationRequestPartyService.kt:588-609 (revokeParty, used by revokeMutation at 552-577 and revokeForOwnershipChange at 579-586) revokes only party.shareId, at 597-604.
- reassignMutation at 500-507 does the same before granting the new principal at 508-522.

Share revocation:
- ShareService.kt:506-518 (revoke) cascades only through findBySourceShareId.
- grantInternal sets this.sourceShareId = null at ShareService.kt:154 and upserts one DIRECT Share per principal and resource at lines 136-160.

Who reads the link:
- [truncated]

Fix outline:

1. Migration V151__information_request_party_linked_user_share.sql: add a nullable linked_user_share_id uuid column to information_request_party, with a foreign key to share(id), and an index on (information_request_id, linked_user_share_id). This is forward-only with no backfill, per the Development-Stage Constraint. Add the field to the InformationRequestParty entity.
2. InformationRequestParticipantAccountUpgradeService.upgradeMutation:
   - Set party.linkedUserShareId = grantedShare.id, bump party.partyRevision and updatedAt, and save the party.
   - Before granting, refuse the upgrade with a new catalog error when the App User already holds an active DIRECT Share on the request that another active party, or the owner's retained read, depends on. This stops the grantInternal upsert from silently changing the role of a Share someone else needs.
   - replayResult can then resolve the Share by the stored id.
3. InformationRequestPartyService:
   - In revokeParty and reassignMutation, after revoking party.shareId, also call shareService.revoke(linkedUserShareId, revokedBy, RESOURCE_LABEL) and clear the field.
   - Skip that revoke when another active party on the same request references the same Share id, as shareId or linkedUserShareId.
   - Revoking the linked user's Share inside the same transaction also covers revokeForOwnershipChange.
4. Tests:
   - InformationRequestPartyServiceTest: revoking an upgraded Participant party revokes both Shares; reassigning it revokes both and grants only the new principal; a Share still referenced by another active party is kept.
   - [truncated]

### GA-005: Request party assignment bypasses the organization B2B/B2C sharing policy

- Severity: high. Verification: confirmed. Fix size: M. Audit key: `G085-P3-T8`.
- Plan reference: P3-T8. Plan basis: What puts the requirement in force:
- Plan lines 1536-1538 (P3-T8): reuse TrustedRecipientValidationService and the related trust services rather than bypassing the existing B2B eligibility path.
- Lines 1759-1761 (Phase 3 exit criteria): organization-owned trusted request parties remain governed by the existing trust-policy services.
- Architectural Decision 34 at lines 646-654: existing trust policy stays authoritative, and request code cannot silently materialize or retain access that [truncated]

Current state:

The only HTTP route that assigns a party is POST /information-requests/{id}/parties, and it accepts a userId, principalGroupId or email. It hands off to InformationRequestPartyService.assign or assignExternalParticipant. Reassignment (POST .../parties/{partyId}/reassignment) takes the same kind of input. None of these paths applies the organization's outbound sharing policy, which Exchange sharing enforces through OrganizationExchangePolicyService.assertCanShareWithUser and assertCanShareWithGroup. The only check on the principal is requireSupportedActingPrincipal, which just confirms the kind is USER, PARTICIPANT or PRINCIPAL_GROUP. After that the service grants an INFORMATION_REQUEST Share straight away, and for a group ShareService materializes Shares for every member.

That means an organization-owned request can be given:
- a user who belongs only to an untrusted organization, even when requireTrustedOrganizationForB2b is true;
- an email-only external participant, even when allowExternalCustomerSharing is false (the External Participant is created and then granted);
- any PrincipalGroup UUID: another organization's group that was never published, an inactive group, another user's personal group, a SHARED_PROJECT group, or an id that does not exist. information_request_party.principal_id has no foreign key, so nothing catches a missing group.

The group checks (active, personal-group ownership, published, SHARED_PROJECT refusal) are also skipped for personally owned requests.

The request authorization context provider performs no trust check, so the Share that gets [truncated]

Evidence:

- InformationRequestPartyService.kt:255-354 (assignMutation): line 289 is requireSupportedActingPrincipal, then shareService.grantRoleKeyWithPrincipalProvenance at 302. The exchangeRecipientId check at 295 is optional.
- InformationRequestPartyService.kt:356-428 (assignExternalParticipantMutation): externalParticipantService.findOrCreate at 369, then the grant at 393, with no B2C gate in between.
- InformationRequestPartyService.kt:479-548 (reassignMutation): requireSupportedActingPrincipal at 484, grant at 508.
- InformationRequestPartyService.kt:877-882: requireSupportedActingPrincipal only checks membership of supportedActingPrincipalKinds.
- InformationRequestPartyService.kt:430-477 and 816-857: the trusted-selection path exists, but grep finds callers only in src/test/.../InformationRequestPartyServiceTest.kt:369, 470 and 553.
- InformationRequestPartyResource.kt:47-107: the assign route builds the principal from userId or principalGroupId, or calls assignExternalParticipant for [truncated]

Fix outline:

Service changes:
1. Add a party-eligibility collaborator in service/informationrequest, for example InformationRequestPartyEligibilityService. It should reuse OrganizationExchangePolicyService plus OrganizationGroupService and AppUserService through their service methods, not their repositories. It must not duplicate policy logic.
2. It exposes requireEligible(request, actor, principal):
   - USER: call assertCanShareWithUser(request.ownerOrganizationId, actor.id, userId), passing null when the user is temporary. First confirm the user exists.
   - PARTICIPANT: call assertCanShareWithUser(ownerOrgId, actor.id, null), which applies the allowExternalCustomerSharing gate.
   - PRINCIPAL_GROUP: load the group, refusing a missing id, then call assertCanShareWithGroup(ownerOrgId, actor.id, group).
   - Personally owned requests pass null as the organization. The user checks then allow the assignment, but the group checks still apply.
3. Call it in InformationRequestPartyService:
   - assignMutation, after requireSupportedActingPrincipal;
   - assignExternalParticipantMutation, before externalParticipantService.findOrCreate so no participant row is created when the assignment is refused;
   - reassignMutation;
   - createActingParty, which covers the Blueprint-default and successor paths.
4. For a cross-organization USER or group on an organization-owned request, either require exchangeRecipientId so the existing attestation revalidation and group reconciliation in requireAssignablePartyRecipient runs, or expose assignTrustedRecipientSelection through the party resource. The [truncated]

### GA-006: Respondent evidence UI cannot capture evidence attributes, so attribute-based policies are unsatisfiable through the product

- Severity: high. Verification: confirmed. Fix size: M. Audit key: `G114-P6-T11`.
- Plan reference: P6-T11. Plan basis: Plan lines that put the requirement in force:
- P2-T5 (1196-1198): versioned evidence policy for issuer, coverage, dates, freshness, jurisdiction, language, certification and signature.
- P6-T2d (2547-2559): the Evidence Version owns the captured issuer, jurisdiction, language, dates, coverage, certification and signature.
- P6-T5 (2634-2644): validates required captured attributes.
- P6-T10 (2744-2756): the staged fixture "needs two continuous, attributed files".
- P6-T11 (2757-2772): the [truncated]

Current state:

The backend records the attributes a file states (issuer, jurisdiction, language, issue date, expiry date, coverage start and end, certification reference, signature reference) only from multipart form fields on upload and replace. The frontend service can send them through `options.attributes`, but nothing in the product ever passes them. `requirementEvidenceCommands.ts` calls upload and replace without attributes. `useRequirementEvidence` and `KeptUpload` carry only a File. `RequirementEvidencePanel` and `EvidenceArtifactRow` offer nothing but a file picker for upload and Replace. There is no other writer either: no reviewer or PATCH path sets attributes, and OCR or automated extraction is an explicit non-goal. The respondent UI also never shows the attributes stored on a version, even though the version DTO carries them.

Meanwhile, the Template authoring UI lets an author mark each attribute OPTIONAL or REQUIRED (`RequirementEvidenceAttributes.tsx`). It also lets them set accepted issuer, jurisdiction and language values, and coverage length, continuity, issue-age and validity rules (`RequirementEvidenceFields.tsx`).

What the respondent then sees depends on the rule:
- **A REQUIRED attribute:** every uploaded file gets ATTRIBUTE_MISSING. The respondent sees "A required detail is missing" with no way to supply it.
- **A coverage rule:** coverage is computed only from stated periods, so the result is COVERAGE_TOO_SHORT.
- **Under the default CONFORMANCE_REQUIRED policy:** the Requirement stays DEFICIENT. That does not complete work (`completesWork=false`), so a REQUIRED [truncated]

Evidence:

**Backend accepts attributes only as form fields:**
- src/main/kotlin/com/docuhyphen/app/api/resource/informationrequest/InformationRequestEvidenceResource.kt:88-97 (upload) and :131-140 (replace) take issuer, jurisdiction, language, issuedOn, expiresOn, coverageStartsOn, coverageEndsOn, certificationReference and signatureReference as @RestForm fields.
- InformationRequestEvidenceHttp.kt:71-90 parses them.
- InformationRequestNoAuthEvidenceResource.kt:122 and :171 do the same for the access-link surface.
- A grep for InformationRequestEvidenceAttributeForm / InformationRequestEvidenceAttributes( finds no other writer.

**Frontend never sends them:**
- web-app/src/services/informationRequestEvidenceService.ts:16-27 defines InformationRequestEvidenceAttributesInput; :46-56 appends attributes only when given.
- web-app/src/app/information-requests/requirement-evidence/requirementEvidenceCommands.ts:64-77: upload and replace pass only expectedETag, idempotencyKey, accessLinkToken and [truncated]

Fix outline:

Frontend only. No migration and no backend change, since the endpoints already accept the fields and V151 stays free.

1. **Pass the policy to the panel.** In StructuredResponseRequirement.tsx, pass `requirement.evidencePolicy` to `RequirementEvidencePanel`.
2. **Add an attributes dialog.** Create web-app/src/app/information-requests/requirement-evidence/evidence-attributes-dialog/EvidenceAttributesDialog.tsx with a co-located EvidenceAttributesDialogStyles.tsx:
   - Show an input only for attributes the policy marks OPTIONAL or REQUIRED: text for issuer, jurisdiction, language, certification and signature references; date pickers for issuedOn, expiresOn, coverageStartsOn and coverageEndsOn.
   - Mark REQUIRED inputs.
   - Require both coverage dates or neither, matching the backend rule.
   - Offer accepted-value choices when the policy lists accepted values.
   - Footer: primary Upload (or Replace) on the left, secondary Cancel. Circular buttons, ids on every element, tokens only.
3. **Show the dialog after a file is picked**, for both upload and Replace. Skip it when the policy captures no attributes. For Replace, prefill from the latest version's stated attributes so a respondent can correct them with the same bytes.
4. **Thread attributes through the commands.** Add `attributes: InformationRequestEvidenceAttributesInput` to `RequirementEvidenceCommands.upload` and `.replace` and forward it as `options.attributes`. Extend `KeptUpload` with the attributes so a retry resends them. Change `upload(file, attributes)` and `replace(artifact, file, attributes)` in [truncated]

### GA-007: Respondent UI cannot resubmit or re-attest a scope under an open correction

- Severity: high. Verification: confirmed. Fix size: S. Audit key: `G161-P8-T11`.
- Plan reference: P8-T11 / P8-T3. Plan basis: - Plan lines 3066-3075 (Phase 8 decision 9): while a correction is open, Attestation Requirements of the corrected scope accept new assent, and resubmitting the scope creates a package naming the corrected one as previous.
- Line 3016 (decision 1): the request state is unchanged during review and correction, so `acceptsSubmission` stays true and the only blocker is `scopeOpen`.
- Lines 3121-3124 (P8-T3) and 3159-3164 (P8-T11): the minimal correction and remediation UI, "respondent Review [truncated]

Current state:

The backend command path supports correction resubmission. The submission preview that drives the respondent UI does not. `InformationRequestSubmissionQueryService.preview` treats a scope as open only when no active package exists for it. While a correction is OPEN, the corrected package is still active: `activePackages` drops only withdrawn packages and packages that a later one follows. So for the corrected scope `scopeOpen` is false, `canSubmit` is false, and every attestation standing gets `callerCanAttest = false`.

The preview never calls `lockService.openCorrectionForStage` or `openCorrections`. The default scope choice also ignores corrections. On a whole-package request the scope is null and `null` is in `submittedStages`, so the scope is always closed. On a staged request the corrected stage shows as "(submitted)" and stays closed even when selected.

In the frontend, `InformationRequestSubmissionPanel` has the only submit button, and it is disabled when `!preview.canSubmit`. `SubmissionAttestationCard` hides the Assent and Refuse controls when `callerCanAttest` is false.

The backend expects new assent because the content hash changes (decision 9). Readiness then usually reports the attestation as missing, and the respondent has no UI way to supply it.

Withdraw-and-resubmit is not a workaround. A correction only exists after a review has been assigned and settled, and `requireWithdrawable` refuses withdrawal once any assignment exists or the review is not PENDING.

The result: a respondent can see the items to fix under Review results (`RespondentReviewCard`, [truncated]

Evidence:

- `src/main/kotlin/com/docuhyphen/app/api/service/informationrequest/InformationRequestSubmissionQueryService.kt:56-63`: `activePackages`, then `submittedStages`, then `scopeOpen = scope !in submittedStages && (scope == null || null !in submittedStages)`. No correction lookup anywhere in the file.
- Same file, line 76: attestable is `scopeOpen && accepting`. Lines 104-106: `callerCanAttest = attestable && ...`. Lines 80-81: `canSubmit = ready && scopeOpen && orderMet && accepting && gate...`.
- Same file, lines 58-60: the default scope picks the first unsubmitted stage, else the first stage. Corrections are ignored.
- `src/main/kotlin/com/docuhyphen/app/api/service/informationrequest/InformationRequestSubmissionLockService.kt:32-38`: `activePackages` keeps a corrected package until a follower names it as previous. Lines 53-57: `openCorrectionForStage` exists but only the submit service uses it.
- [truncated]

Fix outline:

No migration is needed.

Backend:
1. In `InformationRequestSubmissionQueryService.preview`, compute the open correction for the scope. Set `scopeOpen = corrected != null || (scope !in submittedStages && (scope == null || null !in submittedStages))` so that both `canSubmit` and `callerCanAttest` open for a corrected scope.
2. Change the default scope choice to prefer a scope with an open correction. Map `lockService.openCorrections(request.id)` packageIds to their active package stageKeys, and pick that stage before falling back to the first unsubmitted stage.
3. Expose the correction on the preview DTO:
   - add `correctionOpen: Boolean` (or `openCorrectionId`) to `InformationRequestSubmissionPreview`;
   - add a per-stage `correctionOpen` flag to `InformationRequestSubmissionStageStanding`, next to the existing mapper and serialization.
4. `orderMet` needs no change.

Frontend:
1. Add the new fields to `web-app/src/app/models/models.tsx`.
2. In `InformationRequestSubmissionPanel`, label a corrected stage option "(returned for correction)" instead of "(submitted)", and show a short note or "Resubmit" label on the action when `preview.correctionOpen`.
3. Keep the component under about 150 lines, splitting out a small child if needed. IDs and circular shape per AGENTS.md.

Tests:
1. Backend transaction test in `InformationRequestSubmissionTransactionTest` or the review transaction tests: submit, then review with CHANGES_REQUESTED, then preview. Assert:
   - `canSubmit` is true once the corrected content is ready;
   - an attestor gets `callerCanAttest = true` on the [truncated]

### GA-008: No UI path for a request manager to open a pending review and assign the first reviewer

- Severity: high. Verification: confirmed. Fix size: S. Audit key: `G162-P8-T11`.
- Plan reference: P8-T11 / P8-T1. Plan basis: - Phase 8 design decision 4 (plan lines 3028-3036): a review is PENDING until a reviewer is assigned, and the assignment names an active REVIEWER party and a stage.
- P8-T1 (3108-3117): reviewer assignment and queues.
- P8-T11 (3159-3164): minimal reviewer review UI.
- Phase 10 design decision 9 (3569-3573) keeps the requirement in force: the reviewer workspace includes "Assignment management (assign a Reviewer party to a stage, delegate, recuse, revoke)".
- P10-T5 (3653-3654) and P10-T7 [truncated]

Current state:

When a review-required package is submitted, the backend opens a review in PENDING with no assignment (InformationRequestReviewOpeningService.onSubmitted). The UI can reach a review workspace in only two ways. One is the reviewer queue page (ReviewQueueList). The other is the Exchange tab's REVIEW next action (useInformationRequestWorkspaceOpener). Both are fed only by GET /information-request-reviews, and InformationRequestReviewQueryService.queue returns only the caller's own ACTIVE, undecided assignments, directly or through a group. A review with no assignment is therefore in no one's queue. The REVIEW next action is derived from that same queue (InformationRequestExchangeSummaryService builds reviewAwaited from queue(); CallerStandingService line 57), so a manager's next action is MANAGE and the Exchange tab sends them to /manage. The management workspace (InformationRequestAuthorWorkspace) has parties, clocks, follow-ups and outcomes panels, but no reviews section and no link to any review. The backend list endpoint GET /information-requests/{id}/reviews exists and lets managers read (requireReviewReader accepts permitsManage), but no frontend code calls it. The service module has no list function. The operations detail only links to /manage. The help article says a manager selects "Assign reviewer" but never says how to open a pending review. The "Assign reviewer" control in ReviewAssignmentsPanel, shown when review.canManage is true, can only be reached by typing /information-requests/{requestId}/reviews/{reviewId} by hand. So the first reviewer assignment for a [truncated]

Evidence:

- src/main/kotlin/com/docuhyphen/app/api/service/informationrequest/InformationRequestReviewOpeningService.kt:57-77: the review is saved with state = PENDING and no assignment is created.
- src/main/kotlin/com/docuhyphen/app/api/service/informationrequest/InformationRequestReviewQueryService.kt:109-136 (queue): only uses assignmentRepository.findActiveForPrincipal, filtered to ACTIVE and decidedAt == null.
- InformationRequestReviewQueryService.kt:53-58 (reviews) and 182-188 (requireReviewReader): managers may list.
- src/main/kotlin/com/docuhyphen/app/api/service/informationrequest/InformationRequestExchangeSummaryService.kt:41-42: reviewAwaited = queue(access) request ids.
- src/main/kotlin/com/docuhyphen/app/api/service/informationrequest/InformationRequestCallerStandingService.kt:57-59: REVIEW only when reviewAwaited, otherwise MANAGE.
- src/main/kotlin/com/docuhyphen/app/api/resource/informationrequest/InformationRequestReviewResource.kt:49-60 (GET list) and [truncated]

Fix outline:

No migration and no backend change are needed, because GET /information-requests/{id}/reviews already returns InformationRequestReviewDto[] and managers can read it.

1. web-app/src/services/informationRequestReviewService.ts: add listInformationRequestReviews(requestId), a read() over apiClient.get(`/information-requests/${requestId}/reviews`), typed as InformationRequestReviewDto[].
2. Add a new folder web-app/src/app/information-requests/authoring/request-reviews-panel/ with RequestReviewsPanel.tsx, RequestReviewsPanelStyles.tsx and a useRequestReviews hook:
   - Load the list and render one row per review: review number, kind, package number and stage, state label from reviewLabels, and assignment count.
   - For a PENDING review with no assignments, show a clear "Awaiting reviewer assignment" status.
   - Add a circular "Open review" button with a stable id that navigates to informationRequestReviewPath(requestId, review.review.id).
   - Render nothing when the list is empty or refused (403).
   - Follow the house rules: one attribute per line, tokens only, responsive layout.
3. InformationRequestAuthorWorkspace.tsx: render RequestReviewsPanel when request.state !== DRAFT, next to FollowUpPanel and RequestOutcomesPanel.
4. Optional: add a "Reviews" link from InformationRequestOperationsDetail, or keep "Manage this request" as the single entry.
5. Tests:
   - RequestReviewsPanel.test.tsx: a pending unassigned review is listed with its awaiting-assignment status; "Open review" navigates to the review path; a refused or empty list renders nothing.
   - [truncated]

### GA-009: Group occurrence receipt replay returns every occurrence without scope or lifecycle checks

- Severity: high. Verification: confirmed. Fix size: M. Audit key: `OCC-REPLAY`.
- Plan reference: P5-R13, P5-R30, P5-T2c. Plan basis: Requirements in force:
- 2126-2133: P5-T2c. Command Receipt idempotency, central Requirement response authorization and issued-request continuation gates, shared authenticated and no-auth service.
- 2299-2302: P5-R13. Reauthorize receipt replay and any returned representation against current assignment, lifecycle and grant state; payload and ETag must describe the same authorized result.
- 2379-2382: P5-R30. Occurrence metadata restricted by authorized runtime occurrence scope, because a caller [truncated]

Current state:

InformationRequestGroupOccurrenceService (shared by the authenticated and no-auth group-occurrence endpoints) handles Command Receipt replays in replayGroupOccurrenceResult. The only check there is InformationRequestReadAuthorization.requireView, which is INFORMATION_REQUEST_VIEW on the request. It does not lock the parent Exchange or the request. It does not re-run the SAVE_RESPONSE lifecycle check (requireResponseMutationAllowed), the continuation check (requireContinuationEntitlement: operational suspension, revoked grant, owner entitlement), the INFORMATION_REQUEST_REQUIREMENT_RESPOND authorization, or any group or occurrence scope authorization. It then returns groupOccurrenceRepository.findForRequest(request.id), which is every active occurrence in the request across all groups, branches and parties, in its current state, paired with the ETag stored in the receipt.

What this means in practice:
1. The same principal can resend an old Idempotency-Key after the parent is terminated, the grant is revoked, the Exchange is operationally suspended, or respond access is lost while view access remains. The request still succeeds.
2. The response carries occurrence metadata (ids, group ids, parent ids, paths such as group[1], indexes, createdAt) for occurrences the caller cannot see under the InformationRequestWorkspaceOccurrenceProjection rule used on reads. It also carries occurrences created after the receipt.
3. The payload and the ETag no longer describe the same result. The payload is also a different shape from the original response: the whole request instead of one [truncated]

Evidence:

- src/main/kotlin/com/docuhyphen/app/api/service/informationrequest/InformationRequestGroupOccurrenceService.kt
  - 307-308: Recorded returns the original response; Replayed goes to replayGroupOccurrenceResult(decision.result, actor).
  - 714-732: the replay path. Uses requestRepository.findById with no lock, then only InformationRequestReadAuthorization.requireView (724). Returns responseETag = result.etag (the receipt ETag) with occurrences = groupOccurrenceRepository.findForRequest(request.id) (730).
  - 316-330 (mutate): the checks that exist only on the first execution. lockParentExchangeOf, findRequestByIdForUpdate, precondition, authorize (INFORMATION_REQUEST_REQUIREMENT_RESPOND, 384-396), requireResponseMutationAllowed (327), requireContinuationEntitlement (328, which checks suspension and a revoked grant).
  - Add, 177-181 and 195: authorizeMaterializedBindings covers only the authored bindings, then siblings = activeSiblings + occurrence is returned.
  - Remove, 230-234 and [truncated]

Fix outline:

Backend, InformationRequestGroupOccurrenceService.kt:
1. Rewrite replayGroupOccurrenceResult so it takes the command context, not just the actor.
   - Lock state first: lockParentExchangeOf(result.resourceId, ...) and requestRepository.findRequestByIdForUpdate.
   - Re-run the full gate before building any payload: requireResponseMutationAllowed(exchange, request), requireContinuationEntitlement(exchange, request), authorize(access, request.id) (RESPOND), plus requireView.
   - For add and reorder, pass groupKey and parentOccurrenceId through runOnce. For remove, pass occurrenceId and resolve its group and parent, including a removed row. Rebuild the same sibling-set shape as the original response, not findForRequest.
   - Filter that set through a new scope-visibility helper (item 2).
   - Occurrence order has no revision history, so it cannot be rebuilt as of the receipt. Return InformationRequestETag.responsesOf(request) (the current ETag) with the current authorized projection so payload and ETag match. The frontend already refreshes on the ETag, so no UI change is needed.
2. Add a helper to InformationRequestGroupAuthorizationService, e.g. visibleOccurrences(access, requestId, occurrences).
   - Keep an occurrence only when an active Requirement at or under its path passes INFORMATION_REQUEST_REQUIREMENT_VIEW or RESPOND for the caller.
   - Reuse the path containment rule of InformationRequestWorkspaceOccurrenceProjection.
3. Apply the helper in addOccurrence (line 195) and removeOccurrence (line 241) so unauthorized siblings are dropped. The newly created occurrence [truncated]

### GA-010: requireRecipientSignIn is enforced only at first link issuance

- Severity: high. Verification: confirmed. Fix size: M. Audit key: `SIGNIN`.
- Plan reference: Baseline: No-auth access, P4-T4. Plan basis: Plan lines that put the requirement in force:
- 371: treat requireRecipientSignIn as authoritative owner policy that blocks bootstrap-link issuance; never silently override it.
- 540-543: decision 24, "remains the owner's authoritative choice about unauthenticated recipient access"; issuance refused and respondents use the authenticated surface.
- 1838-1840: P4-T4 refuse bootstrap issuance "so the owner's existing no-auth choice is never silently overridden".
- 2013-2015: required [truncated]

Current state:

In all Information Request code, Exchange.requireRecipientSignIn is read in exactly one place: InformationRequestBootstrapShareLinkService.issueMutation. That is the first "Create link". Everything else ignores it:
- rotateMutation mints a new raw secret on the existing link, resets its use count and clears its challenge state. This is what the UI's "Resend link" button calls.
- replaceMutation revokes the old link and saves a brand-new VERIFICATION_BOOTSTRAP ShareLink.
- InformationRequestContactProofService (issueChallenge, verifyChallenge and resolveBootstrapLink), RequestAccessSessionService (issue, authenticate and requireActive) and InformationRequestNoAuthReadAccessService.resolve check link status, expiry, uses and party activity. They also check parent Exchange status and deletion, through the parent snapshot, which has no sign-in field.

Turning the flag on (ExchangeUpdateService, or ExchangeAccessManagementService when a trusted primary recipient is replaced) clears only the legacy Exchange no-auth verification. It does not revoke Information Request bootstrap links or RequestAccessSessions. InformationRequestParentLifecycleService revokes sessions only when the parent status changes.

The result: after an owner requires sign-in, three things still work:
1. Links issued earlier still send codes, verify and create sessions.
2. Sessions created earlier still read and write.
3. "Resend link" (rotate) or replace still hands out a fresh working no-auth link.

The legacy Exchange no-auth path, by contrast, refuses at the moment of use once the flag is set.

The only [truncated]

Evidence:

Backend services (under service/informationrequest/ unless noted):
- InformationRequestBootstrapShareLinkService.kt:136-143 is the only requireRecipientSignIn check, inside issueMutation. rotateMutation (209-232) authorizes, runs requireActive and then mints a new token with no flag check. replaceMutation (268-297) saves a new VERIFICATION_BOOTSTRAP link with no flag check.
- A grep for requireRecipientSignIn and RECIPIENT_SIGN_IN_REQUIRED across src/main/kotlin finds, in Information Request code, only that line 136 and InformationRequestErrorCatalog.kt:97,358. Nothing in service/auth.
- InformationRequestContactProofService.kt:40-120 (issueChallenge and verifyChallenge) and 122-178 (resolveBootstrapLink) check only mode, revoked, expired, use limit and active party.
- RequestAccessSessionService.kt:26-60: issue checks the parent only through lockParentForShare plus InformationRequestTransitionMatrix.canRead. authenticate (62) and requireActive (85-105) check only revoked and [truncated]

Fix outline:

No migration is needed; V151 stays free.

1. Issuance paths
- In InformationRequestBootstrapShareLinkService, extract a private requireNoAuthPermitted(requestId) helper. It loads the request, then the Exchange, and throws RECIPIENT_SIGN_IN_REQUIRED.
- Call it from issueMutation, rotateMutation and replaceMutation.
- In replaceMutation, call it before the old link is revoked.

2. Refuse at the moment of use
- Add recipientSignInRequired to InformationRequestParentSnapshot, filled from parent.requireRecipientSignIn in RequestAccessSessionRepository.lockParentForShare.
- In RequestAccessSessionService.issue and requireActive (which authenticate uses), refuse with RECIPIENT_SIGN_IN_REQUIRED when it is set.
- In InformationRequestContactProofService.resolveBootstrapLink, refuse the same way before issueChallenge, verifyChallenge or no-auth read resolution. Go through an Information Request service method that takes a Share ID and uses that snapshot, not another domain's repository.

3. Revoke when the flag is turned on
- Add InformationRequestBootstrapShareLinkService.revokeAllForExchange(exchangeId), backed by a new ShareLinkRepository.findActiveBootstrapLinksForExchange query that joins Share (INFORMATION_REQUEST) to request to Exchange. It revokes each link and runs revokeAllForShareLink on it.
- Call it from ExchangeUpdateService at 413-417 when requireRecipientSignIn turns true, and from ExchangeAccessManagementService:336-340.

4. Frontend
- Expose a server-derived flag on the author workspace or party DTO, e.g. accessLinksPermitted = !exchange.requireRecipientSignIn.
- [truncated]

### GA-011: Confidentiality compartments are only an MFA-freshness gate, never per-party clearance; no-auth respondents can never answer them and there is no step-up flow

- Severity: high. Verification: confirmed. Fix size: L. Audit key: `critic#1`.
- Plan reference: Completeness critic. Plan basis: - Decision 15 (plan:481-482): confidentiality compartment is a separate policy enforced on reads and writes.
- P2-T4 (plan:1190-1195): compartment stored on the binding.
- P3-T8 (plan:1545-1548): the subordinate provider resolves the compartment.
- P4-T3 (plan:1802-1810): compartment is fed into Requirement authorization; that part is marked implemented.
- Test requirement "Response-mode and confidentiality-compartment read/write tests" (plan:2021).
- P9 notices apply compartment authorization [truncated]

Current state:

Today a confidentiality compartment works as a single yes/no switch. If a Requirement binding has any non-null confidentialityCompartmentKey, the Requirement policy evaluator denies every action on that Requirement unless the caller's AuthorizationContext has mfaSatisfied. The actions covered are VIEW, RESPOND, ATTEST, EVIDENCE_VIEW/UPLOAD/WITHDRAW and REVIEW, and the rule applies to every principal: owners, reviewers and assigned parties alike. The key's value is never read, so "a" and "b" behave the same, and no model grants a party access to a named compartment.

For no-auth respondents, the bootstrap access context is always built with mfaSatisfied=false. RequestAccessSession verification strength has only EMAIL_OTP. Neither the bootstrap path nor the respondent UI offers a way to step up. So a participant assigned to a compartment Requirement can never see, answer, attest or upload evidence for it. The Requirement stays INCOMPLETE and is counted as an undisclosed problem. Readiness.ready is then false, so the request cannot be submitted. The respondent UI makes this worse: it tells the respondent those items are "handled by other parties", which is false. The only indirect way out is the participant account upgrade followed by a separate MFA step-up. That is not a compartment step-up on the bootstrap path.

For authenticated users there is also no prompt. Compartment items are silently dropped from list projections by gate.permitsRequirement filtering. Direct Requirement calls throw a generic ForbiddenException, not a 401 STEP_UP_REQUIRED. So the global step-up modal [truncated]

Evidence:

- src/main/kotlin/com/docuhyphen/app/api/service/informationrequest/InformationRequestRequirementPolicyEvaluator.kt:40-46 has `if (facts.confidentialityCompartmentKey != null && !request.authorizationContext.mfaSatisfied) return deny(CONFIDENTIALITY_DENIED, ...)`. It is not scoped to any action subset and the key value is never compared.
- src/main/kotlin/com/docuhyphen/app/api/service/auth/authz/DefaultAuthorizationService.kt:233-270 (decideWithResourcePolicy) runs the evaluator for every principal and action on the Requirement kind.
- src/main/kotlin/com/docuhyphen/app/api/service/informationrequest/InformationRequestAccessContextFactory.kt:48 builds the bootstrap context as `AuthorizationContext(sessionRef = session.id.toString())`, so mfaSatisfied defaults to false (AuthorizationContext.kt:31). Line 48 is the only bootstrap RequestAccessContext construction.
- src/main/kotlin/com/docuhyphen/app/api/model/entity/RequestAccessSession.kt:13-16: the enum [truncated]

Fix outline:

1. Settle the semantics (user decision). Either (a) a compartment means "sensitive: requires recent step-up", or (b) a named compartment requires a per-party clearance plus step-up. Under (b), add an information_request_party_compartment_clearance table (V151) managed by the owner, and have the evaluator compare facts.confidentialityCompartmentKey against the acting party's clearances.

2. Bootstrap step-up (either option):
   - V151 (or V152) migration: add stepped_up_at TIMESTAMP to request_access_session, and add a STEPPED_UP_EMAIL_OTP (or similar) value to RequestAccessSessionVerificationStrength.
   - Add POST no-auth/information-request-access-links/sessions/{sessionId}/step-up-challenges and .../step-ups to InformationRequestNoAuthAccessResource. Reuse the InformationRequestContactProofService OTP issue/verify, lockout and rate limits. Add both paths to the EndpointAuthorizationFilter allowlist explicitly.
   - Record the step-up via a RequestAccessSessionService method.
   - InformationRequestAccessContextFactory.fromBootstrapSession sets mfaSatisfied=true when stepped_up_at is within the configured freshness window.
   - Add audit events for step-up.

3. Surface the denial:
   - InformationRequestMutationGate.authorizeRequirement maps a CONFIDENTIALITY_DENIED decision caused by missing step-up to a 401 with reasonCode STEP_UP_REQUIRED on authenticated paths, so the apiClient step-up modal fires.
   - On bootstrap paths, return a distinct INFORMATION_REQUEST_STEP_UP_REQUIRED reason.
   - Workspace projections should return a locked placeholder for the caller's own [truncated]

Decision needed. Recommended default: (a) A compartment means "sensitive: requires a recent step-up", with the key as a label. No-auth respondents step up with a fresh contact-proof OTP inside a freshness window. Owners, reviewers and decision makers also need the step-up.

### GA-012: Ad hoc private Version publication skips the service readiness check

- Severity: medium. Verification: confirmed. Fix size: S. Audit key: `ADHOC-READINESS`.
- Plan reference: P2-T10a, P3-T9a. Plan basis: Claim 1: plan 1556-1559 (P3-T9: ad hoc creation "atomically materializes a validated ... Version" and "does not bypass Template validation") and 1564-1568 (P3-T9a). Phase 10 design decisions: 3512 lists "ad hoc creation answers 500 for a validation refusal" as a defect to fix; 3523-3526 say the server stays authoritative, a refused save or publication answers INFORMATION_REQUEST_TEMPLATE_INVALID with the part keys, and publication states the freeze-time storage rules first through [truncated]

Current state:

Claim 1 (partial). InformationRequestPrivateVersionPublisher.publish checks the Schema Version with schemaCompatibility.requireUsable and runs the configuration validator through configurationWriter.replaceConfiguration. It never calls InformationRequestTemplatePublicationReadiness.requireReady. InformationRequestTemplatePublicationService.publishTemplate is the only caller of requireReady. The configuration validator does not restate the three readiness rules, so publication of a private ad hoc or amendment Version is never checked in the service for them: a FIELD requirement with no schemaVersionId, a DOCUMENT requirement with no evidence policy, or a waiver rule that does not match the WAIVED disposition. The claim overstates one thing: no invalid Version is stored. request_template_version_guard (latest in V136/V142) calls request_template_publication_completeness when the status changes, and that function raises a plain exception for each of the three cases, so the transaction rolls back. The real defect is how the refusal comes back. That exception surfaces as a persistence or rollback exception, not InformationRequestTemplateValidationException. InformationRequestResource.handleException (and InformationRequestCommandHttp.refused on the amendment path) sends it to the else branch: a logged 500 "Request failed". It should be the 400 INFORMATION_REQUEST_TEMPLATE_INVALID refusal with section and requirement keys that Phase 10 decisions 1-2 require. The web-app create dialog mirrors these rules (Create stays disabled while editor.problems is non-empty), so only direct [truncated]

Evidence:

- src/main/kotlin/com/docuhyphen/app/api/service/informationrequest/InformationRequestPrivateVersionPublisher.kt:40-41: requireUsable, then replaceConfiguration. Lines 43-69: capability rows, then status flip to PUBLISHED. No requireReady call.
- InformationRequestTemplatePublicationService.kt:18-23: constructor has no schemaCompatibility. Lines 46-48: requireReady, the only call site (a grep of src finds no other).
- InformationRequestTemplatePublicationReadiness.kt:27-56: FIELD without schemaVersionId; DOCUMENT without evidencePolicy; the two waiver/WAIVED mismatch rules.
- InformationRequestTemplateConfigurationValidator.kt:51-84 (normalize) and 455-506 (validateAnswerability): none of the three readiness rules. The only waiver check covers RESPONDENT_DECLARED on non-answerable modes.
- src/main/resources/db/migration/V136__information_request_template_review_plan.sql:119-124: the guard calls completeness when the status changes. Lines 163-213: plain RAISE EXCEPTION for FIELD [truncated]

Fix outline:

No migration is needed; V151 stays free.
1) InformationRequestPrivateVersionPublisher: add an InformationRequestTemplateProjectionLoader constructor parameter. After configurationWriter.replaceConfiguration(draft, configuration) and the empty-capability check, and before any capability rows are saved or the status flips, call InformationRequestTemplatePublicationReadiness.requireReady(projectionLoader.loadVersion(draft)). Inject InformationRequestTemplateProjectionLoader into InformationRequestAdHocCreationService and InformationRequestAmendmentTargetResolver and pass it to the publisher. The refusal is then an InformationRequestTemplateValidationException, which both resources already map to 400 INFORMATION_REQUEST_TEMPLATE_INVALID with the section and requirement keys. The @Transactional caller rolls back the private Definition and draft, and no Command Receipt is recorded. Optionally, have InformationRequestTemplatePublicationService use the same shared helper so both publication paths run one freeze-time check.
2) InformationRequestTemplatePublicationService: inject InformationRequestTemplateSchemaCompatibility. In publishTemplate, call schemaCompatibility.requireUsable(definition, draft.schemaVersionId) before the readiness check, so a Version can never freeze naming a Schema Version that has since been retired, left the owner's scope, or is otherwise unusable. Keep createDraftVersion permissive (do not refuse there) so the author can open the new draft and choose another Schema Version, as P2-T10a intends.
3) Frontend: nothing is required. The server refusal already [truncated]

### GA-013: Request-owned ad hoc Template is not actually non-reusable

- Severity: medium. Verification: confirmed. Fix size: S. Audit key: `ADHOC-REUSE`.
- Plan reference: AD-2, P3-T9a. Plan basis: What puts the requirement in force:
- Plan lines 413-419 (Architectural Decision 2): an ad hoc request creates a "private one-off Template Definition".
- Lines 1556-1568 (P3-T9, P3-T9a): the Template is "request-owned, non-reusable".
- Lines 4355-4357 (V99 ledger): "one-private-Template-per-request".
- Lines 4610-4611 (Program Acceptance Criteria): the ad hoc Template Version is private.

Nothing in the plan supersedes it:
- Phase 10 decision 2 (lines 3528-3531) allows `templateVersionId` for [truncated]

Current state:

The only thing that keeps an ad hoc Template private is the `origin_kind` column (added in V99). Only the three reusable list queries read it. Every lookup by id ignores it, so the "non-reusable private one-off" Template Definition and its Version can be reused and edited through the normal reusable paths:

(1) POST /information-requests with `templateVersionId` set to another request's private Version creates a new request pinned to it. The only check is that the Version is published and that its owner matches the Exchange owner. The ad hoc Definition takes its owner (org or personal) from its Exchange, so any Exchange of the same owner qualifies. The Version id is exposed in InformationRequestDto, the operations DTO, the submission DTO and the response-workspace `templateVersion`.

(2) A Blueprint can be pointed at that private Version when it is created or updated (`selectTemplateVersion`), and it is accepted again every time the Blueprint is instantiated.

(3) The Template authoring endpoints by id work on the ad hoc Definition. That means GET /information-request-templates/{id}, PUT /{id}/draft/configuration, POST /{id}/versions, POST /{id}/draft/publication, POST /{id}/versions/{n}/retirement and POST /{id}/clones. The Definition id is exposed through the response-workspace `templateVersion.templateDefinitionId`. So an owner with Template edit rights can add draft Versions to another request's private Template, publish them or retire them, and those Versions can then be named as amendment targets.

Only two pieces of code check origin: [truncated]

Evidence:

- src/main/kotlin/com/docuhyphen/app/api/repository/informationrequest/InformationRequestTemplateDefinitionRepository.kt:51,63,83: `originKind = REUSABLE` appears only in the list queries.
- src/main/kotlin/com/docuhyphen/app/api/service/informationrequest/InformationRequestTemplateReferenceService.kt:37-52 (requireSelectableVersion), 63-78 (requireInstantiableVersion) and 84-108 (resolve): these check only version status and scope entitlement. There is no `originKind` check.
- src/main/kotlin/com/docuhyphen/app/api/service/informationrequest/InformationRequestTemplateInstantiationService.kt:99-100 and 144-168: `requireInstantiableVersion` plus an owner-scope match only.
- src/main/kotlin/com/docuhyphen/app/api/resource/informationrequest/InformationRequestResource.kt:121: the `templateVersionId` path calls `createFromTemplateVersion`.
- src/main/kotlin/com/docuhyphen/app/api/service/informationrequest/InformationRequestAdHocCreationService.kt:138-149: the ad hoc Definition gets [truncated]

Fix outline:

No migration is needed: V99 already stores `origin_kind` and `origin_request_id`. V151 would only be needed for an optional database guard, described in step 3.

1. InformationRequestTemplateReferenceService.resolve: after loading the definition, if `definition.originKind == AD_HOC_REQUEST`, throw `InformationRequestTemplateVersionUnavailableException(NOT_FOUND, ...)`. This fixes POST /information-requests `templateVersionId`, Blueprint create and update (`selectTemplateVersion`) and Blueprint instantiation (`resolveInformationRequestTemplateVersionForInstantiation` and `loadInformationRequestInstantiationSnapshot`) in one place.

2. InformationRequestTemplateAuthoringService.requireDefinition: treat an `AD_HOC_REQUEST` definition as not found. Throw the same `IllegalArgumentException("...not found")` so it maps to 404. This fixes getTemplate, replaceDraftConfiguration, requireMutationContext (publishTemplate, createDraftVersion, retireVersion) and cloneTemplate, which goes through getTemplate. Leave the private paths that read repositories directly unchanged:
   - InformationRequestAmendmentTargetResolver and InformationRequestPrivateVersionPublisher, which Phase 7 decision 7 needs.
   - InformationRequestAdHocCreationService.

3. Optional hardening (V151):
   - A trigger on the Blueprint's `information_request_template_version_id` column refusing a Version whose Definition has `origin_kind = 'AD_HOC_REQUEST'`.
   - A deferred constraint trigger on `information_request.template_version_id` that allows an AD_HOC_REQUEST Version only for its origin request or that request's [truncated]

### GA-014: Program-created Information Request tables carry App User-only attribution instead of the canonical principal pair

- Severity: medium. Verification: confirmed. Fix size: M. Audit key: `APPUSER-ATTRIBUTION`.
- Plan reference: AD-39, Baseline: Attribution foreign keys. Plan basis: Where the requirement comes from:
- Decision 39, plan lines 674-678: canonical pair only, every principal kind, replaced column dropped in the same migration.
- Lines 4527-4529: "Canonical (kind, id) principal provenance is the only attribution any row carries ... Do not add another one."
- Line 4190, Migration test row: "canonical-only principal attribution with the replaced shape proven refused".
- Exit criterion, lines 4642-4644: "Every row states its canonical storage locator, creator [truncated]

Current state:

Two migrations from this program created their tables with attribution that points only at App Users, and nothing after them fixes it. V86 gives the Template Definition a created_by_app_user_id column and gives the Template Version published_by_app_user_id, retired_by_app_user_id and created_by_app_user_id. V96 gives information_request a created_by_app_user_id column and information_request_party an assigned_by_app_user_id column. All five are nullable foreign keys to app_user, and none of these tables has a *_principal_kind/*_principal_id pair. No migration from V97 through V150 drops or pairs any of these columns. The later freeze triggers (V132, V136, V142) still name published_by_app_user_id and created_by_app_user_id.

The request and party writers only record a User. They store `principal.id.takeIf { kind == USER }`, so the row gets null whenever the actor is anything else. That happens when an APPLICATION principal creates a request or assigns or reassigns a party, which the authenticated endpoints allow because AuthorizationContextFactory.currentPrincipal returns PrincipalRef.application. It also happens when an ad hoc Template or Version is created or published by one of those actors. The one attribution fact that survives is the actor in the transition history.

The same pattern applies to the baseline column share_link.created_by_app_user_id (V1:737), which the program extended into bootstrap mode. Its only writer is InformationRequestBootstrapShareLinkService.

Downstream code reads these App User-only columns. The record export writes createdByAppUserId, so a [truncated]

Evidence:

Migrations:
- V86__information_request_template.sql:30 information_request_template_definition.created_by_app_user_id REFERENCES app_user.
- V86:81, 83, 84 information_request_template_version published_by_app_user_id, retired_by_app_user_id and created_by_app_user_id, all REFERENCES app_user.
- V86:213-215 freeze trigger names these columns. It is redefined in V132:179-181, V136:144-146 and V142:1095-1097.
- V96__information_request_runtime_persistence.sql:20 information_request.created_by_app_user_id.
- V96:146 information_request_party.assigned_by_app_user_id. V96:140-141 has principal_kind/principal_id for the assignee only, not the assigner.
- A grep of V97-V150 for these columns finds only the trigger redefinitions. No DROP and no *_by_principal pair.
- V1 baseline:737 share_link.created_by_app_user_id, with fk_share_link_creator at V1:1900.

Entities:
- InformationRequest.kt:59, InformationRequestParty.kt:54, InformationRequestTemplateDefinition.kt:61, [truncated]

Fix outline:

1. Migration V151__information_request_canonical_attribution.sql, a single forward-only file.
   (a) information_request_template_definition:
       - Add created_by_principal_kind VARCHAR(32) and created_by_principal_id uuid.
       - Backfill 'USER' from created_by_app_user_id.
       - For ad hoc definitions with a null creator, take the creator from the origin request's first transition actor (via origin_request_id). Otherwise remove the row, as the development-stage constraint allows.
       - Set NOT NULL, add a CHECK on the canonical kind set, and drop created_by_app_user_id.
   (b) information_request_template_version:
       - Add the created_by, published_by and retired_by pairs, backfilled the same way.
       - Extend ck_request_template_version_publication so PUBLISHED and RETIRED rows require the published_by pair, and RETIRED rows also require the retired_by pair. Enforce both-or-neither.
       - Drop the three App User columns.
       - Recreate the freeze trigger function from its latest V142 body, pointing it at the new columns.
   (c) information_request:
       - Add a created_by pair, NOT NULL.
       - Backfill as USER, otherwise from the first information_request_transition row's actor_kind/actor_id.
       - Drop created_by_app_user_id.
   (d) information_request_party:
       - Add an assigned_by pair, NOT NULL.
       - Backfill as USER, otherwise from the party transition history (V101), otherwise remove the row.
       - Drop assigned_by_app_user_id.
   (e) share_link:
       - Add a created_by pair.
       - Drop created_by_app_user_id and [truncated]

### GA-015: Runtime request audit events never carry the access-session ID or a safe actor label

- Severity: medium. Verification: confirmed. Fix size: M. Audit key: `AUDIT-LABEL`.
- Plan reference: AD-20, P3-T2. Plan basis: These plan lines put the requirement in force:
- 494-498 (Architectural Decision 20): every mutation supplies an explicit principal, actor kind, safe label, session, owner, correlation and redacted payload. No-auth capture never relies on the AuthTokenContext fallback. No-auth history uses the existing PUBLIC_LINK audit actor kind with a stable non-secret participant or party identifier.
- 1418-1421 (P3-T2): every later mutation passes an explicit PrincipalRef, the applicable access-session ID, [truncated]

Current state:

No Information Request audit writer passes an access-session ID or an explicit actor label to AuditRecorder. The central writer, InformationRequestTransitionHistoryService, builds its AuditEventDraft from a command that holds only a PrincipalRef (there is no session or label field). The eight other IR writers (EvidenceAccessAudit, ItemCorrection, Privacy, RecordExport, ClockPolicy, TemplateAuthoring, NoticeDispatcher, EvidenceScanAudit) do the same. AuditRecorder copies sessionId only from the draft and never falls back to AuthTokenContext.userSessionId, so every IR audit row has session_id = null on both surfaces. That holds even though RequestAccessContext.authorization.sessionRef holds the RequestAccessSession id for no-auth callers and the UserSession id for authenticated callers. Services already write that value to domain rows (recordedBySessionRef, submittedBySessionRef, createdBySessionRef, authorSessionRef, assentedBySessionRef, attestation sessionRef), but it never reaches the audit event. The label is left to AuditRecorder's AuthTokenContext fallback (resolveAuthenticatedActorLabel), which only knows how to label an authenticated App User. On the excluded no-auth paths the AuthToken is never set, so the fallback quietly returns null. As a result, no-auth participant events have no label. Authenticated events get a label only through that implicit fallback, not as explicit data, and TemplateAuthoring even hardcodes actorKind HUMAN. The actor kind comes from AuditActorKind.forPrincipal, which maps PrincipalKind.PARTICIPANT to a separate AuditActorKind.PARTICIPANT [truncated]

Evidence:

- src/main/kotlin/com/docuhyphen/app/api/service/informationrequest/InformationRequestTransitionHistoryService.kt:25-37: the command has actor: PrincipalRef and nothing for session or label. Lines 95-110 build an AuditEventDraft with no sessionId and no actorLabel. Line 101 sets actorKind = AuditActorKind.forPrincipal(command.actor.kind).
- src/main/kotlin/com/docuhyphen/app/api/service/audit/AuditEventDraft.kt:57 has actorLabel and :62 has sessionId, so the draft supports both.
- src/main/kotlin/com/docuhyphen/app/api/service/audit/AuditRecorder.kt:130 sets actorLabel = draft.actorLabel ?: resolveAuthenticatedActorLabel(). Lines 109-116 resolve only authTokenContext.authToken.appUser. Line 153 sets sessionId = draft.sessionId with no fallback. Line 127 has the same AuthTokenContext fallback for actorId.
- src/main/kotlin/com/docuhyphen/app/api/service/audit/catalog/AuditActorKind.kt:14 defines the PARTICIPANT value, and :40 maps PrincipalKind.PARTICIPANT -> PARTICIPANT.
- Grepping [truncated]

Fix outline:

1. Add a model class, model/informationrequest/InformationRequestAuditActor(principal: PrincipalRef, sessionRef: String?, label: String?), with a factory from RequestAccessContext (access.principal, access.authorization.sessionRef?.takeIf { it.isNotBlank() }) and a system() factory.
2. Replace InformationRequestTransitionHistoryCommand.actor: PrincipalRef with that type, or add sessionRef and actorLabel fields. Thread it through all 41 call sites. Owner and respondent services already hold a RequestAccessContext; the scheduler and clock paths pass system().
3. In InformationRequestTransitionHistoryService.recordOne, set AuditEventDraft.sessionId and actorLabel. Do the same in InformationRequestEvidenceAccessAudit, ItemCorrectionService, PrivacyService, RecordExportService, ClockPolicyService and TemplateAuthoringService; TemplateAuthoring should also derive actorKind from the principal instead of hardcoding HUMAN.
4. Add service/informationrequest/InformationRequestAuditActorLabelResolver. It should reuse PrincipalDisplayService for USER. For PARTICIPANT it should produce a stable non-secret label, for example the party's display label or 'Participant <externalParticipantId>', and never a token, OTP or session secret.
5. Map a no-auth actor to AuditActorKind.PUBLIC_LINK. The simplest way is forPrincipal(PARTICIPANT) -> PUBLIC_LINK, then removing the now-unused AuditActorKind.PARTICIPANT value and updating SchemaAssignmentAuditTrailTest:160 (the development-stage constraint forbids keeping the old value). actor_kind is a String column with no check constraint in [truncated]

Decision needed. Recommended default: Keep the PARTICIPANT audit actor kind, because it names the real canonical principal and Fields already relies on it. Record the access-session id as the safe label, and amend the Decision 20 text that says PUBLIC_LINK.

### GA-016: Leftover compatibility fallbacks and legacy-only state in the Fields foundation

- Severity: medium. Verification: confirmed. Fix size: M. Audit key: `COMPAT-LEFTOVERS`.
- Plan reference: DS-T3, DS-T4, Development-Stage Constraint (P1-T5/P1-T7a/P1-T7d/P1-T3). Plan basis: Plan lines that put the requirement in force:
- Development-Stage Constraint, lines 174-185: one shape, no dual-write, no legacy read fallback, no legacy column kept for readers, no state that only describes rows an older release wrote.
- Line 187-189: forward migrations only.
- Lines 190-193: shims already shipped are now defect work.
- DS-T4, lines 251-264: the sweep for retained legacy columns, fallbacks, dual-writes, tolerated older shapes and mixed-version tests. The "outside this program" [truncated]

Current state:

DS-T4 is checked, but several compatibility leftovers from this program are still in the code.

1. Fields foundation (confirmed):
- SchemaAssignmentService.valueSetForWrite silently recreates a missing root Value Set. Its KDoc says this is for "an assignment that predates the set". That branch cannot be reached today: storeAssignment is the only place a SchemaAssignment is built, and it always creates the root set. Unassign and every disposal function delete the set and the assignment together. No test covers an assigned resource with no root set (every rootSetExists=false fixture also has assigned=false).
- The Exchange ETag test and the frontend fallback (expectedETag ?? '*' in saveExchangeFieldValues) exist only for that same legacy root case. Nuance: the mapper's nullable valueSet can still be reached legitimately by an Information Request read of an occurrence that has not been provisioned, so only the root/Exchange side is legacy-only.
- FieldValueRepository.findByResource and findByAssignmentAndContract have no callers and are not Value Set-aware.
- The V77 table schema_field_binding_conflict is still in the schema. Only V77 and its contract test mention it. Its only purpose is to describe conflicts left by pre-V77 rows.

2. Event outbox (confirmed):
- workflow_event_outbox.organization_id is still dual-written. DomainEventOutboxPublisher sets organizationId beside ownerKind/ownerId.
- The V94 CHECK still ties organization_id = owner_id for ORGANIZATION rows. The V51 index on organization_id still exists. V139 dropped only the legacy trigger.
- Nothing reads [truncated]

Evidence:

Claim 1:
- SchemaAssignmentService.kt:590-606: valueSetForWrite. Root falls back to findRootForUpdate(...) ?: createRootValueSet(assignment). The KDoc at 593-594 says "an assignment that predates the set".
- SchemaAssignmentService.kt:202-213: storeAssignment always calls createRootValueSet. grep "SchemaAssignment()" finds only :202.
- SchemaAssignmentService.kt:250-251: unassign deletes the sets and the assignment together. So do the disposal functions: V148:670-686 deletes field_value_set, then schema_assignment (also V142:1318, V146:217, V147:377).
- No test combines assigned=true with rootSetExists=false; every rootSetExists=false fixture has assigned=false (FieldValueCanonicalProvenanceTest:88-125, FieldValueSetRevisionTest:116-131, FieldsPreconditionTest:210).
- SchemaAssignmentDtoMapper.kt:50: etag = valueSet?.let(FieldValueSetETag::of).
- ExchangeFieldsResourceETagTest.kt:85-93.
- web-app/src/services/fieldsService.ts:218, 285: expectedETag ?? ANY_VERSION.
- IR occurrence [truncated]

Fix outline:

Migration V151 (one forward-only file, per the Development-Stage Constraint):
- DROP TABLE schema_field_binding_conflict.
- workflow_event_outbox:
  - Drop ck_workflow_event_outbox_owner and idx_workflow_event_outbox_organization_id.
  - DROP COLUMN organization_id.
  - Add a new CHECK: (owner_kind='PLATFORM' AND owner_id IS NULL) OR (owner_kind IN ('ORGANIZATION','USER') AND owner_id IS NOT NULL).
- request_access_session:
  - DELETE rows WHERE credential_hash IS NULL OR expires_at IS NULL.
  - CREATE OR REPLACE guard_request_access_session_credential with the auto-revoke branch removed; keep only the identity-immutability check.
  - ALTER credential_hash and expires_at SET NOT NULL.
  - Replace ck_request_access_session_credential with credential_hash ~ '^[0-9a-f]{64}$'.
  - Rebuild ux_request_access_session_credential without its WHERE clause.
- Optional, only if the recorded audit follow-up is taken now: on audit_outbox, audit_ledger_event, audit_analytics_fact, audit_export and audit_retention_policy, drop organization_id, rewrite the owner CHECKs without it, and rebuild any organization_id indexes.

Code:
- SchemaAssignmentService.valueSetForWrite: Root becomes findRootForUpdate(id) ?: throw IllegalStateException("Assignment has no root value set"). Update the KDoc.
- fieldsService.ts saveExchangeFieldValues: make expectedETag a required string and drop the ANY_VERSION fallback. useFieldValuesSave should require a served ETag. Keep SchemaAssignmentDtoMapper's nullable valueSet only for unprovisioned occurrences, or make the Exchange root projection non-null.
- [truncated]

### GA-017: Completion gate change endpoint has no UI, and creation never sets the gate

- Severity: medium. Verification: confirmed. Fix size: M. Audit key: `COMPLETION-GATE-UI`.
- Plan reference: P9-Decision-6, P9-T4. Plan basis: The requirement is in force through:
- Architectural decision 32 (plan line 633): "Ending an Exchange must satisfy configured gates and explicitly cancel any remaining nongating requests"
- P3-T4 (1480-1481)
- Phase 9 decision 6 (3252-3263): gates_exchange_closure "becomes changeable by PUT /information-requests/{id}/completion-gate ... while the request is not terminal"
- P9-T4 (3369-3374): "Add configurable Exchange completion gates"
- Core scope (322-324): "Responsive author, respondent, [truncated]

Current state:

The backend for configurable completion gates works: PUT /information-requests/{id}/completion-gate needs If-Match and Idempotency-Key, is authorized by INFORMATION_REQUEST_CONFIGURE_COMPLETION (INFORMATION_REQUEST_ADMIN), records CHANGE_COMPLETION_GATE history, and works only while the request is not terminal. Exchange ending also works: it enforces COMPLETION_GATES_UNSATISFIED and REMAINING_REQUESTS_REQUIRE_CANCELLATION, and cancelRemainingInformationRequests is supported. The frontend cannot view or change the gate.
(1) No service function in web-app/src/services calls completion-gate, and no component calls one. The management workspace's RequestLifecycleActions offers only Issue, Supersede and Cancel request.
(2) The create hook (useCreateInformationRequest.requestFor) builds {exchangeId, templateVersionId | blueprintDefinitionId | displayName+configuration} and never sends gatesExchangeClosure. The backend default (true) therefore always applies. Follow-up drafts copy the gate from their source request (InformationRequestDraftFactory:51), so they gate too.
(3) gatesExchangeClosure is carried by InformationRequestDto and InformationRequestOperationsRowDto, but no non-test component renders it. The Exchange-tab InformationRequestSummaryDto does not carry it at all.
Result: every request created in the product gates its Exchange and cannot be switched. The frontend handling for REMAINING_REQUESTS_REQUIRE_CANCELLATION (the "Cancel requests and end" path in useExchangeEnding.ts / exchangeEndRefusal.ts) is only reachable after a raw API call. The Exchanges help section [truncated]

Evidence:

Backend exists:
- src/main/kotlin/com/docuhyphen/app/api/resource/informationrequest/InformationRequestCompletionGateResource.kt:23 (@Path("/information-requests/{id}/completion-gate"), PUT)
- service/informationrequest/InformationRequestCompletionGateService.kt:31-67 (lock, requireMutation CHANGE_COMPLETION_GATE, If-Match, authorize INFORMATION_REQUEST_CONFIGURE_COMPLETION, history)
- service/auth/authz/Action.kt:151 (CONFIGURE_COMPLETION requires INFORMATION_REQUEST_ADMIN)
- InformationRequestExchangeCompletionService.kt:43-47 and InformationRequestTransitionMatrix.kt:390-394 (gating refusal logic)
- test: src/test/.../InformationRequestExchangeCompletionTransactionTest.kt

Defaults are true:
- resource/model/InformationRequestRequests.kt:20 (CreateInformationRequestDraftRequest.gatesExchangeClosure = true)
- model/entity/InformationRequest.kt:47
- V96__information_request_runtime_persistence.sql:17 (NOT NULL DEFAULT TRUE)
- InformationRequestResource.kt:115/125/137 passes [truncated]

Fix outline:

No migration is needed; the backend is complete. Frontend only, plus help:

1. web-app/src/services/informationRequestAuthoringService.ts: add
   changeInformationRequestCompletionGate(requestId, gatesExchangeClosure, requestETag, idempotencyKey): Promise<InformationRequestDto>.
   It calls PUT `${requestPath(id)}/completion-gate` with body {gatesExchangeClosure} and commandHeaders(idempotencyKey, requestETag). Add ChangeInformationRequestCompletionGateRequest to models.tsx.

2. Management workspace: add a component in its own folder, e.g. authoring/completion-gate-control/CompletionGateControl.tsx with a co-located CompletionGateControlStyles.tsx. Use a Fluent Switch or Checkbox labelled along the lines of "Must finish before the Exchange can end", with an id, shown in InformationRequestAuthorWorkspace next to RequestLifecycleActions.
   - Enable it only while the request is DRAFT, ISSUED or IN_PROGRESS. Show it read-only once the request is terminal.
   - Wire it in useAuthorWorkspace.ts through runner.run(`gate:${requestETag}:${value}`, key => ...) so it uses the same If-Match / 412 refresh handling as cancel and supersede.
   - A 403 from INFORMATION_REQUEST_ADMIN is shown through informationRequestRefusalMessage.

3. Creation: add a checkbox to CreateInformationRequestDialog (default checked, matching the backend default). useCreateInformationRequest.requestFor includes gatesExchangeClosure for all three sources, and the value goes into the idempotency signature.

4. Visibility (optional but recommended): show a "Holds Exchange end" badge in OperationsQueueRow and the [truncated]

### GA-018: Delegated-authority grant and revoke skip the parent lock, transition matrix and entitlement

- Severity: medium. Verification: confirmed. Fix size: M. Audit key: `DELEG-SAFETY`.
- Plan reference: P3-T12, Phase 8 decision 4. Plan basis: Claim 1:
- Architectural Decision 32 (plan 630-641): "Every command rechecks or locks the parent state in its mutation transaction". ENDED is read-only, REJECTED/RESCINDED cancel requests, deletion makes requests read-only.
- P3-T4 (1476-1483): the parent-lock and recheck contract "that later commands must call".
- P3-T10 (1580): "Wire every available command through the Phase 3 transition matrix and parent lock/recheck".
- Phase 3 exit criteria (1752-1755).
- P5-R26 (2361-2367): "Standardize [truncated]

Current state:

Claim 1 (P3-T12, medium): InformationRequestDelegatedAuthorityService.grantMutation and revokeMutation lock only the request row with requestRepository.findRequestByIdForUpdate. The service does not inject InformationRequestMutationGate. It never locks the parent Exchange (lockParentExchangeOf / gate.lock), never calls InformationRequestTransitionMatrix.canMutate or gate.requireMutation, and never calls requireContinuationEntitlement. So over the REST endpoints POST /information-requests/{id}/delegated-authorities and POST .../{authorityId}/revocations, delegated authority can be granted or revoked on a CLOSED, CANCELLED, SUPERSEDED or EXPIRED request. It also works under an ENDED, REJECTED, RESCINDED or deleted parent, and when the owner's entitlement has lapsed or the execution grant is revoked. Each such call bumps request.partyRevision. Nothing is written to transition history or audit. The sibling InformationRequestPartyService assign, reassign and revoke commands all do lock parent-then-request, run the matrix (ASSIGN_PARTY, REASSIGN, REVOKE_PARTY, all allowSameFrom(nonTerminalStates())) and check entitlement. The resource also has no branch for InformationRequestLifecycleException, so once the guard is added a refusal would come back as a 500 unless the mapping is fixed. No test covers refusal for a terminal request or a terminal parent. Claim 2 (Phase 8 decision 4, low): InformationRequestReviewAssignmentService.change requires a non-blank reasonCode only for RECUSAL. DELEGATION and REVOCATION pass command.reasonCode?.trim()?.ifBlank { null }, so the reason can be [truncated]

Evidence:

Claim 1:
- InformationRequestDelegatedAuthorityService.kt:66-73: the constructor has no InformationRequestMutationGate, ExchangeRepository or entitlement dependency.
- :127 and :187: only requestRepository.findRequestByIdForUpdate. :129-146 and :189-195 check only the ETag, authorization, party and authority. No state, parent or entitlement check.
- :173-175 and :206-208: request.partyRevision += 1 without a state check.
- InformationRequestPartyService.kt:257-261, 481-487 and 554-557 call lockPartyMutationRequest, then requirePartyChangeAllowed, then mutationGate.requireContinuationEntitlement. :611-620 lockPartyMutationRequest runs lockParentExchangeOf before the request lock. :754-776 requirePartyChangeAllowed runs InformationRequestTransitionMatrix.canMutate.
- InformationRequestMutationGate.kt:24-30 lock (parent then request), :32-51 requireMutation, :53-69 requireContinuationEntitlement.
- InformationRequestTransitionMatrix.kt:258-265 denies with PARENT_LOCK_REQUIRED or [truncated]

Fix outline:

Claim 1:
1. InformationRequestDelegatedAuthorityService.kt: inject InformationRequestMutationGate. In grantMutation and revokeMutation, replace requestRepository.findRequestByIdForUpdate with val locked = gate.lock(command.requestId), which locks the parent Exchange before the request.
2. After the ETag and authorize checks, call gate.requireMutation(locked, InformationRequestMutation.ASSIGN_PARTY) for grant and REVOKE_PARTY for revoke. Reusing these avoids a new mutation enum value. If distinct values are preferred, add GRANT_DELEGATED_AUTHORITY/REVOKE_DELEGATED_AUTHORITY to the matrix as allowSameFrom(nonTerminalStates()), and check whether information_request_transition has a mutation CHECK that would need V151.
3. Then call gate.requireContinuationEntitlement(locked). Mutate locked.request.
4. Optionally record transition history the way recordPartyHistory does, so grant and revoke are audited (Architectural Decision 31).
5. InformationRequestDelegatedAuthorityResource.handleException: add an InformationRequestLifecycleException branch (409 with reasonCode, 404 for NOT_FOUND), or reuse InformationRequestCommandHttp.
6. Tests in InformationRequestDelegatedAuthorityServiceTest:
   - grant and revoke are refused (STATE_INVALID) for CLOSED, CANCELLED, SUPERSEDED and EXPIRED.
   - both are refused (PARENT_STATE_INVALID) for ENDED, REJECTED, RESCINDED and deleted parents.
   - both are refused for a lapsed entitlement and for a revoked execution grant (EXECUTION_GRANT_REVOKED).
   - no authority save and partyRevision unchanged on refusal.
   - an inOrder check that the [truncated]

### GA-019: cancelRemainingInformationRequests cancels requests when an ending Workflow starts, before ending is confirmed

- Severity: medium. Verification: confirmed. Fix size: M. Audit key: `ENDING-WORKFLOW-CANCEL`.
- Plan reference: P9-Decision-6, P9-T4. Plan basis: plans/DOCUMENT-DRIVEN-INFORMATION-REQUESTS-IMPLEMENTATION-PLAN.md:3252-3265 (Phase 9 decision 6: the completion service runs "directly or when its ending Workflow confirms"; cancelRemainingInformationRequests "cancels each one with reason EXCHANGE_ENDED in the same transaction"; the check runs before an ending Workflow starts and again at confirmation, "where a refusal leaves the Exchange unchanged"). Also 3369-3374 (P9-T4: ending must atomically reject or explicitly cancel), 630-633 (rule 32), [truncated]

Current state:

When an owner ends an Exchange with cancelRemainingInformationRequests=true and the Exchange has an ending Workflow, the remaining nongating Information Requests are cancelled before the Workflow decides. ExchangeUpdateService calls requestCompletion.prepareEnding(exchange, principal, cancelRemaining) first. That call locks the requests and cancels each remaining nongating open one: state CANCELLED, cancelledAt set, CANCEL history with reason EXCHANGE_ENDED. Only after that does the service trigger 'exchange.ending'. If a Workflow starts, the service throws WorkflowConflictException. updateExchange is @Transactional(dontRollbackOn = [WorkflowConflictException::class]), so the transaction commits. The cancellations are saved while the Exchange stays ACCEPTED_STARTED, and the caller gets a 409.

Nothing carries the caller's cancellation choice to the confirmation step. ExchangeApprovalEventHandler.requestsPermitEnding, run on exchange.ending and exchange.ended_confirmed, always calls prepareEnding with cancelRemaining = false. On refusal it only logs a warning. The Exchange end handling has no rollback path when the ending Workflow is rejected, and a cancelled request is terminal anyway. So if the ending Workflow is rejected or never finishes, the Exchange never ends, but those requests stay CANCELLED with reason EXCHANGE_ENDED.

This contradicts Decision 6. It says cancellation happens "in the same transaction" as the ending, and that a refusal at confirmation leaves things unchanged. It also contradicts P9-T4, under which ending must atomically reject or cancel.

No test [truncated]

Evidence:

src/main/kotlin/com/docuhyphen/app/api/service/exchange/ExchangeUpdateService.kt:105 (@Transactional(dontRollbackOn = [WorkflowConflictException::class]) on updateExchange); :273-315 (ENDED branch: :292 prepareEnding(exchange, principal, request.cancelRemainingInformationRequests == true) runs BEFORE :294 workflowEngineService.trigger("exchange.ending"); :306-314 throws WorkflowConflictException when a Workflow starts, so the transaction commits with the cancellations).
src/main/kotlin/com/docuhyphen/app/api/service/informationrequest/InformationRequestExchangeCompletionService.kt:26-53 (REMAINING_REQUESTS_REQUIRE_CANCELLATION branch :45-50 calls cancel() on each remaining request when cancelRemaining is true); :55-75 (sets CANCELLED, cancelledAt, CANCEL history with reasonCode EXCHANGE_ENDED, key information_request.exchange_ending|id).
src/main/kotlin/com/docuhyphen/app/api/service/exchange/ExchangeApprovalEventHandler.kt:255-258 and :279-283 (exchange.ending / [truncated]

Fix outline:

1. InformationRequestExchangeCompletionService: split prepareEnding into two methods. checkEnding(exchange, cancelRemaining) locks the requests, evaluates canEndExchange and throws the same refusals, but changes nothing. prepareEnding(exchange, actor, cancelRemaining) does the check and then cancels.
2. ExchangeUpdateService ENDED branch (lines 273-315): call checkEnding(exchange, cancelRemaining) before the trigger. If the trigger returns null (direct end), call prepareEnding(exchange, principal, cancelRemaining) right before updateStatus(ENDED), in the same transaction. If a Workflow started, save the ending intent (cancel flag and requesting principal kind/id) for that Workflow instance, then throw WorkflowConflictException with no request changed.
3. Persistence: migration V151 creating exchange_ending_intent (id, exchange_id, workflow_instance_id unique, cancel_remaining_information_requests boolean not null, actor_kind, actor_id, created_at). Entity in model/entity, repository in repository/exchange, and a small ExchangeEndingIntentService in service/exchange. Use a dedicated table rather than workflow subject_data_json, so the flag is not exposed as a $subject operand.
4. ExchangeApprovalEventHandler.requestsPermitEnding: load the intent for the Exchange's ending instance. Call prepareEnding(exchange, intent actor or the service account, intent.cancelRemaining) in the same transaction as the ENDED write, then consume the intent. On refusal, leave the Exchange and requests unchanged, as today. When the ending Workflow is rejected or cancelled, discard the intent; no [truncated]

### GA-020: Production S3 version store (write-once and checksum refusal) has no tests

- Severity: medium. Verification: confirmed. Fix size: M. Audit key: `G000-P6-T2a3`.
- Plan reference: P6-T2a3. Plan basis: Plan lines 2498-2514 (P6-T2a3): "the write fails rather than overwriting an existing key"; the object-store implementation uses a conditional write, and the producer selects it from document.version.storage.service. Lines 2530-2546 (P6-T2c): "the object store is sent the checksum so it refuses bytes that do not match". Lines 2781-2782 (Phase 6 Tests to write first): "Typed locator classification, provider routing, unique write-once version key, overwrite-denial, and byte identity tests". Line [truncated]

Current state:

The object-store version store has no tests at all, and it is the one every configured profile actually runs. AwsS3DocumentVersionStorageService does four things that nothing checks: (1) it sends a conditional PutObject with If-None-Match "*"; (2) it sends ChecksumAlgorithm.SHA256 along with the precomputed checksumSHA256 of the expected digest; (3) it turns HTTP 412 into DocumentVersionObjectKeyInUseException and the S3 error code "BadDigest" into DocumentVersionContentDigestMismatchException; (4) it turns 404/NoSuchKey on read into DocumentVersionContentNotFoundException. The class cannot be unit-tested as written because it builds its S3Client inline from a region string, so no fake client can be passed in. DocumentVersionStorageServiceProducer, which picks local or aws from document.version.storage.service, also has no test. The write-once refusal and checksum mismatch refusal are tested only against LocalDocumentVersionStorageService, and DocumentVersionContentServiceTest also runs only against the local store. Meanwhile file.storage.service=aws is set in application.properties and in the local, staging and prod profiles, and document.version.storage.service defaults to it. So on every real deployment, local development included, nothing tests these points: that the SDK really sends the precomputed SHA-256 (rather than working out its own), that a mismatch really comes back as "BadDigest", and that a repeated key really gets 412 and is refused. One thing softens this: DocumentVersionContentService.open re-hashes the stored bytes on every read and refuses a mismatch, [truncated]

Evidence:

src/main/kotlin/com/docuhyphen/app/api/service/storage/AwsS3DocumentVersionStorageService.kt:26-31: the constructor takes only the bucket and region config properties. Line 41: `private val s3Client: S3Client = S3Client.builder().region(Region.of(awsRegion)).build()`, so the client cannot be injected. Line 54: ifNoneMatch("*"). Lines 56-57: checksumAlgorithm(SHA256) plus checksumSHA256(expected.base64Value()). Lines 66-73: 412 maps to KeyInUse and "BadDigest" (const at line 38) maps to DigestMismatch; any other error is rethrown. Lines 97-109: 404/NoSuchKey maps to NotFound. Lines 115-147: deleteVersion. src/main/kotlin/com/docuhyphen/app/api/config/DocumentVersionStorageServiceProducer.kt:26-31: a when-switch on "local" or "aws". src/main/resources/application.properties:64 `file.storage.service=aws` and :72 `document.version.storage.service=${DOCUMENT_VERSION_STORAGE_SERVICE:${file.storage.service}}`. application-local.properties:55, application-staging.properties:51 and [truncated]

Fix outline:

No migration is needed (V151 stays free). 1) Make the S3 client injectable. Add a small CDI producer, for example config/DocumentVersionS3ClientProducer.kt, that builds an @Aws-qualified S3Client from document.version.storage.aws.region. Change AwsS3DocumentVersionStorageService to take that S3Client in its constructor instead of building it at line 41. 2) Add src/test/kotlin/com/docuhyphen/app/api/service/storage/AwsS3DocumentVersionStorageServiceTest.kt using mockito-kotlin with argumentCaptor<PutObjectRequest>. Cover: the put request carries ifNoneMatch "*", checksumAlgorithm SHA256, checksumSHA256 equal to expected.base64Value(), the bucket, and the allocated key. An S3Exception with statusCode 412 throws DocumentVersionObjectKeyInUseException. An S3Exception whose awsErrorDetails errorCode is "BadDigest" throws DocumentVersionContentDigestMismatchException. Any other S3Exception is rethrown unchanged. On openVersion, both NoSuchKeyException and a 404 S3Exception throw DocumentVersionContentNotFoundException and delete the temp file. deleteVersion follows keyMarker/versionIdMarker across truncated pages, deletes every version and delete marker of the exact key only (not other keys under the same prefix), and returns ABSENT when there are none. 3) Add DocumentVersionStorageServiceProducerTest covering "local", "aws" and an invalid value. 4) Optional, to prove the real SDK and S3 behaviour: add a test-scoped org.testcontainers localstack (or MinIO) dependency and a container contract test with a real S3Client. Cover: a second write to the same key is refused with the [truncated]

### GA-021: Schema editor silently wipes binding defaults, visibility overrides and sections; read-only default has no first-party authoring path

- Severity: medium. Verification: confirmed. Fix size: M. Audit key: `G005-P1-T2`.
- Plan reference: P1-T2. Plan basis: These plan lines put the requirement in force:
- Plan lines 948-953 (P1-T2): "Correct read-only default population and client payload behavior now".
- Line 1028: "Read-only default and UI payload tests".
- Line 1084 (exit criterion): "Read-only values have a defined privileged/default population path and do not break saving".
- Line 1077 (exit criterion): projections cannot disclose non-visible Fields. The visibility reset undermines the configuration this criterion relies on.

Nothing in the [truncated]

Current state:

The backend does have a default population path for read-only Fields. SchemaAssignmentService.materializeConfiguredDefaults writes each binding's defaultValueJson with SCHEMA_DEFAULT provenance when a Schema is assigned (V76 added that provenance). But nothing in the product can set that value. The only writer is BindingRequest.defaultValueJson on POST /schemas/definitions and PUT /schemas/definitions/{id}/draft/bindings. The Settings schema editor never sends it, and neither does any other frontend code.

The editor also drops data it does not model. BindingDraft carries only fieldDefinitionId, fieldContractId, label, keyLabel, isRequired and isReadOnly. toBindingRequests sends only fieldContractId, displayOrder, isRequired and isReadOnly. This matters because createDraftVersion copies section, defaultValueJson and visibility from the latest published version, and the SchemasPanel "New version" action then opens SchemaEditorDialog on that draft. Clicking "Save draft" calls updateDraftBindings, which runs deleteByVersion and then replaceBindings. replaceBindings stores section = null and defaultValueJson = null, and resets visibility to contract.dataClassification. So one UI save of a new version silently erases every configured default (the only way a read-only Field gets a value), every section grouping, and every visibility override.

That visibility reset has a security consequence. AudienceFieldBindingPolicy hides any non-PUBLIC binding from external callers. If a binding on a PUBLIC contract was narrowed to INTERNAL, the reset makes it PUBLIC again. Once that version [truncated]

Evidence:

All locations below were checked by hand.

Frontend:
- web-app/src/app/settings/fields-tab/schemaBindingDrafts.ts:8-27: BindingDraft and toBindingDrafts carry no section, defaultValueJson or visibility.
- web-app/src/app/settings/fields-tab/SchemaEditorDialog.tsx:34-40: toBindingRequests sends only fieldContractId, displayOrder, isRequired and isReadOnly.
- SchemaEditorDialog.tsx:62: the draft is seeded from schema.draftVersion.bindings.
- SchemaEditorDialog.tsx:89-92: Save calls updateSchemaDraftBindings or updatePlatformSchemaBindings.
- web-app/src/app/settings/fields-tab/SchemasPanel.tsx:60-66: handleNewVersion calls createSchemaDraftVersion, then opens the editor on the fresh draft.
- A grep of web-app/src for defaultValueJson finds only the type declarations (models.tsx:1881, fieldsService.ts:60). No UI writes it.
- web-app/src/app/exchanges/components/exchange-fields-tab/FieldValueEditor.tsx:19: read-only bindings are disabled. The Blueprint Business Fields tab reuses this [truncated]

Fix outline:

No migration is needed; V151 stays free.

Frontend:
1. schemaBindingDrafts.ts: add section?: string, defaultValueJson?: string and visibility: FieldDataClassification to BindingDraft. toBindingDrafts copies them from SchemaFieldBindingDto. draftForDefinition sets visibility from definition.latestContract.dataClassification and leaves section and default undefined.
2. SchemaEditorDialog.tsx: toBindingRequests sends section, defaultValueJson and visibility, so a save round-trips everything the draft already holds.
3. SchemaBindingsEditor.tsx (137 lines now): extract a SchemaBindingRow child component with its own Styles file to stay under ~150 lines. It adds:
   - a default-value control that reuses FieldValueEditor on a synthetic non-read-only binding and serializes via toCanonicalValue plus JSON.stringify;
   - a visibility dropdown;
   - an optional section text input.
   Follow the ids, circular buttons, one-attribute-per-line and token-spacing rules.

Backend:
4. SchemaDefinitionService.replaceBindings: parse and canonicalize defaultValueJson against the contract with fieldValueValidator. Throw FieldValidationException on an invalid default instead of deferring to the assignment-time skip, and store the canonical JSON.

Tests:
- Backend (service/fields): publish a version with a default, a narrowed visibility and a section; createDraftVersion; updateDraftBindings with the round-tripped request. Assert all three survive, an invalid default is refused, and assigning the published version stores a SCHEMA_DEFAULT value on the read-only binding.
- Frontend: [truncated]

### GA-022: No Workflow duplicate-binding regression test, and the workflow snapshot still reads orphaned values that V77 kept

- Severity: medium. Verification: confirmed. Fix size: S. Audit key: `G006-P1-T3`.
- Plan reference: P1-T3. Plan basis: - Requirement: plan lines 954-958 (P1-T3); 1029 (Schema stable-Field uniqueness migration tests); 1036 ("Workflow duplicate-binding and missing-value regression tests"); 1085 (exit criterion: at most one Contract per stable Field Definition); 1092 (exit criterion: Workflow behavior remains covered by regression tests); 4555 (detect and resolve ambiguous duplicate stable-Field bindings); 4255 (V77 ledger entry).
- The Development-Stage Constraint, lines 168-195, does not supersede this. At 184 [truncated]

Current state:

The P1-T3 service guard and migration are in place. SchemaDefinitionService.replaceBindings refuses a second contract of the same Field Definition, and SchemaVersionStableFieldBindingTest covers that. V77 adds ux_binding_field_definition, and SchemaFieldBindingStableFieldContractTest covers the clean and populated cases. The "missing value is non-match" Workflow test also exists.

Two things are still missing.

(1) There is no Workflow-facing regression test for a duplicate or orphaned stable-Field answer. WorkflowApplicabilityEvaluatorTest mocks ExchangeFieldSnapshot directly. RootFieldValueSetTest only proves that the snapshot reads the root set and skips occurrence sets. No test anywhere puts two answers for one Field Definition into the root set and checks what a condition sees.

(2) The Workflow reader does not filter by the contracts bound in the assigned Schema Version. When V77 removes a binding, it keeps that binding's field_value row: it only sets schema_field_binding_id to NULL and keeps the old field_contract_id. Its own contract test asserts the row survives. V79 then puts every value of an assignment into its ROOT set, and no later migration (V80-V150) removes these orphans. ExchangeFieldQueryService.getCanonicalValues reads every row in the root set through an unordered JPQL query (FieldValueRepository.findByValueSet has no ORDER BY). It maps each row's contract to its fieldDefinitionId and writes byFieldDefinitionId last-wins. In an upgraded database that had duplicate bindings with values on both sides, a Workflow applicability condition can therefore see [truncated]

Evidence:

- ExchangeFieldQueryService.kt:30-58 loops over fieldValueRepository.findByValueSet(rootValueSet.id). The only lookup is fieldContractRepository.findById per row. It never checks the assignment's schemaVersionId bindings, and it writes `byFieldDefinitionId[contract.fieldDefinitionId] = ...` (last-wins).
- FieldValueRepository.kt:34-38: `SELECT v FROM FieldValue v WHERE v.fieldValueSetId = :sid` has no ORDER BY, so which row wins is not fixed.
- FieldsProjectionLoader.kt:84-102: resolveValues gets bindings from bindingRepository.findByVersion, keys stored values by fieldContractId, and looks up only question.contract.id. This is the filtered path the Workflow reader does not use.
- V77__schema_field_binding_stable_field_invariant.sql lines 78-84: "Resolve" only sets `schema_field_binding_id = NULL` on values of REMOVED bindings, then deletes the bindings. The value rows stay with their old contract.
- SchemaFieldBindingStableFieldContractTest.kt:79-83 asserts the orphan keeps "First [truncated]

Fix outline:

1. src/main/kotlin/com/docuhyphen/app/api/service/fields/ExchangeFieldQueryService.kt: change getCanonicalValues to resolve answers through the assigned version's bindings. Inject FieldsProjectionLoader, which is in the same fields package, so the service-to-repository rule is respected. Call resolveValues(assignment.schemaVersionId, rootValueSet) and key each entry with a stored value by question.binding.fieldDefinitionId, reusing its selectionCodes. Because ux_binding_field_definition allows one binding per stable Field per version, the snapshot then holds exactly one bound answer per Field, and any row whose contract is not bound is ignored. Remove the now-unused FieldContractRepository and FieldValueSelectionRepository dependencies. Update the constructor in RootFieldValueSetTest.

2. Optional, allowed by the Development-Stage Constraint: add a forward migration V151__field_value_unbound_contract_removal.sql. It deletes field_value rows whose field_contract_id is not bound in their assignment's schema_version_id, using NOT EXISTS against schema_field_binding joined through schema_assignment. field_value_selection rows go with them via ON DELETE CASCADE, and field_value_revision history is kept as identities. Then add CHECK or trigger-free enforcement by making schema_field_binding_id NOT NULL on field_value, after carrying any remaining null-pointer rows to their bound binding. Record V151 in the migration ledger.

3. Tests:
   (a) A new ExchangeFieldQueryServiceTest, or cases added to RootFieldValueSetTest: the root set holds the bound V2 answer plus an orphaned V1 [truncated]

### GA-023: Blueprint Information Request tab offers platform Template Versions that the backend always refuses

- Severity: medium. Verification: confirmed. Fix size: S. Audit key: `G016-P2-T9`.
- Plan reference: P2-T9. Plan basis: - P2-T9 (plan lines 1240-1247) says a Blueprint references an exact published Template Version.
- Phase 12 design decision 11 (lines 4022-4030, revised 2026-09-30 during P12-T3) says: "a platform-scope Template is a starting point an owner copies into its own scope, never a Version a request pins". The create dialog offers platform Templates only through copying. The backend owner rule enforces this.
- Test list lines 1299-1300 call for "Blueprint Definition reference scope" tests.
- Nothing in [truncated]

Current state:

The Blueprint editor's Information Request tab lists Templates the backend will never accept. useBlueprintTemplateChoices.scopesFor returns [PLATFORM] for APP Blueprints and [ORGANIZATION or PERSONAL, PLATFORM] for ORG and PERSONAL Blueprints. It then offers the latest published Version of every Template in those scopes. It also skips the RETIRED filter that the create dialog applies. On save, BlueprintDefinitionService.selectTemplateVersion accepts only a Version whose owner is the Blueprint's own organization (ORG) or the calling user (PERSONAL), and always returns false for BlueprintScope.APP. It then throws ForbiddenException. So picking a platform Template on an ORG or PERSONAL Blueprint, or picking anything on an APP (platform) Blueprint, makes the whole create or update fail with 403. The dialog only shows the generic "Failed to save blueprint" error. The tab still appears for APP Blueprints even though no choice can ever succeed there. The frontend test enshrines the wrong behaviour: BlueprintInformationRequestTab.test.tsx:68 asserts that the "Platform collection" option is offered. The sibling Information Request create dialog was already narrowed to owner scope only, with a copy-first hint (useRequestSourceOptions.ts templateScopes = [ownerScope]; CreateInformationRequestDialog.tsx PLATFORM_COPY_HINT). The Blueprint tab was never brought in line.

Evidence:

- web-app/src/app/settings/blueprints-tab/blueprint-information-request-tab/useBlueprintTemplateChoices.ts:13-18: scopesFor returns PLATFORM for APP and [owner, PLATFORM] otherwise. Lines 28-29 list those scopes and filter only on latestPublishedVersionNumber, with no RETIRED check.
- BlueprintInformationRequestTab.tsx: renders every choice with no scope or ownership filter and no APP-scope guard.
- BlueprintEditorDialog.tsx:252 always renders the Information Request tab. Line 547 passes scope={effectiveScope}, which can be APP (line 91). The create path sends informationRequestTemplateVersionId; the update path sends templateVersionChange. Line 219 catches the error and shows the generic 'Failed to save blueprint'.
- BlueprintInformationRequestTab.test.tsx:47-50 and 68: mocks PLATFORM results and asserts the 'Platform collection' option is present for an ORG Blueprint.
- src/main/kotlin/com/docuhyphen/app/api/service/blueprint/BlueprintDefinitionService.kt:168-169 (create), 384-386 [truncated]

Fix outline:

No migration is needed (V151 stays free).
1. In web-app/src/app/settings/blueprints-tab/blueprint-information-request-tab/useBlueprintTemplateChoices.ts:
   - Make scopesFor return only the owner scope: ORG gives [ORGANIZATION], PERSONAL gives [PERSONAL], APP gives [] with no fetch.
   - Filter out templates whose status is RETIRED, matching publishedTemplates in useRequestSourceOptions.ts. The shared filter could be pulled out.
2. In BlueprintInformationRequestTab.tsx:
   - For scope APP, render an explanatory Text instead of the ChoiceSelect, with a stable id such as blueprint-information-request-platform-unavailable. It should say that platform Blueprints cannot start an Information Request, and that an owner who clones the Blueprint can choose one of their own Templates.
   - For ORG and PERSONAL, add a hint like the create dialog's PLATFORM_COPY_HINT: copy a platform Template in Settings first, then choose the copy. The hint text could be shared.
   - Alternatively, BlueprintEditorDialog.tsx can hide the Information Request Tab (line 252) when effectiveScope === 'APP'.
3. Update BlueprintInformationRequestTab.test.tsx:
   - Assert that the ORG scope lists only the ORGANIZATION call and no 'Platform collection' option, and that PLATFORM is never requested.
   - Assert that APP scope makes no list call and shows the explanation.
   - Assert that a RETIRED template is not offered.
4. Optional: surface the backend ForbiddenException message instead of the generic 'Failed to save blueprint' in BlueprintEditorDialog.tsx:219.
5. Help docs: add a sentence to [truncated]

### GA-024: ARCHIVE_OUTSIDE_ACTIVE_RESPONSE behaves exactly like RETAIN_SECURELY

- Severity: medium. Verification: confirmed. Fix size: M. Audit key: `G043-P5-T4`.
- Plan reference: P5-T4. Plan basis: Plan lines 2162-2163 (P5-T4 asks for a hidden-data policy per condition: retain securely, clear with confirmation, or archive outside the active response). Lines 2166-2168 (P5-T4b asks to enforce each of the three on FALSE or UNKNOWN). Lines 2314-2317 (P5-R16 asks for activation transitions for every hidden-response policy). Line 4180 (the structured-data test matrix includes hidden-data policy). Lines 4374-4382 (the V115/V116 migration records name three platform policies). I found nothing [truncated]

Current state:

ARCHIVE_OUTSIDE_ACTIVE_RESPONSE exists only as a label. It is in the backend enum, the V115/V116 CHECK constraints, the DTOs, the frontend model, and the authoring dropdown ("Archive hidden answers outside the response"). No production code gives it its own behaviour. When a condition becomes FALSE or UNKNOWN, InformationRequestResponseDraftService branches only on CLEAR_WITH_CONFIRMATION. RETAIN_SECURELY and ARCHIVE_OUTSIDE_ACTIVE_RESPONSE both run the same path: they set activeInResponse=false, hiddenByConditionRuleKey, hiddenDataPolicy and hiddenAt on the same information_request_response row. Disposition, narrative and fieldValueSetId stay on that row, and the collected Field values stay in the live root or occurrence Field Value Set. Nothing is copied or moved to any archive store (none exists), and there is no archive table or column. Reactivation is also the same for both policies: the row comes back as it was. The hidden Field values of both policies still feed InformationRequestConditionEvaluationService, which reads the whole live Field Value Set and does not filter on hidden state. Only the response-envelope projections (findCurrentForRequest, activeResponses, activeFieldProjection, SubmissionContentCollector) treat hidden rows as inactive, and they do so for both policies alike. The tests and the help article record this sameness as intended behaviour, so an author who picks "archive" gets the retain behaviour.

Evidence:

src/main/kotlin/com/docuhyphen/app/api/model/entity/InformationRequestTemplateEnums.kt:86-91 (enum). A grep of src/main for ARCHIVE_OUTSIDE_ACTIVE_RESPONSE finds only that enum line plus V115__information_request_condition_hidden_data_policy.sql:9 and V116__information_request_response_hidden_state.sql:13. InformationRequestResponseDraftService.kt:477-479: only CLEAR_WITH_CONFIRMATION clears Fields. Lines 505-532 (reactivateResponse): only CLEAR resets data, and lines 519-524 are that branch. Lines 534-573 (hideResponse): only CLEAR wipes data (544-556). Every other policy just sets activeInResponse=false and the hidden metadata (566-572). The other hidden-state readers are InformationRequestResponseRepository.kt:15-27 (findCurrentForRequest filters activeInResponse), InformationRequestResponseWorkspaceService.kt:85 and InformationRequestSubmissionContentCollector.kt:57. None of them branch on the policy. InformationRequestConditionEvaluationService.kt:101-135 reads live root and [truncated]

Fix outline:

Pick the archive semantics first; this is the open decision. The recommended reading: ARCHIVE moves hidden data out of the live response into a separate snapshot. RETAIN keeps it in place but hidden.
(1) Migration V151__information_request_response_archive.sql: add an information_request_response_archive table with id, information_request_id, information_request_requirement_id, occurrence_path, condition_rule_key, archived_at, archived disposition, narrative, and archived Field values as canonical JSON plus the source Field revision/etag. Give it FKs to the request and requirement, and make it append-only using the existing append-only guard trigger. Also add a nullable archive_id on information_request_response.
(2) Add a model/entity InformationRequestResponseArchive, a repository/informationrequest/InformationRequestResponseArchiveRepository, and a small service/informationrequest/InformationRequestHiddenResponseArchiver. The archiver snapshots the response envelope and the collected Field value through SchemaAssignmentService.getAssignment, then clears the live Field through SchemaAssignmentService.clearValues. It needs no confirmation because the data is preserved. It resets disposition, narrative and fieldValueSetId on the row.
(3) InformationRequestResponseDraftService: in enforceHiddenResponsePolicies and hideResponse, add an ARCHIVE_OUTSIDE_ACTIVE_RESPONSE branch that calls the archiver. Decide the reactivation rule too: either restore from the archive snapshot through a sparse Field patch plus the envelope, or reactivate empty with the archive kept as history. [truncated]

Decision needed. Recommended default: Implement ARCHIVE_OUTSIDE_ACTIVE_RESPONSE as: snapshot the hidden answer into an append-only archive record, clear the live values, and do not restore automatically when the condition turns true again.

### GA-025: Downstream conditions still read Field values kept on a hidden Requirement

- Severity: medium. Verification: confirmed. Fix size: M. Audit key: `G044-P5-T3`.
- Plan reference: P5-T3. Plan basis: Plan lines 2134-2136 (P5-T3: defined null and unknown semantics and server evaluation). Lines 2150-2154 (P5-T3b: condition inputs are current Field values and current dispositions; an unanswered Requirement defaults to NOT_ANSWERED, which sets the precedent that hidden or absent data counts as absent rather than as a value). Lines 2162-2168 (P5-T4/P5-T4b: archive outside the active response, then re-evaluate active response shape and completeness). Lines 2194-2203 (P5-T10: per-occurrence [truncated]

Current state:

InformationRequestConditionEvaluationService builds its Field inputs from every stored FieldValue in the root Value Set and the ancestor occurrence Value Sets. It never checks whether the Requirement that collected the Field is active. Under RETAIN_SECURELY and ARCHIVE_OUTSIDE_ACTIVE_RESPONSE, hiding a Requirement only changes its response envelope (activeInResponse=false, hiddenByConditionRuleKey, hiddenAt). The Field value stays in the Value Set, and the next evaluation reads it again. Only CLEAR_WITH_CONFIRMATION actually clears the Field.

Disposition inputs work differently. They come from responseStore.findCurrentForRequest, which only returns activeInResponse = true rows, so a hidden Requirement's disposition resolves to NOT_ANSWERED. The draft service's fixed-point loop therefore cascades hiding through disposition chains and clear-policy Field chains, but never through retain/archive Field chains.

The template validator allows the chain. Rule B can read a Field collected by conditional Requirement A, and only cycles are refused. Example: A is governed by rule R and has a retained value. When R turns FALSE, B still evaluates TRUE from A's hidden value. B's dependent Requirement then stays active in the workspace, stays INCOMPLETE and counted in the completeness denominator (so it is still owed at submission), and keeps its response envelope active. All of this is driven by data outside the active response.

Every consumer reads the same evaluation, so the wrong state reaches all of them: request detail conditionEvaluations, the workspace, completeness, and [truncated]

Evidence:

src/main/kotlin/com/docuhyphen/app/api/service/informationrequest/InformationRequestConditionEvaluationService.kt:59 (fieldValuesByScope called with no activeness input), :101-121 (root set plus ancestor occurrence sets are overlaid with no filter), :123-135 (canonicalValues reads fieldValueRepository.findByValueSet(valueSetId) and maps every value to its Field definition, with no check on the collecting Requirement or its response), :155-163 (dispositions come from responseStore.findCurrentForRequest, and a missing one defaults to NOT_ANSWERED).
src/main/kotlin/com/docuhyphen/app/api/repository/informationrequest/InformationRequestResponseRepository.kt:15-27 (findCurrentForRequest filters activeInResponse = true).
src/main/kotlin/com/docuhyphen/app/api/service/informationrequest/InformationRequestResponseDraftService.kt:240-255 (fixed-point loop calls enforceHiddenResponsePolicies again until nothing changes), :477-479 (clearHiddenFields runs only for CLEAR_WITH_CONFIRMATION), [truncated]

Fix outline:

1. Change InformationRequestConditionEvaluationService.evaluate to derive Requirement activity itself instead of trusting raw Value Sets.
   - Load the bindings once, including collectedFieldDefinitionId, conditionalRuleKey and requiredness.
   - Evaluate the rules in rounds until a round changes nothing. The validator guarantees conditional dependencies are acyclic, so this ends after at most depth+1 rounds.
   - In each round, a conditional Requirement at occurrence path P is inactive when its rule's state at P (falling back to root, as InformationRequestActiveResponseProjection does) is not TRUE, or when its stored response envelope is inactive.
   - When building fieldValuesByScope for a scope, replace each inactive Requirement's collectedFieldDefinitionId with null, at that Requirement's own Value Set path, for both root and ancestor occurrence overlays. Using null mirrors the existing NOT_ANSWERED default for dispositions: hidden data counts as absent, IS_EMPTY evaluates TRUE and value comparisons evaluate FALSE.
   - Resolve dispositions from the same computed activity, not only the stored activeInResponse flag, so reads never lag behind the evaluation.
   - Keep the method's signature and its projection output unchanged.
2. Nothing else needs to change.
   - InformationRequestResponseDraftService's existing fixed-point loop will then cascade hiding through retain and archive Field chains in the same command.
   - Completeness, workspace and request-detail consumers pick up the fix automatically.
3. No migration is needed (V151 stays free). There is no REST or [truncated]

### GA-026: The validation extension point is too thin for cross-row, aggregate, or period-coverage checks

- Severity: medium. Verification: confirmed. Fix size: M. Audit key: `G045-P5-T6`.
- Plan reference: P5-T6. Plan basis: Where the requirement is in force:
- Line 2173-2174: P5-T6 "cross-field, cross-row, unit, currency, date-range, period-coverage, and duplicate validation extension points" (checked).
- Line 2215: required test "Cross-occurrence aggregate consistency, period coverage, and duplicate-entry tests".
- Line 2220: exit criterion "save incomplete work without bypassing submission validation".
- Line 915 and 3825: cross-occurrence validation is a shared capability. S3 must prove "cross-occurrence [truncated]

Current state:

P5-T6 is checked off, but the extension point can only handle checks that stay inside a single command. It cannot express cross-row, cross-occurrence aggregate, or period-coverage rules, and it cannot gate submission.

1. What a validator receives (InformationRequestStructuredResponseValidationContext):
   - the request
   - Requirements in active occurrences
   - the raw command patches, where each FieldValueEntry.value is a client JsonElement that has not been canonicalized
   - the pre-write active InformationRequestResponse rows. These carry only disposition, occurrencePath and a fieldValueSetId.

   It gets no stored or post-patch Field values, no occurrence rows (group key, parent, order), and no Template bindings. To compare a new value with an earlier saved occurrence, a validator would have to inject Fields repositories itself and merge the patch onto stored state by hand. The backend rule against using another service's repository forbids that.

2. Where it runs: validate is called in exactly one place, InformationRequestResponseDraftService.mutate, before the Field write. It is not called on occurrence add, remove or reorder. InformationRequestSubmissionReadinessEvaluator does not call it either. So:
   - Removing a row that broke coverage passes unchecked.
   - Two PATCHes that each save one duplicate value pass.
   - Any aggregate or coverage validator could only block sparse draft saves, where coverage is naturally incomplete. That conflicts with the Phase 5 exit criterion "save incomplete work without bypassing submission validation". It could never produce [truncated]

Evidence:

- InformationRequestStructuredResponseValidationService.kt:22-27. The context holds only request, requirementsById, patches and activeResponses.
- Same file:38. `val kinds` is declared. `grep "\.kinds\b" src/main/kotlin` returns nothing.
- Same file:62-71. validate = duplicatePatchIssues + CDI validators, then throws STRUCTURED_RESPONSE_VALIDATION_FAILED.
- Same file:73-104. The only built-in check is a same-patch duplicate Requirement or Field.
- FieldsCommands.kt:49-53. FieldValueEntry.value is a raw JsonElement.
- InformationRequestResponse.kt:37-50. A response row exposes only occurrencePath, disposition, narrative and fieldValueSetId (no values).
- InformationRequestResponseDraftService.kt:190-197. The only call site, run before writeBatchedFieldValues at line 217, so it sees pre-write state.
- InformationRequestGroupOccurrenceService.kt:121-160. add, remove and reorder never call the validation service. A grep for "validat" in the file finds nothing.
- [truncated]

Fix outline:

No migration is needed (V151 stays free).

1. InformationRequestStructuredResponseValidationService.kt:
   - Add a validation stage enum: RESPONSE_SAVE, OCCURRENCE_CHANGE, SUBMISSION. Data classes belong in the model/informationrequest package.
   - Enrich the context with:
     - the stage
     - the request's occurrences (id, groupKey, parent, ordinal, path)
     - the effective Template bindings
     - the canonical post-change Field values keyed by occurrence path and fieldContractId, loaded through SchemaAssignmentService or a small Fields read service rather than repositories
     - the condition evaluations
   - Either read `kinds` (for example, to let each validator declare which stages it applies to) or delete it.
   - Return issues from a non-throwing `evaluate(context)`. Keep `validate` as the throwing wrapper for save paths.

2. InformationRequestResponseDraftService.kt: run the validators after writeBatchedFieldValues and applyPatch, in the same transaction, against post-write canonical state. A refusal then rolls back and catches duplicates against earlier saved occurrences. Keep the current raw-patch duplicate check.

3. InformationRequestGroupOccurrenceService.kt: run OCCURRENCE_CHANGE validation after add, remove and reorder, in the same transaction.

4. InformationRequestSubmissionReadinessEvaluator.kt: run SUBMISSION-stage validators. Map each issue to a new InformationRequestSubmissionProblemCode.STRUCTURED_VALIDATION_FAILED item problem carrying requirementId and occurrencePath, disclosed or undisclosed through the existing gate. Validators that need [truncated]

### GA-027: Condition-driven hiding and clearing leaves no audit trail

- Severity: medium. Verification: confirmed. Fix size: M. Audit key: `G047-Phase`.
- Plan reference: Phase 5 exit criteria. Plan basis: Plan lines that put the requirement in force:
- 2222 (Phase 5 exit criterion): "Conditions are deterministic, versioned, server-authoritative, and auditable."
- 2072 (P5-T1a): SAVE_RESPONSE transition/audit routing.
- 492-498 (cross-cutting decision 20): every state-changing service records its classified audit event and immutable history in the same transaction.
- 853-854: a mutation task needs an audit event plus immutable history or a transition record.
- 916 and 3901: a changed condition [truncated]

Current state:

When a response patch changes a condition, the server can hide a response, clear it (CLEAR_WITH_CONFIRMATION) or reactivate it. None of these effects is recorded in any durable or immutable form. The whole patch writes one SAVE_RESPONSE transition and one information_request.requirement.respond audit event. Their payload carries only transition id, sequence, from/to state, mutation, exchangeId and templateVersionId. It does not say which Requirements the respondent patched, which Requirements were hidden or reactivated, under which rule key, expression version, TRUE/FALSE/UNKNOWN state or hidden-data policy, which were cleared, or which clears the respondent confirmed (confirmedHiddenResponseClears).

The information_request_response row is a single current row, unique per request and Requirement, updated in place. hideResponse overwrites hiddenByConditionRuleKey, hiddenDataPolicy and hiddenAt. Under CLEAR it also nulls disposition, narrative and fieldValueSetId with no history. reactivateResponse sets all three hidden columns back to null. After that, nothing shows that the response was ever hidden, by which rule, or when. Condition evaluation states are computed on each read and never persisted or audited.

Two partial traces do exist, but neither attributes anything to a condition:
- A Field clear goes through SchemaAssignmentService.clearValues. That keeps field_value_revision history and emits a FIELD_VALUE_UPDATE audit event carrying only changedCount and clearedCount.
- A later Submission Package freezes each item's completeness_state as HIDDEN, but only at [truncated]

Evidence:

src/main/kotlin/com/docuhyphen/app/api/service/informationrequest/InformationRequestResponseDraftService.kt:257-273: when anything changed, it records one InformationRequestTransitionHistoryCommand(mutation = SAVE_RESPONSE) with no details, reasonCode or evidence.
- Same file, lines 240-255 and 443-503: enforceHiddenResponsePolicies computes the rule key, evaluation.state and hiddenDataPolicy for each Requirement, then discards them after mutating rows.
- Same file, lines 534-573: hideResponse overwrites the hidden columns and, under CLEAR_WITH_CONFIRMATION (544-556), nulls disposition, narrative and fieldValueSetId in place.
- Same file, lines 505-532: reactivateResponse nulls hiddenByConditionRuleKey, hiddenDataPolicy and hiddenAt (525-528), and also resets the data for CLEAR.
- Same file, lines 580-606: clearHiddenFields calls schemaAssignmentService.clearValues and keeps no record of the [truncated]

Fix outline:

1. Add a new V151 migration, V151__information_request_response_condition_effect.sql. It creates an append-only table information_request_response_condition_effect with these columns:
   - id
   - information_request_id
   - information_request_requirement_id
   - response_id (FK)
   - transition_id (FK to information_request_transition)
   - occurrence_path
   - response_revision
   - rule_key
   - expression_version
   - evaluation_state (TRUE/FALSE/UNKNOWN)
   - hidden_data_policy
   - effect (HIDDEN, CLEARED, REACTIVATED, REACTIVATED_EMPTY)
   - clear_confirmed
   - cleared_field_value_revision_id (nullable; points at the last pre-clear Field revision, and no cleared values are copied)
   - actor kind/id/session_ref
   - occurred_at
   Add CHECK constraints and an update/delete-refusing trigger. In the same migration, CREATE OR REPLACE the V142/V146 record-preservation disposal functions so they delete these rows before information_request_response.
2. Add an entity in model/entity (InformationRequestResponseConditionEffect plus an effect enum), InformationRequestResponseConditionEffectRepository in repository/informationrequest, and an InformationRequestConditionEffectRecorder service in service/informationrequest.
3. In InformationRequestResponseDraftService:
   - Have hideResponse and reactivateResponse return an effect descriptor (requirement, rule key, evaluation state and expression version, policy, cleared, confirmed) alongside the updated row.
   - Collect the descriptors across the enforcement loop.
   - After transitionHistory.record returns the transition, [truncated]

### GA-028: A draft PATCH cannot clear a Field the Schema marks required

- Severity: medium. Verification: confirmed. Fix size: S. Audit key: `G048-P5-T1c`.
- Plan reference: P5-T1c. Plan basis: Line 420-422 (Invariant 3): "A Schema remains a pure structured-data contract. Respondent prompt text, requiredness, ... belong to the Template Requirement binding." Line 950 (P1-T2): "Request requiredness belongs to Template Requirements and is implemented in Phases 5 and 7." Line 314 (Core scope): "Sparse draft saving with explicit server-side completeness validation at submission." Line 2063-2064 (P5-T1) and 2083-2085 (P5-T1c): explicit clear operations through the Fields engine. Line 2208: [truncated]

Current state:

Every Field Requirement draft write goes through InformationRequestResponseDraftService.writeBatchedFieldValues, which calls SchemaAssignmentService.setValues. setValues always runs writeValues with allowRequiredClear=false. writeValues refuses an empty canonical value on any binding whose Schema marks it isRequired, throwing FieldValidationException("<label> is required"). Both the authenticated and the no-auth PATCH /information-requests/{id}/responses resources map that to 400. The Fields path (the patch request's fieldValues.values entries) has no separate clear operation. A JsonNull entry is the only way to clear a value, and the wire mapper passes entries through unchanged. The frontend sends exactly that: buildSparseFieldValuePayload, called from structuredResponseWorkspaceState.fieldValueChanges, sends null when a respondent empties a Field. So once a Field bound as isRequired in the Template's chosen Schema holds a value, the respondent's draft can never hold it empty again, and the whole patch is rejected. This enforces Schema requiredness while the respondent is still drafting. The plan says requiredness belongs to the Template Requirement binding and is checked at submission, not to the Schema. Nothing stops an Information Request Template from pointing at a Schema with required bindings. InformationRequestTemplateConfigurationValidator and InformationRequestTemplatePublicationReadiness only check that schemaVersionId is present. The only bypass, clearValues (allowRequiredClear=true), is used only for condition-driven hidden-data clearing (clearHiddenFields). [truncated]

Evidence:

src/main/kotlin/com/docuhyphen/app/api/service/fields/SchemaAssignmentService.kt:267-274 (clearValues passes allowRequiredClear=true; setValues passes false), :311-313 (if canonical.isEmpty && binding.isRequired && !allowRequiredClear throw FieldValidationException "... is required"). src/main/kotlin/com/docuhyphen/app/api/service/informationrequest/InformationRequestResponseDraftService.kt:217 (mutate calls writeBatchedFieldValues), :723-747 (writeBatchedFieldValues calls schemaAssignmentService.setValues with the caller's entries), :580-606 (clearValues is used only by clearHiddenFields for CLEAR_WITH_CONFIRMATION hidden data). src/main/kotlin/com/docuhyphen/app/api/model/InformationRequestResponsePatchRequestMapper.kt:28-35 (fieldValues entries pass through unchanged; no Field clear flag). src/main/kotlin/com/docuhyphen/app/api/resource/model/InformationRequestRequests.kt (InformationRequestResponseFieldValuesPatchRequest has only etag + values). [truncated]

Fix outline:

No migration needed (V151 stays free).
1. src/main/kotlin/com/docuhyphen/app/api/service/fields/FieldResourceAdapter.kt: add a hook, e.g. `fun enforcesBindingRequirednessOnWrite(resourceId: UUID): Boolean = true`.
2. SchemaAssignmentService.writeValues (line 312): change the guard to `canonical.isEmpty && binding.isRequired && !allowRequiredClear && adapter.enforcesBindingRequirednessOnWrite(resourceId)`. Exchange behaviour and the existing Fields test stay unchanged.
3. src/main/kotlin/com/docuhyphen/app/api/service/informationrequest/InformationRequestFieldResourceAdapter.kt: override the hook to return false. Requiredness for an Information Request comes from the Template binding's `requiredness`, which InformationRequestCompletenessProgressService and submission validation already evaluate.
   Alternative: add `SchemaAssignmentService.writeDraftValues` (allowRequiredClear=true) and call it from InformationRequestResponseDraftService.writeBatchedFieldValues and InformationRequestFactRecertificationService. The adapter hook is cleaner because it keeps the decision with the owning resource.
4. Tests:
   - A SchemaAssignmentService unit test: emptying an isRequired binding succeeds and records a cleared revision when the adapter opts out, and is still refused for EXCHANGE.
   - An Information Request draft test on a real SchemaAssignmentService, or a PostgreSQL-backed transaction test, with a Schema binding isRequired=true. Set a value, then PATCH it to null. Expect 200, the value empty, the response ETag advanced and revision history kept, with completeness/progress [truncated]

### GA-029: Group commands fall back to aggregate contributor authority when no materialized binding or Requirement is in scope

- Severity: medium. Verification: confirmed. Fix size: M. Audit key: `G061-P5-R06`.
- Plan reference: P5-R06 / P5-R24. Plan basis: Plan lines 2271-2274 (P5-R06: authorize group commands against the authored group and every affected Requirement and descendant scope; cover zero-occurrence creation without falling back to aggregate contributor authority). Lines 2352-2356 (P5-R24: authorize a new group occurrence against its intended creation scope; completes the remaining P5-R06 creation gap). Lines 2344-2347 (P5-R22: retain authorized zero-occurrence group controls through authored-policy checks). None of these are [truncated]

Current state:

The only per-scope check on group add, remove and reorder comes from InformationRequestGroupAuthorizationService, and it does nothing when the scope it is given is empty. When that happens, the only check left is the request-level INFORMATION_REQUEST_REQUIREMENT_RESPOND authorize call in mutate(). The request-level policy (InformationRequestPolicyEvaluator) checks only parent Exchange state. Any CONTRIBUTOR or PREPARER on the request holds the RESPOND capability, so that check is aggregate contributor authority.

(1) Add: authorization covers only template.materializedBindings(group), meaning the group's own anchored bindings plus those of child groups with minOccurrences > 0. For a container group, this list is empty and authorizeMaterializedBindings is a no-op. A container group is one that anchors no Requirement and whose child groups are all minimum 0, or that has no children. Neither the backend validator nor the frontend draft validation rejects such groups, so they are authorable. Any contributor can therefore add occurrences of such a group up to maxOccurrences. This is exactly the zero-occurrence-creation fallback that P5-R06 and P5-R24 forbid.

(2) Remove: authorizeOccurrenceScope authorizes only materialized Requirements whose occurrencePath exactly matches a removed occurrence or descendant. A subtree with no materialized Requirements (a container occurrence with no populated optional children) is removable by any contributor, including occurrences created by another party.

(3) Reorder: only the siblings' own paths are passed, never descendant paths. A group [truncated]

Evidence:

src/main/kotlin/com/docuhyphen/app/api/service/informationrequest/InformationRequestGroupOccurrenceService.kt:
- 177-181: add authorizes only template.materializedBindings(group).
- 542-548: materializedBindings = own anchored bindings plus child groups with minOccurrences > 0.
- 229-234: remove authorizes only occurrence paths through authorizeOccurrenceScope.
- 266-270: reorder passes only activeSiblings paths, no descendants.
- 326 and 393-405: the aggregate authorize(access, request.id) with Action.INFORMATION_REQUEST_REQUIREMENT_RESPOND on ResourceRef.informationRequest.

src/main/kotlin/com/docuhyphen/app/api/service/informationrequest/InformationRequestGroupAuthorizationService.kt:
- 25-35: authorizeOccurrenceScope filters Requirements by exact occurrencePath and silently passes when none match.
- 37-46: authorizeMaterializedBindings iterates an empty list without [truncated]

Fix outline:

No migration is needed.

1. InformationRequestGroupAuthorizationService: replace the two permissive helpers with scope-complete, fail-closed methods.
   - authorizeGroupCreation(access, request, groupBindings): for a container group, when materializedBindings is empty, authorize against every authored binding under the group (bindingsUnder, including minimum-0 descendant groups). Refuse with ForbiddenException when that set is also empty.
   - authorizeOccurrenceChange(access, request, occurrencePaths, authoredBindings): authorize every materialized Requirement at or beneath the given paths (prefix match on "path/"), plus every authored binding of the affected groups through authorizeAgainstAuthoredBinding. Refuse when both sets are empty.
   - Neither method may return without having authorized something.

2. InformationRequestGroupOccurrenceService:
   - add: call the creation method with materializedBindings, falling back to bindingsUnder.
   - remove: pass the removed occurrence plus descendants, and bindingsUnder(group).
   - reorder: pass every active sibling plus all their active descendants (findActiveForRequestForUpdate filtered by prefix), and bindingsUnder(group).
   - Keep the aggregate authorize() only as a pre-check.

3. InformationRequestTemplateConfigurationValidator.normalizeGroups and templatePlanValidation.ts groupProblems: refuse a group whose subtree anchors no Requirement (refuseGroup with groupKey), so every group has a creation scope. Keep the frontend message in step with the backend one.

4. Align the workspace: [truncated]

### GA-030: Contact-proof session issuance locks ShareLink before the parent Exchange, inverting the order used by party reassignment and revocation

- Severity: medium. Verification: confirmed. Fix size: S. Audit key: `G062-P5-R26`.
- Plan reference: P5-R26. Plan basis: Where the plan puts the rule in force:
- Plan 2361-2367 (P5-R26): standardize parent-before-request locking for party reassignment "and other existing request commands"; states that session authentication locks the parent through lockParentForShare.
- Status line 62: says party command lock ordering is complete.
- Plan 1476-1487 (P3-T4): the parent-lock and recheck contract that later commands must call.
- Plan 630-641 (Architectural Decision 32): every command rechecks or locks the parent [truncated]

Current state:

No-auth contact-proof verification still takes its locks in the wrong order: the ShareLink row first, then the parent Exchange row.
- POST no-auth/information-request-access-links/sessions calls InformationRequestContactProofService.verifyChallenge. That method is @Transactional (with dontRollbackOn ContactProofInvalidException) and is its own transaction boundary.
- It first takes a PESSIMISTIC_WRITE lock on the bootstrap ShareLink, via resolveBootstrapLink(lockLink=true) and then ShareLinkRepository.findByTokenHashForUpdate.
- It updates the OTP fields and usedCount, then calls RequestAccessSessionService.issue.
- issue() locks the parent Exchange through RequestAccessSessionRepository.lockParentForShare, which runs entityManager.refresh(parent, PESSIMISTIC_WRITE).

Party reassignment and revocation take the same two rows in the opposite order.
- reassignMutation, revokeMutation and revokeForOwnershipChange all call lockPartyMutationRequest. It locks the Exchange (lockParentExchangeOf), then the request, then the party.
- They then call InformationRequestBootstrapShareLinkService.revokeAllForShare. That revokes sessions and sets the same ShareLink rows to REVOKED; the UPDATE on share_link happens at flush.

How the deadlock happens:
- A verification locks link L and waits for Exchange E.
- A reassignment or revocation holds E, R and P, and its UPDATE on L waits for the verification.
- PostgreSQL detects the cycle and aborts one transaction with 40P01. The resource layer turns that into a generic error for that command.

Where the rule is not affected:
- Correctness is [truncated]

Evidence:

Verification path (ShareLink locked first, Exchange second):
- src/main/kotlin/com/docuhyphen/app/api/service/informationrequest/InformationRequestContactProofService.kt:71-120. verifyChallenge takes the ShareLink lock at line 78 via resolveBootstrapLink(lockLink=true, ...), updates the ShareLink at line 108, and calls requestAccessSessionService.issue at line 114.
- Same file, lines 122-132: resolveBootstrapLink uses shareLinkRepository.findByTokenHashForUpdate when lockLink is true.
- src/main/kotlin/com/docuhyphen/app/api/repository/exchange/ShareLinkRepository.kt:23-31: findByTokenHashForUpdate uses setLockMode(PESSIMISTIC_WRITE).
- src/main/kotlin/com/docuhyphen/app/api/service/informationrequest/RequestAccessSessionService.kt:36: issue() calls sessionRepository.lockParentForShare(shareLink.shareId).
- src/main/kotlin/com/docuhyphen/app/api/repository/informationrequest/RequestAccessSessionRepository.kt:12-25: lockParentForShare runs entityManager.refresh(parent, [truncated]

Fix outline:

Scope: small backend change, no migration needed (next free stays V151), no frontend or help-doc change (no user-visible behavior change).

1. InformationRequestContactProofService.verifyChallenge
   - Look up the bootstrap ShareLink without a lock (shareLinkRepository.findByTokenHash) just to get its shareId.
   - Lock the parent Exchange first. Use a new public RequestAccessSessionService method (for example lockParentForShare(shareId), wrapping RequestAccessSessionRepository.lockParentForShare) so the service does not reach into another service's repository.
   - Then lock the ShareLink: findByTokenHashForUpdate, or entityManager.refresh with PESSIMISTIC_WRITE.
   - Re-run the resolveBootstrapLink checks on the locked row: mode, REVOKED/EXPIRED, maxUses, active party.
   - Keep the existing issue() call. Its lockParentForShare call then re-locks a row the same transaction already holds, which is harmless.
   - Split resolveBootstrapLink into a no-lock "find" and a "lock and validate" step so InformationRequestNoAuthReadAccessService keeps its non-locking read.

2. issueChallenge
   - Optionally apply the same parent-first order, for consistency with the standard.

3. Unit test
   - In InformationRequestContactProofServiceTest, use Mockito inOrder to prove the parent lock comes before the ShareLink lock.

4. Real CDI/PostgreSQL concurrency test
   - Add it to InformationRequestPartyConcurrencyTransactionTest or InformationRequestContactProofTransactionTest.
   - Use latches so a verification and a party reassignment or revocation of the same party overlap.
   - Assert [truncated]

### GA-031: Participant registration upgrade endpoint has no frontend caller

- Severity: medium. Verification: confirmed. Fix size: M. Audit key: `G074-P4-T5`.
- Plan reference: P4-T5. Plan basis: Where the requirement comes from:
- Plan 1854-1858 (P4-T4): on a later verified registration upgrade, persist a `ParticipantAccountLink`, grant an equivalent Share, and revoke the bootstrap links and sessions.
- Plan 1864-1875 (P4-T5): only requires the REST resource. That part is met, so P4-T5 on its own terms is done.
- Plan 2011-2012 and 2040-2041 (Phase 4 tests and exit): later registration upgrades future access.
- Plan 3499-3503 (Phase 10 goal): integrate the feature-switched capability [truncated]

Current state:

The backend registration upgrade is fully built and tested. `InformationRequestParticipantAccountLinkResource` handles POST /information-requests/{id}/participant-account-links. It delegates to `InformationRequestParticipantAccountUpgradeService`, which persists a `ParticipantAccountLink`, grants the App User an equivalent Share and revokes the bootstrap links and sessions. Nothing in the web app calls it:
- No service function, TS DTO type or component uses the endpoint.
- The no-auth respondent flow keeps `sessionToken` from `InformationRequestAccessSessionDto` but throws away `sessionId`, which the request body requires.
- The `/nir` respondent workspace offers no "link to my account" or "sign in to continue" action.

As a result, a party who first answered through an access link can never turn that access into access on their own account. The help article `informationRequestAccessArticle.tsx` ("Signed-in parties and groups", lines 27-34) says "If the assigned party later signs in with a verified linked account, that account can read and respond". The product gives no way to create such a link. The only way to reach the capability is to call the REST API by hand.

Evidence:

Backend exists:
- src/main/kotlin/com/docuhyphen/app/api/resource/informationrequest/InformationRequestParticipantAccountLinkResource.kt:46-90. It is `@Path("/information-requests/{id}/participant-account-links")` with a POST. It needs a bearer App User, `If-Match`, `Idempotency-Key`, the `X-Request-Session-Token` header and a body `{sessionId}`.
- src/main/kotlin/com/docuhyphen/app/api/service/informationrequest/InformationRequestParticipantAccountUpgradeService.kt:59-165. The linked account's email must match the participant's (`PARTICIPANT_ACCOUNT_EMAIL_MISMATCH`); a participant linked to a different account is refused (`PARTICIPANT_ACCOUNT_ALREADY_LINKED`); the service grants a USER Share and revokes the bootstrap links and sessions.
- The DTO is in src/main/kotlin/com/docuhyphen/app/api/model/dto/InformationRequestParticipantAccountUpgradeDtos.kt.
- Backend tests exist: src/test/kotlin/.../resource/informationrequest/InformationRequestParticipantAccountLinkResourceContractTest.kt [truncated]

Fix outline:

No migration is needed: V107 already created `participant_account_link`.

1. `web-app/src/app/models/models.tsx`: add `UpgradeInformationRequestParticipantAccountRequest {sessionId: string}` and `InformationRequestParticipantAccountUpgradeDto` (fields: participantAccountLinkId, participantId, appUserId, linkedAt, grantedShareId, grantedRoleName).

2. `web-app/src/services/informationRequestRuntimeService.ts`:
   - Save and read the session id next to the session token (for example under the key `information-request-session-id:${requestId}`). Clear it wherever the token is cleared, including `storeInformationRequestAccessToken`.
   - Add `linkInformationRequestParticipantAccount(requestId, sessionId, partyETag, idempotencyKey)`. It POSTs `{sessionId}` to `/information-requests/${requestId}/participant-account-links` with the `If-Match`, `Idempotency-Key` and `X-Request-Session-Token` headers. `apiClient` adds the bearer token.

3. `useInformationRequestRespondentWorkspace.ts`: save `session.sessionId` after verification.

4. New component folder `respondent-workspace/account-link-offer/` (`AccountLinkOffer.tsx` and `AccountLinkOfferStyles.tsx`):
   - It shows in the `/nir` no-auth workspace after verification.
   - When the viewer is signed in (AuthContext), show a circular primary "Link to my account" button. It reads the caller's own party `partyETag` from the no-auth parties listing, then calls the new service.
   - On success, clear the no-auth tokens and navigate to `/information-requests/:requestId/respond`.
   - Map the errors: `PARTICIPANT_ACCOUNT_EMAIL_MISMATCH`, [truncated]

Decision needed. Recommended default: (a) An explicit "Link to my account" action in the no-auth workspace after contact verification, with a sign-in redirect.

### GA-032: Bootstrap links can be issued to USER-held parties, so a no-auth session acts as an App User principal

- Severity: medium. Verification: confirmed. Fix size: M. Audit key: `G075-P4-T4`.
- Plan reference: P4-T4. Plan basis: - Line 1773: the Phase 4 goal lists "registered recipients" separately from "unregistered magic-link respondents".
- Lines 531-537: decision 24 says contact proof creates the session "used to authorize as the stable participant PrincipalRef".
- Lines 1838-1856 (P4-T4): bind the link to the "canonical participant principal", and a verified registration upgrade revokes bootstrap links. Both imply a bootstrap holder is not yet an App User.
- Lines 2036-2037: exit criterion "Every no-auth actor [truncated]

Current state:

No layer checks the principal kind of a bootstrap (no-auth) access link or its session. InformationRequestBootstrapShareLinkService.issueMutation refuses a link only when the party is inactive, is a SUBJECT, or has no Share. So a party held by a registered App User (PrincipalKind.USER) can get a VERIFICATION_BOOTSTRAP link, and the author UI offers "Create link" for it. InformationRequestContactProofService deliberately sends the OTP to an App User's email (resolvePartyContactEmail has a USER branch, and a test covers it). verifyChallenge then mints a RequestAccessSession bound to PrincipalRef(USER, appUserId). InformationRequestNoAuthReadAccessService and InformationRequestAccessContextFactory.fromBootstrapSession pass that USER principal on unchanged, with a bare AuthorizationContext(sessionRef=...). So after only an email OTP, the no-auth caller is authorized as the registered App User, and never goes through that user's sign-in or MFA.

On the request the session is bound to, the caller gets everything that user holds:
- the user's other party Shares on that request;
- Exchange grants inherited through InformationRequestParentGrantInheritancePolicy (READ, CANCEL, ADMIN, CREATE).

It also makes attestation wrong. InformationRequestSubmissionAttestationService.strengthOf maps any USER principal to ACCOUNT_SIGN_IN. So a no-auth email-OTP session passes an attestation policy whose minimum is ACCOUNT_SIGN_IN, and the attestation is recorded as ACCOUNT_SIGN_IN.

The registration-upgrade path already accepts only PARTICIPANT-held parties, which shows the rest of the design [truncated]

Evidence:

- src/main/kotlin/com/docuhyphen/app/api/service/informationrequest/InformationRequestBootstrapShareLinkService.kt:145-156: issue checks only active, roleKey != SUBJECT and shareId != null. It never checks principalKind. replaceMutation (283-296) and rotateMutation (209-231) have no kind check either.
- src/main/kotlin/com/docuhyphen/app/api/service/informationrequest/InformationRequestContactProofService.kt:206-214: resolvePartyContactEmail has a PrincipalKind.USER branch that uses appUserService.getById(...).email.
- Same file, 110-119: verifyChallenge builds PrincipalRef(party.principalKind, party.principalId) and issues the session with verificationStrength EMAIL_OTP.
- Same file, 169-177: resolveBootstrapLink checks only that the party is active.
- src/main/kotlin/com/docuhyphen/app/api/service/informationrequest/InformationRequestNoAuthReadAccessService.kt:25-29: builds the participant from party.principalKind without restriction.
- [truncated]

Fix outline:

1. InformationRequestBootstrapShareLinkService.issueMutation: after the SUBJECT check, refuse when party.principalKind != PrincipalKind.PARTICIPANT. Use a new stable code, InformationRequestErrorCatalog.ACCESS_LINK_PARTY_NOT_ELIGIBLE ("INFORMATION_REQUEST_ACCESS_LINK_PARTY_NOT_ELIGIBLE"), with a message pointing the party to the authenticated surface. Register it in the catalog list (around line 358) and in the existing HTTP status mapping. Apply the same guard in resolveBootstrapShareLink so rotate, replace and revoke also refuse. Revoke should arguably still be allowed so existing links can be cleaned up.
2. InformationRequestContactProofService:
   - resolveBootstrapLink rejects a non-PARTICIPANT party with ACCESS_LINK_INVALID.
   - Remove the USER branch of resolvePartyContactEmail and the AppUserService dependency.
   - verifyChallenge builds PrincipalRef(PARTICIPANT, id) only.
3. As a second line of defense, require participant.kind == PARTICIPANT in RequestAccessSessionService.issue and in InformationRequestAccessContextFactory.fromBootstrapSession (throw ForbiddenException).
4. Optional hardening: in InformationRequestSubmissionAttestationService.strengthOf, return VERIFIED_CONTACT whenever the access came from a request access session, not only by principal kind.
5. Migration V151 (optional but cheap): ALTER TABLE request_access_session ADD CONSTRAINT ck_request_access_session_participant_kind CHECK (participant_principal_kind = 'PARTICIPANT'). No backfill or compatibility code, per the Development-Stage Constraint.
6. Frontend: in PartyRow.tsx, set `acting` only [truncated]

### GA-033: Link command expected revision never advances, so If-Match cannot detect concurrent link changes

- Severity: medium. Verification: confirmed. Fix size: M. Audit key: `G076-P4-T4`.
- Plan reference: P4-T4. Plan basis: Plan 1857-1858 (bootstrap ShareLink issue, rotation, replacement, revocation and registration upgrade use Command Receipts and expected aggregate revisions). 2018-2019 (replay, fingerprint-conflict, expected-revision and parallel-race tests). 820-822 (require If-Match, 412 with a stable code when stale, return the current ETag after every successful mutation). 626-629 (missing preconditions return 428, stale revisions return 412). 1492-1493 (strong ETag from the persisted aggregate or party [truncated]

Current state:

All four owner-facing access-link commands (issue, rotate, replace, revoke) and the respondent registration upgrade require If-Match. They check it against InformationRequestETag.partyOf(party), which is party.id plus party.partyRevision. None of these five mutations increments party.partyRevision, request.partyRevision, request.aggregateRevision or any link-level revision. The only places that bump party revisions are party assignment, reassignment and revocation in InformationRequestPartyService. Because of this the ETag a caller holds never goes stale because of a link change. Two administrators holding the same party ETag can each, one after the other, issue a link (leaving two active bootstrap links for the party), rotate the same link (the second rotation silently invalidates the token the first admin just handed out), or revoke (the second gets a 200 no-op). None of them gets a 412. Replacing an already-replaced link is still refused, but only by the ACCESS_LINK_REVOKED state check (409), not by the precondition. Row locks do serialize truly simultaneous transactions: issue locks the party row and rotate/replace/revoke lock the share_link row. But rotate/replace/revoke read the party through an unlocked findByShareId, and nothing catches a stale writer. Successful link mutations also return no current ETag: there is no ETag header, and neither the DTO nor the issued DTO carries a partyETag. That breaks the rule to return the current ETag after every mutation. On tests: the link and upgrade service tests only check that a missing precondition [truncated]

Evidence:

src/main/kotlin/com/docuhyphen/app/api/service/informationrequest/InformationRequestETag.kt:26-27 (partyOf = RevisionETag.of(party.id, party.partyRevision)).
src/main/kotlin/com/docuhyphen/app/api/service/informationrequest/InformationRequestBootstrapShareLinkService.kt:145-148 (issue locks the party and checks partyOf) and 158-172 (saves a new ShareLink, no revision bump). Lines 213-231: rotate checks partyOf, then changes only share_link fields (tokenHash, rotationCount, and so on), no party or request bump. Lines 272-296: replace, no bump. Lines 339-351: revoke, no bump, and an already-revoked link returns as a no-op. Lines 354-367: resolveBootstrapShareLink locks share_link but reads the party via the unlocked partyRepository.findByShareId.
src/main/kotlin/com/docuhyphen/app/api/service/informationrequest/InformationRequestParticipantAccountUpgradeService.kt:100-103 (checks partyOf) and 158-162 (revokes links), no revision bump.
Grep across src/main for partyRevision increments: [truncated]

Fix outline:

1) InformationRequestBootstrapShareLinkService:
   - In issueMutation, after the precondition check on the locked party, set party.partyRevision += 1 and update updatedAt, then call partyRepository.update(party).
   - In rotateMutation, replaceMutation and revokeMutation, change resolveBootstrapShareLink to keep lock order the same as InformationRequestPartyService (request, then party, then share_link). Read the link's shareId unlocked, lock the party with partyRepository.findByIdForUpdate, check the precondition against partyOf(party), then lock the share_link with findByIdForUpdate. After the mutation, bump party.partyRevision.
   - Skip the bump on revoke's already-revoked no-op path.
   - Leave request.partyRevision alone so partiesETag-guarded party assignment is not spuriously refused.
2) InformationRequestParticipantAccountUpgradeService.upgradeMutation: lock the party row and bump party.partyRevision when it revokes the bootstrap links. Provenance fields stay unchanged.
3) Return the new ETag:
   - Add partyETag to InformationRequestBootstrapShareLinkIssuance and to the revoke result, or return a small result model.
   - Have InformationRequestAccessLinkResource set the ETag response header, and optionally add partyETag to InformationRequestAccessLinkIssuedDto/InformationRequestAccessLinkDto in model/dto.
   - Update the web-app models, and informationRequestAuthoringService tests if the DTO changes. The frontend already reloads after each command.
4) Tests:
   - In InformationRequestBootstrapShareLinkServiceTest, for issue, rotate, replace and revoke: a [truncated]

### GA-034: Trusted-organization party selection command is unreachable (no REST endpoint, no UI)

- Severity: medium. Verification: confirmed. Fix size: M. Audit key: `G087-P3-T8`.
- Plan reference: P3-T8. Plan basis: Where the requirement comes from:
- Plan 1536-1538 (P3-T8): organization-owned trusted selections reuse `TrustedRecipientValidationService`, party policy, recipient-selection attestation and trusted group reconciliation.
- 646-653 (Architectural Decision 34): request recipient selection and trusted group expansion reuse the trust services, with a tested matrix for new assignment.
- 2361-2364 (P5-R26): lists "trusted recipient assignment" as a production command path.
- 3994-3998 (Phase 12 [truncated]

Current state:

The trust-governed way to add a request party is built but nothing in the product can reach it.

1. The dedicated command has no production caller. `InformationRequestPartyService.assignTrustedRecipientSelection` resolves a TRUSTED_PERSON or TRUSTED_GROUP selection through `ExchangeRecipientSelectionResolver` and binds it to the parent Exchange's accepted recipient. Only `InformationRequestPartyServiceTest` calls it. No resource or other service does.

2. The only REST route is the generic one, and clients cannot use it. `POST /information-requests/{id}/parties` accepts `userId` or `principalGroupId` plus an optional `exchangeRecipientId`. When `exchangeRecipientId` is given, `requireAssignableRecipient` runs the full trust path: the suspension 409, attestation revalidation through `TrustedRecipientValidationService`, and trusted group reconciliation. But no owner-facing endpoint returns an ExchangeRecipient id. `ExchangeRecipientDtoTransformer` is unused, and the frontend has no ExchangeRecipient DTO. So an honest client cannot build that request.

3. Omitting the binding skips the trust checks. A `userId` or `principalGroupId` sent without `exchangeRecipientId` is only checked for principal kind.

4. The author UI never offers a trusted path. The add-party dialog has two holders:
   - "email", which calls `assignExternalParticipant`. It creates an owner-scoped External Participant with no trust check, even when the person belongs to a trusted organization.
   - "group", which lists only the caller's own organization groups or personal groups.

   The only `userId` the UI [truncated]

Evidence:

- `src/main/kotlin/com/docuhyphen/app/api/service/informationrequest/InformationRequestPartyService.kt`:
  - 144-166: `assignTrustedRecipientSelection`.
  - 430-477: its mutation, which resolves the trusted selection, requires a direct Exchange Share, then calls `requireAssignableRecipient`.
  - 816-855: `resolveTrustedSelection`.
  - 697-717: `requireAssignableRecipient`, which raises TRUST_SUSPENDED and TRUSTED_RECIPIENT_UNAVAILABLE.
  - 289-297: generic assign only calls `requireAssignableRecipient` when `exchangeRecipientId` is non-null. Otherwise it only runs `requireSupportedActingPrincipal`, a kind check.
- Grep for `assignTrustedRecipientSelection` and `AssignTrustedRecipientInformationRequestPartyCommand` across src/main finds only the service. The only other hit is `InformationRequestPartyServiceTest.kt:369,470,553`.
- `src/main/kotlin/com/docuhyphen/app/api/resource/informationrequest/InformationRequestPartyResource.kt`:
  - 46-104: `assign` dispatches to [truncated]

Fix outline:

No migration is needed (V151 stays free). Keep exactly one trust-bound path and delete the other; this is not compatibility code.

Backend:
1. Add a query service and endpoint that list the parent Exchange's ACCEPTED trusted recipients for party selection. Suggested endpoint: `GET /information-requests/{id}/trusted-recipient-candidates`, backed by a new `InformationRequestTrustedRecipientCandidateQueryService` in `service/informationrequest/`. It needs a new DTO in `model/dto` with `exchangeRecipientId`, `selectionType`, principal kind and id, a label, the target organization name and `trustSuspended`. Map it in a dedicated mapper class. Authorize it with `INFORMATION_REQUEST_MANAGE_PARTIES` and return an empty list for personally owned requests.
2. Pick one assignment path:
   - Option A (preferred): the client sends `userId` or `principalGroupId` plus `exchangeRecipientId` to the existing POST. Delete `assignTrustedRecipientSelection`, `assignTrustedRecipientSelectionMutation`, `resolveTrustedSelection`, `trustedSelectionFingerprint`, `ASSIGN_TRUSTED_RECIPIENT_OPERATION`, the `ExchangeRecipientSelectionResolver` dependency and their tests.
   - Option B: add a `trustedSelection` field to `AssignInformationRequestPartyRequest` and dispatch it in `InformationRequestPartyResource.assign` to `assignTrustedRecipientSelection`, with the initiator AppUser taken from the access context.
3. Close the omission bypass in `assignMutation` and `reassignMutation`: when a USER or PRINCIPAL_GROUP principal holds a trusted ExchangeRecipient on the parent Exchange [truncated]

Decision needed. Recommended default: (b) Expose the trusted person or published group selection as a REST option and UI picker. Refuse email entry for a verified member of a trusted organization with guidance to use the trusted picker.

### GA-035: Owner history reads after Exchange termination are retained only for personally owned requests

- Severity: medium. Verification: confirmed. Fix size: M. Audit key: `G089-P3-T4`.
- Plan reference: P3-T4. Plan basis: P3-T4 at plan lines 1476-1487 ("REJECTED and RESCINDED revoke external access by default while preserving owner history, and deletion ... retaining authorized owner or recovery reads"). Architectural Decision 32 at lines 630-641 ("Rejected or rescinded history remains visible to authorized owners and administrators ... Deletion ... permits only authorized owner or recovery access"). P5-R02 at lines 2255-2258 ("permitted historical reads ... on every access surface"). Phase 9 decision 14 at [truncated]

Current state:

The Exchange owner can see requests only through the capabilities its Exchange OWNER Share passes down (INFORMATION_REQUEST_CREATE/READ/CANCEL/ADMIN via InformationRequestParentGrantInheritancePolicy). ExchangeUpdateService revokes every active Exchange Share, OWNER included, on ENDED, REJECTED, RESCINDED and delete. InformationRequestParentLifecycleService.apply runs just before that revocation, and it only restores a read path when request.ownerUserId != null, meaning personally owned requests. It does this by granting the owner a request-scoped SUBJECT Share, which carries INFORMATION_REQUEST_ATTEST as well as READ. Terminal parent policy blocks every mutation, so the extra ATTEST is not exploitable today, but it is the wrong role for the job. Organization-owned requests always have ownerUserId = null (V30 exchange owner check plus the creation services copying exchange.ownerUserId), so the organization Exchange's OWNER user (the initiator) gets nothing retained. Organization roles also carry no INFORMATION_REQUEST_READ, and plan decision 14 says giving them request administration is a user decision. Result: after ENDED, REJECTED, RESCINDED or deletion, that user is refused INFORMATION_REQUEST_VIEW (and listing, requirement, evidence, operations and export reads) unless they separately hold a request-scoped DECISION_MAKER (ADMIN) Share. Termination never revokes request-scoped Shares, and the frontend tries to name the author Decision Maker right after creation, so in practice the gap covers these cases: a request created through REST or other non-UI paths with no [truncated]

Evidence:

InformationRequestParentLifecycleService.kt:29 (`request.ownerUserId?.let { shares.retainInformationRequestOwnerRead(request.id, it) }`, the only retention). ShareService.kt:521-528 (grants InformationRequestShareRoleKey.SUBJECT). RoleCapabilities.kt:251-254 (SUBJECT = READ + ATTEST), 201-204 (Exchange OWNER Share carries IR CREATE/READ/CANCEL/ADMIN), 113-117 and 136-162 (organization roles have no INFORMATION_REQUEST_READ). InformationRequestParentGrantInheritancePolicy.kt:24-29. DefaultAuthorizationService.kt:365-370 and 429-447 (only ACTIVE Shares count, including inherited parent grants). ShareRepository.kt:16-25 (findActiveByResource returns every active Share, OWNER included). ExchangeUpdateService.kt:249/253 (REJECTED via workflow), 328/335-341 (ENDED/REJECTED/RESCINDED), 491/522 (rescind), 562/572 (delete), 761/765 (lifecycle apply always precedes revokeAllForResource(EXCHANGE)). ExchangeInitiationService.kt:483-493 (the initiator USER gets the OWNER Share, also for [truncated]

Fix outline:

1. Add a read-only request Share role, for example InformationRequestShareRoleKey.RECORD_OWNER, in model/entity/RoleNames.kt. Map it in RoleCapabilities.INFORMATION_REQUEST_SHARE to INFORMATION_REQUEST_READ, INFORMATION_REQUEST_EVIDENCE_READ and a new Capability.INFORMATION_REQUEST_RETAINED_OWNER_READ (Capability.kt). Give it no ATTEST, RESPOND, WRITE or ADMIN. 2. Migration V151__information_request_record_owner_share_role.sql: drop and re-add share_role_name_check so the INFORMATION_REQUEST branch also allows 'RECORD_OWNER'. 3. ShareService: replace retainInformationRequestOwnerRead(requestId, ownerUserId) with retainInformationRequestOwnerReads(exchangeId, requestId). It reads the Exchange's ACTIVE OWNER Shares (USER principals) before they are revoked and grants RECORD_OWNER on the request, idempotently. Remove the SUBJECT grant. 4. InformationRequestParentLifecycleService.apply: call the new method for every request whatever its ownerType, since the ordering already guarantees OWNER Shares are still active. 5. InformationRequestParentPolicy.evaluate: count INFORMATION_REQUEST_RETAINED_OWNER_READ toward isOwner for readActions only. Mutations keep the existing terminal-parent deny. 6. Tests: add a real-grant test with DefaultAuthorizationService and an in-memory or Quarkus ShareRepository. Cover organization-owned and personal Exchanges, with and without a Decision Maker party, across ENDED, REJECTED, RESCINDED and deleted. Assert that the owner can still VIEW, list, requirement-view and evidence-view, that every mutation and ATTEST is denied, and that ordinary parties [truncated]

### GA-036: Party revocation on organization membership removal records no party history, audit, or domain event

- Severity: medium. Verification: confirmed. Fix size: S. Audit key: `G091-P3-T8`.
- Plan reference: P3-T8. Plan basis: - Plan 1415-1419 (P3-T2): every later mutation passes an explicit PrincipalRef and audit owner and commits audit plus event with the mutation.
- Plan 1539-1544 (P3-T8): each party revocation revokes the matching Share in the same transaction. The ownership-change path does satisfy this part.
- Plan 3318-3321 (Phase 9 decision 13) and 3440-3444 (P9-T11): organization member removal consults the ownership policy about that member's request parties. This extension point is what added the revoke [truncated]

Current state:

When an organization admin removes a member, OrganizationMembershipService.removeMember calls InformationRequestOwnershipChangeService.memberRemoved. For each of that user's active parties on requests the organization owns, it asks RecordOwnershipChangePolicy for a decision. If the decision is REVOKE, it calls InformationRequestPartyService.revokeForOwnershipChange. That method runs the private revokeParty step: it deactivates the party, sets revokedAt, bumps both revisions, returns reserved recipient capacity, revokes bootstrap links and the party Share, then returns. It never calls recordPartyHistory or transitionHistory.record. So the revocation writes no information_request_transition row (REVOKE_PARTY), no information_request.party.revoke audit event and no Information Request domain event on the per-request ordered outbox. The only trace is ShareService.markRevoked's SHARE_REVOKE audit, which is best-effort (failures are caught and logged) and is written only when the party has a Share. The author-initiated revoke goes through revokeMutation, which does record history, audit and event.

Two things narrow the claim:
1. The path only runs when the non-default setting app.records.member-removal-party-decision=REVOKE is used. The shipped default is RETAIN, and then nothing is revoked.
2. The secondary point about materializeBlueprintDefaultParties and materializeSubjectParties is overstated. Those run inside creation mutations that are already audited (CREATE_DRAFT via recordDraftCreation in InformationRequestBlueprintInstantiationService, and CREATE_SUCCESSOR in [truncated]

Evidence:

- InformationRequestPartyService.kt:579-586: revokeForOwnershipChange locks, loads the party and returns revokeParty(request, party, OWNERSHIP_CHANGE_ACTOR). There is no history call.
- InformationRequestPartyService.kt:588-609: revokeParty mutates the party and Share, bumps request.partyRevision and does not record history.
- InformationRequestPartyService.kt:552-577: revokeMutation calls revokeParty and then recordPartyHistory(..., REVOKE_PARTY, ...). This is the only revoke path that audits.
- InformationRequestPartyService.kt:778-797: recordPartyHistory delegates to transitionHistory.record.
- InformationRequestTransitionHistoryService.kt:72-124: recordOne writes the transition row, then auditRecorder.record, then domainEventPublisher.publish (transactional sink).
- InformationRequestTransitionHistoryService.kt:211: REVOKE_PARTY maps to INFORMATION_REQUEST_PARTY_REVOKE.
- AuditEventType.kt:220: information_request.party.revoke exists.
- [truncated]

Fix outline:

No migration is needed: REVOKE_PARTY and information_request.party.revoke already exist, and V151 stays free.

1. In InformationRequestPartyService.revokeForOwnershipChange, after revokeParty, call recordPartyHistory(request, saved, InformationRequestMutation.REVOKE_PARTY, actor, key). Use a deterministic key such as "member-removal|$organizationId|$appUserId" so a retried removal cannot duplicate the row. Pass a stable reasonCode (for example ORGANIZATION_MEMBER_REMOVED) through InformationRequestTransitionHistoryCommand.reasonCode. This needs a small overload or extra parameters on recordPartyHistory, since it does not accept a reasonCode today.
2. Change the revokeForOwnershipChange signature to take organizationId and appUserId, and ideally the removing admin's PrincipalRef. Thread the admin principal from OrganizationAppUserService through OrganizationMembershipService.removeMember into InformationRequestOwnershipChangeService.memberRemoved, so the audit actor is the real admin rather than the nil-UUID SERVICE_ACCOUNT. If threading is out of scope, keep OWNERSHIP_CHANGE_ACTOR.
3. Optionally, record ASSIGN_PARTY history for each party created by materializeBlueprintDefaultParties and materializeSubjectParties, with keys derived from the creation command's idempotency key. This is lower priority because those runs are already covered by CREATE_DRAFT and CREATE_SUCCESSOR audits.
4. Tests: extend InformationRequestOwnershipChangeTransactionTest's revoking-policy case to assert:
   - exactly one information_request_transition row with mutation REVOKE_PARTY, the party_id [truncated]

### GA-037: Request transitions never record their idempotency key or command receipt

- Severity: medium. Verification: confirmed. Fix size: M. Audit key: `G100-CDM-RequestTransition`.
- Plan reference: CDM-RequestTransition. Plan basis: Plan line 719 (domain model table: RequestTransition is an "Immutable state transition with actor, reason, time, and idempotency key"). Supporting lines: 626-628 (decision 31: shared CommandReceipt, atomic with the mutation, audit, and event), 817-819 (idempotency key required for retryable transitions), 1326-1328 (Phase 3 goal: append-only transition history plus client-command safety), 1469-1475 (P3-T3 CommandReceipt), 1488-1489 (P3-T5 transition history entities), 4344-4345 (V96 record), [truncated]

Current state:

The immutable transition row (information_request_transition / InformationRequestTransition) records neither an idempotency key nor its command receipt. The table has no idempotency_key column at all: V96 created it without one, and no later migration adds one (V101 adds party_id; V133/V137/V139/V144/V147/V148 only rewrite the CHECK constraints). The command_receipt_id column exists (V96:317, FK to command_receipt, not deferrable) and is mapped (InformationRequestTransition.kt:56-57). InformationRequestTransitionHistoryCommand.commandReceiptId defaults to null (InformationRequestTransitionHistoryService.kt:33) and is copied at line 86, but none of the 35 production files that build InformationRequestTransitionHistoryCommand sets it. Every transition row therefore has command_receipt_id NULL. The client idempotency key only reaches the audit outbox. For lifecycle commands it is wrapped as "information_request.lifecycle|MUTATION|requestId|key" (InformationRequestLifecycleService.kt:239, 410-411), and the audit row points back through businessTransactionId = transition.id. The auto-recorded START_RESPONSE transition uses a synthetic key with no client component. Wiring the column is also blocked by the current design. CommandReceiptService.runOnce (service/command/CommandReceiptService.kt) runs the mutation first and only then creates the CommandReceipt with a fresh UUID, so the receipt id does not exist yet when the transition is written. The FK is not deferrable, and nextSequenceNumber's JPQL query auto-flushes the transition insert, so the transition row reaches the [truncated]

Evidence:

src/main/resources/db/migration/V96__information_request_runtime_persistence.sql:306-344: information_request_transition has actor_kind, actor_id, reason_code, command_receipt_id, occurred_at, and no idempotency_key column. Grep of all migrations for information_request_transition shows only V101 (party_id) plus constraint rewrites, and no idempotency column. Grep of 'command_receipt' in migrations: only V95 (table) and V96:317 (the FK, not DEFERRABLE).
src/main/kotlin/com/docuhyphen/app/api/model/entity/InformationRequestTransition.kt:56-57 maps commandReceiptId, and there is no idempotencyKey property.
src/main/kotlin/com/docuhyphen/app/api/service/informationrequest/InformationRequestTransitionHistoryService.kt:33 (commandReceiptId: UUID? = null), :34 (idempotencyKey used only for audit), :86 (copies the null receipt id), :104 (idempotencyKey = command.idempotencyKey on AuditEventDraft), :105 (businessTransactionId = transition.id), :62 (START_RESPONSE synthetic key [truncated]

Fix outline:

1. Migration V151__information_request_transition_command_identity.sql: add `idempotency_key VARCHAR(256)` to information_request_transition (NOT NULL is fine because there are no production users, so no backfill or compatibility is needed; local and test data can be truncated or backfilled from the audit outbox by business_transaction_id). Add a CHECK that it is not blank. Replace the V96 command_receipt_id FK with one declared DEFERRABLE INITIALLY DEFERRED, because the transition is flushed before the receipt row. Optionally add a unique index on (information_request_id, command_receipt_id, sequence_number) or an index on command_receipt_id for lookup.
2. InformationRequestTransition.kt: add `@Column(name = "idempotency_key", nullable = false, length = 256) lateinit var idempotencyKey: String`.
3. CommandReceiptService.runOnce: create the receipt id before running the mutation and pass it in. Either change the signature to `mutation: (UUID) -> CommandMutationResult<T>`, or have the service open a transaction-scoped CommandReceiptContext bean holding the current receipt id and idempotency key. Then use that id when inserting the receipt.
4. InformationRequestTransitionHistoryService.recordOne: write `idempotencyKey` to the row. Take the key from the command, and fall back to a deterministic system key per mutation and request, as already done for START_RESPONSE. Take `commandReceiptId` from the command or from the transaction-scoped context. Using the context avoids editing all 35 builder call sites. Otherwise update each client-command caller (lifecycle, party, [truncated]

### GA-038: Validation errors carry no Requirement ID or field path, and Field validation errors drop the reason code and field key

- Severity: medium. Verification: partial. Fix size: M. Audit key: `G102-REST-StableErrorCodesRequirementIdsFieldPaths`.
- Plan reference: REST-StableErrorCodesRequirementIdsFieldPaths. Plan basis: **In force**
- Line 823: "Use stable error codes, Requirement IDs, and field paths so the client can focus the failing item."
- Line 3583, Phase 10 item 11: "a refusal moves focus to the part it names".

**Narrowing or acknowledgment**
- Lines 3606-3608, Phase 10 "Refinements recorded while implementing" (2026-09-27/28): "where the refusal names a part ... moves focus there ... A response save refusal names no part."

**No supersession found**
Nothing waives line 823 in any of these:
- Status [truncated]

Current state:

The gap is real in the code, but the claim gets two details slightly wrong, and the plan already records the behavior as known.

What is true:
- No error body in the response-save path can name a Requirement, occurrence path or Field. The shared ResponseError has only errorMessage, reasonCode and retryAfterSeconds. InformationRequestLifecycleException has only reasonCode and message.
- PATCH /information-requests/{id}/responses (authenticated and no-auth) sends many patches in one call. When one item fails, the 409 names none of these: its requirementId, its occurrencePath, or the Field involved. This covers RESPONSE_DISPOSITION_NOT_PERMITTED, RESPONSE_NARRATIVE_REQUIRED, FIELD_ENTRY_NOT_BOUND, SUBMISSION_LOCKED, CORRECTION_SCOPE_DENIED, GROUP_OCCURRENCE_REMOVED, a per-patch NOT_FOUND, HIDDEN_RESPONSE_CLEAR_CONFIRMATION_REQUIRED and STRUCTURED_RESPONSE_VALIDATION_FAILED.
- The structured validator actually builds issues that carry requirementId and fieldContractId. The service then flattens them into one joined message string and discards the IDs.
- The frontend matches the backend. It sends every unsaved edit in one PATCH and shows one workspace-level error string, so it cannot focus the item that failed.
- Only three bodies carry item-level identity: the submission refusal (problems with requirementId, requirementKey, occurrencePath, code), the Template refusal (section, requirement, group and stage keys), and the Exchange completion refusal (request IDs only).

What the claim gets wrong:
- It says Field validation errors "drop the reason code". FieldValidationException [truncated]

Evidence:

**Backend**
- `src/main/kotlin/com/docuhyphen/app/api/resource/model/RequestsResponses.kt:10-15`: ResponseError has only errorMessage, reasonCode and retryAfterSeconds.
- `service/informationrequest/InformationRequestLifecycleService.kt:63-66`: the exception holds only reasonCode and message.
- `service/informationrequest/InformationRequestStructuredResponseValidationService.kt`:
  - Lines 29-34: the Issue type has requirementId and fieldContractId.
  - Lines 63-71: the exception is thrown with `issues.joinToString("; ") { it.message }`, so both IDs are discarded.
- `service/informationrequest/InformationRequestResponseDraftService.kt`: none of these throws carries an item ID.
  - Lines 175-186: per-patch NOT_FOUND and GROUP_OCCURRENCE_REMOVED.
  - Lines 347-366: RESPONSE_DISPOSITION_NOT_PERMITTED.
  - Lines 429-441: RESPONSE_NARRATIVE_REQUIRED.
  - Lines 546-551 and 599: HIDDEN_RESPONSE_CLEAR_CONFIRMATION_REQUIRED.
  - Lines 699-711: FIELD_ENTRY_NOT_BOUND. The fieldContractId appears [truncated]

Fix outline:

No migration is needed; V151 stays free.

1. **Refusal payload**
   - Add `InformationRequestResponseRefusalDto` in `model/dto/InformationRequestResponseDtos.kt` with: errorMessage, reasonCode, problems.
   - Each problem is a new `InformationRequestResponseProblemDto` with: requirementId, occurrencePath, fieldContractId?, fieldKey?, code.
   - This mirrors `InformationRequestSubmissionRefusalDto`.
   - Alternatively, add optional requirementId, occurrencePath and fieldPath to the shared ResponseError.

2. **Item-level exception**
   - Add `InformationRequestResponseItemRefusalException : InformationRequestLifecycleException` in `service/informationrequest`, carrying a list of problems.
   - Throw it in `InformationRequestResponseDraftService` from:
     - `requirePermittedDisposition`
     - `requireNarrativeForDisposition` (pass the requirement)
     - `requireEntriesBoundToRequirement`
     - the per-patch NOT_FOUND and GROUP_OCCURRENCE_REMOVED checks
     - the hidden-clear checks
     - `mergeFieldPreconditions`
   - Also throw it from `InformationRequestSubmissionLockService.requireUnlocked`.
   - Change `InformationRequestStructuredResponseValidationService.validate` to pass its issues (which already hold requirementId and fieldContractId) into the exception instead of joining the messages.

3. **Fields layer**
   - Add a stable `reasonCode` and a `fieldContractId` to `FieldValidationException`.
   - Add a small Fields error catalog: FIELD_VALUE_INVALID, FIELD_VALUE_REQUIRED, FIELD_NOT_IN_SCHEMA, FIELD_READ_ONLY.
   - In `SchemaAssignmentService.setValues`, wrap [truncated]

Decision needed. Recommended default: Yes. Add requirementId, occurrencePath and fieldKey to a dedicated Information Request refusal DTO and focus the item in the workspace. Line 823 is authoritative over the Phase 10 note.

### GA-039: Nine Information Request REST resources have no resource contract test

- Severity: medium. Verification: confirmed. Fix size: M. Audit key: `G103-REST-TryCatchAndResourceContractTests`.
- Plan reference: REST-TryCatchAndResourceContractTests. Plan basis: - Plan lines 801-830 (REST and Application-Service Rules). Line 820-821 requires 428 when If-Match is missing, 412 when it is stale, and the current ETag after every mutation. Lines 827-830 say every REST resource method uses return try/catch and that "Resource contract tests must verify delegation and response mapping".
- The resources come from P7-T6 and P7-T7c (lines 2934-2947: successors, lineage, recurrence, refresh rules), P9-T4 (lines 3369-3374: PUT completion-gate), P9-T5 (lines [truncated]

Current state:

The package src/main/kotlin/com/docuhyphen/app/api/resource/informationrequest has 60 resource classes. 25 *ResourceContractTest files under src/test/kotlin/com/docuhyphen/app/api/resource/informationrequest cover 51 of them, since several test files cover more than one resource: RecordResourcesContractTest covers the audit, privacy and restriction resources, and ExternalSourceResourceContractTest covers the generated-output and connector resources. The claim's "other 25 IR resources" is slightly imprecise for that reason, but the gap itself is exact.

Nine resources have no test anywhere in src/test:
- InformationRequestClockResource
- InformationRequestClockPolicyResource
- InformationRequestCompletionGateResource
- InformationRequestNoticeResource
- InformationRequestRecurrenceResource
- InformationRequestRefreshRuleResource
- InformationRequestSuccessorResource
- InformationRequestCarryForwardResource
- InformationRequestNoAuthCarryForwardResource

No test mentions these classes by name, and none mentions their paths ("/clocks", "clock-policies", "completion-gate", "/notices", "/recurrences", "refresh-rules", "/successors", "carry-forwards"). No test mentions the InformationRequestClockEndpoint helper that the two clock resources delegate to. No architecture test scans the resource package reflectively either.

Their services are tested at service level:
- InformationRequestLineageTransactionTest
- InformationRequestExchangeCompletionTransactionTest
- InformationRequestCarryForwardPlannerTest
- The migration contract tests

Nothing checks these endpoints at the [truncated]

Evidence:

- `grep -rlw <ClassName> src/test` returns nothing for all nine classes. Every other resource in the package gets at least one hit, from a contract test under src/test/kotlin/com/docuhyphen/app/api/resource/informationrequest/.
- `grep -rnE '/clocks|clock-policies|completion-gate|/notices|/recurrences|refresh-rules|/successors|carry-forwards' src/test` returns nothing.
- `grep InformationRequestClockEndpoint src/test` returns nothing.

Untested behaviour in each resource:
- InformationRequestClockResource.kt:23 (@Path /information-requests/{id}/clocks). It has GET list plus POST start, pauses, resumptions and extensions, which pass If-Match and Idempotency-Key through to InformationRequestClockEndpoint (lines 125-141).
- InformationRequestCompletionGateResource.kt:23 and :31-51. PUT with CommandPreconditionHeader.required(ifMatch) and an idempotency key, returning 200 with an ETag header.
- InformationRequestSuccessorResource.kt:24, :47-71. POST returns 201 with the successorETag [truncated]

Fix outline:

Tests only. No production change or migration is needed, so V151 stays free. Add contract tests under src/test/kotlin/com/docuhyphen/app/api/resource/informationrequest/, following InformationRequestReminderResourceContractTest. Each test mocks the service(s) and InformationRequestAccessContextFactory, builds the resource directly, and asserts the @Path and verb annotations.

1. InformationRequestClockResourceContractTest
   - Covers list, start, pause, resume and extend through a real InformationRequestClockEndpoint with mocked InformationRequestClockPolicyService and InformationRequestClockService.
   - Asserts the command carries the change kind, the If-Match precondition and the Idempotency-Key.
   - Asserts 400 when the Idempotency-Key is missing, 428 when If-Match is missing, 412 when it is stale, and that the ETag header comes back.
   - Asserts a bad clock id maps to 400.
2. InformationRequestClockPolicyResourceContractTest
   - Covers list, get, define and publishVersion.
   - Asserts the 201 or 200 statuses, the policy DTO mapping and refusal of a null body.
3. InformationRequestCompletionGateResourceContractTest
   - Asserts a null body gives 400, missing If-Match gives 428, a stale one gives 412, and a missing key gives 400.
   - Asserts success returns 200 with the ETag equal to result.requestETag.
   - Asserts the command's gatesExchangeClosure value.
4. InformationRequestLineageResourcesContractTest, which may be one file for the Successor, Recurrence and RefreshRule resources.
   - Asserts POST returns 201 with the successorETag header and that the command [truncated]

### GA-040: Encrypted and corrupt content is detected only for PDFs; password-protected Office or ZIP files and corrupt images pass as conforming

- Severity: medium. Verification: confirmed. Fix size: M. Audit key: `G115-P6-T6b`.
- Plan reference: P6-T6b / P6-T7. Plan basis: In force:
- Plan 2672-2674 (P6-T6b): detected-content inspection must cover "encrypted or corrupt content". This clause stands apart from "PDF page count".
- Plan 2709-2710 and 2720-2722 (P6-T7): corrupt, encrypted and password-protected file behavior. A corrupt file is marked CORRUPT, and a password-protected file carries the blocking CONTENT_ENCRYPTED finding and is never offered for review.
- Plan 2787: the tests to write include corrupt and password-protected cases.
- Plan 2800-2802 (exit [truncated]

Current state:

Only PDFs get checked for encryption and corruption. InformationRequestEvidenceContentInspector.inspect detects the media type with tika-core. For any type other than application/pdf it returns immediately with encrypted=false and corrupt=false. Only PDFs are opened, using PDFBox.

The project has no Tika parser module and no POI dependency. With tika-core alone, detection gives only generic container types. I checked this in the scratchpad against the tika-core 3.1.0 jar: an OLE2 container (the format of password-protected DOCX/XLSX/PPTX and legacy .doc/.xls) is detected as application/x-tika-msoffice. A ZIP with the encryption flag set is still detected as a normal ZIP/OOXML. A truncated PNG or JPEG is still image/png or image/jpeg.

All of these files are recorded as a CONTENT_INSPECTION assessment with outcome INSPECTED. They never get the blocking CONTENT_ENCRYPTED or CONTENT_CORRUPT finding, and never get CORRUPT conformance. What happens next depends on the declared type:
- Declared type matches the detected type (encrypted ZIP sent as application/zip, truncated image sent as image/*, or any file from an API client that declares nothing or application/octet-stream): the file is CONFORMING and can satisfy the Requirement unless the Template restricts CONTENT_TYPE.
- Office file uploaded from a browser: the browser declares the specific Office MIME type, but tika-core detects a generic type. The non-blocking CONTENT_TYPE_MISMATCH makes the file DEFICIENT instead of CONFORMING. Under a DEFICIENCY_REVIEWABLE policy it becomes REVIEWABLE, so a reviewer can accept it. A [truncated]

Evidence:

- src/main/kotlin/com/docuhyphen/app/api/service/informationrequest/InformationRequestEvidenceContentInspector.kt:16-20: `if (detected != PDF) return InformationRequestEvidenceInspectionFacts(detected, pageCount = null, encrypted = false, corrupt = false)`. Lines 22-35: the PDFBox InvalidPasswordException/IOException branches are the only encrypted/corrupt detection anywhere.
- src/main/kotlin/com/docuhyphen/app/api/model/informationrequest/InformationRequestEvidenceMediaTypes.kt:8,18-19: detection uses Tika().detect on the stream only.
- pom.xml:82-84 has pdfbox 3.0.8 and pom.xml:220-222 has tika-core 3.1.0. There is no tika parser module and no POI dependency.
- Tika check run in the scratchpad against tika-core-3.1.0.jar:
  - OLE2 header -> application/x-tika-msoffice
  - OOXML-like zip -> application/x-tika-ooxml
  - zip with the encryption flag set -> application/x-tika-ooxml
  - truncated PNG -> image/png
  - truncated JPEG -> image/jpeg
- InformationRequestEvidenceIntake.kt:59 [truncated]

Fix outline:

No migration is needed: V130 and V133 already allow the ENCRYPTED and CORRUPT values.

1. Split the per-format checks out of InformationRequestEvidenceContentInspector.kt into small inspection helpers under service/informationrequest/, and dispatch on the detected type:
   - PDF: keep the current PDFBox logic.
   - OLE2 (application/x-tika-msoffice): open with POI's POIFSFileSystem. If the root has an "EncryptedPackage" or "EncryptionInfo" entry, return encrypted=true. For legacy binary .doc/.xls/.ppt, opening through POI raises EncryptedDocumentException for a password, which maps to encrypted=true. If the container fails to parse, return corrupt=true.
   - ZIP-based types (application/zip, application/x-tika-ooxml, OOXML/ODF subtypes, epub): stream the entries with ZipInputStream. Bit 0 of the general-purpose flag, or ZipException "encrypted ZIP entry not supported", means encrypted=true. Any other ZipException, EOF, or CRC failure means corrupt=true. Cap the entry count and the total decompressed bytes to guard against zip bombs.
   - Raster images in INLINE_SAFE (png/jpeg/gif): decode fully with an ImageIO ImageReader. IIOException or EOFException means corrupt=true. Add webp through a decoder plugin, or through a RIFF chunk-length check if no plugin is added.
2. Dependencies in pom.xml: add org.apache.poi:poi, which is a library and not an AWS service. Alternatively, add tika-parser-microsoft-module and tika-parser-zip-commons so the detector reports application/x-tika-ooxml-protected. That second option would also fix the generic-type CONTENT_TYPE_MISMATCH on [truncated]

### GA-041: Expanding amendments are not blocked after a commercial lapse

- Severity: medium. Verification: confirmed. Fix size: S. Audit key: `G121-AD-14`.
- Plan reference: AD-14. Plan basis: - **In force:** plan line 466-468 (Architecture Decision 14) explicitly lists "expanding amendments" and "new authoring" among what a later commercial lapse blocks.
- **Restated:** plan line 1899, P4-T4/T7; plan line 4077, P12-T4 ("Paid lapse or trial expiry prevents new or expanding work"); plan line 4664 (exit gate).
- **Not superseded:**
  - Phase 7 design decision 7 (lines 2857-2864) and P7-T5 (2913-2933) define when an amendment is compatible, but say nothing about entitlement.
  - Phase [truncated]

Current state:

An issued Information Request can still be amended after the owner's plan lapses (trial ended, past due after grace, canceled, or feature removed), including amendments that add Requirements. The only entitlement check on the amend path is InformationRequestMutationGate.requireContinuationEntitlement. Once a RequestExecutionGrant exists, that check only asks whether the owner is operationally suspended or the grant is revoked. It never looks at the owner's live plan. Nothing on the amend path decides whether an amendment is "expanding": InformationRequestAmendmentClassifier produces ADDED, REMOVED, MEANING_CHANGED and PRESENTATION_CHANGED changes plus group changes, and InformationRequestAmendmentGuard only refuses schema changes, narrowed occurrence structure and changes that reach a submitted scope.

Two paths are affected:
- **Existing later Version:** a lapsed owner can re-pin to a later published Version, for example one authored before the lapse, that adds Requirements or raises a group's maxOccurrences. The materializer then creates the new runtime Requirements.
- **Ad hoc requests:** InformationRequestAmendmentTargetResolver.publishPrivateVersion calls InformationRequestPrivateVersionPublisher.publish with arbitrary new configuration. There is no template or runtime entitlement check, so a lapsed owner can author entirely new Requirements. That is both "new authoring" and an "expanding amendment", and Decision 14 blocks both after a lapse.

Successors and recurrence do re-check the live plan through InformationRequestDraftFactory -> [truncated]

Evidence:

- **Amend path:** src/main/kotlin/com/docuhyphen/app/api/service/informationrequest/InformationRequestAmendmentService.kt:90-113. amendLocked calls gate.requireMutation(AMEND), then gate.requireContinuationEntitlement(locked) (line 94), authorize, ETag, targetResolver.resolve, classify, guard.requireAmendable, and materializer.advance. There is no live-plan check.
- **Continuation check:** InformationRequestMutationGate.kt:53-69 (requireContinuationEntitlement). With a grant present it only calls entitlementGuard.requireNotOperationallySuspended and checks grant.revokedAt. The live check entitlementGuard.requireRequestMutation (plan feature plus mutations allowed) runs only when grant == null.
- **Live-plan guard:** InformationRequestEntitlementGuard.kt:45-51. requireRequestMutation is the live-plan check the amend path skips after issuance.
- **Ad hoc publish:** InformationRequestAmendmentTargetResolver.kt:59-70. publishPrivateVersion publishes a new private Version from [truncated]

Fix outline:

1. **Classify expansion.** Add `val expanding: Boolean` to InformationRequestAmendmentPlan in model/informationrequest/InformationRequestAmendmentModels.kt, computed in InformationRequestAmendmentClassifier.classify. It is true when any change is ADDED or MEANING_CHANGED (a changed meaning can add obligations, so this is the conservative reading), or when a group's maxOccurrences rises (the classifier needs the earlier maxOccurrences to compare). Presentation-only and pure removal amendments are not expanding.
2. **Gate expansion on the live plan.** Add `requireExpansionEntitlement(locked)` to InformationRequestMutationGate, delegating to entitlementGuard.requireRequestMutation(locked.exchange). That check covers the live plan feature, mutations allowed under enforcement mode, and suspension. It throws SubscriptionDenialException, which InformationRequestCommandHttp already rethrows to the global subscription denial mapper.
3. **Call it in the amend path.** In InformationRequestAmendmentService.amendLocked:
   - Before targetResolver.resolve, when command.configuration != null (the ad hoc private Version publish is new authoring), call gate.requireExpansionEntitlement(locked).
   - After classifying, call it again when plan.expanding.
   - Keep requireContinuationEntitlement for non-expanding amendments.
4. **Tests:** add InformationRequestAmendmentServiceTest cases, or extend the runtime test harness with a real InformationRequestEntitlementGuard over a lapsed SubscriptionAccessService, as the P4-T8 tests do. Cover:
   - a lapsed owner (CANCELED, PAST_DUE past grace, [truncated]

### GA-042: Imported-value reconciliation POST requires an Idempotency-Key but the service ignores it

- Severity: medium. Verification: partial. Fix size: M. Audit key: `G133-P11-T12`.
- Plan reference: P11-T12 / Phase 11 decision 9. Plan basis: Where the requirement comes from:
- Plan 3782-3787 (Phase 11 decision 9): "Every POST requires an Idempotency-Key", covering POST .../imported-value-reconciliations.
- Plan 3742-3745 (decision 2): Command Receipts are reused as-is, and deciding and reconciling are named under INFORMATION_REQUEST_DECIDE_EXTERNAL_VALUES.
- Plan 817-819 (global command rule): replaying the same key and fingerprint returns the original result, and reusing a key with a different fingerprint returns a stable [truncated]

Current state:

`POST /information-requests/{id}/imported-value-reconciliations` refuses a request with no Idempotency-Key and passes the key into `ReconcileInformationRequestImportedValuesCommand.idempotencyKey`. But `InformationRequestImportedValueService.reconcile` never reads that key and never goes through `CommandReceiptService.runOnce`. No command_receipt row is written, and nothing is replayed. Every call, including a retry with the same key, recomputes the comparison against the current state.

Why this is narrower than claimed:
- A same-key retry against unchanged state already behaves idempotently by accident. Discrepancies are de-duplicated per (imported value, response revision). The transition history entry is keyed "information_request.external|reconciliation|<first new discrepancy id>" and is written only when new discrepancies are created.
- The "fingerprint mismatch is never refused" point does not apply. The command has no body or parameters apart from the request id, which is already part of the receipt scope (resource, operation, actor, key). So no different request could share a key.

The real gap: a same-key retry after something changes returns a freshly computed result instead of the original one. Such changes include a revised answer, a value expiring, a new value being proposed, or a value being rejected. The retry can also record new discrepancies and a new transition entry. That breaks the plan's command-safety rule that replaying the same key returns the original result.

Tests: no test reuses a reconcile key. The service tests and conformance tests use a new [truncated]

Evidence:

- InformationRequestImportedValueReconciliationResource.kt:28 reads the header. Line 34 calls `InformationRequestCommandHttp.idempotencyKey`, which rejects a missing or blank key. Line 36 builds `ReconcileInformationRequestImportedValuesCommand(requestId, access, key)`.
- InformationRequestExternalSourceModels.kt:96-100: the command has requestId, access and idempotencyKey only, with no body fields.
- InformationRequestImportedValueService.kt:209-268 (`reconcile`): locks, authorizes and computes outcomes. It saves discrepancies only when none exists for the same importedValueId and responseRevision (lines 238-252). It records history only for newly created discrepancies (lines 262-267). `command.idempotencyKey` and `commandReceiptService` are never referenced.
- By contrast, `propose` (line 76) and `decide` (line 162) go through the `once(...)` helper (lines 339-368), and `resolve` (lines 270-297) calls `commandReceiptService.runOnce`, each with a SHA-256 fingerprint.
- Only three [truncated]

Fix outline:

Replay needs the original computed outcomes, and today they exist only in memory, so persist each reconciliation run.

1. Migration V151__information_request_imported_value_reconciliation.sql:
   - Table `information_request_imported_value_reconciliation`: id, information_request_id with FK, recorded_by_principal_kind/id, recorded_at.
   - Table `information_request_imported_value_reconciliation_item`: reconciliation_id with FK and cascade, imported_value_id, requirement id, outcome with a CHECK over the five values, response_canonical_value, discrepancy_id.
   - Add both tables to the request disposal path and its disposal counts. Decision 3 requires every external-source record to be removed and counted.
2. Entities, repositories and mapping:
   - Entities in model/informationrequest.
   - Repositories in repository/informationrequest.
   - Map rows back to `InformationRequestImportedValueReconciliation` through the existing mapper class.
3. `InformationRequestImportedValueService.reconcile`:
   - Add `RECONCILE_OPERATION = "reconcile-information-request-imported-values"`.
   - Build a `CommandReceiptRequest` with resource `ResourceRef.informationRequest(requestId)`, actor principal, `command.idempotencyKey`, and fingerprint `sha256Hex("$RECONCILE_OPERATION|$requestId")`.
   - Move the current body into `reconcileOnce`, which saves the run and its items. Return `CommandMutationResult(results, CommandResultReference(ResourceType.INFORMATION_REQUEST_EXTERNAL_SOURCE, run.id))`.
   - On `Replayed`, re-authorize with `INFORMATION_REQUEST_DECIDE_EXTERNAL_VALUES` (as `resolve` [truncated]

### GA-043: If-Match: * skips the reviewed-content check on submission and attestation

- Severity: medium. Verification: confirmed. Fix size: S. Audit key: `G139-P7-T3`.
- Plan reference: P7-T3 / Phase 7 design decision 2. Plan basis: Requirement in force:
- Plan lines 2832-2835 (Phase 7 design decision 2): the submission ETag is stated as `If-Match` when a client attests or submits, "so a submitter can only submit exactly what was reviewed".
- Lines 2898-2902 (P7-T3): uses the Phase 3 `If-Match` contract and marks "the submission ETag as `If-Match`" as done.
- Lines 626-629 (constraint 31) and 1469-1475 (P3-T3): define the 428/412 contract but say nothing about the wildcard.

Nothing supersedes this:
- Line 262 (DS-T4) [truncated]

Current state:

Package submission and Submission Attestation both accept `If-Match: *` and treat it as no condition at all, so the server never checks the reviewed-content guarantee. The shared header parser turns `*` into `CommandPrecondition.Unconditioned`, and that value's `requireSatisfiedBy` does nothing. The submission endpoint (authenticated and no-auth) and the attestation endpoint build their commands with `CommandPreconditionHeader.required(ifMatch)`. The services check that precondition against `InformationRequestETag.submissionOf(stageKey, content.contentHash)`, so a wildcard request submits or attests whatever the content is at that moment, even if it changed after review-before-submit. A missing header is correctly refused (Absent, 428), and a wrong strong ETag is correctly refused (412). Only the wildcard gets through.

What limits the impact:
- The first-party web app always sends the preview's `submissionETag`, so it is not affected. Only a hand-built or third-party client sending `*` can bypass the check.
- The stored records stay accurate: the package freezes the hash it actually froze (`contentHashSha256`), and each attestation stores the hash it was actually made against (`attestedContentHashSha256`).
- The loss is the design guarantee itself. A submitter or attestor can go through against content changed by a concurrent contributor after they reviewed it.

No test covers the wildcard for submission or attestation. The only wildcard test is at the shared-helper level (CommandPreconditionHttpTest), and it asserts that `*` is accepted.

Evidence:

Code paths:
- src/main/kotlin/com/docuhyphen/app/api/resource/command/CommandPreconditionHeader.kt:21 maps `*` to `CommandPrecondition.Unconditioned`.
- src/main/kotlin/com/docuhyphen/app/api/service/command/CommandPrecondition.kt:9-12: `Unconditioned.requireSatisfiedBy` returns Unit (does nothing).
- src/main/kotlin/com/docuhyphen/app/api/resource/informationrequest/InformationRequestSubmissionEndpoint.kt:50 and InformationRequestAttestationEndpoint.kt:34 both use `CommandPreconditionHeader.required(ifMatch)`.
- Callers: InformationRequestSubmissionResource.kt:70, InformationRequestNoAuthSubmissionResource.kt:88, InformationRequestAttestationResource.kt:40, InformationRequestNoAuthAttestationResource.kt:45. There is no ContainerRequestFilter that handles If-Match (only CorrelationContextFilter, EndpointAuthorizationFilter and InputSanitizationFilter exist).
- InformationRequestSubmissionService.kt:129 and InformationRequestSubmissionAttestationService.kt:136 call [truncated]

Fix outline:

Refuse the wildcard (and a missing header) on the two content-bound commands only. Leave the shared wildcard behavior alone for every other command.

1. Service layer, where the domain rule belongs:
   - Add a small helper, e.g. `InformationRequestReviewedContentPrecondition.require(precondition, currentETag)` in service/informationrequest. Put it in a dedicated object, or add a `requireStrictlySatisfiedBy` member to `CommandPrecondition`.
   - It throws `CommandPreconditionException.required(currentETag)` for `Unconditioned` or `Absent`, and otherwise delegates to `requireSatisfiedBy`.
   - Call it instead of `command.precondition.requireSatisfiedBy(...)` at InformationRequestSubmissionService.kt:129 and InformationRequestSubmissionAttestationService.kt:136.
   - The existing `CommandPreconditionResponse` or `InformationRequestCommandHttp.refused` mapping then answers 428 COMMAND_PRECONDITION_REQUIRED with the current submission ETag in the `ETag` header, so the client can review again.
   - Leave withdraw (which uses the responses ETag) and other commands unchanged.
2. Tests:
   - In InformationRequestSubmissionTransactionTest, add a case where submit and attest with `CommandPrecondition.Unconditioned` are refused with `Kind.REQUIRED` and `currentETag == submissionETag(...)`, and assert that no package or attestation row and no command receipt are written.
   - In InformationRequestSubmissionResourceContractTest, add a case where `If-Match: *` answers 428 on the authenticated and no-auth submission and attestation surfaces.
3. Docs: update [truncated]

### GA-044: Submission content hash leaves out governing assessments, conformance, and attestation-item envelopes

- Severity: medium. Verification: partial. Fix size: S. Audit key: `G140-P7-T1b`.
- Plan reference: P7-T1b / Phase 7 design decision 2. Plan basis: In force:
- Plan lines 2830-2835 (Phase 7 design decision 2): the hash covers each active runtime Requirement occurrence with its revision, response envelope and revision, and current Evidence Versions with document hashes and governing assessments. It is the strong ETag, "so a submitter can only submit exactly what was reviewed".
- Lines 2836-2838 (decision 3): a later scope change changes the hash and stops earlier attestations counting.
- Lines 2851-2853 (decision 4): an exception [truncated]

Current state:

Part of this claim holds today, part only becomes possible once a scanner exists, and part is not a plan requirement.

(1) Attestation items: confirmed, and reachable today. InformationRequestSubmissionContentCollector.contentHash (lines 128-162) drops every RESPONSE_ATTESTATION item at line 142. Plan decision 2 says the hash covers each active runtime Requirement occurrence. For attestation occurrences, none of these reach the submission ETag: requirement id, current revision, binding, occurrence path, response id and revision, disposition, and narrative.

This matters because an attestation Requirement is complete when a permitted exception disposition with a narrative is recorded (InformationRequestAttestationCompletenessEvaluator lines 28-30, 57-60). Authoring allows an attestation Requirement to permit exception dispositions, and the ordinary response save path has no type restriction. The package freezes that disposition and narrative anyway (InformationRequestSubmissionService.writeMembers lines 228-229). So a submit or attest call carrying an ETag read before the exception was recorded still passes the If-Match check.

The respondent workspace UI filters attestation Requirements out of response editing (structuredResponseWorkspaceState.ts:198). So today the path is the REST response API, not the UI. The same applies to an amendment that advances an attestation Requirement's revision on an unsubmitted scope.

(2) Governing assessments: missing from the hash as the plan text requires, but it cannot happen today. Line 156 hashes only [truncated]

Evidence:

- src/main/kotlin/com/docuhyphen/app/api/service/informationrequest/InformationRequestSubmissionContentCollector.kt:142 has `items.filter { it.requirementType != InformationRequestRequirementType.RESPONSE_ATTESTATION }`. Line 155-157 has evidence material `"${it.versionId}:${it.documentVersionId ?: ""}:${it.contentHash ?: ""}"`, which has no assessment ids and no conformance.
- InformationRequestEvidenceEvaluationService.kt:104-106: submissionFacts computes conformance, inspectionAssessmentId and malwareAssessmentId per member.
- InformationRequestSubmissionService.kt:228-229 freezes attestation-item disposition and narrative. Lines 260-262 freeze conformance and both assessment ids. Line 129 checks If-Match against the content hash only.
- InformationRequestSubmissionAttestationService.kt:135-136 applies the same precondition for attestations. Attestations live in their own table (lines 156-179) and never write a response, so hashing attestation-item response envelopes would not be [truncated]

Fix outline:

1. src/main/kotlin/com/docuhyphen/app/api/service/informationrequest/InformationRequestSubmissionContentCollector.kt, contentHash:
   - Remove the RESPONSE_ATTESTATION filter so every active occurrence in scope contributes requirement, revision, binding, path, response id and revision, disposition, and narrative hash. Attestation items have no evidence and no field revision. Attestation records themselves stay out of the hash, as decision 3 requires.
   - Extend each evidence member token to `versionId:documentVersionId:contentHash:inspectionAssessmentId:malwareAssessmentId`.
   - Do not add conformance: it is clock-derived and not named in decision 2.
   - No migration is needed; the hash is computed, and the development-stage rule forbids compatibility handling for packages hashed the old way.
2. Optionally, align InformationRequestSubmissionService.itemHash with the governing assessment ids, so review carry-forward treats a rescanned file as changed.
3. Tests, written first:
   - A focused InformationRequestSubmissionContentCollector unit test (none exists) proving:
     - the hash changes when an attestation Requirement's disposition or narrative changes;
     - the hash changes when a malware assessment is appended to a current Evidence Version;
     - recording a Submission Attestation does not change the hash.
   - In InformationRequestSubmissionTransactionTest:
     - read the submission ETag, record a permitted exception disposition on the attestation Requirement through the response service, then show that submit with the earlier ETag is refused as STALE (412);
  [truncated]

### GA-045: Earlier stage can be withdrawn while a later stage depending on it through a condition stays submitted

- Severity: medium. Verification: confirmed. Fix size: S. Audit key: `G141-P7-T7a`.
- Plan reference: P7-T7a / P7-T7b / Phase 7 design decision 1. Plan basis: - Plan 2825-2829 (Phase 7 design decision 1): condition rules, repeatable groups and anchored Requirements stay inside one stage, so an editable stage can never change what a submitted stage holds.
- 2942-2945 (P7-T7a, "stage containment validation"; P7-T7b, "sequential stage order, and withdrawal before review").
- 919 and 2971 (a submitted stage stays immutable while another stays editable).
- 3075 (decision 10) only adds the before-review limit and does not supersede decision 1.
- Nothing in [truncated]

Current state:

Phase 7 design decision 1 says a condition rule must stay inside one stage, so an editable stage can never change what a submitted stage holds. The code relaxes this on purpose. The stage-containment validator accepts a condition on a Requirement in stage B that reads a source Requirement in an earlier stage A whenever the Template Version orders stages SEQUENTIAL. A unit test confirms the relaxation. It only holds if A stays submitted for as long as B does, and nothing enforces that.

Withdrawal (recordWithdrawal) checks three things: the package is still active, the caller is authorized, and requireWithdrawable finds no review started or reviewer assigned for that package. It never looks at other stages. Once A and B are both submitted and the request is still ACTIVE (for example because a review is pending), the A package can be withdrawn. A then drops out of activePackages, so its Requirements unlock and the condition source can be edited. That flips whether B's conditional Requirement applies. B stays locked and current, and its frozen items still carry the old completeness state (HIDDEN vs required).

A can then be resubmitted after B. requireStageOrder only checks that the stages before the one being submitted are active, so that passes. The sequential invariant (B submitted implies A submitted) is broken while A is withdrawn. isSatisfied only asks whether each scope has an active package with accepted reviews, so the request can close with B's frozen condition outcome contradicting A's current answers.

The same cross-stage leak also exists through Phase 8 [truncated]

Evidence:

- src/main/kotlin/com/docuhyphen/app/api/service/informationrequest/InformationRequestTemplateSubmissionPolicyValidator.kt:83,97-98: `val earlier = stageOrder.indexOf(sourceStage) < stageOrder.indexOf(conditionalStage)`; `if (sourceStage != conditionalStage && !(sequential && earlier))` throws, so an earlier-stage source under SEQUENTIAL is accepted.
- src/test/kotlin/com/docuhyphen/app/api/service/informationrequest/InformationRequestTemplateSubmissionPolicyValidationTest.kt:87,115-124: the `earlierSequential` configuration with the source in record-stage and the conditional in confirmation-stage is asserted valid.
- src/main/kotlin/com/docuhyphen/app/api/service/informationrequest/InformationRequestSubmissionService.kt:289-340 (recordWithdrawal): checks only the mutation gate, authorization, the If-Match, that the package is active (306) and reviewOpening.requireWithdrawable (313). No check for later stages or condition dependents.
- InformationRequestSubmissionService.kt:139 and [truncated]

Fix outline:

Plan-conforming fix (no migration needed; V151 stays free):

1. In InformationRequestTemplateSubmissionPolicyValidator.validateConditionStages, remove the `sequential && earlier` exception. Any predicate whose source Requirement is in a different stage from its conditional Requirement is then refused with the existing stable message. The `sequential`/`stageOrder` parameters become unused, so drop them.

2. Update InformationRequestTemplateSubmissionPolicyValidationTest: the `earlierSequential` case should now assert a throw. Add an InformationRequestTemplateConfigurationWriterContractTest or publication-rule case showing that a SEQUENTIAL staged Version with a cross-stage condition cannot be saved or published.

3. For already-drafted Versions, publication calls normalize, so a draft with a cross-stage condition fails to publish. No backwards-compatibility path is needed (Development-Stage Constraint).

4. Defense in depth, and to protect the sequential invariant, which breaks even without conditions: in InformationRequestSubmissionService.recordWithdrawal, when the Version is SEQUENTIAL and staged, refuse withdrawing stage A while any later stage has an active package. Use SUBMISSION_NOT_WITHDRAWABLE with a message such as "A later part was submitted after this one; withdraw it first". Resolve the stage order through InformationRequestSubmissionStages. Add a transaction test in InformationRequestSubmissionTransactionTest (submit A, submit B, withdraw A is refused; withdraw B then A succeeds).

5. Update [truncated]

### GA-046: Carry-forward for a staged source reads only the last stage package

- Severity: medium. Verification: confirmed. Fix size: M. Audit key: `G142-P7-T6`.
- Plan reference: P7-T6 / P7-T7c / Phase 7 design decision 8. Plan basis: Plan 2865-2869 (Phase 7 design decision 8: a successor records a carry-forward decision per Requirement occurrence that matches an item of its source package). 2825-2829 (decision 1: STAGED submission is per stage, so a staged source's submitted content spans several stage packages). 2934-2938 (P7-T6: preserve "the original request and package"). 2946-2947 (P7-T7c: superseding, recurrence and refresh successors). 920 and 3905 (a linked follow-up "explicitly carries forward or invalidates [truncated]

Current state:

When a successor request (supplement, superseding, recurrence or refresh) is created from a STAGED source and no source package is named, InformationRequestSuccessorService.sourcePackageOf picks only the active package with the highest package number (`lockService.activePackages(source.id).lastOrNull()`). That is the stage submitted most recently. A staged package holds only the items of its own stage, so carry-forward decisions (OFFERED or INVALIDATED) are written only for Requirements in that one stage. Requirements answered in the other submitted stages get no carry-forward row. The respondent is not offered those earlier answers under "From your previous submission", and nothing records that they were invalidated. In practice a package is never named for most successors. Recurrence and refresh never pass one (InformationRequestFollowUpService), and neither frontend supplement call sends sourcePackageId. Only a direct REST caller of POST /information-requests/{id}/successors can name a package, and even then it can name just one. The schema has the same limit: information_request_lineage (V135) has a single nullable source_package_id, and information_request_carry_forward_guard refuses any carry-forward item that is not in that one package. No test covers carry-forward from a staged source with more than one submitted stage. The one staged successor test (superseding, one stage submitted) makes no carry-forward assertions.

Evidence:

src/main/kotlin/com/docuhyphen/app/api/service/informationrequest/InformationRequestSuccessorService.kt:193-208 (sourcePackageOf; line 207 `lockService.activePackages(source.id).lastOrNull()`), :156 (the lineage stores one sourcePackageId), :167 and :225-256 (recordCarryForward plans against `sourcePackage.items` of one package only, line 242), :180 (history details carry a single sourcePackageId).
src/main/kotlin/com/docuhyphen/app/api/service/informationrequest/InformationRequestSubmissionLockService.kt:32-38 (activePackages returns every non-withdrawn, non-superseded package, which is one per submitted stage in STAGED mode).
src/main/kotlin/com/docuhyphen/app/api/repository/informationrequest/InformationRequestSubmissionRepositories.kt:18-24 (ORDER BY packageNumber, so lastOrNull is the most recently submitted stage).
src/main/kotlin/com/docuhyphen/app/api/service/informationrequest/InformationRequestSubmissionContentCollector.kt:64-66 (a stage package collects only Requirements [truncated]

Fix outline:

1. Migration V151__information_request_lineage_source_packages.sql:
   - Create information_request_lineage_source_package (lineage_id, successor_request_id, source_request_id, package_id, PK (lineage_id, package_id)).
   - Add FK (lineage_id, successor_request_id) to information_request_lineage (id, successor_request_id) and FK (package_id, source_request_id) to information_request_submission_package (id, information_request_id). This needs a unique (id, source_request_id) on lineage, or carry source_request_id via a guard trigger.
   - Make the table append-only with information_request_append_only_guard.
   - Copy existing lineage.source_package_id values into the new table, then drop lineage.source_package_id and its FK. No compatibility column is kept.
   - Replace information_request_carry_forward_guard so the item's package_id = NEW.source_package_id and that package is listed for NEW.lineage_id in the new table.
2. InformationRequestSuccessorService:
   - sourcePackageOf becomes sourcePackagesOf. With no package named it returns packageReader views for every lockService.activePackages(source.id), which is one per submitted stage, or the single whole package. With a package named it keeps the current withdrawal check and returns that view alone.
   - Save one lineage-source-package row per package.
   - recordCarryForward passes the combined items of all views to InformationRequestCarryForwardPlanner.plan. Keys do not collide because each Requirement lives in exactly one stage. Each row's sourcePackageId is set from the item's own packageId.
   - History details [truncated]

### GA-047: Expiry-triggered refresh rules are never triggered: lead_days unused, no scheduler, no due check or duplicate guard

- Severity: medium. Verification: confirmed. Fix size: M. Audit key: `G143-P7-T7c`.
- Plan reference: P7-T7c. Plan basis: Where the requirement comes from:
- Plan lines 2939-2941: P7-T7 includes "expiry-triggered refresh rules" and says "Phase 9 owns the clock calculation and scheduler that executes expiry and refresh".
- Lines 2946-2947: P7-T7c, "expiry refresh rules and refresh command (V135)".
- Line 2985: "expiry refresh" tests are required.
- Line 2997: the exit criterion only requires that evidence refresh "can be represented".
- Line 48: Status claims the refresh follow-ups are done.

Nothing supersedes, [truncated]

Current state:

You can store an expiry refresh rule, but nothing ever acts on it. The only thing that happens is a refresh you start by hand, and that command has no checks. Details:

- lead_days is written by InformationRequestFollowUpService.defineRefreshRule, stored, and mapped to the DTO. No code ever reads it back to decide anything.
- InformationRequestFollowUpService.refresh (POST /information-requests/{id}/refresh-rules/{ruleId}/refreshes) goes straight to InformationRequestSuccessorService.follow and creates a REFRESH successor draft. It does not:
  - compare the rule with the expires_on of any Evidence Version in the source package,
  - apply lead_days,
  - check whether this rule already produced a refresh.
- The only duplicate protection is the per-key command receipt. Calling again with a new Idempotency-Key creates another REFRESH successor.
- V135 adds no uniqueness on refresh lineage. information_request_lineage is unique only on successor_request_id and on (recurrence_id, recurrence_sequence). ck_information_request_lineage_refresh even allows a REFRESH row with no refresh_rule_id.
- No scheduler or processor touches refresh rules. InformationRequestClockScheduler only processes clock points. The only callers of refresh are the REST resource and one happy-path test.
- There is no error code for a refresh that is not yet due, unlike RECURRENCE_NOT_DUE for recurrence.
- Rules are also invisible:
  - InformationRequestLineageQueryService returns the recurrence and its next due date, but not refresh rules.
  - The frontend has no service call or UI to define, list, or [truncated]

Evidence:

- src/main/kotlin/com/docuhyphen/app/api/service/informationrequest/InformationRequestFollowUpService.kt:
  - 136-170: defineRefreshRule stores leadDays; it only checks that requirementKey names a DOCUMENT requirement.
  - 172-195: refresh does a lookup, lock, receipt, authorize and successorService.follow(kind=REFRESH). There is no expiry, lead-day or prior-refresh check.
  - Compare 113-121: createNextOccurrence does have a RECURRENCE_NOT_DUE check.
- InformationRequestSuccessorService.kt:132-191: follow() saves the lineage with refreshRuleId and carry-forward, with no refresh-specific guard.
- src/main/resources/db/migration/V135__information_request_lineage.sql:
  - 25-45: refresh_rule table, where lead_days has only CHECK >= 0.
  - 83: ck_information_request_lineage_refresh only says refresh_rule_id implies REFRESH.
  - 89-91: the only unique constraints are successor and (recurrence_id, recurrence_sequence).
  - No later migration touches refresh rules except the disposal [truncated]

Fix outline:

1. Migration V151 (information_request_refresh_trigger):
   - Tighten the lineage CHECK to (lineage_kind = 'REFRESH') = (refresh_rule_id IS NOT NULL) AND (lineage_kind <> 'REFRESH' OR source_package_id IS NOT NULL).
   - Add a unique partial index on information_request_lineage (refresh_rule_id, source_package_id) WHERE lineage_kind = 'REFRESH', so each rule refreshes a given submitted package at most once.
2. New pure calculator InformationRequestRefreshDue (service/informationrequest). Given a rule and the latest active submission package view:
   - Find the Evidence Version members whose requirement key equals rule.requirementKey.
   - Take the earliest expiresOn and compute dueOn = expiresOn - leadDays.
   - Return null when there is no package or no captured expiry.
3. InformationRequestFollowUpService.refresh:
   - Resolve the latest package and compute due.
   - Refuse with new catalog codes REFRESH_NOT_DUE (no expiry, or dueOn is after today in UTC) and REFRESH_ALREADY_CREATED (lineage exists for rule plus package).
   - Pass sourcePackageId explicitly to follow().
4. defineRefreshRule: refuse, with SUCCESSOR_SOURCE_INVALID or a new code, when the requirement's evidence policy has expiryDateRequirement = NOT_CAPTURED.
5. Scheduler:
   - Add InformationRequestRefreshScheduler (@Scheduled every "${app.information-request.refresh.every:1h}", identity "information-request-refresh", ConcurrentExecution.SKIP) and InformationRequestRefreshProcessor.
   - Add a repository query for refresh-rule candidates on non-cancelled, non-superseded requests with an active package and [truncated]

### GA-048: No save-vs-submit or upload-vs-submit race tests

- Severity: medium. Verification: confirmed. Fix size: M. Audit key: `G144-P7`.
- Plan reference: P7 Tests to write first / P7-T3. Plan basis: In force:
- Plan line 2976 (Phase 7 "Tests to write first"): "Save-versus-submit and upload-versus-submit race tests."
- Lines 2899-2903: P7-T3 requires submission to be concurrency-safe when save, upload, expiry or duplicate submissions race. Its "Done" note cites only a parallel duplicate test.
- Line 4184 (Cross-Phase Test Matrix, Command safety row) requires tests of "concurrent ... response, evidence, submission, and review races".
- Line 4182 (Submission row) lists concurrency.

Nothing [truncated]

Current state:

The production code appears to serialize these races correctly, but no test proves it. Submission, response patch and evidence upload all lock the parent Exchange and then the request row (SELECT FOR UPDATE) before doing any work. After taking the lock, submission checks its If-Match content-hash ETag, and patch and upload check the submission-package lock. So whichever command commits second should fail cleanly: a submit that waited behind a save or upload should get a STALE precondition error, and a save or upload that waited behind a submit should get SUBMISSION_LOCKED. None of this is exercised concurrently. The only concurrent submission test races submit against submit (`parallel duplicates of one submission record exactly one package`). The other submission tests run one command at a time: the stale-ETag test uses a made-up all-zero hash, and the two SUBMISSION_LOCKED tests commit the package first and then try the evidence withdrawal or response patch in a new transaction. The submission test harness cannot run an upload at all, because SubmissionServices holds only submissions, attestations, evidence collection and responses. The existing concurrency test classes cover other pairs: duplicate upload retries, reassignment against save or parent termination, contact proof, quota, fact recertification and parent-termination lock ordering. None of them races a response save or an evidence upload against a submission. So the required save-versus-submit and upload-versus-submit race tests do not exist.

Evidence:

Tests:
- src/test/kotlin/com/docuhyphen/app/api/service/informationrequest/InformationRequestSubmissionTransactionTest.kt:391-414 is the only concurrent submission test (Executors.newFixedThreadPool(2) with invokeAll), and it races two identical submits.
- Same file: lines 184-202 (stale ETag, a made-up zero hash, run one command at a time), 310-340 (withdrawal after submit, one at a time) and 344-372 (patch after submit, one at a time).
- Same file, 626-631: SubmissionServices has no upload service.
- A grep for Executors/invokeAll/CountDownLatch/CyclicBarrier/CompletableFuture under src/test/kotlin finds 13 files. The Information Request ones and what they race:
  - InformationRequestEvidenceCommandTransactionTest:219: same-key upload retry.
  - InformationRequestPartyConcurrencyTransactionTest:74,105: reassignment against save or termination.
  - InformationRequestContactProofTransactionTest:74,108.
  - InformationRequestQuotaConcurrencyTest:44.
  - [truncated]

Fix outline:

This needs tests only. No production change and no migration are needed, so V151 stays free.

1. In InformationRequestSubmissionTransactionTest (or a new InformationRequestSubmissionRaceTransactionTest next to it), copy the latch pattern from InformationRequestEvidenceCommandTransactionTest:219: a mocked InformationRequestTransitionHistoryService whose record() counts down `insideFirst` and then waits on `releaseFirst`, plus a two-thread executor.
   a. Save first: read the submission ETag, then start a response patch and hold it inside its transaction. Start the submit with that ETag; it blocks on the request lock. Release the patch. Assert the submit fails with CommandPreconditionException STALE and a new currentETag, the package count is 0 and the response revision moved once.
   b. Submit first: hold the submit inside its transaction and start a patch using the pre-submit responses ETag. Release. Assert the patch fails with SUBMISSION_LOCKED, exactly one package exists, and the frozen item's response revision equals the pre-patch revision.
2. Upload side: add InformationRequestEvidenceUploadService to the harness in either direction. Either put `uploads` into SubmissionServices, wired like InformationRequestEvidenceCommandTransactionTest.services(), or write the tests in the evidence transaction test with a submission service added.
   c. Upload first: hold the upload, then submit with the pre-upload ETag. Expect STALE and no package.
   d. Submit first: hold the submit, then upload to a Requirement in the submitted scope. Expect SUBMISSION_LOCKED, with no evidence [truncated]

### GA-049: Cancel and supersede reasonCode subject field is free text, not a stable code

- Severity: medium. Verification: confirmed. Fix size: S. Audit key: `G153-P9-Decision-1`.
- Plan reference: P9-Decision-1. Plan basis: Phase 9 design decision 1, plan lines 3218-3227: version 1 request trigger subject fields carry "identifiers, states, stable codes, counts, and instants only", with reasonCode listed, and never a response value, label, or contact endpoint. P9-T1, lines 3345-3356: "safe versioned subject schemas". Line 398: "safe subject-field descriptors". Line 3260 is consistent with this: system cancellation uses the stable code EXCHANGE_ENDED. Nothing supersedes or defers the requirement. The Status section [truncated]

Current state:

When an author cancels or supersedes a request, the reasonCode they send is never checked to be a code. The UI collects it as free text: CancelRequestDialog and SupersedeRequestDialog each show a TextField labelled "Reason" (maxLength 120), and the cancel dialog's hint is "Recorded in the request history." The service sends the trimmed text as {reasonCode}. On the backend, the request DTOs (CancelInformationRequestRequest and SupersedeInformationRequestRequest, reasonCode: String?), InformationRequestResource.cancel and supersede, and InformationRequestLifecycleService.cancel, supersede and mutate all pass the value through with no format check. The database column information_request_transition.reason_code is a plain VARCHAR(128) with no CHECK. InformationRequestTransitionHistoryService then writes the value to four places: the transition row, the audit `reason` column, the audit and DomainEvent payload key "reasonCode" (which InformationRequestAuditPayloadPolicy allow-lists), and the outbox event. InformationRequestWorkflowSubject maps it to the version 1 subject field reasonCode for information_request.request.cancel and .supersede (these trigger names come from InformationRequestWorkflowTriggerCatalog). InformationRequestWorkflowTriggerConsumer passes that subject to the Workflow engine as subjectData. So any sentence an author types, including names or other personal details, becomes Workflow subject data and audit payload. This contradicts Phase 9 decision 1, which limits these fields to stable codes, and V139's descriptors "Stable cancellation reason code" and [truncated]

Evidence:

web-app/src/app/information-requests/authoring/cancel-request-dialog/CancelRequestDialog.tsx:15,23-24,29-34 (free-text "Reason" TextField, maxLength 120, passes reason.trim() as reasonCode). web-app/src/app/information-requests/authoring/supersede-request-dialog/SupersedeRequestDialog.tsx:24,46-47,59-63 (same). web-app/src/services/informationRequestAuthoringService.ts:198-221 (posts {reasonCode} and {supersededByRequestId, reasonCode}). web-app/src/app/information-requests/authoring/author-workspace/InformationRequestAuthorWorkspace.test.tsx:303-314 (the test types into the "Reason" free-text box). src/main/kotlin/com/docuhyphen/app/api/resource/model/InformationRequestRequests.kt:55-63 (reasonCode: String? with no constraint). src/main/kotlin/com/docuhyphen/app/api/resource/informationrequest/InformationRequestResource.kt:181-243 (passes request.reasonCode through unchanged). [truncated]

Fix outline:

Backend: (1) Add one shared validator, for example InformationRequestReasonCodes.requireMachineCode(value, label), in service/informationrequest. It reuses the machine-code pattern ^[a-z0-9][a-z0-9._-]{0,127}$ that InformationRequestReviewFindingService.REASON_CODE and the bundle MACHINE_KEY already use; move REASON_CODE there so review findings share it. (2) Call it in InformationRequestLifecycleService.cancel and supersede before runOnce. A malformed value refuses with InformationRequestCommandRequestException or a lifecycle exception carrying a stable code such as REASON_CODE_INVALID, added to InformationRequestErrorCatalog. InformationRequestResource maps it to 400 or 409 like other command refusals. Keep reasonCode optional, since the DTOs already default it to null. The system paths (EXCHANGE_ENDED, PARENT_*, CLOCK_DUE) write history directly and stay unaffected. (3) Optionally add defense in depth: migration V151 adds a CHECK on information_request_transition.reason_code allowing NULL, the lowercase machine pattern, or ^[A-Z][A-Z0-9_]{0,127}$ for platform codes. Under the development-stage constraint there are no legacy rows to preserve; drop any non-conforming dev rows rather than writing compatibility code. Frontend: (4) Replace the free-text TextField in CancelRequestDialog and SupersedeRequestDialog. Either use a ChoiceSelect over a small, industry-neutral platform set with lowercase codes and display labels (for example no-longer-needed, issued-in-error, replaced, other), or use a code input validated against the same pattern with a clear hint. Keep the UI [truncated]

### GA-050: A finding is always attributed to the reviewer's earliest assignment, so a later-stage worksheet cannot return or reject an item

- Severity: medium. Verification: confirmed. Fix size: M. Audit key: `G163-P8-T2`.
- Plan reference: P8-T2 / P8-T4b. Plan basis: - Plan line 3051 (Phase 8 design decision 6): "A CHANGES_REQUIRED or REJECTED outcome needs a finding by that assignment on the item". This is the requirement in force.
- Lines 3031-3036 (decision 4): an assignment names a party and a stage, so one party may hold assignments on several stages.
- Lines 3020-3027 (decision 2): stages are SEQUENTIAL or PARALLEL, and the default stage has no exclusions.
- Line 3080 (decision 11): the prior-reviewer exclusion applies only when a stage is configured [truncated]

Current state:

When a reviewer records a finding, the service ties it to whichever of that reviewer's assignments on the review was assigned first. It does not check the stage, whether that assignment has already decided, or whether the assignment covers the item. InformationRequestReviewAccess.callerAssignments keeps every ACTIVE assignment the caller acts for. A decided assignment keeps state ACTIVE and only gains decidedAt. The list is ordered by assignedAt, then id, and the finding service takes firstOrNull(). Neither the finding command nor the REST request can name an assignment.

Recording a worksheet requires, for each CHANGES_REQUIRED or REJECTED entry, a finding whose assignmentId matches the assignment being recorded. So when one reviewer party holds assignments on two stages of the same review, every finding lands on the earlier assignment. This applies to SEQUENTIAL stage 1 then 2, or two PARALLEL stages. Nothing blocks that setup:
- The assignment uniqueness check is per stage only.
- excludesPriorReviewers is opt-in and defaults to false.
- Separation only refuses someone who already decided an earlier stage.

The worksheet on the later assignment then fails with REVIEW_FINDING_REQUIRED for every CHANGES_REQUIRED or REJECTED entry. That reviewer can only satisfy, except, or waive in the later stage. If the later stage was assigned first, the problem runs the other way and the earlier stage is the one that cannot return. The same thing happens when one principal acts for two REVIEWER parties assigned to the same review.

The frontend has a related problem. The reviewer [truncated]

Evidence:

Backend (paths under src/main/kotlin/com/docuhyphen/app/api/):
- service/informationrequest/InformationRequestReviewFindingService.kt:117 picks `access.callerAssignments(review, command.access).firstOrNull()`. Line 171 stores `assignmentId = assignment?.id?.takeIf { reviewing }`. Nothing checks the stage, decidedAt, or item coverage for that assignment.
- service/informationrequest/InformationRequestReviewAccess.kt:86-87: callerAssignments filters only `state == ACTIVE && actsAs`. It does not filter out decided assignments.
- repository/informationrequest/InformationRequestReviewRepositories.kt:65: findForReview is `ORDER BY assignment.assignedAt, assignment.id`.
- model/informationrequest/InformationRequestReviewModels.kt:95-109 (RecordInformationRequestReviewFindingCommand) and resource/model/InformationRequestRequests.kt:205-215 (RecordInformationRequestReviewFindingRequest) have no assignmentId field.
- resource/informationrequest/InformationRequestReviewEndpoint.kt:195-217 does [truncated]

Fix outline:

No migration is needed: information_request_review_finding.assignment_id and its FK to (assignment_id, review_id) already exist in V137. V151 stays free.

Backend:
1. Add `assignmentId: UUID?` to RecordInformationRequestReviewFindingRequest (resource/model/InformationRequestRequests.kt) and RecordInformationRequestReviewFindingCommand (model/informationrequest/InformationRequestReviewModels.kt). Pass it through InformationRequestReviewEndpoint.finding and add it to the request fingerprint in InformationRequestReviewFindingService.record.
2. In InformationRequestReviewFindingService.recordFinding:
   - If assignmentId is given, resolve it with access.requireAssignment(review, id), then call access.requireActsAs and access.requireActive. requireActive refuses a decided assignment.
   - Also require that the assignment's stage covers the item (snapshot.coverage[assignment.stageKey]) and that the stage is OPEN.
   - If assignmentId is absent, use the caller's only undecided, open-stage assignment covering the item. When there are none or several, refuse with a request error that asks for an assignment.
   - A request administrator without a reviewing assignment keeps assignmentId null.
   - Do not add a compatibility fallback that keeps silently taking firstOrNull().
3. Move the shared open-stage and coverage helpers (openItems, requireStageOpen) out of the decision service into a small collaborator so the finding and decision services apply the same rule.
4. Optionally make the replay branch in InformationRequestReviewDecisionService.run return the replayed assignment rather [truncated]

### GA-051: Retest of an earlier finding cannot be recorded from the reviewer UI

- Severity: medium. Verification: confirmed. Fix size: M. Audit key: `G164-P8-T7`.
- Plan reference: P8-T7 / P8-T11. Plan basis: Lines that put the requirement in force:
- 321: core scope includes "Item-level review findings, remediation, retesting".
- 324: responsive reviewer experiences.
- 3054-3059: Decision 7, a resubmission-review finding may retest an earlier finding as RESOLVED/UNRESOLVED, and a finding optionally names one exact Evidence Version.
- 3073-3074: Decision 9, only corrected items are retested.
- 3141-3143: P8-T7 retest.
- 3159-3164: P8-T11 reviewer finding, correction and remediation UI.
- 3569-3571: [truncated]

Current state:

The backend supports retests end to end, but the reviewer UI gives no way to record one.

Backend (working):
- V137 has the retests_finding_id and retest_result columns, a CHECK that the two are set together, and a guard trigger that only allows retesting a finding from the review named in prior_review_id.
- InformationRequestReviewFindingService validates the pair and the prior-review membership (REVIEW_RETEST_INVALID).
- The endpoint, the request model and the DTO all carry retestsFindingId and retestResult.
- A RESUBMISSION review gets priorReviewId set to the corrected package's review.
- Service-level tests record a RESOLVED retest.

Frontend (the gap):
- ReviewFindingDialog builds its request from only submissionItemId, reasonCode, narrative, severity, visibility, correctionScope and evidenceVersionId. It has no control for choosing an earlier finding or a RESOLVED/UNRESOLVED result, so the retest fields typed in models.tsx are never filled in.
- Retests are only displayed, as a text line in ReviewItemFindings.
- A reviewer on a RESUBMISSION (or reconsideration/appeal) review can't even see the earlier findings. The review read model uses snapshot.findings, which the loader fills with findForReview(review.id), so only this review's findings come back.
- Remediations are also filtered to this review's findings, so the remediation records linking earlier findings to the resubmitted items are left out of the new review's page.
- The web app never reads priorReviewId (it appears only in models.tsx) and never links to the prior review.

The help article says "a later [truncated]

Evidence:

Frontend:
- web-app/src/app/information-requests/review/review-finding-dialog/ReviewFindingDialog.tsx:39-48 (state has no retest fields), :99-107 (file choice only when fileScoped), :115-123 (onConfirm payload has no retestsFindingId/retestResult).
- web-app/src/app/information-requests/review/review-item-findings/ReviewItemFindings.tsx:48-53 (retest shown only as a text line).
- web-app/src/app/information-requests/review/review-item-card/ReviewItemCard.tsx:35 (findings come only from review.findings).
- web-app/src/app/information-requests/review/review-workspace-content/ReviewWorkspaceContent.tsx:114-119 (dialog gets only item, busy and handlers).
- web-app/src/app/information-requests/review/useInformationRequestReview.ts:45 (fetches only the one review).
- web-app/src/app/models/models.tsx:3584 (priorReviewId is declared; a grep of web-app/src finds no other use), :3671-3672 and :3824-3825 (retest fields typed but never set).
- [truncated]

Fix outline:

No migration is needed; V151 stays free.

Backend:
1. InformationRequestReviewQueryService.readable(): when review.priorReviewId is set, load findingRepository.findForReview(priorReviewId).
   - Filter by access.permitsItemReview on requirementId (or permitsManage).
   - Expose the result as a new priorFindings list on InformationRequestReadableReview (model/informationrequest).
   - Include the remediations whose findingId is in that prior set, or add them as priorRemediations.
2. InformationRequestReviewDtoMapper: add priorFindings: List<InformationRequestReviewFindingDto> (and optionally priorRemediations) to InformationRequestReviewDto.
3. Optional hardening in InformationRequestReviewFindingService.recordFinding:
   - Require the retested finding's requirementId to equal item.informationRequestRequirementId.
   - Refuse a second retest of the same prior finding by the same assignment.

Frontend:
4. models.tsx: add priorFindings to InformationRequestReviewDto.
5. ReviewItemCard: pass the item's earlier findings, matched by requirementId and occurrence or through remediation remediatedByItemId, into a small "Earlier findings" list (new child component with its own Styles file). Show whether each is already retested in this review.
6. ReviewFindingDialog:
   - Take an earlierFindings prop.
   - Add a new ReviewFindingRetest child (keeps the dialog under ~150 lines) with an "Earlier finding" dropdown that includes a "Not a retest" option, plus a RESOLVED/UNRESOLVED result choice. Send retestsFindingId and retestResult together.
   - Default to severity MINOR and scope [truncated]

### GA-052: No command-level tests for recusal/delegation/revocation, override, outcome rules, or reviewer authorization refusals

- Severity: medium. Verification: confirmed. Fix size: M. Audit key: `G166-Tests`.
- Plan reference: Tests to write first. Plan basis: The requirement is in force at plan lines 3171-3185 (Phase 8 "Tests to write first"): reviewer authorization and stage-order tests; recusal, delegation and override tests; separation-of-duties tests; and review-decision replay, fingerprint, missing or stale precondition and concurrent draft tests using the shared command-safety contracts.

It is reinforced by the Phase 8 design decisions:
- 3035-3037: recusal, delegation and revocation record who, when and why.
- 3043-3044: recused, delegated [truncated]

Current state:

The production code for reviewer assignment changes (recusal, delegation, revocation), authorized override, worksheet outcome rules and reviewer authorization refusals exists, but no backend test runs any of it. Across src/test the review services are called only through assign (5 calls), saveDraft (4), recordDecisions (6), reviewFindings.record (4), reviewComments.record (3) and reviewCycles.reopen (3). Nothing calls InformationRequestReviewAssignmentService.change or InformationRequestReviewDecisionService.override, and neither ChangeInformationRequestReviewAssignmentCommand nor OverrideInformationRequestReviewItemCommand appears in any test. InformationRequestReviewResourceContractTest has six cases (paths, assign, worksheet save, keyless or settled record, reconsider or appeal, queries). None reaches the recuse, delegate, revoke or override endpoints. Recusal, delegation and override appear only as hand-built facts or states. That covers InformationRequestReviewAggregatorTest (RECUSED/DELEGATED assignments and OVERRIDE decision facts), InformationRequestTemplateWalkingSkeletonPhase8Test (a synthetic OVERRIDE fact passed to the aggregator) and a raw-SQL RECUSED update in the migration contract test. None of these goes through the command path, so authorization, receipts, preconditions, separation of duties at delegation and override, settlement and audit are all untested.

No test asserts these refusals: REVIEW_FINDING_REQUIRED, REVIEW_NARRATIVE_REQUIRED, REVIEW_OUTCOME_NOT_PERMITTED (waiver not permitted), REVIEW_STAGE_NOT_OPEN (deciding or overriding a WAITING [truncated]

Evidence:

Production code under test:
- InformationRequestReviewAssignmentService.kt:111-161. change() handles RECUSAL (a reason is required, the assignment becomes RECUSED), DELEGATION and REVOCATION. It uses a receipt fingerprint, an ETag precondition and settleIfDecided.
- InformationRequestReviewAssignmentService.kt:163-182. authorizeChange: revocation needs manage; recusal needs requireActsAs; delegation needs manage or requireActsAs.
- InformationRequestReviewAssignmentService.kt:184-219. delegate: reviewerParty (can throw REVIEWER_NOT_ELIGIBLE), requireNotAssigned, then separation.requireMayReview.
- InformationRequestReviewDecisionService.kt:204-287. override: MANAGE_REVIEWS authorization, requireStageOpen, then REVIEW_OVERRIDE_NOT_PERMITTED, REVIEW_ITEM_NOT_COVERED, REVIEW_ALREADY_DECIDED, separation, REVIEW_NARRATIVE_REQUIRED.
- InformationRequestReviewDecisionService.kt:298-313. requireStageOpen throws REVIEW_STAGE_UNKNOWN or REVIEW_STAGE_NOT_OPEN.
- [truncated]

Fix outline:

This fix adds tests only; no production change or migration is needed, so V151 stays free.

1. InformationRequestReviewTestSupport.kt: add helpers `change(services, fixture, review, assignment, InformationRequestReviewAssignmentChange, reason, delegatePartyId, access, key)` and `override(services, fixture, review, stageKey, itemId, outcome, narrative, access, key)`. Add an extra-reviewer party fixture and a stage-insert helper that takes override_permitted, tie_resolution, aggregation and minimum count.

2. New InformationRequestReviewAssignmentChangeTransactionTest (QuarkusTest, same runtime.build pattern):
- Recusal by the assigned reviewer records state RECUSED, the reason code and the changer. It needs a reason (InformationRequestCommandRequestException without one). A different principal is refused with ForbiddenException, and the stage drops back to UNDERSTAFFED or PENDING.
- Delegation by the reviewer and by an administrator creates an ACTIVE assignment with delegatedFromAssignmentId. It refuses a non-REVIEWER party (REVIEWER_NOT_ELIGIBLE), an already-assigned party (REVIEWER_ALREADY_ASSIGNED) and a response party on an excluding stage (REVIEW_SEPARATION_OF_DUTIES). The delegate can then record decisions and the review settles.
- Revocation is refused for the reviewer (Forbidden) and succeeds for the administrator.
- A second change on a closed assignment answers REVIEW_ASSIGNMENT_INACTIVE.
- A replay with the same key returns the same result. A changed fingerprint (reason or delegate) raises CommandReceiptConflictException. A missing or stale review ETag raises [truncated]

### GA-053: No UI to name an individual platform user as a party; email always creates an External Participant

- Severity: medium. Verification: confirmed. Fix size: M. Audit key: `G173-P10-T2`.
- Plan reference: P10-T2. Plan basis: Plan lines 3537-3543 (Phase 10 design decision 4: parties take roleKey with exactly one of userId, principalGroupId, email, or subjectIdentityRefId; email means an owner-scoped External Participant). Lines 3619-3621 (P10-T2: request-author creation including "subject and contributor selection"). Line 3545-3550 region (decision 9: reviewer assignment management, which needs reviewer parties that can sign in). Lines 1854-1858 (P4-T4) and 4624-4638 (acceptance criteria: registration upgrade grants [truncated]

Current state:

The backend fully supports naming an individual App User as a request party: POST /information-requests/{id}/parties accepts userId, and POST .../parties/{partyId}/reassignment accepts userId or principalGroupId. The request-author frontend never uses this for anyone except the author. The Add party dialog (PartyHolderFields) offers only two holder types, "A person by email" and "A group". addPartyForm.partyRequestFrom builds only principalGroupId, email or subjectIdentityRefId payloads, and PartyHolder is typed as "email" | "group". The only userId assignments in web-app are the automatic author-as-Decision-Maker calls (useAuthorWorkspace.ts:103, useCreateInformationRequest.ts:72). An email holder goes to partyService.assignExternalParticipant, which calls ExternalParticipantService.findOrCreate. That service always creates or reuses an owner-scoped External Participant (a PARTICIPANT principal) and never looks up an existing App User. This matches plan decision 4, so the gap is in the UI, not the backend.

What this means in practice:
(1) An individual colleague cannot usefully be made a Reviewer or Decision Maker through the UI. An email Reviewer becomes a PARTICIPANT principal. The reviewer workspace and queue are authenticated-only (review/ has no no-auth code), and InformationRequestNoAuthReviewResource exposes only respondent comments and appeals. The backend does not refuse a participant Reviewer at assignment, so the UI lets the author create a reviewer who can never act.
(2) On an Exchange with requireRecipientSignIn, InformationRequestBootstrapShareLinkService [truncated]

Evidence:

web-app/src/app/information-requests/authoring/party-holder-fields/PartyHolderFields.tsx:14-17 (HOLDERS lists only email and group); web-app/src/app/information-requests/authoring/add-party-dialog/addPartyForm.ts:8 (PartyHolder = "email" | "group"), :54-60 (partyRequestFrom builds subjectIdentityRefId, principalGroupId or email only); web-app/src/app/information-requests/authoring/author-workspace/useAuthorWorkspace.ts:103 and create-information-request-dialog/useCreateInformationRequest.ts:72 (the only userId party payloads, both author-as-DECISION_MAKER); web-app/src/app/information-requests/authoring/add-party-dialog/usePartyCandidates.ts (loads only groups and subjects, no users); src/main/kotlin/com/docuhyphen/app/api/resource/informationrequest/InformationRequestPartyResource.kt:58-95 (accepts userId; email goes to assignExternalParticipant), :118-124 (reassignment takes userId/principalGroupId); [truncated]

Fix outline:

Frontend only; no backend contract change and no migration (V151 stays free).
1. addPartyForm.ts: extend PartyHolder to "user" | "email" | "group"; add userId to AddPartyForm and emptyAddPartyForm. addPartyProblem: add "Choose a person." for user. partyRequestFrom: return {roleKey, userId} for the user holder. Optionally default the holder to "user" for REVIEWER and DECISION_MAKER, and refuse the email holder for those roles with a message saying a reviewer must sign in (their workspaces are authenticated-only).
2. usePartyCandidates.ts: also load user candidates. In an organization session use organizationApi.fetchMyOrganizationUsers. In a personal session use personalContactsApi.searchContacts/fetchRecentContacts, which is reciprocity-gated. Trusted-org visibility stays group-only. Expose a PartyUserCandidate {id, name}.
3. PartyHolderFields.tsx: add a "A person with an account" option and a new co-located party-user-field/PartyUserField.tsx (+ Styles) combobox, keeping components under 150 lines, with ids, circular buttons and responsive layout. Update the email hint to say an access link is unavailable when the Exchange requires sign-in.
4. party-row/PartyRow.tsx: add a Reassign action with a dialog (user or group picker) that calls the existing reassignInformationRequestParty with If-Match and Idempotency-Key.
5. No-auth respondent workspace: add a "Use my account" action. It signs in with a return URL, then calls a new informationRequestRuntimeService.upgradeInformationRequestParticipantAccount (POST /information-requests/{id}/participant-account-links with [truncated]

### GA-054: Correction guidance counts returned files but never names which evidence versions a correction reopens

- Severity: medium. Verification: confirmed. Fix size: M. Audit key: `G175-P10-T3`.
- Plan reference: P10-T3 / P10-T5. Plan basis: Plan line 3563-3564 (Phase 10 decision 8): the respondent workspace includes "correction guidance naming the items and files a correction reopens".
Line 3570 (decision 9): exact evidence version preview for reviewers.
Line 3624 (P10-T3): correction guidance.
Line 3628-3629 (P10-T5): correction selection.
Line 3703 (exit criterion): "Review and correction identify exact Requirements and evidence versions."
Lines 3054-3070 (Phase 8 decisions 7-9): a finding names one exact Evidence Version; the [truncated]

Current state:

The backend stores the exact returned evidence versions for a correction (information_request_correction_evidence, with evidence_artifact_id and evidence_version_id) and sends them as bare UUIDs in InformationRequestCorrectionDto.evidenceVersionIds. No frontend surface turns those IDs into a file identity. The respondent's Review results card lists the reopened Requirements by label. For files it shows only a count: "N returned files can be replaced or withdrawn." The respondent's evidence panel (RequirementEvidencePanel / EvidenceArtifactRow) never gets the correction data. It does not mark which artifact or version was returned, and it shows Replace and Withdraw on every active artifact, so the respondent finds out which files are locked only when the server refuses. RespondentFinding also ignores finding.evidenceVersionId, so a file-scoped finding does not say which file it concerns.

The reviewer side is the same. ReviewCorrectionSummary shows only "N returned file versions may be replaced or withdrawn." ReviewItemFindings does not show a finding's evidence version. ReviewItemContent and ReviewFindingDialog label files only as "File version N", because the backend InformationRequestSubmissionEvidenceDto has no declared file name. Two artifacts in one Document Requirement that are both at version 1 therefore get identical labels. The only test that says it checks naming ("names returned files...") asserts just the count text. The help article says "only the files named by a finding can be replaced or withdrawn", but the UI never names those files to the respondent.

Evidence:

web-app/src/app/information-requests/review/respondent-review-card/RespondentReviewCard.tsx:60-78: requirement labels are listed; files appear only as a count from correction.evidenceVersionIds.length.
web-app/src/app/information-requests/review/review-correction-summary/ReviewCorrectionSummary.tsx:50-54: count only.
web-app/src/app/information-requests/review/respondent-finding/RespondentFinding.tsx:39-45: shows the requirement label, severity and narrative, never finding.evidenceVersionId.
web-app/src/app/information-requests/review/review-item-findings/ReviewItemFindings.tsx: shows no evidence version either.
web-app/src/app/information-requests/requirement-evidence/requirement-evidence-panel/RequirementEvidencePanel.tsx and evidence-artifact-row/EvidenceArtifactRow.tsx:41-130: take no correction input. Replace and Withdraw render for every ACTIVE artifact.
Grep: the only frontend reads of evidenceVersionIds are these two review files and PromoteFactDialog / outcomeLabels, which [truncated]

Fix outline:

No migration is needed; the data already exists in information_request_correction_evidence (V137). The fix is about M: roughly 8-10 files across backend, frontend, tests and help.

1. Backend: replace InformationRequestCorrectionDto.evidenceVersionIds with evidence: List<InformationRequestCorrectionEvidenceDto>. Each entry carries requirementId, artifactId, evidenceVersionId, versionNumber, declaredFileName and sourceKind or externalReferenceValue. Do not keep the old field alongside, since the plan forbids backwards-compatibility code. Build it in InformationRequestReviewDtoMapper.toDto(view) from view.evidence joined to the evidence version. Load the versions in InformationRequestReviewQueryService through the evidence owner's service method, not its repository. Keep the existing visibility filter by visible correction items. Also add declaredFileName to InformationRequestSubmissionEvidenceDto so reviewers see real file names.

2. Frontend models: update InformationRequestCorrectionDto and InformationRequestSubmissionEvidenceDto in web-app/src/app/models/models.tsx.

3. Respondent: in RespondentReviewCard, list each returned file under its Requirement ("<file name>, version N") instead of the count, using a small child component to stay under 150 lines. In RespondentFinding, show the file a file-scoped finding names. Put the open correction's returned versions into RequirementEvidenceContext, or a new prop, so EvidenceArtifactRow shows a "Returned for correction" badge on the matching artifact. While a correction is open, hide Replace and Withdraw on artifacts that were [truncated]

### GA-055: Clocks of finished requests stop late, at scheduler time, so SLA can say OVERDUE for a request met on time

- Severity: medium. Verification: confirmed. Fix size: M. Audit key: `G179-P9-T5`.
- Plan reference: P9-T5. Plan basis: Phase 9 decision 7 (plan lines 3266-3277), ending with "A clock stops when its request becomes terminal". Decision 8 (3278-3282) defines the MET/OVERDUE SLA statuses. The P9-T5 done note (3375-3381) says a finished request stops its clocks. Exit criterion (3484): "Clocks and reminders are deterministic across timezones and pause cycles." Nothing supersedes or defers it. The Status section (1-80), the Latest Implementation Result (4679+) and the non-goals do not mention asynchronous clock stops [truncated]

Current state:

Clocks do not stop when their request becomes terminal. No close, cancel or supersede path touches clocks: request satisfaction close, lifecycle cancel and supersede, Exchange-completion cancel and parent-lifecycle cancel all leave them alone. No DB trigger does it either, and no domain-event consumer. The only thing that stops a clock for a finished request is the 1-minute InformationRequestClockScheduler sweep. It picks clocks via findUnstoppedOfFinishedIds (batch 200, ordered by started_at) and calls InformationRequestClockPointProcessor.process(clockId, now), which calls recorder.stop(clock, SYSTEM, now, REQUEST_FINISHED or PARENT_FINISHED). "now" is the scheduler's instant, not the request's closedAt, cancelledAt or supersededAt. So stopped_at, the STOPPED event's occurredAt and its inputs {stoppedAt} all record the sweep time. InformationRequestSlaCalculator.statusOf returns OVERDUE when stoppedAt is after dueAt. A request closed at 09:59:30 with due 10:00 and swept at 10:00:30 is reported OVERDUE instead of MET, both in GET /information-request-operations and in its SLA filter. The same record gives wrong deadline proof: the actor is SYSTEM and the stop time is the sweep. Until the sweep runs, which can be longer than a minute with more than 200 backlogged clocks, the clock stays RUNNING for a finished request. During that window the SLA is computed against the live now (OVERDUE once now >= due), and the Exchange summary's nextDueAt still shows the finished request's due time. Exception: the EXPIRE path is correct, because expiry and stop share the same instant. A [truncated]

Evidence:

src/main/kotlin/com/docuhyphen/app/api/service/informationrequest/InformationRequestClockPointProcessor.kt:45-48 (terminal/parent-finished branch calls recorder.stop(clock, SYSTEM, now, ...) with the processing instant), :131 (expiry stop, same instant as expiredAt, correct). InformationRequestClockRecorder.kt:61-70 (stoppedAt = Timestamp.from(at); STOPPED event occurredAt = at, inputs stoppedAt = at). InformationRequestClockScheduler.kt:21-25 (every 1m, SKIP concurrent), :40-45 (now = clock.instant(); unstopped-of-finished + due-point ids), :61 (BATCH_SIZE 200). repository/informationrequest/InformationRequestClockRepositories.kt:137-154 (findUnstoppedOfFinishedIds, LIMIT, ORDER BY started_at). InformationRequestSlaCalculator.kt:39-40 (STOPPED -> OVERDUE if stoppedAt.isAfter(due)), :43-45 (RUNNING -> OVERDUE when now >= due, applies to finished requests before the sweep). InformationRequestOperationsService.kt:106-110 (queue standing via the calculator), :138-145 (ageOf already uses [truncated]

Fix outline:

1) Stop clocks synchronously on terminal transitions. Add a method on InformationRequestClockRecorder, or a small InformationRequestClockStopper in service/informationrequest: stopForTerminal(requestId, at: Instant, actor: PrincipalRef, reasonCode). It loads the request's clocks with findForUpdate, the request lock being already held, and stops each non-STOPPED clock at the terminal instant. Call it from every path that sets a terminal state: InformationRequestSatisfactionService.closeIfSatisfied (at = closedAt), InformationRequestLifecycleService cancel/supersede (cancelledAt/supersededAt), InformationRequestExchangeCompletionService and InformationRequestParentLifecycleService cancels (cancelledAt), and any successor/supersede path. Alternatively hook it once in InformationRequestTransitionHistoryService.record when toState.isTerminal && fromState != toState, excluding EXPIRE, which the processor already stops. Pass the transition actor, not SYSTEM. 2) Keep the scheduler sweep as a safety net, mainly for parent-finished cases such as a deleted or ended Exchange. In InformationRequestClockPointProcessor.process, stop at the request's terminal instant (closedAt/cancelledAt/supersededAt/expiredAt), or at the Exchange's end/deletion instant when one is recorded (else now). Clamp it to no earlier than clock.startedAt, and add processedAt = now to the STOPPED inputs so the record shows both. 3) SLA for paused clocks: when a PAUSED clock stops, treat it as MET unless overdueAt was recorded, rather than comparing stoppedAt to the stale pre-pause dueAt. Put this in the STOPPED [truncated]

### GA-056: Holds on Submission Packages, Evidence, Outbound Notices, or Exports are accepted but ignored by disposal

- Severity: medium. Verification: confirmed. Fix size: M. Audit key: `G184-P9-T9`.
- Plan reference: P9-T9. Plan basis: **Lines that put the requirement in force**
- P9-T9 (plan lines 3409-3432): preservation and disposal foundations for Information Requests, Submission Packages, Evidence Versions, referenced Document Versions, Outbound Notices, retained notice content, and exports, with descendant and reference-graph propagation.
- Background item 28 (lines 598-602): Phase 9 adds propagation and purge for requests, packages, evidence, Documents, notices, and exports.
- Security section (lines 4220-4222): [truncated]

Current state:

The API accepts a hold placed directly on a Submission Package, Evidence Artifact, Evidence Version, Outbound Notice, or Record Export, but disposal never checks it and deletes the held rows anyway.

**How holds are placed.** RecordPreservationHoldService.place accepts any non-blank resource type string, uppercases it and stores it without checking it. Both callers pass the type straight through:
- POST /record-preservation-holds, via RecordPreservationAdministration.placeHold
- the audit-governance AuditLegalHoldService.placeHold

**How disposal checks holds.** Disposal of an Information Request (by retention schedule or privacy deletion) asks one question: is any active hold covering one of the keys built by InformationRequestDisposalEligibility.scopeKeys? Holds are matched by exact resource type and id. The keys are:
- INFORMATION_REQUEST (direct)
- EXCHANGE, ORGANIZATION or APP_USER, and SUBJECT_IDENTITY, which count only when the hold has DESCENDANTS_AND_REFERENCES scope
- DOCUMENT and DOCUMENT_VERSION for stored objects that are not shared (direct)

RecordPreservationResourceTypes defines only these seven types. The same keys are written to record_disposal_claim_scope, so both DB guards compare holds against this set only:
- record_disposal_scope_guard (V142)
- audit_legal_hold_guard (V142)

**What happens to the held rows.** A hold on any of these ids returns 201 and is never matched: submission package id, information_request_evidence_artifact id, information_request_evidence_version id, information_request_outbound_notice id, or record export id. The disposal [truncated]

Evidence:

- RecordPreservationHoldService.kt:40-64: place stores command.resourceType through normalizedType.
- RecordPreservationHoldService.kt:168-169: normalizedType only trims and uppercases.
- RecordPreservationHoldService.kt:130-139: refuseWhileUnderDisposal only checks claim scope keys.
- RecordPreservationAdministration.kt placeHold and RecordPreservationHoldResource.kt POST: pass body.resourceType through unchecked.
- AuditLegalHoldService.kt:30-53: forwards any resourceType.
- RecordPreservationModels.kt:113-122: RecordPreservationResourceTypes has only INFORMATION_REQUEST, EXCHANGE, ORGANIZATION, APP_USER, SUBJECT_IDENTITY, DOCUMENT, DOCUMENT_VERSION.
- InformationRequestDisposalEligibility.kt:59-65 and 77-90: covering holds are computed only from these keys.
- RecordPreservationRepositories.kt findActiveCovering: exact type and id match. hasOpenClaimCovering: same key set. insertScope: persists only these keys.
- InformationRequestDisposalService.kt ~60-70: scopeKeys = [truncated]

Fix outline:

1. **RecordPreservationModels.kt:** add these types to RecordPreservationResourceTypes: SUBMISSION_PACKAGE, EVIDENCE_ARTIFACT, EVIDENCE_VERSION, OUTBOUND_NOTICE, RECORD_EXPORT.
2. **InformationRequestDisposalRepository:** add descendantKeys(requestId). It is a native query returning (type, id) for:
   - information_request_submission_package rows
   - information_request_evidence_artifact rows
   - information_request_evidence_version rows
   - information_request_outbound_notice rows
   - record exports: information_request_record_export where information_request_id = request, UNION export_id from information_request_record_export_source for the request

   This is the same set record_dispose_information_request deletes.
3. **InformationRequestDisposalEligibility.scopeKeys:** add each of these as direct = true, because disposal deletes the row itself.
4. **What this closes, with no migration:**
   - Holds placed first are refused as RECORD_HELD during assessment.
   - The existing generic guards then cover both race directions. record_disposal_scope_guard covers a claim opened after a hold. audit_legal_hold_guard and hasOpenClaimCovering cover a hold placed after a claim.
   - Only if the fix also adds reference guards stopping new packages, notices or exports on a claimed request would V151 be needed.
5. **Optional hardening:** in the placement path, check that an Information Request-family target id exists and belongs to the caller's owner, and refuse otherwise with a new RecordPreservationErrorCatalog code. Do this through an Information Request service method, not its [truncated]

### GA-057: ShareLink password, domain and MFA constraints are never enforced or rejected

- Severity: medium. Verification: partial. Fix size: S. Audit key: `G191-Baseline:`.
- Plan reference: G191-Baseline:. Plan basis: These put the requirement in force:
- Line 371 (Verified Repository Baseline, No-auth access row): the baseline notes the model "does not enforce every stored password or domain field" and responds "enforce every configured constraint".
- Lines 533-534 (Architectural Decision 24): "Every configured password, domain, MFA, Share, expiry, status, and use constraint must either be enforced or rejected as unsupported at creation."
- Lines 1842-1843 (P4-T4, marked [x]): "Enforce or reject every [truncated]

Current state:

The gap is real but narrower than the title says. MFA is enforced for DIRECT_GRANT links in two places: DefaultAuthorizationService.resolveLinkGrant and ShareLinkValidationService.validateForNoAuth. It is only missing on the bootstrap path. What stands today:
(1) share_link.password_hash and share_link.domain_allowlist, which date from the V1 baseline and map to ShareLink.passwordHash and ShareLink.domainAllowlist, are dead columns. No production code writes them and no production code reads them. None of the three places that resolve a link token check them: resolveLinkGrant (central authorizer), ShareLinkValidationService.validateForNoAuth (no-auth Exchange metadata path), and InformationRequestContactProofService.resolveBootstrapLink. A row that carries a password hash or a domain allowlist is treated as unrestricted.
(2) resolveBootstrapLink ignores require_mfa on VERIFICATION_BOOTSTRAP links and does not refuse a link that sets it.
(3) No database CHECK or service guard rejects these constraints as unsupported. No test covers the "unsupported-constraint rejection" the plan requires.
In practice the risk is latent rather than exploitable:
- The only production code that creates ShareLinks is InformationRequestBootstrapShareLinkService.issue and replace. Neither sets passwordHash, domainAllowlist or requireMfa.
- The issue and replace REST bodies accept only partyId, expiresAt and maxUses. Quarkus kotlin-serialization defaults to strict keys, so extra keys fail as a generic 400, not with a stable unsupported-constraint reason.
- No production code creates DIRECT_GRANT [truncated]

Evidence:

- src/main/kotlin/com/docuhyphen/app/api/model/entity/ShareLink.kt:55-56 (passwordHash), 65-66 (domainAllowlist), 68-69 (requireMfa). Grepping src/ for passwordHash|password_hash|domainAllowlist|domain_allowlist matches only this entity and V1__baseline.sql:730,733.
- DefaultAuthorizationService.kt:645-672: resolveLinkGrant checks linkMode, status, expiry, maxUses, requireMfa (line 665), Share effectiveness and resource. It never checks password or domain.
- service/exchange/ShareLinkValidationService.kt:51-102: validateForNoAuth checks status, expiry, maxUses, requireMfa (line 81) and Share. It does not check password, domain or linkMode.
- InformationRequestContactProofService.kt:122-178: resolveBootstrapLink checks linkMode, status, expiry, maxUses (only when enforceUseLimit is set) and whether the party is active. It checks neither requireMfa, passwordHash nor domainAllowlist, and rejects none of them.
- InformationRequestBootstrapShareLinkService.kt:160-171 (issue) and 283-295 [truncated]

Fix outline:

The plan permits the "reject as unsupported" route, and it is the smallest fix. Nothing in the product writes these values.
1. Migration V151__share_link_unsupported_constraints.sql:
   - DROP COLUMN password_hash and domain_allowlist from share_link. There is no writer or reader, and the Development-Stage Constraint forbids keeping dead shape.
   - ADD CONSTRAINT ck_share_link_bootstrap_no_mfa CHECK (link_mode <> 'VERIFICATION_BOOTSTRAP' OR require_mfa = false). Bootstrap strength comes from RequestAccessSession verification strength, not from link MFA.
   - An alternative is to drop require_mfa for bootstrap semantics entirely. It must stay for DIRECT_GRANT, which enforces it.
2. ShareLink.kt: remove passwordHash and domainAllowlist. Update the KDoc that promises "password-protected, domain-restricted" links.
3. InformationRequestContactProofService.resolveBootstrapLink: refuse a bootstrap link with requireMfa = true, using InformationRequestErrorCatalog.ACCESS_LINK_INVALID or a new stable ACCESS_LINK_UNSUPPORTED_CONSTRAINT code, as defence in depth.
4. InformationRequestBootstrapShareLinkService.issue and replace: add require(!requireMfa) guards, or leave the entity default and rely on the CHECK.
5. Optionally, ShareLinkValidationService.validateForNoAuth: refuse linkMode == VERIFICATION_BOOTSTRAP so it mirrors resolveLinkGrant.
6. Tests:
   - CleanSchemaMigrationContractTest: add assertions that the columns are gone and that ck_share_link_bootstrap_no_mfa refuses a bootstrap row with require_mfa = true.
   - InformationRequestContactProofServiceTest: a bootstrap link [truncated]

### GA-058: Execution grant is missing entitlement/policy version, trial grant reference, continuation actions and feature snapshot

- Severity: medium. Verification: confirmed. Fix size: M. Audit key: `GRANT-SNAPSHOT`.
- Plan reference: AD-14, P4-T7. Plan basis: These plan lines put the requirement in force:
- P4-T7, lines 1889-1908: the grant contains entitlement and policy version, the paid or trial source, the trial grant reference and issuance-time expiry, permitted continuation actions and expiry; "Snapshot both INFORMATION_REQUESTS and ... BUSINESS_FIELDS_AND_SCHEMAS into the grant at issuance and require both then".
- Lines 1910-1911: subtasks may not check the parent box until all are done.
- P4-T7a, lines 1920-1923: explicitly defers [truncated]

Current state:

`request_execution_grant` (V108, changed by V150) and the `RequestExecutionGrant` entity store only these columns: owner type and id, plan_code, subscription_status, enforcement_mode, trial_expires_at, mutation_allowance_expires_at, acting_party_cap, evidence_file_allowance, evidence_byte_allowance, revoked_at/revoked_reason, issued_at and created_at. Four things the P4-T7 task text asks for are not there:
(1) An entitlement or policy version. The subscription domain has no policy or catalog version at all: PlanCatalog is code-owned, and no version column exists in V71 or on EffectiveSubscription.
(2) A trial grant reference. Nothing references `subscription_trial_grant` (V74). "Trial" is recorded only as subscription_status='TRIALING' plus trial_expires_at. So the paid-or-trial source is covered in part, but whether a feature came from a platform-admin override is never recorded.
(3) Permitted continuation actions. They exist only as code-wide gates: `requireContinuationEntitlement` in several services, plus the execution-standing kinds. No grant stores them.
(4) A feature snapshot. Issuance does require both features in the same transaction. `INFORMATION_REQUESTS` is checked by `entitlementGuard.requireRequestMutation` in `LifecycleService.mutate()` before `issueGrant` runs. `BUSINESS_FIELDS_AND_SCHEMAS` is checked in `issueGrant` for organization owners whose Template has a Field-bound Requirement. Neither result is saved. `InformationRequestFieldResourceAdapter.mutationEntitlementFrozen` returns true whenever any grant exists, so the grant never says which features it [truncated]

Evidence:

- src/main/resources/db/migration/V108__request_execution_grant.sql lines 1-29: the full column list. No version, trial-grant, continuation or feature columns.
- src/main/resources/db/migration/V150__request_execution_quotas.sql lines 1-3: renames the cap and adds evidence_file_allowance and evidence_byte_allowance. Lines 22-49: the `request_execution_grant_frozen` trigger compares an explicit column list.
- src/main/kotlin/com/docuhyphen/app/api/model/entity/RequestExecutionGrant.kt lines 22-70: fields match the migrations exactly.
- src/main/kotlin/com/docuhyphen/app/api/service/informationrequest/InformationRequestExecutionGrantService.kt lines 50-55: requires BUSINESS_FIELDS_AND_SCHEMAS but persists nothing about it. Lines 64-84: the grant builder sets only planCode, subscriptionStatus, enforcementMode, trialExpiresAt (only when TRIALING), mutationAllowanceExpiresAt and the three caps.
- InformationRequestLifecycleService.kt lines 209-221: `requireRequestMutation`, which includes [truncated]

Fix outline:

1. Migration V151__request_execution_grant_provenance.sql:
   - Add `policy_version VARCHAR(32) NOT NULL`: a code-owned `PlanCatalog.POLICY_VERSION` constant, bumped whenever plan limits or features change.
   - Add `entitlement_source VARCHAR(16) NOT NULL` with CHECK IN ('PAID','TRIAL','OVERRIDE','UNENFORCED').
   - Add `trial_grant_id UUID REFERENCES subscription_trial_grant(id)`, plus CHECK (entitlement_source <> 'TRIAL' OR trial_grant_id IS NOT NULL OR trial_expires_at IS NOT NULL).
   - Add `continuation_actions TEXT[]` or a child table `request_execution_grant_continuation_action` with a closed CHECK vocabulary, for example RESPOND, SUBMIT, ATTEST, REVIEW, ASSIGN_PARTY_WITHIN_CAP, UPLOAD_WITHIN_ALLOWANCE, NON_EXPANDING_AMENDMENT, CANCEL.
   - Add `granted_features TEXT[] NOT NULL` with CHECK that it contains 'INFORMATION_REQUESTS' and every element is IN ('INFORMATION_REQUESTS','BUSINESS_FIELDS_AND_SCHEMAS'). Alternatively use a child table `request_execution_grant_feature`.
   - Backfill existing local rows deterministically (dev-stage, no compatibility shims). Then `CREATE OR REPLACE FUNCTION request_execution_grant_frozen()` so the ROW comparison includes every new column, and the new columns are frozen too.
2. Entity RequestExecutionGrant.kt: add the matching fields (arrays via a converter or child entities in model/entity).
3. InformationRequestExecutionGrantService.issueGrant:
   - Record `grantedFeatures`: INFORMATION_REQUESTS always. BUSINESS_FIELDS_AND_SCHEMAS when the organization Template has Field-bound Requirements, or for Personal owners per Decision [truncated]

Decision needed. Recommended default: Implement it: a code-owned plan-policy version constant, a closed continuation-action vocabulary stored per grant, and a feature snapshot that the Field adapter and amendments check.

### GA-059: No real concurrent-edit test for group occurrence add, remove, or reorder

- Severity: medium. Verification: confirmed. Fix size: M. Audit key: `OCC-CONCURRENCY-TESTS`.
- Plan reference: P5-R10, P5-T2c. Plan basis: What puts the requirement in force:
- Plan line 2126-2127 (P5-T2c): add, remove, and reorder "with concurrent-edit coverage".
- Line 2209 (Tests to write first): "Repeatable group add, remove, reorder, and concurrent-edit tests."
- Lines 2286-2289 (P5-R10, a mandatory remediation checked as complete): "Add PostgreSQL coverage for remove, reorder, then add, including concurrent adds and correct Field Value Set paths. Restore the review's duplicate-path probe as permanent coverage."
- Line 4184 [truncated]

Current state:

The production code is correct. It is the tests that fall short of what the plan asks for.

How the code serializes: InformationRequestGroupOccurrenceService.mutate first locks the parent Exchange, then the request row (findRequestByIdForUpdate). It checks the response-shape ETag. It then locks sibling occurrences FOR UPDATE. Paths get a stable identity index that is never reused, and V112 backs this with UNIQUE (information_request_id, occurrence_path).

The test gaps as they stand today:
(1) Concurrent-edit coverage for P5-T2c exists only as one mocked optimistic-concurrency case: a stale ETag refuses add, in InformationRequestGroupOccurrenceServiceTest, which is a Mockito unit test. No service-level stale-ETag case exists for remove or reorder. The resource contract test only maps a mocked STALE exception from remove to 412. No test runs two transactions against the real service for add, remove, or reorder.
(2) For P5-R10, the only PostgreSQL service flow is RepeatableConditionalRequestConformanceTest. It covers add, add, nested add, remove, add, and checks that a removed path is not reused. It never reorders and never adds concurrently. The remove, reorder, then add scenario exists only as a mocked unit test. The PostgreSQL persistence contract test checks the duplicate-path probe and display-order changes with raw SQL inserts and never calls the service. No test in the repository that uses CountDownLatch, Executors or CompletableFuture touches group occurrences.

The review's duplicate-path probe (constraint level) is covered.

Evidence:

- src/test/kotlin/com/docuhyphen/app/api/service/informationrequest/InformationRequestGroupOccurrenceServiceTest.kt: a plain JUnit class with no @QuarkusTest, and every collaborator is a mock (lines 809-831).
  - Line 310: `adding after removal and reorder keeps a new stable occurrence path`, mocked.
  - Line 356: `a stale response-shape precondition refuses before touching group occurrences`, which covers add only. A grep for stale/Precondition shows no stale case for remove or reorder.
- src/test/kotlin/com/docuhyphen/app/api/migration/InformationRequestRuntimePersistenceContractTest.kt
  - Line 447: `group occurrence display order can change while stable paths remain unique`.
  - It inserts rows through insertGroupOccurrence raw SQL (line 886) and asserts the ux_request_group_occurrence_path refusal (line 492). It never calls the service.
- src/test/kotlin/com/docuhyphen/app/api/service/informationrequest/conformance/RepeatableConditionalRequestConformanceTest.kt: @QuarkusTest with [truncated]

Fix outline:

This is a test-only fix. No production change and no migration are needed, so V151 stays unallocated.

1. Add src/test/kotlin/com/docuhyphen/app/api/service/informationrequest/InformationRequestGroupOccurrenceConcurrencyTransactionTest.kt.
   - Annotate it @QuarkusTest with @QuarkusTestResource(DocumentVersionStoragePostgreSQLResource or RequestTemplatePostgreSQLResource).
   - Publish a template with PublishedRequestSupport, following RepeatableConditionalRequestConformanceTest. Use one repeatable root group (min 0, max e.g. 3) with a Field Requirement, and inject the real InformationRequestGroupOccurrenceService.
   - Case A, sequential remove, reorder, then add on PostgreSQL:
     - Add three occurrences, remove the middle one, reorder the remaining two, then add again.
     - Assert that the new path is items[3] and that no path is reused.
     - Assert that the display indexes are contiguous.
     - Assert that the runtime Requirement rows exist at the new path.
     - Query field_value_set.occurrence_path to show the new occurrence's Field Value Set exists at items[3] and that the removed occurrence's set is still addressable.
     - Assert that the response ETag advances after each command.
   - Case B, concurrent adds:
     - Run two threads from Executors.newFixedThreadPool(2). Each calls add inside QuarkusTransaction.requiringNew with the same expected ETag.
     - Hold the first transaction after it takes the Exchange lock, using a CountDownLatch gate or the pg_stat_activity lock-waiter wait used by awaitLockWaiterOrCompletion in [truncated]

### GA-060: Manual reminders are still sent for requests under operational suspension or with a revoked grant

- Severity: medium. Verification: confirmed. Fix size: M. Audit key: `P12#0`.
- Plan reference: P12. Plan basis: In force:
- Plan lines 3982-3987 (Phase 12 Decision 5): commercial lapse, a removed feature, operational suspension and grant revocation "refuse mutations with a stable reason", and every request projection states an execution standing that the UI shows.
- Lines 4083-4086 (P12-T4a): operational suspension refuses changes whatever the enforcement mode, and listing rows state their standing.
- Line 4097-4098 (P12-T4f): follow-up controls are hidden unless the request is ACTIVE.
- Line 4077-4081 [truncated]

Current state:

`POST /information-request-reminders` (InformationRequestReminderService.send/remind) never checks the execution standing. It checks owner-scope access (INFORMATION_REQUEST_SEND_REMINDERS), ownership, the reminder cooldown and the transition matrix (`gate.requireMutation(locked, SEND_REMINDER)`). The matrix allows SEND_REMINDER in ISSUED and IN_PROGRESS no matter the subscription status or grant. Revoking a grant only sets `revokedAt` and leaves the request state unchanged, so a revoked request stays ISSUED or IN_PROGRESS.

So for an operationally SUSPENDED owner, or a request whose execution grant is revoked, a manual reminder still does three things:
- writes a SEND_REMINDER transition-history row,
- saves a RESPONSE_REMINDER notice intent for every active responding party,
- has the notice dispatcher render and email those intents with no standing check.

The parties are then refused on every answer, through `requireContinuationEntitlement` in the response, draft, evidence and submission services. Every other owner and respondent mutation path calls `gate.requireContinuationEntitlement` or `entitlementGuard.requireNotOperationallySuspended`. The lifecycle service checks suspension and revocation even after a grant exists. The reminder service is the outlier.

The operations queue cannot compensate:
- `InformationRequestOperationsRowDto` (and the TS `InformationRequestOperationsRowDto`) carries no `executionStanding`, although Decision 5 says every request projection states one.
- `OperationsQueue.tsx` makes every row selectable whenever `canSendReminders` is true, so [truncated]

Evidence:

- src/main/kotlin/com/docuhyphen/app/api/service/informationrequest/InformationRequestReminderService.kt:48-63. `send` does ownerAccess.requireAccess, then requireOwned, then gate.lock, then remind. It injects no entitlement guard or grant service.
- InformationRequestReminderService.kt:85-94. Only the cooldown check comes before runOnce, and inside runOnce the only guard is `gate.requireMutation(locked, InformationRequestMutation.SEND_REMINDER)`.
- InformationRequestReminderService.kt:95-116. It records the SEND_REMINDER transition and saves a RESPONSE_REMINDER InformationRequestNoticeIntent per responding party (SUBJECT, CONTRIBUTOR, PREPARER, ATTESTOR).
- InformationRequestMutationGate.kt:32-51. requireMutation only evaluates InformationRequestTransitionMatrix.canMutate.
- InformationRequestMutationGate.kt:53-69. requireContinuationEntitlement, which checks suspension and then throws EXECUTION_GRANT_REVOKED, exists but is not called by the reminder service.
- [truncated]

Fix outline:

Backend:
1. In InformationRequestReminderService.remind, call `gate.requireContinuationEntitlement(locked)` before the cooldown check, only when `!commandReceiptService.isRecorded(receipt)`. A replay of a reminder already recorded stays a read-only replay. Also call it inside runOnce, before `transitionHistory.record`, so a racing first execution is also refused. SEND_REMINDER only runs in ISSUED or IN_PROGRESS, which always hold a grant, so this reduces to the suspension check plus the EXECUTION_GRANT_REVOKED check. A suspension (SubscriptionDenialException) or revocation (InformationRequestLifecycleException EXECUTION_GRANT_REVOKED) aborts the whole @Transactional batch with the stable reason. That matches how every other mutation refuses. Suspension is owner-wide anyway, since all requests in a batch share one owner. If per-request skipping of revoked requests is preferred instead, add a `refusedReason` to InformationRequestReminderResult, the DTO and reminderOutcome.ts, as the cooldown result does.
2. InformationRequestOperationsService: inject InformationRequestExecutionStandingService and compute `ownerStanding(...)` once per queue call, since the owner is fixed. Put `standingOf(request, ownerStanding)` on InformationRequestOperationsRow, InformationRequestOperationsRowDto (`executionStanding: InformationRequestExecutionStandingDto`) and InformationRequestOperationsDtoMapper. Give the reason code to the manager only, which the queue caller always is.

Frontend:
3. Add `executionStanding` to InformationRequestOperationsRowDto in web-app/src/app/models/models.tsx.
4. [truncated]

### GA-061: Record exports and subject exports omit privacy item corrections

- Severity: medium. Verification: confirmed. Fix size: S. Audit key: `PRIVACY-EXPORT`.
- Plan reference: P9-Decision-9 / P9-Decision-12, P9-T7 / P9-T10. Plan basis: Decision 9 at plan lines 3283-3288 (record export freezes who requested, provided, reviewed, changed, and decided each item). Decision 12 at 3311-3317 (access/export produce the subject record export; correction appends a correction record against a package item). The refinement at 3335-3337 narrows correction to submitted package items only and does not touch exports. P9-T10 at 3433-3439. Exit criterion at 3485 ("Audit and export reconstruct who ... changed ... each item"). Phase 11 decision [truncated]

Current state:

Privacy item corrections (table information_request_item_correction, V143) are written and audited, but no record export includes them. InformationRequestRecordAssembler builds both the request record export (freeze) and the subject access/export record (createSubjectExport, which just wraps assembler.assemble per request). It never reads information_request_item_correction. Its "corrections" section covers only review-cycle corrections (InformationRequestCorrectionRepository), and itemOf emits only the original frozen field value and narrative. A correction is also not a request transition, so the exported "history" does not show it either. Result: after a correction, a request record export or a subject access/export still shows only the uncorrected answer. Nothing in the export records that a correction was made, who made it, when, why, or what the corrected content is. The only places a correction can be read back are the privacy-request view (InformationRequestPrivacyService.view, which attaches the one correction linked to that privacy request) and the audit outbox event information_request.item.correct. That breaks decision 9's "who changed each item" promise for exports, decision 12's requirement that access/export produce the subject's record, and the exit criterion that "Audit and export reconstruct who ... changed ... each item" (audit covers it, export does not). The help text also says the export freezes the whole record. On claim 2's extra points: the code matches the description. InformationRequestItemCorrectionService.correct stores input.value?.toString() [truncated]

Evidence:

src/main/kotlin/com/docuhyphen/app/api/service/informationrequest/InformationRequestRecordAssembler.kt:40-63 (constructor has no InformationRequestItemCorrectionRepository or correction service), :65-81 (assemble sections: no item/privacy corrections), :179-217 (itemOf emits original fieldValueRevision value and narrative only), :319-346 (correctionsOf reads only review corrections via InformationRequestCorrectionRepository/CorrectionItemRepository), :501 (SCHEMA_VERSION = 2). InformationRequestRecordExportService.kt:119-140 (createSubjectExport content = assembler.assemble per request), :190 (freeze uses same assembler). InformationRequestItemCorrectionService.kt:43-69 (no requirementType/valueType check, correctedValueJson = input.value?.toString() at :62, no gate.lock), :70-89 (audit event information_request.item.correct). InformationRequestPrivacyService.kt:57-78 (ACCESS/EXPORT -> createSubjectExport; CORRECTION -> corrections.correct), :195-205 (only reader of [truncated]

Fix outline:

1) Add a read method to InformationRequestItemCorrectionService, e.g. correctionsFor(requestId): List<InformationRequestItemCorrection>, delegating to InformationRequestItemCorrectionRepository.findForRequest. This keeps the repository owned by its service and follows the backend rule that services talk through methods. 2) Inject it into InformationRequestRecordAssembler and add an "itemCorrections" section, ordered by recordedAt then id, with correctionId, packageId, submissionItemId, privacyRequestId, correctedValue (parse correctedValueJson with Json.parseToJsonElement, or JsonNull), correctedNarrative, reasonCode, recordedByPrincipalKind, recordedByPrincipalId, and recordedAt. Optionally also nest a "corrections" array of correction IDs under each item in itemOf so readers can find a correction from the item. 3) Bump InformationRequestRecordAssembler.SCHEMA_VERSION to 3. No compatibility reader is needed (development-stage constraint). 4) No migration needed; the table already exists (V143). V151 stays free. 5) Tests: extend InformationRequestPrivacyTransactionTest so that after a CORRECTION it submits ACCESS and EXPORT privacy requests and asserts the subject export content contains the correction with its corrected value, reason, actor, and time, while the item's original value is unchanged. Add an assertion that a request record export (InformationRequestRecordExportService.create) made after a correction includes itemCorrections. Update InformationRequestImportedValueTransactionTest.kt:328, which asserts schemaVersion 2, to expect 3. Optionally extend the S8 [truncated]

### GA-062: Party reassignment has a frontend service but no UI, while help docs describe it

- Severity: medium. Verification: confirmed. Fix size: M. Audit key: `REASSIGN-UI`.
- Plan reference: P10-T2, P3-T11. Plan basis: Plan lines 1584-1632 (P3-T11, T11a-d) define reassignment for replacing recipients mid-request. They say it must stay usable while a request is issued or in progress (1596-1602) and must keep completed work on the stable party (1629-1632). Line 395 says to generalize replacePrimaryRecipient into party reassignment. Phase 10 decision 4 (3537-3543) sets POST .../parties/{partyId}/reassignment and .../revocation as the parties API the author UI builds on. The Phase 10 goal (3499-3503) is to [truncated]

Current state:

The backend reassignment command is fully implemented and tested: POST /information-requests/{id}/parties/{partyId}/reassignment runs InformationRequestPartyService.reassign, which takes the parent lock, checks the transition matrix, cuts off bootstrap links and sessions, and keeps completed work on the same party. The frontend wrapper reassignInformationRequestParty also exists. Nothing in the app calls it, though. Its only caller is its own service unit test. useAuthorWorkspace exposes issue, cancel, supersede, assignParty, assignSubject, revokeParty and the three link actions, but has no reassign action. PartyRow and PartyPanel offer only Create link, Resend link, Revoke link, Remove and Add party. So an author cannot reassign a party from the UI. The only way to replace one is Remove plus Add party. That creates a new party row, so the work already completed stays on the old, inactive party instead of carrying over, which defeats the P3-T11d stable-party design. Two help articles still describe reassignment as something users do: informationRequestManagingArticle.tsx:45 and informationRequestAccessArticle.tsx:32. A second, smaller mismatch: the REST reassignment request accepts only userId or principalGroupId. The author UI's "person" holder works by email, which creates an owner-scoped External Participant. That means a UI reassignment to a person by email cannot be done today without also extending the backend request.

Evidence:

web-app/src/services/informationRequestAuthoringService.ts:90-101 defines reassignInformationRequestParty, which POSTs to /parties/{partyId}/reassignment with If-Match. The only reference outside the file is web-app/src/services/__tests__/informationRequestAuthoringService.test.ts:20,87. I searched all of web-app, excluding node_modules and dist, for "reassign" (case-insensitive). The only hits are the models, that service, its test, and help articles. web-app/src/app/information-requests/authoring/author-workspace/useAuthorWorkspace.ts:76-107 has no reassign member in the returned object. web-app/src/app/information-requests/authoring/party-row/PartyRow.tsx:13-23 props are onIssueLink, onResendLink, onRevokeLink and onRemove, and the buttons at 76-116 match. web-app/src/app/information-requests/authoring/party-panel/PartyPanel.tsx:14-27 and 77-89 pass through only those props, and InformationRequestAuthorWorkspace.tsx:90-100 wires onRemove to revokeParty. The TS model [truncated]

Fix outline:

No migration is needed.

1) Hook: in web-app/src/app/information-requests/authoring/author-workspace/useAuthorWorkspace.ts, add reassignParty(party, request). It calls authoring.reassignInformationRequestParty(requestId, party.id, request, parties.partiesETag, key) through runner.run, with key `reassign:${party.id}:${JSON.stringify(request)}:${parties.partiesETag}` and the notice "The party was reassigned."

2) New folder authoring/reassign-party-dialog/ with ReassignPartyDialog.tsx and ReassignPartyDialogStyles.tsx:
- Reuse usePartyCandidates and a holder picker built like PartyHolderFields.
- Footer: primary "Reassign" and secondary "Cancel" at the bottom right.
- Circular buttons, ids on every element, one attribute per line, and a responsive layout.
- Explain in the dialog that the party keeps its completed answers and that its current access links and sessions end.

3) PartyRow.tsx: add an onReassign prop and a circular "Reassign" button (id `${idPrefix}-reassign`) for acting parties only. Hide it for roleKey SUBJECT, since the backend refuses to reassign a subject.
- Thread the prop through PartyPanel.tsx.
- Wire it in InformationRequestAuthorWorkspace.tsx to state.reassignParty.

4) Backend, so the email holder the UI already uses also works for reassignment:
- Add optional email and displayName to ReassignInformationRequestPartyRequest (resource/model/InformationRequestRequests.kt) and to the TS model at models.tsx:4234.
- In InformationRequestPartyResource.reassign, require exactly one of userId, principalGroupId or email.
- Add an InformationRequestPartyService [truncated]

### GA-063: Automatic retention disposal can starve behind held or referenced requests and re-audits denials every pass

- Severity: medium. Verification: confirmed. Fix size: M. Audit key: `RETENTION-STARVE`.
- Plan reference: P9-Decision-11, P9-T9. Plan basis: Where the plan sets the requirement:
- Lines 3298-3309 (Phase 9 decision 11): automatic disposal age, and "Every claim, denial, object deletion, and finalization is audited".
- Lines 3409-3429 (P9-T9): "a retryable purge job using existing scheduling", "Every claim, denial, retry ... is audited and reproducible".
- Lines 3486-3488 (exit criteria): "Purge removes eligible database and stored objects through an idempotent, audited, retryable path".
- Lines 4221-4222: enforce holds and [truncated]

Current state:

Both claims describe the same defect, and both are accurate. Each hourly pass of the automatic retention disposal job loops over the owner schedules that have a disposal age. For each owner it takes only the first 50 finished requests past the cutoff, ordered by createdAt then id. The query does not skip requests that will be refused, and there is no cursor or paging past them. Refusal reasons are a covering hold (RECORD_HELD), a live reference such as a lineage successor, recurrence series, superseded request, carry-forward, fact successor, assessment reuse, account link or template in use (RECORD_REFERENCED), or an open disposal claim (DISPOSAL_IN_PROGRESS). A refused request stays in the same top-50 window on every pass. Once an owner has 50 or more long-lived refused requests created before its eligible ones, the eligible ones are never claimed. This happens easily with a recurring series or an Exchange-scoped hold that covers many requests. That breaks the retryable purge path (P9-T9, exit criterion at 3487) and contradicts the help article, which says a request is disposed once its age has passed, no hold covers it and nothing depends on it. Each refusal also writes a new record.disposal.denied audit row. Its idempotency key ends with clock.instant(), so AuditRecorder's dedupe never matches. The result is up to 50 identical denial audits per owner per hour for as long as the hold or reference lasts. The scheduler logs only when finalized or pending is above 0, and the platform health report only counts stalled claims (DISPOSAL_CLAIMS_STALLED). So the starvation [truncated]

Evidence:

- InformationRequestDisposalWorker.kt: line 31 `run(limit = BATCH_SIZE)`, line 72 `BATCH_SIZE = 50`. Line 44 makes a single call to `requestRepository.findFinishedIdsBefore(ownerType, schedule.ownerId, cutoff, limit)` per schedule, with no loop or cursor. Line 47 claims each id and line 55 only counts `Refused`, so nothing advances past it.
- InformationRequestRepository.kt:103-128: `state IN :finished AND COALESCE(closedAt, cancelledAt, supersededAt, expiredAt) < :finishedBefore ORDER BY request.createdAt, request.id` with `setMaxResults(limit)`. There is no NOT EXISTS against record_disposal_claim, no hold or reference exclusion, and no offset or keyset parameter.
- InformationRequestDisposalEligibility.kt:38 (DISPOSAL_IN_PROGRESS when any claim exists), :64 (RECORD_HELD), :70 (RECORD_REFERENCED, from InformationRequestDisposalRepository.liveReferences:14-54). These are persistent conditions: successor lineage, recurrence origin, account link, template in use and so on.
- [truncated]

Fix outline:

1. InformationRequestRepository.findFinishedIdsBefore
   - Add keyset cursor parameters (afterCreatedAt: Timestamp?, afterId: UUID?) with `(request.createdAt > :c OR (request.createdAt = :c AND request.id > :id))`.
   - Add `AND NOT EXISTS (SELECT 1 FROM RecordDisposalClaim claim WHERE claim.resourceType = 'INFORMATION_REQUEST' AND claim.resourceId = request.id)` so requests with an open claim, which openClaimIds already retries, are never re-assessed or re-denied.

2. InformationRequestDisposalWorker.run
   - For each schedule, page with the cursor until a page returns fewer than the page size or a per-run claim budget (limit) is reached. Advance the cursor past every refused or failed id so refused requests can never fill the batch.
   - Each page gets its own requiringNew read transaction, as today.
   - Keep the claim budget per run. Scanning continues only while claims are below budget.

3. InformationRequestDisposalService.assessAndOpen
   - When basis == RETENTION_SCHEDULE and actor == SYSTEM, build a stable denial idempotency key: `RECORD_DISPOSAL_DENIED|requestId|basis|reasonCode|scheduleId|sorted(holdId:holdRevision)|sorted(referenceKinds)`.
   - To get the reference kinds, extend InformationRequestDisposalAssessment.Refused (model/informationrequest) with a references list populated at InformationRequestDisposalEligibility:70.
   - Result: an unchanged automatic denial is audited once, and a changed reason (new hold, hold revision, new reference set, new schedule version) is audited again.
   - Keep the time-based key for operator and privacy-request calls, [truncated]

### GA-064: Evidence gate and version recorder use other domains' repositories directly

- Severity: medium. Verification: confirmed. Fix size: L. Audit key: `RULE-REPO`.
- Plan reference: AGENTS backend rules / Decision 7, AGENTS.md backend rules, AGENTS.md backend rules (P5-R remediation services), Backend rules, CDM-PackageAndLayeringRules, P1-T1, P1-T7, P11-T12 / P11-T1c (AGENTS.md [truncated]. Plan basis: These plan lines put the requirement in force:
- Line 742: "Services communicate through service methods and never use another service's repository directly."
- Lines 1497-1498 (P3-T6): "expose only dedicated Fields service methods to the Information Request domain".
- Lines 434-436 (Decision 7): Field access goes through Fields service operations.
- Lines 974-980 (P1-T7): the generalized Fields read contract.
- Lines 102-103: read AGENTS.md first.

Nothing supersedes it. I checked:
- the [truncated]

Current state:

The Information Request (IR) domain breaks the plan's own layering rule (plan line 742) and the AGENTS.md backend rule. 24 files in service/informationrequest make 41 imports of repositories that other domains own. Nothing in the plan waives or narrows the rule, and no architecture test enforces it.

(1) Exchange: 15 files inject repository.exchange.ExchangeRepository: AdHocCreation, BlueprintInstantiation, BootstrapShareLink, EvidenceGate, ExchangeSummary, GroupOccurrence, Lifecycle, MutationGate, NoticeDispatcher, ParentLock, Party, Query, ResponseDraft, SubmissionQuery and TemplateInstantiation. They either call findById or findByIdForUpdate directly, or go through the internal helper lockParentExchangeOf(requestId, requestRepository, exchangeRepository). Claim P10 says 18 files; the real count is 15. ContactProof, ParticipantAccountUpgrade and BootstrapShareLink inject ShareLinkRepository. ContactProof and ParticipantAccountUpgrade also inject ExternalParticipantRepository, even though ExternalParticipantService and ShareLinkValidationService exist. ParticipantAccountUpgradeService also copies the bootstrap revocation loop (lines 158-162) instead of calling InformationRequestBootstrapShareLinkService.revokeAllForShare (lines 330-336).

(2) Fields: six services inject SchemaAssignmentRepository, FieldValueSetRepository, FieldValueRepository, FieldValueSelectionRepository, FieldContractRepository and SchemaFieldBindingRepository: ConditionEvaluation, CompletenessProgress, FactRecertification, GroupOccurrence, ImportedValueCanonicalizer and ResponseDraft. They [truncated]

Evidence:

Imports of other domains' repositories (counted by grep): 24 files and 41 import lines in service/informationrequest. Every other service package has 23 or fewer such imports; auth has 23 and workflow 8.

Exchange:
- InformationRequestParentLock.kt:4,13-29: exchangeRepository.findByIdForUpdate at line 24.
- InformationRequestEvidenceGate.kt:7,24,35.
- InformationRequestMutationGate.kt:26.
- InformationRequestPartyService.kt:613.
- InformationRequestLifecycleService.kt:197.
- InformationRequestGroupOccurrenceService.kt:95,319.
- InformationRequestResponseDraftService.kt:106,159,616.
- InformationRequestAdHocCreationService.kt:103, InformationRequestBlueprintInstantiationService.kt:72 and InformationRequestTemplateInstantiationService.kt:90, all findByIdForUpdate.
- InformationRequestQueryService.kt:29,65.
- InformationRequestSubmissionQueryService.kt:115.
- InformationRequestNoticeDispatcher.kt:169. ExchangeRetrievalService.kt:108 getExchangeNameForDisplay already exists for this.
- [truncated]

Fix outline:

No migration is needed; this is refactoring only. V151 stays free.

1. Exchange domain: add an Exchange service, or extend ExchangeRetrievalService, with:
   - findExchange(id)
   - lockExchangeForUpdate(id)
   - exchange name and status reads

   Replace every ExchangeRepository use in the 15 IR files with these methods. Change lockParentExchangeOf to take the Exchange service instead of ExchangeRepository, so the parent-before-request lock order stays the same. NoticeDispatcher should call ExchangeRetrievalService.getExchangeNameForDisplay.

2. ShareLink and ExternalParticipant:
   - Add to a ShareLink service in service/exchange (for example ShareLinkValidationService or a new ShareLinkCommandService): findByTokenHash, findByTokenHashForUpdate, findByIdForUpdate, findBootstrapLinksForShares, findActiveBootstrapLinksForShare, save, update and revoke.
   - Use ExternalParticipantService (findOwned, or a new findById) in ContactProofService and ParticipantAccountUpgradeService.
   - Replace the upgrade service's revocation loop (lines 158-162) with InformationRequestBootstrapShareLinkService.revokeAllForShare.

3. Fields domain: add a Fields query service in service/fields, modelled on ExchangeFieldQueryService, that serves any FieldsResourceRef. It needs:
   - findAssignment(resource)
   - rootValueSetId and occurrenceValueSetId
   - canonicalValues(valueSetIds), keyed by fieldDefinitionId
   - resolveFieldContract(schemaVersionId, fieldDefinitionId), returning the contract, value type and scale
   - fieldDefinitionIdForContract

   Move the ConditionEvaluation, [truncated]

### GA-065: toDto logic and request DTOs remain inside the Fields Definition services

- Severity: medium. Verification: confirmed. Fix size: M. Audit key: `RULE-TODTO-LAYERING`.
- Plan reference: Coding and backend rules, P1-T8, P6-T4 / P6-T5. Plan basis: P1-T8 is at plan lines 1003-1012. Its subtask P1-T8a covers only the value, binding and assignment projections, and the exit criterion at line 1091 is limited to SchemaAssignmentService. Lines 736-741 set the program's model layout: mappers go in the flat model package and never in resources or services, "Existing mapper locations outside this program are not reorganized", and "REST resources remain thin adapters". The Model layout row at line 401 keeps DTOs and entities in their flat packages. [truncated]

Current state:

Claim 1 is partial. P1-T8 only targets SchemaAssignmentService, and that part is done: SchemaAssignmentService has no toDto left and delegates to SchemaAssignmentDtoMapper (line 678). SetFieldValuesRequest and AssignSchemaRequest now live in resource/model/FieldsRequests.kt. The toDto extensions and request DTOs in FieldDefinitionService and SchemaDefinitionService are older than this program: they already existed at commit 5c71fed2, before the IR program started. Plan line 740 says existing mapper locations outside the program are not reorganized. So those are an older AGENTS.md hygiene issue, not a P1-T8 gap. What P1-T8 does leave behind is FieldValueEntry. It is the @Serializable element of the SetFieldValuesRequest body and was one of the request DTOs moved out of SchemaAssignmentService, but it went to service/fields/FieldsCommands.kt:48-53. That file is a data-class-only file in a service package, not a model package.

Claim 2 is confirmed. InformationRequestResponseDraftService.patch returns every active response on the request (activeResponses(request.id) at lines 278 and 646). Deciding what the caller may see is left to the REST adapters: InformationRequestResponseResource.ok (lines 93-105, filter at 96) and InformationRequestNoAuthRequestResource.ok (lines 311-323, filter at 314) each filter down to the Requirements named in the caller's patch. The two copies are identical, and the class KDoc names this filter as the rule that stops one respondent from reading another party's responses. Separately, 13 service/evaluator files in service/informationrequest declare [truncated]

Evidence:

Claim 1:
- service/fields/FieldDefinitionService.kt:320-353 has private toDto methods; line 322 calls fieldContractRepository.findByDefinition. Lines 358-379 hold FieldContractRequest and CreateFieldDefinitionRequest.
- service/fields/SchemaDefinitionService.kt:450-499 has toDto and resolvedView, with findDraft/findLatestPublished calls. Lines 502-539 hold ValidatedSchemaDefault, BindingRequest, CreateSchemaRequest, PublishSchemaRequest and UpdateBindingsRequest.
- `git show 5c71fed2:.../FieldDefinitionService.kt` and `git show 5c71fed2:.../SchemaDefinitionService.kt` show the same toDto methods and request classes before the IR program.
- `git show 5c71fed2:.../SchemaAssignmentService.kt` shows FieldValueEntry, SetFieldValuesRequest and AssignSchemaRequest at lines 303-316.
- Today FieldValueEntry is at service/fields/FieldsCommands.kt:48-53. It is imported by resource/model/FieldsRequests.kt:4,16, InformationRequestRequests.kt:7,83 and RequestsResponses.kt:5,340.
- [truncated]

Fix outline:

No migration is needed (V151 stays free).

1. Response disclosure filter. Move the filter into the service. InformationRequestResponseDraftService should restrict InformationRequestResponseDraftResult.responses to the patched Requirement IDs, in both the mutate path (lines 275-283) and the receipt-replay path (lines 646-660), or add a disclosedResponses field computed there. Then change InformationRequestResponseResource.ok and InformationRequestNoAuthRequestResource.ok to map result.responses with InformationRequestResponseDtoMapper and nothing else, and drop the requirementIds parameter. Tests to add:
   - A service test showing that a shared request's patch result, and its idempotent replay, leave out another party's responses.
   - Keep InformationRequestResponseResourceContractTest green, and add a matching no-auth contract assertion.

2. Data classes in service files. Move the command, patch, result, enum and projection types out of the 13 service/evaluator files into model/informationrequest, which is the program's existing convention. Examples:
   - InformationRequestResponseDraftModels.kt for PatchInformationRequestResponsesCommand, InformationRequestResponsePatch, ResponseFieldValuesPatch, ResponseNarrativePatch and InformationRequestResponseDraftResult.
   - InformationRequestGroupOccurrenceModels.kt.
   - InformationRequestStructuredResponseValidationModels.kt.
   - InformationRequestConditionEvaluationModels.kt.

   The InformationRequestResponseStore interface can stay beside the service or move to repository/informationrequest as a port. Fold [truncated]

### GA-066: Template draft replace and publish have no If-Match or ETag precondition

- Severity: medium. Verification: confirmed. Fix size: M. Audit key: `TEMPLATE-PRECOND`.
- Plan reference: AD-31, P2-T8, REST-IdempotencyKeys. Plan basis: In force: Decision 31 (plan lines 626-629), which says retryable client commands use CommandReceipt and mutable drafts use If-Match, with 428 when missing and 412 when stale. REST rules 817-822: the idempotency key applies to "other retryable transitions", and ETag is returned after every mutation. The rule's explicit If-Match list names request drafts, parties, Value Sets, response cycles and review drafts, not Template drafts. Line 400: ETag and precondition handling stay authoritative for [truncated]

Current state:

The Template administration API has no optimistic concurrency and no command idempotency. PUT /information-request-templates/{id}/draft/configuration replaces the whole editable Version with no If-Match check, no 428 or 412, and no ETag in the response. The service takes a row lock (findDraftForUpdate), which puts concurrent saves in order but never detects a stale one. The information_request_template_version table and entity have no revision column, so no ETag can be derived today. The result: two authors silently overwrite each other (last write wins). POST .../draft/publication freezes whatever draft is current, so it can publish a configuration saved after the publisher reviewed it. Create, clones, versions and versions/{n}/retirement also read no Idempotency-Key and never go through CommandReceiptService. The frontend informationRequestTemplateService.ts sends neither header, and apiClient adds neither globally. The template retries do not create duplicates, as claim 3 says they do. The unique definition key (V86 ux_request_template_definition_key) and the one-editable-Version check in createDraftVersion turn a retried create, clone, new version, publish or retire into a 409 instead of replaying the first result. Outside templates, InformationRequestPrivacyRequestResource.record, InformationRequestSubjectRestrictionResource.lift, and InformationRequestClockPolicyResource.define and publishVersion also take no Idempotency-Key. Real duplicates can happen only for privacy requests (V143 has no uniqueness on information_request_privacy_request) and clock policy versions [truncated]

Evidence:

src/main/kotlin/com/docuhyphen/app/api/resource/informationrequest/InformationRequestTemplateResource.kt:56-67 (create), 85-102 (PUT draft/configuration, no @HeaderParam, plain Response.ok with no ETag), 104-118 (publishDraft), 120-143 (versions), 150-169 (retirement), 171-194 (clones): no If-Match or Idempotency-Key anywhere in the file. InformationRequestTemplateAuthoringService.kt:221-249 replaceDraftConfiguration(id, request) has no precondition parameter; findDraftForUpdate followed by configurationWriter.replaceConfiguration is a wholesale replace. InformationRequestTemplatePublicationService.kt:31-65 publishTemplate(templateDefinitionId) has no precondition. model/entity/InformationRequestTemplateVersion.kt has no revision or version field; InformationRequestTemplateDto (model/dto/InformationRequestTemplateDtos.kt:29-43) carries no ETag. A grep for if-match, etag, CommandReceipt and idempotency across service/informationrequest/InformationRequestTemplate*.kt finds matches only [truncated]

Fix outline:

1. Migration V151__information_request_template_draft_revision.sql: add revision BIGINT NOT NULL DEFAULT 0 to information_request_template_version with a CHECK revision >= 0. If the V86 or V132/V136-restated request_template_version_guard trigger compares columns on a frozen row, restate it so revision cannot change once status is not DRAFT. 2. Entity InformationRequestTemplateVersion gets a revision field. Add a template-draft ETag helper using RevisionETag.of(draftVersionId, revision). Put draftETag on InformationRequestTemplateVersionDto or InformationRequestTemplateDto and in the DTO mapper. 3. InformationRequestTemplateAuthoringService.replaceDraftConfiguration(id, request, precondition: CommandPrecondition): after findDraftForUpdate, call precondition.requireSatisfiedBy(currentETag) before any write, then increment revision. InformationRequestTemplatePublicationService.publishTemplate(id, precondition, idempotencyKey) checks the draft ETag under the same lock and runs inside CommandReceiptService.runOnce. InformationRequestTemplateLifecycleService.createDraftVersion, retireVersion and cloneTemplate, and createTemplate, take an idempotency key and use CommandReceiptService, so a retry replays the original DTO instead of returning 409. 4. InformationRequestTemplateResource: add @HeaderParam("If-Match") on PUT draft/configuration and POST draft/publication, @HeaderParam("Idempotency-Key") on every POST, and an ETag response header on GET /{id} and on each successful mutation. Map CommandPreconditionException to 428/412 through CommandPreconditionResponse, keeping the [truncated]

### GA-067: Request-scoped trusted group Shares expand without trusted-group eligibility reconciliation

- Severity: medium. Verification: confirmed. Fix size: M. Audit key: `TRUST-GROUP`.
- Plan reference: AD-34, P3-T8. Plan basis: Requirement in force:
- Plan line 394: the Trusted Organizations reuse row.
- Lines 646-654: Architectural Decision 34 ("request code cannot silently materialize or retain access that existing trust reconciliation would deny. A tested matrix defines new assignment, issuance, group expansion, already-issued response, session, and recovery behavior").
- Lines 1536-1544: P3-T8, reuse trusted group reconciliation and extend ShareService group inheritance and reconciliation for group expansion.
- [truncated]

Current state:

Trust eligibility is checked for a trusted group request party only when it is assigned (InformationRequestPartyService.requireAssignableRecipient). After that, group expansion of the request-scoped PRINCIPAL_GROUP Share (resource_type INFORMATION_REQUEST) never consults trusted-group eligibility.

The only eligibility hook is ShareService.canExpandTrustedGroup. It finds the attestation through ExchangeRecipientAttestationService.findForDirectShare, which only resolves exchange_recipient.direct_share_id, meaning Exchange Shares. A request party Share can never occupy that column (V63 trigger), so the lookup returns null and the method returns true. As a result, when a member joins the group (PrincipalGroupService -> ShareService.synchronizeGroupMemberAccess), the new member gets an INHERITED_FROM_GROUP request Share. This still happens while the relationship is suspended, the group is unpublished, or the policy has made the attestation ineligible. In the same situation the Exchange Share for that group is held back, and the help article promises "new group members do not inherit access while trust is suspended or no longer eligible".

Resume reconciliation (TrustedGroupAccessReconciliationService.reconcileRelationship) also only walks Exchange direct Shares, so request Shares sit outside the reconciliation path altogether. Requirement-level party resolution (InformationRequestRequirementAuthorizationContextProvider.groupMemberPrincipals) uses live group membership with no eligibility check. DefaultAuthorizationService step 3 grants any active group Share to current members [truncated]

Evidence:

- src/main/kotlin/com/docuhyphen/app/api/service/exchange/ShareService.kt:311-315: canExpandTrustedGroup does `findForDirectShare(parentShare.id) ?: return true`.
- ShareService.kt:207-223: synchronizeGroupMemberAccess gates only on canExpandTrustedGroup.
- ShareService.kt:301-309: reconcileGroupShare, same gate.
- ShareService.kt:177-180: grantInternal expands any ACTIVE group Share immediately.
- ShareService.kt:95-120: grantRoleKeyWithPrincipalProvenance delegates to grantInternal.
- src/main/kotlin/com/docuhyphen/app/api/service/exchange/ExchangeRecipientAttestationService.kt:88-92: findForDirectShare resolves only through ExchangeRecipientService.findByDirectShareId.
- src/main/kotlin/com/docuhyphen/app/api/service/exchange/TrustedGroupAccessReconciliationService.kt:14-20: reconcileRelationship maps attestations to Exchange direct Shares only.
- OrganizationTrustRelationshipService.kt:277-321: suspend does no reconciliation. Lines 323-372: resume calls reconcileRelationship at [truncated]

Fix outline:

1. Resolve attestations for request Shares.
   - Add a resource-type-keyed lookup interface in service/exchange, for example `TrustedGroupShareAttestationResolver`, with a `supports(resourceType)` method and a `findAttestation(share)` method.
   - The Exchange implementation wraps the existing findForDirectShare.
   - The Information Request implementation lives in service/informationrequest. It maps share.id to a party through a new InformationRequestPartyService/query method backed by InformationRequestPartyRepository.findByShareId, then to party.exchangeRecipientId, then to ExchangeRecipientAttestationService.findForRecipient.
   - ShareService.canExpandTrustedGroup iterates the resolvers through `Instance`/`Provider` to avoid a CDI cycle.
   - Keep grantInternal's immediate expansion at assignment time: the party row is not saved yet, and requireAssignableRecipient already validated eligibility in the same transaction. Document that ordering by test.

2. Extend reconciliation.
   - TrustedGroupAccessReconciliationService.reconcileRelationship also reconciles active request party Shares whose exchange_recipient_id belongs to a GROUP attestation of the relationship.
   - Get those Shares through a new InformationRequestPartyService method such as `activeShareIdsForExchangeRecipient(recipientId)`, not through the repository directly, and call shareService.reconcileGroupShare on each.
   - Resuming trust then materializes the held-back request members.

3. Close the remaining matrix cells. These need a user decision:
   - Issuance: either refuse issue with 409 [truncated]

Decision needed. Recommended default: (a) Refuse issuance with a stable 409 while any party has a suspended or ineligible trust relationship. (b) Limit the group-expansion fix to request-scoped Shares; leave Exchange authorization unchanged.

### GA-068: Participant contact verification is never recorded (verifyContact unwired)

- Severity: medium. Verification: confirmed. Fix size: M. Audit key: `VERIFY-CONTACT`.
- Plan reference: AD-40, Baseline: External participants, P3-T8, P4-T4. Plan basis: In force: plan line 391 (baseline row: build the lifecycle including contact verification, owner-scoped lookup and collision handling). Lines 679-687 (Decision 40: the program owns creation, contact verification, owner-scoped lookup and collision handling, and activity state). Lines 1520-1528 (P3-T8: "Add creation, contact verification, owner-scoped lookup, collision handling, activity state ... Route all resolution and creation through the owning participant service"). Line 1716 (lifecycle [truncated]

Current state:

The contact proof that controls access works. A RequestAccessSession is only issued after the emailed OTP succeeds: RequestAccessSessionService.issue has exactly one caller, InformationRequestContactProofService.verifyChallenge, and it passes EMAIL_OTP. So no request content is readable without verified contact, and a registration upgrade can only run from an OTP-minted session.

What is missing is the External Participant identity lifecycle that plan decision 40 and P3-T8 assign to the owning participant service:
(1) ExternalParticipantService.verifyContact is dead code. Its only caller is ExternalParticipantServiceTest. ExternalParticipant.emailVerifiedAt and lastSeenAt are never written anywhere in src/main, so every participant row keeps NULL verification and activity state forever, even after a successful OTP proof.
(2) No activity state is recorded on the participant. Only session-level useCount and lastUsedAt are touched, through RequestAccessSessionService.touchUse from InformationRequestNoAuthReadAccessService.
(3) Two Information Request services skip the owning participant service. Both inject ExternalParticipantRepository directly and read participants with an unscoped findById instead of ExternalParticipantService.findOwned: InformationRequestContactProofService (resolving the OTP delivery email) and InformationRequestParticipantAccountUpgradeService (the email-match check). The upgrade's error message says "verified contact address", but the code never looks at emailVerifiedAt; it relies on the session implicitly. Only InformationRequestPartyContactResolver [truncated]

Evidence:

src/main/kotlin/com/docuhyphen/app/api/service/exchange/ExternalParticipantService.kt:58-72 verifyContact sets emailVerifiedAt/lastSeenAt; :74-78 findOwned (owner-scoped); :23-56 findOrCreate has no unique-violation handling.
Grep for verifyContact|emailVerifiedAt|lastSeenAt|email_verified_at across src: only writers are ExternalParticipantService.kt:69-70, only caller is src/test/.../service/exchange/ExternalParticipantServiceTest.kt:89; entity fields at model/entity/ExternalParticipant.kt:53-59; columns in V1__baseline.sql:286-287.
service/informationrequest/InformationRequestContactProofService.kt:34 injects ExternalParticipantRepository; :72-120 verifyChallenge only updates ShareLink (103-108) and issues a session (114-119), never touches the participant; :206-214 resolvePartyContactEmail uses externalParticipantRepository.findById(it) with no owner scope.
service/informationrequest/InformationRequestParticipantAccountUpgradeService.kt:51 injects ExternalParticipantRepository; [truncated]

Fix outline:

No migration needed: email_verified_at and last_seen_at already exist from V1, so V151 stays free.
1. Add a single owner-resolution helper, e.g. InformationRequestParticipantOwnerResolver, that maps request ownerType to ExternalParticipantOwner. Point the private copies in InformationRequestPartyContactResolver.participantOwnerOf and InformationRequestPartyService.participantOwnerFor at it.
2. ExternalParticipantService:
   - Add recordActivity(owner, participantId, at). It sets lastSeenAt, throttled to skip the write when the existing value is recent.
   - Make findOrCreate collision-safe. Do the insert in a REQUIRES_NEW helper (or a native insert with ON CONFLICT against the two partial unique indexes), catch the unique violation (SQLState 23505) and re-read with findByOwnerAndEmail.
3. InformationRequestContactProofService:
   - Drop ExternalParticipantRepository.
   - Resolve the contact email through InformationRequestPartyContactResolver.emailOf(request, party), or through ExternalParticipantService.findOwned. This needs the request, loaded via party.informationRequestId through the existing request lookup.
   - In verifyChallenge, after the OTP succeeds and when party.principalKind == PARTICIPANT, call externalParticipantService.verifyContact(owner, participantId) in the same transaction.
4. InformationRequestParticipantAccountUpgradeService:
   - Drop ExternalParticipantRepository and use externalParticipantService.findOwned(participantId, ownerOf(request)).
   - Refuse with CONTACT_PROOF_REQUIRED when participant.emailVerifiedAt is null, before the email [truncated]

### GA-069: Walking-skeleton fixtures are not extended in Phases 3, 4 and 10

- Severity: medium. Verification: confirmed. Fix size: L. Audit key: `WALKING-FIXTURES`.
- Plan reference: P2-T11, P5-T8, P7-T8, P8-T10, Walking-skeleton fixtures. Plan basis: In force:
- Walking-skeleton rule, lines 889-904: "Each later phase extends the same fixtures". The stress fixture must prove delegated authority, repeatable occurrences, conditions, staged submission, multi-stage review, correction, supplemental requests, recurrence, clocks, retention and export.
- P2-T11, lines 1269-1273: "Extend these same walking-skeleton scenarios in every later phase".
- P5-T8, lines 2178-2180: sparse draft response, occurrence creation, conditions, completeness.
- P7-T8, [truncated]

Current state:

The two named test-only fixtures, `basicFieldDocumentResponseAttestationRequest` and `multiPartyStagedEvidenceRequest` in InformationRequestTemplateWalkingSkeletonFixtures.kt, are never turned into a runtime Information Request. No test materializes, issues, or assigns parties to a request built from either fixture. Only four test classes call the fixture functions, besides the contract test: the Phase 5, 6, 7 and 8 walking tests. Each of them either mocks the repositories, publishes the Template and checks its projection, or feeds hand-built objects into pure evaluators.
- Phase 5 runs only InformationRequestCompletenessProgressService, with every repository mocked. It fabricates occurrence rows and stubs the condition result to TRUE. It never calls the draft-patch, group-occurrence-add or real condition-evaluation services.
- Phase 7 publishes both fixtures. It then calls InformationRequestAttestationPolicyEvaluator, InformationRequestAmendmentClassifier.classify, InformationRequestCarryForwardPlanner.plan and followUpService.dueAt on synthetic inputs. It never calls submissions, attestations, amendments, successors or recurrence at runtime. The stress fixture's SUBJECT-then-ATTESTOR ROLE_SEQUENCE policy is checked only through the pure evaluator.
- Phase 8 checks the stress fixture only through its projection and the pure InformationRequestReviewAggregator, using random UUIDs. Its runtime correction, Accepted Fact and Business Decision test uses a reviewed SubmissionRuntimeSqlFixture, not either named fixture.
- Phase 9 never references the fixtures or the fixture [truncated]

Evidence:

- The fixture functions are used only in the walking tests. A grep for `InformationRequestTemplateWalkingSkeletonFixtures|multiPartyStagedEvidenceRequest|basicFieldDocumentResponseAttestationRequest` across src/test, src/main and web-app/src matches only the Fixtures, ContractTest and Phase5/6/7/8Test files.
- InformationRequestTemplateWalkingSkeletonFixtures.kt:45 and :99 define the two fixtures.
- InformationRequestTemplateWalkingSkeletonPhase5Test.kt:
  - Lines 145-166: every repository, the condition service and the contribution evaluators are mocks around InformationRequestCompletenessProgressService.
  - Lines 205-215: occurrence rows are fabricated in the occurrence repository stub.
  - Lines 83-93: the condition result is stubbed to TRUE.
  - Lines 231-255: responses are injected directly, with no draft service.
- MultiPartyStagedEvidenceRequestConformanceTest.kt:
  - Line 74 uses `support.fieldRequest(staged = true)`. ConformanceRequestSupport.kt:44-53 backs this with [truncated]

Fix outline:

This is a test-only fix: no production code changes and no migration (V151 stays free).
1. Add a shared helper, for example WalkingSkeletonRequestSupport in src/test/.../service/informationrequest/. It wraps conformance/PublishedRequestSupport.issue so that each named fixture becomes an issued runtime request:
   - Basic fixture: fieldKeys ["basic-recorded-summary"], parties CONTRIBUTOR and ATTESTOR, configuration `{ schema, fields -> basicFieldDocumentResponseAttestationRequest(schema, fields[...].fieldDefinitionId).configuration }`.
   - Stress fixture: fieldKeys for subject-status and delegate-note, parties SUBJECT, PREPARER, ATTESTOR, a delegate, and at least 5 REVIEWER parties (3 for the content quorum plus 2 new ones for the confirmation consensus that excludes prior reviewers).
   PublishedRequestSupport is `internal` in the same module, so it can be reused or moved up one package.
2. Add InformationRequestTemplateWalkingSkeletonPhase3And4Test. Both fixtures should materialize one requirement per authored key, create parties and Shares, and project request detail and parties through InformationRequestQueryService and the party projection for owner and assignee. The stress fixture's delegate should gain and lose respond rights through InformationRequestDelegatedAuthorityService grant, expiry and revoke. A no-auth bootstrap token should resolve to the same RequestAccessContext.
3. Rewrite InformationRequestTemplateWalkingSkeletonPhase5Test on the issued stress request. It should add reported-item occurrences through InformationRequestGroupOccurrenceService.add and [truncated]

### GA-070: A privacy request is committed before it is processed, so any refusal strands it in RECORDED forever; the endpoint takes no Idempotency-Key

- Severity: medium. Verification: confirmed. Fix size: M. Audit key: `critic#2`.
- Plan reference: Completeness critic. Plan basis: Plan:492-499 (decision 20: every state-changing service records its audit and history in the same transaction as the mutation). Plan:817-819 (REST: require an idempotency key for retryable transitions, replay the original result, conflict on a different fingerprint). Plan:3311-3317 (Phase 9 decision 12: privacy requests are recorded and processed, and deletion refuses with a stable reason). Plan:3433-3439 (P9-T10 marked done). Plan:3577-3581 (Phase 10: owner-scope commands replayed by [truncated]

Current state:

InformationRequestPrivacyService.submit first commits the privacy request row and its PRIVACY_RECORD audit in a separate QuarkusTransaction.requiringNew() (line 54). Only after that does it process the request, in a second requiringNew per kind. The only finalizers are complete() (COMPLETED) and refuse() (REFUSED), and refuse() is reached only from the DELETION eligibility and disposal paths.

Any exception thrown during processing rolls back only the processing transaction. The committed row then stays in its default RECORDED state (entity line 67) with no targets, no refusal code and no PRIVACY_REFUSE audit. The resource maps the exception to a 4xx, so the caller never gets the privacy request id. Cases that cause this:
- a CORRECTION with no correction body (service line 74);
- a correction with a blank reason, no value or narrative, an unknown item, or an item belonging to a different subject (InformationRequestItemCorrectionService lines 43-54);
- an EXPORT whose transfer region the transfer policy refuses (InformationRequestRecordExportService lines 128-133);
- any failure inside disposals.claimAndProcess during DELETION (line 148). Here the disposal claims continue under their own recovery, but the privacy request stays RECORDED.

Nothing in production reads or advances RECORDED. The only references are the enum, the entity default and the frontend label. There is also no retry or resume endpoint. The V143 guard allows updates only while the row is RECORDED, and the privacy list (Privacy tab) shows such rows as "Recorded" indefinitely.

Separately, [truncated]

Evidence:

src/main/kotlin/com/docuhyphen/app/api/service/informationrequest/InformationRequestPrivacyService.kt:54 (record() committed in its own requiringNew), :57-70 (EXPORT/ACCESS processing in a separate tx), :71-78 (CORRECTION separate tx, :74 throws on missing body after record committed), :84 and :127-164 (DELETION: refuse() only for eligibility/disposal refusals; claimAndProcess exceptions at :148 escape), :104-125 (record saves row + PRIVACY_RECORD audit), :166-193 (complete/refuse are the only finalizers).
src/main/kotlin/com/docuhyphen/app/api/model/entity/InformationRequestPrivacy.kt:22-27, :67 (default RECORDED). grep for InformationRequestPrivacyRequestState.RECORDED in src/main finds only :67. Frontend reference: web-app/src/app/information-requests/operations/privacy/privacyLabels.ts:21 (label "Recorded").
src/main/resources/db/migration/V143__information_request_privacy.sql:29-33 (state check: RECORDED means no completed_at and no refusal_code), :57-59 (only a RECORDED row may [truncated]

Fix outline:

1. Service: restructure InformationRequestPrivacyService.submit.
   - Validate the command shape before recording anything: key format, and for CORRECTION a non-null body, a non-blank reason and a value or narrative. Malformed input returns 400 and records no row.
   - For ACCESS, EXPORT, CORRECTION and RESTRICTION, run record(), processing and complete() in one transaction. A malformed or failed command then commits nothing, and the PRIVACY_RECORD audit shares the mutation's transaction (decision 20).
   - Catch domain refusals (InformationRequestLifecycleException such as NOT_FOUND item or TRANSFER_NOT_PERMITTED). In a fresh transaction, record the request and immediately refuse(id, principal, reasonCode, message), so the outcome is REFUSED with a stable code and a PRIVACY_REFUSE audit. Return that view with 200/201, matching how DELETION refusals are returned today.
   - For DELETION, keep the multi-transaction claim flow but wrap claimAndProcess. On an unexpected failure, write REFUSED targets and refuse() with a stable code, for example a new InformationRequestErrorCatalog.PRIVACY_REQUEST_PROCESSING_FAILED. Alternatively, let the disposal recovery worker finalize the privacy request once its claims settle.

2. Idempotency:
   - Resource: add @HeaderParam(IDEMPOTENCY_KEY_HEADER) and pass InformationRequestCommandHttp.idempotencyKey(raw) into RecordInformationRequestPrivacyRequestCommand as a new idempotencyKey field.
   - Service: wrap the recorded outcome in commandReceiptService.runOnce with a CommandReceiptRequest. Scope it to the subject (add ResourceType [truncated]

### GA-071: Lifting a subject restriction writes no audit event or history, takes no Idempotency-Key or If-Match, and does not lock the row

- Severity: medium. Verification: confirmed. Fix size: M. Audit key: `critic#3`.
- Plan reference: Completeness critic. Plan basis: Requirement in force:
- Decision 20 (plan 492-498): every state-changing service records its classified audit event and immutable history in the same transaction.
- Progress rule (plan 853): a mutation task cannot be checked until its audit event, history, transaction and redaction tests pass.
- REST rules (plan 817-819): retryable transitions need an idempotency key.
- Phase 9 decision 12 (plan 3311-3317): restriction stops Accepted Fact promotion and reuse.
- P9-T10 (plan 3433-3439) is [truncated]

Current state:

Lifting a subject restriction (POST /information-request-subject-restrictions/{restrictionId}/lift) turns Accepted Fact promotion and reuse back on for that subject. It changes state, but it records no classified audit event. InformationRequestSubjectRestrictionService has no AuditRecorder, and the closed catalog has no lift event type, so the owner-scope audit search cannot show who lifted a restriction or when. Every other privacy mutation is audited (PRIVACY_RECORD, _COMPLETE and _REFUSE in InformationRequestPrivacyService).

The claim that there is "no history" is only partly right. The restriction row itself keeps lifted_at, lifted_by_principal_kind/id and lift_reason_code. A V143 CHECK constraint and a trigger keep those values together and make a lifted row immutable. So who lifted it and why can be read from the row, but no audit or transition record exists.

The lift takes no Idempotency-Key. A client that retries after a lost response gets a 409 PRIVACY_REQUEST_STATE_INVALID instead of the original result replayed. Owner-scope commands in the same area do replay by key (reminders; accepted-fact revocation).

The service loads the row with findById, not with a pessimistic lock. Data integrity still holds, because the V143 guard trigger refuses to update a row that is already lifted. But when two lifts race, the loser gets an unmapped database or commit error instead of the stable 409 code. A lift can also race with a new RESTRICTION privacy request: restrict() reads the active row without a lock, returns it, and the concurrent lift then leaves the subject [truncated]

Evidence:

- src/main/kotlin/com/docuhyphen/app/api/service/informationrequest/InformationRequestSubjectRestrictionService.kt:17-21. The constructor injects only ownerAccess, restrictionRepository and clock, with no AuditRecorder.
- Same file, lines 36-54. lift() calls restrictionRepository.findById (no lock), sets liftedAt, liftedByPrincipalKind/Id and liftReasonCode, then calls restrictionRepository.update. It records no audit and has no idempotency.
- Same file, lines 24-34. restrict() uses findActive without a lock.
- src/main/kotlin/com/docuhyphen/app/api/resource/informationrequest/InformationRequestSubjectRestrictionResource.kt:43-57. The endpoint is @POST @Path("/{restrictionId}/lift") and has no @HeaderParam for Idempotency-Key or If-Match.
- src/main/kotlin/com/docuhyphen/app/api/service/audit/catalog/AuditEventType.kt:320-322. Only INFORMATION_REQUEST_PRIVACY_RECORD, _COMPLETE and _REFUSE exist; there is no restriction-lift type.
- [truncated]

Fix outline:

1. Audit catalog. In AuditEventType.kt, add INFORMATION_REQUEST_PRIVACY_RESTRICTION_LIFT("information_request.privacy.restriction_lift", AuditCategory.INFORMATION_REQUEST). Update AuditEventTypeTest and any frontend audit event label or filter list.

2. Repository. In InformationRequestSubjectRestrictionRepository, add findForUpdate(id) using LockModeType.PESSIMISTIC_WRITE, and a locking variant of findActive for restrict().

3. Service. In InformationRequestSubjectRestrictionService:
- Inject AuditRecorder and CommandReceiptService (the same idempotency mechanism InformationRequestAcceptedFactService uses).
- lift(restrictionId, reasonCode, idempotencyKey) locks the row with findForUpdate, checks the owner, refuses if already lifted with the stable PRIVACY_REQUEST_STATE_INVALID, and sets the lift fields.
- In the same transaction, it records an AuditEventDraft:
  - Owner scope: Personal or Organization.
  - actorKind: AuditActorKind.forPrincipal.
  - targetType: INFORMATION_REQUEST_SUBJECT_RESTRICTION, with targetId set to the restriction id.
  - Redacted payload: restrictionId, subjectIdentityRefId, privacyRequestId, reasonCode.
  - idempotencyKey: "<eventKey>|<restrictionId>".
- Validate reasonCode as a lowercase machine key of at most 128 characters, like keyOf in PrivacyService, so an oversized value is a 400 rather than a database error.
- Replaying the same key and fingerprint returns the stored restriction. A different fingerprint returns a CommandReceiptConflict.

4. Resource. Take @HeaderParam(IDEMPOTENCY_KEY_HEADER) and pass [truncated]

### GA-072: Record and subject exports bypass the Requirement confidentiality-compartment step-up that every other read enforces

- Severity: medium. Verification: confirmed. Fix size: M. Audit key: `cross-gates#0`.
- Plan reference: Cross-phase gates. Plan basis: In force:
- Invariant 15 (lines 481-482): the confidentiality compartment must be enforced on both reads and writes.
- Capability table row (line 923): "unauthorized values remain redacted" for export.
- Security and Privacy Gates (4195-4204): "Apply the same audience policy to reads, writes, mutation responses, notifications, realtime payloads, exports..." and "apply step-up policy for sensitive compartments".
- Program acceptance (4645-4647): confidentiality compartments and [truncated]

Current state:

The gap is real. Record exports and subject exports never check Requirement-level authorization. Every other read of submitted content does. The Requirement evaluator refuses any action on a Requirement that has a confidentialityCompartmentKey unless authorizationContext.mfaSatisfied is true. Exports are authorized only by INFORMATION_REQUEST_EXPORT on the request, and that request-level evaluator checks only the parent Exchange state. The assembler then writes every submitted item's value, narrative, disposition and valueType into the frozen contentJson, with no Requirement filter. The external-source section does the same for imported values and for discrepancy importedValue/responseValue. GET /information-requests/{id}/record-exports/{exportId} returns that content, and the UI downloads it.

Corrections to the auditor's description:
(1) Who can create and read request exports is narrower than claimed. Only a request-scoped DECISION_MAKER party holds INFORMATION_REQUEST_EXPORT. The Exchange owner inherits only CREATE, READ, CANCEL and ADMIN, and organization roles do not hold EXPORT. Organization Owners and Admins hold INFORMATION_REQUEST_PRIVACY_MANAGE. That lets them create a subject export with no Requirement or step-up check, but they can read its content only through a source request's record-exports route, where they would also need DECISION_MAKER.
(2) factsOf does not emit fact values. It emits only IDs, purpose, confidence and timestamps. The value leak is in itemOf and in the external-source assembler.
(3) The leak is broader than compartments. A DECISION_MAKER [truncated]

Evidence:

- InformationRequestRequirementPolicyEvaluator.kt:40-46 denies with CONFIDENTIALITY_DENIED when confidentialityCompartmentKey != null && !mfaSatisfied. Lines 57 and 135-147 make REQUIREMENT_VIEW an exact assigned-party action.
- InformationRequestRecordExportService.kt: lines 84, 92, 99 and 180 call only gate.authorizeRequest(INFORMATION_REQUEST_EXPORT). createSubjectExport (119-140) has no authorization or Requirement check and assembles every subject request. read() returns view(..., withContent = true) at 115, and view() puts export.contentJson in at 280-281. requireExport (273-275) uses findForRequest.
- InformationRequestRecordExportRepository.kt:40-55: findForRequest also matches exports through the information_request_record_export_source table, so subject exports are included.
- InformationRequestRecordAssembler.kt, itemOf (179-217): disposition (185), narrative (186), valueType, valueCleared and value (190-194) for every item, with no filter. factsOf (348-370) has no value [truncated]

Fix outline:

1. In Action.kt, add INFORMATION_REQUEST_REQUIREMENT_EXPORT(Capability.INFORMATION_REQUEST_EXPORT) on the INFORMATION_REQUEST_REQUIREMENT resource. Keep it out of exactPartyActions so the evaluator applies the parent policy plus the compartment step-up (line 40). Make sure InformationRequestRequirementParentGrantInheritancePolicy lets INFORMATION_REQUEST_EXPORT through from the request-scoped Share.
2. Change InformationRequestRecordAssembler.assemble(request) to assemble(request, disclosed: (UUID) -> Boolean). For Requirements not permitted, itemOf leaves out value, valueType, valueCleared and narrative and emits "contentRedacted": true. IDs, hashes, disposition metadata and evidence hashes stay. InformationRequestExternalSourceRecordAssembler does the same for the imported value and the discrepancy importedValue/responseValue. Also emit a top-level redactedRequirementIds list.
3. In InformationRequestRecordExportService.freeze, build the predicate with gate.permitsRequirement(access, INFORMATION_REQUEST_REQUIREMENT_EXPORT, id).
4. Add V151 to persist confidentiality on the export row, for example information_request_record_export.contains_confidential_content boolean not null default false (or a text[] of compartment keys). Set it when any included Requirement has a compartment. read(), and the Replayed branch of create(), refuse CONFIDENTIALITY_DENIED (or StepUpRequiredException, so the existing step-up dialog runs) and audit FAILURE when the flag is set and access.authorization.mfaSatisfied is false.
5. Subject exports:
   - Pass the caller's AuthorizationContext from [truncated]

### GA-073: Record preservation hold and retention schedule mutations have no Idempotency-Key, Command Receipt or If-Match

- Severity: medium. Verification: partial. Fix size: M. Audit key: `cross-gates#1`.
- Plan reference: Cross-phase gates. Plan basis: In force:
- Program Acceptance 4656-4657: "Retryable mutations use scoped Command Receipts and mutable drafts use required HTTP preconditions".
- Architectural decision 31 at 625-629: "Retryable client commands use a shared CommandReceipt... Mutable drafts use If-Match".
- REST rules 818-820: an idempotency key for "submission, issuance, review decisions, and other retryable transitions".
- Cross-Phase Test Matrix, Command safety row, line 4184.

Narrowing:
- The If-Match mandate at 820 lists [truncated]

Current state:

The code matches the claim. None of the four record-preservation mutations reads an Idempotency-Key, records a Command Receipt, checks If-Match, or returns an ETag. They are POST /record-preservation-holds, PATCH /record-preservation-holds/{id}/scope, POST /record-preservation-holds/{id}/release and PUT /record-retention-schedules/{resourceType}. The plan's program-wide rule ("Retryable mutations use scoped Command Receipts", decision 31) covers these retryable client commands, so the idempotency and receipt half of the claim is a real gap:
- A retried hold placement after a lost response creates a second ACTIVE hold, with its own PLACED event and its own audit record. Nothing in the schema stops two active holds on one resource.
- A retried release returns 409 HOLD_RELEASED instead of replaying the original 200.
- A retried scope change returns 409 HOLD_SCOPE_UNCHANGED instead of replaying.
- A retried schedule PUT appends a duplicate version (max + 1), so the PUT is not idempotent.

The If-Match half is narrower than claimed. The plan requires HTTP preconditions only for "mutable drafts", and its explicit list (request drafts, party changes, Value Sets, response cycles, review drafts) does not include holds or schedules. The auditor's scenario that "two administrators changing one hold's scope overwrite each other" cannot happen:
- Scope has only two values (RESOURCE and DESCENDANTS_AND_REFERENCES).
- The hold row is locked with findForUpdate.
- A change to the current scope is refused as HOLD_SCOPE_UNCHANGED.
- A change against a released hold is refused as [truncated]

Evidence:

- src/main/kotlin/com/docuhyphen/app/api/resource/recordpreservation/RecordPreservationHoldResource.kt:61-76 (POST place), 78-92 (PATCH scope), 94-108 (POST release): no @HeaderParam of any kind, and responses carry no ETag header.
- src/main/kotlin/com/docuhyphen/app/api/resource/recordpreservation/RecordRetentionScheduleResource.kt:39-53 (PUT publish): no headers.
- RecordPreservationHttp.kt:23-42: refused() has no mapping for CommandReceiptConflictException or CommandPreconditionException, and any other exception, such as a unique violation, becomes a 500.
- src/main/kotlin/com/docuhyphen/app/api/service/recordpreservation/RecordPreservationAdministration.kt:29-87: passes commands straight to the services with no key or expected revision.
- RecordPreservationHoldService.kt:39-64: place always inserts a new hold. 66-84: changeScope throws HOLD_SCOPE_UNCHANGED at 71-74. 86-103: release. 115-123: activeHold locks with findForUpdate and throws HOLD_RELEASED.
- [truncated]

Fix outline:

No Flyway migration is required: command_receipt.resource_type is unconstrained (V95). If an extra partial unique index is wanted later, V151 is the next free number.

Backend:
1. Add ResourceType.RECORD_PRESERVATION_HOLD and ResourceType.RECORD_RETENTION_SCHEDULE for receipt and result references.
2. In RecordPreservationHttp:
   - Add IDEMPOTENCY_KEY_HEADER and an idempotencyKey(raw) helper that answers 400 when the key is missing.
   - Map CommandReceiptConflictException to 409 COMMAND_RECEIPT_FINGERPRINT_CONFLICT.
   - Map CommandPreconditionException to CommandPreconditionResponse.refused.
3. In RecordPreservationHoldResource and RecordRetentionScheduleResource:
   - Take @HeaderParam("Idempotency-Key") on place, scope, release and publish.
   - Pass the key through RecordPreservationAdministration into the command models in model/recordpreservation.
4. In RecordPreservationHoldService, wrap each command in CommandReceiptService.runOnce with actor CommandActorRef.principal(principal):
   - place: scope the receipt to the owner (owner kind and id), operation record_preservation.hold.place. Fingerprint the canonical resourceType, resourceId, scope, reason, caseReference and effectiveFrom.
   - changeScope: scope the receipt to the hold id, operation .scope_change. Fingerprint scope and reason.
   - release: scope the receipt to the hold id, operation .release. Fingerprint the reason.
   - On Replayed, reload and return the original hold view instead of re-running the mutation.
5. In RecordRetentionScheduleService.publish:
   - Use the same runOnce, scoped to (owner, [truncated]

### GA-074: Information Request backend, entities and migrations are full of descriptive KDoc and SQL comments

- Severity: medium. Verification: confirmed. Fix size: M. Audit key: `rules-sweep#0`.
- Plan reference: AGENTS.md rules sweep. Plan basis: Nothing in the plan waives the rule:
- Lines 102-103 require reading AGENTS.md completely before changing code, which brings in its "No comments on classes, methods, variables, sql migrations" rule.
- Line 137 ("Code comments must describe the implementation itself. They must never mention this plan...") restates the separate AGENTS.md rule against referencing the plan. It governs what the few allowed comments may say; it does not permit essay comments.
- The Status section (1-80), the Latest [truncated]

Current state:

The Information Request backend is full of descriptive comments, which the AGENTS.md coding rule forbids. The auditor's counts reproduce exactly:
- service/informationrequest: 41 of 202 files have KDoc, with 156 KDoc blocks and 50 `//` lines, 731 comment lines in total.
- resource/informationrequest: 7 KDoc blocks in 6 files, 48 lines.
- repository/informationrequest: 36 KDoc blocks in 20 of 52 files, 159 lines.
- Together that is 938 comment lines.
- 28 IR entity files carry KDoc.
- IR tests: 29 of 223 files have KDoc. My count differs from the auditor's 25 of 190 only because I matched files differently.

The comments are mostly essays explaining design reasons and behaviour, not minimal necessary notes. There are also `// ──` section dividers in 3 Template services and the Template DTOs, and `-- ──` dividers in V86-V91, V111 and V114.

The migration comments exist as described, but they cannot be removed. The plan (lines 188-189) says Flyway files that may already have been applied are never edited, and the app runs `quarkus.flyway.migrate-at-start=true`, so editing them would break checksums. For migrations, the only possible fix is for new migrations (V151 onward) to have no comments. The same pattern appears elsewhere in the repo (service/fields: 29 of 30 files have KDoc; service/exchange: 22 of 40), so it is not limited to IR, but the IR violation is real.

Evidence:

Counts were taken with grep over src/main/kotlin/com/docuhyphen/app/api:
- service/informationrequest: files_with_kdoc=41, kdoc_blocks=156, slash_lines=50, total=731.
- resource/informationrequest: 6 files, 7 blocks, 48 lines.
- repository/informationrequest: 20 files, 36 blocks, 159 lines.

Service examples:
- InformationRequestTemplateAuthoringService.kt:40-54 is a class-level essay. There are method KDocs at 87-91, 148-152 and 213-220, and `// ── Reads ──` / `// ── Writes ──` dividers at 85 and 146.
- RequestAccessContext.kt:5-19 is an essay-length KDoc on a two-field data class.
- InformationRequestLifecycleService.kt:206-218 has multi-line `//` reasoning about the frozen grant.
- InformationRequestErrorCatalog.kt has 79 comment lines, including a class KDoc at 3-11 and a KDoc on each constant.
- Other comment line counts: InformationRequestTemplateConfigurationValidator.kt 78, InformationRequestTemplateConfigurationWriter.kt 57, InformationRequestCapabilityExecutor.kt [truncated]

Fix outline:

This is a mechanical, deletion-only cleanup with no schema change and no new migration.

1. Kotlin production code:
   - Remove descriptive KDoc blocks, explanatory `//` lines and `// ──` dividers from the 41 service/informationrequest files, the 6 resource/informationrequest files and the 20 repository/informationrequest files.
   - Do the same in the 28 IR entity files under model/entity (for example InformationRequestTemplateEnums.kt, InformationRequestTemplateEvidencePolicy.kt, InformationRequestTemplateRequirementBinding.kt) and in model/dto/InformationRequestTemplateDtos.kt.
   - The InformationRequestResource.kt:25 KDoc is already covered by the RULE-REPO finding.
2. Keep the code self-explanatory:
   - Where a comment carries a reason the code cannot show, move that reason into names. For example, split the grant branch in InformationRequestLifecycleService.kt:206-218 into private functions such as `requirePreIssuanceEntitlement(exchange)` and `requireIssuedGrantUsable(exchange, grant)`.
   - Keep at most one short line only where it is truly unavoidable.
   - In InformationRequestErrorCatalog, drop the per-constant KDocs; the constant names already carry the meaning.
3. Tests: remove class KDoc from the 29 IR test files under src/test/kotlin.
4. Migrations:
   - Do not edit V86, V87, V88, V89, V91, V111, V114 or V122 (plan lines 188-189; Flyway migrate-at-start would fail checksum validation).
   - Write any new migrations (V151 onward) without comments.
5. Verification:
   - Run the backend compile and the full IR test suite; there should be no behaviour change.
 [truncated]

### GA-075: Clock policy reminder and overdue Communications cannot be chosen in the UI and are dropped when the screen publishes a new version

- Severity: medium. Verification: confirmed. Fix size: S. Audit key: `wiring-sweep#0`.
- Plan reference: Wiring sweep. Plan basis: In force:
- P9 decision 10 (plan ~3289-3297): the OutboundNotice snapshots its "source Communication ... (or the platform default content for the notice kind when none is named)".
- P9-T8 (3393-3408): source Communication ID and pre-interpolation hash; mutable Communication stays authoring input.
- The V141 row in the migration table (4475) records the notice intent sources.
- P9-T13 (3450-3455): the clock policy screen was deferred to Phase 10.
- P10 decision 10 (3575-3581): "Clock policies [truncated]

Current state:

The backend fully supports naming a reminder Communication and an overdue Communication on each clock policy version. V141 added the columns. The define and publish endpoints accept the IDs, and the service checks that the owner can see each one. The notice hook copies the chosen ID onto each RESPONSE_REMINDER or RESPONSE_OVERDUE Notice Intent, and the renderer uses that Communication or falls back to the platform default.

The frontend does not expose this. The Due date policies screen (ClockPoliciesPanel, ClockPolicyDialog, and the calendar and timing field components) has no Communication control. Nothing under web-app/src/app/information-requests mentions Communications at all.

ClockPolicyForm has no fields for either Communication. formFromVersion does not read them from the latest version, and definitionFromForm does not write them. The dialog pre-fills a new version from the latest one and then publishes exactly that definition through useClockPolicies.publish. The backend publish() stores definition.reminderCommunicationId and definition.overdueCommunicationId as given and does not carry forward the previous version's values. So a policy whose Communications were set through REST loses them silently on the next screen edit, and later clocks render the platform default.

Neither the help article (informationRequestOperationsArticle, "Due date policies") nor docs/information-requests/api-reference.md (line 108) mentions the Communication fields.

This claim is narrower on one point. Manual reminders (InformationRequestReminderService:109-114) and amendment notices [truncated]

Evidence:

Backend (the feature is implemented):
- src/main/resources/db/migration/V141__information_request_outbound_notice.sql:25-30 adds reminder_communication_id and overdue_communication_id, with FKs, to the clock policy version.
- InformationRequestRequests.kt:292-293 and InformationRequestClockEndpoint.kt:109-110 accept both IDs.
- InformationRequestClockPolicyService.kt:64 and :93 call requireVisibleCommunications (defined at :236), which checks scope visibility.
- InformationRequestClockPolicyService.kt:143-144 publish() stores definition.reminderCommunicationId and overdueCommunicationId verbatim, with no carry-forward from the previous version.
- InformationRequestClockDtoMapper.kt:35-36 and InformationRequestClockDtos.kt:35-36 return both IDs to clients.
- InformationRequestClockNoticeHook.kt:24 and :27 pass the version's Communication IDs into owe(), which sets sourceCommunicationId at :45.
- InformationRequestNoticeRenderer.kt:36-38 uses the Communication, otherwise [truncated]

Fix outline:

Frontend, web-app/src/app/information-requests/operations/clock-policies/clockPolicyForm.ts:
- Add reminderCommunicationId?: string and overdueCommunicationId?: string (plus display names for the chosen summaries, if shown) to ClockPolicyForm.
- defaultClockPolicyForm leaves them undefined.
- formFromVersion copies version.reminderCommunicationId and overdueCommunicationId.
- definitionFromForm spreads them into the request when present, the same way escalationAfterMinutes is handled.

Frontend, new component clock-policy-notice-fields/ClockPolicyNoticeFields.tsx with its own Styles file:
- Two rows, "Reminder notice wording" and "Overdue notice wording".
- Each row shows the chosen Communication name or "Platform default", a circular "Choose" button that opens the existing components/communication-picker/CommunicationPickerDialog (scopes PERSONAL, ORG, and PLATFORM, with selectedId), and a subtle "Use platform default" button that clears it.
- Stable ids, one attribute per line, and a responsive layout.
- Render it in ClockPolicyDialog.tsx after ClockPolicyTimingFields, keeping the dialog under ~150 lines.
- To show the chosen name when republishing, resolve it with getCommunication(id) or the picker's summary.
- Optionally extend clockPolicySentence or ClockPolicyRow to say which wording each notice uses.

No backend change or migration is needed. The endpoint, validation, storage, hook, and renderer already work. No V151 is required.

Tests:
- clockPolicyForm.test.ts: a round-trip test showing that formFromVersion followed by definitionFromForm keeps both IDs, and that [truncated]

### GA-076: Request audit history panel shows only the 100 oldest events, with no paging or total

- Severity: medium. Verification: confirmed. Fix size: S. Audit key: `wiring-sweep#1`.
- Plan reference: Wiring sweep. Plan basis: Line 323 (core scope includes audit history). Lines 3282-3288, P9 decision 9: GET /information-requests/{id}/audit-events returns the request's classified events. The plan text does not literally mention limit or offset; the endpoint implements them anyway. Lines 3323-3333, P9 decision 14: request-level audit history stays on INFORMATION_REQUEST_VIEW_OPERATIONS for the Exchange owner or decision maker, while owner-scope audit search needs the queue capability that only Organization Owners and [truncated]

Current state:

The Audit history tab on the operations request detail page (AuditHistoryPanel) calls GET /information-requests/{id}/audit-events without limit or offset. The backend applies DEFAULT_AUDIT_TARGET_LIMIT = 100 and offset 0, and the request-scoped query sorts ascending (newestFirst = false: ORDER BY occurredAt ASC, recordedAt ASC, eventId ASC). The panel therefore gets the 100 oldest audit records. It renders value.items only and ignores total, limit and offset. It has no pager, no count, and no sign that the list is cut short. The backend endpoint does support limit (1..500) and offset, and the response carries total, so this is a frontend wiring gap plus an ordering choice on the backend. Every save that changes a response records a SAVE_RESPONSE transition, which is audited as information_request.requirement.respond. The respondent workspace autosaves 2 seconds after the last edit, so an active request goes past 100 records quickly, and its newest activity then disappears from this tab. The owner-scope Audit search (AuditSearchPanel/useAuditSearch) does page, 50 at a time, newest first, and can filter by request id. It requires INFORMATION_REQUEST_VIEW_OPERATIONS_QUEUE, which only Organization Owners and Administrators hold (and personal owners for their own requests). An Exchange owner or decision maker who administers one organization-owned request has no way to see records past the first 100. The help article says the tab "lists the request's audit records", which suggests the list is complete.

Evidence:

web-app/src/app/information-requests/operations/audit-history/AuditHistoryPanel.tsx:21 loadEvents = getInformationRequestAuditEvents(requestId), no paging arguments; :37 isEmpty only; :41 renders value.items.map, and total, limit and offset are never read; there is no pager element. web-app/src/services/informationRequestOperationsService.ts:46-47 apiClient.get(`${requestPath(requestId)}/audit-events`) sends no params. web-app/src/app/models/models.tsx:3245-3251 InformationRequestAuditPageDto has items, total, limit, offset. src/main/kotlin/.../resource/informationrequest/InformationRequestAuditEventResource.kt:32-33,39 accepts limit and offset query params. src/main/kotlin/.../resource/informationrequest/InformationRequestAuditQuery.kt:25-28 resolvedLimit = limit ?: DEFAULT_AUDIT_TARGET_LIMIT, range 1..MAXIMUM_AUDIT_TARGET_LIMIT. src/main/kotlin/.../model/audit/AuditTargetModels.kt:41-42 DEFAULT_AUDIT_TARGET_LIMIT = 100, MAXIMUM_AUDIT_TARGET_LIMIT = 500. [truncated]

Fix outline:

No migration is needed. Backend: in InformationRequestAuditService.events, return the newest records first (newestFirst = true), or add an explicit order query parameter to InformationRequestAuditEventResource and InformationRequestAuditQuery, so the first page shows recent activity. Leave reconciliation() alone; it already reads with limit Int.MAX_VALUE. Frontend: (1) change getInformationRequestAuditEvents(requestId, page: {limit: number; offset: number}) in web-app/src/services/informationRequestOperationsService.ts to send params limit and offset. (2) In AuditHistoryPanel.tsx, keep offset state and a page size constant, reload when the offset changes, and show a polite live-region count ("N events"). Add a Previous/Next pager with circular secondary Buttons, ids, and the range text "x to y of N", reusing the AuditSearchPanel pattern. Extract a shared AuditPager component under operations/audit-pager/ with its own AuditPagerStyles.tsx, so AuditSearchPanel and AuditHistoryPanel both use it and each stays under 150 lines. Tests: a Vitest test for AuditHistoryPanel checking that the total is shown, the pager appears when total > page size, Next requests offset = page size, and Previous is disabled at offset 0. Update informationRequestOperationsService tests to cover the limit and offset params. Add a backend service/resource test that seeds more than 100 audit records for one request and asserts the default page holds the newest records, that total is right, and that the offset reaches older records. Help: update informationRequestOperationsArticle.tsx (Audit history [truncated]

### GA-077: The multi-party scenario never has the reviewer review the package, and never checks outsider mutation denial

- Severity: low. Verification: confirmed. Fix size: M. Audit key: `CONFORMANCE-TESTS`.
- Plan reference: P11-T10 / Executable capability proofs (Timed retained export), P11-T3 / P11-T9 / P11-T10, P11-T4 / Executable capability proofs (Delegated multi-party execution). Plan basis: Requirements in force:
- Plan line 914: neutral proof "...while another actor reviews".
- Lines 3859-3861: P11-T4 requires cross-party denial.
- Line 3900: the delegated multi-party proof ("another actor reviews; unauthorized parties cannot read or mutate data").
- Lines 3878-3880 and 3906: timed retained export requires immutable notices and access history.
- Line 3827: the traceability matrix says S2 proves party roles under real authorization.
- Lines 3742-3745: Decision 2 says external [truncated]

Current state:

These are test-coverage gaps only. No production behaviour is missing.
(1) S2 (MultiPartyStagedEvidenceRequestConformanceTest) is the only scenario that combines delegated authority, a distinct subject, a separate attestor and a reviewer. In its first test, fieldRequest(staged = true) is built without review, and the request goes straight to CLOSED after the confirmation stage. The reviewer never assigns, decides or settles a package review. In the second test the reviewer only reconciles and decides a connector-imported value. The outsider is checked only for INFORMATION_REQUEST_VIEW (the authorizer query at line 86, plus a ForbiddenException on importedValues.values at line 170). The outsider never attempts a write (respond, attest, submit, evidence upload, delegation grant), so "unauthorized parties cannot ... mutate data" is not proven for this pattern.
(2) S8 (TimedRetainedExportRequestConformanceTest) tests append-only refusal only for information_request_outbound_notice and information_request_clock_event (lines 188-196). For access history it only checks that an export_read audit event exists (lines 218-222). It never tries to UPDATE or DELETE that row, so "access history is immutable" is not proven in the scenario.
(3) S1 (line 44), S7 (lines 69, 112, 177) and S8 (line 150) call runtime.build without centralAuthorization, so they run under stubbedAuthorization. That stub allows every action except those named in `denies` or a hidden Requirement (InformationRequestRuntimeTestServices.kt:171-187), and it uses mocked grants. S7 passes a respondingSide deny lambda but [truncated]

Evidence:

src/test/kotlin/com/docuhyphen/app/api/service/informationrequest/conformance/MultiPartyStagedEvidenceRequestConformanceTest.kt:74 (fieldRequest(staged = true), no review), :84 (reviewer is only denied RESPOND), :86 (outsider checked only for INFORMATION_REQUEST_VIEW), :127 (CLOSED with no review cycle), :168-172 (outsider denied only the imported-values read), :178-187 (reviewer only reconciles and decides an imported value).
TimedRetainedExportRequestConformanceTest.kt:150 (runtime.build without centralAuthorization), :188-196 (append-only checked only for the notice and clock_event tables), :218-222 (access history is only checked to be non-empty), :284-294 (attestor used as the reviewer for decide and reconcile).
BasicFieldDocumentResponseAttestationRequestConformanceTest.kt:44 (stubbed authorization).
RecurringSupplementalRequestConformanceTest.kt:69, :112, :177 (stubbed), :229, :241 (PrincipalRef.user(UUID.randomUUID())).
InformationRequestRuntimeTestServices.kt:155-187 (build [truncated]

Fix outline:

Tests only. No production code or migration changes are needed (V151 is not required).
(1) In MultiPartyStagedEvidenceRequestConformanceTest, build the request with review required (fieldRequest(staged = true, reviewed = true) or equivalent) and give people.reviewer the request review assignment. After both stages submit, have the reviewer decide or settle the package review, and assert the request closes only after that. Assert the contributor, attestor and delegate are denied the review decision. Add outsider write assertions: expect ForbiddenException from support.answerField, attest/assent, submit and an evidence upload as people.outsider, and deny INFORMATION_REQUEST_REQUIREMENT_RESPOND and INFORMATION_REQUEST_REQUIREMENT_ATTEST through assertDenied.
(2) In TimedRetainedExportRequestConformanceTest, after reading the export_read event, run refusedBy(connection, "append-only") against an UPDATE and a DELETE of that event's row in the audit event store (the append-only tables protected by V41/V42).
(3) In TimedRetainedExportRequestConformanceTest.recordExternalSources, decide and reconcile as a principal that holds the request review capability rather than the attestor. Ideally, build S8 (and S1 and S7) with centralAuthorization = authorizationService, and use assigned respondent principals in S7 instead of PrincipalRef.user(UUID.randomUUID()). Update the Phase 11 traceability row if the scope of any scenario's proof changes.

### GA-078: Delegated authority has no read endpoint, no frontend and no help docs

- Severity: low. Verification: confirmed. Fix size: M. Audit key: `DELEG-VISIBILITY`.
- Plan reference: P3-T12, Phase 3 Tests to write first. Plan basis: Plan 1633-1655 (P3-T12 models the grantor, instrument, window and revocation; it does not mention a read surface). Plan 1736 (Phase 3 tests: "Delegated-authority expiry, revocation, and reassignment tests"). Plan 3897-3900 (capability proof: delegated multi-party execution). Phase 10 design decisions (3505-3570) list the UX scope without a delegated-authority UI, which narrows the frontend part of claim 1. AGENTS.md help-docs rule: a new endpoint or entity requires a docs update. No waiver or [truncated]

Current state:

Claim 1 (partial). Delegated authority can be granted and revoked, but nothing can list it. InformationRequestDelegatedAuthorityResource at /information-requests/{id}/delegated-authorities has only two endpoints: POST (grant) and POST /{authorityId}/revocations. There is no GET. The repository is used only by InformationRequestDelegatedAuthorityService and InformationRequestDelegatedAuthorityFactSource. No party DTO, query DTO or record projection shows grants. So an owner can only get an authorityId (which revoke needs) from the original grant response, and the revocation ETag has to come from that response too. The web app has no delegated-authority service, model or screen. The only related model field is madeUnderDelegatedAuthority on attestations; every other "delegate" hit belongs to review-stage delegation. The help docs mention delegation only for review stages (informationRequestReviewArticle.tsx:38). One part of the claim is overstated: the Phase 10 UX design decisions (items 1-12) never list a delegated-authority UI. Item 4 covers parties and item 9 covers reviewer delegation, so a missing frontend is not a plan violation. What remains a real gap is the missing read endpoint, plus AGENTS.md's rule that a new endpoint or entity needs a help-docs update. Claim 2 (confirmed). Plan line 1736 requires delegated-authority reassignment tests. None of the 10 test files that reference DelegatedAuthority mention reassignment. InformationRequestPartyService.reassignMutation keeps the party id and swaps its principal, share and bootstrap links. It never touches [truncated]

Evidence:

src/main/kotlin/com/docuhyphen/app/api/resource/informationrequest/InformationRequestDelegatedAuthorityResource.kt: only @POST grant and @POST @Path("/{authorityId}/revocations"), no @GET import. The repository is referenced only by InformationRequestDelegatedAuthorityService.kt and InformationRequestDelegatedAuthorityFactSource.kt (grep of src/main). InformationRequestDelegatedAuthorityFactSource.kt factsFor filters on active, effectiveAt, expiresAt, assignedPartyId and requirementId, and never looks at the party principal. InformationRequestPartyService.kt reassignMutation (about lines 479-540) revokes the old share and bootstrap links and rewrites party.principalKind/principalId, with no delegated-authority handling. web-app/src/app/models/models.tsx: delegated authority appears only as madeUnderDelegatedAuthority (2811); DELEGATED (3498), delegatedFromAssignmentId (3638) and delegatePartyId (3791) are review-assignment fields. No delegated-authorities URL exists anywhere in [truncated]

Fix outline:

1) Backend read endpoint: add GET /information-requests/{id}/delegated-authorities to InformationRequestDelegatedAuthorityResource, using the same try/catch shape with its own error message. It delegates to a new InformationRequestDelegatedAuthorityService.list(requestId, access), authorized like grant/revoke (request manage authority). The list returns InformationRequestDelegatedAuthorityDtoMapper DTOs, including revoked rows for history, and sends the authorities ETag in the header so revoke's If-Match can be obtained. Add a resource contract test and a service authorization test covering an owner allowed, an outsider denied, and correct ETag values. 2) Reassignment decision and behavior: the recommended default is that reassigning a party revokes that party's active delegated authorities in the same transaction, because the grantor's authority belonged to the previous principal. Implement this in InformationRequestPartyService.reassignMutation through a new InformationRequestDelegatedAuthorityService.revokeForParty(partyId, actor, reason) method, since one service must not call another service's repository. The revocation should stamp revoked_at, revoked_by and the reason, and bump the authorities revision. Also consider doing the same on party revocation. If the user prefers delegation to survive reassignment, record that decision instead and test it. 3) Tests: add a reassignment test to InformationRequestDelegatedAuthorityServiceTest or InformationRequestPartyService tests. It should prove the chosen behavior, and a fact-source/authorization test should show the [truncated]

Decision needed. Recommended default: Revoke delegated authorities automatically on reassignment or revocation. Add a read endpoint, a management-workspace section and help text.

### GA-079: Workflow designer cannot author requirement conditions for request triggers

- Severity: low. Verification: partial. Fix size: M. Audit key: `DESIGNER-REQ-CONDITIONS`.
- Plan reference: P9-Decision-5 / P9-T13, P9-T13 / P9-T3. Plan basis: Decision 5 at plan lines 3245-3251 defines the scoped operand contract but says nothing about a UI. P9-T3 at 3364-3368 is marked done for the backend evaluation (InformationRequestWorkflowOperandService). P9-T13 at 3450-3455 claims only "request-trigger applicability in the designer". The Status section (1-80), the non-goals (304-346) and the Latest Implementation Result (~4660-4742) neither require nor defer a requirement-condition editor. No later phase adds or waives one.

Current state:

The backend fully supports ApplicabilitySpec.requirementConditions (RequirementConditionSpec in WorkflowSpec.kt, evaluated in WorkflowApplicabilityEvaluator), and the frontend model has WorkflowRequirementConditionDraft. The workflow designer, however, only detects a request trigger, keeps any requirement conditions already stored, and shows their count. It has no editor to add, change or remove a condition's templateRequirementId, occurrencePath, valueType, operator or literal. So an org admin can set requirement conditions only by sending the workflow definition through the API. The help article (requestTriggerEventsArticle.tsx:38-46) says this plainly, so the docs are accurate. This is only a partial gap because the plan never explicitly requires authoring in the UI. Decision 5 (plan 3245-3251) defines the data contract and evaluation semantics. P9-T3 (3364-3368) is backend-only. P9-T13's note "request-trigger applicability in the designer" (3453) is vague, and the current behavior meets it: the designer handles request triggers and does not drop stored conditions. The capability works end to end through the API, but the product UI cannot create it.

Evidence:

web-app/src/app/settings/workflows-tab/workflow-designer/workflowApplicability.ts:6-23 (normalizedApplicability/savedApplicability only keep the existing requirementConditions array), :34-39 (the summary shows only a count); web-app/src/app/settings/workflows-tab/workflow-designer/workflow-applicability-page/WorkflowApplicabilityPage.tsx:23-33 (a request trigger gets a Text message with the count and no editor; ApplicabilityEditor is used only for Exchange field conditions); a grep for requirementConditions/templateRequirementId in web-app/src finds no editing component (outside the designer it matches only tests and IR workspace code that is unrelated to workflow applicability); applicability-editor/ holds only field-condition rows (ApplicabilityConditionRow.tsx, ApplicabilityEditor.tsx); src/main/kotlin/com/docuhyphen/app/api/service/workflow/WorkflowSpec.kt:57,61-68 (RequirementConditionSpec: templateRequirementId, occurrencePath, requirementKey, valueType, operator, value); [truncated]

Fix outline:

Frontend only; no migration or backend change needed. (1) Add web-app/src/app/settings/workflows-tab/workflow-designer/requirement-condition-editor/ with RequirementConditionEditor.tsx, RequirementConditionRow.tsx and RequirementConditionEditorStyles.tsx. It lists and edits WorkflowRequirementConditionDraft rows with: a Requirement picker populated from the org's published Information Request Template Requirements (reuse the existing template service/versions endpoint; store the stable templateRequirementId and put requirementKey in for display only); an occurrencePath input defaulting to "root"; a valueType derived from the chosen Field Requirement; an operator dropdown reusing applicabilityOperators.ts filtered by valueType; a literal input reusing conditionLiteral.ts; and circular Add/Remove buttons with ids. (2) Render it from WorkflowApplicabilityPage.tsx when requestTrigger is true and the trigger names a Submission Package, and wire onChange so savedApplicability persists the edits. (3) Tests: RequirementConditionEditor.test.tsx (add/edit/remove, operator filtering by type, literal canonicalization); extend __tests__/workflowApplicability.test.ts and add a WorkflowApplicabilityPage test for the request-trigger path; optionally a backend round-trip test proving an edited definition passes WorkflowApplicabilityEvaluator validation. (4) Update requestTriggerEventsArticle.tsx to describe authoring in the designer rather than through the API, then run npx tsc --noEmit and vite build.

Decision needed. Recommended default: Add requirement-condition authoring to the Workflow designer for Information Request triggers.

### GA-080: External typed-reference evidence subtype has no writer, command, endpoint or UI

- Severity: low. Verification: partial. Fix size: M. Audit key: `EXT-EVIDENCE-REF`.
- Plan reference: CDM-EvidenceVersion, P6-T1. Plan basis: Line 713 (EvidenceVersion is a Document Version or an external-reference subtype). Lines 2443-2445 (P6-T1: external evidence uses a separate typed-reference subtype). Lines 2446-2458 (P6-T1a says model groundwork only; P6-T1b says nothing writes these tables yet; P6-T1c says keep mutation APIs disabled until command contracts exist). Line 277 (objective 2, collect an evidence reference, which file-backed evidence satisfies). Lines 1-80 Status: Phase 6 complete, program complete, no follow-up [truncated]

Current state:

The external-reference Evidence Version subtype exists only in persistence and read paths: V120 source-exclusivity check, the EXTERNAL_REFERENCE enum value, the InformationRequestExternalEvidenceSource value class, mapper read/write branches, DTO fields externalReferenceType/Value, and a query-service branch that refuses content download. No production code ever constructs InformationRequestExternalEvidenceSource. The only writer of InformationRequestEvidenceVersion (InformationRequestEvidenceUploadService) always writes a Document Version source. No REST body, no-auth path, frontend service or UI records an external evidence reference, and Phase 9 connectors (including the STRUCTURED_EVIDENCE kind) write external-source records, not Evidence Versions. So the subtype can never occur at runtime. The claim overstates it as a missed plan requirement, though. The plan requires the subtype to exist as a model (lines 713, 2443-2450), and P6-T1a/T1b/T1c describe it as groundwork that is persisted only and has mutation APIs switched off. No later Phase 6-12 task asks for a command, endpoint or UI that records external-reference evidence. Objective item 2's "evidence reference" is already met by file-backed evidence. What is left is an unwired, dead-code subtype, not a missing required task.

Evidence:

InformationRequestEvidenceUploadService.kt:~297-303 always calls InformationRequestEvidenceVersionSourceMapper.write(this, InformationRequestDocumentVersionEvidenceSource(documentVersion.id)). grep for InformationRequestEvidenceVersion( in src/main finds only InformationRequestEvidenceUploadService.kt. grep for InformationRequestExternalEvidenceSource( finds only the model definition (InformationRequestEvidenceSource.kt:11), the mapper read branch (InformationRequestEvidenceVersionSourceMapper.kt:19), and tests (InformationRequestEvidenceSourceTest.kt, InformationRequestEvidencePersistenceContractTest.kt:147,644). InformationRequestEvidenceQueryService.kt:82 maps the external source to contentUnavailable(). InformationRequestEvidenceDtos.kt:27-28 exposes externalReferenceType/Value for reading only. web-app/src/services/informationRequestEvidenceService.ts has only certificationReference/signatureReference attributes and uploads files. InformationRequestConnectorWorker.kt never [truncated]

Fix outline:

A product decision picks one of two options. (A) Wire it: add an InformationRequestExternalEvidenceCommand plus a service method in service/informationrequest (for example InformationRequestExternalEvidenceService). It appends an Evidence Version with InformationRequestExternalEvidenceSource under the same Requirement authorization, Command Receipt, audit and provenance rules as upload. Leave out the malware assessment, but define how an external reference counts toward Requirement eligibility, probably verifier review only. Add POST /information-requests/{id}/requirements/{requirementId}/evidence/external-references plus the matching no-auth resource, a request model in resource/model, a frontend service function, a small form component in web-app/src/app/information-requests/ with its Styles file, a help-docs article update, and resource/service/contract tests. No migration is needed because V120 already has the columns. (B) Remove it: per the development-stage constraint against dead or backwards-compatibility code, drop the EXTERNAL_REFERENCE enum value, the value class, the mapper branches, the query-service branch and the DTO fields. A V151 migration would drop external_reference_type/value and simplify ck_information_request_evidence_version_source. Update the persistence and model tests to match.

Decision needed. Recommended default: Remove the unused external-reference subtype in a forward migration. External evidence is a later configurable extension (plan line 330).

### GA-081: Authorization matrix never models an unassigned Exchange participant or the evidence Document on ordinary Document surfaces

- Severity: low. Verification: partial. Fix size: S. Audit key: `G001-P6-T3f`.
- Plan reference: P6-T3f. Plan basis: Plan lines 2612-2620 (P6-T3f) name the unassigned Exchange participant on both surfaces. The done note there argues structural impossibility ("The evidence Document is never one of the Exchange's Documents..."). No later text waives or defers the named test case.

Current state:

The gap is only in test coverage, and it is narrower than the claim says. Two cases the plan names are missing. (1) InformationRequestEvidenceAuthorizationMatrixTest never gives any principal a Share on the parent Exchange. Its "unrelated" principal holds no share at all, so nothing checks that an ordinary Exchange participant is refused on the evidence actions. (2) No test takes an uploaded evidence Document id and calls the ordinary Exchange Document services or endpoints with it: file, preview, thumbnail, versions or comments. The property those surfaces depend on is tested, though, against real Postgres. InformationRequestEvidenceCommandTransactionTest (lines 180-184) asserts that the evidence Document has zero exchange_document rows, and that exchangeRepository.findDocumentBySessionIdAndDocumentId(exchangeId, evidenceDocId) returns null. Every ordinary Document service resolves Documents through that one lookup, so the refusal is structurally backed and indirectly tested. What is missing is the explicit matrix case the plan names. No product code is missing.

Evidence:

src/test/kotlin/com/docuhyphen/app/api/service/informationrequest/InformationRequestEvidenceAuthorizationMatrixTest.kt:100-105 (the unrelated principal is refused, with emptySet), :150 (unrelated = a random user with no share), :189-196 (every share is an INFORMATION_REQUEST party role; there is no EXCHANGE share), :276 (contextRegistry.kindOf is stubbed). src/test/kotlin/com/docuhyphen/app/api/service/informationrequest/InformationRequestEvidenceCommandTransactionTest.kt:180-184 (the evidence Document has no exchange_document row, and findDocumentBySessionIdAndDocumentId returns null), :354-363. Product code where every ordinary surface resolves Documents through that same lookup: ExchangeDocumentService.kt:527-531 (getDocument, used for file, preview and thumbnail), ExchangeDocumentVersionService.kt:170, ExchangeDocumentCommentsService.kt:264, ExchangeRetrievalService.kt:85,112, and the definition at ExchangeRepository.kt:462.

Fix outline:

No migration is needed. This is tests only. (a) In InformationRequestEvidenceAuthorizationMatrixTest, add an exchangeParticipant principal. Give it an EXCHANGE-scoped Share on fixture.exchangeId, for example PARTICIPANT or EDITOR, in the mocked ShareRepository, with contextRegistry resolving it. Add a test asserting matrix.allowed(exchangeParticipant) == emptySet(). Also add explicit cross-party list, preview and download denial assertions if they are not already listed per action. (b) Add a Postgres-backed test next to InformationRequestEvidenceCommandTransactionTest, or extend ExchangeDocumentVersionAccessTest. It should upload evidence through InformationRequestEvidenceUploadService, then call the ordinary services with the resulting Document id: ExchangeDocumentService file, preview and thumbnail, ExchangeDocumentVersionService list and download, and ExchangeDocumentCommentsService. The caller should be an Exchange OWNER or participant. Assert ExchangeDocumentNotFoundException, or a 404 at the resource layer. (c) Optionally update the P6-T3f done note to name the new tests.

### GA-082: S3 version reads leave a permanent temp copy of every stored file, evidence included

- Severity: low. Verification: confirmed. Fix size: M. Audit key: `G002-P6-T2a3`.
- Plan reference: P6-T2a3. Plan basis: Phase 6 goal (plan line ~2408): evidence is versioned, policy-checked material scoped to the Requirement. P6-T2a3 (lines 2498-2514): the provider-neutral version-storage port with local and object-store implementations, marked done. P6-T7 (line ~2713-2716): Phase 9 owns physical object deletion. I found nothing in the plan that waives, defers or narrows local copy handling. The plan never mentions temp or local copies at all. The requirement is therefore implied by the security and disposal [truncated]

Current state:

When the object-store (S3) implementation is selected, every read of a Document Version downloads the whole object into a new temp directory, and nothing ever deletes that copy. AwsS3DocumentVersionStorageService.openVersion only removes it when the object is missing or S3 throws. The copy is left behind by everything that reads the file: Exchange version download, evidence content download and preview, and the evidence malware scan. The digest-mismatch path in DocumentVersionContentService.open throws DocumentVersionContentIntegrityException and also leaves the file. So each read of a piece of evidence leaves another plaintext copy on the app host's temp disk. Those copies are outside the object store, the Requirement-scoped access checks and Phase 9 physical deletion. The local implementation returns the stored file itself, so it does not have this problem. No test covers the S3 read path.

Evidence:

src/main/kotlin/com/docuhyphen/app/api/service/storage/AwsS3DocumentVersionStorageService.kt:80-113: Files.createTempDirectory("document-version") at line 87. target.delete() runs only in the NoSuchKeyException and S3Exception catches (lines 99 and 104), and the success path returns target at line 112 without cleaning it up.
DocumentVersionStorageService.kt:11: the port returns a bare File, with no close or cleanup contract.
DocumentVersionContentService.kt:40-53: open() checks the digest and throws DocumentVersionContentIntegrityException (line 49) without deleting the file.
InformationRequestEvidenceHttp.kt content(): Response.ok(content.file.inputStream(), ...), with no cleanup afterwards.
ExchangeDocumentVersionResource.kt:138-142: the same pattern.
InformationRequestEvidenceMalwareAssessmentService.kt:108: open(content).file is scanned and never deleted.
InformationRequestEvidenceQueryService.kt:88: opens the content for evidence download and preview.
Grepping src/main for [truncated]

Fix outline:

No migration is needed.
1) Change the port so a read returns a closeable handle rather than a bare File. For example, openVersion returns DocumentVersionReadHandle(file, cleanup): AutoCloseable. Model classes go in model/document. Alternatively, stream the object directly: S3Client.getObject(request) returns a ResponseInputStream.
2) In AwsS3DocumentVersionStorageService, have close() delete the temp file and its parent directory (deleteRecursively). The local implementation's close() does nothing.
3) Make DocumentVersionContent AutoCloseable. DocumentVersionContentService.open closes the handle before rethrowing on a digest mismatch.
4) Update the callers. The malware assessment and any digest checks wrap their work in use{}. InformationRequestEvidenceHttp.content and ExchangeDocumentVersionResource.downloadVersion return a StreamingOutput that copies the file to the response and closes the content in a finally block, keeping the existing headers. Check DocumentVersionRecordingService, ExchangeDocumentVersionService and InformationRequestEvidenceQueryService so every path passes ownership on or closes.
5) Tests: a unit test for the S3 implementation, using a mocked S3Client or a test seam, showing close() removes the temp directory. A DocumentVersionContentService test showing the integrity-failure path leaves no file. A resource-level test showing the streamed download deletes the temp copy after writing.

### GA-083: Help docs claim registered-application access and recipient Public-field visibility that the code does not provide, and describe a schema removal with no UI

- Severity: low. Verification: confirmed. Fix size: S. Audit key: `G009-P1-T9`.
- Plan reference: P1-T9. Plan basis: Plan lines 1015-1018 (P1-T9: correct inaccurate help documentation, including entitlement-loss visibility) and exit criterion line 1093 ("Required help documentation is accurate"). Line 942-948 (P1-T1) scopes principal coverage to registered users plus synthetic PARTICIPANT and PUBLIC_LINK principals at the service boundary. It does not expose Exchange Fields HTTP endpoints to application tokens, so nothing in the plan makes the application claim true. No non-goal or later waiver covers these [truncated]

Current state:

The article web-app/src/app/components/help-docs/sections/articles/usingExchangeFieldsArticle.tsx is inaccurate in three places.
(a) Lines 60-61 say a registered application reads the version from the ETag or sends `*`, and line 64 says changes are recorded against the application that made them. No application token can reach the Exchange Fields endpoints. Application tokens are only admitted under /auth/application/integration and /auth/application/service. The only endpoints there are two ping endpoints (ApplicationScopedResource). On top of that, ExchangeFieldsResource.guard returns 401 whenever appUser is null.
(b) Lines 116-121 say recipients from another organization see Public fields. The server does filter to PUBLIC bindings for external audiences (FieldBindingPolicy). But the frontend only shows the Details tab when the viewer's own subscription includes Business Fields (Exchanges.tsx:93, 221, 991, 1053, through usePlanFeature and useCurrentSubscription). It does not check the owner's plan. So a recipient whose plan lacks Business Fields sees no Details tab at all. The entitlement-loss paragraph at lines 106-111 ("If the plan stops including Business Fields") never says whose plan counts. That entitlement-loss accuracy is exactly what P1-T9 requires.
(c) Lines 27-30 and 56-59 describe removing the schema, including If-Match on removal. unassignExchangeSchema (fieldsService.ts:230) exists but only its unit test calls it. No remove control exists in the exchange-fields-tab components; only SchemaAssignPanel calls assignExchangeSchema.

Evidence:

- src/main/resources/application.properties:296: `allowed-endpoint-prefixes` defaults to /auth/application/integration and /auth/application/service.
- src/main/kotlin/com/docuhyphen/app/api/service/auth/ApplicationTokenBoundaryService.kt:48-63: isApplicationTokenAllowedForPath rejects any path that is not an application endpoint.
- src/main/kotlin/com/docuhyphen/app/api/resource/application/ApplicationScopedResource.kt:15-29: the only endpoints are GET /integration/ping and GET /service/ping.
- src/main/kotlin/com/docuhyphen/app/api/resource/exchange/ExchangeFieldsResource.kt, guard(): `authTokenContext.authToken.appUser ?: return ... UNAUTHORIZED`.
- usingExchangeFieldsArticle.tsx:60-64 (application claims), 106-111 (plan wording that does not say whose plan), 116-121 (recipient Public visibility), 27-30 and 56-59 (schema removal).
- web-app/src/app/exchanges/Exchanges.tsx:93: `canViewBusinessFields = usePlanFeature(PlanFeature.BUSINESS_FIELDS_AND_SCHEMAS).isDiscoverable`. It gates [truncated]

Fix outline:

Docs-only fix, S-sized, in web-app/src/app/components/help-docs/sections/articles/usingExchangeFieldsArticle.tsx:
1. Delete the "registered application" sentence at lines 60-61. Change line 64 to "the person or recipient", because application tokens cannot reach /exchanges/{id}/fields.
2. Rewrite the entitlement paragraphs (106-111 and 116-121) to say the Details tab appears only when the viewer's own plan includes Business Fields. A recipient from another organization sees only Public fields, and only when their own plan includes Business Fields.
3. Either remove the schema-removal wording at lines 27-30 and 56-59, or keep it and add a "Remove schema" control to the Details tab. That control would go in a new component under exchange-fields-tab/, call unassignExchangeSchema with the loaded ETag, be owner/admin only and Pending only, and handle 412/428 conflicts. It would need an ExchangeFieldsTab.test.tsx case.
No migration is needed. Afterwards run `npx tsc --noEmit` and keep the article under 150 lines.
Optional product change instead of rewording (b): gate the Details tab on the Exchange owner's entitlement rather than the viewer's subscription.

### GA-084: Blueprint-default value writes and materialized schema defaults produce no value-mutation audit event

- Severity: low. Verification: confirmed. Fix size: S. Audit key: `G011-P1-T6`.
- Plan reference: P1-T6. Plan basis: Plan lines 965-969 (P1-T6) require "immutable FieldValueRevision, value-mutation and assignment audit coverage". Plan lines 1551-1556 (P3-T9) map BlueprintFieldDefault into root Field defaults and say not to reinterpret a default as submitted respondent data. That rules out treating it as a USER write, but it does not exempt it from audit. Grepping the plan for default and audit wording found nothing that waives, defers or narrows audit coverage for default writes.

Current state:

SchemaAssignmentService.applyBlueprintFieldDefaults stores Field Values with BLUEPRINT_DEFAULT provenance, records FieldValueRevisions and advances the root Value Set, but never calls SchemaAssignmentAuditTrail. So a Blueprint instantiation that writes Field defaults leaves no FIELD_VALUE_UPDATE (or any other value-mutation) audit record. The Information Request instantiation service does not fill the gap either: its recordDraftCreation only writes transition history. For configured schema defaults, materializeConfiguredDefaults runs just before auditTrail.schemaAssigned. That SCHEMA_ASSIGNMENT_ASSIGN record does exist, but its payload is empty apart from the common resource and version keys, so it does not show that any values were stored or how many. This part is only partly covered. Revision history does exist for both paths. No test covers auditing for Blueprint-default or schema-default writes.

Evidence:

SchemaAssignmentService.kt:327-331: auditTrail.valuesUpdated is called only in writeValues. SchemaAssignmentService.kt:335-374: applyBlueprintFieldDefaults calls upsertValue(..., BLUEPRINT_DEFAULT) and advanceValueSet, with no audit call. SchemaAssignmentService.kt:409-442: materializeConfiguredDefaults stores SCHEMA_DEFAULT values, with no audit call. SchemaAssignmentService.kt:214-215: defaults are materialized, then schemaAssigned is called. SchemaAssignmentAuditTrail.kt:37-45: schemaAssigned has payload = emptyMap(). SchemaAssignmentAuditTrail.kt:62-75: valuesUpdated is the only value event (FIELD_VALUE_UPDATE, AuditEventType.kt:173). InformationRequestBlueprintInstantiationService.kt:115 and :123-141: applyFieldDefaults delegates to applyBlueprintFieldDefaults. InformationRequestBlueprintInstantiationService.kt:228-244: recordDraftCreation writes only transition history. In SchemaAssignmentAuditTrailTest.kt, no Blueprint or schema default cases turned up. [truncated]

Fix outline:

1. SchemaAssignmentAuditTrail.kt: give valuesUpdated a source or provenance payload key (for example "valueProvenance" = USER / BLUEPRINT_DEFAULT), or add a dedicated method. Also add an appliedDefaultCount (or materializedDefaultCount) parameter to schemaAssigned and put it in the payload as a count only, never a value. 2. SchemaAssignmentService.applyBlueprintFieldDefaults: count the values actually stored and, when the count is above 0, call auditTrail.valuesUpdated(assignment, adapter.ownerScope(resourceId), provenance, schemaDisplayNameOf(assignment), changedCount, 0) tagged BLUEPRINT_DEFAULT. 3. materializeConfiguredDefaults: return the stored count and pass it to schemaAssigned. 4. If a new AuditEventType is chosen instead, add it to AuditEventType.kt. Check whether the audit catalog is also seeded in the DB; if it is, the next free Flyway migration is V151. The payload-key approach needs no migration. 5. Tests in SchemaAssignmentAuditTrailTest (or a service-level test): Blueprint defaults emit one FIELD_VALUE_UPDATE with the right count and provenance tag and no values in the payload; empty or unchanged defaults emit nothing; assigning a schema with configured defaults records the materialized count on SCHEMA_ASSIGNMENT_ASSIGN. No help-docs change is needed.

### GA-085: A concurrent schema assignment surfaces as 500, and there is no persisted assignment revision

- Severity: low. Verification: confirmed. Fix size: S. Audit key: `G012-P1-T7b`.
- Plan reference: P1-T7b. Plan basis: Plan lines 974-979 (P1-T7) and 991-997 (P1-T7b): Schema-assignment commands carry the expected assignment revision, and the missing vs stale precondition must be distinguishable. Exit criterion around 1086 says concurrency semantics are deterministic. Line 370 and lines 428 and 4513 keep the UNIQUE(resource_type, resource_id) invariant. None of these relaxes the revision requirement. I found nothing in the Status section, the Development-Stage Constraint, the non-goals (304-346) or later design [truncated]

Current state:

The Schema Assignment has no revision of its own. SchemaAssignment.kt has no revision/version column, and nothing in src/main or web-app/src uses assignmentRevision, assignment_revision or expectedAssignment. The only precondition for assign and unassign is the root FieldValueSet ETag (answersETagOf, SchemaAssignmentService.kt:608-609). That is a proxy for the Value Set, not an assignment revision. When no assignment exists, currentETag is null. ExpectedRevision then always throws STALE, so only Unconditioned ('If-Match: *') can succeed, and the first-party client always sends '*' for assign (fieldsService.ts ANY_VERSION, assignExchangeSchema). There is no way to say "expect no assignment". assignSchema reads the existing row with a plain findByResource and takes no lock (SchemaAssignmentService.kt:163-166). The Exchange adapter's exists/schemaAssignmentMutable also take no lock. So two concurrent assigns can both see null and both insert. The second one violates UNIQUE ux_assignment_resource (V36__fields_engine.sql:152). The resulting PersistenceException/ConstraintViolationException is not an IllegalStateException, IllegalArgumentException or FieldsPreconditionException, so ExchangeFieldsResource falls through to the generic catch and returns 500 "Request failed" (ExchangeFieldsResource.kt:189-193). The API should return 409 or 412. The data invariant holds, because the DB constraint prevents a duplicate. What is missing is deterministic concurrency semantics and an expected assignment revision. The missing-vs-stale precondition codes do exist [truncated]

Evidence:

src/main/kotlin/com/docuhyphen/app/api/model/entity/SchemaAssignment.kt (no revision column); service/fields/SchemaAssignmentService.kt:163-166 (unlocked findByResource, precondition against answersETagOf, then IllegalState if existing); SchemaAssignmentService.kt:608-609 (answersETagOf = root value-set ETag); service/fields/FieldsPrecondition.kt (Unconditioned passes anything; ExpectedRevision fails when currentETag is null); service/exchange/ExchangeFieldResourceAdapter.kt:31 (exists via plain findById, no lock; schemaAssignmentMutable defaults to valuesEditable, FieldResourceAdapter.kt:64); resource/exchange/ExchangeFieldsResource.kt:144-149 (precondition 428/412 mapping), 170-193 (IllegalState to 409, IllegalArgument to 404, generic Exception to 500 'Request failed'); db/migration/V36__fields_engine.sql:152 (ux_assignment_resource UNIQUE); web-app/src/services/fieldsService.ts ~214-228 (assign always sends If-Match '*'). No test covers concurrent assignment.

Fix outline:

1) Serialize assignment per resource: add a lockForSchemaChange(resourceId) hook to FieldResourceAdapter. It would run SELECT ... FOR UPDATE on the exchange / information_request row, called at the start of assignSchema, assignExactVersion and unassignSchema. As an alternative, catch ConstraintViolationException on ux_assignment_resource in storeAssignment and rethrow as IllegalStateException or a FieldsPreconditionException STALE, so the API answers 409 or 412, never 500. 2) Add a persisted assignment revision. Option A: migration V151__schema_assignment_revision.sql adds `revision BIGINT NOT NULL DEFAULT 1` (plus a CHECK >= 1) to schema_assignment, and a strong assignment ETag is derived from it. Option B: treat assign-when-absent as expected revision "none" by supporting `If-None-Match: *` (or a sentinel ETag for "no assignment") in FieldsPrecondition, and have GET /exchanges/{id}/schema return an ETag for the absent state. 3) Frontend: assignExchangeSchema sends the ETag or If-None-Match it read instead of '*'. 4) Tests: a service test with two concurrent assigns (one succeeds, the other gets 409/412 and never 500); resource tests for stale and absent assignment preconditions on the IR and Exchange schema endpoints. No help-doc change is needed beyond an error-message note.

### GA-086: Details tab offers assign and save controls regardless of caller capability, and swallows refusal messages

- Severity: low. Verification: partial. Fix size: S. Audit key: `G013-P1-T1`.
- Plan reference: P1-T1. Plan basis: - Plan lines 941-947 (P1-T1): "require an explicit owner or configuration capability for assignment changes". This is met by server-side authorization.
- Plan line 1078 (exit criterion): "Assignment changes require the intended owner/configuration permission". Also met on the server.
- The plan does not require the frontend to hide the assign or save controls from callers without the capability, and it does not require an unassign UI. The remaining gap therefore rests on the project's frontend [truncated]

Current state:

The server side of P1-T1 is in place. Assigning a schema checks Action.EXCHANGE_MANAGE_SCHEMA (which needs Capability.EXCHANGE_ADMIN), and writing values checks EXCHANGE_EDIT. So the plan's requirement and its exit criterion, "Assignment changes require the intended owner/configuration permission", are met on the backend.

What remains is a smaller frontend gap in the Exchange Fields (Details) tab.

(1) The tab decides `editable` from `exchange.status === INITIATED` alone (ExchangeFieldsTab.tsx:27, not line 104 as the claim says). As a result, any reader of a draft Exchange is shown the same controls, whether they are an editor, a viewer or a non-initiator recipient:
- SchemaAssignPanel, when no schema is assigned yet.
- FieldValuesForm with its Save control, when a schema is assigned.

The server then refuses the action with 403. The frontend has nothing to gate on: ExchangeDetailedDto and ExchangePermissions (ExchangePermissions.ts) carry no schema-management or field-edit capability, and SchemaAssignmentDto has no per-caller edit flag.

(2) SchemaAssignPanel.tsx:35-37 looks for an `error` key on the rejection. fieldsService.executeRequest throws `error.response.data`, which is a ResponseError with `errorMessage`/`reasonCode` (models.tsx:3-8). So every refusal on assign (403/409/428) shows the generic "Failed to assign schema" instead of the server's message.

What the claim overstates: refusals when saving values are not swallowed. useFieldValuesSave.ts:26 reads `ResponseError.errorMessage` correctly.

(3) unassignExchangeSchema (fieldsService.ts:230-234) has no UI [truncated]

Evidence:

- web-app/src/app/exchanges/components/exchange-fields-tab/ExchangeFieldsTab.tsx:27 has `const editable = exchange.status === ExchangeStatus.INITIATED;`. Lines 83-87 render SchemaAssignPanel when there is no assignment and the Exchange is editable. Lines 124-129 render FieldValuesForm whenever it is editable.
- web-app/src/app/exchanges/components/exchange-fields-tab/SchemaAssignPanel.tsx:35-37 contains `'error' in e ? String((e as {error: string}).error) : 'Failed to assign schema'`.
- web-app/src/services/fieldsService.ts:102-114: executeRequest throws `error.response?.data || error.message`. Lines 230-234 define unassignExchangeSchema; grep finds no caller in web-app/src/app.
- web-app/src/app/models/models.tsx:3-8: ResponseError has { errorMessage, reasonCode, retryAfterSeconds }. Lines 398-423: ExchangeDetailedDto has no role or capability fields. Lines 1955-1980: SchemaAssignmentDto has no per-caller edit or manage flag.
- web-app/src/app/exchanges/ExchangePermissions.ts:3-79: [truncated]

Fix outline:

No migration is needed.

1. Backend: add per-caller flags so the frontend has something to gate on. Two options:
   - Add `canManageSchema` and `canEditValues` to the Exchange schema read response (SchemaAssignmentDto, in its dedicated toDto mapper class). Compute them in the fields service through the resource adapter's authorization checks, using EXCHANGE_MANAGE_SCHEMA and EXCHANGE_EDIT together with valuesEditable.
   - Or expose the same flags on ExchangeDetailedDto.
   Because the no-assignment case returns null today, either return a small capability envelope or add a sibling `GET /exchanges/{id}/schema/capabilities`.
2. Frontend ExchangeFieldsTab.tsx:
   - Show SchemaAssignPanel only when `editable && canManageSchema`.
   - Otherwise show the "No business schema" text.
   - Render FieldValuesForm only when `editable && canEditValues`, and FieldValuesReadOnly otherwise.
   - Set the Editable/Read only badge from the same value.
3. SchemaAssignPanel.tsx: read `(e as ResponseError).errorMessage`, falling back to the generic text, as useFieldValuesSave does.
4. Optionally, add an "Unassign" action using unassignExchangeSchema with the assignment ETag, shown only with canManageSchema. Alternatively, remove the unused service function.
5. Tests:
   - Vitest cases in ExchangeFieldsTab.test.tsx: a caller without the capability on an INITIATED Exchange sees no assign panel and no Save; a refused assign shows the server's errorMessage.
   - A backend test asserting the capability flags for OWNER against EDITOR/VIEWER/recipient.
6. Help docs: check the Fields article in [truncated]

### GA-087: Template READ granted to every organization member, who can then read drafts and copy them out

- Severity: low. Verification: partial. Fix size: S. Audit key: `G019-P2-T7a`.
- Plan reference: P2-T7a. Plan basis: Plan lines 1216-1217 (P2-T7a: "template-configuration Action and Capability values with organization-administrator grants only"). Nothing later in the plan was found that explicitly narrows this or grants members read access. The member use of Templates comes from later request-creation and Blueprint work in the code, not from recorded plan text.

Current state:

Plan line 1216-1217 says the template-configuration Action/Capability values get organization-administrator grants only. In code, ORG_MEMBER also holds INFORMATION_REQUEST_TEMPLATE_READ (RoleCapabilities.kt:161), even though the comment at RoleCapabilities.kt:111-112 says Template authoring "stays with the roles that administer the organization rather than reaching every member". Part of that member grant is needed on purpose, and a unit test covers it. The request-creation dialog (useRequestSourceOptions.ts) and the blueprint Template picker list the organization's published Templates for ordinary members, so taking READ away from members would break creating requests from Templates. That part is intended, not a gap.

The real gap is narrower. getTemplate (InformationRequestTemplateAuthoringService.kt:130-144) only checks INFORMATION_REQUEST_TEMPLATE_VIEW, then returns toDto(definition). toDto fills draftVersion with the full projection of the editable draft (about lines 426-432). So any member can read unpublished draft configuration, and only authors should see it. A projection that leaves out the draft already exists: toPublishedDto, used for PLATFORM Templates. The member path should use it too.

The claim is only partly right about cloning. cloneTemplate (InformationRequestTemplateLifecycleService.kt:102-117) calls getTemplate as its access check but copies only a PUBLISHED Version (requirePublishedVersion), so drafts cannot be copied out. Members can copy a published organization Template into their personal scope. That matches the existing member grants [truncated]

Evidence:

- RoleCapabilities.kt:111-113: an admin-only comment, then TEMPLATE_READ in ORG_OWNER_AND_ADMIN_COMMON.
- RoleCapabilities.kt:136-162: ORG_MEMBER also gets INFORMATION_REQUEST_TEMPLATE_READ, next to BLUEPRINT_READ/CLONE and WORKFLOW_READ/CLONE.
- Action.kt:128: INFORMATION_REQUEST_TEMPLATE_VIEW maps to TEMPLATE_READ.
- InformationRequestTemplateAuthoringService.kt:130-144: getTemplate calls requireOwnerAccess(VIEW) and then toDto(definition), with no WRITE check.
- About :426-432: toDto sets draftVersion = versionRepository.findDraft(...).loadVersion. toPublishedDto sets draftVersion = null and is used only for PLATFORM.
- InformationRequestTemplateLifecycleService.kt:102-117: cloneTemplate calls getTemplate on the source, then requirePublishedVersion, so it copies only published configuration.
- InformationRequestTemplateAuthoringServiceTest.kt:160-174: the test "an organization member can list organization templates without managing them" shows the member read is intended.
- [truncated]

Fix outline:

1. In InformationRequestTemplateAuthoringService.getTemplate, after the VIEW check, return toDto (with the draft) only when the caller also passes Action.INFORMATION_REQUEST_TEMPLATE_EDIT (the WRITE-backed action) for the owner. Otherwise return toPublishedDto. Check listTemplates/toSummaryDto the same way, so member summaries do not show draft-only details beyond status. Also check the Clock Policy read at InformationRequestClockPolicyService.kt:101/109, which uses the same VIEW action.
2. Keep ORG_MEMBER TEMPLATE_READ, because request creation depends on it. Rewrite the misleading comment at RoleCapabilities.kt:111-112 to describe how the grant is actually split, with no plan references.
3. Decide whether members may clone organization Templates into their personal scope. If not, make cloneTemplate require TEMPLATE_WRITE on the source owner. If yes, keep the current behaviour to match BLUEPRINT_CLONE.
4. Tests in InformationRequestTemplateAuthoringServiceTest: an org member getTemplate gets draftVersion == null; an org admin gets the draft. Add a lifecycle test for the chosen clone rule.

No migration is needed. Help docs: check that informationRequestsSection and the Template articles describe what members can see.

Decision needed. Recommended default: Members may read and clone published organization Templates only. Drafts are readable only by Template writers.

### GA-088: Subject merge and supersession history has storage but no service

- Severity: low. Verification: partial. Fix size: M. Audit key: `G020-P2-T2`.
- Plan reference: P2-T2. Plan basis: - Plan lines 1183-1186 (P2-T2) ask for a "foundation" including merge and supersession history.
- Line 1291 asks for merge and supersession tests. The DB contract test satisfies this.
- Lines 4281-4283 (migration ledger) record V85 as the created and contract-tested merge and supersession lineage.
- Phase 10 design decision 5, around lines 3544-3548, defines the only subject operations: POST /information-requests/{id}/subjects (find or create) and GET /information-request-subjects (list). No [truncated]

Current state:

The facts in the claim are correct. Merge and supersession lineage exists only at the storage layer: the V85 table subject_identity_transition, the SubjectIdentityTransition entity, the SubjectIdentityTransitionKind enum (MERGED, SUPERSEDED) and SubjectIdentityTransitionRepository.findBySource. Nothing in src/main injects or calls that repository. No service, endpoint, or UI records a merge or supersession. Nothing reads the lineage either: InformationRequestSubjectService.knownSubject returns the alias's subjectIdentityRefId without following a transition to its successor, and listForOwner shows merged or superseded subjects as if they were live. The only coverage is the DB contract test SubjectIdentityRefContractTest ("merge and supersession history stays in one tenant and cannot fork").

The gap is narrower than "unwired required behaviour", though. P2-T2 asks for a "foundation" with merge and supersession history, and the plan's migration ledger records V85 as meeting it ("tenant-bound, single-successor, cycle-safe, append-only merge and supersession lineage"). No later phase asks for an operation that records a merge or supersession. The Phase 10 subjects design (decision 5) only covers finding or creating a subject and listing subjects. So the P2-T2 checkbox is met as written. What remains is a latent inconsistency: lineage can be stored but can never be written through the application, and resolution would ignore it if it were written.

Evidence:

- src/main/kotlin/com/docuhyphen/app/api/repository/informationrequest/SubjectIdentityTransitionRepository.kt:10-32 defines the repository with only findBySource. A grep of src/main finds no other reference outside the entity and enum files.
- src/main/kotlin/com/docuhyphen/app/api/model/entity/SubjectIdentityEnums.kt:18-22 defines SubjectIdentityTransitionKind with MERGED and SUPERSEDED.
- src/main/resources/db/migration/V85__subject_identity_ref.sql:57-131 creates the transition table, the single-successor unique constraint, the tenant/cycle guard trigger and the append-only trigger.
- src/main/kotlin/com/docuhyphen/app/api/service/informationrequest/InformationRequestSubjectService.kt:28-35 does not inject the transition repository. Lines 73-80 (knownSubject) return the alias's subject directly. Lines 64-71 (listForOwner) do not filter merged or superseded subjects.
- src/test/kotlin/com/docuhyphen/app/api/migration/SubjectIdentityRefContractTest.kt:99-190 exercises MERGED and [truncated]

Fix outline:

No migration is needed because V85 already holds the lineage.

1. Add SubjectIdentityLineageService in service/informationrequest (@ApplicationScoped) that injects SubjectIdentityTransitionRepository and SubjectIdentityRefRepository:
   - recordTransition(sourceId, targetId, kind, reason, access) checks that both subjects belong to the caller's owner and authorizes INFORMATION_REQUEST_MANAGE_PRIVACY (or a new action). It saves an append-only SubjectIdentityTransition with the actor kind and id, maps the DB guard violations (fork, cycle, cross-tenant) to stable error codes, and emits an audit event.
   - resolveCurrent(subjectId, ownerType, ownerId) follows findBySource until no successor remains, with a hop limit.
2. Use resolveCurrent in InformationRequestSubjectService.knownSubject and in party assignment by subjectIdentityRefId, so new work lands on the successor. Have listForOwner mark or hide subjects that have a successor, and add a successorId field to InformationRequestSubjectDto.
3. If the user wants the operation exposed: add POST /information-request-subjects/{id}/transitions (a sub-resource action) as a thin resource with its own try/catch log message, plus a frontend service method and a merge/supersede control in the subject privacy listing. Update the help article in help-docs/sections/articles.
4. Tests: a service test that records MERGED and SUPERSEDED, refuses cross-owner transitions, and refuses a second successor or a cycle with stable codes; knownSubject resolving an alias of a merged subject to the successor; listForOwner showing the successor; and a [truncated]

Decision needed. Recommended default: Keep the storage-only lineage and record merge and supersession operations as a documented follow-up.

### GA-089: No web-app surface for platform-administered personal feature overrides

- Severity: low. Verification: partial. Fix size: M. Audit key: `G021-P2-T1d2`.
- Plan reference: P2-T1d2. Plan basis: Plan lines 1171-1174 (P2-T1d2 names only the service, thin REST resource, admin-action approval and audit, "mirroring the organization surface already released"); lines 66-67 and 514-516 ("Platform-admin feature overrides are the only per-owner feature control"; INFORMATION_REQUESTS is in Personal and Business); line 389 and line 4185 ("Organization and personal commercial override under platform-admin control"). No plan line requires or rules out a web-app editor, and nothing supersedes or [truncated]

Current state:

P2-T1d2 lists four parts: a service, a thin REST resource, admin-action approval, and audit. All four exist and have tests. The backend has PlatformUserFeatureEntitlementService (it requires a platform admin, checks the target is an individual account, takes an AdminApprovalContext and writes audit through AuthAuditService) and PlatformUserFeatureEntitlementResource at GET/PUT /platform/users/{appUserId}/feature-entitlements, with a service test and a resource contract test. The gap is that the web app never uses these endpoints. No frontend service function, type or component calls /platform/users/{id}/feature-entitlements. platformUserSubscriptionApi.ts covers only subscription policies and trials, and nothing under platform-administration/user-subscriptions deals with feature overrides. The organization side does have a client and UI (updatePlatformOrganizationFeatureEntitlements plus OrganizationFeatureSelector and OrganizationEntitlementRow in the organization editor). So a platform admin can record a personal override only with a raw API call. The practical impact is smaller than the claim implies. PlanCatalog PERSONAL already includes INFORMATION_REQUESTS, so a personal override matters only for Free-plan users (grant) or for turning the feature off for one person. The plan never names a UI for this task.

Evidence:

src/main/kotlin/com/docuhyphen/app/api/resource/user/PlatformUserFeatureEntitlementResource.kt:23 (@Path "/platform/users/{appUserId}/feature-entitlements", GET and PUT); src/main/kotlin/com/docuhyphen/app/api/service/subscription/PlatformUserFeatureEntitlementService.kt:50,71-94,109,129 (get and replace, AdminApprovalContext, AuthAuditService.emitRequired, requirePlatformAdmin, requireIndividualAccount); src/test/kotlin also has PlatformUserFeatureEntitlementServiceTest.kt and PlatformUserFeatureEntitlementResourceContractTest.kt; web-app/src/services/platformUserSubscriptionApi.ts:38-78 (only subscription-policies, subscription-policy and subscription-trials paths); web-app/src/services/platformOrganizationApi.ts:76-81 (organization counterpart); grepping web-app/src for "feature-entitlement" or "featureEntitlement" finds only organization files (OrganizationFeatureSelector.tsx, useOrganizationEditor.ts, types/platformOrganizations.ts); [truncated]

Fix outline:

Frontend only; no migration needed. (1) In web-app/src/services/types/, add PlatformUserFeatureEntitlement and PlatformUserFeatureEntitlementsRequest/Response interfaces matching the resource DTO and PlatformUserFeatureEntitlementsUpdateRequest (including changeReason). (2) Add getPlatformUserFeatureEntitlements and updatePlatformUserFeatureEntitlements to platformUserSubscriptionApi.ts, or to a new platformUserFeatureEntitlementApi.ts. Send the admin-approval/X-Request-Id headers the same way updatePlatformOrganizationFeatureEntitlements does. (3) In platform-administration/user-subscriptions, add a feature-override section to UserSubscriptionEditorDialog. Reuse or generalize OrganizationFeatureSelector and OrganizationEntitlementRow into an owner-neutral FeatureEntitlementSelector, and load and save through useUserSubscriptions. Follow the component rules: separate Styles file, ids, circular buttons, responsive. (4) Add Vitest tests: an API test with the path and body, and a dialog/hook test like useOrganizationEditor.test.tsx. (5) Update platformAdministrationOverviewArticle.tsx in help-docs to describe per-user feature overrides, then run npx tsc --noEmit.

Decision needed. Recommended default: Add the per-user override editor to the platform user-subscriptions editor, matching the organization editor.

### GA-090: Capability rows are written by two services with duplicated publish logic

- Severity: low. Verification: confirmed. Fix size: S. Audit key: `G022-P2-T7b`.
- Plan reference: P2-T7b. Plan basis: Plan lines 1221-1232 (P2-T7b): "Settle ownership of InformationRequestTemplateVersionCapabilityRepository so the reader and the writer are not two services both writing it." Lines 1564-1567 (P3-T9a) require ad hoc creation to produce a "validated" private Template Definition and immutable published Version, which also means the same readiness validation should run. No later design decision, waiver, or non-goal was found that supersedes this.

Current state:

Two classes write InformationRequestTemplateVersionCapability rows. The first is InformationRequestTemplatePublicationService.publishTemplate, the template-administration publish path. The second is InformationRequestPrivateVersionPublisher.publish, a plain class that InformationRequestAdHocCreationService and InformationRequestAmendmentTargetResolver each build by hand with `new`. Both copy the same steps: findRequiredCapabilities, the "configures no requirements" refusal, saving the capability rows with contractVersion, flushChanges, setting the draft Version to PUBLISHED with publishedAt/publishedBy, and setting the Definition to PUBLISHED. The private publisher never calls InformationRequestTemplatePublicationReadiness.requireReady. That check refuses a FIELD requirement with no schemaVersionId, a DOCUMENT requirement with no evidence policy, and a waiver policy that does not match the permitted dispositions. So on the ad hoc and amendment paths these configurations either get past the service layer or fail later as database or persistence errors, not as keyed InformationRequestTemplateValidationException refusals carrying sectionKey and requirementKey. The other users of the repository (CapabilityGate, Materializer, ProjectionLoader) only read it. The plan asked for a single writer, and that ownership was never settled.

Evidence:

src/main/kotlin/com/docuhyphen/app/api/service/informationrequest/InformationRequestTemplatePublicationService.kt:39-71 (findRequiredCapabilities, empty refusal, requireReady at :46, capabilityRepository.save at :51, status flips). src/main/kotlin/com/docuhyphen/app/api/service/informationrequest/InformationRequestPrivateVersionPublisher.kt:43-71 (the same sequence with no requireReady, capabilityRepository.save at :51). InformationRequestAdHocCreationService.kt:71 and InformationRequestAmendmentTargetResolver.kt:23 each build their own InformationRequestPrivateVersionPublisher. Searching for requireReady/PublicationReadiness finds only InformationRequestTemplatePublicationReadiness.kt and InformationRequestTemplatePublicationService.kt. Searching for `InformationRequestTemplateVersionCapability()` construction finds only these two files.

Fix outline:

Pull the freeze step into one @ApplicationScoped owner, for example InformationRequestTemplateVersionFreezer in service/informationrequest. It would expose freeze(definition, draft, publishedByAppUserId): check findRequiredCapabilities and refuse an empty set, run InformationRequestTemplatePublicationReadiness.requireReady on the projected draft (or on a projection of the configuration request), save the capability rows, flush, then set the Version and Definition to PUBLISHED. Make it the only class that injects InformationRequestTemplateVersionCapabilityRepository for writes. InformationRequestTemplatePublicationService.publishTemplate keeps its auth and mutation context and audit event, and hands the freeze to the new class. InformationRequestPrivateVersionPublisher (or its replacement) keeps draft creation, schema compatibility, and configuration writing, then calls the freezer. Make it CDI-injected, not built with `new` in AdHocCreationService and AmendmentTargetResolver, and take the capability repository out of those two constructors. No migration is needed. Add tests showing that ad hoc creation and amendment refuse (a) a FIELD requirement with no schema version and (b) a DOCUMENT requirement with no evidence policy, each with an InformationRequestTemplateValidationException keyed to the section and requirement keys. Add a test that capability rows still match request_template_required_capabilities on both paths.

### GA-091: A retired or non-latest pinned Template Version cannot be identified on the Blueprint

- Severity: low. Verification: confirmed. Fix size: S. Audit key: `G023-P2-T9`.
- Plan reference: P2-T9. Plan basis: Plan lines 1240-1247 (P2-T9): "A Blueprint may retain a reference after that Template Version is retired for history and editing". Plan lines 1310-1312 (Phase 2 exit criteria): "Retired references remain inspectable but cannot create a new request." I found no superseding text. The Blueprint-related lines 4067-4073 (P12-T3), 4556-4560 and 4612-4613 cover pinning and compatibility only. They do not narrow the inspectability requirement.

Current state:

The backend keeps the pinned reference correctly. A Blueprint can hold a retired Template Version, and a new request from it is refused with the retired-version error. What is missing is the "inspectable" part. BlueprintDefinitionDto returns only the raw informationRequestTemplateVersionId UUID. It has no Template name, version number or retired/published status. The frontend only matches the pinned id against the latest published Version of each listed, non-retired Template (from useBlueprintTemplateChoices). So if the pin points to a retired Version, an older published Version, or a Template outside the listed scopes, the Blueprint editor just says "an earlier Template Version". It does not say which Template or which version it is, or that it is retired. The Template dropdown then shows empty. The author only finds out the Version is retired when creating a request fails.

Evidence:

src/main/kotlin/com/docuhyphen/app/api/model/dto/BlueprintDtos.kt:99-100: the DTO field is only a UUID. There are no name, version-number or status fields.
src/main/kotlin/com/docuhyphen/app/api/service/blueprint/BlueprintDefinitionService.kt:712-727: toDto() maps only the raw id.
web-app/src/app/settings/blueprints-tab/blueprint-information-request-tab/useBlueprintTemplateChoices.ts: builds choices from detail.latestPublishedVersion only.
web-app/src/app/settings/blueprints-tab/blueprint-information-request-tab/BlueprintInformationRequestTab.tsx:18 and 37-39: when the pinned id does not match a choice, it shows the fallback text "an earlier Template Version".
web-app/src/app/models/models.tsx:2097-2112: InformationRequestTemplateDto exposes only the draft and latest published Versions. There is no list of Versions and no lookup by Version id.
informationRequestTemplateService.ts has no call to fetch a Version by its id.

Fix outline:

1. Backend: add a small summary to the Blueprint DTO, for example `BlueprintInformationRequestTemplateVersionSummaryDto(templateId, templateDisplayName, versionNumber, status, retiredAt?)` in model/dto/BlueprintDtos.kt. BlueprintDefinitionDto then gets an `informationRequestTemplateVersion` summary field. Fill it in through a method on the Information Request Template service, not by reading its repository directly. The mapping goes in a dedicated mapper class, not in the service's toDto.
2. Frontend: add the matching TS interface in models.tsx. In BlueprintInformationRequestTab, use the summary to show "{Template name}, version {n}" and a retired or superseded Badge. Tell the author that new requests will be refused until they pick a published Version.
3. Tests: backend DTO or service tests for a retired pin and a non-latest pin. Update BlueprintInformationRequestTab.test.tsx so it checks that the retired badge and the Template name appear.
4. Update the Blueprint help article (informationRequestManagingArticle.tsx or the Blueprint article) to say retired pins are labelled.
No migration is needed.

### GA-092: Blueprint help article does not cover the Information Request tab

- Severity: low. Verification: confirmed. Fix size: S. Audit key: `G024-P2-T9`.
- Plan reference: P2-T9. Plan basis: Plan line 1240-1247 (P2-T9) adds the Blueprint Template Version reference: future-only effect, and a retired Version is kept but blocks new instantiation. Plan line 1049 lists "Fields, Workflow, and Blueprint help articles" as likely code areas. AGENTS.md requires a help docs update when a UI tab is added or lifecycle behaviour changes. No superseding waiver or deferral for Blueprint help was found.

Current state:

The Blueprint editor now has five tabs (Details, Documents, Business Fields, Information Request, Permissions). The Blueprint help article managingBlueprintsArticle.tsx still says "The editor has four tabs" (line 36) and has sections only for Details, Documents, Business Fields and Permissions. No Blueprint help article (managingBlueprintsArticle, blueprintOverviewArticle, orgBlueprintsArticle, usingBlueprintsArticle, blueprintPlatformArticle) mentions the Information Request tab. None of them explain that you pick a published Template there, that doing so pins that Template's current published Version, that a Remove action exists, or that changes apply only to Exchanges created afterwards. None cover what happens to a pinned Version once it is retired: it stays on the Blueprint, and the tab shows "an earlier Template Version", but new instantiation fails until the author picks a current Version. The only related help text is informationRequestManagingArticle.tsx lines 21-28, and it covers only the future-only effect of re-pointing or retiring a Version. The article is therefore both incomplete and factually wrong about the tab count.

Evidence:

web-app/src/app/settings/blueprints-tab/BlueprintEditorDialog.tsx:70 (EditorTab includes 'informationRequest'), :249-256 (five Tabs including id blueprint-editor-information-request-tab), :546 (renders the tab). web-app/src/app/settings/blueprints-tab/blueprint-information-request-tab/BlueprintInformationRequestTab.tsx:23-56: explanation text, pinned text with the "earlier Template Version" fallback, a Remove button, and a Template ChoiceSelect that picks a Template's published versionId. web-app/src/app/components/help-docs/sections/articles/managingBlueprintsArticle.tsx:36 says "The editor has four tabs:" and has h3 sections only at 38 (Details), 46 (Documents), 65 (Business Fields) and 78 (Permissions). A grep for "Information Request", "informationRequest" and "Template Version" across the Blueprint articles returns nothing. informationRequestManagingArticle.tsx:21-28 is the only Blueprint and Version guidance.

Fix outline:

1. Edit web-app/src/app/components/help-docs/sections/articles/managingBlueprintsArticle.tsx. Change "four tabs" to "five tabs". Add an "Information Request tab" h3 section between Business Fields and Permissions, with ids per the frontend rules. It should explain:
   - Choose a published Template from the Template list; the Blueprint pins that Template's current published Version.
   - Remove clears the pin.
   - Changes affect only Exchanges created afterwards; existing requests keep their Version.
   - A retired Version stays on the Blueprint and shows as an earlier Template Version, but new Exchanges cannot start the request until you choose a currently published Template.
   - Only Templates published in the Blueprint's own scope are listed.
2. Optionally add one sentence to usingBlueprintsArticle.tsx saying that an Exchange created from a Blueprint with a pinned Template starts an Information Request from that Version.
3. Keep the article under 150 lines (it is 105 now).
4. Run npx tsc --noEmit in web-app/.
No migration and no backend change are needed.

### GA-093: No production path creates a PLATFORM Template, so the platform list, copy and choice paths are always empty

- Severity: low. Verification: confirmed. Fix size: M. Audit key: `G025-P2-T7a`.
- Plan reference: P2-T7a. Plan basis: P2-T7a (plan ~1219-1220) says to refuse PLATFORM ownership through the owner configuration API. Phase 12 decision 11 (4022-4030) and P12-T6 (4121-4128) say a platform Template is a starting point that owners copy, that the owner API never creates one, and that the create dialog offers platform Templates only through copying. Line 3915 says conformance fixtures are not installed as platform Templates or seed data. No line in the plan defines who authors platform Templates, or how, and no [truncated]

Current state:

The PLATFORM scope for Information Request Templates exists in storage and read paths, but nothing in production can create a PLATFORM Template. The owner API refuses PLATFORM on purpose (InformationRequestTemplateAuthoringService.createTemplate). Ad hoc creation only makes ORGANIZATION or PERSONAL definitions (InformationRequestAdHocCreationService.scopeKindOf). No platform-admin resource authors or promotes Information Request Templates; the only platform Information Request resource is PlatformInformationRequestHealthResource. No Flyway migration inserts into information_request_template_definition, and plan line 3915 forbids installing conformance fixtures as platform Templates or seed data. The list call (findAllPlatform), the unauthenticated-owner read in getTemplate, the platform copy path (TemplateCloneDialog), the Platform tab in TemplateAdministration, and the platform choices in the Blueprint Information Request tab are all built, but in production they can only ever be empty. Only tests create platform rows, using direct SQL. The help articles (informationRequestTemplatesArticle, informationRequestManagingArticle) describe copying platform Templates that cannot exist. Workflows are different: they have a platform-template publishing path, and Information Request Templates have no equivalent.

Evidence:

src/main/kotlin/com/docuhyphen/app/api/service/informationrequest/InformationRequestTemplateAuthoringService.kt:157-164 (PLATFORM refused with "Platform Templates are not created here. Copy a platform Template..."), :113 (PLATFORM -> definitionRepository.findAllPlatform()), :133 (PLATFORM read path); InformationRequestAdHocCreationService.kt:242-248 (scopeKindOf only ORGANIZATION/PERSONAL); repository/informationrequest/InformationRequestTemplateDefinitionRepository.kt:50 (platform query); a grep over src/main shows no other assignment of scopeKind = PLATFORM; resource/informationrequest has only PlatformInformationRequestHealthResource as a platform resource; migrations that touch information_request_template_definition (V86, V96, V99, V142, V146-V148) contain no INSERT; InformationRequestTemplateInstantiationService.kt:146-149 (PLATFORM_COPY_REQUIRED); frontend web-app/src/app/settings/information-request-templates-tab/template-administration/TemplateAdministration.tsx and [truncated]

Fix outline:

A product decision comes first: who authors platform Templates. Option A: a platform-admin authoring surface. Add the resource resource/informationrequest/PlatformInformationRequestTemplateResource (POST/PUT /platform/information-request-templates, plus /{id}/versions/{versionId}/publication), backed by a new PlatformInformationRequestTemplateAuthoringService. That service reuses the configuration, validation and publication logic from InformationRequestTemplateAuthoringService and InformationRequestTemplateLifecycleService, but checks the platform-admin role instead of the owner entitlement guard, since InformationRequestTemplateEntitlementGuard already throws for PLATFORM. Audit these writes under AuditOwnerScope.Platform. It would only accept PLATFORM Schemas. Option B: a platform-admin action that promotes a published ORGANIZATION Template Version into a PLATFORM copy (POST /platform/information-request-templates/promotions). Option C: accept that no platform Templates exist yet and remove or hide the Platform tab, the platform copy option and the Blueprint platform choices, then update informationRequestTemplatesArticle.tsx and informationRequestManagingArticle.tsx. None of the options needs a migration; the existing V86 scope checks already allow PLATFORM rows. Tests: a platform-admin creates and publishes a PLATFORM Template; a non-admin is refused; an owner copies it into PERSONAL and ORGANIZATION scope; POST /information-requests still refuses the platform Version with PLATFORM_COPY_REQUIRED; a frontend admin-surface test. Also update the help docs and follow the [truncated]

Decision needed. Recommended default: Let platform administrators author PLATFORM Templates through the existing Template editor.

### GA-094: Credential cutoff after reassignment or revocation has no transaction test

- Severity: low. Verification: confirmed. Fix size: S. Audit key: `G035-P3-T11c`.
- Plan reference: P3-T11c. Plan basis: Plan 1627-1628 puts the requirement in force and marks it [x]. P5-R27 (plan ~2368-2372) closes the recheck by saying "Focused unit tests cover persisted old credential revocation and bootstrap session cutoff". That describes what was done. It does not waive the transaction-test half of P3-T11c. P5-R28 (2373-2375) requires transaction-backed coverage only for preserving response and provenance, which the concurrency test provides. Nothing in the Status section, the Development-Stage Constraint [truncated]

Current state:

The production cutoff exists. InformationRequestPartyService reassignment (line ~501) and revocation (line ~598) call InformationRequestBootstrapShareLinkService.revokeAllForShare(shareId) before revoking the old Share, and revokeAllForShare (line 330-333) calls RequestAccessSessionService.revokeAllForShareLink for each link. Tests only cover this with mocks or in-memory fakes. InformationRequestPartyServiceTest:824/1007 only verify that revokeAllForShare is called. InformationRequestBootstrapShareLinkServiceTest:356-362 only verify revokeAllForShareLink calls. InformationRequestNoAuthReadAccessServiceTest:116-138 is a plain JUnit test with fakes that shows sessions are refused after revokeAllForShareLink or a principal change. The only @QuarkusTest that exercises reassignment, InformationRequestPartyConcurrencyTransactionTest, builds InformationRequestPartyService with mock<InformationRequestBootstrapShareLinkService>() at line 203. InformationRequestContactProofTransactionTest persists bootstrap ShareLinks but tests code budgets, lockout and rollback on session issuance, not reassignment or revocation cutoff. No database-backed test commits a reassignment or party revocation and then shows that a pre-existing bootstrap ShareLink or RequestAccessSession no longer resolves. The line 1628 requirement for "service and transaction tests" is only half met: service-level unit tests exist, transaction tests do not.

Evidence:

plans/...IMPLEMENTATION-PLAN.md:1627-1628 (P3-T11c requires service and transaction tests for old credential cutoff); src/main/kotlin/.../service/informationrequest/InformationRequestPartyService.kt:500-501 and 597-598 (revokeAllForShare before shareService.revoke); InformationRequestBootstrapShareLinkService.kt:330-345 (revokeAllForShare -> requestAccessSessionService.revokeAllForShareLink); src/test/kotlin/.../InformationRequestPartyConcurrencyTransactionTest.kt:203 (mock<InformationRequestBootstrapShareLinkService>()) and 270 (mock<RequestAccessSessionService>()), and its only tests (lines 74, 105, 136) cover response-save ordering, parent termination and provenance; InformationRequestPartyServiceTest.kt:824,1007 and InformationRequestBootstrapShareLinkServiceTest.kt:356-362 are Mockito verify calls; InformationRequestNoAuthReadAccessServiceTest.kt:116-138 uses in-memory fakes; InformationRequestContactProofTransactionTest.kt tests (57,74,108,151) do not cover reassignment or [truncated]

Fix outline:

No production change and no migration are needed. Add a @QuarkusTest, for example src/test/kotlin/com/docuhyphen/app/api/service/informationrequest/InformationRequestCredentialCutoffTransactionTest.kt, that uses the real InformationRequestPartyService, InformationRequestBootstrapShareLinkService, RequestAccessSessionService and repositories against PostgreSQL. Seed fixtures with neutral synthetic names, following the fixture pattern in InformationRequestPartyConcurrencyTransactionTest and InformationRequestContactProofTransactionTest. Seed a request with an active party bound to a Share, issue a VERIFICATION_BOOTSTRAP ShareLink through the issue command, and create a RequestAccessSession for it. Case 1: commit a reassignment in its own transaction. Then assert in a fresh transaction that the ShareLink row is revoked, the session row has revokedAt set, RequestAccessSessionService/InformationRequestNoAuthReadAccessService.resolve with the old link token and session token throws, the old Share is revoked, and the new Share is bound to the replacement principal with no raw token returned. Case 2: do the same for party revocation. Optionally add Case 3: a reassignment that fails after revokeAllForShare, for example a forced exception in grantRoleKeyWithPrincipalProvenance, rolls back so the old link and session stay valid.

### GA-095: Missing per-precondition reassignment cases and trusted-matrix cases

- Severity: low. Verification: confirmed. Fix size: M. Audit key: `G036-Phase`.
- Plan reference: Phase 3 Tests to write first. Plan basis: - Plan lines 1727-1735 put these tests in force:
  - the Trusted Organization selection, policy revision, suspension, acceptance, expansion, reassignment, already-issued response, session and recovery matrix;
  - one request-specific recipient replacement case per replacePrimaryRecipient precondition.
- Line 395 requires the five preconditions to be treated as five separate, explicit decisions.
- P3-T11a (lines 1588-1618) changes what each case should assert but waives none of them:
  - (1) not [truncated]

Current state:

This is a test-coverage gap only. The production behaviour for all five decisions exists in InformationRequestPartyService.reassignMutation, and P3-T11a records a decision for each replacePrimaryRecipient precondition. The tests that should pin those decisions are only partly there:

- Covered today:
  - Precondition 2 (reassign while the request is ACTIVE; terminal parent denied).
  - Precondition 4 (a non-trusted PARTICIPANT principal is accepted).
- Missing:
  - Precondition 1: no test shows a personally owned request can be reassigned without a sender organization. The Fixture is always organization-owned.
  - Precondition 3: no test reassigns an inactive party and expects the require(party.active) rejection.
  - Precondition 5: no test reassigns with a non-USER RequestAccessContext principal. The caller is always a USER.
- Trusted matrix for reassignment:
  - Trust suspension and TRUSTED_RECIPIENT_UNAVAILABLE are tested only on assign (InformationRequestPartyServiceTest lines 936 and 953). No reassign case passes an exchangeRecipientId whose trust is suspended, revised or unavailable.
  - No trust policy-revision test exists in the Information Request tests.
  - Session and recovery behaviour is covered only indirectly: NoAuthReadAccessServiceTest "sessions cannot be combined with ... a reassigned recipient", plus the P3-T11b/c bootstrap revocation work. There is no trusted-selection-specific matrix.
  - The trusted-group revalidation exists only at the Exchange layer (ExchangeRecipientServiceTest:126).

Evidence:

- The test file is src/test/kotlin/com/docuhyphen/app/api/service/informationrequest/InformationRequestPartyServiceTest.kt.
  - Its reassign tests are at lines 979, 1028, 1188 and 1232. They cover the ETag, an active request, lock order and a terminal parent.
  - The trust-suspended refusal (line 936) and the trusted-recipient-unavailable refusal (line 953) both call fixture.service.assign only.
  - activeActingParty (line 1395) always returns an active PARTICIPANT party. No reassign test deactivates the party.
- In src/main/kotlin/.../service/informationrequest/InformationRequestPartyService.kt, reassignMutation (lines 479-506) has these checks:
  - require(party.active) at line 492.
  - requireSupportedActingPrincipal at line 484.
  - requireAssignableRecipient when exchangeRecipientId is set (lines 497-499). No test exercises it on the reassign path.
- A grep of src/test for reassign turned up nothing else relevant:
  - PartyResourceContractTest and PartyConcurrencyTransactionTest [truncated]

Fix outline:

No migration is needed. This is tests only; production code is unchanged.

Add to InformationRequestPartyServiceTest:
1. `reassigning a party on a personally owned request needs no sender organization`: a Fixture variant with a personal owner and a null owner organization. Assert the reassign succeeds and the new Share is granted.
2. `reassigning a revoked party is rejected`: set active = false on the party from activeActingParty. Assert IllegalArgumentException, no revoke and no grant.
3. `reassigning with a non-User caller authorizes through the central service`: the RequestAccessContext principal is a PARTICIPANT (or delegated). Assert authorize(INFORMATION_REQUEST_MANAGE_PARTIES) is called and the reassign succeeds. Also assert a denied Decision blocks it.
4. `reassigning onto a suspended trusted recipient is refused with TRUST_SUSPENDED`, plus a TRUSTED_RECIPIENT_UNAVAILABLE variant. Both pass exchangeRecipientId, and both assert the old Share is not revoked.
5. `reassigning onto a trusted group recipient revalidates attestation and reconciles group Shares`: verify requireAssignablePartyRecipient is invoked with the new principal.

Trusted matrix: add a parameterized InformationRequestTrustedPartyMatrixTest. For each state (active, suspended, revised policy that drops the group, unavailable), crossed with each operation (assign, reassign, response already issued, access session read, recovery by replacement party), assert the refusal code or the success. Include that a prior response keeps its provenance and that the old sessions are revoked. Reuse the existing [truncated]

### GA-096: Temporary-App-User exclusion tests only partially present

- Severity: low. Verification: partial. Fix size: S. Audit key: `G037-Phase`.
- Plan reference: Phase 3 Tests to write first. Plan basis: - Lines 1723-1726 list the required tests: no request party creates a temporary App User, and all three User-only paths refuse a participant-principal party.
- Lines 684-695 (item 41) require the constraint to be "proven by test before Phase 4 exposes the no-auth surface".
- Line 392 records the same decision.
- The Latest Implementation Result (~4618-4627) restates that no request path creates a temporary App User.
- Nothing in the plan supersedes or defers these tests.

Current state:

The production guards all exist. ExternalEmailAcceptancePolicyService.validate denies any Share whose principalKind is not USER. resendNoAuthPrimaryRecipientInvitation requires primaryShare.principalKind == USER. recordExternalEmailPrimaryDecision refuses a PARTICIPANT share. InformationRequestPartyService has no AppUserService dependency and creates external parties through ExternalParticipantService as PrincipalKind.PARTICIPANT. The gap is in the tests. Only recordExternalEmailPrimaryDecision has a test that sends a PARTICIPANT-kind share and expects a refusal (ExchangeRecipientServiceTest:513-530). resendNoAuthPrimaryRecipientInvitation has no test with a PARTICIPANT primary share. Its tests at ExchangeAccessManagementServiceTest:373 and :410 cover only a USER-share success and a requireRecipientSignIn refusal. ExternalEmailAcceptancePolicyServiceTest has no PARTICIPANT case either: its share helper always sets principalKind = USER (line 151). The claim overstates one point. There is some Information Request coverage that no temporary App User is created. InformationRequestParticipantAccountUpgradeServiceTest:98 checks that the upgrade path only reads an existing AppUser. InformationRequestPartyServiceTest:228 checks that external contact assignment produces a PARTICIPANT principal through ExternalParticipantService. What no test does is directly assert that party assignment never creates an AppUser with isTemporary = true.

Evidence:

- The plan requires these tests at plan lines 1723-1726 and makes them a pre-Phase-4 obligation at 684-695 (item 41).
- src/main/kotlin/.../service/exchange/ExternalEmailAcceptancePolicyService.kt:26 has the guard `if (recipientShare.principalKind != PrincipalKind.USER) deny()`.
- src/main/kotlin/.../service/exchange/ExchangeAccessManagementService.kt:424 has the guard `require(primaryShare.principalKind == PrincipalKind.USER)` inside resendNoAuthPrimaryRecipientInvitation (the function starts at line 399).
- src/test/kotlin/.../service/exchange/ExchangeRecipientServiceTest.kt:513-530 contains `participant principal cannot use the no-auth primary recipient decision path`, which is present.
- src/test/kotlin/.../service/exchange/ExchangeAccessManagementServiceTest.kt:340-414: the resend tests use `directShare(PrincipalKind.USER, ...)` or requireRecipientSignIn = true. There is no PARTICIPANT case.
- src/test/kotlin/.../service/exchange/ExternalEmailAcceptancePolicyServiceTest.kt: the [truncated]

Fix outline:

This is test-only work: no production change and no migration.
1. In ExchangeAccessManagementServiceTest, add `resend no-auth invitation refuses a participant principal primary`. Set up an unsigned INITIATED Exchange whose primary recipient is EXTERNAL_EMAIL, and whose directShare is PrincipalKind.PARTICIPANT with status ACTIVE. Assert IllegalArgumentException. Verify that there are no otpService.generateEmailOtp, exchangeRepository.update, noAuthExchangeAccessTokenService.issue or notificationDeliveryService.scheduleAfterCommit calls, and that appUserService.getById is never called.
2. In ExternalEmailAcceptancePolicyServiceTest, add `participant principal share is denied before any user lookup`. Build the share with PrincipalKind.PARTICIPANT, run it once with authenticatedAppUserId null and once non-null, and assert the generic eligibility exception. Verify appUserService and organizationExchangePolicyService have no interactions.
3. In InformationRequestPartyServiceTest, extend the external contact assignment test, or add a new one, to assert that no AppUser is created. Either verify there are no interactions with an AppUserService/AppUserRepository mock, or add a Postgres-backed check in InformationRequestPartyConcurrencyTransactionTest that `SELECT count(*) FROM app_user WHERE is_temporary` is unchanged after assigning an external participant party and issuing its bootstrap link.

### GA-097: No test of two Information Requests using the same stable Field

- Severity: low. Verification: confirmed. Fix size: S. Audit key: `G038-Phase`.
- Plan reference: Phase 3 Tests to write first. Plan basis: Plan 1737 (Phase 3 Tests to write first) and 1746-1747 (exit criteria). I searched the plan for "stable field" (704, 955, 1085, 1263, 1737) and found nothing that supersedes, defers or waives this test.

Current state:

The code is built so that two requests should not collide. Each Information Request gets its own INFORMATION_REQUEST Schema Assignment. Field values are unique per (schema_assignment_id, field_contract_id) (V36 ux_field_value_binding) and live in a root value set for each assignment (V79). What is missing is the test the plan asks for: none of them creates two Information Requests on one Exchange that collect the same stable fieldDefinitionId and then checks that each has its own Schema Assignment, root value set and answers, and that writing an answer to one request does not change the other or the Exchange's own assignment.

Some nearby tests come close but do not check this:
- SchemaVersionStableFieldBindingTest only covers one Schema Version.
- ExchangeMetadataSeparationTest uses a single request.
- RootFieldValueSetTest covers a single assignment.
- InformationRequestTemplateCollectedFieldContractTest and InformationRequestTemplatePersistenceContractTest check the same field across Template Versions, not across runtime requests.
- The sibling-request fixtures (insertSiblingRequest, otherRequestId in the evidence and external-source contract tests, the privacy tests) add a second request but no Field answers.
- RecurringSupplementalRequestConformanceTest creates a supplement on the same Exchange from a Template that collects scenario.answers.fieldDefinitionId. It only patches the Document requirement, though, and never asserts that the Field answers of the two requests stay separate.

Evidence:

- Plan line 1737 lists "Two Information Requests using the same stable Field without collision." under Phase 3 Tests to write first. Plan lines 1746-1747 give the exit criterion "Several Information Requests can coexist on one Exchange."
- src/test/kotlin/com/docuhyphen/app/api/service/informationrequest/conformance/ExchangeMetadataSeparationTest.kt:30 has a single test, and it uses one request.
- src/test/kotlin/com/docuhyphen/app/api/service/fields/RootFieldValueSetTest.kt:342-488 only tests a single assignment.
- src/test/kotlin/com/docuhyphen/app/api/migration/InformationRequestSubmissionRuntimeSqlFixture.kt:303 insertSiblingRequest inserts only the request and a SUBJECT party, with no schema assignment or field values.
- src/test/kotlin/com/docuhyphen/app/api/service/informationrequest/conformance/RecurringSupplementalRequestConformanceTest.kt:65-140 creates a supplement, then patches only supplementDocument, and makes no field-value separation assertion.
- [truncated]

Fix outline:

Add src/test/kotlin/com/docuhyphen/app/api/service/informationrequest/conformance/SharedStableFieldRequestIsolationTest.kt, a QuarkusTest built on SubmissionRuntimeSqlFixture plus FieldAnswerSqlFixture. It should:
1. Materialize one Template Version whose FIELD requirement collects fieldDefinitionId F.
2. Create two ISSUED requests on the same Exchange from that version, either by materializing twice or with a sibling helper that also materializes an INFORMATION_REQUEST schema assignment.
3. Optionally give the Exchange its own EXCHANGE assignment that also binds F.
4. Patch different answers for F through each request's response draft service.
5. Assert the following:
   - The two requests have distinct schema_assignment ids, and each has one root value set.
   - Each field_value row is keyed by its own assignment.
   - Reading each request's projection returns only its own answer.
   - Submitting or closing one request leaves the other request's value and the Exchange's value unchanged.
   - EXCHANGE field cardinality is unchanged.

No migration is needed (the next free migration would be V151). No production code changes are needed unless the test shows a real collision.

### GA-098: No-auth commands return 500 when the owner is operationally suspended

- Severity: low. Verification: confirmed. Fix size: S. Audit key: `G051-P5-T1b`.
- Plan reference: P5-T1b. Plan basis: Plan lines 2072-2076 (P5-T1b) require both the authenticated and the no-auth PATCH surfaces to "map stable service refusals to HTTP responses". Nothing I read in the plan waives, defers or supersedes this for the no-auth surface.

Current state:

InformationRequestNoAuthRequestResource wraps each command in try/catch and sends every exception to a private handleException. That function maps CommandPrecondition, FieldsPrecondition, CommandReceiptConflict, Lifecycle, FieldValidation, IllegalState, IllegalArgument, Forbidden and Unauthorized exceptions. It has no case for SubscriptionDenialException, which extends RuntimeException directly and so matches none of those. It also does not rethrow the exception to the global SubscriptionDenialExceptionMapper the way the authenticated resources do. The four no-auth mutations are patchResponses, addGroupOccurrence, removeGroupOccurrence and reorderGroupOccurrences. The response-draft and group-occurrence services call entitlementGuard.requireRequestMutation or requireNotOperationallySuspended for all four. So when the owner's subscription is operationally suspended or mutations are not allowed, a no-auth respondent gets a 500 "Request failed" with an error log, not the structured 403 SubscriptionDenialDto. No no-auth test covers this path.

Evidence:

- InformationRequestNoAuthRequestResource.kt lines 349-373 (handleException): has no SubscriptionDenialException branch, and the else branch logs and returns INTERNAL_SERVER_ERROR "Request failed". The catch blocks at lines 179-181, 221-223, 262-264 and 305-307 all route to it. `grep SubscriptionDenial` finds nothing in the file.
- SubscriptionDenialException.kt: `class SubscriptionDenialException(val denial) : RuntimeException`, so no mapped supertype catches it.
- SubscriptionDenialExceptionMapper.kt maps it to a 403 with a SubscriptionDenialDto body.
- The authenticated resources rethrow it to that mapper with `if (exception is SubscriptionDenialException) throw exception`: InformationRequestResponseResource.kt:117, InformationRequestGroupOccurrenceResource.kt:167, InformationRequestResource.kt:268, InformationRequestCommandHttp.kt:57, InformationRequestEvidenceHttp.kt:103, InformationRequestTemplateResource.kt:214.
- InformationRequestEntitlementGuard.kt:64 throws [truncated]

Fix outline:

In InformationRequestNoAuthRequestResource.handleException, add the same passthrough the authenticated resources use (`if (exception is SubscriptionDenialException) throw exception`) before the `when`, so the global SubscriptionDenialExceptionMapper returns a 403 with a SubscriptionDenialDto. The alternative is a `when` branch that returns that 403 directly. Also check the other no-auth resources in resource/informationrequest (evidence, submit and other no-auth command resources) for the same missing passthrough. Add a no-auth contract test that puts the Exchange owner's subscription into an operationally suspended or mutations-not-allowed state. It should assert 403 with the denial body for PATCH /no-auth/information-requests/{id}/responses and for POST, DELETE and PATCH-order on /group-occurrences. No migration is needed and no help-docs change (this is a bug fix).

### GA-099: The template validator never checks group min/max occurrence bounds

- Severity: low. Verification: confirmed. Fix size: S. Audit key: `G052-P5-T2a`.
- Plan reference: P5-T2a. Plan basis: Plan lines 2100-2113 (P5-T2a): the group definition includes "min/max occurrence cardinality", authored through the same configuration document and validator. The completion note lists what the validator checks (distinct keys, parent resolution, cycle detection, occurrence-anchor resolution) and leaves the bounds out. That describes what was built; it does not waive the bounds. It also says cross-requirement rules are "enforced only ... at the Kotlin validator". Lines 2121-2127 (P5-T2b/c) rely [truncated]

Current state:

The server never checks the occurrence bounds of a repeatable group. InformationRequestTemplateConfigurationValidator.normalizeGroups/normalizeGroup check the key format, that keys are distinct, that the parent exists, self-parenting and nesting cycles. Nothing checks minOccurrences >= 0, maxOccurrences >= 1 or maxOccurrences >= minOccurrences. The request DTO has no bean-validation annotations on these fields either. The only server-side guard is the database CHECK constraints in V111 (ck_request_template_group_min, ck_request_template_group_max). A draft with bad bounds therefore fails with a Hibernate/Persistence constraint exception. InformationRequestTemplateResource.handleException does not map that exception, so the author gets a generic 500 "Request failed" instead of an InformationRequestTemplateValidationException refusal that names the group. The client check in templatePlanValidation.ts (lines 47-51) rejects a negative minimum and max < min. It does not reject maxOccurrences = 0 when min is 0, and the database refuses that. No backend test authors invalid bounds.

Evidence:

src/main/kotlin/com/docuhyphen/app/api/service/informationrequest/InformationRequestTemplateConfigurationValidator.kt:268-320 (normalizeGroups/normalizeGroup; no grep hit for minOccurrences/maxOccurrences anywhere in the file). src/main/kotlin/com/docuhyphen/app/api/model/dto/InformationRequestTemplateDtos.kt:308-313 (InformationRequestTemplateGroupRequest, plain Int fields with no constraints). src/main/resources/db/migration/V111__information_request_template_requirement_group.sql:37-40 (the database CHECK constraints are the only server-side guard). src/main/kotlin/com/docuhyphen/app/api/resource/informationrequest/InformationRequestTemplateResource.kt:211-232 (a persistence exception falls to the else branch and returns 500). InformationRequestTemplateConfigurationWriter.kt:181-182 copies authored.minOccurrences/maxOccurrences into the entity without checking them. web-app/src/app/information-requests/template-document/templatePlanValidation.ts:47-51 (client check only; max=0 is [truncated]

Fix outline:

In InformationRequestTemplateConfigurationValidator.normalizeGroup, after normalizing the key, call refuseGroup(..., groupKey) in three cases: minOccurrences < 0 ("cannot require a negative number of occurrences"), maxOccurrences != null && maxOccurrences < 1, and maxOccurrences != null && maxOccurrences < minOccurrences. The draft is then refused with a structured InformationRequestTemplateValidationException that names the group, the same way other refusals work. Consider an optional sanity upper limit if the plan wants one; do not add one otherwise. In templatePlanValidation.ts, also flag maxOccurrences < 1 so the client matches the server, and add a case to templateDraftValidation.test.ts. Add InformationRequestTemplateConfigurationValidatorTest cases for each of the three invalid bounds, each asserting the refusal carries the group key, plus a valid case (min=0/max=null, min=2/max=2). No migration is needed: the V111 CHECK constraints stay as the backstop. No help-doc change unless an article lists the validation rules.

### GA-100: The REJECTED completeness state is never produced

- Severity: low. Verification: partial. Fix size: M. Audit key: `G053-P5-T7`.
- Plan reference: P5-T7. Plan basis: Plan lines 2174-2177 (P5-T7) require only "defined denominator behavior" for rejected responses, plus a composable evaluator that Phase 6 and Phase 7 extend. That is satisfied at the contract level.

Plan lines 2621-2634 (P6-T4) say "Reviewer acceptance and rejection arrive with review in Phase 8 and add to these states rather than replacing them". This implies review rejection was expected to feed item state.

Plan line 3125 (P8-T4) covers requirement outcomes including rejected, and lines [truncated]

Current state:

The factual claim holds. InformationRequestCompletenessItemState.REJECTED is declared, but no production code ever emits it. The built-in structured path (InformationRequestCompletenessProgressService.contributionFor) and both registered extension evaluators (InformationRequestEvidenceCompletenessEvaluator and InformationRequestAttestationCompletenessEvaluator) only produce HIDDEN, OPTIONAL_UNANSWERED, COMPLETE or INCOMPLETE. That makes the REJECTED branch in InformationRequestSubmissionReadinessEvaluator.stateOf (which folds it into INCOMPLETE) unreachable in production. Only a fake "review:" extension contribution in the unit test produces it.

The plan requirement is narrower than the claim suggests. P5-T7 asks that rejected responses "have defined denominator behavior", and that behavior is defined. Through the extension contract, a REJECTED contribution counts in the denominator but not the numerator, the truth-table test pins this down, and readiness treats it as blocking. What is missing is any producer. Reviewer rejection (Phase 8 InformationRequestReviewOutcome.REJECTED / CHANGES_REQUIRED) never feeds back into completeness. So in a correction cycle, a returned item that still has its previous response is reported as COMPLETE, not REJECTED. The REJECTED enum value and the readiness branch are effectively dead contract surface.

Evidence:

src/main/kotlin/com/docuhyphen/app/api/service/informationrequest/model/InformationRequestCompletenessProgressModels.kt:16 declares REJECTED.
InformationRequestCompletenessProgressService.kt:130-139 has the structured state `when`, with no REJECTED branch. Lines 145-147 set the denominator and numerator flags only for COMPLETE and INCOMPLETE.
InformationRequestEvidenceCompletenessEvaluator.kt:33-41 and InformationRequestAttestationCompletenessEvaluator.kt:36-43 emit only HIDDEN, OPTIONAL_UNANSWERED, COMPLETE and INCOMPLETE.
InformationRequestSubmissionReadinessEvaluator.kt:80-81 is the only production reference to CompletenessItemState.REJECTED (grep over src/main).
InformationRequestCompletenessProgressServiceTest.kt:36-104 gets REJECTED only from a hand-built extension contribution with itemKey "review:..." (lines 56-63), and asserts total 4 and completed 2.
Review outcomes exist separately: InformationRequestReviewAggregator.kt:68 and InformationRequestReviewCycleService.kt:111. No [truncated]

Fix outline:

Option A: wire the state, which keeps the enum.
1. Add InformationRequestReviewCompletenessEvaluator (@ApplicationScoped, implements InformationRequestCompletenessContributionEvaluator) in service/informationrequest.
2. For each Requirement in an open correction response cycle, whose latest settled review outcome is REJECTED or CHANGES_REQUIRED and which has no response revision after that settlement, emit REJECTED with contributesToDenominator=true and contributesToNumerator=false. Get the outcome through a method on InformationRequestReviewQueryService or InformationRequestReviewCycleService, not through the review repository directly.
3. Merge per requirementId with the evidence and attestation contributions so a review REJECTED overrides their COMPLETE. Today, extension contributions for the same requirement are simply concatenated, so this needs a precedence rule in InformationRequestCompletenessProgressService.evaluate. REJECTED > INCOMPLETE > COMPLETE is one option.
4. Tests:
   - A progress-service integration test: after a review settles CHANGES_REQUIRED, the returned item shows REJECTED and counts in the denominator only. After the respondent revises it, it returns to COMPLETE.
   - A readiness test showing the item blocks final submission.
5. Update the help article in web-app/src/app/components/help-docs/sections/articles on progress, and the frontend progress rendering if states are shown.

Option B: remove the dead surface.
Drop REJECTED from InformationRequestCompletenessItemState, drop the readiness branch at [truncated]

Decision needed. Recommended default: Implement it: a returned REJECTED or CHANGES_REQUIRED Requirement shows REJECTED, counts in the denominator only, and blocks resubmission until revised.

### GA-101: The progress formula is undocumented and the summary label is wrong

- Severity: low. Verification: confirmed. Fix size: S. Audit key: `G054-Phase`.
- Plan reference: Phase 5 exit criteria. Plan basis: - Plan line 2225 (Phase 5 exit criteria): "Progress uses one documented formula and does not conflict with ordinary Exchange Document counts."
- P5-T7 (lines 2175-2177): optional, hidden, waived, rejected and conditional responses must have defined denominator behavior.
- I found no superseding, waiving or deferring text for the documentation or label requirement in the plan.

Current state:

The code has a single deterministic progress formula, in InformationRequestCompletenessProgressService.contributionFor. An item counts in the denominator when its state is COMPLETE or INCOMPLETE. It counts in the numerator only when it is COMPLETE. The states break down as follows:
- A CONDITIONAL item whose condition is not TRUE is HIDDEN and is left out of both counts.
- An unanswered OPTIONAL item is OPTIONAL_UNANSWERED and is left out of both counts.
- An answered OPTIONAL item is COMPLETE, so it counts in both the numerator and the denominator.
- A completing exception disposition, such as not applicable, unavailable or waived, makes the item COMPLETE.

Nothing user-facing documents this formula:
- The help article's Progress section only defines when an answer counts as complete.
- The overview article says the tab shows "how many required answers are complete".

InformationRequestExchangeSummaryService passes the counts through as completedCount / requiredCount. InformationRequestSummaryRow then shows "N of M required answers". As soon as a respondent answers an optional item, both N and M go up by one, so the label overstates the required work. For example, with 3 required items and 1 answered optional item, the row shows "4 of 4 required answers" when there are really 3 required answers. The code works and is deterministic. The gap is the missing documentation and the wrong label and field name.

Evidence:

- src/main/kotlin/com/docuhyphen/app/api/service/informationrequest/InformationRequestCompletenessProgressService.kt:130-147: state `when` (HIDDEN / OPTIONAL_UNANSWERED / COMPLETE / INCOMPLETE). contributesToDenominator = COMPLETE || INCOMPLETE, so an answered OPTIONAL item counts. Lines 183-188: a completing exception disposition counts as complete.
- src/main/kotlin/com/docuhyphen/app/api/service/informationrequest/InformationRequestExchangeSummaryService.kt:55-56: completedCount = count of contributesToNumerator, requiredCount = count of contributesToDenominator.
- web-app/src/app/information-requests/exchange-tab/information-request-summary-row/InformationRequestSummaryRow.tsx:82,85: text and aria-label read "N of M required answers".
- web-app/src/app/components/help-docs/sections/articles/informationRequestTemplatesArticle.tsx:88-93: the Progress section covers only when an answer is complete. There is no denominator rule.
- [truncated]

Fix outline:

1. Pick the semantics. The recommended option is to keep answered optional items out of the "required" metric. In InformationRequestCompletenessProgressService.contributionFor, set contributesToDenominator/contributesToNumerator to false when binding.requiredness == OPTIONAL. The alternative is to keep the formula and rename the DTO field and label from "required answers" to "answers". Whichever you choose, keep the naming consistent with InformationRequestSummary.requiredCount and the respondent section summaries (sectionSummaries.ts).
2. Update InformationRequestSummaryRow.tsx (text and aria-label, lines 82/85) if the label changes.
3. Document the formula in the Progress section of informationRequestTemplatesArticle.tsx. It should say what counts toward the total: required items and conditional items whose condition is true. It should say that optional unanswered and hidden items are excluded. It should say that allowed exception answers (not applicable, unavailable, waived) count as complete. It should also say how an answered optional item is treated. Align the wording in informationRequestsOverviewArticle.tsx:12 and informationRequestSubmissionArticle.tsx:12.
4. Tests:
   - Extend the progress truth-table test in src/test/kotlin (the InformationRequestCompletenessProgressService tests) with an answered-optional case.
   - Update ExchangeInformationRequestsTab.test.tsx and sectionSummaries.test.ts for the label or counts.
5. No migration is needed.
6. Run `npx tsc --noEmit` in web-app.

### GA-102: The inactive-condition notice ignores occurrence path

- Severity: low. Verification: confirmed. Fix size: S. Audit key: `G056-P5-T10`.
- Plan reference: P5-T10. Plan basis: Plan lines 2192-2204 (P5-T10, marked complete) say the respondent workspace keys condition state by rule key plus occurrence path. Line 2184 (P5-T9) says the workspace names each inactive condition rule. Lines 2312-2313 (P5-R15) say not to hide UNKNOWN conditions as a substitute for ancestor resolution. Lines 2314-2316 (P5-R16) require hidden-policy handling for UNKNOWN sequences. Nothing in the plan supersedes or defers per-occurrence notices.

Current state:

The respondent workspace does not key the inactive-condition notice by rule key plus occurrence path. StructuredResponseInactiveConditionNotices collapses evaluations to rule keys. A rule counts as inactive only when no evaluation for that rule key is TRUE at any occurrence, and each notice is rendered once per template requirement, not per occurrence. So if a rule is TRUE in one repeated entry, the "is not asked because of an earlier answer" notice disappears for every other entry where the same rule is FALSE or UNKNOWN. Those entries' conditional Requirements are then silently hidden by isRequirementActive with no explanation. A related gap: hiddenClearConfirmations only treats FALSE evaluations as inactive and matches response.occurrencePath exactly. The backend (InformationRequestResponseDraftService.enforceHiddenResponsePolicies) hides and clears on any non-TRUE state, including UNKNOWN, and falls back to the root-occurrence evaluation. For a CLEAR_WITH_CONFIRMATION rule that is UNKNOWN, or that is resolved only through the root fallback, over a response with data, the backend throws HIDDEN_RESPONSE_CLEAR_CONFIRMATION_REQUIRED, but the UI never offers the confirmation checkbox. The rest of the workspace (isRequirementActive, conditionByScope, the draft patches) is already scope-keyed correctly.

Evidence:

web-app/src/app/information-requests/structured-response-workspace/StructuredResponseInactiveConditionNotices.tsx:22-28: the inactive rules are a set of ruleKeys filtered by `!conditionEvaluations.some(e => e.ruleKey === ruleKey && e.state === TRUE)`, and occurrencePath is never read. Lines 32-40 render one notice per template requirement, not per occurrence. InformationRequestStructuredResponseWorkspace.tsx:54 mounts it once at the workspace root with request.conditionEvaluations. structuredResponseWorkspaceState.ts:86-96: isRequirementActive is scope-keyed, with a root fallback, and hides anything that is not TRUE. structuredResponseWorkspaceState.ts:223-253: hiddenClearConfirmations filters `evaluation.state === FALSE` only and matches the exact `${ruleKey}:${response.occurrencePath}`, with no root fallback. src/main/kotlin/com/docuhyphen/app/api/service/informationrequest/InformationRequestResponseDraftService.kt:463-499: the backend looks up the (ruleKey, occurrencePath) [truncated]

Fix outline:

Frontend only; no migration is needed (V151 stays free).
1) Make the inactive notice per-occurrence. Either render it inside each occurrence in StructuredResponseOccurrenceList / the occurrence component, or pass controller.shownOccurrences, the grouping data and conditionByScope into StructuredResponseInactiveConditionNotices. For each occurrence and each anchored requirement with a conditionalRuleKey where `!isRequirementActive(requirement, occurrence.occurrencePath, conditionByScope)`, emit one notice. Include a sanitized occurrence path or occurrence id in the element id and key. Name the occurrence in the text if there are several, and optionally distinguish UNKNOWN ("waiting on an earlier answer") from FALSE.
2) In hiddenClearConfirmations, treat every non-TRUE state (FALSE and UNKNOWN) as inactive. Resolve each response's evaluation the same way the backend does: exact `${ruleKey}:${occurrencePath}` first, then fall back to the ROOT_OCCURRENCE_PATH scope. Reuse the conditionScopeKey / isRequirementActive logic.
3) Add Vitest tests: a repeatable group where the rule is TRUE in one occurrence and FALSE or UNKNOWN in another shows the notice only for the inactive occurrence; hiddenClearConfirmations returns entries for UNKNOWN and root-fallback scopes.
4) Review the help article on conditions in informationRequestsSection/articles so it says the notice appears per entry, and run `npx tsc --noEmit`.

### GA-103: Help docs do not cover adding, removing, or reordering repeated entries

- Severity: low. Verification: confirmed. Fix size: S. Audit key: `G057-P5-T9`.
- Plan reference: P5-T9. Plan basis: Plan line 2181-2185 (P5-T9 complete: workspace renders occurrences with add, remove, and reorder controls); Help Documentation Requirements lines 4592-4604 and line 121 (for every user-visible feature change, update affected help); AGENTS.md required help-doc steps. Nothing in the plan supersedes or defers help coverage for this respondent UI.

Current state:

The respondent structured-response workspace lets a respondent add group entries with the top-level toolbar button and the nested per-entry "Add <group>" buttons, move an entry up or down, and remove an entry. On the backend a removed entry is soft-removed: removedAt and removedBy are set on the entry and its descendants. None of the help articles describe these respondent controls. The "Answering" list in informationRequestSubmissionArticle.tsx covers sections, answer values and alternatives, notes, autosave, conflicts and carry-forward, but says nothing about repeated entries. The only other mention is the "Submitting in parts" line 76, which says "repeated items" freeze after submission. The Templates article (lines 40-41) covers groups and their min/max only from the Template author's side. So the help docs never explain adding, reordering or removing entries, how the authored minimum and maximum limit the respondent, or that removing an entry also removes its nested entries and answers while the removal is still recorded.

Evidence:

web-app/src/app/information-requests/structured-response-workspace/StructuredResponseToolbar.tsx:62 (Add button); StructuredResponseOccurrence.tsx:66-105 (Move occurrence up/down, Remove occurrence, nested Add buttons); service/informationrequest/InformationRequestGroupOccurrenceService.kt:175,229-238 (soft removal with removedAt/removedBy across descendants); web-app/src/app/components/help-docs/sections/articles/informationRequestSubmissionArticle.tsx:9-38 (Answering list has no entry controls), :76 (only "repeated items" mention); informationRequestTemplatesArticle.tsx:40-41 (author-side groups min/max only). A grep of help-docs/sections for occurrence/entry/reorder/repeat finds no respondent-side coverage.

Fix outline:

Edit web-app/src/app/components/help-docs/sections/articles/informationRequestSubmissionArticle.tsx. It is currently 116 lines and must stay under 150. Add one or two <li> items to the Answering list, each with its own id. Say that a repeated group shows one entry per item set. Say that "Add <group>" adds an entry, including nested entries inside an entry, up to the maximum the Template sets. Say that the up and down arrows reorder entries. Say that Remove takes out an entry together with its nested entries and answers, and that the removal stays recorded. Say that the Template's minimum number of entries must be met before submitting. Before stating how the minimum and maximum are enforced, check it in the code: add disabling or blocking and whether the minimum is a submission blocker. Optionally add a cross-reference from the Templates article. Then run npx tsc --noEmit in web-app. No backend change or migration is needed.

### GA-104: Recipient-safe Template projection keeps reviewStages, including section keys of fully denied sections

- Severity: low. Verification: confirmed. Fix size: S. Audit key: `G067-P5-R22`.
- Plan reference: P5-R22. Plan basis: Plan lines 2343-2347 (P5-R22, marked [x]): "Filter denied Requirement prompts, identifiers, policies, relationships, conditions/literals, and occurrence metadata on authenticated and no-auth reads." A plan search on reviewStage/review stage combined with recipient/safe/disclose/projection found no later text that exempts review stage configuration from recipient-safe projection or defers it.

Current state:

InformationRequestResponseWorkspaceService.recipientSafeTemplateVersion filters sections, requirements (and their cross-references), groups and conditionRules down to what the caller may view, but it builds its result with templateVersion.copy(sections, groups, conditionRules) and never touches reviewStages. So every InformationRequestTemplateReviewStageDto goes out unchanged in the response workspace. That covers the authenticated route (InformationRequestResource) and the no-auth route (InformationRequestNoAuthRequestResource), which both use this service. Each DTO carries stageKey, title, aggregation, quorumCount, minimumReviewerCount, tieResolution, overridePermitted, excludesResponseParties, excludesPriorReviewers and sectionKeys. The sectionKeys still list sections that were dropped because none of their requirements were disclosed. As a result, respondents can see the identifiers of fully denied sections and the internal review policy. The response workspace frontend does not read reviewStages; only the Template authoring panels do. Dropping or narrowing the field therefore has no UI cost.

Evidence:

src/main/kotlin/com/docuhyphen/app/api/service/informationrequest/InformationRequestResponseWorkspaceService.kt:184-230: sections are filtered to disclosed bindings (208-219) and groups/conditionRules are filtered, but the return at 225-229 copies only sections, groups and conditionRules, so reviewStages passes through. Line 71 builds safeTemplateVersion and line 94 puts it into the workspace DTO. src/main/kotlin/com/docuhyphen/app/api/model/dto/InformationRequestTemplateDtos.kt:79 (reviewStages on the version DTO) and 173-186 (stage DTO with sectionKeys, the quorum/exclusion policy fields and the other review policy fields). Callers: resource/informationrequest/InformationRequestResource.kt and InformationRequestNoAuthRequestResource.kt. A grep of web-app/src/app/information-requests and web-app/src/services shows reviewStages used only in template-document authoring components, never in the response workspace.

Fix outline:

In InformationRequestResponseWorkspaceService.recipientSafeTemplateVersion, also set reviewStages in the returned copy. The preferred fix is reviewStages = emptyList(): respondents have no use for review routing or quorum policy, and the workspace UI does not read it. The alternative keeps only stages whose sectionKeys intersect the safe section keys, filters sectionKeys to those keys, and hides the policy fields. Consider clearing reviewStageOrdering the same way. No migration is needed. Tests: extend the existing mixed-party / NOT_DISCLOSED JSON-disclosure regression for the response workspace, for both the authenticated and the no-auth read, with a Template that has a review stage covering a fully denied section. Assert that the serialized workspace JSON contains neither that section key nor the stage key/title. Run the backend information request test suite. No help-docs change is needed, because there is no user-visible behaviour change for respondents.

### GA-105: Frontend still has path-parsing and Template-id fallbacks for runtime identity

- Severity: low. Verification: partial. Fix size: S. Audit key: `G068-P5-R15`.
- Plan reference: P5-R15 / P5-R09. Plan basis: Plan lines 2283-2286 (P5-R09): explicit runtime Requirement identity; remove guessing. Plan lines 2309-2314 (P5-R15): expose immutable group, occurrence and parent identifiers; remove outermost-path parsing as identity. Both are marked [x]. The main path meets them, and the leftover fallbacks contradict the "remove" wording. The Development-Stage Constraint (lines 168-265) forbids compatibility or legacy fallback code, which supports removing them. No later waiver or deferral covers these [truncated]

Current state:

P5-R09 and P5-R15 are implemented on the main path. The frontend resolves an occurrence's group through the immutable sourceTemplateGroupId. It groups siblings by sourceTemplateGroupId plus parentOccurrenceId. It also names the runtime Requirement through response.informationRequestRequirementId. Two defensive fallbacks remain in structuredResponseWorkspaceState.ts:
(1) If no group's id matches occurrence.sourceTemplateGroupId, occurrenceGroupKeyFromTemplate falls back to occurrenceGroupKey(occurrencePath). That function takes the group key from the path by removing everything from the first "[N]". For a nested path, that gives the outermost group key, which is exactly the path parsing the plan says to remove.
(2) If no response DTO matches, buildResponsePatches sends `requirementId: response?.informationRequestRequirementId ?? requirement.id`. Here requirement.id is the Template binding id (responseForFieldRequirement compares it with response.sourceTemplateBindingId), not a runtime Requirement id.
Both branches are normally unreachable because the server projects the groups and responses. If they are reached, they guess an identity instead of failing closed. The server rejects a Requirement id it does not know, so this is a low-severity leftover of the forbidden guessing, not a missing feature.

Evidence:

web-app/src/app/information-requests/structured-response-workspace/structuredResponseWorkspaceState.ts:40-43 (occurrenceGroupKey strips the path with the regex /\[[0-9]+].*$/). Lines 45-52: occurrenceGroupKeyFromTemplate uses sourceTemplateGroupId first, then falls back to occurrenceGroupKey(occurrencePath). Line 52 is the only caller of occurrenceGroupKey in web-app/src. Lines 70-77: siblingOccurrenceIds uses sourceTemplateGroupId and parentOccurrenceId, which is correct. Lines 98-108: responseForFieldRequirement matches sourceTemplateBindingId === requirement.id, then sourceTemplateRequirementId, so requirement.id is the binding id. Line 214: `requirementId: response?.informationRequestRequirementId ?? requirement.id`. Line 212 already drops non-FIELD Requirements that have no response, so the fallback applies only to FIELD Requirements without a response DTO. web-app/src/app/models/models.tsx:2475-2476: sourceTemplateGroupId and parentOccurrenceId are on the occurrence DTO.

Fix outline:

Frontend only; no migration needed.
In web-app/src/app/information-requests/structured-response-workspace/structuredResponseWorkspaceState.ts:
(a) Delete occurrenceGroupKey. Make occurrenceGroupKeyFromTemplate return the matched group's groupKey, or null or undefined when there is no match. Callers (occurrencePresentation, buildResponsePatches) should then skip that occurrence, or show an error state, instead of parsing the path.
(b) In buildResponsePatches, return null (or collect an error) when no response DTO carries informationRequestRequirementId, instead of sending requirement.id. Remove the `?? requirement.id` fallback. If a FIELD Requirement can legitimately have no response yet, have the server project a runtime Requirement id on the Requirement DTO and use that instead.
Add Vitest cases in the co-located state test file:
- a nested occurrence whose sourceTemplateGroupId has no matching group produces no patches and no parent group key;
- a FIELD edit with no response DTO produces no patch with a binding id;
- the existing nested save test still sends the child's runtime Requirement id.
No help-doc change is needed because there is no user-visible behavior change.

### GA-106: A hidden REQUIRED requirement that carries a condition rule is counted INCOMPLETE

- Severity: low. Verification: confirmed. Fix size: S. Audit key: `G069-P5-R17`.
- Plan reference: P5-R17. Plan basis: Plan lines 2318-2321 (P5-R17: compute structured completeness from the collected canonical value and the allowed exception semantics). This must be consistent with P5-R16 (lines 2314-2317) on hidden-response activation. I found nothing in the plan that allows a rule on a non-CONDITIONAL binding or that defers this. The Development-Stage Constraint forbids compatibility shims, so the fix should normalize the data rather than tolerate both forms.

Current state:

The code uses two different tests to decide whether a requirement is hidden. Visibility checks only whether a conditionalRuleKey is present, but completeness checks the requiredness value, and the Template layer lets a REQUIRED (or OPTIONAL) binding keep a conditionalRuleKey. The backend validator requires a rule when requiredness is CONDITIONAL, but it never refuses or strips a rule when requiredness is REQUIRED or OPTIONAL. The frontend validator does the same. The Template editor only hides the Condition select when requiredness leaves CONDITIONAL; it never clears draft.conditionalRuleKey. As a result, a REQUIRED binding can be persisted with a rule. When that rule is not TRUE: (1) the backend workspace (InformationRequestActiveResponseProjection.isActive, used by ResponseWorkspaceService and ResponseDraftService) and the frontend (isRequirementActive) hide the requirement, because both look only at whether ruleKey is present; (2) the structured, evidence and attestation completeness evaluators mark it HIDDEN only when requiredness == CONDITIONAL, so they count it INCOMPLETE. The respondent cannot see or answer the item, so completeness never reaches 100% and submission readiness stays blocked. The validator's cycle detection also skips rules attached to non-CONDITIONAL requirements, so dependency cycles through such rules go unchecked. For an OPTIONAL binding carrying a rule, completeness shows OPTIONAL_UNANSWERED while the item is hidden, which does no harm to the percentage.

Evidence:

src/main/kotlin/com/docuhyphen/app/api/service/informationrequest/InformationRequestCompletenessProgressService.kt:125-139 (HIDDEN only when requiredness == CONDITIONAL and the condition is not TRUE; otherwise INCOMPLETE). InformationRequestEvidenceCompletenessEvaluator.kt:34 and InformationRequestAttestationCompletenessEvaluator.kt:37 have the same CONDITIONAL gate. InformationRequestActiveResponseProjection.kt:8-15: isActive returns true only when ruleKey is null or its evaluation is TRUE, and never looks at requiredness. It is used at InformationRequestResponseWorkspaceService.kt:82 and InformationRequestResponseDraftService.kt:801,818. InformationRequestTemplateConfigurationValidator.kt:408-416 only requires a rule for CONDITIONAL; normalizeRequirement returns requirement.copy(...) and never clears conditionalRuleKey. Validator line 242: cycle detection filters to CONDITIONAL only. [truncated]

Fix outline:

Make "has a condition" mean exactly "requiredness == CONDITIONAL" everywhere.
1. In InformationRequestTemplateConfigurationValidator.normalizeRequirement, either refuse a conditionalRuleKey when requiredness != CONDITIONAL (a new clear refusal message) or normalize it to null in the returned copy. Refusing is preferred for explicit contracts.
2. In InformationRequestActiveResponseProjection.isActive (and its callers), and in the frontend isRequirementActive, gate on CONDITIONAL requiredness, or rely on the normalized data. Optionally, factor one shared "conditionStateFor(binding, occurrence)" helper that both the completeness evaluators and the workspace use.
3. In the frontend, make RequirementAnsweringFields clear conditionalRuleKey when requiredness changes away from CONDITIONAL (onChange({requiredness, conditionalRuleKey: requiredness === CONDITIONAL ? draft.conditionalRuleKey : undefined})). Make templateDraftValidation report a rule on a non-conditional requirement.
4. If existing dev data can hold such bindings, add migration V151 to null information_request template requirement binding conditional_rule_key where requiredness <> 'CONDITIONAL', plus an optional CHECK constraint. This is allowed under the no-backwards-compat rule.
5. Tests: a validator test refusing REQUIRED+rule; a completeness test showing a CONDITIONAL item hidden versus the refused REQUIRED case; the editor clearing the rule on a requiredness change (vitest); and a frontend validation test.

### GA-107: Access link expiry and use limit are not validated

- Severity: low. Verification: partial. Fix size: S. Audit key: `G079-P4-T4`.
- Plan reference: P4-T4. Plan basis: Plan line 1843 ("Enforce or reject every configured ... expiry, and usage constraint"), which is met at use time. P5-R12 (around line 2294) requires atomic enforcement of maxUses, which is done. Line 4018 (P12 decision 10) requires only default expiry and use limit "when the author gives none", so an upper cap is not required. Nothing supersedes validating the values at issue time, but no plan line explicitly requires it either.

Current state:

Expiry and use limits on bootstrap access links are enforced when a link is used. InformationRequestContactProofService refuses an expired link with ACCESS_LINK_EXPIRED and a used-up link with ACCESS_LINK_EXHAUSTED, and rotate/replace call requireActive. So the plan's "enforce or reject" wording is met by enforcement. What is missing is checking the values when a link is issued or replaced. IssueInformationRequestAccessLinkRequest and ReplaceInformationRequestAccessLinkRequest pass expiresAt and maxUses through the resource without checks. issueMutation and replaceMutation store them as given, falling back to the abuse-limit defaults (30 days, 25 uses) only when a value is null. The result: an expiresAt in the past, or a maxUses of 0 or less, is accepted and creates a link that is dead from the start, with a 2xx response, when it should get a 400. share_link.max_uses (V1 baseline) has no CHECK constraint. There is also no upper bound tied to abuse limits, but plan item 10 only requires defaults "when the author gives none", so the missing cap is not a plan requirement. The frontend never sends custom values, so today only direct API callers are affected.

Evidence:

src/main/kotlin/com/docuhyphen/app/api/resource/model/InformationRequestAccessLinkRequests.kt:10-20 (bare nullable expiresAt/maxUses, no validation); resource/informationrequest/InformationRequestAccessLinkResource.kt:75-76,144-145 (passed through unchanged); service/informationrequest/InformationRequestBootstrapShareLinkService.kt:164-165 and 287-288 (`command.expiresAt ?: defaultExpiry()`, `command.maxUses ?: abuseLimits.accessLinkUses`, no range checks); InformationRequestBootstrapShareLinkService.kt:370-389 requireActive (checks expiry at rotate/replace); service/informationrequest/InformationRequestContactProofService.kt:151-167 (use-time ACCESS_LINK_EXPIRED / ACCESS_LINK_EXHAUSTED enforcement); model/informationrequest/InformationRequestAbuseModels.kt:24-25 (defaults 30 days / 25 uses); db/migration/V1__baseline.sql:731 (`max_uses integer`, no CHECK); web-app/src/app/models/models.tsx:4283-4303 (fields exist in the frontend types but no component sets them).

Fix outline:

In InformationRequestBootstrapShareLinkService, add a private validateLinkLimits(expiresAt, maxUses), called from issueMutation and replaceMutation before the ShareLink is built. It should reject an expiresAt that is not after now, and a maxUses below 1. Throw InformationRequestLifecycleException with a new stable code, for example ACCESS_LINK_LIMITS_INVALID in InformationRequestErrorCatalog, mapped to 400. Optionally, if the user wants it, cap values at the abuseLimits ceilings. Add migration V151__share_link_max_uses_positive.sql with `ALTER TABLE share_link ADD CONSTRAINT share_link_max_uses_positive CHECK (max_uses IS NULL OR max_uses > 0)`. Check first that no existing rows break it; there is no backfill because of the development-stage constraint. Tests: in the bootstrap share link service test, issue and replace with a past expiresAt, with maxUses 0 and -1, and with valid values, asserting the error code and that no ShareLink or receipt is persisted; plus a resource test asserting the 400. No help docs change is needed unless the author-facing UI later exposes these fields.

### GA-108: Several named Phase 4 tests are missing

- Severity: low. Verification: partial. Fix size: M. Audit key: `G080-P4`.
- Plan reference: P4 Tests to write first. Plan basis: Plan lines 2006-2030 list these tests under Phase 4 "Tests to write first". Line 4189 of the no-auth coverage table repeats the Exchange-token and Exchange-OTP rejection requirement. Nothing in the Status section (lines 1-80), the Development-Stage Constraint or the non-goals supersedes them.

Current state:

Only some of the named Phase 4 tests exist. The production behavior is all there: InformationRequestBootstrapShareLinkService calls CommandReceiptService with request fingerprints for issue, rotate and replace, and checks exchange.requireRecipientSignIn at line 136. What is missing is test coverage.

1. Exchange-token rejection is only partly covered. Exchange direct-grant ShareLinks are refused when issuing a challenge (InformationRequestContactProofServiceTest:64) and when rotating (InformationRequestBootstrapShareLinkServiceTest:237), and a forwarded bootstrap token is refused without a session secret. The claim says nothing was found, which overstates it. Still, no test sends an Exchange recipient OTP, or an Exchange no-auth token, to the /no-auth/information-requests content or read endpoints and checks it is rejected.

2. requireRecipientSignIn is only tested for refusal of issuance (BootstrapShareLinkServiceTest:88). No test shows that the authenticated surface still serves that respondent. No test shows that a request-level setting cannot override the flag.

3. For bootstrap issue, rotate, replace and upgrade, only same-key replay is tested (lines 170, 276, 321 and UpgradeServiceTest:238), along with ETag, meaning the expected-revision check. None of these tests covers a fingerprint conflict: the same idempotency key sent with a different payload. None covers a parallel race. The only party concurrency test covers reassignment.

4. No test covers trial-extension non-drift, or non-drift after a later plan change on an issued grant. [truncated]

Evidence:

- Required test list: plan lines 2006-2024. Line 2008 covers Exchange-token rejection. Lines 2013-2015 cover requireRecipientSignIn and the authenticated surface. Lines 2018-2019 cover fingerprint conflict and parallel-race tests. Line 2022 covers trial-extension non-drift. Line 2030 covers later-plan-change non-drift.
- src/test/kotlin/com/docuhyphen/app/api/service/informationrequest/InformationRequestBootstrapShareLinkServiceTest.kt:88 is the only sign-in test. The replay tests are at lines 170, 276 and 321. The direct-grant rotation refusal is at line 237.
- InformationRequestParticipantAccountUpgradeServiceTest.kt:238 covers replay only. Grepping it, the bootstrap test and InformationRequestAccessLinkResourceContractTest for "fingerprint" or "conflict" finds nothing.
- InformationRequestContactProofServiceTest.kt:64 refuses a direct-grant ShareLink when issuing a challenge.
- InformationRequestNoAuthRequestResourceContractTest.kt:111 checks that a forwarded bootstrap token is [truncated]

Fix outline:

These are test-only changes. No migration is needed, so V151 stays free.

1. InformationRequestNoAuthRequestResourceContractTest and InformationRequestNoAuthReadAccessServiceTest: add cases that send an Exchange direct-grant ShareLink token, or an Exchange recipient OTP credential, to the IR no-auth detail, parties and patch endpoints. Assert each one is refused before any content service is called.

2. InformationRequestBootstrapShareLinkServiceTest, or an authenticated resource test: add a case where the Exchange has requireRecipientSignIn=true, a respondent holding a User Share reads the request and saves responses through the authenticated services, and bootstrap issuance still fails. Add a second case showing no request-level setting can change the outcome.

3. Bootstrap and upgrade services: for issue, rotate, replace and upgrade, add a test that reuses an idempotency key with a different payload and expects the CommandReceiptService conflict error. Add a QuarkusTest transaction test, modelled on InformationRequestContactProofTransactionTest, in which parallel issue or rotate commands with one key produce exactly one ShareLink, and parallel upgrades produce exactly one ParticipantAccountLink.

4. InformationRequestTrialContinuationTest, or a new transaction test: issue a grant during a trial, extend the trial (move current_period_end later), change the plan tier, re-read the persisted request_execution_grant, and assert that trialExpiresAt, mutationAllowanceExpiresAt, the caps and the frozen plan are unchanged.

### GA-109: Request detail projection shows owner and internal identifiers to every party

- Severity: low. Verification: confirmed. Fix size: S. Audit key: `G081-P4-T6`.
- Plan reference: P4-T6. Plan basis: Plan 1876-1877 (P4-T6: apply recipient-safe projection to every read and mutation response; the same audience rule must govern identifier disclosure). 1877-1889 record only P4-T6a (parties) as complete and defer P4-T6b (occurrence/response). 2042 (both API surfaces enforce identical recipient-safe projections). 2343-2347 (P5-R22 completes the response/occurrence portion of P4-T6). Nothing in the Status section, Development-Stage Constraint, non-goals (304-346) or later design decisions waives [truncated]

Current state:

The request header DTO has no audience projection. InformationRequestDtoMapper.toDto always fills id, exchangeId, templateVersionId, ownerType, ownerOrganizationId, ownerUserId and supersededByRequestId. Only conditionEvaluations are filtered, and only on the workspace path. This one mapper backs several endpoints: authenticated GET /information-requests/{id}, the no-auth GET /no-auth/information-requests/{id} (both go through InformationRequestResponseWorkspaceService.loadRequest), the `request` block inside both response-workspace reads (load), the lifecycle mutation responses in InformationRequestResource (issuance/cancellation/supersession), the completion gate, and the lineage successor mapper. Every caller who passes INFORMATION_REQUEST_VIEW gets the same fields, including recipients and no-auth participants. That covers the owner's internal user or organization id and the template version id. The party projection takes the opposite approach: InformationRequestPartyQueryService/InformationRequestPartyDtoMapper hide principalId, principalKind, subjectIdentityRefId and exchangeRecipientId from anyone who lacks INFORMATION_REQUEST_MANAGE_PARTIES or is not viewing their own row. So P4-T6's rule that one audience rule governs identifier disclosure does not hold for the request header. P4-T6a (parties) and P5-R22 (workspace configuration, occurrences, responses) closed the other parts of P4-T6. No later plan text narrows it for the header. Severity stays low. exchangeId is largely known to Exchange participants anyway. The real leak is the owner principal ids and [truncated]

Evidence:

src/main/kotlin/com/docuhyphen/app/api/model/InformationRequestDtoMapper.kt:11-34 (unconditional mapping of exchangeId, templateVersionId, ownerType, ownerOrganizationId, ownerUserId). src/main/kotlin/com/docuhyphen/app/api/service/informationrequest/InformationRequestResponseWorkspaceService.kt:39-50 (loadRequest returns toDto with no audience check) and :92 (the workspace `request` block uses the same mapper). src/main/kotlin/com/docuhyphen/app/api/resource/informationrequest/InformationRequestNoAuthRequestResource.kt:54,65-66 (no-auth GET) and :115-117 (no-auth response-workspace). src/main/kotlin/com/docuhyphen/app/api/resource/informationrequest/InformationRequestResource.kt:60-62, 248, 253 (authenticated GET and mutation responses). InformationRequestCompletionGateResource.kt:51 and InformationRequestLineageDtoMapper.kt:53 also use the unprojected mapper. For contrast, the plan's P4-T6 text describes the party projection as manager-or-self only. Frontend: [truncated]

Fix outline:

1. Add a projection step to InformationRequestDtoMapper, for example toDto(request, evaluations, audience: InformationRequestHeaderAudience). Also add a small service helper, such as InformationRequestHeaderProjectionService in service/informationrequest. It resolves the audience from RequestAccessContext through AuthorizationService, using the same test as InformationRequestPartyQueryService: callers with INFORMATION_REQUEST_MANAGE_PARTIES (or an owner-side manage action), or the owning user or organization, get the full view. Everyone else gets ownerUserId, ownerOrganizationId and templateVersionId nulled. ownerType, state, dates, etag and conditionEvaluations stay. Keep exchangeId, or null it for no-auth callers if the product prefers. 2. Route every caller through the helper: InformationRequestResponseWorkspaceService.loadRequest/load, the InformationRequestResource issuance/cancellation/supersession responses, InformationRequestCompletionGateResource, and InformationRequestLineageDtoMapper (pass the audience in rather than calling the service from the mapper). 3. Make the DTO fields nullable in model/dto and in web-app models.tsx InformationRequestDto if they are not already. mayCorrectAsOwner then returns false for null ids. 4. Tests: service tests showing a recipient, a no-auth participant and an owner-side manager each get the correct header fields from GET, from both response-workspaces and from a mutation response. Add a regression test that the authenticated and no-auth surfaces return identical projections for the same audience. No migration is needed. 5. Help [truncated]

### GA-110: Request ETags for aggregate, party and response revisions share one namespace and can satisfy each other

- Severity: low. Verification: confirmed. Fix size: S. Audit key: `G093-P3-T5`.
- Plan reference: P3-T5. Plan basis: Plan lines 1488-1493 (P3-T5): "Derive a strong response ETag from the persisted aggregate or party revision, never from timestamps or serialized response order". Lines 3537-3541 describe a separate "parties ETag" used for party assignment and returned by GET .../parties, which means it is a validator distinct from the request ETag. Nothing in the Status section, the Development-Stage Constraint, the non-goals (304-346) or later design decisions narrows or defers this. Under the [truncated]

Current state:

The request-level aggregate, parties and responses validators are all built by RevisionETag.of(request.id, counter), which produces "\"<requestId>:<n>\"". Nothing marks which revision family an ETag belongs to. Each command family checks If-Match with plain set membership (CommandPrecondition.ExpectedRevision), so an ETag from one family passes another family's check whenever the two counters are equal. aggregate_revision and party_revision both default to 1 (V96), and response_revision also defaults to 1 (V110), so on a new request any of the three ETags satisfies all three families. Examples: party commands (InformationRequestPartyService, DelegatedAuthorityService) accept the aggregate requestETag, and response and submission commands accept the aggregate or parties ETag. The validators are strong and based on persisted revisions, as the plan asks, but they do not identify their own representation. Impact is low: a client has to send the wrong family's ETag, and the counters have to match.

Evidence:

InformationRequestETag.kt:17-24: aggregateOf, partiesOf and responsesOf all return RevisionETag.of(request.id, ...). CommandPrecondition.kt:66: RevisionETag.of = "\"$resourceId:$revision\"", with no family prefix. CommandPrecondition.kt:20-28: ExpectedRevision is checked only with `currentETag !in etags`. V96 lines 18-19: aggregate_revision and party_revision both BIGINT NOT NULL DEFAULT 1. V110:2: response_revision DEFAULT 1. Party preconditions: InformationRequestPartyService.kt:258, 361, 435, 482, 555 and InformationRequestDelegatedAuthorityService.kt:129, 189 use partiesOf. Aggregate preconditions: AmendmentService:96, LifecycleService:203, SuccessorService:91, FollowUpService:209, CompletionGateService:46 use aggregateOf. Response preconditions: ResponseDraftService:165, SubmissionService:302, GroupOccurrenceService:325 use responsesOf. The aggregate ETag is exposed as requestETag (InformationRequestDtoMapper.kt:32) next to partiesETag and responseETag. No main code calls [truncated]

Fix outline:

In InformationRequestETag.kt, give each request-level family its own tag, for example "\"request:<id>:<n>\"", "\"parties:<id>:<n>\"" and "\"responses:<id>:<n>\"". Either add a family parameter to a shared helper in service/command (for example RevisionETag.of(family, id, revision)) or build the strings locally the way submissionOf already does. Optionally apply the same treatment to partyOf, requirementOf, evidenceOf, artifactOf and clockOf, which use row ids and so are already mostly separate; note that evidenceOf keys on the requirement id and could collide with requirementOf. No migration is needed, and V151 stays free. Tests: add an InformationRequestETagTest asserting that aggregateOf, partiesOf and responsesOf differ even when the counters are equal (the default of 1). Add service tests showing that a party command with the aggregate ETag in If-Match, and a response-draft command with the parties ETag, both fail with COMMAND_PRECONDITION_STALE (412). Update any backend or conformance tests that hardcode "\"<id>:<n>\"" strings for these families. The frontend needs no change because it treats ETags as opaque.

### GA-111: share_resource_type_check not widened for the Requirement-occurrence type

- Severity: low. Verification: confirmed. Fix size: S. Audit key: `G095-P3-T1c`.
- Plan reference: P3-T1c. Plan basis: Requirement in force at plan lines 1346-1349 (P3-T1), 560-563 (design), 373 (baseline table), 1389-1393 (P3-T1c subtask), 1692-1694 (tests), 4178 and 4190 (verification matrix) and 4540-4542 (latest guidance). Each says share_resource_type_check must admit both the request aggregate and the Requirement-occurrence types. The only nearby text is lines 570-572 and 1545-1549, which say Requirement occurrences inherit grants from the parent request through parent-grant inheritance. That explains why [truncated]

Current state:

V92 widens share_resource_type_check only to EXCHANGE, DOCUMENT, PRINCIPAL_GROUP and INFORMATION_REQUEST. INFORMATION_REQUEST_REQUIREMENT is left out, even though the plan requires the check to admit it. No later migration (V93-V150) touches the check. The code agrees with the migration: RoleCapabilities.isShareRoleValid returns false for any other type, including INFORMATION_REQUEST_REQUIREMENT. ShareResourceScopedRoleContractTest treats only four types as Share-bearing and asserts that every other ResourceType, including the Requirement type, is refused by share_resource_type_check. share_role_name_check defines no role keys for a Requirement-occurrence Share. At runtime, Requirement authorization goes through owner-matched parent-grant inheritance from the aggregate INFORMATION_REQUEST Share plus the central resource-policy evaluator. No Requirement-level Share is ever materialized, so nothing fails today. This is a literal departure from the plan text, not a functional defect.

Evidence:

src/main/resources/db/migration/V1__baseline.sql:716 (original check). src/main/resources/db/migration/V92__share_resource_scoped_role_key.sql:9-10 (widened check admits only EXCHANGE, DOCUMENT, PRINCIPAL_GROUP, INFORMATION_REQUEST) and :11-20 (role_name check has no Requirement branch). A grep across db/migration finds no other migration that alters share_resource_type_check. src/main/kotlin/com/docuhyphen/app/api/service/auth/authz/RoleCapabilities.kt:315-323 (isShareRoleValid: else -> false). src/test/kotlin/com/docuhyphen/app/api/migration/ShareResourceScopedRoleContractTest.kt:21-27 (shareBearingTypes is the four types) and :62-80 (all other ResourceType entries are refused by share_resource_type_check). src/main/kotlin/com/docuhyphen/app/api/model/entity/ResourceType.kt:37 (INFORMATION_REQUEST_REQUIREMENT exists). ResourceAuthorizationContextRegistry.kt:119 maps it to its own ResourceKind for inheritance-based authorization.

Fix outline:

Option A: follow the plan literally. Add V151__share_requirement_resource_type.sql, which drops and recreates share_resource_type_check to include 'INFORMATION_REQUEST_REQUIREMENT' and extends share_role_name_check with a Requirement branch. The branch could reuse the request role keys, or be a deny-all branch that admits no role keys, so the type is admitted but no Share can actually be created. Then extend RoleCapabilities.isShareRoleValid to match, add INFORMATION_REQUEST_REQUIREMENT to shareBearingTypes in ShareResourceScopedRoleContractTest, and add an acceptance/refusal test for the chosen role policy. Option B, which fits the implemented design better: keep the schema and amend the plan lines listed above so they say only the aggregate type is Share-bearing and Requirement occurrences are authorized only through parent-grant inheritance. The existing refusal test would then be the regression guard. Either way, no frontend or help-doc change is needed.

Decision needed. Recommended default: No per-occurrence Shares. Keep inheritance-only authorization and correct the plan text.

### GA-112: No characterization of Document and Principal Group Share capability derivation, whose behavior changed

- Severity: low. Verification: confirmed. Fix size: S. Audit key: `G096-P3-T1c`.
- Plan reference: P3-T1c. Plan basis: Plan lines where the requirement is in force:
- 1349-1353: add characterization and migration coverage for Exchange, Document and Principal Group before changing capability derivation.
- 1389-1394 (P3-T1c): characterize the three Share-bearing ResourceTypes.
- 1688-1691: Share role migration and capability characterization for the three types, plus unchanged Exchange capability tests.
- 1759: every pre-existing Share-bearing resource keeps its characterized capability behavior.
- 373 and [truncated]

Current state:

The plan says capability behavior for Document and Principal Group Shares must be pinned by characterization tests before derivation changes, and must be kept afterwards. Neither happened.

Before this program, DefaultAuthorizationService.toGrant derived capabilities for every Share with RoleCapabilities.forExchangeShareRole(roleName). Git 663031c4 shows this at DefaultAuthorizationService.kt:590-592. Today toGrant calls RoleCapabilities.forShareRole(resourceType, roleName). That function only maps EXCHANGE and INFORMATION_REQUEST, and its `else -> emptySet()` branch applies to DOCUMENT and PRINCIPAL_GROUP. So a Document or Principal Group Share with a valid role key now grants no capabilities. RoleCapabilities.isShareRoleValid still accepts Exchange role names for DOCUMENT and PRINCIPAL_GROUP, so those Shares can still be inserted and validated, but they grant nothing.

No test calls forShareRole or checks the capabilities a DOCUMENT or PRINCIPAL_GROUP Share resolves to:
- ShareRoleCapabilityRegistryTest only covers INFORMATION_REQUEST Shares.
- ParentGrantInheritanceTest mentions in its KDoc that a Document Share role maps to no capability, but it always places the Share on an Exchange and never asserts that behavior.
- ShareResourceScopedRoleContractTest only checks that the database accepts these resource types.
- ShareServiceAuditTest covers PRINCIPAL_GROUP only for audit owner, not capabilities.

So the change was made silently, and it contradicts the plan's requirement to keep this behavior.

Evidence:

- src/main/kotlin/com/docuhyphen/app/api/service/auth/authz/RoleCapabilities.kt:303-313: forShareRole maps only EXCHANGE and INFORMATION_REQUEST; everything else returns emptySet.
- RoleCapabilities.kt:315-322: isShareRoleValid accepts Exchange role names for DOCUMENT and PRINCIPAL_GROUP.
- src/main/kotlin/com/docuhyphen/app/api/service/auth/authz/DefaultAuthorizationService.kt:759-770: toGrant uses forShareRole.
- git show 663031c4, DefaultAuthorizationService.kt:590-592: the old toGrant used `RoleCapabilities.forExchangeShareRole(this.roleName)` for every Share.
- src/test/kotlin/com/docuhyphen/app/api/service/auth/authz/ShareRoleCapabilityRegistryTest.kt: INFORMATION_REQUEST only.
- src/test/kotlin/com/docuhyphen/app/api/service/auth/authz/ParentGrantInheritanceTest.kt:25-34 (KDoc) and :177-215 (fixture): the Share is always on an Exchange or grandparent Exchange; there is no direct Document Share assertion.
- [truncated]

Fix outline:

1. In RoleCapabilities.forShareRole, add a `ResourceType.DOCUMENT, ResourceType.PRINCIPAL_GROUP ->` branch that uses parseExchangeShareRole and EXCHANGE_SHARE. This restores the pre-program behavior the plan says to keep, and matches isShareRoleValid.
2. Add tests in src/test/kotlin/com/docuhyphen/app/api/service/auth/authz/ShareRoleCapabilityRegistryTest.kt, or a new ShareBearingResourceCapabilityCharacterizationTest.kt:
   - For every ExchangeShareRoleName, check that forShareRole gives the same result as forExchangeShareRole for EXCHANGE, DOCUMENT and PRINCIPAL_GROUP.
   - Check that DefaultAuthorizationService.capabilities for a direct DOCUMENT Share (ResourceRef.document) and a direct PRINCIPAL_GROUP Share (ResourceRef.group) resolves to the expected set, using the same mocked-repository builder as the existing test.
   - Check that an unknown role key, and an Exchange role on INFORMATION_REQUEST, both give an empty set.
   - Check that the other nine ResourceTypes return an empty set.
3. Update the ParentGrantInheritanceTest KDoc if the Document mapping changes. Its fixture shares on the Exchange, so its assertions are not affected.

No migration is needed. This is a small change (fix size S).

### GA-113: Missing Fields coexistence and legacy assignment isolation tests

- Severity: low. Verification: partial. Fix size: S. Audit key: `G097-P3-T6`.
- Plan reference: P3-T6. Plan basis: - The requirement is in force at plan lines 1737 ("Two Information Requests using the same stable Field without collision") and 1742 ("Legacy EXCHANGE assignment isolation tests"), within the P3 tests-to-write-first list, with P3-T6 at line 1494.
- The legacy-isolation half is closed by P12-T2 at lines 4060-4067, which cites ExchangeMetadataSeparationTest. Line 4553 ("Leave all current EXCHANGE assignments and values attached to their Exchanges") also bears on it.
- Nothing in the plan [truncated]

Current state:

Only half of the claim holds. Legacy EXCHANGE assignment isolation is tested. ExchangeMetadataSeparationTest (added under P12-T2) seeds an EXCHANGE schema_assignment and answer for the same stable Field a request collects. It then answers, attests and submits the request. The Exchange assignment, value, set revision and history stay unchanged, no submission item points at an Exchange revision, and the package points only at the request's own INFORMATION_REQUEST assignment. Exact Schema Version assignment also has coverage: SchemaAssignmentExactVersionTest runs with a newer published version present and checks that findLatestPublished is never called. The real gap: no test puts two Information Requests on one stable Field Definition (for example two requests on one Exchange from the same Template Version), answers each, and proves the schema assignments, root Field Value Sets, values and packages stay separate. RecurringSupplementalRequestConformanceTest does create successor requests from the same Template, but it never writes the shared Field in both requests or checks for a collision. InformationRequestVolumeTest creates many requests on one Exchange with no Fields. The ux_assignment_resource UNIQUE (resource_type, resource_id) constraint (V36) keeps assignments apart at the database level, but no test exercises that for two requests.

Evidence:

- The plan lists both tests at lines 1737 and 1742.
- src/test/kotlin/com/docuhyphen/app/api/service/informationrequest/conformance/ExchangeMetadataSeparationTest.kt:30-85 is the legacy EXCHANGE isolation test. It asserts the EXCHANGE assignment and value are unchanged, that zero submission items point at the Exchange assignment's revisions, and that the item's assignment has resource_type INFORMATION_REQUEST.
- src/test/kotlin/com/docuhyphen/app/api/service/fields/SchemaAssignmentExactVersionTest.kt:44-67 assigns the exact version while a newerVersionId exists, and verifies findLatestPublished is never called.
- InformationRequestTemplateMaterializerTest:73-483 covers a single request only, including a no-schema case and occurrence value sets.
- InformationRequestFieldResourceAdapterTest:46-144 covers a single request only.
- RecurringSupplementalRequestConformanceTest:65-190 creates a successor, but only patches a Document requirement in it.
- InformationRequestVolumeTest has no [truncated]

Fix outline:

1. Add a QuarkusTest at src/test/kotlin/com/docuhyphen/app/api/service/informationrequest/conformance/SharedStableFieldRequestCoexistenceTest.kt, using ConformanceRequestSupport and InformationRequestRuntimeTestServices with DocumentVersionStoragePostgreSQLResource.
2. In that test, materialize two Field-bearing requests on the same Exchange from the same Template Version, so both collect the same fieldDefinitionId and fieldContractId. If ConformanceRequestSupport.fieldRequest cannot reuse an existing runtime or Exchange, extend it with a variant that can.
3. Answer request A with "A value" and request B with "B value".
4. Assert:
   - Two schema_assignment rows with resource_type INFORMATION_REQUEST and different resource_id values, both pinned to the same schema_version_id.
   - Each assignment has its own ROOT field_value_set.
   - Each field_value keeps its own text after the other request writes.
   - Each field_value_revision chain belongs only to its own assignment.
   - After submitting both requests, each package's submission item points only at its own request's revision.
   - The Exchange's Field cardinality is unchanged, with no EXCHANGE assignment created.
5. Optionally, publish a newer Schema Version between creating the two requests and assert that both stay on the pinned version, which covers the later-publication non-drift case end to end.

No migration and no production code change are needed.

### GA-114: No tests proving participant-principal parties are refused by resend and acceptance-policy paths

- Severity: low. Verification: confirmed. Fix size: S. Audit key: `G098-P3-T8`.
- Plan reference: P3-T8. Plan basis: Plan lines 1530-1535 (P3 task: "prove by test that a participant-principal party cannot be routed through a User-only decision path") and 1722-1726 (test list: a participant-principal party "is refused by recordExternalEmailPrimaryDecision, resendNoAuthPrimaryRecipientInvitation, and ExternalEmailAcceptancePolicyService"). The same requirement appears at lines 392 and 688-694. No waiver, non-goal or later decision narrows it.

Current state:

The code has guards on all three User-only paths. The tests only cover one of them. Only ExchangeRecipientService.recordExternalEmailPrimaryDecision has a test showing that a PARTICIPANT-kind Share is refused, at ExchangeRecipientServiceTest:514. ExchangeAccessManagementService.resendNoAuthPrimaryRecipientInvitation refuses non-USER primary Shares (a require on principalKind == USER), but no test covers that. Its only tests are a success case and a sign-in-required refusal. ExternalEmailAcceptancePolicyService.validate calls deny() for non-USER Shares, but every case in its test file uses a USER-kind Share built by the userShare helper. None of these tests is a Share with principalKind other than USER. The gap is limited to tests. The production behaviour is already correct.

Evidence:

src/main/kotlin/com/docuhyphen/app/api/service/exchange/ExchangeAccessManagementService.kt ~line 423: require(primaryShare.principalKind == PrincipalKind.USER) { "The primary recipient does not have an email invitation" }. src/main/kotlin/com/docuhyphen/app/api/service/exchange/ExternalEmailAcceptancePolicyService.kt:26: if (recipientShare.principalKind != PrincipalKind.USER) deny(). src/test/kotlin/com/docuhyphen/app/api/service/exchange/ExchangeRecipientServiceTest.kt:513-530 has the test "participant principal cannot use the no-auth primary recipient decision path" (the only covered path). src/test/kotlin/com/docuhyphen/app/api/service/exchange/ExchangeAccessManagementServiceTest.kt:373 and :410 are the only calls to resendNoAuthPrimaryRecipientInvitation (success and sign-in-required). src/test/kotlin/com/docuhyphen/app/api/service/exchange/ExternalEmailAcceptancePolicyServiceTest.kt: all 7 tests use the userShare() helper, which hard-codes PrincipalKind.USER. A grep of src/test [truncated]

Fix outline:

Tests only. No production change and no migration are needed. (1) In ExchangeAccessManagementServiceTest.kt, add a test "participant principal primary recipient cannot be resent a no-auth invitation". Stub exchangeRecipientService.findPrimary to return an EXTERNAL_EMAIL primary recipient whose directShareId points to an ACTIVE EXCHANGE Share with principalKind = PARTICIPANT. Assert that IllegalArgumentException is thrown. Also verify that otpService.generateEmailOtp, noAuthExchangeAccessTokenService.issue, exchangeRepository.update, appUserService.getById and email dispatch are never called. (2) In ExternalEmailAcceptancePolicyServiceTest.kt, add a test "participant principal share is denied before user lookup or policy evaluation". Pass a Share with principalKind = PARTICIPANT to validate with authenticatedAppUserId both null and non-null. Assert ExchangeRecipientEligibilityException, and verify that appUserService.getById, findRegisteredByEmail and policyService.assertCanShareWithUser are never called. Optionally, parameterize over GROUP as well.

### GA-115: V97 performs the legacy backfill and owner-derivation pass the plan forbade

- Severity: low. Verification: confirmed. Fix size: S. Audit key: `G099-P3-T8`.
- Plan reference: P3-T8. Plan basis: In force:
- Plan lines 1519-1527 (P3-T8): "Treat personal ownership as a forward-only schema addition to an empty table: do not plan or journal a data backfill, an ambiguous-legacy-row report, or a personal-owner derivation pass".
- Line 391: "Do not plan a data backfill, ambiguity report, or legacy-row resolution pass for a table with no rows".
- Line 684: the same restriction.
- Lines 1716-1718: "No backfill or ambiguous-legacy-row test is required because the table has no rows".
- [truncated]

Current state:

P3-T8 says personal ownership on external_participant must be a forward-only schema addition to an empty table, with no backfill, no ambiguous-legacy-row report and no personal-owner derivation pass. V97__external_participant_personal_owner.sql does add the owner_app_user_id column, the exactly-one-owner check and the owner-scoped unique indexes, but it also runs a full legacy adoption pass before the constraint:
- It builds a temp table legacy_participant_owner that works out each ownerless participant's owner from the first PARTICIPANT Exchange Share: the Exchange's owner_organization_id, or else COALESCE(owner_user_id, initiator_id).
- It builds a temp table legacy_participant_duplicate that finds surviving directory entries with the same email.
- It moves share.principal_id and principal_group_member rows over to those survivors and deletes duplicate group memberships.
- It backfills the owner columns and deletes any rows still left without an owner.
The header comment of the migration describes this adoption on purpose. A contract test also locks the behavior in: ExternalParticipantPersonalOwnerContractTest has a test named "participants created before ownership existed are adopted by the owner of their exchange". Plan line 1718 says no backfill or legacy-row test is needed.
The practical impact is low. The plan says the table was empty in every environment, so the pass did nothing when it ran. Still, the code, its comments and the test contradict the plan's explicit exclusion. The plan's migration ledger entry for V97 (line 4347) describes only the schema change and [truncated]

Evidence:

- src/main/resources/db/migration/V97__external_participant_personal_owner.sql: the header comment (lines 1-3) says legacy rows "are adopted into the owner of the Exchange". It then contains CREATE TEMP TABLE legacy_participant_owner (derived from share JOIN exchange), CREATE TEMP TABLE legacy_participant_duplicate, UPDATE share SET principal_id = survivor, DELETE/UPDATE principal_group_member, UPDATE external_participant owner columns from the derived owner, DELETE FROM external_participant WHERE both owners are null, and finally the ck_external_participant_owner check and the uq_external_participant_owner_org_email and uq_external_participant_owner_user_email indexes.
- src/test/kotlin/.../ExternalParticipantPersonalOwnerContractTest.kt:47 `participants created before ownership existed are adopted by the owner of their exchange` (with insertOwnerlessParticipant and insertParticipantShare helpers).
- The plan's ledger at line 4347 describes V97 as schema-only and says nothing about [truncated]

Fix outline:

V97 has already been applied and the Development-Stage Constraint forbids editing applied Flyway files, so V97 itself stays as it is. The pass did nothing on an empty table, so no V151 corrective migration is needed.
1. Test: remove the test `participants created before ownership existed are adopted by the owner of their exchange` and its now-unused helpers (insertOwnerlessParticipant, insertParticipantShare, ownerOrganizationOf, shareCountForPrincipal) from ExternalParticipantPersonalOwnerContractTest.kt. That keeps the test suite from making legacy adoption part of the contract, per plan line 1718. Keep the test for owner-scoped email uniqueness.
2. Plan: record the divergence in the plan's migration ledger entry for P3-T8 / V97 at line 4347, stating that V97 contains a no-op legacy-adoption pass that is kept only because applied migrations are immutable.
3. Code check: confirm no application code depends on derived owners. All creation must go through ExternalParticipantService with an explicit owner.
Fix size is small and no user decision is needed.

### GA-116: Several successful mutations return no current ETag

- Severity: low. Verification: partial. Fix size: S. Audit key: `G104-REST-IfMatch428-412-ETag`.
- Plan reference: REST-IfMatch428-412-ETag. Plan basis: Lines 819-822 put the requirement in force. They require `If-Match` for mutable request drafts, party changes, Value Sets, response cycles and review drafts, return 428/412, and say to return the current ETag after every successful mutation. Line 2279 adds consistent resulting ETags within one atomic command. I found nothing in the plan that waives or defers this. Its scope sentence names only the preconditioned resources, which is why only the party-precondition endpoints count as a real gap.

Current state:

Two groups of mutations require `If-Match` against the party ETag (`InformationRequestETag.partyOf(party)`) but send no ETag back, neither as a header nor in the DTO. They are access link issue, rotate, replace and revoke, and the participant account upgrade. That literally breaks the plan rule "Return the current ETag after every successful mutation". The practical impact is small. None of these operations increments `party.partyRevision`, so the current party ETag is the same value the client just sent, and the party DTO already exposes it as `partyETag`.

The other endpoints in the claim take no `If-Match` and have no revisioned validator. That covers accepted-fact promote/revoke, connector exchanges, imported values, generated outputs, discrepancy resolution, reconciliation, record exports and reminders. Plan lines 819-822 tie ETags to the resources that need a precondition (drafts, parties, Value Sets, response cycles, review drafts), so a missing ETag on these endpoints is at most a stylistic gap, not a functional one.

Evidence:

- `InformationRequestAccessLinkResource.kt`: the `issued()` helper (lines 191-194), `rotated()` (196-197) and the revoke `Response.ok` (171) build responses with no ETag header. Each of those methods reads `If-Match` through `CommandPreconditionHeader.required(ifMatch)`.
- `InformationRequestBootstrapShareLinkService.kt`: lines 148, 215, 274 and 343 check `InformationRequestETag.partyOf(party)`. The issuance at 159-176 saves only a `ShareLink` and never increments `party.partyRevision`.
- `InformationRequestParticipantAccountLinkResource.kt`: lines 82-84 return CREATED with no ETag. The upgrade service checks `partyOf` at line 103.
- `InformationRequestETag.kt:26`: `partyOf = RevisionETag.of(party.id, party.partyRevision)`.
- `partyRevision` is only ever incremented on the request entity (`InformationRequestPartyService` 249/315/339/407 and `DelegatedAuthorityService` 173/206), never by link or upgrade code.
- `InformationRequestPartyDtoMapper.kt:26` exposes `partyETag`.
- None of the [truncated]

Fix outline:

1. Have `InformationRequestBootstrapShareLinkService` issue/rotate/replace/revoke return the party ETag alongside the result. For example, add `partyETag: String` to `InformationRequestBootstrapShareLinkIssuance` and to a revoke result type, and fill it from `InformationRequestETag.partyOf(party)`. Do the same for `InformationRequestParticipantAccountUpgrade`.
2. In `InformationRequestAccessLinkResource` (the `issued()`, `rotated()` and revoke paths) and in `InformationRequestParticipantAccountLinkResource.upgrade`, add `.header(HttpHeaders.ETAG, result.partyETag)`.
3. Make command-receipt replays return the same ETag.
4. Optionally, the frontend access-link service can read the header back into the party state it holds.
5. Add resource contract tests asserting the ETag header on each of the 5 endpoints, plus a replay test.
6. No migration is needed. Leave the non-preconditioned external-source, fact, export and reminder endpoints unchanged, or document that they carry no validator.

### GA-117: Template sections have no localized title or help

- Severity: low. Verification: confirmed. Fix size: M. Audit key: `G112-CDM-TemplateSection`.
- Plan reference: CDM-TemplateSection. Plan basis: Plan line 703 (Core Domain Model): TemplateSection is a "Stable ordered section with localized title and help." Nothing narrows or defers it. P2-T3 (lines 1187-1189) and the V86 migration ledger entry (4284-4288) name an "ordered section" without mentioning localization, but they do not waive it. P10-T8 (3646) and Phase 10 item 11 (3582-3584) cover locale formatting of times, numbers and sizes only. The Product Boundary's later extensions and non-goals (304-346) do not mention localization. The [truncated]

Current state:

Each Information Request Template Section stores exactly one title (VARCHAR 255, required) and one optional help_text (VARCHAR 2048). There is no per-locale storage, no locale tag, no fallback-locale rule, and nothing that picks a variant for the viewer. This is not specific to sections. The whole platform has no content-localization infrastructure: the backend has no locale-aware content model, no migration mentions locale, and the web app has no i18n library and does not read navigator.language. Requirement prompt and help on the binding are single strings in the same way. The only localization the plan delivered is formatting (P10-T8, plan item 11: times, numbers and sizes formatted by locale). That is different from localized authored text.

Evidence:

src/main/kotlin/com/docuhyphen/app/api/model/entity/InformationRequestTemplateSection.kt: the only columns are template_version_id, section_key, display_order, title (nullable=false, length 255), help_text (nullable, 2048) and submission_stage_key. src/main/resources/db/migration/V86__information_request_template.sql:117-118 has title VARCHAR(255) NOT NULL and help_text VARCHAR(2048), and line 125 only checks that the title is not blank. A grep for locale/locali across src/main/kotlin finds only java.util.Locale imports in auth/communication/identity services and nothing under informationrequest. No migration contains 'locale'. The web-app src has no hits for i18n, useTranslation or navigator.language.

Fix outline:

1. Migration V151: create information_request_template_section_localization (section_id FK, locale VARCHAR(35) as a BCP 47 tag, title VARCHAR(255) NOT NULL, help_text VARCHAR(2048), unique (section_id, locale)). Extend the V86 freeze guard to this table so a published Version's localized text cannot change. Keep the existing title/help_text as the Version's default-locale text, and add default_locale to information_request_template_version. Do not add a compatibility shim (Development-Stage Constraint).
2. Model/repository: add the entity under model/informationrequest and a repository in repository/informationrequest.
3. Service: accept localized section variants in template authoring and Version drafting. Validate locale tags and require the default-locale text. Add a resolver that picks the best variant from Accept-Language or a stored viewer preference, then falls back to the default locale. Include the variants in the configuration hash and the snapshot taken when a Version is frozen.
4. DTO mappers: return the resolved title and help, plus the full variant list for authors.
5. Frontend: add a per-locale title/help editor to the section authoring in Settings, in its own component and *Styles.tsx file. The respondent and reviewer workspaces show the resolved text.
6. Tests: repository contract test for the freeze guard and unique locale. Service tests for resolution and fallback, and for rejecting a malformed tag or a missing default. Resource test for Accept-Language. Vitest for the editor.
7. Help docs: update the template authoring article in [truncated]

Decision needed. Recommended default: Drop "localized" from the model description. No locale infrastructure exists anywhere on the platform.

### GA-118: No existing-Document linking path and no 'Existing Document linking' tests

- Severity: low. Verification: partial. Fix size: S. Audit key: `G116-Phase`.
- Plan reference: Phase 6 Tests to write first. Plan basis: In force: the Phase 6 "Tests to write first" bullet (2790-2791), "Existing Document linking, exact-hash assessment reuse..."; the exit criterion at 2802, "including evidence linked from an existing Document Version"; the P6-T6 scan contract at 2650-2651, "whether the Document Version already existed". Narrowing: the scanner section at 2432-2434 is permissive ("Linking may create a pending Evidence Version, but cannot bypass this gate"). P6-T1 (2443-2445) requires only a reference to an exact [truncated]

Current state:

The claim's facts hold. No command, service, endpoint or UI lets a party link an existing Exchange Document Version, or any Document Version that already exists, as Information Request evidence. Every file-backed Evidence Version comes from InformationRequestEvidenceUploadService.upload/replace, which always records new bytes through DocumentVersionRecordingService.recordStandaloneDocument or recordNextVersion. InformationRequestDocumentVersionEvidenceSource is only built in that service (line 303), in the source mapper and in tests. No test is named for or exercises "Existing Document linking". But no plan task ever requires a linking capability. P6-T1 only requires evidence to reference an exact Document Version, and the model and persistence do that. The security text (2432-2434) is conditional: linking "may" create a pending Evidence Version and cannot bypass the scan gate. With no linking path, that gate holds trivially. So the real gap is narrower: the Phase 6 test list (2790) and exit criterion (2802) name a linking path that was never built, and the plan does not record it as deferred or out of scope. This is a leftover inconsistency between the plan and the code, not a missing required feature or a bypass risk.

Evidence:

Checked in the code:
- src/main/kotlin/com/docuhyphen/app/api/service/informationrequest/InformationRequestEvidenceUploadService.kt:55 (upload), :88 (replace), :143 (recordStandaloneDocument), :211-212 (recordNextVersion / recordStandaloneDocument), :303 (the only production construction of InformationRequestDocumentVersionEvidenceSource).
- src/main/kotlin/com/docuhyphen/app/api/model/informationrequest/InformationRequestEvidenceSource.kt: holds only the typed source values.
- A grep for existingDocument, linkExisting, LINK_EXISTING and existingVersion in the resource/informationrequest and model packages found nothing.
- Other uses of InformationRequestDocumentVersionEvidenceSource are test fixtures only: InformationRequestEvidenceSourceTest, InformationRequestEvidencePersistenceContractTest:136/626, InformationRequestEvidenceEvaluationServiceTest:288, InformationRequestEvidenceIntakeTest:289.
- InformationRequestEvidenceMalwareAssessmentServiceTest:187/202 covers exact-hash reuse [truncated]

Fix outline:

The user needs to pick one of two options.
(A) Mark linking out of scope. Edit plans/DOCUMENT-DRIVEN-INFORMATION-REQUESTS-IMPLEMENTATION-PLAN.md: change the 2790 test bullet and the 2802 exit criterion to say evidence is always recorded as new request-owned Document Versions and existing-Document linking is not offered in this program. Optionally add a guard test to InformationRequestEvidenceUploadServiceTest. It would assert that every created Evidence Version's documentVersionId belongs to a request-owned Document with no Exchange, and that every new version gets CONTENT_INSPECTION and scan assessments. No code or migration is needed. Size S.
(B) Build linking. This touches:
- InformationRequestEvidenceLinkService: a new command. It takes the parent and request locks, a Command Receipt whose fingerprint includes the documentVersionId and the verified content digest, If-Match on the occurrence evidence ETag, and INFORMATION_REQUEST_EVIDENCE_UPLOAD authorization plus DOCUMENT_VIEW on the source Exchange Document. It verifies the stored digest, refuses END_TO_END content, runs InformationRequestEvidenceIntake checks, records CONTENT_INSPECTION, and leaves the version PENDING for the malware scan.
- Endpoints: POST .../evidence-artifacts/links on InformationRequestEvidenceResource and on the no-auth resource, if allowed.
- A frontend picker in RequirementEvidencePanel and informationRequestEvidenceService.ts.
- Help article updates.
- Tests: link happy path, changed-bytes denial, exact-hash assessment reuse across the linked version, and denial when the caller cannot view [truncated]

Decision needed. Recommended default: Strike existing-Document linking from the plan. Evidence is always uploaded as new request-owned versions.

### GA-119: Per-file and no-auth upload limits are enforced only after the whole multipart body is received and hashed

- Severity: low. Verification: partial. Fix size: S. Audit key: `G117-P6-T9`.
- Plan reference: P6-T9. Plan basis: Plan 2738-2739 is the requirement ("before accepting content"; Phase 12 may tune limits but must not introduce the first abuse guard). Plan 2740-2743 is its Done note, which narrows the wording to "before anything is stored", and the code satisfies that. Plan 4017-4020 (Phase 12 abuse controls) keeps the configured upload limits as the platform ceiling and adds rate limits on the no-auth challenge and session endpoints only. It adds no transport-level body guard for uploads, and nothing [truncated]

Current state:

All six configured limits exist and are enforced: 25 MiB per file, 10 MiB per file without sign-in, request file and byte totals, and party file and byte totals, refusing with INFORMATION_REQUEST_EVIDENCE_UPLOAD_LIMIT_EXCEEDED before any DocumentVersion or evidence row is stored. So the task is met as its own Done note reads it ("before anything is stored"). It is not met under a strict reading of "before accepting content". On the upload and replace evidence endpoints, both authenticated and no-auth, the only ceiling applied before the body is buffered is the global quarkus.http.limits.max-body-size=50M. RESTEasy Reactive therefore writes a multipart body of up to 50 MB to a temp file before the resource method runs. For the no-auth resource this happens before the access link and session are resolved in withAccess. Next, InformationRequestEvidenceUploadService.upload/replace hashes the whole file with SHA-256 and takes the request lock. Only after that does InformationRequestEvidenceIntake.admit call requireWithinUploadLimits. No Content-Length check, per-route body limit or pre-matching filter exists for evidence uploads. The practical effect is that an anonymous or oversized upload still costs up to 50 MB of transfer, temp disk and a full hash before it is refused. It is never stored.

Evidence:

src/main/resources/application.properties:60 (quarkus.http.limits.max-body-size=50M, the only body ceiling); application.properties:428-433 (configured per-file/no-auth/request/party limits); resource/informationrequest/InformationRequestNoAuthEvidenceResource.kt:91-96 and :137-144 (@RestForm("file") FileUpload bound as a method parameter, so the body is buffered before withAccess at :115 resolves the link/session); service/informationrequest/InformationRequestEvidenceUploadService.kt:57 and :90 (DocumentVersionContentDigests.of(command.file.file) hashes the full file before gate.lock and before intake); service/informationrequest/InformationRequestEvidenceIntake.kt:56 (requireWithinUploadLimits runs inside admit, after hashing). grep for Content-Length / max-body / ServerRequestFilter / RouteFilter in src/main/kotlin finds no evidence-upload guard (only AuditExportResource sets a Content-Length response header).

Fix outline:

No migration needed (V151 stays free). Two changes, both backend-only:
(1) Early transport guard. Add a pre-matching guard for POST .../evidence and .../evidence/{artifactId}/versions on the authenticated and no-auth evidence resources. This can be a Vert.x @RouteFilter or a RESTEasy Reactive @ServerRequestFilter(preMatching = true) in service/informationrequest or a shared http package. It reads Content-Length and refuses with 413 and INFORMATION_REQUEST_EVIDENCE_UPLOAD_LIMIT_EXCEEDED when the value exceeds the surface's maximum file size (the no-auth ceiling for the no-auth path) plus a small multipart envelope allowance. Chunked requests without Content-Length would need a counting body handler, or could be refused on the no-auth path. Optionally lower max-body-size, or set a per-path body limit, to match the largest per-file limit plus the envelope.
(2) Order of work. In InformationRequestEvidenceUploadService.upload/replace, check the per-file limit from FileUpload.size() before DocumentVersionContentDigests.of. This could be a new intake.requireWithinFileLimit(surface, size) that runs before the hash and before gate.lock. The per-request and per-party totals stay inside admit, under the lock.
Tests: a resource-level test that an oversized no-auth upload with a declared Content-Length is refused with 413 or the limit error, never reaches withAccess, and creates no temp file or hash; and a service test that the per-file refusal happens before the digest and lock (for example, with a mocked digest that must not be called). Check the help-docs article on evidence upload [truncated]

### GA-120: Response envelope references the mutable Value Set, not an exact Field Value Revision

- Severity: low. Verification: partial. Fix size: S. Audit key: `G131-AD-9`.
- Plan reference: AD-9. Plan basis: Plan lines 441-444 (Decision 9) and 711 state the requirement. Lines 2082-2089 (P5-T1c, complete) explicitly name "current Value Set identity" and InformationRequestResponse.fieldValueSetId as the draft design. That narrows Decision 9 for drafts. Line 2832 (submission content hash includes the "exact Field Value Revision") and lines 4525-4526 ("every recorded response resolves the exact canonical value") are both met by submission items. Line 4380 (V116) also calls these rows "mutable response [truncated]

Current state:

The facts in the claim are correct. The mutable draft envelope (information_request_response / InformationRequestResponse) has only a field_value_set_id. It has no field_value_revision_id. There is one row per request and Requirement, updated in place, so earlier draft envelope revisions are not kept as rows. But the claim overstates the conflict with the plan. The plan's own completed task P5-T1c lists "current Value Set identity" and `InformationRequestResponse.fieldValueSetId` as the delivered design for the draft. Every recorded (submitted) response is frozen into information_request_submission_item. That item copies disposition, narrative, response_revision and provenance, and it pins the exact field_value_revision_id. Accepted facts and recertification (V138, V147) use source_field_value_revision_id. So a submitted response never resolves an older value through a mutable Field Value row, which is what Decision 9 and line 4525 protect. What is left is narrow. (1) The draft envelope row never carries an exact revision pointer. The pin is picked late, at submission time, via FieldValueRevisionRepository.latestRevision. The submission content hash / If-Match guards that choice. (2) Between submissions, no per-save history of the envelope row is kept. Decision 9 read literally puts the revision reference on RequestResponse itself. The code puts it on the frozen submission item.

Evidence:

src/main/resources/db/migration/V110__information_request_response_draft.sql:16 (field_value_set_id REFERENCES field_value_set), :41-42 (UNIQUE(information_request_id, information_request_requirement_id)); src/main/kotlin/com/docuhyphen/app/api/model/entity/InformationRequestResponse.kt:13-19 (documented as the mutable draft; submission packages copy the exact revision), :46-47 (fieldValueSetId only); src/main/resources/db/migration/V133__information_request_submission_package.sql:144-152,166-172,192-194 (submission_item freezes disposition, narrative, response_id, response_revision, provenance, field_value_set_id and FK'd field_value_revision_id); src/main/kotlin/com/docuhyphen/app/api/service/informationrequest/InformationRequestSubmissionContentCollector.kt:78,117-126 (fieldRevisionOf resolves latestRevision at submission time); V138__information_request_accepted_fact_business_decision.sql:18,51,151 and V147__information_request_fact_recertification.sql:34 (downstream facts pin [truncated]

Fix outline:

To match Decision 9 literally, add a V151 migration. It adds information_request_response.field_value_revision_id uuid REFERENCES field_value_revision(id), plus a CHECK that field_value_revision_id IS NULL OR field_value_set_id IS NOT NULL. Also add a trigger guard: the revision's field_value_set_id must equal the response's field_value_set_id. No backfill beyond setting the latest revision per existing row, and no compatibility code, per the Development-Stage Constraint. Add fieldValueRevisionId to InformationRequestResponse.kt. In InformationRequestResponseDraftService.writeFieldValues, set it after SchemaAssignmentService.setValues returns the revision that FieldValueRevisionRecorder recorded. Clear it together with fieldValueSetId at lines 523/555. Change InformationRequestSubmissionContentCollector.fieldRevisionOf to read response.fieldValueRevisionId instead of calling latestRevision. Expose it on InformationRequestResponseDto and the frontend model if needed. Tests: in InformationRequestResponseDraftServiceTest, check that a field patch pins the new revision id and a second patch advances it. Add a submission test proving the package item's field_value_revision_id equals the envelope's pinned id. Add a migration contract test for the set/revision consistency guard. Optionally, if per-save envelope history is wanted, add an append-only information_request_response_revision table. That is a larger design choice.

### GA-121: Bundle Template Version references need a content hash the platform never produces, and bundles are only validated

- Severity: low. Verification: partial. Fix size: S. Audit key: `G134-P11-T2`.
- Plan reference: P11-T2. Plan basis: Requirement: plan lines 3852-3855 (P11-T2, "Define a versioned generic configuration-bundle format"). Narrowing: 3920-3921 (exit criterion: bundles define generic schemas and extension points); 331 (customer-authored configuration bundles listed under "Later configurable extensions", lines 304-346), so applying or importing a bundle is outside the scope of this program; 3792 (Phase 11 decision 11 changes only the connector contract fields). No plan text defines or waives a Template Version [truncated]

Current state:

The content-hash half of the claim holds. BundleTemplateVersionReference.contentHashSha256 is a required, non-null field. The validator only checks that it is 64 lowercase hex characters (hash.malformed). No Template Version content hash exists anywhere in the platform. InformationRequestTemplateVersion has no hash column, no DTO or frontend model carries one, and no service computes one. The only related hash is the configuration hash on each materialized Requirement revision (configuration_hash_sha256, V96). So a bundle author cannot get a real value for this required field, and the platform cannot check that value against a real Template Version. The tests use placeholder values such as "a".repeat(64). The format therefore requires a value it never defines. The second half of the claim is not a gap. The plan asks only to "define" the format (P11-T2), and the Phase 11 exit criterion says bundles define schemas and extension points. Section 331 lists customer-authored bundles under "Later configurable extensions". Having no import or apply path for role presets, clocks, retention, reason codes or validation policies is therefore within scope, and the help article says the endpoint only checks a bundle.

Evidence:

src/main/kotlin/com/docuhyphen/app/api/model/informationrequest/InformationRequestConfigurationBundleModels.kt:24-35 (contentHashSha256: String is required); src/main/kotlin/com/docuhyphen/app/api/service/informationrequest/InformationRequestConfigurationBundleValidator.kt:206-219 (checks the format only via SHA256.matches, no lookup); src/main/kotlin/com/docuhyphen/app/api/model/entity/InformationRequestTemplateVersion.kt:27-76 (no hash column); InformationRequestTemplateDefinition.kt:40 (template_key exists, so templateKey plus versionNumber can be resolved); V86__information_request_template.sql has no hash; the only related hash is V96:257 configuration_hash_sha256 on Requirement revisions (InformationRequestTemplateMaterializer.kt:300,413); src/main/kotlin/com/docuhyphen/app/api/resource/informationrequest/InformationRequestConfigurationBundleResource.kt:16-38 (validation is the only consumer); InformationRequestConfigurationBundleValidatorTest.kt:160,240 (placeholder hashes [truncated]

Fix outline:

Recommended option, which needs no migration: make the hash a real, checkable value. Add an InformationRequestTemplateVersionContentHasher service that computes a canonical SHA-256 over a published version's immutable content (submission mode, stage orderings, and its Requirement bindings with their configuration hashes, in sorted order). Reuse the existing configurationHash material from InformationRequestTemplateMaterializer rather than duplicating it. Show the value as contentHashSha256 on the Template Version DTO and the TS model. Add an optional authenticated lookup to the validator that resolves templateKey plus versionNumber within the caller's scope and reports reference.unknown or hash.mismatch. Alternatively, if the value should be stored, add V151 with a content_hash_sha256 VARCHAR(64) column on information_request_template_version, set at publish time with a CHECK on the hex format. Under the development-stage constraint, backfill existing rows without keeping any compatibility path. The simplest alternative is to drop contentHashSha256 from BundleTemplateVersionReference, or make it optional, and pin by templateKey and versionNumber only. Tests: hasher determinism and sensitivity to binding changes, validator mismatch and unknown-version refusals, a DTO contract test, and an update to InformationRequestConfigurationBundleValidatorTest so it no longer uses placeholder hashes. Update the help article to say where the hash comes from. Importing or applying bundles stays out of scope as a later extension.

Decision needed. Recommended default: Expose a platform-computed content hash on the Template Version DTO and validate bundle references against it.

### GA-122: Frozen supporting-link member is not tied to its request by a composite key or guard

- Severity: low. Verification: confirmed. Fix size: S. Audit key: `G146-P7-T1a`.
- Plan reference: P7-T1a. Plan basis: Plan lines 2889-2891 (P7-T1a: composite keys holding every member, including the supporting link member, to its own request, marked [x]). Lines 2885-2886 say packages freeze the materialized supporting-evidence link rows. I found nothing in the plan that supersedes, narrows or defers this.

Current state:

In V133, information_request_submission_supporting_link ties its package to the request with a composite FK (package_id, information_request_id). Its supporting_evidence_link_id, however, has only a single-column FK to information_request_supporting_evidence_link(id). Nothing at the database level checks that the frozen link belongs to the same information_request_id. supported_requirement_id and supporting_requirement_id have no FK, and no check or trigger makes them match the referenced link row. The member table has only an append-only trigger and no BEFORE INSERT guard. V131 has no UNIQUE (id, information_request_id) on information_request_supporting_evidence_link, so a composite FK is not possible today. None of the later migrations (V134-V150) add one; they only touch this table in disposal DELETEs. The application path is correct: InformationRequestSubmissionService.kt:267-276 copies the member values from link rows loaded for the same request. So the gap is database-level defense-in-depth only. The contract test that checks members stay inside their own request leaves out the link member.

Evidence:

src/main/resources/db/migration/V133__information_request_submission_package.sql:299-314 (link_fkey is single-column on supporting_evidence_link_id; supported_requirement_id and supporting_requirement_id are bare uuid NOT NULL columns); V133:518-520 (only an append-only trigger on this table); src/main/resources/db/migration/V131__information_request_supporting_evidence_link.sql:1-26 (composite FKs to requirement, UNIQUE only on (supported_requirement_id, supporting_requirement_id), no UNIQUE (id, information_request_id)); V142/V146/V147/V148 only DELETE from the table; src/main/kotlin/com/docuhyphen/app/api/service/informationrequest/InformationRequestSubmissionService.kt:267-276 (the app copies values from the link row); src/test/kotlin/com/docuhyphen/app/api/migration/InformationRequestSubmissionPackageContractTest.kt:147 (the cross-request test covers item, evidence and attestation only; the link member is inserted only in the happy-path test at lines 58-66)

Fix outline:

1. Add a new migration, V151__information_request_submission_supporting_link_scope.sql. The Development-Stage Constraint means no backfill or compatibility code is needed. In it:
   - Add `ALTER TABLE information_request_supporting_evidence_link ADD CONSTRAINT ux_information_request_supporting_evidence_link_request UNIQUE (id, information_request_id)`.
   - Drop information_request_submission_supporting_link_link_fkey and replace it with a composite FK (supporting_evidence_link_id, information_request_id) REFERENCES information_request_supporting_evidence_link (id, information_request_id).
   - Make the frozen requirement ids match the link. Either add a composite FK (supporting_evidence_link_id, supported_requirement_id, supporting_requirement_id) against a new UNIQUE (id, supported_requirement_id, supporting_requirement_id) on the link table, or add a BEFORE INSERT guard trigger (information_request_submission_supporting_link_guard) that raises when the row does not match. Also add composite FKs (supported_requirement_id, information_request_id) and (supporting_requirement_id, information_request_id) to information_request_requirement.
2. No entity or service change is needed. The service already writes consistent values.
3. Tests: in InformationRequestSubmissionPackageContractTest, extend the "stay inside the package's own request" test, or add a new one, so that inserting a link member whose supporting_evidence_link_id belongs to another request is rejected. Also assert that an insert with supported_requirement_id or supporting_requirement_id different from the link [truncated]

### GA-123: Attestation service refusals and the naming boundary are untested

- Severity: low. Verification: confirmed. Fix size: M. Audit key: `G147-P7`.
- Plan reference: P7 Tests to write first / P7-T4. Plan basis: Plan 2977-2978 (P7 tests to write first: response-attestation version, naming boundary, actor-strength; multi-party order, quorum, refusal, expiry, delegated-authority). 2904-2912 (P7-T4 marked done, keep names distinct from ExchangeRecipientAttestation). 642 and 1191 (the naming distinction). Exit criterion near 2992: attestation must not be confused with Trusted Organization recipient-selection attestation. Nothing found in the plan that waives or defers these tests.

Current state:

InformationRequestSubmissionAttestationService is implemented with its own refusal paths: ATTESTATION_NOT_AN_ASSERTION, ATTESTATION_STRENGTH_INSUFFICIENT (weak proof or unsupported principal kind), ATTESTATION_ORDER_UNMET (ROLE_SEQUENCE assent before an earlier role), ATTESTATION_PARTY_NOT_ELIGIBLE / ATTESTATION_PARTY_AMBIGUOUS, ATTESTATION_REFUSAL_REASON_INVALID, and ATTESTATION_SIGNATURE_REFERENCE_REQUIRED / _NOT_ACCEPTED. None of these error codes appear anywhere in src/test. Service-level coverage is limited to InformationRequestSubmissionTransactionTest and the basic conformance test, which record only a single ASSENTED attestation from one attestor and test the stale-ETag precondition. No service or transaction test records a REFUSED decision; REFUSED appears only in the pure evaluator test and in V133 SQL constraint tests. ROLE_SEQUENCE ordering, expiry, quorum and weak strength are covered only at the pure evaluator level (InformationRequestAttestationPolicyEvaluatorTest, WalkingSkeletonPhase7Test), and never through the recording service with two real roles. No Information Request test asserts the naming or semantic boundary against ExchangeRecipientAttestation: that name appears only in Exchange tests. The resource contract test mocks the service, so it proves nothing about these refusals.

Evidence:

src/main/kotlin/com/docuhyphen/app/api/service/informationrequest/InformationRequestSubmissionAttestationService.kt:111-112 (NOT_AN_ASSERTION), 124-125 and 292-293 (STRENGTH_INSUFFICIENT), 144-145 (ORDER_UNMET), 274-279 (PARTY_NOT_ELIGIBLE/AMBIGUOUS), 308-309 (REFUSAL_REASON_INVALID), 327-338 (SIGNATURE_REFERENCE_NOT_ACCEPTED/REQUIRED). grep of src/test for these codes returned nothing. src/test/.../service/informationrequest/InformationRequestSubmissionTransactionTest.kt:125-195,227,379,474-479 records only ASSENTED from attestorUserId plus a stale test. src/test/.../model/informationrequest/InformationRequestAttestationPolicyEvaluatorTest.kt:27-120 covers evaluator only (quorum, refusal, expiry, strength, ROLE_SEQUENCE). src/test/.../InformationRequestTemplateWalkingSkeletonPhase7Test.kt:76-98 evaluator only. src/test/.../resource/informationrequest/InformationRequestSubmissionResourceContractTest.kt:72 mocks the attestation service. grep for RecipientAttestation in the [truncated]

Fix outline:

No migration needed. Add service-level tests using the existing SubmissionRuntimeSqlFixture/InformationRequestRuntimeTestServices, either in InformationRequestSubmissionTransactionTest or in a new InformationRequestSubmissionAttestationTransactionTest: (1) REFUSED with a valid reason is recorded and blocks readiness, then a later assent from the same party supersedes it; REFUSED with a missing or invalid reason is refused with ATTESTATION_REFUSAL_REASON_INVALID. (2) Under a ROLE_SEQUENCE policy, a second-role assent before the first role assents raises ATTESTATION_ORDER_UNMET; after the first role assents, the second succeeds and readiness is SATISFIED. This needs a fixture with two role-bound users (for example SUBJECT then ATTESTOR). (3) A caller below minimumAuthenticationStrength (for example a no-auth or verified-contact principal) raises ATTESTATION_STRENGTH_INSUFFICIENT. (4) A caller who holds no eligible role raises ATTESTATION_PARTY_NOT_ELIGIBLE; a caller holding two eligible roles raises ATTESTATION_PARTY_AMBIGUOUS. (5) When the policy requires an external signature reference, a missing one raises ATTESTATION_SIGNATURE_REFERENCE_REQUIRED; when the policy forbids one, supplying one raises ATTESTATION_SIGNATURE_REFERENCE_NOT_ACCEPTED. (6) Add a quorum and expiry case through the service. (7) Add a naming-boundary test that reflects over the informationrequest model, entity, DTO and service packages and the V132/V133 table names, and asserts that no Information Request type or table references ExchangeRecipientAttestation or exchange_recipient_attestation, and that [truncated]

### GA-124: No test that an amendment Notice Intent is recovered when its event was consumed before any consumer existed

- Severity: low. Verification: confirmed. Fix size: S. Audit key: `G148-P7`.
- Plan reference: P7 Tests to write first / P7-T5. Plan basis: The requirement is in force through plan lines 2913-2919 (P7-T5: the intent "cannot be lost if an outbox event is dispatched before a later consumer exists") and 2981-2982 (P7 tests to write first: "outbox-delivered-before-consumer recovery"). Lines 2870-2871 kept Phase 7 intents pending only until Phase 9, and Phase 9 has installed delivery (V141, the worker and the scheduler), so the recovery behaviour can now be tested. Plan line 3465 (Phase 9 "Notice Intent claim and recovery") restates it. [truncated]

Current state:

The recovery path itself exists. The amendment writes PENDING REQUIREMENTS_AMENDED Notice Intents in the same transaction as the amendment, and its domain event carries pendingNoticeCount. InformationRequestNoticeConsumer picks up that event and calls dispatchForRequest. Separately, InformationRequestNoticeScheduler.tick calls InformationRequestNoticeWorker.dispatchPending, which finds every unclaimed intent (findUnclaimedIds) whether or not an event is still waiting. So an intent left behind after its event was consumed is picked up by the scheduled poll.

What is missing is a test. No test covers the "outbox delivered before consumer" case the plan lists. No test calls dispatchPending or InformationRequestNoticeScheduler.tick. No test claims, renders or delivers a REQUIREMENTS_AMENDED intent. The amendment tests only check that intents are created PENDING. Every notice-worker test builds its intents from overdue clocks or reminders and uses dispatchForRequest or the consumer. The only test that mentions REQUIREMENTS_AMENDED is a migration contract test checking the V134 constraint.

Evidence:

src/main/kotlin/com/docuhyphen/app/api/service/informationrequest/InformationRequestNoticeWorker.kt:22-29 (dispatchPending uses intentRepository.findUnclaimedIds); InformationRequestNoticeScheduler.kt:14-24 (the scheduled tick calls dispatchPending); InformationRequestNoticeConsumer.kt:17-23,31 (handles pendingNoticeCount, calls dispatchForRequest); InformationRequestAmendmentService.kt:130 (event payload pendingNoticeCount); InformationRequestNoticeModels.kt:20 (REQUIREMENTS_AMENDED content exists); V141__information_request_outbound_notice.sql:13-16 (outbound notice accepts REQUIREMENTS_AMENDED with amendment_id). Tests: InformationRequestAmendmentTransactionTest.kt:87 (only checks PENDING); InformationRequestNoticeTransactionTest.kt:68-160 (overdue fixture only, dispatchForRequest and routeDurable); the other worker users (InformationRequestOperationsTransactionTest:179,294, InformationRequestTemplateWalkingSkeletonPhase9Test:241, TimedRetainedExportRequestConformanceTest:186) all [truncated]

Fix outline:

Add a @QuarkusTest, for example "an amendment's notice intents are recovered by the pending dispatch after its event was consumed without them" in src/test/kotlin/com/docuhyphen/app/api/service/informationrequest/InformationRequestNoticeTransactionTest.kt or a new InformationRequestAmendmentNoticeRecoveryTest.kt:
(1) Run an amendment the way InformationRequestAmendmentTransactionTest does, so each active party gets a PENDING REQUIREMENTS_AMENDED intent.
(2) Record the amendment event as already consumed, or delivered before any consumer existed, without rendering anything. For example, insert a domain_event_consumption row for the consumer key, or route the event while InformationRequestNoticeConsumer is not registered. Then check that no outbound notice exists.
(3) Call InformationRequestNoticeWorker.dispatchPending() (or InformationRequestNoticeScheduler.tick()).
(4) Check that each intent is claimed exactly once and rendered with the REQUIREMENTS_AMENDED content and amendment_id. Check that notices are delivered through RecordingInformationRequestNoticeSender. Check that a second dispatchPending renders and delivers nothing more.
Optionally, add a pending-versus-delivered presentation check: the intent's state reader reports pending before the dispatch and delivered after it. No production code change and no migration (V151 stays free). Help docs are not affected.

### GA-125: Respondent workspace shows 'Request more information' to callers who cannot request a supplement

- Severity: low. Verification: confirmed. Fix size: S. Audit key: `G149-P7-T9`.
- Plan reference: P7-T9. Plan basis: Plan lines 2934-2938 (P7-T6): a supplement is "open to a reviewer through INFORMATION_REQUEST_REQUEST_SUPPLEMENT or to the request administrator". Lines 2953-2957 (P7-T9): supplemental-request UI in the respondent workspace; its Done note says the action needs a signed-in caller whose plan includes Information Requests. That note describes the current gating but does not waive the server permission rule. I found no superseding decision, waiver or non-goal that allows showing the action to [truncated]

Current state:

The respondent workspace (RespondentWorkspaceBody, used by the authenticated and no-auth InformationRequestRespondentWorkspace routes in App.tsx) mounts InformationRequestSubmissionSection. That section shows the "Request more information" button whenever the caller is not using an access link, execution standing is ACTIVE, and the request state is ISSUED, IN_PROGRESS or CLOSED. It never checks whether the caller may create a supplement. The server allows a supplement only for callers who hold INFORMATION_REQUEST_SUPERSEDE (capability INFORMATION_REQUEST_ADMIN) or INFORMATION_REQUEST_REQUEST_SUPPLEMENT (capability INFORMATION_REQUEST_REVIEW). A RESPONDENT or ATTESTOR role has neither. InformationRequestResponseWorkspaceDto, on both the backend and the frontend, carries no permission flag. So an ordinary signed-in respondent sees the action, and every attempt ends in a 403 ("Access denied to this Information Request"). Permission data does exist, but only on the list or summary path (InformationRequestCallerStandingService gives canManage, canRespond and canReview), not on the workspace.

Evidence:

web-app/src/app/information-requests/submission/follow-up-section/InformationRequestSubmissionSection.tsx:43-45: canRequestSupplement = !accessLinkToken && executionStanding ACTIVE && FOLLOW_UP_STATES. The button is rendered at lines 98-108.
web-app/src/app/information-requests/respondent-workspace/respondent-workspace-body/RespondentWorkspaceBody.tsx:46: mounts the section.
web-app/src/App.tsx:81,116: routes to the respondent workspace.
web-app/src/app/models/models.tsx:2513-2526 and src/main/kotlin/com/docuhyphen/app/api/model/dto/InformationRequestResponseWorkspaceDtos.kt:8-20: the workspace DTO has no permission fields.
src/main/kotlin/com/docuhyphen/app/api/service/informationrequest/InformationRequestSuccessorService.kt:86-90: requires SUPERSEDE or REQUEST_SUPPLEMENT.
InformationRequestMutationGate.kt:87-93: throws ForbiddenException when neither is held.
Action.kt:138,144: SUPERSEDE needs ADMIN; REQUEST_SUPPLEMENT needs REVIEW.
RoleCapabilities.kt:~260-280: the RESPONDENT and [truncated]

Fix outline:

No migration is needed.
1. Backend: add `canRequestSupplement: Boolean` to InformationRequestResponseWorkspaceDto (model/dto/InformationRequestResponseWorkspaceDtos.kt). Wherever the workspace DTO is assembled (the respondent workspace service or its mapper), compute it for principal callers as gate.permitsRequest(access, INFORMATION_REQUEST_REQUEST_SUPPLEMENT, id) || gate.permitsRequest(access, INFORMATION_REQUEST_SUPERSEDE, id). Access-link callers always get false. Keep the calculation in a service, not in the resource or the mapper logic.
2. Frontend: add the field to InformationRequestResponseWorkspaceDto in models.tsx. In InformationRequestSubmissionSection.tsx, AND workspace.canRequestSupplement into canRequestSupplement.
3. Tests: in the backend workspace test, assert false for a RESPONDENT and true for a REVIEWER and for an administrator. Update the InformationRequestSubmissionSection.test.tsx fixtures and add a case where the button is hidden when canRequestSupplement is false.
4. Help docs: check informationRequestManagingArticle.tsx lines 82-84 and add a sentence if the respondent-facing action is documented.

### GA-126: Help says the requesting party can amend a request, but no screen offers an amendment

- Severity: low. Verification: partial. Fix size: S. Audit key: `G150-P7-T5`.
- Plan reference: P7-T5 / help docs. Plan basis: - Plan lines 2913-2933 (P7-T5): amendment service and records. Backend only, done.
- Lines 2953-2958 (P7-T9): the minimal UI covers only an "amendment change summary with pending-notice state". It does not include a screen that creates an amendment, so the missing screen is out of scope.
- No later plan text (for example lines 3826, 3876, 4182, 4658) adds an amendment-authoring UI.
- The requirement that is in force comes from the AGENTS.md rule that help docs must accurately describe UI paths.

Current state:

Amending a request works on the backend: POST /information-requests/{id}/amendments in InformationRequestAmendmentResource, backed by InformationRequestAmendmentService. The frontend can only read amendments. informationRequestSubmissionService.ts exports getInformationRequestAmendments, and InformationRequestAmendmentSummary shows the "What changed in this request" list in the respondent workspace. No service function or component creates an amendment.

The plan never asked for a screen that creates one. P7-T9 asks only for an amendment change summary with pending-notice state, and that is built. So the missing screen is not a plan gap.

What remains is a small help-doc gap. informationRequestSubmissionArticle.tsx ("Amendments") says the requesting party "can amend an issued request to a later published Version of its Template". informationRequestManagingArticle.tsx says "only an amendment of the request itself moves it to another Version". Neither article says how to amend or that no screen offers it, so a reader on the requesting side will look for a control that does not exist. Both statements are still true, because the API supports amendment. The docs are incomplete, not wrong.

Evidence:

- src/main/kotlin/com/docuhyphen/app/api/resource/informationrequest/InformationRequestAmendmentResource.kt:21 has @Path("/information-requests/{id}/amendments"), with @GET list and @POST amend (If-Match and Idempotency-Key headers).
- InformationRequestNoAuthAmendmentResource.kt:18 has the no-auth path.
- web-app/src/services/informationRequestSubmissionService.ts:100-104 has getInformationRequestAmendments (a GET). The grep found no POST to /amendments anywhere in web-app/src.
- web-app/src/app/information-requests/submission/amendment-summary/InformationRequestAmendmentSummary.tsx is read-only. It is mounted from follow-up-section/InformationRequestSubmissionSection.tsx:74.
- Outside the summary, "amend" appears only in operationsLabels.ts:81 (REQUIREMENTS_AMENDED label), submissionLabels.ts, and models.tsx.
- Help: informationRequestSubmissionArticle.tsx:88-100 ("Amendments") and informationRequestManagingArticle.tsx:23. Neither mentions the API or says no screen exists.

Fix outline:

Change only the help docs. No backend change and no migration.

1. In web-app/src/app/components/help-docs/sections/articles/informationRequestSubmissionArticle.tsx, add a sentence to the Amendments paragraph (lines 89-96). It should say the requesting party amends a request through the API (POST /information-requests/{id}/amendments with the target published Version), that no screen offers this yet, and that respondents see the result under "What changed in this request".
2. Add the same note to informationRequestManagingArticle.tsx near line 23.
3. Keep both articles under 150 lines, then run `npx tsc --noEmit` in web-app/.

If the product owner wants a real screen instead: add amendInformationRequest(requestId, body, etag, idempotencyKey) to informationRequestSubmissionService.ts, build a small requesting-side dialog (a Version picker plus a refusal-reason display) with a co-located Styles file, add a Vitest test, and update the help to name the screen. The current plan does not call for this.

### GA-127: Privacy deletion refuses entirely when the subject has any open request

- Severity: low. Verification: partial. Fix size: M. Audit key: `G157-P9-Decision-12`.
- Plan reference: P9-Decision-12. Plan basis: - Plan lines 3311-3317 (decision 12): deletion claims each terminal request unless a hold or minimum retention forbids it.
- Plan lines 3433-3439 (P9-T10 Done note): "deletion is all or nothing". This narrows decision 12 to an all-or-nothing outcome but does not say whether an open request is a valid blocker.

Current state:

The behaviour described in the claim is what the code does today. InformationRequestPrivacyService.delete collects every request that has a SUBJECT party for the subject, whatever its state (subjectRequestIds, InformationRequestPrivacyRepositories.kt ~79-96). It then assesses each request with InformationRequestDisposalEligibility.assess. A non-terminal request returns RECORD_NOT_FINISHED (InformationRequestDisposalEligibility.kt:40-41). Any single refusal marks the whole privacy request REFUSED and claims nothing (InformationRequestPrivacyService.kt:139-146). As a result, one open request blocks deletion of all the subject's terminal requests, even though no hold or minimum retention forbids it. Decision 12 names only hold or minimum retention as reasons to refuse.

This is not purely a gap, because the plan itself records "deletion is all or nothing" as the delivered P9-T10 behaviour (plan line 3438-3439). A test also asserts this behaviour on purpose: InformationRequestPrivacyTransactionTest lines 142-147 and 183-195, where an open sibling request causes RECORD_NOT_FINISHED and nothing is disposed. So blocking the whole deletion is a recorded, accepted choice. Whether an open request should count as a blocker at all is a product decision that the plan text does not settle: decision 12 lists only hold and retention, while the Done note says all or nothing.

The part that is genuinely wrong is atomicity, and it breaks the plan's own "all or nothing" note. Assessment runs in one transaction (lines 132-138). Afterwards each request is claimed and processed separately: [truncated]

Evidence:

- src/main/kotlin/com/docuhyphen/app/api/service/informationrequest/InformationRequestPrivacyService.kt:127-164: the delete flow. It assesses first (132-138), refuses the whole request if anything is refused (139-146), then claims and processes each request separately (148), and ends REFUSED if any claim refused (161-162).
- InformationRequestDisposalEligibility.kt:40-41: returns RECORD_NOT_FINISHED for a non-terminal request.
- InformationRequestPrivacyRepositories.kt subjectRequestIds: has no filter on request state.
- InformationRequestDisposalService.kt:131-136: claimAndProcess processes (deletes) straight after each claim.
- src/test/kotlin/.../InformationRequestPrivacyTransactionTest.kt:142-147 and 183-195: assert that an open request refuses the whole deletion and nothing is disposed.

Fix outline:

1. Product decision needed: should open requests be skipped or block the deletion?
   - To skip them: filter subjectRequestIds to terminal states (or skip RECORD_NOT_FINISHED in delete) and record the skipped open requests as a target outcome such as SKIPPED_OPEN. This needs a new InformationRequestPrivacyTargetOutcome value. If the outcome column has a check constraint, it also needs migration V151 to widen it.
   - To keep blocking: update the decision 12 text instead.
2. Needed either way, to make the documented all-or-nothing hold: split the disposal service into two steps.
   - First, claim every request in a single transaction (claim with the lock and recheck of holds, retention and references). If any claim refuses, roll back all of them.
   - Only then call process() for each claim.
   - Alternatively, take the hold-placement refusal lock (open claims already refuse new holds) before any processing.
   This changes InformationRequestPrivacyService.delete and InformationRequestDisposalService (expose claim-only and process separately).
3. Tests in InformationRequestPrivacyTransactionTest:
   - A hold placed on the second request after assessment must result in no disposal and a REFUSED privacy request with no claims.
   - If open requests are skipped: finished requests are disposed and open requests are reported as skipped.
4. Update the help-doc article on privacy requests if the user-visible behaviour changes.

Decision needed. Recommended default: Dispose finished requests and report still-open ones as skipped, per Phase 9 Decision 12.

### GA-128: Only subscription refusals are SKIPPED; other Workflow trigger failures retry forever and block the request's ordered queue

- Severity: low. Verification: partial. Fix size: M. Audit key: `G158-P9-Decision-3`.
- Plan reference: P9-Decision-3. Plan basis: Plan lines 3234-3240 (Decision 3): the SKIPPED receipt is required only for "a subscription refusal". Lines 3357-3363 (P9-T2 done note): "SKIPPED receipts for subscription refusals". Nothing in the plan asks for poison-event or dead-letter handling for other Workflow trigger failures. The dispatcher's never-discard retry semantics come from earlier outbox design and are not superseded. The plan's rationale ("never an endless retry that would block the request's ordered queue") supports the [truncated]

Current state:

The behaviour described is accurate. InformationRequestWorkflowTriggerConsumer.consume catches only SubscriptionDenialException and turns it into a SKIPPED receipt. Any other exception thrown by WorkflowEngineService.trigger escapes, for example WorkflowSpecJson.decode on a corrupt stepsJson, an applicability evaluation error, or an assignee or step creation failure inside triggerOne. That exception propagates through DomainEventConsumptionService.consumeOnce (MANDATORY tx) and EventRouter.routeDurable, which has no per-consumer isolation, so the routing transaction rolls back every consumer's effect for that event. DomainEventDispatcher.deliverNext then leaves the row PENDING and retries it with backoff capped at 1 hour, with no limit on attempts. claimNextPending will not claim a later row with the same ordering_key while an earlier one is PENDING. A deterministic failure in one matching definition therefore stalls every later event for that request (view, start, submit, close, overdue) for all consumers, including the notice consumer. The plan does not cover this, and that is why the verdict is partial rather than confirmed. Decision 3 and the P9-T2 done note ask for SKIPPED handling only for subscription refusals, and that is implemented: refusals are checked for all definitions before any instance is created. Retrying other failures forever is the documented outbox behaviour ("Required events are never discarded"). So the plan's stated requirement is met. What is left is a robustness risk the plan leaves open: a permanent, non-refusal failure blocks the request's [truncated]

Evidence:

src/main/kotlin/com/docuhyphen/app/api/service/informationrequest/InformationRequestWorkflowTriggerConsumer.kt:39-47: try/catch covers only SubscriptionDenialException. src/main/kotlin/com/docuhyphen/app/api/service/workflow/DefaultWorkflowEngineService.kt:123: subscriptionGuard.requireInstanceStart runs for all definitions before triggerOne. :170: WorkflowSpecJson.decode, uncaught. src/main/kotlin/com/docuhyphen/app/api/service/notification/EventRouter.kt:59-69: routeDurable calls consumeOnce for each consumer with no try/catch. src/main/kotlin/com/docuhyphen/app/api/service/notification/DomainEventConsumptionService.kt:15-23: MANDATORY transaction, and the receipt is recorded only after consume returns. src/main/kotlin/com/docuhyphen/app/api/service/notification/DomainEventDispatcher.kt:119-130: on a Throwable the row stays PENDING with a backoff retry. :151-157 and :180: backoff capped at 1 hour, no maximum number of attempts. Only an envelope decode error marks the row FAILED [truncated]

Fix outline:

Pick a policy, then implement it. Option A, recommended: in DefaultWorkflowEngineService, or a new WorkflowTriggerFailureClassifier in service/workflow, classify deterministic per-definition failures (spec decode, invalid applicability, unresolvable assignees) as a new typed WorkflowDefinitionStartRefusedException carrying a safe reason code. InformationRequestWorkflowTriggerConsumer then returns DomainEventConsumptionResult.skipped("WORKFLOW_START_REFUSED:<definitionId>:<reason>") and logs an error, with the same audit/ops visibility as refusals. Keep transient failures (DB, lock) retrying. To stop one bad definition from blocking the others, wrap each triggerOne call so a deterministic failure skips only that definition. Option B: a dispatcher-level attempt cap for ordered rows that moves an ordered row to FAILED after EXHAUSTED_THRESHOLD and unblocks the ordering key. That changes the never-discard guarantee and needs sign-off. No migration is needed for Option A. The receipt outcome already allows SKIPPED with detail. Option B may need a status value that V139/outbox constraints already allow (FAILED exists). Tests: (1) consumer unit test where the engine throws the classified exception, expecting a SKIPPED receipt; (2) integration test where a request with an active definition whose stepsJson fails to decode still delivers a later event with the same ordering key, and the notice consumer receipts are applied; (3) a transient exception still retries and keeps order.

Decision needed. Recommended default: Record a SKIPPED receipt with an operator-visible reason for deterministic non-subscription failures, so the ordered queue keeps flowing.

### GA-129: Empty requirement operand skips the value type check (IS_EMPTY matches across types)

- Severity: low. Verification: confirmed. Fix size: S. Audit key: `G159-P9-Decision-5`.
- Plan reference: P9-Decision-5. Plan basis: Plan lines 3248-3253 (Phase 9 decision 5) say a type mismatch is a non-match: "automation never picks a response". I found nothing that supersedes, narrows or defers this rule for empty operands.

Current state:

Requirement conditions check the declared valueType against the frozen value's type only when the operand is WorkflowRequirementOperand.Value. When InformationRequestWorkflowOperandService returns WorkflowRequirementOperand.Empty (a FIELD item with no fieldValueRevisionId, or a canonical value that is empty), the object carries no type. The evaluator then calls evaluate(condition.valueType, ..., null) with no type comparison. Result: an IS_EMPTY condition declared with any valueType (INTEGER, BOOLEAN, ...) matches an unanswered or empty Requirement of a different type, such as SHORT_TEXT, and IS_NOT_EMPTY returns false. The existing unit test encodes this: an Empty operand with an INTEGER IS_EMPTY condition asserts true, and nothing in the test fixes the Requirement's own type. The save-time validator (WorkflowApplicabilityEvaluator.validate) also never checks the declared valueType against the Template Requirement's field type, so a wrong type is accepted when the definition is saved. This goes against Decision 5, which says a type mismatch is a non-match. The impact is small, because only IS_EMPTY and IS_NOT_EMPTY behave differently on empty operands.

Evidence:

WorkflowApplicabilityEvaluator.kt:127-134. Unavailable -> false; `WorkflowRequirementOperand.Empty -> evaluate(condition.valueType, condition.operator, condition.value, null)` (no type check); Value -> `operand.value.type == condition.valueType && evaluate(...)`. Lines 161-163 show that IS_EMPTY returns !present no matter what the type is. Lines 74-82: validate() checks only the UUID, the occurrencePath and validateOperator(valueType, operator, value), never the Requirement's actual field type. InformationRequestWorkflowOperandService.kt:45 and :48 return Empty, a data object with no type (WorkflowRequirementOperand.kt:9 `data object Empty`). WorkflowRequirementConditionTest.kt:70-79 asserts assertTrue(applies(condition(INTEGER, IS_EMPTY, null))) for an Empty operand. InformationRequestRequirement.kt carries only the source template ids and occurrencePath, no value type, so the operand service does not know the type today.

Fix outline:

1) In model/workflow/WorkflowRequirementOperand.kt, change Empty to `data class Empty(val type: FieldValueType)`. 2) In InformationRequestWorkflowOperandService.frozenValue, look up the item's field value type from the Template Requirement's field definition or schema binding, using that owning service's query method rather than its repository. Return Empty(type) for a missing revision or an empty value, and return Unavailable if the type cannot be resolved. 3) In WorkflowApplicabilityEvaluator.requirementConditionsMatch, change the Empty branch to `operand.type == condition.valueType && evaluate(...)`. 4) Optionally, at definition save in validate(), resolve each templateRequirementId's field type through an injectable lookup and reject a valueType that does not match. 5) Tests: update WorkflowRequirementConditionTest so that an Empty(SHORT_TEXT) operand with an INTEGER IS_EMPTY condition is a non-match, and Empty(INTEGER) with INTEGER IS_EMPTY is a match. Extend InformationRequestWorkflowOperandServiceTest to check that Empty carries the field's type. No migration is needed.

### GA-130: Reconsider is offered for satisfied or non-reopenable reviews that the backend always refuses; help text is wrong

- Severity: low. Verification: confirmed. Fix size: S. Audit key: `G165-P8-T7`.
- Plan reference: P8-T7 / P8-T11. Plan basis: Plan line 3085-3087 (Decision 13: reconsideration and appeal name the exact prior settled review, REJECTED or CHANGES_REQUESTED with its correction still open). P8-T7 at line 3141. Phase 12 design line 3570 requires a reconsideration control in the reviewer workspace, but nothing widens the reopenable rule or permits offering an action that always fails. No waiver or deferral was found.

Current state:

The reviewer workspace shows the Reconsider button whenever review.canManage is true and the review state is one of CHANGES_REQUESTED, REJECTED, SATISFIED or SATISFIED_WITH_EXCEPTION (ReviewWorkspaceContent.tsx SETTLED set). The backend's reopenReview accepts only the latest review of a current package that is REJECTED, or CHANGES_REQUESTED with its correction still OPEN. It refuses everything else with INFORMATION_REQUEST_REVIEW_NOT_REOPENABLE. So the button still shows for satisfied reviews, for CHANGES_REQUESTED reviews whose correction was resubmitted or superseded, for reviews that are not the latest one for their package, for packages that are no longer current, and for terminal requests. Pressing it in any of those cases always fails. The respondent side gets a server-computed canAppeal that uses exactly the backend's reopenable rule. The reviewer readable DTO has only canManage and no matching canReconsider. The help article (informationRequestReviewArticle.tsx lines 105-106) says "A settled review can be reconsidered with Reconsider". That is wrong: satisfied reviews and reviews whose correction has closed cannot be reconsidered. Backend enforcement is correct and matches the plan. The gap is limited to the UI offering the action and to the help text.

Evidence:

web-app/src/app/information-requests/review/review-workspace-content/ReviewWorkspaceContent.tsx:24-29 (the SETTLED set includes SATISFIED and SATISFIED_WITH_EXCEPTION), :73 (the button is gated only by `review.canManage && SETTLED.has(review.review.state)`). src/main/kotlin/com/docuhyphen/app/api/service/informationrequest/InformationRequestReviewCycleService.kt:106-121 (reopenable requires latest, current, and REJECTED or CHANGES_REQUESTED with an OPEN correction; otherwise it throws REVIEW_NOT_REOPENABLE). InformationRequestReviewQueryService.kt:86-90,106 (canAppeal applies the same reopenable rule and a non-terminal check for respondents), :172 (the reviewer readable view exposes only canManage). No canReconsider exists anywhere in the backend or the frontend (grep). web-app/src/app/components/help-docs/sections/articles/informationRequestReviewArticle.tsx:104-107 ("A settled review can be reconsidered with Reconsider").

Fix outline:

Backend: add `canReconsider: Boolean` to the reviewer readable review model (model/informationrequest/InformationRequestReviewModels.kt), to InformationRequestReviewDtos.kt and to InformationRequestReviewDtoMapper. Compute it in InformationRequestReviewQueryService.readable as canManage && latest-for-package && package current && (REJECTED || (CHANGES_REQUESTED && correction OPEN)) && !request.state.isTerminal. Extract the reopenable predicate into one shared helper, for example an InformationRequestReviewReopenPolicy component, used by reopenReview, results() and readable() so the three cannot drift. Frontend: add canReconsider to the reviewer DTO in models.tsx. In ReviewWorkspaceContent.tsx, gate the button on `review.canReconsider`, remove the SETTLED set, and move the button into a small child component if needed to stay under the size limit. Help: rewrite informationRequestReviewArticle.tsx lines 104-107 to say that only the latest review of a current submission can be reconsidered, and only when it was rejected or returned with its correction still open; satisfied reviews and reviews whose correction has closed cannot be. Keep the 'Reconsider' phrase that helpDocs.test.tsx checks for. Tests: extend InformationRequestReviewQueryTransactionTest to assert canReconsider is true for REJECTED and for CHANGES_REQUESTED with an open correction, and false for SATISFIED, a resubmitted correction, a non-latest review and a terminal request. Add a Vitest test for ReviewWorkspaceContent covering button visibility. No migration is needed.

### GA-131: No multiple-correction-cycle history test

- Severity: low. Verification: confirmed. Fix size: S. Audit key: `G167-Tests`.
- Plan reference: Tests to write first / Exit criteria. Plan basis: In force:
- Plan line 3177 lists "Multiple correction cycle history tests" under the P8 tests to write first.
- Plan line 3191, a P8 exit criterion, says "Several review and correction cycles preserve complete history."
- Line 4658 repeats in general terms that corrections and retesting preserve history.

Nothing supersedes or narrows it:
- The Status section (lines 1-80) marks Phase 8 complete but waives only the manual width check.
- There is no user waiver, deferred follow-up or non-goal [truncated]

Current state:

The review, correction and resubmission path is implemented, and it is tested for exactly one cycle: a review returns an item, a correction opens, the package is resubmitted, and a RESUBMISSION review retests the item and settles. No backend test runs two or more correction cycles on the same request. None goes initial review, CHANGES_REQUESTED, correction 1, resubmission, second CHANGES_REQUESTED, correction 2, second resubmission, and then checks the whole history. That history would include both correction rows and their states, the resubmitted package ids, the previousPackageId chain across three packages, the RESUBMISSION priorReviewId chain across three reviews, retest lineage from a later finding to an earlier one, a carried outcome that is itself carried again, and remediation rows from both cycles. Every test that reads corrections calls `.single()`. The SQL contract test only inserts a single RESUBMISSION or APPEAL review with a priorReviewId. The plan's P8 test list and exit criterion for several cycles preserving complete history have no direct test.

Evidence:

- src/test/kotlin/com/docuhyphen/app/api/service/informationrequest/InformationRequestReviewTransactionTest.kt:125-205: the correction test runs one cycle. Line 138 reads the correction with `correctionRepository.findForRequest(...).single()`, and lines 181-182 check that the retest review has kind RESUBMISSION and that its priorReviewId is the first review. The request closes after that one retest.
- The other test methods in that file (lines 69, 208, 256, 277, 313, 365) cover a carried outcome, withdrawal, separation of duties, reconsideration and appeal, and worksheet staleness. None runs a second correction.
- src/test/kotlin/com/docuhyphen/app/api/service/informationrequest/conformance/MultiStageReviewCorrectionRequestConformanceTest.kt:137 and :153 both use `.single()`. Line 154 expects exactly one remediation.
- RepeatableConditionalRequestConformanceTest.kt:213 uses `.single()`.
- InformationRequestTemplateWalkingSkeletonPhase8Test.kt: the tests at lines 93 and 174 each run a [truncated]

Fix outline:

No migration and no production code change should be needed, because this is a test-only gap. Add a test to src/test/kotlin/com/docuhyphen/app/api/service/informationrequest/InformationRequestReviewTransactionTest.kt, named something like `two correction cycles each keep their correction, package, review chain, retest lineage and remediation`, built from the existing helpers (fixture, submitWhole, assign, finding, draft, record, patchNarrative, reviewsOf).

The test should run these steps:
1. Package 1, then the INITIAL review returns item A (finding F1). This opens correction C1. Patch A and submit package 2.
2. The RESUBMISSION review R2 records a retest of F1 with the result not resolved, or adds a new finding F2 on item A, and returns CHANGES_REQUIRED. This opens correction C2. Patch A again and submit package 3.
3. R3 retests F2 as RESOLVED and ends SATISFIED. The request closes.

Then assert:
- correctionRepository.findForRequest returns [C1, C2], both RESUBMITTED, with resubmittedPackageId set to package 2 and package 3.
- Package 3's previousPackageId is package 2, and package 2's is package 1.
- reviewsOf returns 3 reviews: R2's priorReviewId is R1 and R3's is R2. Review numbers run 1, 2, 3 and kinds run INITIAL, RESUBMISSION, RESUBMISSION.
- Remediation rows exist for F1 and F2.
- The earlier findings and decisions are unchanged.
- A second unchanged accepted item B carries its outcome through both resubmissions, so the carried decision of a carried decision stays traceable to R1.

Optionally, add a multi-cycle variant of the SQL contract (review numbers 3+ [truncated]

### GA-132: Separation of duties leaves the SUBJECT role and earlier non-final responders out of 'answering party'

- Severity: low. Verification: confirmed. Fix size: S. Audit key: `G169-P8-T5`.
- Plan reference: P8-T5. Plan basis: Plan lines 3076-3080: Phase 8 design decision 11 says a stage excluding response parties refuses a reviewer who "responded to, submitted, or attested any item of the package, or who is an answering party of the request". The P8-T5 task at 3135-3137 names only preparer and contributor, but decision 11 is the later and broader design text. Line 1795 lists SUBJECT among the respondent-side share roles. I found nothing in the Status section, the Development-Stage Constraint, the non-goals or the [truncated]

Current state:

The check for a review stage that excludes response parties (InformationRequestReviewSeparationPolicy.responsePrincipals) builds the excluded set from three things: each frozen package item's final respondedByPrincipal (the recorder of the frozen response revision), the package submitter, and the package attestations. It then adds every principal acting for an active request party whose role is CONTRIBUTOR, PREPARER or ATTESTOR. Three things are left out:
(1) An active SUBJECT party. Everywhere else in the code SUBJECT counts as a responding role (the clock-notice, first-view and reminder RESPONDING_ROLES sets), and RoleCapabilities gives it INFORMATION_REQUEST_ATTEST under a comment calling every attesting role an "answering role". So a SUBJECT who has not attested can still be assigned to, or decide in, a stage that excludes response parties.
(2) The creators of the Evidence Versions frozen into the package (InformationRequestSubmissionEvidence -> InformationRequestEvidenceVersion.createdByPrincipal*). Neither this set nor the authors of earlier response revisions are counted, so someone who uploaded a file the package froze, but did not record the final response revision, is not excluded.
(3) Parties that have since been revoked. Only findActiveForRequest is consulted, so a revoked party's principals escape unless they happen to be the final recorder, submitter or attestor.
Part (1) is plainly a gap against "answering party of the request". Part (2) follows from "responded to ... any item of the package". Part (3) is less certain, because the plan does not say whether a [truncated]

Evidence:

- src/main/kotlin/com/docuhyphen/app/api/service/informationrequest/InformationRequestReviewSeparationPolicy.kt:38-51 (responsePrincipals uses item.respondedByPrincipal*, submittedBy, attestations and partyRepository.findActiveForRequest filtered by ANSWERING_ROLES)
- same file:78-82: ANSWERING_ROLES = CONTRIBUTOR, PREPARER, ATTESTOR, with no SUBJECT.
- service/auth/authz/RoleCapabilities.kt:247-254: the comment says "Every answering role may attest", and SUBJECT gets READ plus ATTEST.
- InformationRequestClockNoticeHook.kt:55-56, InformationRequestFirstViewService.kt:61-62 and InformationRequestReminderService.kt:138-139: each RESPONDING_ROLES set includes SUBJECT.
- InformationRequestCallerStandingService.kt:73: ATTESTING_ROLES includes SUBJECT.
- InformationRequestSubmissionService.kt:232: respondedByPrincipalKind comes only from the final response's recordedByPrincipalKind.
- model/entity/InformationRequestSubmissionPackage.kt:148-166: InformationRequestSubmissionEvidence has [truncated]

Fix outline:

1. In InformationRequestReviewSeparationPolicy.kt, add InformationRequestShareRoleKey.SUBJECT to ANSWERING_ROLES. It would be cleaner to put one shared RESPONDING_ROLES constant (SUBJECT, CONTRIBUTOR, PREPARER, ATTESTOR) in a small shared object and use it in place of the four duplicated sets.
2. Extend responsePrincipals to include the createdBy principal of every Evidence Version the package froze (load it through the evidence service or query method, not another service's repository). Optionally also include the recorders of every response revision for the package items' occurrences, and the uploaders of artifacts in the package.
3. If the user agrees that revoked parties still count, use a party lookup that includes revoked parties (for example a findForRequest over all statuses) in place of findActiveForRequest.
4. No migration is needed (V151 stays free).
5. Tests: in InformationRequestReviewTransactionTest, refuse assignment and decision for (a) a principal acting for an active SUBJECT party that did not attest, (b) the uploader of a frozen evidence version who did not record the final response, and (c) a revoked contributor, if adopted.
6. Update informationRequestReviewArticle.tsx line 18 if the wording changes, for example to "anyone who answered, uploaded evidence for, submitted, or confirmed the submission, and anyone who is a responding party of the request".

### GA-133: Respondent review results leak remediation records for reviewers-only findings and hidden items

- Severity: low. Verification: confirmed. Fix size: S. Audit key: `G170-Phase`.
- Plan reference: Phase 8 decision 7. Plan basis: Plan line 3058-3059 (Phase 8 decision 7), which says respondents see respondent-visible findings only after settlement and only on items they may view. Lines 3071-3073 define the remediation records that link returning findings. The Status section, the Development-Stage Constraint, the non-goals (304-346) and the later Phase 8 text have nothing that narrows, defers or waives respondent visibility for remediation records.

Current state:

InformationRequestReviewQueryService.results() builds the respondent-facing review result. Findings and comments are filtered correctly: RESPONDENT_VISIBLE only, only after settlement, and only on requirements the caller may view. Remediations are not filtered that way. Line 105 keeps every remediation whose findingId matches any finding of the review, including REVIEWERS_ONLY findings and findings on items the respondent cannot view. InformationRequestReviewDtoMapper.toDto(remediation) (lines 244-248) then sends findingId, remediatedByPackageId and remediatedByItemId to the respondent. RespondentReviewCard.tsx:87-91 shows "{n} findings were addressed by your later submission" using result.remediations.length. So the count, and the finding IDs in the payload, can reveal that reviewers-only findings exist, or that findings exist on items the respondent cannot see. The correction view already avoids this kind of leak with undisclosedItemCount. The remediation list has no equivalent guard, and no test covers it.

Evidence:

src/main/kotlin/com/docuhyphen/app/api/service/informationrequest/InformationRequestReviewQueryService.kt:95-102: visibleFindings and visibleComments are filtered by RESPONDENT_VISIBLE, `requirementId in visible` and `settled`. Line 105: `remediations = remediations.filter { remediation -> findings[review.id].orEmpty().any { it.id == remediation.findingId } }`. This uses all findings, not the visible ones. src/main/kotlin/com/docuhyphen/app/api/model/InformationRequestReviewDtoMapper.kt:139 and 244-248: the respondent DTO includes remediations with findingId, remediatedByPackageId and remediatedByItemId. web-app/src/app/information-requests/review/respondent-review-card/RespondentReviewCard.tsx:87-91: shows the unfiltered count. src/test/kotlin: no test of respondent results that checks remediation filtering.

Fix outline:

In InformationRequestReviewQueryService.results(), compute the visible findings once. Filter `findings[review.id]` by `review.state.settled`, RESPONDENT_VISIBLE and `requirementId in visible`. Use that list for both visibleFindings and remediations: `remediations.filter { r -> visibleFindingIds.contains(r.findingId) }`. Optionally, also drop remediations whose remediatedByItemId points at a resubmitted item whose requirement is not visible. No migration is needed. The frontend needs no change once the payload is filtered, though the card text could be made singular or plural. Add an integration test in the respondent review-results suite. Settle a CHANGES_REQUESTED review that has one RESPONDENT_VISIBLE finding, one REVIEWERS_ONLY finding, and one finding on a requirement the respondent cannot view, then resubmit. Assert that GET results returns exactly one remediation, and that the reviewer-side readable review still returns all three.

### GA-134: Review due instant cannot be set from the UI

- Severity: low. Verification: confirmed. Fix size: S. Audit key: `G171-P8-T1`.
- Plan reference: P8-T1. Plan basis: Plan line 3034 (Decision: an assignment names an optional explicit due instant), lines 3108-3117 (P8-T1/P8-T1b review due dates, explicit due instants), Status lines 40-45 (Phase 8 reviewer UI done), P10-T5 at about line 3631 (reviewer workspaces with stage status). Nothing supersedes or defers it. Phase 9 clocks (about lines 3272-3273) cover only calculated deadlines, which P8-T1 explicitly keeps separate.

Current state:

The backend fully supports an optional explicit review due instant on assignment. The frontend request type AssignInformationRequestReviewerRequest also has an optional dueAt, and the reviewer queue shows it. But AssignReviewerDialog, the only UI that creates assignments, always sends just {stageKey, reviewerPartyId} and has no due-date input. No other component supplies dueAt. The Reviewers panel rows (ReviewAssignmentRow) do not show an assignment's due instant either. So a review due date can only be set through a direct API call, yet the help article says "Reviews assigned to you" lists reviews "with their due dates". Nothing in the plan narrows or defers this: P8-T1 is marked done with explicit dueAt, Phase 8 status says the reviewer UI is done, and P10-T5 adds reviewer workspaces with stage status. Phase 9 clocks cover calculated request deadlines, not the explicit review due instant.

Evidence:

web-app/src/app/information-requests/review/assign-reviewer-dialog/AssignReviewerDialog.tsx:32 onConfirm({stageKey, reviewerPartyId: partyId}) with only Stage and Reviewer ChoiceSelects; web-app/src/app/models/models.tsx:3780-3785 AssignInformationRequestReviewerRequest has dueAt?: string; ReviewAssignmentsPanel.tsx:73 is the only caller of the dialog; grep for "due" under review-assignments-panel returns nothing; ReviewQueueList.tsx:70-73 shows entry.dueAt; backend InformationRequestReviewEndpoint.kt:78 maps body.dueAt, InformationRequestReviewAssignmentService.kt:80-86 checks that dueAt is in the future and stores it; InformationRequestReviewDtoMapper.kt:98,155 exposes it; help informationRequestReviewArticle.tsx:25-26 promises due dates; a reusable shared/date-time-field/DateTimeField.tsx and dateTimeInput.ts already exist and are used by RecordDecisionDialog and PromoteFactDialog.

Fix outline:

Frontend only, no migration needed. (1) AssignReviewerDialog.tsx: add an optional "Due" DateTimeField (id information-request-assign-reviewer-due) using the shared date-time-field and dateTimeInput helpers, turn the local value into an ISO instant, include dueAt only when it is set, and block confirm, or show a hint, when the value is not in the future. This matches the backend's "must be in the future" refusal. (2) ReviewAssignmentRow: show "Due <formatInformationRequestTime(dueAt)>" when assignment.dueAt is present. (3) Tests: add a Vitest test for AssignReviewerDialog showing that dueAt is sent when a time is entered and left out when empty, plus a row rendering test for the due date. (4) Help: update informationRequestReviewArticle.tsx under Reviewers to say that a manager can set an optional due date when assigning. Then run npx tsc --noEmit and the vite build.

### GA-135: Unsafe retry for review findings and comments, which have no ETag and get a new Idempotency-Key per attempt

- Severity: low. Verification: confirmed. Fix size: S. Audit key: `G176-P10-T4`.
- Plan reference: P10-T4. Plan basis: - Plan lines 3625-3630 (P10-T4): "safe retry behavior". Persisted revisions, ETags and 412 are the correctness mechanism.
- Line 3049 and lines 3563-3568: retry under the same Idempotency-Key (the upload example) is the model for commands without a revision.
- Lines 3051-3054: findings are immutable appends, so a duplicate cannot be corrected.
- Nothing in the Status section, the Development-Stage Constraint, or the non-goals (304-346) defers or waives this retry behavior.

Current state:

Recording a review finding or a review comment is an append-only POST. It is protected only by Idempotency-Key replay: the endpoints take no If-Match or expected revision. All three frontend callers create a new crypto.randomUUID() key on every call. These are useInformationRequestReview.recordFinding (the reviewer finding dialog, which stays open after a failure so the user can press it again), useReviewManagement.comment (reviewer comment), and InformationRequestReviewResults (respondent reply on settled reviews, used by both the authenticated and no-auth surfaces). Suppose the server commits the first attempt and the response is lost, for example to a timeout or a network drop. A user retry then sends a different key, and the server records a second immutable finding or comment. Nothing on the server stops a duplicate once the key differs. Other flows in the feature already keep one key per attempt signature (useCreateInformationRequest, useRequestClocks, useOperationsQueue, useAuthorCommandRunner). The review finding and comment flows do not. The other review commands (assign, override, reconsider, recording decisions) also mint a fresh key each time, but they carry an If-Match ETag, so a replay after a commit gets a 412 and does not create a duplicate.

Evidence:

- src/main/kotlin/com/docuhyphen/app/api/resource/informationrequest/InformationRequestReviewResource.kt:260-296: POST /{reviewId}/findings and /{reviewId}/comments take only the IDEMPOTENCY_KEY_HEADER; there is no ifMatch parameter, unlike override at ~252.
- InformationRequestReviewEndpoint.kt:195-240: finding() and comment() pass idempotencyKey only.
- web-app/src/app/information-requests/review/useInformationRequestReview.ts:143: recordInformationRequestReviewFinding(..., crypto.randomUUID()); a failure returns false and the dialog stays open.
- web-app/src/app/information-requests/review/useReviewManagement.ts:83: recordInformationRequestReviewComment(..., crypto.randomUUID()).
- web-app/src/app/information-requests/review/review-results/InformationRequestReviewResults.tsx:93: respondent reply with crypto.randomUUID().
- For contrast, the stable-key pattern: useCreateInformationRequest.ts:50 (a signature-keyed ref), useRequestClocks.ts:64 and useOperationsQueue.ts:103 [truncated]

Fix outline:

This is a frontend-only fix. No migration is needed, and V151 stays free.
1. Extract the existing signature-keyed pattern into a small shared hook, for example web-app/src/app/information-requests/shared/useStableIdempotencyKey.ts. It should return keyFor(signature), which reuses the key while the signature is unchanged, and release(signature), which clears it after a success or a definitive refusal (4xx other than 408 or 429).
2. useInformationRequestReview.recordFinding: build the signature from the JSON of the finding request plus reviewId, use keyFor(signature), and release it on success.
3. useReviewManagement.comment: do the same with the comment request plus reviewId.
4. InformationRequestReviewResults reply: do the same with findingId, submissionItemId and body. This one change covers both the authenticated and no-auth surfaces.
5. Optionally refactor useRequestClocks and useOperationsQueue onto the shared hook.
6. Tests (Vitest):
   - For each hook or component, fail the first call with a network error, retry, and assert that the second call uses the same Idempotency-Key.
   - Change the input and assert that the key changes.
   - After a success, assert that the next identical submission gets a new key.
7. On the backend, add or extend a test asserting that replaying a finding and a comment with the same key returns the original record and does not create a second row.
8. No help-docs change is needed (there is no user-visible behavior change beyond not creating duplicates).

### GA-136: No responsive-state tests; respondent evidence replacement and preview flows untested

- Severity: low. Verification: confirmed. Fix size: M. Audit key: `G177-Phase`.
- Plan reference: Phase 10 Tests to write first / P10-T8. Plan basis: Plan lines 3671-3678 (Tests to write first) cover evidence upload, replacement, preview and error tests, plus keyboard, focus, accessible name and responsive-state tests, plus responsive states for the Exchange tab. Line 3704 is the exit criterion that responsive and accessibility tests pass. Lines 3689-3694 hold the session exception from 2026-09-28, which waives only the manual 1440/1024/768/360 width checks and not the automated tests. Line 845 records the same waiver as limited to the [truncated]

Current state:

No Information Request frontend test covers responsive layout states. A grep of every *.test.* file under web-app/src/app/information-requests for matchMedia, innerWidth, responsive, viewport, useMediaQuery, narrow or compact finds nothing, and that includes ExchangeInformationRequestsTab.test.tsx. Responsiveness exists only as @media rules in the *Styles.tsx files (24 of them), and no test asserts anything about them.

On the respondent evidence side, RequirementEvidencePanel.test.tsx does test upload, stale ETag, retry, withdraw, progress and the upload-unavailable case. For replacement and preview, though, it only checks that the preview, download, replace and withdraw buttons render (line 114). It also checks that the replace button is absent when upload is unavailable (line 225). No test clicks Replace or Preview, and no test checks that commands.replace or commands.open is called, which RequirementEvidencePanel.tsx calls at lines 82 and 84. Replace errors and preview errors are not tested at the component level either. The only coverage is at the transport layer, in services/__tests__/informationRequestEvidenceService.test.ts at lines 78 and 113.

Evidence:

- web-app/src/app/information-requests/requirement-evidence/requirement-evidence-panel/RequirementEvidencePanel.test.tsx:24,26 define the replace and open mocks, which are never asserted. Line 114 only checks that the action buttons exist. Line 225 checks that the replace button is absent when upload is unavailable.
- RequirementEvidencePanel.tsx:82 wires onReplace to replace(artifact, file), and line 84 wires onOpen to open(artifact, version, use).
- services/__tests__/informationRequestEvidenceService.test.ts:78 (replace) and :113 (preview blob) are the only replace and preview tests, and both are at the transport level.
- A grep for matchMedia, innerWidth, responsive, viewport, useMediaQuery, narrow and compact across app/information-requests/**/*.test.* returns 0 hits. The only responsive handling is 24 @media occurrences in the IR Styles files, and none of them is tested.

Fix outline:

No backend change and no migration are needed. V151 stays free.

1. In RequirementEvidencePanel.test.tsx, add these tests:
   - Choose a file through the `${row}-replace` file input. Assert that commands.replace is called with the artifact and the file under the artifact revision, and that the list refreshes.
   - A replace that returns STALE or an error shows the conflict or error MessageBar and keeps the chosen file.
   - Clicking `${row}-preview` and `${row}-download` calls commands.open(artifact, version, "preview"/"download").
   - A failed preview shows an error message.
2. Add responsive-state tests for the IR workspaces and ExchangeInformationRequestsTab. jsdom does not evaluate @media rules, so choose one of two approaches:
   - Stub window.matchMedia (or use a shared test helper) and assert on components that switch layout, for example a list collapsing into cards or a drawer.
   - Or render at a mocked narrow width and assert that the key controls and IDs stay present and reachable at mobile widths, with no desktop-only controls.
   If no component switches layout in JS today, the choice is between two options: add a small useMediaQuery-driven layout toggle that can be tested, or accept a snapshot of the makeStyles class output that checks the @media rules exist.
3. Run npm test, npx tsc --noEmit and npm run lint in web-app.

### GA-137: Reading notice content and endpoints leaves no access history

- Severity: low. Verification: confirmed. Fix size: S. Audit key: `G185-P9-T8`.
- Plan reference: P9-T8. Plan basis: P9-T8, plan lines 3392-3408: "Treat recipient endpoints and retained content as sensitive: apply compartment authorization, protection at rest..., redaction, access history, privacy rules, and retention." The "Done" note (3406-3408) mentions only masked endpoints, not access history. Lines 923, 3879 and 4188 list access history among the Phase 9 outcomes.

Nothing narrows or defers it:
- Design decision 10 (3289-3297) and the refinement at 3334-3336 only settle endpoint masking.
- Decision 14 [truncated]

Current state:

GET /information-requests/{id}/notices (InformationRequestNoticeResource.list, which calls InformationRequestNoticeQueryService.notices) checks INFORMATION_REQUEST_VIEW_OPERATIONS and then returns each immutable OutboundNotice: rendered subject and full rendered body, the masked recipient endpoint, the content hashes and the Sequence allocations. It records nothing in access history. There is no audit event type for reading a notice. The catalog has only NOTICE_RENDER and NOTICE_DELIVER for notices. Record exports, by contrast, audit every read with INFORMATION_REQUEST_EXPORT_READ. So retained notice content can be read without a trace, which falls short of P9-T8's "access history" requirement for sensitive retained content.

One part of the claim is not a gap. It also says the rendered-content hash is returned without being recomputed on read. The plan only requires hash verification on read for large content kept in a content-addressed retained object. Notices keep their content inline in the database row, so that requirement does not apply here.

Evidence:

- src/main/kotlin/com/docuhyphen/app/api/service/informationrequest/InformationRequestNoticeQueryService.kt:17-21: notices() only calls gate.authorizeRequest(... INFORMATION_REQUEST_VIEW_OPERATIONS ...) and then states.views(requestId). There is no audit call.
- src/main/kotlin/com/docuhyphen/app/api/resource/informationrequest/InformationRequestNoticeResource.kt:23-33: the GET handler maps every view to a DTO.
- src/main/kotlin/com/docuhyphen/app/api/model/InformationRequestNoticeDtoMapper.kt:21-28: the DTO exposes maskedEndpoint, renderedSubject, renderedBody, renderedContentHash and sourceContentHash.
- src/main/kotlin/com/docuhyphen/app/api/service/audit/catalog/AuditEventType.kt:318-324: the only notice events are INFORMATION_REQUEST_NOTICE_RENDER and INFORMATION_REQUEST_NOTICE_DELIVER. There is no NOTICE_READ or NOTICE_VIEW. INFORMATION_REQUEST_EXPORT_READ exists at line 324. CATALOG_VERSION = 29 is at line 375.
- [truncated]

Fix outline:

1. Add INFORMATION_REQUEST_NOTICE_READ("information_request.notice.read", AuditCategory.INFORMATION_REQUEST) to AuditEventType.kt. Bump CATALOG_VERSION from 29 to 30 and update AuditEventTypeTest to match. No migration is needed, because event keys are not DB-constrained. V151 stays free.
2. In InformationRequestNoticeQueryService.notices(), make it @Transactional. After authorization, record one audit event per read. Carry explicit canonical principal and request owner scope, the same way InformationRequestRecordExportService.audit does. Use allow-listed payload keys only: request id, notice count, and notice ids or rendered hashes. Never put the endpoint or content in the payload. Use a unique idempotency key: key|requestId|principalId|instant.
3. Put the audit helper in a small shared collaborator rather than duplicating the export service's private method. This respects SOLID.
4. Add the new key to the audit projection allow-list, so GET /information-requests/{id}/audit-events shows it.
5. Tests:
   - A service or resource test that asserts one NOTICE_READ audit row per GET, with the actor principal and without the endpoint or body in the payload.
   - An audit-projection test that asserts it appears in the request's audit history.
6. Optional help-docs update: in informationRequestsSection or the matching article, note that viewing notice history is recorded in the audit history.

### GA-138: Disposal retry failures are not audited

- Severity: low. Verification: partial. Fix size: S. Audit key: `G186-P9-T9`.
- Plan reference: P9-T9. Plan basis: P9-T9 plan lines 3428-3429 say "Every claim, denial, retry, database deletion, and object deletion is audited and reproducible". Phase 9 design decision 11 (lines 3298-3310, ending at 3309-3310) narrows this to "Every claim, denial, object deletion, and finalization is audited", so retry is left out. Line 3487 in the tests list asks for an "idempotent, audited, retryable path" for purge. Line 4002 (health diagnostics) reports stalled disposal claims, which covers operational visibility but not [truncated]

Current state:

When a disposal attempt fails, no audit event is written. This covers a stored object that cannot be deleted and a finalization that fails. InformationRequestDisposalService.process logs a warning and calls RecordDisposalService.recordAttemptFailure. That method only bumps attempt_count, last_error_code (the exception's simple class name) and claim_revision on the claim row. The AuditEventType catalog has only RECORD_DISPOSAL_CLAIMED, DENIED, OBJECT_DELETED and FINALIZED, with no retry or attempt-failed type. A retry that later succeeds is still audited, through OBJECT_DELETED per object (keyed by object id) and FINALIZED. The failed attempts leave only the latest error code and a counter on the mutable claim row. That row is exposed as attemptCount/lastErrorCode in RecordPreservationDtos, so the history of each failed attempt cannot be rebuilt. The P9-T9 task line (3428-3429) lists "retry" among the audited actions. The Phase 9 design decision 11 (3298-3310), which governs the implementation, narrows this to "claim, denial, object deletion, and finalization" and leaves retry out. So the gap is real against the literal task text but narrower than the claim says.

Evidence:

src/main/kotlin/com/docuhyphen/app/api/service/informationrequest/InformationRequestDisposalService.kt:95-99 (object delete failure: logger.warn + disposals.recordAttemptFailure, no audit call) and :106-112 (finalize failure: same); audit calls exist only at :50 (DENIED), :73 (CLAIMED), :120 (FINALIZED), :144 (OBJECT_DELETED). src/main/kotlin/com/docuhyphen/app/api/service/recordpreservation/RecordDisposalService.kt:94-102 recordAttemptFailure only mutates attemptCount/lastErrorCode/claimRevision. src/main/kotlin/com/docuhyphen/app/api/service/audit/catalog/AuditEventType.kt:360-363 has only four RECORD_DISPOSAL_* types. src/main/kotlin/com/docuhyphen/app/api/model/dto/RecordPreservationDtos.kt:97-98 exposes attemptCount/lastErrorCode. src/test/kotlin/.../InformationRequestDisposalTransactionTest.kt:147 asserts attemptCount only, with no audit assertion for the failure.

Fix outline:

1. Add RECORD_DISPOSAL_ATTEMPT_FAILED("record.disposal.attempt_failed", AuditCategory.AUDIT_GOVERNANCE) to AuditEventType.kt. Update AuditEventTypeTest.
2. In InformationRequestDisposalService.process, in the same requiringNew transaction as recordAttemptFailure, use the claim that call returns to call audit.disposal(RECORD_DISPOSAL_ATTEMPT_FAILED, owner, TARGET, resourceId, SYSTEM, ...). Include these metadata keys: claimId, stage (OBJECT_DELETION with documentVersionId, or FINALIZATION), errorCode, attemptCount. Use the idempotency key "record.disposal.attempt_failed|<claimId>|<attemptCount>". Pull this into a private helper so both failure branches share it.
3. No migration is needed, because audit event keys are not constrained in SQL (V151 stays free).
4. Tests: extend InformationRequestDisposalTransactionTest so a failing storage delete and a failing finalize each write exactly one attempt-failed audit event with the right attemptCount and stage. A later successful retry should add OBJECT_DELETED and FINALIZED without duplicates.
5. Optionally, make design decision 11 and the help article for disposal status mention retry auditing.

### GA-139: No concurrent notice claim-race test and no ordered communication-timeline test

- Severity: low. Verification: confirmed. Fix size: S. Audit key: `G188-Tests`.
- Plan reference: Tests to write first. Plan basis: Plan lines 3465-3468 (Phase 9 Tests to write first) put the requirement in force. I searched the Status section, the Development-Stage Constraint, the waivers and the non-goals, and nothing defers or waives these tests. The only waivers are the manual width and UX checks (lines 7, 845, 3695).

Current state:

The production code guards the claim race and orders notice history. InformationRequestNoticeClaimRepository.claim uses INSERT ... ON CONFLICT (notice_intent_id) DO NOTHING and returns executeUpdate()==1. InformationRequestNoticeDispatcher.claimAndRender returns null when the claim is lost. The notice and attempt repositories order by renderedAt,id and by attemptedAt,attemptNumber. No test exercises either behaviour. No test runs claimAndRender or InformationRequestNoticeWorker.dispatch* from two threads against the same intent and then checks for one claim row, one outbound notice, one sequence allocation and one sent message. The only claim coverage is the SQL-level primary-key refusal in the migration contract test, run one statement at a time on a single connection. No test calls the ordered notice or attempt read models (findForRequest / findForRequests / attempt findForRequest) or InformationRequestNoticeStateReader and checks that notices and delivery attempts come back in chronological order. Current tests only check counts per delivery state or notices.size.

Evidence:

plans/DOCUMENT-DRIVEN-INFORMATION-REQUESTS-IMPLEMENTATION-PLAN.md:3465-3468 lists "ordered communication-timeline tests" and "claim race" under Phase 9 "Tests to write first". src/test/.../service/informationrequest/InformationRequestNoticeTransactionTest.kt has 4 tests (lines 68, 103, 129, 148). None of them is concurrent or checks ordering. src/test/.../migration/InformationRequestOutboundNoticeContractTest.kt:31-61 only checks that a second sequential INSERT into information_request_notice_claim is refused by information_request_notice_claim_pkey. A grep of src/test for claimAndRender returns nothing. The concurrency tests that exist (CountDownLatch/Executors) are InformationRequestPartyConcurrencyTransactionTest, QuotaConcurrencyTest, ParentTerminationConcurrencyPostgresContractTest and similar; none touches notices. Production code: repository/informationrequest/InformationRequestNoticeRepositories.kt claim() uses ON CONFLICT DO NOTHING, with ORDER BY notice.renderedAt, notice.id [truncated]

Fix outline:

Add tests only. No migration is needed (V151 stays free). (1) In InformationRequestNoticeTransactionTest, or a new InformationRequestNoticeClaimConcurrencyTest using the Postgres test resource, create an overdue notice intent. Start N threads (a CountDownLatch start gate plus an ExecutorService) that each call dispatcher.claimAndRender(intentId, now) in QuarkusTransaction.requiringNew(). Assert that exactly one call returns a notice and the rest return null. Assert exactly one information_request_notice_claim row, one information_request_outbound_notice row and one sequence allocation per key. After the worker delivers, assert RecordingInformationRequestNoticeSender received exactly one message. Optionally, also race two InformationRequestNoticeWorker.dispatchForRequest calls. (2) Add an ordered-timeline test. Produce a reminder notice and an overdue notice at distinct fixed clock instants, force one failed attempt and then a retry. Read noticeRepository.findForRequest, attemptRepository.findForRequest and InformationRequestNoticeStateReader.statesForRequests. Assert that the notice order follows renderedAt and that attempts come back in attemptedAt/attemptNumber order with ascending attempt numbers per notice. If the request-detail DTO or resource exposes notice history, add a resource contract assertion on its order as well.

### GA-140: Capability discovery omits the plan quotas and reports new work as available when the open-request allowance is used up

- Severity: low. Verification: confirmed. Fix size: M. Audit key: `P12#1`.
- Plan reference: P12. Plan basis: Decision 6 at plan lines 3988-3993 lists "the plan quotas" and the stable reason when new work is unavailable. Decision 9 at lines 4009-4021 sets the quota values and says "Open-request count answers to the live plan at creation". P12-T4a at about line 4084 says the listing states why creation is unavailable. P12-T4c at line 4090 is marked done. Decision 12 (lines 4031-4034) covers the pricing and billing quotas, which is a separate requirement. Nothing in the Status section, the [truncated]

Current state:

GET /information-request-capabilities returns ownerType, planCode, subscriptionStatus, enforcementMode, featureIncluded, newWorkAvailable, newWorkUnavailableReason, operationallySuspended, typedAnswersAvailable, personalTemplatesAvailable, assignedWork and holdsRequests. It returns no plan quotas: no open-request cap, acting-party cap, per-request file or byte allowance, or committed evidence cap. Decision 6 requires these. The open-request cap is checked only when a request is created, in SubscriptionAccessService.requireInformationRequestCapacity, which InformationRequestEntitlementGuard calls. ExecutionStandingService.newWorkUnavailableReason looks only at suspension, enforcement mode, subscription mutability and feature inclusion. InformationRequestStandingReason has no value for a reached limit. A Personal owner with 25 open requests under an evaluating enforcement mode therefore gets newWorkAvailable=true from capabilities and canCreate=true from the Exchange listing. The create call then fails with PLAN_LIMIT_REACHED. The quotas can be seen only through the billing subscription DTO (maxOpenInformationRequests in SubscriptionDtos), which reports the viewer's own subscription, not the owner scope that capabilities resolves.

Evidence:

model/dto/InformationRequestCapabilityDtos.kt:11-24 (no quota fields). service/informationrequest/InformationRequestCapabilityService.kt:22-37 (builds capabilities from ownerStanding only, no limits). service/informationrequest/InformationRequestExecutionStandingService.kt:67-84 (newWorkUnavailableReason never reads subscription.limits). model/informationrequest/InformationRequestExecutionStandingModels.kt:18-26 (reason enum: FEATURE_NOT_INCLUDED, TRIAL_ENDED, SUBSCRIPTION_PAST_DUE, SUBSCRIPTION_CANCELED, SUBSCRIPTION_SUSPENDED, EXECUTION_GRANT_REVOKED; no limit reason). service/informationrequest/InformationRequestExchangeSummaryService.kt:64-65 (canCreate = creationPermitted && owner.newWorkAvailable). service/subscription/SubscriptionAccessService.kt:203-229 (the open-count check exists only as a create-time requirement). service/subscription/PlanCatalog.kt:68 (Personal maxOpenInformationRequests = 25). service/informationrequest/InformationRequestEntitlementGuard.kt:40 (the only [truncated]

Fix outline:

1) Add a quotas model in model/informationrequest, for example InformationRequestPlanQuotas with maxOpenRequests, openRequests, maxActingPartiesPerRequest, maxEvidenceFilesPerRequest, maxEvidenceBytesPerRequest and maxCommittedEvidenceBytes, filled from EffectiveSubscription.limits. Add a matching quotas field to InformationRequestCapabilitiesDto and its mapper. 2) Add OPEN_REQUEST_LIMIT_REACHED to InformationRequestStandingReason. In InformationRequestExecutionStandingService.ownerStanding, when the mode refuses denied requests and maxOpenInformationRequests is not null, count the owner's open requests through a method on the request service (not by calling another service's repository directly) and return that reason when open >= limit. Put it after the feature check. Do not apply the reason to standingOf for issued requests: it limits new drafts only. Either make it drive only newWorkAvailable, or give NEW_WORK_UNAVAILABLE drafts a separate path, so existing drafts are not blocked from issuance by the count. That needs care, because a draft counts toward the open total. 3) canCreate and creationUnavailableReason in ExchangeSummaryService then follow from this automatically. 4) Frontend: add the new fields to web-app/src/app/models/models.tsx and the capability service types. Show the reason text in the standing notice or reason label mappings, and optionally show quotas on the Settings Information Requests tab. 5) Tests: a capability service test where a Personal owner at 25 open gets newWorkAvailable=false with reason OPEN_REQUEST_LIMIT_REACHED and the quotas are [truncated]

### GA-141: Request detail, operations queue and review queue projections carry no execution standing

- Severity: low. Verification: partial. Fix size: M. Audit key: `P12#3`.
- Plan reference: P12. Plan basis: Decision 5 at plan lines 3981-3986 requires that every request projection state its execution standing, with the reason code, and that the UI show it. The subtask P12-T4a at lines 4084-4087, split on 2026-09-30 and checked, narrows this to "every workspace and Exchange listing row states its execution standing (the reason only to a caller who manages the request)". P12-T4e at lines 4092-4096 lists "standing notices and badges" without naming the operations or review pages. The Latest [truncated]

Current state:

Execution standing is computed by InformationRequestExecutionStandingService and exposed on only two projections: the Exchange listing summary row (InformationRequestSummaryDto.executionStanding) and the response/author workspace (InformationRequestResponseWorkspaceDto.executionStanding). The UI shows it in the Exchange tab row badge, the author workspace and the respondent workspace. It is missing from InformationRequestDto (GET /information-requests/{id} and the list), InformationRequestOperationsRowDto (operations queue), and the review queue DTOs in InformationRequestReviewDtos.kt. The operations list and detail pages only show the scope-level InformationRequestScopeNotice (from capability discovery), never the individual request's standing. This falls short of Decision 5 ("Every request projection states an execution standing ... and the UI shows it"). It is narrower than a full gap, though: the P12-T4a subtask the plan actually scheduled and checked off (line 4084-4086) only requires "every workspace and Exchange listing row", and the code meets that. So what is left is a mismatch between the design decision and the narrowed subtask. It is not a regression against the subtask as written.

Evidence:

grep executionStanding over src/main: only model/dto/InformationRequestSummaryDtos.kt:26 and model/dto/InformationRequestResponseWorkspaceDtos.kt:19 carry the field. Mappers: InformationRequestSummaryDtoMapper.kt:36 and InformationRequestResponseWorkspaceDtoMapper.kt:34. Producers: InformationRequestExchangeSummaryService.kt:61 (forParticipant for non-managers) and InformationRequestResponseWorkspaceService.kt:115. model/dto/InformationRequestOperationsDtos.kt:16-34 (InformationRequestOperationsRowDto) has no standing field. model/dto/InformationRequestDtos.kt:18 (InformationRequestDto) has none. model/dto/InformationRequestReviewDtos.kt has none. Frontend: InformationRequestOperationsDetail.tsx:49 and InformationRequestOperations.tsx:41 render only InformationRequestScopeNotice. ExecutionStandingNotice is used only in InformationRequestAuthorWorkspace.tsx:53 and RespondentWorkspaceBody.tsx:32, and executionStandingBadge only in InformationRequestSummaryRow.tsx:28.

Fix outline:

Backend:
1. Add `executionStanding: InformationRequestExecutionStandingDto` to InformationRequestDto, InformationRequestOperationsRowDto and the review queue entry DTO in InformationRequestReviewDtos.kt.
2. Resolve the standing through InformationRequestExecutionStandingService in the owning services, not in the resources:
   - the request read/list service
   - the operations queue service
   - the review queue service
3. For the list and queue services, add a batch `standingsOf(requests)` so owner standing and grants are resolved once per owner and not once per row.
4. Apply `forParticipant()` when the caller does not manage the request, the same way InformationRequestExchangeSummaryService does.
5. Map the standing through InformationRequestExecutionStandingDtoMapper in the existing toDto mappers.

Frontend:
1. Add `executionStanding` to the matching interfaces in models.tsx.
2. Render ExecutionStandingNotice in InformationRequestOperationsDetail.tsx, next to the scope notice.
3. Show executionStandingBadge in the operations table rows and the review queue rows.

Tests:
- Service tests for each projection covering the managing and non-managing reason visibility.
- A resource contract test asserting the field on GET /information-requests/{id} and the operations page.
- Vitest tests for the operations detail notice and the row badges.

Help: update the operations and review help articles.

No migration is needed.

Decision needed. Recommended default: Carry execution standing on every request projection, including the operations queue, operations detail and review queue.

### GA-142: Revocation guidance describes an admin action that does not exist and a recovery path the code refuses

- Severity: low. Verification: confirmed. Fix size: S. Audit key: `P12#4`.
- Plan reference: P12. Plan basis: Status line 72-73 says an administrative surface for explicit grant revocation remains a future scoping decision. P4-T7d (lines 1951-1963) says revoke has no REST caller yet and the admin surface is left for a future session. Phase 12 design decision 5 (lines 3982-3987) says revocation refuses mutations with a stable reason. P12-T9 (line 4138) requires complete operator guidance and support diagnostics. Together these defer the admin action, which narrows claim 1. Nothing supersedes the [truncated]

Current state:

InformationRequestExecutionGrantService.revoke(requestId, reason) exists but has no production caller: no resource, platform-admin service, or worker invokes it, and only unit tests exercise it. A grant can only become revoked through a direct database write, which the V108 trigger allows once. The plan's Status (line 73) and P4-T7d (lines 1960-1963) explicitly defer the administrative surface, so the missing action is not a gap in itself. Still, operator-guide.md:19 says revocation is set by a "platform action on the grant", and the help overview article (line 40) and executionStandingText.ts:19 tell users "A platform administrator stopped this request". Neither text says that no such action exists yet or that it is only a manual database operation. The guidance that really breaks is recovery-runbook.md:97-98, which says the owner "creates a replacement request, for example by superseding it". The code refuses every one of those paths. InformationRequestLifecycleService.mutate() is shared by ISSUE, CANCEL and SUPERSEDE, and at line 221 it calls requireGrantNotRevoked, which throws EXECUTION_GRANT_REVOKED. InformationRequestSuccessorService.follow (line 142) calls gate.requireContinuationEntitlement, and InformationRequestMutationGate.kt:62 throws the same code. So a revoked request can never be cancelled, superseded or followed up. It stays non-terminal indefinitely. The only recovery that works is authoring a brand-new request, which the runbook does not mention. No test covers the documented recovery path.

Evidence:

src/main/kotlin/com/docuhyphen/app/api/service/informationrequest/InformationRequestExecutionGrantService.kt:96 (fun revoke). Grep for grants/executionGrants/executionGrantService .revoke( in src/ finds no matches, so there is no production caller. InformationRequestLifecycleService.kt:91-148 route ISSUE, CANCEL and SUPERSEDE through mutate(). Lines 209-222 call requireGrantNotRevoked when a grant exists, and lines 275-283 throw EXECUTION_GRANT_REVOKED. InformationRequestSuccessorService.kt:141-142 call gate.requireMutation(CREATE_SUCCESSOR) and gate.requireContinuationEntitlement. InformationRequestMutationGate.kt:53-66 throw EXECUTION_GRANT_REVOKED when grant.revokedAt != null, and InformationRequestEvidenceGate.kt:103-116 does the same. docs/information-requests/operator-guide.md:19 says "platform action on the grant". docs/information-requests/recovery-runbook.md:96-99 says "Its owner creates a replacement request, for example by superseding it". [truncated]

Fix outline:

Docs-only minimum (S):
(1) operator-guide.md:19: change "How it is set" to say there is no product surface yet. Revocation is a controlled, write-once manual operation on request_execution_grant (revoked_at/revoked_reason), or InformationRequestExecutionGrantService.revoke, pending a future admin surface.
(2) recovery-runbook.md:97-98: remove the supersede suggestion. Say that a revoked request cannot be cancelled, superseded or followed up. The owner authors a new request in the Exchange, and the revoked one stays non-terminal and readable. Also note that it keeps its open-request count and committed evidence allowance, and blocks Exchange ending if it gates closure.
(3) executionStandingText.ts:19 and informationRequestsOverviewArticle.tsx:40: use neutral wording, for example "The platform stopped this request."

Behavioural fix, if chosen (M): let CANCEL, and optionally SUPERSEDE, proceed under a revoked grant, because cancelling only reduces work. In InformationRequestLifecycleService.mutate, skip requireGrantNotRevoked when mutation == CANCEL, and still check operational suspension. Add InformationRequestLifecycleServiceTest cases: cancel succeeds on a revoked grant, and issue, supersede and follow-up still throw EXECUTION_GRANT_REVOKED. Add a conformance test showing the revoked-then-cancelled request frees its countOpenForOwner place and its committed evidence bytes. No migration is needed (V151 stays free). Run npx tsc --noEmit after the help and text edits.

Decision needed. Recommended default: Let the owner cancel or supersede a request whose grant is revoked so it reaches a terminal state, and correct the guidance.

### GA-143: Contact-code lockout and challenge exhaustion log no abuse marker

- Severity: low. Verification: confirmed. Fix size: S. Audit key: `P12#5`.
- Plan reference: P12. Plan basis: Plan line 4017-4021 (decision 10: "every abuse refusal logs a stable marker") and 4114-4117 (P12-T5d checked done, including stable INFORMATION_REQUEST_ABUSE_REFUSED markers and the alarm). No later text narrows the marker requirement to rate-limit refusals only; the non-goals and Development-Stage Constraint do not affect it.

Current state:

The only callers of InformationRequestAbuseLog.refused are InformationRequestNoAuthRateLimit (per-address NO_AUTH_CHALLENGE_RATE / NO_AUTH_SESSION_RATE), InformationRequestEvidenceIntake (EVIDENCE_UPLOAD_LIMIT), InformationRequestReminderService (REMINDER_COOLDOWN) and InformationRequestRecordExportService (EXPORT_DAILY_CEILING). InformationRequestContactProofService refuses per-link contact-code abuse in four places without any marker: throwIfOtpLocked (CONTACT_PROOF_LOCKED), the challenge-count ceiling in issueChallenge (CONTACT_PROOF_CHALLENGE_LIMIT), the lockout being set in registerOtpFailure after 5 bad codes, and the use-limit refusal in resolveBootstrapLink (ACCESS_LINK_EXHAUSTED). The InformationRequestAbuseControl enum has no value for these controls. So contact-code probing that stays under the per-address rate (10 challenges / 20 sessions per minute per address, or spread across addresses) is never counted by the InformationRequestAbuseRefusal metric filter, even though the alarm description and the recovery runbook both say the alarm catches contact-code probing. Per-address rate-limit refusals on the same endpoints are logged, so the gap is limited to the per-link controls.

Evidence:

src/main/kotlin/com/docuhyphen/app/api/service/informationrequest/InformationRequestContactProofService.kt:44-51 (challenge limit throws CONTACT_PROOF_CHALLENGE_LIMIT, no log), :161-166 (ACCESS_LINK_EXHAUSTED, no log), :180-189 (throwIfOtpLocked throws CONTACT_PROOF_LOCKED, no log), :195-204 (registerOtpFailure sets lockout silently); InformationRequestAbuseLog.kt:15-18 refused(); grep of refused( callers: InformationRequestNoAuthRateLimit.kt:31, InformationRequestEvidenceIntake.kt:187, InformationRequestReminderService.kt:88, InformationRequestRecordExportService.kt:231 only; model/informationrequest/InformationRequestAbuseModels.kt:6-13 enum has only NO_AUTH_CHALLENGE_RATE, NO_AUTH_SESSION_RATE, REMINDER_COOLDOWN, EXPORT_DAILY_CEILING, EVIDENCE_UPLOAD_LIMIT; infra/cloudformation.yml:1145-1157 alarm description "such as contact-code probing"; docs/information-requests/recovery-runbook.md:34-36 says the alarm usually means probing of contact codes; [truncated]

Fix outline:

1) Add enum values to InformationRequestAbuseControl (model/informationrequest/InformationRequestAbuseModels.kt): CONTACT_PROOF_LOCKOUT, CONTACT_PROOF_CHALLENGE_LIMIT, ACCESS_LINK_USE_LIMIT. 2) In InformationRequestContactProofService: call InformationRequestAbuseLog.refused(CONTACT_PROOF_LOCKOUT, "link=${shareLink.id}") in throwIfOtpLocked before throwing and in registerOtpFailure when the lockout starts; refused(CONTACT_PROOF_CHALLENGE_LIMIT, "link=...") before the challenge-limit throw; refused(ACCESS_LINK_USE_LIMIT, "link=...") before the ACCESS_LINK_EXHAUSTED throw (only when enforceUseLimit). Use the link id, never the raw token or the contact email. 3) Tests: extend InformationRequestContactProofService tests (or add a log-capture test) to confirm each refusal emits the marker line with the right control; extend InformationRequestAbuseLogTest for the new controls. No migration or infra change needed, since the existing metric filter matches the marker prefix. Optionally list the per-link controls in docs/information-requests/recovery-runbook.md and the abuse controls help article.

### GA-144: No concurrency test for the committed-evidence reservation at issuance

- Severity: low. Verification: confirmed. Fix size: S. Audit key: `P12#6`.
- Plan reference: P12. Plan basis: Plan line 4036 (Decision 13: "concurrency tests for the new reservation kinds") and lines 4111-4112 (P12-T5b: both reservations serialized on the owner's subscription row) put the requirement in force. Lines 4134-4136 (P12-T8 marked Done: "volume and concurrency tests") and line 4698 (Latest Implementation Result: "committed evidence under the owner lock") claim completion but do not waive or defer it. The plan search found nothing that narrows or supersedes it.

Current state:

The code serializes both reservations on the same owner subscription row lock. requireInformationRequestCapacity and requireCommittedEvidenceCapacity in SubscriptionAccessService both call lockAllowances. However, the only PostgreSQL race test, InformationRequestQuotaConcurrencyTest, has a single test. That test races entitlementGuard.requireRequestCreation for the last open-request place. No test races two concurrent issuances (issueGrant, which calls requireCommittedEvidenceCapacity and then grantRepository.insertNow) for the owner's last committed-evidence capacity. Every other test that uses committed evidence is single-threaded or mocked: SubscriptionAccessServiceTest, InformationRequestExecutionGrantServiceTest, InformationRequestQuotaUsageTest, InformationRequestPersonalOwnerTest and InformationRequestVolumeTest. None of them uses futures, latches or threads. The shared lockAllowances path is exercised indirectly by the open-request race. The committed-evidence path itself is not, including committedEvidenceBytes() being evaluated after the lock is taken so it sees the other transaction's committed grant. So Decision 13's "concurrency tests for the new reservation kinds" is only half covered.

Evidence:

src/test/kotlin/com/docuhyphen/app/api/service/informationrequest/InformationRequestQuotaConcurrencyTest.kt: the single @Test is "two creations racing for a Personal owner's last open request place cannot both take it". It calls only entitlementGuard.requireRequestCreation.
src/main/kotlin/com/docuhyphen/app/api/service/subscription/SubscriptionAccessService.kt:230-253: requireCommittedEvidenceCapacity calls lockAllowances(context), then committedBytes(), then checks committed + reserved > limit.
src/main/kotlin/com/docuhyphen/app/api/service/informationrequest/InformationRequestExecutionGrantService.kt:~57: issueGrant calls requireCommittedEvidenceCapacity { grantRepository.committedEvidenceBytes(...) } and then insertNow.
Searching src/test for committed-evidence usage finds only single-threaded or mocked tests: SubscriptionAccessServiceTest:356-377, InformationRequestExecutionGrantServiceTest:111-116, conformance/InformationRequestQuotaUsageTest, InformationRequestPersonalOwnerTest [truncated]

Fix outline:

Add a second @Test to InformationRequestQuotaConcurrencyTest; no migration is needed. Set up a Personal owner (PERSONAL plan, ACTIVE, enforcement mode that refuses) whose existing grants leave committed evidence exactly one per-request allowance below maxCommittedEvidenceBytes. Seed request_execution_grant rows for open requests with evidence_byte_allowance so committedEvidenceBytes = limit - allowance. Create two DRAFT requests on the owner's Exchange. In thread 1, inside QuarkusTransaction.requiringNew(), call executionGrantService.issueGrant(request1, exchange) (or the issuance command service), count down a latch and sleep HOLD_MILLIS while holding the lock. In thread 2, after the latch, call issueGrant(request2, exchange) in its own transaction. Assert that the first succeeds and the second fails with SubscriptionDenialException PLAN_LIMIT_REACHED. Assert that exactly one new request_execution_grant row exists for the owner. Optionally add an organization-owner variant to cover the organization subscription row lock.

### GA-145: No test that record exports stay available under suspension, revocation or lapse

- Severity: low. Verification: confirmed. Fix size: S. Audit key: `P12#7`.
- Plan reference: P12. Plan basis: Plan lines 4076-4081 (P12-T4): "separate, explicit, auditable states with tested effects ... None silently hides retained records or permitted exports from authorized readers." The subtasks at 4082-4097 (P12-T4a-f) cover reads, the workspace and listings, assignment, capabilities, health, UI and help. None of them explicitly covers exports, and nothing in the plan supersedes or defers export availability under these states.

Current state:

The behavior is correct, but no test covers it. InformationRequestRecordExportService.create/exports/read call only gate.lock and gate.authorizeRequest(INFORMATION_REQUEST_EXPORT), followed by the daily ceiling and transfer checks. None of them calls requireContinuationEntitlement, requireRequestMutation or requireNotOperationallySuspended. So exports stay available under operational suspension, execution-grant revocation, paid lapse, trial expiry and trust suspension. No test sets up any of these states and then creates, lists or reads a record export. InformationRequestReadAvailabilityTest has four tests, covering the workspace, the listing and answer refusal. InformationRequestRecordExportServiceTest has only the two daily-ceiling tests. The other export tests (TimedRetainedExportRequestConformanceTest, the AuditExport and Privacy transaction tests, the Phase9 walking skeleton, the resource contract test) never mention suspension, revocation, lapse, trial or feature loss. P12-T4 requires that no state "silently hides ... permitted exports from authorized readers", and that clause is still unproven by tests.

Evidence:

- src/main/kotlin/.../service/informationrequest/InformationRequestRecordExportService.kt:63-100 and 178-180: create, exports and read only call gate.authorizeRequest(access, [INFORMATION_REQUEST_EXPORT], id). freeze() then checks requireWithinDailyCeiling and the transfer decision. It has no standing or entitlement check.
- InformationRequestMutationGate.kt:53-61: the standing checks (requireContinuationEntitlement, entitlementGuard.requireNotOperationallySuspended) are separate methods. authorizeRequest (87-93) checks only the permission.
- src/test/.../conformance/InformationRequestReadAvailabilityTest.kt:50,69,88,113: four tests on suspension, revocation and feature loss. None involves exports.
- src/test/.../InformationRequestRecordExportServiceTest.kt:83,101: only the daily-ceiling tests.
- A grep for suspend|revok|lapse|trial|entitle|feature under the test informationrequest package does not match any of the export test files.

Fix outline:

Add Quarkus/PostgreSQL tests to src/test/kotlin/com/docuhyphen/app/api/service/informationrequest/conformance/InformationRequestReadAvailabilityTest.kt, or to a new sibling InformationRequestExportAvailabilityTest.kt, reusing the same fixtures (InformationRequestRuntimeTestServices). Inject InformationRequestRecordExportService. For each state (operational suspension of the owning Exchange's org, revoked execution grant, paid lapse or trial expiry, owner losing the feature, and Trusted Organization suspension if a fixture exists), an authorized owner or manager should be able to:
- call create() with an idempotency key and get a verified export,
- see it in exports(),
- read() it with verified=true and its content.
Also assert that an unauthorized caller still gets a ForbiddenException, so the test shows that authorization, not standing, is what gates exports. No production change and no migration are needed.

### GA-146: Runtime Requirement revision effective interval is never closed

- Severity: low. Verification: confirmed. Fix size: S. Audit key: `REQ-EFFECTIVE-TO`.
- Plan reference: CDM-InformationRequestRequirement, P3-T5. Plan basis: Plan line 708 (the Canonical Domain Model's InformationRequestRequirement is an append-only revision with an "effective interval"); lines 1489-1492 (P3-T5: each Requirement revision stores its effective interval). I found nothing in the plan that supersedes or defers this: no match for effective_to or effectiveTo anywhere, and the Development-Stage Constraint, which forbids backwards-compatibility code, allows the schema to change without a compatibility shim.

Current state:

information_request_requirement_revision has effective_from and a nullable effective_to column (with a CHECK that effective_to > effective_from). Nothing ever writes effective_to. Revisions are inserted with effective_from = now in three places: InformationRequestTemplateMaterializer.materializeRequirement, InformationRequestTemplateMaterializer.advanceRequirement, and InformationRequestGroupOccurrenceService (~597). The append-only trigger rejects every UPDATE, so a superseded revision can never be closed after the fact. When advanceRequirement adds revision n+1, revision n stays open-ended. The only thing that marks which revision is current is the information_request_requirement_current pointer table. InformationRequestTemplateMaterializer.advance also skips runtime Requirements whose binding is absent from the new Template Version (an amendment REMOVED). Those Requirements keep an open current revision with no end marker. The only reader of effective_to is InformationRequestRequirementAuthorizationContextProvider.kt:90 (`filter { it.effectiveTo == null }`), and that filter matches every row, so it has no effect. It is still correct only because it then takes the highest revisionNumber. The interval can be partly worked out from the next revision's effective_from, but the stored interval the plan asks for never ends.

Evidence:

V96__information_request_runtime_persistence.sql:255-278 (effective_from NOT NULL, effective_to nullable, interval CHECK); V96:~363-367 (trigger information_request_requirement_revision_append_only, BEFORE UPDATE OR DELETE, raises an error); V127 disables that trigger only for a one-off occurrence_path backfill (lines 8-25). InformationRequestRequirementRevision.kt:42-46 (entity fields). InformationRequestTemplateMaterializer.kt:275-308 (advanceRequirement saves a new revision and moves the current pointer, with no close of the prior revision), :384-423 (materializeRequirement), :159-211 (advance never handles REMOVED Requirements). InformationRequestGroupOccurrenceService.kt:597 (another insert with effectiveFrom only). Grep for effectiveTo/effective_to across src/main and src/test finds only the entity declaration, the V96 DDL and the no-op filter at InformationRequestRequirementAuthorizationContextProvider.kt:90. No test covers it.

Fix outline:

Pick one of two approaches. (A) Keep append-only and make the interval derivable. Add migration V151 to drop effective_to and its CHECK constraint. Then add a repository or read-model method that computes each revision's end as the next revision's effective_from, or as the time an amendment removed the Requirement. Removal can be recorded as an append-only row, either a new information_request_requirement_retirement table or a closing revision kind. Replace the no-op filter at InformationRequestRequirementAuthorizationContextProvider.kt:90 with a lookup through information_request_requirement_current. (B) Close intervals in place. Add migration V151 to change the append-only guard on information_request_requirement_revision so it allows exactly one UPDATE: effective_to going from NULL to a value, with every other column unchanged. In InformationRequestTemplateMaterializer.advanceRequirement, set effective_to = now on the prior current revision before inserting the new one. In advance(), close the current revision of any runtime Requirement whose binding is missing from the new Template Version. Either way, add tests. First, a migration contract test showing that the trigger still rejects any other update, and that a closed revision's interval is valid. Second, a materializer test showing that after an amendment the prior revision ends and the new one is open. Third, a removed-Requirement test showing that it has no open revision. Finally, make the authorization context provider select the current revision the same way.

### GA-147: ExchangeFieldsResource endpoints share one guard and one generic error log instead of per-method return try/catch

- Severity: low. Verification: confirmed. Fix size: S. Audit key: `RULE-TRYCATCH`.
- Plan reference: P1-T7c, REST-TryCatchAndResourceContractTests. Plan basis: Plan lines 828-829 require every REST resource method to use `return try { } catch { }` and log a unique operation-specific error message. Plan lines 805, 984 and 998 (P1-T7c) put PATCH /exchanges/{id}/fields in scope for this program. A search of the plan for guard or try/catch found no text that supersedes or waives this, and the Development-Stage Constraint does not touch it. AGENTS.md BACKEND RULES sets the same rule for the whole repo. For FieldDefinitionResource and SchemaResource the [truncated]

Current state:

ExchangeFieldsResource was modified by this program (commits 459e64ed, 7d3024a3, 23ed5d04), which added the canonical PATCH /exchanges/{id}/fields (patchValues). All four of its methods (getSchema, assignSchema, unassignSchema, patchValues) are written as `= guard { ... }`. The private guard() checks auth and wraps everything in one try/catch. Its only unexpected-exception log is the class-wide "Exchange fields request failed", and its only warn log is "Exchange Fields request refused by subscription policy". No operation-specific failure message exists, which breaks both AGENTS.md ("each resource must log their own unique error messages") and plan line 828-829. FieldDefinitionResource (7 methods) and SchemaResource (8 methods) use the same guard pattern, with the class-wide messages "Field definition request failed" and "Schema request failed". Those two files were last changed in "platform tiers" (204a13f8), before the Information Request program, so for them it is an older AGENTS.md violation, not a gap in this plan. These three files are the only resources in the repo that use `= guard {`. All Information Request resources follow the per-method pattern.

The completeness critic independently found the same gap: The code matches the claim. FieldDefinitionResource sends all 7 endpoints through one private `guard { }` helper. SchemaResource sends all 8 endpoints through its own copy of that helper. Neither class has a `return try { } catch { }` block in its endpoint methods. Each class logs only one generic error ("Field definition request failed" / "Schema request [truncated]

Evidence:

src/main/kotlin/com/docuhyphen/app/api/resource/exchange/ExchangeFieldsResource.kt:50,65,82,108 (`= guard {`), :156-194 (guard; logger.error("Exchange fields request failed") at ~191). src/main/kotlin/com/docuhyphen/app/api/resource/fields/FieldDefinitionResource.kt:61,67,74,80,88,96,104 (guard), :114-143 (logger.error("Field definition request failed")). src/main/kotlin/com/docuhyphen/app/api/resource/fields/SchemaResource.kt:64,71,77,85,93,101,109,117 (guard), :127-160 (logger.error("Schema request failed")). grep for "= guard {" across resource/ finds only these 3 files. git log: ExchangeFieldsResource was touched by the IR commits 459e64ed/7d3024a3/23ed5d04; FieldDefinitionResource and SchemaResource were last touched in 204a13f8 (platform tiers), before the program. Existing tests: src/test/kotlin/.../resource/exchange/ExchangeFieldsResourceRouteContractTest.kt, ExchangeFieldsResourceETagTest.kt, ExchangeFieldsConditionalWriteTest.kt.

Fix outline:

1) In ExchangeFieldsResource, change each method to `return try { ... } catch (...)`. Keep the shared exception-to-status mapping as a private helper (for example `mapFailure(e, operationMessage)`), or write the catches inline. Each method must log its own message on unexpected failure, for example "Failed to get Exchange schema {id}", "Failed to assign schema to Exchange {id}", "Failed to unassign schema from Exchange {id}" and "Failed to patch Exchange field values {id}". The subscription-denial warn log must also name the operation. Move the auth check into each method's try, or into a small non-catching helper. Keep the current status mapping for FieldsPreconditionException (428/412 with ETag), FieldValidationException (400), IllegalStateException (409), IllegalArgumentException (404), ForbiddenException (403) and SubscriptionDenialException (rethrow). 2) Optionally apply the same refactor to FieldDefinitionResource and SchemaResource, with per-operation messages for list types, list, create, get, contracts, status, resolved, draft bindings, publish and new version. 3) Tests: extend ExchangeFieldsResourceRouteContractTest, or add a resource unit test with a mocked service that throws RuntimeException, and check that each method returns 500 and logs its operation-specific message. Keep the existing ETag and conditional-write tests passing. No migration is needed, and there is no user-visible change, so no help-docs update is needed.

### GA-148: Bootstrap ShareLink does not record last use or verification strength

- Severity: low. Verification: confirmed. Fix size: S. Audit key: `SHARELINK-STRENGTH`.
- Plan reference: AD-24, CDM-RequestAccessContext, P4-T4. Plan basis: Plan lines 528-535 (Decision 24: the bootstrap ShareLink "records a hashed secret, explicit expiry, revocation, replacement and rotation lineage, verification strength, atomic use count, and last use"). Lines 1841-1842 (P4-T4: "record explicit expiry, revocation, replacement, rotation lineage, verification strength, atomic use count, and last use"). Lines 722-725 (domain model rows for ShareLink bootstrap mode, RequestAccessSession and RequestAccessContext). Line 2841 (attestation strength [truncated]

Current state:

The bootstrap-mode ShareLink stores a hashed token, expiry, ACTIVE/REVOKED/EXPIRED status, rotation lineage (rotated_at, rotation_count, replaces_share_link_id) and used_count, which is incremented when contact proof succeeds. It does not store a last-use timestamp or a verification strength, and revocation leaves no timestamp (only status=REVOKED). Decision 24 and P4-T4 explicitly require the ShareLink itself to record verification strength and last use. Those facts exist only on the RequestAccessSession row minted after contact proof (verification_strength, last_used_at, revoked_at, use_count in V104; touchUse sets lastUsedAt). So the ShareLink-level requirement is unmet, but the same facts can be read at session level. Claim 3 is narrower than stated. RequestAccessContext is only (principal, AuthorizationContext) and carries no authentication method, strength or request capabilities. InformationRequestSubmissionAttestationService.strengthOf works out strength from principal kind: PARTICIPANT maps to VERIFIED_CONTACT and it never reads the session's verificationStrength. RequestAccessSessionVerificationStrength has only EMAIL_OTP today, so the inferred value matches the stored one and nothing behaves wrongly yet. The session has share_link_id and the participant principal but no information_request_id or party id column. The request party is reached only through ShareLink, then Share, then party.shareId. Request capabilities are decided by the central authorizer for each action rather than carried in the context, which fits the plan's "no parallel policy engine" rule.

Evidence:

src/main/kotlin/com/docuhyphen/app/api/model/entity/ShareLink.kt: its fields are id, shareId, tokenHash, passwordHash, maxUses, usedCount, domainAllowlist, requireMfa, status, linkMode, contactOtp* fields, rotatedAt, rotationCount, replacesShareLinkId, expiresAt, createdByAppUserId and createdAt. There is no lastUsedAt, verificationStrength or revokedAt. The share_link migrations (V1, V103, V105, V106, V118, V119) never add those columns. The only matches for last_used_at, verification_strength and revoked_at are in V104__request_access_session.sql:7,10,11. RequestAccessSession.kt:38-56 has verificationStrength, revokedAt, lastUsedAt and useCount. RequestAccessSession.kt:13-16 shows the enum has only EMAIL_OTP. InformationRequestContactProofService.kt:103-108 clears the OTP and runs usedCount += 1 on the ShareLink but records no timestamp or strength, and line 117 sets verificationStrength=EMAIL_OTP only on the session. RequestAccessSessionService.kt:132-137 (touchUse) sets lastUsedAt [truncated]

Fix outline:

1. Add migration V151__share_link_bootstrap_use_metadata.sql. It adds share_link.last_used_at TIMESTAMP NULL, share_link.verification_strength VARCHAR(32) NULL and share_link.revoked_at TIMESTAMP NULL. Optionally add a CHECK that verification_strength is null unless link_mode='VERIFICATION_BOOTSTRAP'. No backfill or compatibility code, per the Development-Stage Constraint.
2. Add matching fields to ShareLink.kt. Reuse RequestAccessSessionVerificationStrength, or move it to a shared model enum.
3. In InformationRequestContactProofService.verify, on success set shareLink.lastUsedAt = now and shareLink.verificationStrength = EMAIL_OTP next to usedCount += 1. Make the increment atomic with a conditional UPDATE ... SET used_count = used_count + 1, last_used_at = now() WHERE id = ? AND (max_uses IS NULL OR used_count < max_uses), or with a pessimistic lock.
4. In InformationRequestBootstrapShareLinkService.revoke, replace and rotate (and the participant account upgrade revocation path), set revokedAt when status becomes REVOKED.
5. Optional, for claim 3: add authenticationMethod/strength to RequestAccessContext. RequestAccessContextFactory.fromBootstrapSession fills it from session.verificationStrength, and the authenticated path fills it from mfaSatisfied. strengthOf then reads the context instead of principal kind. Add a request party reference to the context, or to request_access_session in the same V151, if a direct binding is wanted.
6. Tests: extend the bootstrap ShareLink service and contact-proof tests to assert last_used_at, verification_strength and used_count after a [truncated]

### GA-149: Registration upgrade skips trusted-recipient validation

- Severity: low. Verification: confirmed. Fix size: M. Audit key: `UPGRADE-SAFETY`.
- Plan reference: AD-32, P4-T4, REST-DualSurfacesSharedServices. Plan basis: In force:
- Decision 32 (plan 630-641): every command rechecks or locks the parent state, and an ENDED session may read but cannot mutate.
- Decision 34 (646-654): registration upgrade reuses TrustedRecipientValidationService, and a tested matrix covers session and recovery behavior under suspension.
- Line 394: the Trusted Organizations reuse row names registration upgrade.
- REST rules 824-827: shared services never read a raw credential and receive an explicit RequestAccessContext.
- [truncated]

Current state:

InformationRequestParticipantAccountUpgradeService.upgrade (the registration-upgrade command behind POST /information-requests/{id}/participant-account-links) has three gaps against the plan.
(1) Trust: the service has no trust dependency at all. It never calls TrustedRecipientValidationService, ExchangeRecipientService.trustSuspended or requireAssignablePartyRecipient, and no test or matrix row defines how trust behaves during an upgrade. The gap is narrower than claimed, though. A PARTICIPANT-held party can never be bound to a trusted ExchangeRecipient, because requireAssignablePartyRecipient requires the direct Share principal to match the party principal, and trusted selections are USER or PRINCIPAL_GROUP. So trust suspension cannot apply to the party being upgraded. What is really missing is Decision 34's explicit, tested policy for the new USER Share: for example, what happens when the registering App User belongs to an organization whose trust relationship with the request owner is suspended.
(2) Parent lifecycle: upgradeMutation loads the request with a plain findById. It never takes lockParentExchangeOf or a FOR UPDATE on the Exchange, and never consults InformationRequestTransitionMatrix or the request status. The session and ShareLink rows are locked, but the parent is not. On an ENDED Exchange, parentEffects keeps external sessions alive until they expire (EXPIRE_NATURALLY). A session there can still run this command, which grants a new USER Share, writes a ParticipantAccountLink and revokes links. That breaks both "every command rechecks or locks the parent [truncated]

Evidence:

- src/main/kotlin/com/docuhyphen/app/api/service/informationrequest/InformationRequestParticipantAccountUpgradeService.kt:1-33: the imports and constructor (47-57) have no trust, ExchangeRepository or transition-matrix dependency.
- Same file, :64: `requestAccessSessionService.authenticate(command.sessionToken)`.
- Same file, :89: `requestRepository.findById(command.requestId)`. No parent lock anywhere in 85-165.
- Same file, :148-162: grants the USER Share via ShareService.grantRoleKeyWithPrincipalProvenance and revokes bootstrap links.
- InformationRequestParticipantAccountLinkResource.kt:60 takes the `X-Request-Session-Token` header. Lines 68-79 build the access context but pass only appUserId plus the raw sessionToken.
- InformationRequestParentLock.kt:13-28 (lockParentExchangeOf) is used by 7 other IR service files but not by this one.
- InformationRequestTransitionMatrix.kt:212-218: ENDED gives externalSessionRead=true and EXPIRE_NATURALLY.
- [truncated]

Fix outline:

No migration needed.
1. Parent lock: in InformationRequestParticipantAccountUpgradeService.upgradeMutation, replace requestRepository.findById with lockParentExchangeOf(requestId, requestRepository, exchangeRepository), injecting ExchangeRepository. Add a check that refuses with PARENT_STATE_INVALID unless the Exchange is INITIATED or ACCEPTED_STARTED. Also require the request itself to be in a non-terminal state, using InformationRequestTransitionMatrix or the existing request gate lock that other party commands use.
2. Raw credential: move session-credential resolution to the adapter boundary. Either add InformationRequestAccessContextFactory.fromUpgradeSession(appUserAccess, sessionToken), or reuse a resolver like InformationRequestNoAuthReadAccessService, so the resource resolves the session once. Then change UpgradeInformationRequestParticipantAccountCommand to carry an explicit RequestAccessContext (App User principal plus the verified session id) and drop the sessionToken field. The service should only re-lock the session with requireActive.
3. Trust: add a trust gate for the new USER Share. If the request is organization-owned and the App User's organization has an OrganizationTrustRelationship with the owner organization, consult TrustedRecipientValidationService.isRelationshipSuspended (or the equivalent reconciliation). Refuse with TRUST_SUSPENDED instead of materializing access. Record upgrade as a row in the trust-suspension matrix.
4. Tests in InformationRequestParticipantAccountUpgradeServiceTest:
   - upgrade refused on an ENDED Exchange, a cancelled [truncated]

Decision needed. Recommended default: Refuse the upgrade while the trust relationship is suspended. Refuse upgrades on an ENDED Exchange.

### GA-150: Clock policy define and version publish have no Idempotency-Key or If-Match, and a concurrent publish surfaces as 500

- Severity: low. Verification: partial. Fix size: M. Audit key: `critic#4`.
- Plan reference: Completeness critic. Plan basis: Plan 817-819: retryable transitions require an idempotency key, with stable conflict on fingerprint mismatch. Plan 820-822: If-Match is required only for mutable drafts, party changes, Value Sets, response cycles, and review drafts, which narrows the If-Match part of the claim. Plan 823: stable error codes. Plan 3266 (Decision 7): immutable numbered versions. Plan 3379-3381 (P9-T5 Done) mentions If-Match only for clock commands, not for policy commands. No later text waives idempotency or [truncated]

Current state:

POST /information-request-clock-policies (define) and POST /information-request-clock-policies/{policyId}/versions (publishVersion) take no Idempotency-Key header and no If-Match header, and the service does not record a command receipt. The request clock commands (start, change) in the same endpoint class do use an idempotency key and If-Match, so the gap is only in the two policy commands. A client that retries publishVersion after losing the response creates an extra immutable version (N+1) instead of getting the original result back. publishVersion loads the policy with a plain findById, without the PESSIMISTIC_WRITE lock that BaseRepository.findByIdForUpdate provides. It then takes max(version_number)+1 over rows that are not locked. Two concurrent publishes both pick the same number and collide on the UNIQUE (policy_id, version_number) constraint. The same happens to two concurrent defines with the same key: the findByKey pre-check passes in both, and one insert hits the owner/key unique index. In both cases the resulting persistence/constraint exception matches no branch in InformationRequestCommandHttp.refused, so it is logged and returned as 500 "Request failed" with no stable reason code. The If-Match part of the claim is narrower than stated. The plan requires If-Match only for mutable resources (request drafts, party changes, Value Sets, response cycles, review drafts). A clock policy has no mutable draft, and its versions are append-only and immutable (Decision 7). So a missing If-Match is not a violation by itself, but it would be one reasonable way to [truncated]

Evidence:

InformationRequestClockPolicyResource.kt:55-83: define(request) and publishVersion(policyId, request) have no @HeaderParam. InformationRequestClockEndpoint.kt:35-47: definePolicy/publishVersion pass no key and return no ETag. By contrast, start (52-68) uses InformationRequestCommandHttp.idempotencyKey and returns an ETag header, and change takes ifMatch and idempotencyKey. InformationRequestClockPolicyService.kt publishVersion: policyRepository.findById (not findByIdForUpdate), then versionRepository.nextVersionNumber(policy.id). define: a findByKey pre-check, then save. InformationRequestClockRepositories.kt: nextVersionNumber = max over findForPolicy + 1, with no lock. BaseRepository.kt:27-30: findByIdForUpdate with PESSIMISTIC_WRITE exists but is unused here. V140__information_request_clock.sql:50: CONSTRAINT ux_information_request_clock_policy_version_number UNIQUE (policy_id, version_number); lines 25/29: unique owner-key indexes. InformationRequestCommandHttp.kt refused(): no [truncated]

Fix outline:

1) Add @HeaderParam("Idempotency-Key") to InformationRequestClockPolicyResource.define and publishVersion. Pass it through InformationRequestClockEndpoint via InformationRequestCommandHttp.idempotencyKey into DefineInformationRequestClockPolicyCommand and PublishInformationRequestClockPolicyVersionCommand (model/informationrequest). In InformationRequestClockPolicyService, record a command receipt keyed on owner + key with a request fingerprint, following the pattern the clock start command already uses: a replay returns the original policy view, and a fingerprint mismatch throws CommandReceiptConflictException. If the existing receipt table is generic, no migration is needed. Otherwise V151 adds a receipt table or columns. 2) In publishVersion, use policyRepository.findByIdForUpdate to serialize version numbering. Optionally accept If-Match against a policy ETag (the latest version number) and return that ETag from define/publish/get. 3) In define, catch the unique-index violation on the owner/key index (or lock the owner scope) and throw InformationRequestLifecycleException(CLOCK_POLICY_KEY_TAKEN) so the response is 409. Also add a defensive mapping in InformationRequestCommandHttp.refused so any remaining constraint violation returns 409 with a stable code instead of 500. 4) Frontend: send an Idempotency-Key (and If-Match if adopted) from defineInformationRequestClockPolicy and publishInformationRequestClockPolicyVersion in web-app/src/services/informationRequestAdministrationService.ts. 5) Tests: extend InformationRequestClockPolicyServiceTest with a replay of the same [truncated]

### GA-151: No test changes a hold's scope successfully or covers platform-owned holds

- Severity: low. Verification: confirmed. Fix size: S. Audit key: `critic#5`.
- Plan reference: Completeness critic. Plan basis: Plan 3470-3472 (Phase 9 Tests to write first: hold scope change, immutable history, platform/organization/personal ownership). Exit criterion ~3490 ("place, change, and release holds without erasing hold history or racing past a claimed disposal"). P9-T13 (3451-3455) defers only the hold scope change screen to Phase 10 (3581), not backend tests; nothing supersedes the test requirement.

Current state:

RecordPreservationHoldService.changeScope (unchanged-scope refusal, holdRevision += 1, SCOPE_CHANGED hold event, AUDIT_LEGAL_HOLD_SCOPE_CHANGED audit, refusal while a disposal claim is open) has no successful-path test at any layer. The only callers in tests are RecordPreservationEntitlementTest (asserts entitlement refusal with holds mocked, so changeScope is never reached) and a raw SQL UPDATE in RecordPreservationDisposalContractTest that only proves the DB trigger requires a revision bump. The HOLD_SCOPE_UNCHANGED refusal, the scope-change-under-disposal refusal, the PUT/PATCH /{holdId}/scope resource, and the SCOPE_CHANGED history projection are untested. Platform ownership is only partially covered: RecordPreservationDisposalContractTest:180 inserts a PLATFORM-owned hold via SQL and proves it blocks a disposal claim, CleanSchemaMigrationContractTest:284-285 checks the owner check constraint, and AuditLegalHoldServiceTest:88 refuses re-release of a released PLATFORM hold; but no test places a PLATFORM hold through RecordPreservationHoldService/RecordPreservationAdministration (platform-admin capability path) or changes/releases one successfully.

Evidence:

src/main/kotlin/com/docuhyphen/app/api/service/recordpreservation/RecordPreservationHoldService.kt:66-84 (changeScope). src/main/kotlin/com/docuhyphen/app/api/resource/recordpreservation/RecordPreservationHoldResource.kt:79-90 (/{holdId}/scope endpoint). src/test/kotlin/com/docuhyphen/app/api/service/recordpreservation/RecordPreservationEntitlementTest.kt:48 (only changeHoldScope call, refusal, holds is a mock). src/test/kotlin/com/docuhyphen/app/api/service/audit/AuditLegalHoldServiceTest.kt covers place (ORG), release (ORG), re-release refusal of a PLATFORM hold (:88), foreign-owner not found, disposal-claim refusal; no changeScope. src/test/kotlin/com/docuhyphen/app/api/migration/RecordPreservationDisposalContractTest.kt:94-101 raw SQL scope update; :180 PLATFORM hold inserted by SQL blocks claim. CleanSchemaMigrationContractTest.kt:284-285 PLATFORM owner constraint. grep of src/test for changeScope|changeHoldScope|holdRevision|SCOPE_CHANGED returns only these files plus IR [truncated]

Fix outline:

No migration needed. Add src/test/kotlin/com/docuhyphen/app/api/service/recordpreservation/RecordPreservationHoldServiceTest.kt (mocked repositories, fixed Clock) covering: successful changeScope RESOURCE -> DESCENDANTS_AND_REFERENCES asserts holdRevision incremented, SCOPE_CHANGED event saved with new scope and reason, audit.hold(AUDIT_LEGAL_HOLD_SCOPE_CHANGED) called; HOLD_SCOPE_UNCHANGED refusal; HOLD_RELEASED refusal; DISPOSAL_IN_PROGRESS refusal when claimRepository.hasOpenClaimCovering is true (no update, no event); blank reason refusal; place/changeScope/release for RecordOwnerRef.PLATFORM, organization and user owners; wrong-owner not-found. Extend RecordPreservationEntitlementTest (or an administration test) with a permitted-path changeHoldScope for org (entitled), personal (entitled) and platform-admin owners verifying delegation to holds.changeScope. Optionally add a resource test for PUT /{holdId}/scope and a DB-backed test in RecordPreservationDisposalContractTest asserting a scope widened through the service makes a previously claimable descendant unclaimable.

### GA-152: Owner audit search omits classified IR events whose target is not a request (privacy requests, clock policies, Templates)

- Severity: low. Verification: partial. Fix size: S. Audit key: `critic#6`.
- Plan reference: Completeness critic. Plan basis: Plan 3282-3286 (decision 9: GET /information-request-audit-events returns classified Information Request events), 3387-3391 (P9-T7: "cross-request audit search ... over the classified transactional events already recorded by Phases 2 through 8"; marked done as "owner-scope audit search"). Nothing I found in the plan explicitly supersedes or defers including the non-request targets. The "cross-request" wording, and the fact that the privacy events come from Phase 9 itself rather than Phases 2-8, [truncated]

Current state:

The owner-scope Information Request audit search (GET /information-request-audit-events, the Operations "Audit search" tab) filters on only one target type, INFORMATION_REQUEST. Some events are catalogued as AuditCategory.INFORMATION_REQUEST with keys starting "information_request." but are recorded against other target types, so this search can never return them:
- privacy.record, privacy.complete and privacy.refuse (target INFORMATION_REQUEST_PRIVACY_REQUEST)
- clock_policy.publish (target INFORMATION_REQUEST_CLOCK_POLICY)
- template.create, configure, publish, new_version and retire (target INFORMATION_REQUEST_TEMPLATE_DEFINITION)

The gap is narrower than claimed, for two reasons. First, the plan calls P9-T7 a "cross-request" search, and help describes Audit search as searching "the audit record of every request you own by request, event, actor, and time". Both can fairly be read as limited to events that target a request, so help is not strictly inaccurate. Second, organization owners can still find these events through the general organization audit log (GET /organizations/{organizationId}/audit-events?categories=INFORMATION_REQUEST). The events are truly unreachable only in the Information Request operations surface, and for personal (USER) owners, who have no organization audit log. Decision 9 says "classified Information Request events" without limiting them to request targets, so the gap is real under a broad reading.

Evidence:

InformationRequestAuditService.kt:44 calls history.recordsForOwner(scope, TARGET, ...) with TARGET = RecordPreservationResourceTypes.INFORMATION_REQUEST (line 113). AuditTargetHistoryService.kt:27-31 passes a single targetType to AuditOutboxRepository.findForTargets/countForTargets (AuditOutboxRepository.kt:72-92). InformationRequestPrivacyService.kt:223 uses targetType "INFORMATION_REQUEST_PRIVACY_REQUEST". InformationRequestClockPolicyService.kt:179 uses "INFORMATION_REQUEST_CLOCK_POLICY". InformationRequestTemplateAuthoringService.kt:378/450 uses TEMPLATE_TARGET_TYPE = "INFORMATION_REQUEST_TEMPLATE_DEFINITION". AuditEventType.kt:176-190, 315-322 put these under AuditCategory.INFORMATION_REQUEST with information_request.* keys, and InformationRequestAuditPayloadPolicy.eventClassOf would classify them (template, clock_policy, privacy). The help article informationRequestOperationsArticle.tsx:61-62 says Audit search covers "every request you own by request, event, actor, and time". [truncated]

Fix outline:

1. Change AuditTargetHistoryService.recordsForOwner and AuditOutboxRepository.findForTargets/countForTargets/targetClauses to accept a Set<String> of target types (a.targetType IN :targetTypes). Keep any single-type callers working through setOf(...).
2. In InformationRequestAuditService.search, pass setOf(INFORMATION_REQUEST, "INFORMATION_REQUEST_PRIVACY_REQUEST", "INFORMATION_REQUEST_CLOCK_POLICY", "INFORMATION_REQUEST_TEMPLATE_DEFINITION"). Move those strings into shared constants (for example next to RecordPreservationResourceTypes) and have the privacy, clock policy and template services use the constants. When search.requestId is set, keep the request-only target restriction.
3. Allow-list the payload keys for these events in InformationRequestAuditPayloadPolicy so they are not withheld: privacyRequestId, requestKind, purposeKey, policyBasisKey, state, reasonCode, policyKey, versionNumber, clockType, dueEffect, namespace, templateKey, scopeKind. Leave subjectIdentityRefId withheld.
4. Show targetType/targetLabel in the frontend Audit search tab so rows that are not requests read correctly, and add event class filter options for template, clock_policy and privacy.
5. Tests: add service/resource tests showing that the owner search returns template publish, clock policy publish and privacy record events for both an organization owner and a personal owner, stays owner-scoped (no events from other owners), and returns only request-targeted events when requestId is given.
6. Update informationRequestOperationsArticle.tsx to say Audit search also covers Template, due date [truncated]

Decision needed. Recommended default: Include the owner's Template, clock policy and privacy request events in the owner audit search. Personal owners have nowhere else to see them.

### GA-153: Help and UI say a platform administrator can stop a request, but grant revocation has no caller or endpoint

- Severity: low. Verification: partial. Fix size: S. Audit key: `cross-gates#2`.
- Plan reference: Cross-phase gates. Plan basis: Plan lines that defer the revocation endpoint or admin surface:
- Status, lines 72-73: "An administrative surface for explicit execution-grant revocation remains a future scoping decision."
- P4-T7 result, lines 1959-1963: "Neither has a REST caller yet; an administrative surface for either was left for a future session to place."

Plan lines that keep the help-accuracy requirement in force:
- Help Documentation Requirements, lines 4592-4601, step 3: "Update navigation, names, permissions, [truncated]

Current state:

Nothing can revoke an execution grant today. InformationRequestExecutionGrantService.revoke(requestId, reason) is the only code that writes RequestExecutionGrant.revokedAt/revokedReason, and nothing in src/main or src/test calls it. No resource, scheduler or admin service reaches it. Six places check the flag (MutationGate, EvidenceGate, LifecycleService, ResponseDraftService, GroupOccurrenceService, ExecutionStandingService), and tests reach that state only through mocks. So in production the EXECUTION_GRANT_REVOKED standing and the error code can never occur.

The missing endpoint is not a gap in itself. The plan explicitly defers an administrative revocation surface.

The real gap is in the help docs, which describe this deferred capability as if it works today:
- informationRequestsOverviewArticle.tsx line 40 lists "Stopped: a platform administrator stopped the request." as one of the current standings.
- informationRequestAccessArticle.tsx lines 40-41 says "an explicit grant revocation stops further answers and changes". This reads as something that can happen now.

The frontend label in executionStandingText.ts (lines 19 and 27) is a lower concern. It only shows when the backend returns EXECUTION_GRANT_REVOKED, which cannot happen yet, so users never see it. It is dormant, not misleading.

Evidence:

- src/main/kotlin/com/docuhyphen/app/api/service/informationrequest/InformationRequestExecutionGrantService.kt:96-107: revoke() is the only code that sets revokedAt.
- A grep of src/ for `(executionGrantService|grants|executionGrants)\.revoke\(` returns nothing. The only `.revoke(` hits are AcceptedFact revocation, which is unrelated.
- The only write path for the revoked_at column created in V108__request_execution_grant.sql:14 is the service method above.
- Checks of the flag: InformationRequestMutationGate.kt:62, InformationRequestEvidenceGate.kt:112, InformationRequestLifecycleService.kt:277, InformationRequestResponseDraftService.kt:322, InformationRequestGroupOccurrenceService.kt:384, InformationRequestExecutionStandingService.kt:56.
- Frontend: web-app/src/app/information-requests/shared/executionStandingText.ts:19 has "A platform administrator stopped this request." and line 27 has "Stopped". The standing enum is in web-app/src/app/models/models.tsx:4121 and 4131.
- Help: [truncated]

Fix outline:

This is a docs-only fix with no backend change and no migration; V151 stays free.

1. informationRequestsOverviewArticle.tsx: remove the "Stopped: a platform administrator stopped the request." bullet at line 40. Also drop "or that the request was stopped" from the standing-reason paragraph at lines 42-45.
2. informationRequestAccessArticle.tsx, lines 40-41: reword to cover only operational suspension, for example "An operational suspension stops further answers and changes, but everything already recorded stays readable."
3. Optional: in executionStandingText.ts line 19, use neutral wording such as "This request was stopped and can no longer change." so the UI makes no claim about an actor or surface that does not exist yet. Update executionStandingText.test.ts if it asserts the exact text.
4. Run `npx tsc --noEmit` and vitest in web-app.

If the user later scopes the admin surface, the alternative is to add a platform-admin-only endpoint such as POST /information-requests/{id}/execution-grant/revocation. It would be a thin resource that delegates to a service method with an admin authz check, an audit event and a reason code, plus contract tests. Then the current wording would become accurate.

### GA-154: Flyway allocation ledger omits 14 program migrations and keeps stale statuses

- Severity: low. Verification: confirmed. Fix size: S. Audit key: `cross-gates#3`.
- Plan reference: Cross-phase gates. Plan basis: The requirement is at lines 4245-4247 (Migration and Compatibility Strategy ledger rule) and lines 199 and 4236-4244. Nothing in the Status section (1-80), the Development-Stage Constraint (168-265), non-goals (304-346) or the Latest Implementation Result (4679+) waives or supersedes the ledger rule. Line 4492 acknowledges V118/V119 but does not record them as ledger rows.

Current state:

All 75 migration files V76-V150 exist in src/main/resources/db/migration with no gaps or duplicates. The plan's Flyway allocation ledger (table starting line 4252, running to about line 4489) has rows for V76-V102, V115-V117 and V120-V150, but no row for V103__share_link_mode, V104__request_access_session, V105__share_link_contact_otp, V106__share_link_rotation_replacement, V107__participant_account_link, V108__request_execution_grant, V109__request_execution_usage_reservation, V110__information_request_response_draft, V111__information_request_template_requirement_group, V112__information_request_group_occurrence, V113__information_request_group_occurrence_removal, V114__information_request_template_condition_rule, V118__share_link_contact_proof_attempts or V119__share_link_contact_proof_challenge_limit. That is 14 missing rows. V103-V108, V111, V112 and V114 are mentioned only in task prose (lines 1821-1830, 1914, 2104, 2119, 2141). V109, V110 and V113 do not appear anywhere in the plan. V118 and V119 are mentioned only in one prose line (4492), with no task ID, filename, date or status. The V115 (lines 4374-4377) and V116 (4379-4382) rows still say the Testcontainers contract "could compile but could not execute". Both tests exist (src/test/kotlin/com/docuhyphen/app/api/migration/InformationRequestConditionHiddenDataPolicyContractTest.kt and InformationRequestRuntimePersistenceContractTest.kt), and the Status section (line 20) reports a full green backend suite of 3,379 tests on 2026-09-30. So these statuses are out of date. This is a documentation-only gap. Schema and [truncated]

Evidence:

src/main/resources/db/migration: V76 through V150 are all present and contiguous. plans/DOCUMENT-DRIVEN-INFORMATION-REQUESTS-IMPLEMENTATION-PLAN.md:4245-4247 contains the requirement to record the task ID, filename, allocation date and status in the ledger before implementation. The ledger table header is at 4252. A grep of the ledger region finds rows only for V76-V102, V115-V117 and V120-V150. grep -c returns 0 for V109, V110 and V113 across the whole plan. Line 4492 reads 'V118 and V119 were taken by prior P5 remediation work before this row was written.' and has no ledger rows. Lines 4374-4382 are the V115/V116 rows with 'Docker was unavailable ... could not execute'. Line 20 (Status) reports the full backend suite green with 3,379 tests. Both contract tests exist under src/test/kotlin/com/docuhyphen/app/api/migration/.

Fix outline:

Edit plans/DOCUMENT-DRIVEN-INFORMATION-REQUESTS-IMPLEMENTATION-PLAN.md only. No migration is needed, and V151 stays the next free number. Add 14 ledger rows in version order. Each row needs the owning task ID, the exact filename, the allocation date and the status:
- V103-V107: P4 bootstrap/link/session/OTP/rotation/account-link tasks. Take the IDs from the task prose around lines 1821-1830.
- V108: P4-T7a.
- V109: the usage reservation task.
- V110: the response draft task.
- V111-V114: the P5 requirement group, occurrence, occurrence removal and condition rule tasks, around lines 2104-2141.
- V118-V119: the P5 remediation contact-proof tasks.
Get the allocation dates from git log on each migration file, and name the contract test that covers each one. Then update the V115 and V116 rows to 'Created and PostgreSQL contract-tested', naming InformationRequestConditionHiddenDataPolicyContractTest and InformationRequestRuntimePersistenceContractTest, once a Docker-backed run is confirmed. Replace the note at line 4492 with a pointer to the new rows. No code, tests or help docs are affected.

### GA-155: 30 program migrations contain descriptive SQL comments, which AGENTS.md forbids

- Severity: low. Verification: confirmed. Fix size: S. Audit key: `cross-gates#4`.
- Plan reference: Cross-phase gates. Plan basis: In the plan's Development-Stage Constraint section, relative lines 21-22 say that Flyway files that may already have been applied are never edited, renamed, reused or squashed. That makes the existing files immutable; it does not waive the rule. No plan text supersedes or exempts the AGENTS.md coding rule on SQL comments. Plan line 1640 acknowledges a migration comment exists but does not authorize the practice.

Current state:

30 of the Information Request program migrations (V76-V150) contain descriptive SQL comment lines that explain what the migration or schema does, which AGENTS.md forbids ("No comments on ... sql migrations ... describing what the functionality does"). The heaviest are V88 (63 lines), V87 (50), V86 (47, opening with a multi-paragraph design narrative), V89 (44), V80 and V91 (38 each), V79 (25), V114 (24), V82 (23), V122 (22), V77 (21), V83 (20), V81 and V111 (17 each), V90 (14), V78 (13), V112 (8). There are also smaller ones, such as V124's 6-line header explaining the creator-principal backfill and delete. None of them refer to the plan. The plan says Flyway files that may already have been applied are never edited, so these files cannot be cleaned up in place. The gap only matters from here on: V151 and later migrations must carry no descriptive comments. Nothing in the plan exempts migrations from the AGENTS.md rule.

Evidence:

A grep for lines starting with '--' in src/main/resources/db/migration/V76-V150 matches 30 files. Counts: V88=63, V87=50, V86=47, V89=44, V91=38, V80=38, V79=25, V114=24, V82=23, V122=22, V77=21, V83=20, V81=17, V111=17, V90=14, V78=13, V112=8, V97=6, V96=6, V124=6, V76=5, V127=5, V123=5, V125=4, V102=4, V84=3, V126=3, V101=3, V98=2, V94=2. V86__information_request_template.sql lines 1-17 are a design narrative ("Reusable, versioned configuration for a request for information. The configuration is split into what stays and what freezes..."). V124__document_version_canonical_creator.sql lines 1-6 describe the backfill and delete. V86 was committed in 459e64ed, "Information request Phase 1-5", so it may already be applied. The plan itself refers to such comments at line 1640 ("exactly the gap its own migration comment named as deferred").

Fix outline:

Do not edit V76-V150, because they may already be applied and the plan forbids editing them. Going forward, V151 onward must carry no descriptive '--' comments. Optionally add a lightweight guard: a test or a CI script, for example in src/test/kotlin as a migration lint test, that scans migrations numbered at or above V151 and fails on lines starting with '--' that are not an allowlisted marker. Record that V76-V150 are grandfathered. No new migration is needed.

### GA-156: No-auth request-scope binding and the App-User-only upgrade gate are decided in the HTTP layer

- Severity: low. Verification: confirmed. Fix size: M. Audit key: `rules-sweep#1`.
- Plan reference: AGENTS.md rules sweep. Plan basis: This rule comes from AGENTS.md (REST resources must hold no business logic or domain decisions), not from the plan. A grep of plans/DOCUMENT-DRIVEN-INFORMATION-REQUESTS-IMPLEMENTATION-PLAN.md for resource-layer or thin-adapter exceptions found nothing that waives or narrows the rule for no-auth binding or the upgrade gate. The Development-Stage Constraint (168-265) forbids backwards-compatibility code, so the fix can change signatures directly without shims.

Current state:

Two authorization decisions sit in the HTTP adapters rather than the service layer, which breaks the AGENTS.md rule that resources contain no domain decisions. (1) InformationRequestNoAuthReadAccessService.resolve(token, sessionToken) returns InformationRequestNoAuthAccess(access, party.informationRequestId) and never checks that ID against the request named in the path. RequestAccessContext carries only principal and authorization. The check that the no-auth credential is bound to the requested Information Request, and the resulting 404, is repeated in adapter code: 7 times inline in InformationRequestNoAuthRequestResource, once in a private helper in InformationRequestNoAuthEvidenceResource, and once in InformationRequestCommandHttp.withNoAuthAccess (a resource-package object used at 14 call sites). Today every no-auth endpoint does run the check, so this is a layering violation, not an open security hole. Any new no-auth caller of resolve() that skips the check would still get cross-request access. (2) InformationRequestParticipantAccountLinkResource.upgrade works out whether the caller may upgrade (the principal must be of kind USER) and returns a 403 before calling the service. UpgradeInformationRequestParticipantAccountCommand accepts a bare appUserId UUID, so InformationRequestParticipantAccountUpgradeService trusts the caller for that decision. The service's appUserService.getById only indirectly guards against a non-user ID.

Evidence:

src/main/kotlin/com/docuhyphen/app/api/service/informationrequest/InformationRequestNoAuthReadAccessService.kt:17-33 (resolve returns InformationRequestNoAuthAccess(access, party.informationRequestId) with no path request parameter or check); src/main/kotlin/com/docuhyphen/app/api/service/informationrequest/RequestAccessContext.kt:22-25 (principal plus authorization only); src/main/kotlin/com/docuhyphen/app/api/resource/informationrequest/InformationRequestNoAuthRequestResource.kt:77,102,128,162,204,246,287 ('if (noAuthAccess.requestId != requestId)' returns not found); src/main/kotlin/com/docuhyphen/app/api/resource/informationrequest/InformationRequestNoAuthEvidenceResource.kt:326-329 (same check in a private helper); src/main/kotlin/com/docuhyphen/app/api/resource/informationrequest/InformationRequestCommandHttp.kt:35-51 (withNoAuthAccess does the same comparison; grep counts 14 withNoAuthAccess( call sites under resource/); [truncated]

Fix outline:

1) Change InformationRequestNoAuthReadAccessService.resolve to resolve(requestId: UUID, rawToken: String, sessionToken: String?): RequestAccessContext. After resolving the party, throw InformationRequestLifecycleException(InformationRequestErrorCatalog.NOT_FOUND, "Information Request not found") when party.informationRequestId != requestId. Do this before touchUse, or keep the current order on purpose. Drop the requestId field from InformationRequestNoAuthAccess, or delete that model if nothing else uses it. 2) Delete the inline comparisons in InformationRequestNoAuthRequestResource (7 sites), the helper check in InformationRequestNoAuthEvidenceResource (around line 326), and the comparison in InformationRequestCommandHttp.withNoAuthAccess. Pass the path requestId into resolve instead. The existing refused()/evidence exception mapping already turns a NOT_FOUND lifecycle exception into a 404, so the evidence handler may need a matching case. 3) Change UpgradeInformationRequestParticipantAccountCommand to carry the caller's RequestAccessContext (or PrincipalRef) instead of appUserId. In InformationRequestParticipantAccountUpgradeService.upgrade, throw ForbiddenException("Only an authenticated App User may complete a registration upgrade") when principal.kind != PrincipalKind.USER, then use principal.id. Remove the takeIf/forbidden branch from InformationRequestParticipantAccountLinkResource and let handleException map ForbiddenException to 403. Update the KDoc wording. 4) Tests: service-level tests in src/test/kotlin for resolve with a mismatched requestId (NOT_FOUND, and [truncated]

### GA-157: Test class and constant names carry plan phase numbers

- Severity: low. Verification: confirmed. Fix size: S. Audit key: `rules-sweep#2`.
- Plan reference: AGENTS.md rules sweep. Plan basis: AGENTS.md coding rule on phased-plan references. Plan P2-T11, lines 1268-1272, requires neutral capability terminology for test classes and helper functions. The walking-skeleton rule (lines 889-900) says to extend the same fixtures in every phase and gives no naming by phase. Plan lines 2747, 2951, 3158 and 3448 only record the existing names as Done, and nothing in the plan supersedes the requirement.

Current state:

Five Information Request walking-skeleton test classes use plan phase numbers as their only distinguishing name: InformationRequestTemplateWalkingSkeletonPhase5Test through ...Phase9Test, in src/test/kotlin/com/docuhyphen/app/api/service/informationrequest/. InformationRequestEventConsumptionContractTest defines a companion constant PHASE_NINE_MUTATIONS (line 232) and iterates it at line 144. The mutations in that list (CHANGE_COMPLETION_GATE, START/PAUSE/RESUME/EXTEND_CLOCK, RECORD_REMINDER/OVERDUE/ESCALATION) are clock and completion-gate mutations, so the name describes plan sequencing, not capability. This breaks the AGENTS.md rule that code from a phased plan must not reference its phase/task numbers. The plan also requires, at P2-T11 (line 1269-1270), that every fixture "test class, and helper function must use neutral capability terminology". Nothing in the plan waives or defers this. The plan's "Done:" lines (2747, 2951, 3158, 3448) only record the names that exist; they do not ask for them. Out of scope for this claim: some older non-IR tests have KDoc comments that mention phases (for example ExchangeOwnerContextResolutionTest.kt:26, GroupMediatedShareTest.kt:23, OrgGroupAuthorizationTest.kt:30, WorkflowInstanceGraphRecordingTest.kt:36).

Evidence:

InformationRequestTemplateWalkingSkeletonPhase5Test.kt:30 declares `class InformationRequestTemplateWalkingSkeletonPhase5Test` (covers sparse drafts and structured completeness). Phase6Test.kt:48 (covers supporting-record files, scan quarantine and summary linkage). Phase7Test.kt:43 (covers staged submission, multi-party assertion, amendments and supplements). Phase8Test.kt:48 (covers quorum/consensus review, return and correction). Phase9Test.kt:45 (covers automation, clocks, reconciliation, export, privacy, holds and disposal). InformationRequestEventConsumptionContractTest.kt:144 has `PHASE_NINE_MUTATIONS.forEachIndexed`, and line 232 has `val PHASE_NINE_MUTATIONS = listOf("CHANGE_COMPLETION_GATE","START_CLOCK",...,"RECORD_ESCALATION")`. A grep of src/main/kotlin found no plan-phase references in IR production code.

Fix outline:

Test-only rename, with no migration and no production change. Rename each file and class after the capability it exercises, for example: Phase5Test -> InformationRequestWalkingSkeletonResponseCompletenessTest; Phase6Test -> InformationRequestWalkingSkeletonEvidenceFilesTest; Phase7Test -> InformationRequestWalkingSkeletonSubmissionAmendmentTest; Phase8Test -> InformationRequestWalkingSkeletonReviewCorrectionTest; Phase9Test -> InformationRequestWalkingSkeletonClockRecordLifecycleTest. Use git mv so history is kept. In InformationRequestEventConsumptionContractTest.kt, rename PHASE_NINE_MUTATIONS to CLOCK_AND_COMPLETION_GATE_MUTATIONS at lines 144 and 232. Grep for any other references to the old class names, such as cross-test helper imports or surefire includes. Optionally update the plan's Done lines (2747, 2951, 3158, 3448) to the new names. Then run the focused Maven tests for the renamed classes, followed by ./mvnw test.

### GA-158: Delegated-authority tests use a legal-domain 'power-of-attorney' fixture

- Severity: low. Verification: confirmed. Fix size: S. Audit key: `rules-sweep#3`.
- Plan reference: AGENTS.md rules sweep. Plan basis: Plan lines 140-161 (Industry-Neutrality Constraint): "Tests, reusable fixtures, seed data, example Templates, and executable acceptance scenarios must use neutral synthetic process names and content" (lines 158-161). Line 1271 repeats the neutral-terminology requirement for tests. I found nothing in the Status section, the Development-Stage Constraint, the non-goals, or later design decisions that relaxes or defers this for tests. The AGENTS.md Industry-Neutral Platform Rules state the same [truncated]

Current state:

Two Information Request delegated-authority tests use the legal-industry instrument name "power-of-attorney:doc-42" as the authorityInstrumentRef fixture value. They pass it in and then assert it comes back. This breaks the AGENTS.md rule and the plan's Industry-Neutrality Constraint, which both require tests and fixtures to use neutral synthetic terminology. Production code is neutral: authorityInstrumentRef is a free-text reference, and no production path branches on this value. So this is a test-fixture naming problem only. Separately, the legacy platform seed migration V22__platform_seed_data.sql:63-69 also contains "Power of Attorney" with a "legal" tag. That predates this program and is outside the claim, but the same rule applies to it.

Evidence:

src/test/kotlin/com/docuhyphen/app/api/service/informationrequest/InformationRequestDelegatedAuthorityServiceTest.kt:94 sets authorityInstrumentRef = "power-of-attorney:doc-42", and line 104 asserts that value. src/test/kotlin/com/docuhyphen/app/api/resource/informationrequest/InformationRequestDelegatedAuthorityResourceContractTest.kt:92 sets the same value, and line 109 asserts it. A case-insensitive grep for "attorney" across src/ finds only these 4 test lines plus the legacy seed src/main/resources/db/migration/V22__platform_seed_data.sql:63,69.

Fix outline:

In both test files, replace "power-of-attorney:doc-42" with a neutral synthetic reference such as "authority-instrument:doc-42" or "delegation-record:doc-42". Change it in both places in each file: the command construction and the assertion. That is InformationRequestDelegatedAuthorityServiceTest.kt:94 and 104, and InformationRequestDelegatedAuthorityResourceContractTest.kt:92 and 109. No production code change is needed, and no migration is needed, so V151 stays free. Run the two test classes to confirm they still pass. The legacy V22 seed row is a separate item: it is outside this program, and changing it would need a new migration. It is out of scope for this claim.

### GA-159: Styled IR child components borrow a parent's Styles file instead of their own co-located one

- Severity: low. Verification: confirmed. Fix size: M. Audit key: `rules-sweep#4`.
- Plan reference: AGENTS.md rules sweep. Plan basis: Plan line 3658 ("Put all styles in co-located `*Styles.tsx` files using Fluent UI `makeStyles` and tokens") restates the AGENTS.md frontend rules. Nothing in the Status section, the Development-Stage Constraint or the non-goals waives or defers this rule.

Current state:

There are 11 styled IR child components, each in its own folder with no Styles file. Each one calls another folder's makeStyles hook through a ../ import. On top of that, 7 child components in structured-response-workspace/ and ContactProofPanel in respondent-workspace/ are styled but share their parent's Styles file instead of having their own folder and Styles file. The claim overstates two points. InformationRequestStructuredResponsePanel uses no styles, and InformationRequestStructuredResponseWorkspace is the rightful owner of its Styles file, so 7 components share it, not 9. ReviewFindingChoice imports no Styles at all (it is just a Field and a Dropdown), so the AGENTS.md styling-folder rule does not apply to it. This is only a rule violation. Behaviour is not affected.

Evidence:

Cross-folder imports (each folder holds only its component .tsx):
- authoring/accepted-fact-row/AcceptedFactRow.tsx:9 and authoring/promotable-answer-row/PromotableAnswerRow.tsx:3 import ../accepted-facts-section/AcceptedFactsSectionStyles.tsx
- authoring/business-decision-row/BusinessDecisionRow.tsx:5 imports ../business-decisions-section/BusinessDecisionsSectionStyles.tsx
- authoring/lineage-list/LineageList.tsx:7 imports ../follow-up-panel/FollowUpPanelStyles.tsx
- operations/clock-policy-row/ClockPolicyRow.tsx:3 imports ../clock-policies/ClockPoliciesPanelStyles.tsx
- operations/privacy-request-list/PrivacyRequestList.tsx:4, privacy-subject-list/PrivacySubjectList.tsx:3 and subject-restriction-list/SubjectRestrictionList.tsx:3 import ../privacy/PrivacyPanelStyles.tsx
- review/review-assignment-row/ReviewAssignmentRow.tsx:4, review-decision-history/ReviewDecisionHistory.tsx:9 and review-item-conversation/ReviewItemConversation.tsx:3 import [truncated]

Fix outline:

This is a frontend-only change. No migration or backend work is needed.

1. For each of the 11 cross-folder components, add a co-located XStyles.tsx next to it (for example accepted-fact-row/AcceptedFactRowStyles.tsx exporting useAcceptedFactRowStyles).
2. Move only the classes that component uses out of the parent Styles file into the new file.
3. Remove those classes from the parent file when the parent no longer uses them.
4. Switch the component's import to its own hook.
5. In structured-response-workspace/, move the 7 styled children into their own subfolders, for example structured-response-toolbar/StructuredResponseToolbar.tsx plus StructuredResponseToolbarStyles.tsx. Existing subfolders like response-requirement-header already follow this pattern. Split the relevant classes out of InformationRequestStructuredResponseWorkspaceStyles, and move StructuredResponseRequirement.test.tsx along with its component.
6. Move ContactProofPanel into respondent-workspace/contact-proof-panel/ with its own ContactProofPanelStyles.tsx.
7. Leave ReviewFindingChoice as it is.
8. Update imports, then run `npx tsc --noEmit` and the vitest suites for the affected folders to confirm nothing changed visually or behaviourally.

No help-docs update is needed because nothing user-visible changes.

### GA-160: About 45 rendered IR elements have no id (Text, Tooltip, Field, Badge, div, li)

- Severity: low. Verification: confirmed. Fix size: M. Audit key: `rules-sweep#5`.
- Plan reference: AGENTS.md rules sweep. Plan basis: The requirement comes from AGENTS.md (Front-end rules: "Add html/react ids to all components"). The plan repeats it at line 3660: "Add stable IDs to every added or updated HTML and React element and use circular Buttons." I searched the plan for a waiver or narrowing of the id rule and found none.

Current state:

Many rendered elements in the Information Request frontend have no id attribute. This breaks the AGENTS.md front-end rule and the plan's own requirement for stable IDs on every added or updated element. I spot-checked 11 of the cited locations and every one lacks an id:
- Text elements in FollowUpPanel, LineageList, SubmissionReviewDialog and RequirementEvidenceAttributes
- the Badge in RecipientPreviewDialog
- the "Verification code" Field in ContactProofPanel
- the Tooltip wrappers in TemplateItemActions
- the actions div in ReusableAnswerOffer
- the li in ReviewStageSummary
- the Spinners in RecordExportsPanel and InformationRequestRespondentWorkspace

A rough grep for Text/Tooltip/Badge/Spinner/Field/li/div opening lines without an id across information-requests/*.tsx returns 65 lines. That count includes false positives, because multi-line tags can carry an id on a later line. So the auditor's figure of about 45 is plausible. The children inside these wrappers (Buttons, Inputs, Checkboxes) already have ids. Only the wrappers and display elements are missing them.

Evidence:

- web-app/src/app/information-requests/authoring/follow-up-panel/FollowUpPanel.tsx:51 `<Text>{recurrenceSentence(recurrence)}</Text>`
- authoring/lineage-list/LineageList.tsx:25 `<Text>` with no id
- authoring/recipient-preview-dialog/RecipientPreviewDialog.tsx:61 `<Badge appearance shape>` with no id
- respondent-workspace/ContactProofPanel.tsx:67 `<Field label={"Verification code"}>` (the inner Input has an id)
- template-document/template-item-actions/TemplateItemActions.tsx:36 `<Tooltip content relationship>` (the inner Button has an id)
- reusable-answer/reusable-answer-offer/ReusableAnswerOffer.tsx:83 `<div className={styles.actions}>`
- review/review-stage-summary/ReviewStageSummary.tsx:66 `<li key className>`
- operations/record-exports/RecordExportsPanel.tsx:32 `<Spinner size={"tiny"}/>`
- respondent-workspace/InformationRequestRespondentWorkspace.tsx:86 `<Spinner size label>`
- submission/submission-review-dialog/SubmissionReviewDialog.tsx:36-37 two `<Text>` elements
- [truncated]

Fix outline:

This is a frontend-only change. No migration or backend work is needed.

1. In each of the 20 listed files, add stable, descriptive ids to every Text, Badge, Field, Tooltip-wrapped element, div, li and Spinner that lacks one:
   - Derive them from the component's existing id prefix, e.g. `${id}-recurrence-text`.
   - For items rendered from a list, add the index or key, e.g. `${id}-rule-${index}`.
   - Tooltip in Fluent UI v9 does not render its own DOM node, so the id belongs on the child. The child Buttons already have ids, so the Tooltip case is effectively already satisfied.
2. Follow the multi-attribute formatting rule: once a tag gains a second attribute, put each attribute on its own line.
3. Run `grep -rnE "<(Text|Badge|Spinner|Field|li|div)\b" web-app/src/app/information-requests` and check every hit, including multi-line tags, until none is missing an id.
4. Run `npx vite build` in web-app to confirm the build still passes. Optionally add a Vitest DOM assertion for key ids.

No help-docs update is needed.

### GA-161: Several IR services are oversized multi-responsibility classes

- Severity: low. Verification: confirmed. Fix size: L. Audit key: `rules-sweep#6`.
- Plan reference: AGENTS.md rules sweep. Plan basis: The requirement comes from the AGENTS.md backend rule on SOLID and class size. I searched the plan for these class names and for terms like SOLID, maintainability and single responsibility. The plan names these services only as the places where work was delivered (lines 1588, 2075-2092, 2108, 2128, 2203). No waiver, deferral or non-goal (304-346) exempts them from the class-size rule, and the Development-Stage Constraint (168-265) does not affect this.

Current state:

The numbers in the claim are correct, and nothing in the plan waives the AGENTS.md rule "Use SOLID software design principles so that classes are not too big."

1. InformationRequestPartyService.kt (1008 lines) has 15 constructor collaborators. Its public API does several unrelated jobs: assign (99), assignExternalParticipant (121), assignTrustedRecipientSelection (145), revoke (169), reassign (191), materializeBlueprintDefaultParties (212), materializeSubjectParties (227), revokeForOwnershipChange (580), and a companion partyCapacityReservationKey (1001).

2. InformationRequestResponseDraftService.kt (858 lines) has 20 collaborators. It mixes a thin data-access layer (findCurrentForRequest, findAllForRequest, findCurrentForUpdate, save, update at lines 54-62) with the whole patch workflow (patch at 128). The patch workflow covers condition evaluation, structured validation, locking, receipts and history.

3. InformationRequestGroupOccurrenceService.kt (775 lines) has 25 collaborators, 16 of them repositories. It exposes only add, remove and reorder (122, 137, 147). Its size comes from copying a Template group into requirements, bindings, evidence policies, substitutes and evidence links inside the service.

4. InformationRequestTemplateConfigurationValidator.kt (815 lines) has one collaborator and a single public normalize(). It has one job (validating and normalizing the Template configuration), so this is only a size issue, not mixed responsibilities. That makes this part partial.

Other rule issues seen in passing (outside this claim): the draft and occurrence services [truncated]

Evidence:

- src/main/kotlin/com/docuhyphen/app/api/service/informationrequest/InformationRequestPartyService.kt: 1008 lines (wc). Constructor at lines 80-96 has 15 params. Public funs at 99, 121, 145, 169, 191, 212, 227, 580; companion key at 1001.
- InformationRequestResponseDraftService.kt: 858 lines. Constructor at 104-125 has 20 params. Public data-access funs at 54-62; patch at 128.
- InformationRequestGroupOccurrenceService.kt: 775 lines. Constructor at 93-119 has 25 params, 16 of them repositories. Public add/remove/reorder at 122, 137, 147.
- InformationRequestTemplateConfigurationValidator.kt: 815 lines. Class at 43 has no injected collaborators, only a local FieldTypeRegistry. One public normalize at 51 and 31 private funs.
- These four are the four largest files in service/informationrequest (the next largest is InformationRequestTemplateMaterializer.kt at 544 lines).

Fix outline:

This is a behaviour-preserving refactor. No migration is needed (V151 stays free). All new classes are @ApplicationScoped in service/informationrequest/.

1. PartyService: split into:
   - InformationRequestPartyAssignmentService (assign, assignExternalParticipant, assignTrustedRecipientSelection)
   - InformationRequestPartyRevocationService (revoke, reassign, revokeForOwnershipChange)
   - InformationRequestPartyMaterializationService (materializeBlueprintDefaultParties, materializeSubjectParties)
   Move partyCapacityReservationKey to a small InformationRequestPartyCapacityKeys object. Keep the shared lock, receipt and history plumbing in an internal helper. Update the resources and callers that inject the old service.
2. ResponseDraftService: move the find/save/update pass-throughs to an InformationRequestResponseQueryService (or into the existing InformationRequestResponseStore). Pull condition/validation orchestration into an InformationRequestResponsePatchValidator. Keep patch as a thin coordinator.
3. GroupOccurrenceService: move the Template-to-runtime requirement/binding/evidence copying into an InformationRequestGroupOccurrenceMaterializer. That removes most of the 16 repositories from the service. Keep add/remove/reorder as orchestration.
4. Validator: split it into rule-focused validators behind the existing normalize() facade, e.g. group/requirement structure, field typing, conditions, evidence policy.
5. While doing this, replace direct ExchangeRepository/SchemaAssignmentRepository/FieldValueSetRepository/FieldContractRepository use with calls to the owning [truncated]

### GA-162: Personally owned requests never start the owner's personal Workflows; only platform-scope definitions run

- Severity: low. Verification: confirmed. Fix size: M. Audit key: `wiring-sweep#2`.
- Plan reference: Wiring sweep. Plan basis: Plan line 3238-3240 (Phase 9 decision 3): the Workflow trigger consumer starts matching definitions with subject type INFORMATION_REQUEST. Lines 4121-4128 (P12-T6): complete Personal Information Request support; personal owners may create and issue requests. Lines 387, 504 and 1411: events must carry an explicit owner kind and ID so personal events can be told apart; line 4671 also requires tenant-safe personal event owner scopes. The help article requestTriggerEventsArticle.tsx lines 4-5 says [truncated]

Current state:

Workflow trigger resolution cannot reach PERSONAL-scope definitions. For a personally owned Information Request, the published DomainEvent carries organizationId = null. DomainEvent has no owner kind or owner ID field, although the outbox row stores them. The trigger consumer forwards only that null organizationId, and TriggerRequest has no owner-user field. DefaultWorkflowEngineService.trigger calls WorkflowDefinitionRepository.findAllActiveForTrigger(triggerEvent, organizationId). With a null org, that method skips the ORG tier and returns only active APP definitions. It never queries WorkflowScope.PERSONAL. Personal owners can still create, activate and give request triggers to 'My Workflows' definitions: neither the designer nor WorkflowSpecValidator restricts this. Those definitions never run for the owner's own requests, while every active APP definition with the same trigger runs for all personal requests. The same lookup serves Exchange triggers, so personal Exchange Workflows have the same problem. No test covers a PERSONAL definition firing.

Evidence:

WorkflowDefinitionRepository.kt:19-47: findAllActiveForTrigger queries scope ORG with organizationId (only when it is non-null) and otherwise scope APP; PERSONAL appears only in findAllAccessibleForCaller (lines 90, 113). DefaultWorkflowEngineService.kt:116: the only caller, passing request.organizationId. WorkflowEngineService.kt:80-87: TriggerRequest has triggerEvent, subjectResourceType, subjectResourceId, organizationId, subjectData and initiatedByAppUserId, with no owner-user field. InformationRequestWorkflowTriggerConsumer.kt:29-38: organizationId = event.organizationId only. InformationRequestTransitionHistoryService.kt:117: organizationId = command.request.ownerOrganizationId?.toString(), which is null for a personal request; the owner is passed to publish() separately and is not on the DomainEvent. DomainEvent.kt:26-33 has no owner fields. WorkflowDefinitionService.kt:294-309 creates PERSONAL definitions. WorkflowDesigner.tsx supports scope 'PERSONAL' with no request-trigger [truncated]

Fix outline:

1) Carry the owner through the trigger path. Either add ownerKind and ownerId to DomainEvent, filled in InformationRequestTransitionHistoryService from the same owner it already passes to publish(), or have the consumer resolve the request's ownerAppUserId through an InformationRequest service method. 2) Add ownerAppUserId: UUID? to TriggerRequest and set it in InformationRequestWorkflowTriggerConsumer, and in the Exchange trigger callers for personally owned Exchanges. 3) Change WorkflowDefinitionRepository.findAllActiveForTrigger(triggerEvent, organizationId, ownerAppUserId). When organizationId is null and ownerAppUserId is set, query active PERSONAL definitions with createdByAppUserId = ownerAppUserId first; if any exist, return them, otherwise fall back to APP. This mirrors the existing ORG-overrides-APP tier. 4) Update DefaultWorkflowEngineService.trigger to pass the owner, and make subscriptionGuard.requireInstanceStart evaluate PERSONAL definitions against the owner's personal subscription context (WorkflowSubscriptionGuard already has a PERSONAL branch). 5) Tests: repository and engine tests showing that a PERSONAL active definition fires for its owner's personal request and not for another user's request or an org request; that it overrides APP; and that APP still runs when no personal definition exists. Add a consumer test with a personal-owner event and a PostgreSQL-backed integration test. No migration is needed. 6) Update requestTriggerEventsArticle and the workflowsSection help to state which scopes run for personal versus organization requests.

Decision needed. Recommended default: Yes, for request triggers only: PERSONAL definitions replace APP definitions, as ORG definitions do today. Exchange triggers stay unchanged.

### GA-163: Access-link replacement and author-set expiry or use limits are REST-only, though help describes them

- Severity: low. Verification: partial. Fix size: S. Audit key: `wiring-sweep#3`.
- Plan reference: Wiring sweep. Plan basis: P4-T4 (plan lines 1818-1858) requires rotate/replace/revoke only at the service layer ("Complete at the service layer"), and that is implemented. Phase 10 design decisions (2026-09-27), line 3550 decision 6: "A link is issued per acting party and shown once... 'Resend' rotates the link", which scopes the UI to issue, show once, resend and revoke, with no replace or custom-limit UI. P12-T5d (line 4114) adds access-link default expiry and use limit, which covers links without author-set limits. [truncated]

Current state:

The code facts in the claim are accurate. POST /information-requests/{id}/access-links/{shareLinkId}/replacement exists and accepts expiresAt/maxUses. The issue endpoint also accepts expiresAt/maxUses. The frontend has no replace function in informationRequestAuthoringService.ts. useAuthorWorkspace.ts issues links with {partyId} only, rotates for "Resend link" and revokes. It never sends expiresAt or maxUses, even though IssueInformationRequestAccessLinkRequest declares both. The missing UI is not a gap against the plan, though. P4-T4 required replace at the service layer, and it is implemented there. The Phase 10 design decisions (decision 6) scope the author UI to three actions: issue a link per acting party, show it once, and "Resend" by rotating it. They do not call for a replace action or custom expiry and use-limit inputs. P12-T5d covers links without limits through platform defaults. What remains is a help-accuracy problem under the AGENTS.md rule. informationRequestAccessArticle.tsx says "Replacing or revoking the link ends its sessions". It also says "A link created without its own expiry or use limit works for 30 days and for 25 verifications", and it describes "When a link has a use limit" as though authors can choose one. From the UI, every link gets the default expiry and use limit, and the author's link actions are Resend (rotate) and revoke. The help presents API-only options as author choices and uses the word "replacing" where the UI calls it "Resend link".

Evidence:

src/main/kotlin/com/docuhyphen/app/api/resource/informationrequest/InformationRequestAccessLinkResource.kt: issue passes request.expiresAt/maxUses (~lines 72-73); @POST @Path("/{shareLinkId}/replacement") replace(...) takes ReplaceInformationRequestAccessLinkRequest with expiresAt/maxUses (~lines 120-154). web-app/src/services/informationRequestAuthoringService.ts:126-156 has get/issue/rotate/revoke only, with no replacement call. The only "replacement" matches in web-app/src are for supersede and evidence. web-app/src/app/information-requests/authoring/author-workspace/useAuthorWorkspace.ts:106-113: issueLink sends {partyId: party.id}, resendLink calls rotateInformationRequestAccessLink, and revokeLink calls revoke. web-app/src/app/models (line ~4299): IssueInformationRequestAccessLinkRequest has optional expiresAt?/maxUses?, which the UI never sets. web-app/src/app/components/help-docs/sections/articles/informationRequestAccessArticle.tsx:14-15 ("Replacing or revoking the link ends [truncated]

Fix outline:

The minimal fix, consistent with the Phase 10 decision, touches help text only. 1) Edit web-app/src/app/components/help-docs/sections/articles/informationRequestAccessArticle.tsx. Change "Replacing or revoking the link ends its sessions" to "Resending (which creates a new link) or revoking the link ends its sessions". Replace the use-limit sentences with "Each access link works for 30 days and for 25 successful verifications; each successful verification uses one, while saving and reading use the verified session instead." Drop the "own expiry or use limit" phrasing. Keep the article under 150 lines, and run `npx tsc --noEmit` in web-app. The alternative, only if the user wants authors to control limits, is to add replaceInformationRequestAccessLink(requestId, shareLinkId, {expiresAt?, maxUses?}, partyETag, key) to informationRequestAuthoringService.ts. Add a ReplaceInformationRequestAccessLinkRequest model, optional expiry/use-limit inputs in the issue-link dialog, and a Replace action in the party link controls wired through useAuthorWorkspace. Add Vitest coverage for the new service call and dialog. No migration is needed in either case (the next free migration is V151 if one is ever needed).

### GA-164: Expiry refresh rules have no screen, while help says a follow-up can come from a refresh set up for the request

- Severity: low. Verification: confirmed. Fix size: M. Audit key: `wiring-sweep#4`.
- Plan reference: Wiring sweep. Plan basis: Plan lines 2940-2947 (P7-T7c: expiry refresh rules and the refresh command, V135; Phase 9 owns the scheduler). Line 48 (Status: refresh follow-ups done). Lines 3619-3621 (P10-T2: author creation and dispatch including schedule, supersede and supplemental actions; refresh is not named explicitly, but the author follow-up actions are in force). Line 4463 (V135 refresh rules). AGENTS.md help-accuracy steps require the help to match behaviour. I found nothing in the plan that defers or waives a UI [truncated]

Current state:

The backend exposes POST /information-requests/{id}/refresh-rules (define a rule for a requirementKey plus leadDays, with If-Match and Idempotency-Key) and POST /information-requests/{id}/refresh-rules/{ruleId}/refreshes (create a REFRESH successor). The web app has no service function and no screen for either. A search of web-app/src finds no "refresh-rules" or "refreshRule" call. The only refresh-related frontend code is the REFRESH lineage kind enum and the optional refreshRuleId on the lineage model (models.tsx:2990, 3009), plus the "Refreshed request" label in followUpLabels.ts:39-40. That code can show a refresh successor that already exists but cannot create one. Recurrence is fully wired by comparison: informationRequestAuthoringService.ts:164-178 provides defineInformationRequestRecurrence and createNextInformationRequestOccurrence, with tests. The help article informationRequestSubmissionArticle.tsx:107-108 says a follow-up can "come from a recurring schedule or an expiry refresh set up for the request". No user can set up an expiry refresh from the UI, so the refresh half of that sentence is inaccurate. The missing automatic expiry trigger and duplicate guard are a separate item (G143).

Evidence:

src/main/kotlin/com/docuhyphen/app/api/resource/informationrequest/InformationRequestRefreshRuleResource.kt:23-86 (define and refresh endpoints delegating to InformationRequestFollowUpService.defineRefreshRule/refresh). The only callers of defineRefreshRule are InformationRequestFollowUpService.kt, the resource and InformationRequestLineageTransactionTest.kt. A search of web-app/src for refresh-rules, refreshRule and defineRefresh finds no service function or component. web-app/src/app/models/models.tsx:2990 (REFRESH lineage kind) and :3009 (refreshRuleId?). web-app/src/app/information-requests/authoring/follow-up-panel/followUpLabels.ts:39-40 (display label only). web-app/src/services/informationRequestAuthoringService.ts:164-178 (recurrence is wired; refresh is not). web-app/src/app/components/help-docs/sections/articles/informationRequestSubmissionArticle.tsx:106-108 (the help claim).

Fix outline:

Frontend only; no migration needed (V135 already has refresh rules). 1) In web-app/src/services/informationRequestAuthoringService.ts, add defineInformationRequestRefreshRule(requestId, {requirementKey, leadDays}, requestETag, idempotencyKey), which POSTs to `${requestPath}/refresh-rules` with the If-Match and Idempotency-Key headers. Also add createInformationRequestRefresh(requestId, ruleId, idempotencyKey), which POSTs to `/refresh-rules/{ruleId}/refreshes`. Add DefineInformationRequestRefreshRuleRequest and the DTO types to models.tsx. 2) Add a RefreshRuleForm component, with a co-located styles file, to the authoring follow-up panel next to RecurrenceForm. It needs a requirement picker (from the request's evidence requirements), a lead-days input and circular buttons with ids. Wire it through useFollowUps, including a "Refresh now" action on each defined rule if the lineage DTO lists rules. If the lineage read does not return rules, add them to the lineage DTO or add GET /information-requests/{id}/refresh-rules. 3) Add service tests in services/__tests__/informationRequestAuthoringService.test.ts and a component test. 4) Update informationRequestSubmissionArticle.tsx, and the authoring help article, to describe where the refresh is set up. The interim alternative is to remove the "or an expiry refresh" wording from the help until a UI exists. Run npx tsc --noEmit.

### GA-165: Operations queue UI cannot filter by request state, Exchange or exception kind, so finished requests always appear

- Severity: low. Verification: confirmed. Fix size: M. Audit key: `wiring-sweep#5`.
- Plan reference: Wiring sweep. Plan basis: - Plan line 3278-3281 (Phase 9 decision 8): the operations projection is "filtered by state, Exchange, SLA status, and exceptions".
- Plan line 3634-3635 (P10-T6): operational work queues with search, filters, aging, deadlines, assignees, delivery status and exceptions.
- Plan line 3574-3581 (Phase 10 decision 10) adds search and an assignee filter to the queue. It adds to the decision 8 filters and does not replace them.

Nothing in the Status section, the Development-Stage Constraint, the [truncated]

Current state:

The backend supports every filter decision 8 names. InformationRequestOperationsResource.queue accepts repeated `state`, `exchangeId`, repeated `slaStatus`, repeated `exception` and `exceptionsOnly`, and InformationRequestOperationsService.queue applies all of them. By default it returns every request in the owner scope, whatever its state.

The frontend uses only part of this:
- The InformationRequestOperationsFilter model has fields for search, assigneeId, one slaStatus, exceptionsOnly, limit and offset. It has no states, exchangeId or exceptions fields.
- getInformationRequestOperations sends only those parameters.
- OperationsQueueFilters shows only these controls: Search, Assigned to, a single-select Service level, and an Exceptions only switch.

As a result, the operations queue always lists closed, cancelled, superseded and expired requests together with open ones. It cannot be narrowed to one Exchange, to a set of SLA statuses, or to one exception kind (for example a notice failure). No other component or service in the operations area sends these parameters. Nothing in the plan removes, narrows or postpones this requirement.

Evidence:

- src/main/kotlin/com/docuhyphen/app/api/resource/informationrequest/InformationRequestOperationsResource.kt:26-34 accepts state, exchangeId, search, assigneeId, slaStatus (list), exception (list), exceptionsOnly, limit and offset. Lines 39-50 parse them into the filter.
- InformationRequestOperationsService.queue (around lines 36-56) applies the filters: `filter.states.isEmpty() || it.state in filter.states`, the exchangeId check, and the slaStatuses and exceptions filters. With no state given, every request from findForOwner is returned, including terminal ones.
- web-app/src/app/models/models.tsx:3129-3137: InformationRequestOperationsFilter contains only search, assigneeId, slaStatus?, exceptionsOnly, limit and offset.
- web-app/src/services/informationRequestOperationsService.ts:28-38: the params sent are search, assigneeId, slaStatus, exceptionsOnly, limit and offset. No state, exchangeId or exception is sent.
- [truncated]

Fix outline:

Frontend only. No migration and no backend change.

1. In models.tsx, extend InformationRequestOperationsFilter with:
   - `states: InformationRequestState[]`
   - `exchangeId?: string`
   - `slaStatuses: InformationRequestSlaStatus[]` (or keep the single slaStatus)
   - `exceptions: InformationRequestOperationsException[]`
2. In informationRequestOperationsService.ts, send these as `state`, `exchangeId`, `slaStatus` and `exception`. Use axios `paramsSerializer: {indexes: null}` so arrays go out as repeated keys, which is what JAX-RS List params expect.
3. In OperationsQueueFilters.tsx, add controls, splitting into child components to stay under ~150 lines:
   - A multi-select state Dropdown. It should default to the non-terminal states, so open work shows first, with an option to include terminal states.
   - An Exchange picker. A Dropdown built from the Exchanges in the current rows or from the Exchange list service is enough.
   - A multi-select exception-kind Dropdown using exceptionLabels.
   Put styles in OperationsQueueFiltersStyles.tsx, add ids to every control, and use circular buttons.
4. In the operations page, hold the new filter state and reset offset to 0 when any filter changes. If the CSV export uses the queue filter, pass the new filters to it as well.
5. Tests:
   - Extend InformationRequestOperations.test.tsx and add an OperationsQueueFilters test asserting that choosing a state, an Exchange and an exception kind sends `state=...&exchangeId=...&exception=...`, and that the default excludes terminal states.
   - Add a backend resource test for repeated [truncated]

### GA-166: Dead and misleading Information Request code: an unused listing endpoint and frontend read, uncalled service methods, an unused Action, and a stale resource comment

- Severity: low. Verification: confirmed. Fix size: S. Audit key: `wiring-sweep#6`.
- Plan reference: Wiring sweep. Plan basis: The Development-Stage Constraint (plan lines 168-265) forbids compatibility code, and DS-T4 required a sweep that removed superseded aliases, such as the superseded PUT /exchanges/{id}/fields alias. The old list endpoint survives in the same way. Plan line 3553 makes GET /exchanges/{exchangeId}/information-requests the Exchange-tab listing. Plan line 1985 keeps the backend GET /information-requests/{id} as a planned detail projection, so only the unused frontend helper is dead. Nothing in the [truncated]

Current state:

Several leftover paths remain in the Information Request code. (1) InformationRequestResource.list (GET /information-requests?exchangeId=, lines 44-58) has no frontend caller. The frontend uses getExchangeInformationRequests, which calls GET /exchanges/{exchangeId}/information-requests (InformationRequestExchangeListingResource). Only InformationRequestResourceContractTest uses list. (2) The export getInformationRequest in web-app/src/services/informationRequestAuthoringService.ts:73 is referenced only from services/__tests__. The backend GET /information-requests/{id} endpoint is still a planned detail projection (plan line 1985), so only the frontend helper is dead. (3) InformationRequestAttestationEvaluationService.attestationsFor (line 66) and InformationRequestConnectorRegistry.installedKeys (InformationRequestConnector.kt:48) have no callers at all. SchemaAssignmentService.assignPublishedSchemaVersion (line 84) is called only by SchemaAssignmentExactVersionTest and InformationRequestTemplateMaterializationTransactionTest. (4) Action.INFORMATION_REQUEST_REASSIGN_PARTY (Action.kt:142) is never passed to authorize(). Only the vocabulary test lists it, and InformationRequestPartyService.authorize (around line 806) gates reassignment with INFORMATION_REQUEST_MANAGE_PARTIES. (5) The KDoc on InformationRequestResource (lines 25-30) says issuance and respondent-facing actions are "deliberately not exposed here", but the class serves POST /{id}/issuance and GET /{id}/response-workspace. The comment also describes functionality, which AGENTS.md forbids. All of these are [truncated]

Evidence:

InformationRequestResource.kt:25-30 is the KDoc that says "Issuance and every respondent-facing action are deliberately not exposed here". Lines 44-58 are the @GET list with @QueryParam exchangeId. Lines 75-77 serve GET /{id}/response-workspace and lines 153-154 serve POST /{id}/issuance. InformationRequestExchangeListingResource.kt:15 declares @Path("/exchanges/{exchangeId}/information-requests"). informationRequestAuthoringService.ts:67-68 has getExchangeInformationRequests using /exchanges/${exchangeId}/information-requests, and line 73 exports getInformationRequest; grep finds no non-test importer. A grep across src/ and web-app/src for attestationsFor, installedKeys and assignPublishedSchemaVersion finds only the definitions (InformationRequestAttestationEvaluationService.kt:66, InformationRequestConnector.kt:48, SchemaAssignmentService.kt:84) plus the two tests SchemaAssignmentExactVersionTest.kt:48 and InformationRequestTemplateMaterializationTransactionTest.kt:82. A grep for [truncated]

Fix outline:

No migration is needed. (1) Remove InformationRequestResource.list along with its now-unused queryService dependency, if nothing else needs it. Update InformationRequestResourceContractTest to drop the list assertions (lines 75-76, 179-185, 415), or move them to the exchange listing contract test. (2) Remove getInformationRequest from informationRequestAuthoringService.ts and its case in services/__tests__/informationRequestAuthoringService.test.ts. (3) Delete InformationRequestAttestationEvaluationService.attestationsFor and InformationRequestConnectorRegistry.installedKeys. For assignPublishedSchemaVersion, either delete it and move the two tests to assignSchemaVersionForCreation, or have production call it. Also delete PublishedSchemaAssignmentCommand if nothing else uses it. (4) Either remove Action.INFORMATION_REQUEST_REASSIGN_PARTY and its entry in InformationRequestAuthorizationVocabularyTest, or have InformationRequestPartyService authorize the reassign path with REASSIGN_PARTY and keep MANAGE_PARTIES for add/remove. Removing it is simpler, since both actions map to the INFORMATION_REQUEST_ADMIN capability. (5) Delete the KDoc on InformationRequestResource, or cut it to a neutral non-descriptive form under AGENTS.md. Afterwards run the backend tests and npx tsc --noEmit plus vitest in web-app. None of this changes user-visible behaviour, so no help-doc update is needed. Check that help docs do not mention GET /information-requests?exchangeId.

### GA-167: Every Exchange requires at least one document, even when its Blueprint's Information Request collects the documents

- Severity: high. Verification: confirmed by an investigator and an independent re-trace. Fix size: M.
- Plan reference: none; the plan never addressed the Exchange document rule. Scheduled as
  `P13-T3`, with design decisions 5 and 6 in Phase 13 of the implementation plan.

Current state:

The web app refuses to start any Exchange with no documents (`ExchangeInitiation.tsx:568-573`). Its
message says "when requesting documents", but the check reads neither the start mode nor the
selected Blueprint. The server enforces the same rule without exception
(`ExchangeInitiationService.validateExchangeFields`, `:757-760`, "Session documents cannot be
empty"). Blueprints can be saved with no document slots. A locked Organization or Platform Blueprint
hides Add Document (`ExchangeInitiationDocumentsTab.tsx:80`), so a locked Blueprint whose
Information Request collects the documents cannot be started at all.

Recommended fix:

Allow zero Exchange documents only when the selected Blueprint pins an instantiable Template
Version, applied identically on the server and in the web app, and change the message to "Add at
least one document". Every other Exchange keeps the rule. The documents tab explains that the
Information Request collects the documents. Tests cover no Blueprint, a Blueprint without a Version,
a Blueprint with a Version, and a locked Blueprint with no document slots.

### GA-168: Starting an Exchange from a Blueprint that pins a Template Version never creates the Information Request

- Severity: high. Verification: confirmed by an investigator and an independent re-trace. Fix size: M.
- Plan reference: none scheduled it. Phase 10 decision 2 defined only manual creation through
  `POST /information-requests`. Scheduled as `P13-T2`, `P13-T4`, `P13-T5`, `P13-T6`, and `P13-T7`,
  with design decisions 1 to 4, 7, and 8 in Phase 13.

Current state:

`handleBlueprintSelect` (`ExchangeInitiation.tsx:262-339`) never reads `blueprint.id` or
`informationRequestTemplateVersionId`. Neither `ExchangeInitiationRequest` (`models.tsx:172-195`) nor
`ExchangeInitiationDto` (`RequestsResponses.kt:320-343`) carries a Blueprint id, and
`ExchangeInitiationService` has no Information Request dependency. No event consumer or Workflow step
creates a request. The only creator from a Blueprint is `createFromBlueprint`, reached only from the
manual New Information Request dialog. The Exchange's Information Requests tab therefore shows no
request after a Blueprint start, while the Blueprint editor says "Exchanges from this Blueprint start
an Information Request from ..." (`BlueprintInformationRequestTab.tsx:38-39`).

Recommended fix:

- Carry the Blueprint id into initiation. The server resolves the Version itself.
- Create exactly one `DRAFT` request pinned to that Version, in the same transaction as the Exchange,
  with the initiator as `DECISION_MAKER`.
- Refuse the whole start with a stable reason code when the request cannot be created, including a
  Personal Blueprint whose Template owner differs from the Exchange owner.
- Map the new refusals to 409 in `ExchangeResource`.
- Name the created request in the success summary, and align the Blueprint editor copy and help
  articles.
- GA-002 (`P13-T1`) must land first or in the same change, because automatic creation would
  otherwise make every Blueprint un-editable after its first use.

## Refuted Claims

- `G111-STATE-RequestCollectionState` Request collection state ACTIVE is persisted as IN_PROGRESS. Reason: - Lines 748-756 list ACTIVE as a collection state. Lines 2972 and 2974 (Phase 7 tests) and line 4098 (P12-T4f) still say ACTIVE.
- The Phase 9 design decisions (2026-09-26) supersede that wording. Line 3210 says nothing yet reaches "IN_PROGRESS or EXPIRED". Decision 2 at line 3230 says START_RESPONSE "moves ISSUED to IN_PROGRESS" on the first response mutation.
- Later phases' design decisions override earlier vocabulary. The Development-Stage Constraint (lines 168-265) rules out adding an ACTIV
- `G118-P6-T8` Workspace supportingEvidenceLinks projection is never rendered in the respondent UI. Reason: - Lines 2727-2737 (P6-T8): the requirement is materialization, projection and later Submission Package preservation, and the workspace "projects supportingEvidenceLinks only between Requirements the caller may see". Rendering is not required.
- Lines 2758-2773 (P6-T11): the scope of the minimal evidence UI is upload, progress, preview, replace, withdraw and conformance. Link display is not in it.
- Lines 2736-2737 and 2879-2885: the links are frozen into Submission Packages in P7-T1, and those s
- `P12#2` assignedWork ignores work assigned through a group, so group reviewers without the feature lose the review queue link. Reason: Plan lines 3988-3993 (Decision 6: capability discovery reports whether the caller holds assigned work; menu links use it instead of the viewer's plan) and 4092-4096 (P12-T4e done: capability discovery drives menu links). The code meets this for direct and group-assigned callers through materialised inherited Shares. The auditor's reading of the query is correct but misses how Shares are stored. ShareRepository.kt:64-75 (existsActiveForPrincipalOnResourceType) does match only the caller's own pri
- `critic#7` SUBJECT-PICKER: The known-subject picker needs the privacy capability and uses the caller's active scope, so non-admin authors silently get [truncated]. Reason: Plan lines 3544-3549 (P10 decision 5: subject list is "the active owner's subjects ... under INFORMATION_REQUEST_MANAGE_PRIVACY"; a known external reference finds the owner's existing SubjectIdentityRef), lines 3323-3328 (decision 14: privacy capability for org Owners/Admins only, personal owner always permitted), line 3619-3620 (P10-T2 checked complete). InformationRequestSubjectService.kt:64-71 (listForOwner uses ownerScopeAccess.currentOwner() and requires INFORMATION_REQUEST_MANAGE_PRIVACY);
