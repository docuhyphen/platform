# Workspace-Scoped Personal Items Design

## Status

- Stage: design only. Nothing in this document is approved for implementation.
- Created: 2026-09-30, from a code investigation of Exchange initiation, Blueprint scope, and
  Information Request ownership.
- Why it is a design plan and not an implementation plan: the change crosses ownership,
  entitlement, authorization, audit, and six configuration domains. The open decisions below must
  be settled, and the design reviewed, before any task list, migration number, or test plan is
  written.
- Related documents:
  - [Information Requests gap audit](DOCUMENT-DRIVEN-INFORMATION-REQUESTS-GAP-AUDIT.md), especially
    GA-002 (Blueprint placeholder foreign key), GA-023 (Blueprint editor offers platform Template
    Versions), GA-092 (Blueprint help does not cover the Information Request tab), and GA-162
    (personal Workflows never start).
  - [Information Requests implementation plan](DOCUMENT-DRIVEN-INFORMATION-REQUESTS-IMPLEMENTATION-PLAN.md),
    Architectural Decisions 2 and 22, Phase 10 decision 2, and Phase 12 decision 11.

## Problem

Users understand "Personal" (the "My Blueprints", "My Documents", "My Workflows", "My Templates",
"My Communications", and "My Variables" tabs) as "my private items inside the workspace I am
working in". A member of an organization expects a Personal Blueprint to be a private organizer
for their own work in that organization, used for Exchanges that the organization owns.

The code implements a different model: a PERSONAL item is owned by, and billed to, the person's own
account, independent of any workspace. That mismatch produces confusing and broken behavior:

- "My Blueprints" in an organization workspace also lists items created in other organizations or
  in the personal workspace. Those items can reference Schemas, Document Library entries, or
  Templates that do not resolve in the current workspace.
- Personal items are entitlement-checked against the person's own plan. A Free-plan member of a
  Business organization cannot create any Blueprint, because members who are not administrators
  may only create PERSONAL Blueprints, and the Free plan has no Blueprint feature. The help text
  says the opposite.
- A Personal Blueprint can only pin its author's Personal Information Request Template. Because an
  Information Request's owner must equal its Exchange's owner and its Template's owner, that
  Template can never be used in an organization-owned Exchange.
- Personal Workflows never start, and every Workflow clone lands in PERSONAL scope billed to the
  person, which enforcement refuses because no personal plan includes Workflow automation.

## Current Behavior (verified 2026-09-30)

### Exchange ownership

- Ownership comes only from the active workspace. `EndpointAuthorizationFilter` sets the active
  organization only for an active membership and silently falls back to personal mode for a stale
  header (`EndpointAuthorizationFilter.kt:431-457`).
- `ExchangeInitiationService` sets `ownerOrganizationId` from the active organization, otherwise
  `ownerUserId` (`ExchangeInitiationService.kt:174`, `:207-211`). The Blueprint's scope is never
  sent or read.
- A personally owned Exchange is a real persisted state: `owner_user_id` is set (V30 check),
  `ExchangeAuthorizationContextProvider` resolves it to `OwnerContext.Personal`, organization role
  grants give nothing on it, and organization audit refuses it.
- The web app auto-selects a lone organization or forces the organization picker
  (`AuthContext.tsx`). At the time of writing, uncommitted work in the tree adds a "Personal
  workspace" account-menu item and a persisted personal-mode flag. That makes user-owned Exchanges
  an explicit choice for organization members, which this design must account for.

### PERSONAL scope in configuration domains

| Domain | Storage | Entitlement | Visibility |
|---|---|---|---|
| Blueprints | `organization_id` null, owner `createdByAppUserId` (`BlueprintDefinitionService.kt:173-174`) | Management and get-by-id billed to the user (`BlueprintSubscriptionGuard.kt:65-69`, `:104-111`); listing billed to the active organization | Every workspace (`BlueprintDefinitionRepository.kt:34-35`, `:56-57`) |
| Document Library | `organization_id` null (`DocumentLibraryService.kt:87`) | Billed to the user (`DocumentLibrarySubscriptionGuard.kt:65-82`); a denied library-file copy at Exchange start is swallowed (`ExchangeInitiationService.kt:250-253`) | Every workspace |
| Workflows | PERSONAL; non-admins default to PERSONAL; clones always PERSONAL (`WorkflowDefinitionService.kt:294`, `:309-310`, `:499-504`) | Billed to the user; no user plan has `WORKFLOW_AUTOMATION` | Engine never selects PERSONAL definitions (`WorkflowDefinitionRepository.kt:19-47`) |
| Information Request Templates | `scopeUserId` (`InformationRequestTemplateAuthoringService.kt:165-169`) | Active organization sponsors authoring only; use resolves against the person's plan (`InformationRequestTemplateReferenceService.kt:98-106`) | Personal Settings scope |
| Communications | PERSONAL tab | Not yet investigated | Not yet investigated |
| Variables and Sequences | PERSONAL tab | Not yet investigated | Not yet investigated |

### Organization sub-tab gating

- The Organization sub-tab is hidden by `appUserPersonOrganization?.isActive`, not by plan tier and
  not by the active workspace (`BlueprintsTab.tsx:80`, `WorkflowsListView.tsx:150`,
  `DocumentLibraryTab.tsx:81`, `CommunicationsTab.tsx:66`, `VariablesTab.tsx:135`,
  `BlueprintPicker.tsx:51-52`).
- Information Request Templates gate on the active workspace instead
  (`useInformationRequestTemplateAdministration.ts:42`, `:119`).
- Top-level Settings tabs follow the session subscription (`useSettingsPlanAvailability.ts:30-48`).

### Information Request ownership invariant

- Every creation path copies the Exchange owner to the request (Blueprint, Template, ad hoc, and
  successor draft paths).
- The V96 trigger and `InformationRequestTemplateMaterializer.requireRequestOwnerMatchesTemplate`
  require the Template owner to equal the request owner.
- Phase 12 decision 11 relies on this invariant: organization sponsorship of the Personal Settings
  scope covers Template authoring only, and a request on a personally owned Exchange answers to
  that person's plan.

## Related Exchange Initiation Defects

These are not ownership questions, but they share the same code path and will be designed together
with, or immediately after, this work.

1. The "at least one document" rule is enforced for every Exchange in the web app
   (`ExchangeInitiation.tsx:568-573`) and on the server (`ExchangeInitiationService.kt:757-760`),
   regardless of start mode or Blueprint. A locked Organization or Platform Blueprint whose
   Information Request collects the documents, and which has no document slots, cannot be started,
   because Add Document is hidden when the form is locked (`ExchangeInitiationDocumentsTab.tsx:80`).
2. Starting an Exchange from a Blueprint that pins an Information Request Template Version never
   creates the request. `handleBlueprintSelect` ignores `informationRequestTemplateVersionId`,
   neither `ExchangeInitiationRequest` nor `ExchangeInitiationDto` carries a Blueprint id, and
   `ExchangeInitiationService` never calls the instantiation service. The Blueprint editor
   nevertheless tells authors "Exchanges from this Blueprint start an Information Request"
   (`BlueprintInformationRequestTab.tsx:38-39`). The implementation plan only defined manual
   creation, so this is a plan gap as well as a broken UI promise.
3. GA-002 must be resolved first: once any request is created from a Blueprint with document
   defaults, every later save of that Blueprint fails on the placeholder foreign key. Automatic
   creation would make that failure happen after the Blueprint's first use.

## Target Model (proposed, not approved)

"Personal" means private to the person within the workspace where the item was created.

- Each PERSONAL item records both its author and its owning workspace: an organization, or the
  person's personal workspace.
- "My Blueprints" (and every other "My ..." tab) lists only the author's private items for the
  active workspace. Switching workspace switches the set.
- A private item in an organization workspace is billed to, and entitlement-checked against, that
  organization, and may reference that organization's Schemas, Document Library entries, and
  Templates.
- A private item in the personal workspace is billed to the person's own plan, as today.
- Items created in an organization never appear in another organization or in the personal
  workspace, and the reverse.
- The "My ..." tab carries a short description such as "Only visible to you in <workspace name>".
- The Information Request ownership invariant (request owner equals Exchange owner equals Template
  owner) is preserved, because a private Template in an organization workspace is owned by that
  organization for billing and ownership purposes while remaining private to its author for
  visibility.

This separates two concepts the current code conflates: ownership and billing (the workspace) and
visibility (private to the author, or shared with the workspace).

## Design Alternatives

| Option | Summary | Strengths | Weaknesses |
|---|---|---|---|
| A. Workspace-scoped private items (proposed) | PERSONAL items carry an owning workspace; visibility is private to the author inside it | Matches the user model; resolves billing, cross-workspace references, and the Information Request owner invariant | Requires a data model change across six domains and a decision on existing rows |
| B. Account-owned items used in any workspace | Keep account ownership; bill use to the active workspace and copy on use across owners | Smaller schema change | Still shows foreign-workspace items; references to other organizations' resources remain broken; copy-on-use multiplies Templates |
| C. Remove PERSONAL for organization members | Members only see Organization items; PERSONAL exists only in the personal workspace | Simplest model | Loses the private organizer the product intends; members who are not administrators cannot author anything |

## Open Decisions

1. Confirm the target model: option A, B, or C.
2. Admin visibility: may organization administrators see, audit, or transfer members' private
   items in their organization, or never?
3. Offboarding: when a member leaves an organization, are their private items in that organization
   transferred to the organization, archived, or deleted?
4. Existing rows: the platform has no production users (Development-Stage Constraint), so existing
   PERSONAL rows may be reassigned or removed in one forward migration. Decide which rule applies,
   for example "assign to the personal workspace".
5. Personal workspace for organization members: should the uncommitted "Personal workspace" switch
   ship? With it, a member can create user-owned Exchanges that the organization cannot see or
   audit. If personal ownership should apply only to people without an organization, it should not.
6. Sub-tab gating: gate the Organization sub-tab on the active workspace everywhere, and keep plan
   tier logic only in `useSettingsPlanAvailability`.
7. Workflows: should private Workflows fire for Exchanges and Information Requests in their
   workspace? If yes, do they replace or run alongside Organization and Platform definitions? Where
   do administrator clones land?
8. Exchange start from a Blueprint with an Information Request:
   - Create the draft request automatically in the same transaction, or offer an opt-in?
   - On failure (missing feature, capacity, retired Version), refuse the whole start or start the
     Exchange with a warning?
   - Assign the initiator as Decision Maker on the server (changes Phase 10 decision 4) or in the
     UI after start?
   - Should the request gate Exchange closure?
9. Document rule: allow zero Exchange documents only when the Blueprint carries an Information
   Request, or for any Exchange? Recipient notifications currently list document titles.
10. Blueprint document slots with an Information Request: Exchange document slots, request
    placeholders, or both (overlaps the GA-002 decision)?

## Affected Areas (for sizing, not a task list)

- Persistence: Blueprints, Document Library, Workflows, Communications, Variables and Sequences,
  Information Request Templates; owner and visibility columns and their check constraints.
- Entitlement guards: `BlueprintSubscriptionGuard`, `DocumentLibrarySubscriptionGuard`,
  `WorkflowSubscriptionGuard`, `InformationRequestTemplateEntitlementGuard`, and the equivalents
  for Communications and Variables.
- Repositories and list queries that currently return PERSONAL items in every workspace.
- Authorization: organization role grants over private items, if administrators may see them.
- Audit: owner scope for private items in an organization workspace.
- Workflow engine selection of definitions.
- Exchange initiation: DTO, service, resource error mapping, document rule, Information Request
  start.
- Web app: every "My ..." tab, `BlueprintPicker`, `useRequestSourceOptions`,
  `useBlueprintTemplateChoices`, `ExchangeInitiation`, the account menu workspace switch, and
  `AuthContext`.
- Help articles: Blueprints overview, managing and using Blueprints, Document Library overview,
  Information Request Templates and managing requests, profile settings, platform Templates.

## Constraints

- Follow `AGENTS.md`, including industry-neutral naming, service boundaries, and REST conventions.
- No backwards-compatibility code (implementation plan `## Development-Stage Constraint`). Replace
  shapes in one forward migration; never edit an applied migration.
- No new AWS service or paid resource type.
- Preserve the Information Request owner invariant unless a separate decision reverses Phase 12
  decision 11.

## Next Steps

1. Settle open decisions 1 through 5, which determine the data model.
2. Complete the investigation of Communications and Variables and Sequences PERSONAL behavior.
3. Write the data model and migration design, the entitlement and visibility rules, and a
   verification strategy covering at least two materially different workspace patterns (a member
   of one organization, and a person with a personal workspace plus two organizations).
4. Only then convert this document into a phased implementation plan with TDD tasks.
