# Plans

Planning, design, and analysis documents for work that is proposed, paused, or not yet started.

A document belongs here while it still describes work to be done. Once its work ships, either
delete it or fold the durable parts into the code and `AGENTS.md` — this folder is not an archive
of completed features.

Per `AGENTS.md`, code comments must never reference these documents, their file names, or their
phase and task numbers. Shipped code has to stand on its own.

## Index

| Document | Covers | Status |
|----------|--------|--------|
| [DOCUMENT-DRIVEN-INFORMATION-REQUESTS-IMPLEMENTATION-PLAN.md](DOCUMENT-DRIVEN-INFORMATION-REQUESTS-IMPLEMENTATION-PLAN.md) | Business Fields foundation program, phased | Not started. Next task `P1-T1`, secure Schema Assignment read/write. Updated 2026-08-30. |
| [DOCUMENT-DRIVEN-INFORMATION-REQUESTS-PROPOSAL.md](DOCUMENT-DRIVEN-INFORMATION-REQUESTS-PROPOSAL.md) | Product and architecture rationale behind the above | Conceptual, for discussion. Design input to the implementation plan; not a plan itself. |
| [WEBHOOK-INTEGRATIONS-FEATURE.md](WEBHOOK-INTEGRATIONS-FEATURE.md) | Integrations settings, registered applications, outbound webhooks, durable delivery | Implementation design, awaiting review. |
| [EXTERNAL-APPLICATION-INTEGRATION-BUSINESS-SPECIFICATION.md](EXTERNAL-APPLICATION-INTEGRATION-BUSINESS-SPECIFICATION.md) | Business requirements for external application integration | Requirements only. Architecture and sequencing deferred to a later plan. |
| [INFRA-SECURITY-ANALYSIS.md](INFRA-SECURITY-ANALYSIS.md) | Earlier static security review of `infra/` | Reviewed again on 2026-10-01; current assessment is in the browser, infrastructure, and secrets remediation plan. |
| [BROWSER-INFRA-SECRETS-SECURITY-REMEDIATION.md](BROWSER-INFRA-SECRETS-SECURITY-REMEDIATION.md) | Browser protections, production infrastructure, and secrets | Review complete on 2026-10-01. 2 high and 11 medium remediation findings; no fixes applied. |
| [AUTH-SIGNUP-SIGNIN-SECURITY-REMEDIATION.md](AUTH-SIGNUP-SIGNIN-SECURITY-REMEDIATION.md) | Signup and sign-in security findings and remediation | All findings addressed, including AUTH-3B from the 2026-10-01 recheck; 34 focused tests pass. Not committed. |
| [OBJECT-STORAGE-SECURITY-GAPS.md](OBJECT-STORAGE-SECURITY-GAPS.md) | Dead document-encryption path; absent tenant partitioning in object storage | Identified, not scheduled. Open questions must be answered before design starts. |
| [DOCUHYPHEN-VALUE-SECTION-IMPLEMENTATION-PLAN.md](DOCUHYPHEN-VALUE-SECTION-IMPLEMENTATION-PLAN.md) | Marketing site value section | Paused for later continuation. No website source files changed. |

## Notes

- The two Document-Driven Information Requests documents are a pair: the proposal is the design
  input, the implementation plan is the source of truth for execution.
- `OBJECT-STORAGE-SECURITY-GAPS.md` cross-references `INFRA-SECURITY-ANALYSIS.md` by filename;
  both live here, so the reference resolves.
