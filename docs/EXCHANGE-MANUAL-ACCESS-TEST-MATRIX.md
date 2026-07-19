# Exchange Manual Access Test Matrix

## Purpose

Use this plan to manually verify Exchange access between personal users, organization users,
external recipients, groups, and Trusted Organizations. It covers:

- A primary recipient who already has an account.
- A primary recipient who does not yet exist and must register.
- A primary recipient who uses the no-sign-in email and access-code flow.
- A user or group added only as an additional participant.
- Every assignable Exchange Share role.
- Direct, group-inherited, and external participant access.
- Acceptance, rejection, access changes, revocation, expiry, and terminal Exchange states.

The word "participant" has two meanings in the product:

1. An additional Exchange recipient whose purpose is `PARTICIPANT`. This person never accepts or
   rejects the Exchange.
2. The Exchange Share role named `PARTICIPANT`, which grants general read access.

Record both the recipient purpose and Share role in every test result.

## Current access rules to verify

### Primary recipient versus additional participant

| Property | Primary recipient | Additional participant |
|---|---|---|
| Required to create an Exchange | Yes | No |
| Can accept or reject | Yes, when acceptance is required | No |
| Can be a direct user | Yes | Yes |
| Can be a personal group | Yes | Yes |
| Can be an internal organization group | Yes | Yes |
| Can be a Trusted Organization group | Yes | Yes |
| Can be a new external email at initiation | Yes | No |
| Can be a new external email from Manage access | Not applicable | Yes |
| Can be the initiator | No | No |

When the primary recipient is a group, only an active group Owner or Manager can accept or reject.
Group Members and Observers receive inherited access but cannot make the primary decision.

### Exchange Share roles

| Role | View Exchange and documents | Download | Add or update documents | Delete documents | Comment | Sign | Manage access | Accept as primary |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| Owner | Yes | Yes | Yes | Yes | Yes | No explicit signing grant | Yes | Not applicable |
| Editor | Yes | Yes | Yes | Only with an explicit deletion constraint | Yes | No | No | Yes |
| Reviewer | Yes | Yes | No | No | Yes | No | No | Yes |
| Signer | Yes | Yes | No | No | No | Yes | No | Yes |
| Viewer | Yes | Only when `can_download` is enabled | No | No | No | No | No | Yes |
| Commenter | Yes | No | No | No | Yes | No | No | Yes |
| Participant | Yes | Only when `can_download` is enabled | No | No | No | No | No | Yes |

Notes:

- Owner is structural. It cannot be assigned, changed, or revoked.
- Editor, Reviewer, and Signer include download by role unless another enforced constraint removes it.
- Viewer and Participant are the only roles for which the current UI exposes constraint controls.
- Document addition, upload, update, and deletion flags can add document capabilities to the primary
  recipient independently of the selected role.
- Only the primary recipient can use the role's acceptance capability to decide the Exchange.

### Lifecycle access

| Exchange state | Owner | Primary recipient | Additional participant |
|---|---|---|---|
| Draft, acceptance pending | Can view and manage | Can view enough to accept or reject | Active participant Shares can view |
| Active | Full active access | Role and constraint based | Role and constraint based |
| Rejected | Can view terminal record and delete | No document changes | No document changes |
| Ended | Can view terminal record and delete | Read-only or denied for writes | Read-only or denied for writes |
| Rescinded | Can view terminal record and delete | No active document changes | No active document changes |
| Deleted | Must no longer appear or be retrievable | No access | No access |

## Complete permutation model

The full test space is the Cartesian product below. Do not execute impossible combinations. Use the
validity rules after the table to identify valid cases.

| Dimension | Values |
|---|---|
| Sender context | Personal user, Org Owner, Org Admin, Org Member, Org Guest, user without initiate capability |
| Exchange owner | Personal, sender's active organization |
| Primary selection | Registered user, new external email, existing email entered as external, personal group, internal org user, internal org group, Trusted Organization person, Trusted Organization group |
| Primary account state | Active, temporary placeholder, inactive, deprovisioned, nonexistent |
| Relationship | Same user, same org, no org, trusted org active, trusted org suspended, trusted org ended, external org with no trust relationship |
| Acceptance policy | Required without workflow, required with workflow, bypassed, Trusted Organization group forced acceptance |
| Recipient sign-in | Required, not required |
| Primary decision | None, accept, reject |
| Primary role | Auto, Editor, Reviewer, Signer, Viewer, Commenter, Participant |
| Additional participant count | 0, 1, 2 or more |
| Additional participant source | Direct registered user, new email from Manage access, personal group, internal group, Trusted Organization group |
| Participant role | Editor, Reviewer, Signer, Viewer, Commenter, Participant |
| Share source | Direct, inherited from group |
| Share status | Pending approval, active, revoked, expired |
| Constraints | Download on or off, reshare on or off, watermark on or off, MFA on or off, allowed IP or denied IP, expiry future or past |
| Exchange state | Draft, Active, Rejected, Ended, Rescinded, Deleted |
| Access route | Exchange list, email link, direct URL, no-auth secure link, stale browser tab |

Validity rules:

- The sender and primary recipient cannot be the same principal.
- The sender cannot be added as an additional participant.
- The primary recipient cannot also be an additional participant.
- A participant principal cannot be added twice during initiation.
- A new external email is supported as the primary recipient.
- A new external email is rejected as an additional participant during initiation.
- A new external email can be added later through Manage access and becomes an external participant.
- A primary Trusted Organization group always requires acceptance.
- Only an active Trusted Organization group Owner or Manager may make that decision.
- An org-owned sender may share internally without a trusted-organization relationship.
- B2C sharing to a user with no organization depends on `allowExternalCustomerSharing`.
- B2B sharing to another organization's user requires an active trusted-organization
  relationship when `requireTrustedOrganizationForB2b` is enabled.
- A Trusted Organization group requires active trust, both directional Exchange policies, group
  discovery permission, and an active published group.
- Terminal Exchanges do not allow document or access changes.

## Test data and personas

Create dedicated accounts and do not reuse real customer data.

| Code | Persona |
|---|---|
| P01 | Personal active sender with Exchange initiation capability |
| P02 | Organization A Owner |
| P03 | Organization A Admin |
| P04 | Organization A Member |
| P05 | Organization A Guest |
| P06 | Organization A active ordinary recipient |
| P07 | Organization A second ordinary recipient |
| P08 | Active registered user with no organization |
| P09 | Active user in Organization B, in active trust with Organization A |
| P10 | Active user in Organization C, with no trust relationship with Organization A |
| P11 | New email address with no DocuHyphen account |
| P12 | Inactive registered account |
| P13 | Deprovisioned registered account |
| P14 | Organization B group Owner |
| P15 | Organization B group Manager |
| P16 | Organization B group Member |
| P17 | Organization B group Observer |

Create these groups:

| Code | Group |
|---|---|
| G01 | P01 personal group with P08 and P09 |
| G02 | Organization A internal group with P06 as Owner, P07 as Member |
| G03 | Organization B published trusted group with P14 Owner, P15 Manager, P16 Member, P17 Observer |
| G04 | Organization B unpublished group |
| G05 | Organization A inactive group |
| G06 | Organization B published group with no Owner or Manager |

Prepare at least one PDF and one editable test document. Give every Exchange a unique name using the
test ID, for example `EX-PRI-01 registered personal recipient`.

## Execution order

Run the suites in this order:

1. Sender eligibility and recipient selection.
2. Primary acceptance and registration.
3. Role capability checks.
4. Additional participant checks.
5. Group inheritance and Trusted Organization checks.
6. Constraint checks.
7. Lifecycle, revocation, and isolation checks.

This order creates reusable Exchanges while keeping failed setup from contaminating later results.

## Recommended first manual run

Run these cases first for broad coverage with a manageable number of Exchanges:

1. `EX-PRI-01`: registered personal primary recipient.
2. `EX-PRI-02`: same-organization primary recipient.
3. `EX-PRI-03`: trusted-organization primary recipient.
4. `EX-PRI-06`: primary recipient whose email does not yet exist.
5. `EX-REG-02`: invited recipient registers, signs in, and retains the Exchange.
6. `EX-REG-10`: no-account recipient accepts using the secure link and code.
7. `EX-PAR-01`: registered user added only as a Participant.
8. `EX-PAR-07`: unknown email added later through Manage access.
9. `EX-ROL-03`: Editor capability boundary.
10. `EX-ROL-06`: Viewer capability boundary.
11. `EX-ROL-08`: Participant capability boundary.
12. `EX-ACC-07`: additional participant attempts to accept.
13. `EX-GRP-02`: internal group Owner accepts for the group.
14. `EX-ACC-14`: trusted group Member attempts to accept.
15. `EX-CON-02`: download is disabled.
16. `EX-MUL-03`: restricted direct Share plus less-restricted inherited Share.
17. `EX-PAR-14`: active access is revoked.
18. `EX-LIF-11`: access is revoked while the Exchange is open.
19. `EX-LIF-07`: participant attempts a write after the Exchange ends.
20. `EX-LIF-15`: unrelated user attempts a guessed direct URL.

If all 20 pass, continue with the remaining cases by suite. A failure in a shared setup path, such as
registration, group inheritance, or acceptance, should be resolved before running dependent cases.

## Suite A: Sender and ownership permutations

| ID | Sender | Active context | Action | Expected |
|---|---|---|---|---|
| EX-SND-01 | P01 | Personal | Create Exchange to P08 | Exchange is personal-owned and creation succeeds |
| EX-SND-02 | P02 | Organization A | Create Exchange to P06 | Exchange is Organization A-owned and creation succeeds |
| EX-SND-03 | P03 | Organization A | Create Exchange to P06 | Creation succeeds |
| EX-SND-04 | P04 | Organization A | Create Exchange to P06 | Creation succeeds when the member has `EXCHANGE_INITIATE` |
| EX-SND-05 | P05 | Organization A | Attempt creation | Creation is hidden or denied if the effective session lacks `EXCHANGE_INITIATE` |
| EX-SND-06 | P02 | Personal context | Create Exchange to P08 | Ownership is personal, not Organization A |
| EX-SND-07 | P02 | Organization A | Switch active org after creation and open direct URL | Access remains through the Owner Share |
| EX-SND-08 | P01 | Personal | Select P01 as primary | Validation blocks self-recipient |
| EX-SND-09 | P02 | Organization A | Add P02 as participant | Validation blocks adding the initiator |

## Suite B: Primary recipient identity permutations

| ID | Sender | Primary recipient | Setup | Expected |
|---|---|---|---|---|
| EX-PRI-01 | P01 | P08 registered personal user | Acceptance required | P08 receives one pending Exchange and can decide it |
| EX-PRI-02 | P02 | P06 same-org user | Acceptance required | Internal share succeeds |
| EX-PRI-03 | P02 | P09 trusted external-org user | Trust active | B2B share succeeds |
| EX-PRI-04 | P02 | P10 external-org user with no trust | `requireTrustedOrganizationForB2b` on | Creation is denied with no Exchange or Share left behind |
| EX-PRI-05 | P02 | P10 external-org user with no trust | `requireTrustedOrganizationForB2b` off | Creation succeeds |
| EX-PRI-06 | P02 | P11 nonexistent email | External customer sharing on | Temporary user is created and invitation path succeeds |
| EX-PRI-07 | P02 | P11 nonexistent email | External customer sharing off | Creation is denied with no partial Exchange |
| EX-PRI-08 | P02 | P08 existing user entered by email | External email entry | Email resolves safely and only one effective recipient is created |
| EX-PRI-09 | P02 | P12 inactive account | Direct selection or exact email | Access is denied or the account is excluded from selection |
| EX-PRI-10 | P02 | P13 deprovisioned account | Direct selection or exact email | Access is denied or the account is excluded from selection |
| EX-PRI-11 | P01 | G01 personal group | P01 owns group | Group Share and inherited member Shares are created |
| EX-PRI-12 | P02 | G02 internal group | Organization A active | Group Share and inherited member Shares are created |
| EX-PRI-13 | P02 | G03 trusted published group | Trust and policies active | Creation succeeds and acceptance is required |
| EX-PRI-14 | P02 | G04 unpublished external group | Trust active | Group is hidden or creation is denied |
| EX-PRI-15 | P02 | G05 inactive internal group | Direct URL or stale selection | Creation is denied |
| EX-PRI-16 | P02 | G03 trusted group | Trust suspended | Creation is denied |
| EX-PRI-17 | P02 | G03 trusted group | Trust ended | Creation is denied |

## Suite C: New-user registration and no-sign-in permutations

Use a fresh email address for each test so temporary-user merging can be verified precisely.

| ID | Sign-in required | Acceptance required | Recipient action | Expected |
|---|---:|---:|---|---|
| EX-REG-01 | Yes | Yes | Open invitation before registering | Recipient is directed to register or sign in and cannot access as an anonymous user |
| EX-REG-02 | Yes | Yes | Register with the invited email | Temporary user is upgraded in place and the pending Exchange remains attached |
| EX-REG-03 | Yes | Yes | Register with a different email | The invited Exchange is not visible |
| EX-REG-04 | Yes | Yes | Sign in after registration and accept | Exchange becomes Active and both parties see the new state |
| EX-REG-05 | Yes | Yes | Sign in after registration and reject with reason | Exchange becomes Rejected and the sender sees the reason where supported |
| EX-REG-06 | Yes | No | Register with invited email | Exchange is already Active and becomes visible after registration |
| EX-REG-07 | No | Yes | Open secure email link and enter correct code | No-auth Exchange details become available and recipient can decide |
| EX-REG-08 | No | Yes | Enter an incorrect code | Access is denied without leaking Exchange documents |
| EX-REG-09 | No | Yes | Use expired code or expired secure link | Access is denied and resend or recovery path is offered |
| EX-REG-10 | No | Yes | Accept through no-auth flow | Exchange becomes Active and an audit decision is recorded |
| EX-REG-11 | No | Yes | Reject through no-auth flow | Exchange becomes Rejected and reason handling is correct |
| EX-REG-12 | No | No | Complete no-auth verification | Active Exchange documents are accessible within the validity window |
| EX-REG-13 | No | Any | Reuse verified browser session inside validity window | Access works without exceeding the configured window |
| EX-REG-14 | No | Any | Open link after no-auth validity expires | Access is denied until a new code and secure link are issued |
| EX-REG-15 | No | Yes | Accept, then register with the same email | The temporary user is upgraded in place and the accepted Exchange appears in the account |
| EX-REG-16 | No | Yes | Accept, then register with different letter casing in same email | Normalized email merge preserves Exchange access |
| EX-REG-17 | No | Yes | Forward secure link and code to another browser | Access follows the possession-based credentials; record this as expected security behavior |
| EX-REG-18 | No | Yes | Omit the secure-link credential on a request | Request is denied even if the Exchange ID and code are known |

For EX-REG-02, EX-REG-15, and EX-REG-16, verify that the user ID referenced by the Exchange Share
does not change during registration and that duplicate active accounts are not created.

## Suite D: Primary recipient role permutations

Create one Active Exchange per row. For each role, try every listed action through the UI and by
refreshing after any denied request.

| ID | Primary role | Expected allowed actions | Expected denied actions |
|---|---|---|---|
| EX-ROL-01 | Auto with no write flags | View; behavior matches Viewer | Add, update, upload, delete, comment, sign, manage access |
| EX-ROL-02 | Auto with any write flag | View, download, add or update according to enabled flags, comment | Manage access; any document operation whose flag is off |
| EX-ROL-03 | Editor | View, download, add, update, upload, comment | Manage access, sign, delete unless deletion is explicitly enabled |
| EX-ROL-04 | Reviewer | View, download, comment | Add, update, upload, delete, sign, manage access |
| EX-ROL-05 | Signer | View, download, sign | Add, update, upload, delete, comment, manage access |
| EX-ROL-06 | Viewer | View; download only when enabled | Add, update, upload, delete, comment, sign, manage access |
| EX-ROL-07 | Commenter | View, comment | Download, add, update, upload, delete, sign, manage access |
| EX-ROL-08 | Participant | View; download only when enabled | Add, update, upload, delete, comment, sign, manage access |
| EX-ROL-09 | Owner assignment attempt | None | Owner is absent from assignable role controls and API request is rejected |

Repeat EX-ROL-03 through EX-ROL-08 once for a registered primary user and once for an additional
participant. The role capabilities should match, except only the primary user can accept or reject.

## Suite E: Acceptance policy permutations

| ID | Policy and recipient | Action | Expected |
|---|---|---|---|
| EX-ACC-01 | Acceptance required, registered user, no workflow | Primary accepts | Draft becomes Active |
| EX-ACC-02 | Acceptance required, registered user, no workflow | Primary rejects | Draft becomes Rejected |
| EX-ACC-03 | Acceptance required, workflow active | Primary accepts | Decision follows the workflow and activates only after required approval |
| EX-ACC-04 | Acceptance required, workflow active | Primary rejects | Workflow and Exchange rejection state are consistent |
| EX-ACC-05 | Acceptance bypassed, registered user | Create Exchange | Exchange becomes Active immediately |
| EX-ACC-06 | Acceptance bypassed, additional participant | Participant looks for decision controls | No accept or reject controls appear |
| EX-ACC-07 | Acceptance required | Additional participant calls decision endpoint | Request is denied |
| EX-ACC-08 | Acceptance required | Unrelated user calls decision endpoint | Request is denied without confirming Exchange existence |
| EX-ACC-09 | Acceptance already accepted | Repeat accept after refresh or retry | State remains Active and no duplicate side effects occur |
| EX-ACC-10 | Acceptance already rejected | Attempt accept | Invalid transition is denied |
| EX-ACC-11 | Trusted group with global acceptance bypassed | Create Exchange | Exchange remains pending because trusted groups force acceptance |
| EX-ACC-12 | Trusted group primary | G03 Owner accepts | Exchange becomes Active |
| EX-ACC-13 | Trusted group primary | G03 Manager accepts | Exchange becomes Active |
| EX-ACC-14 | Trusted group primary | G03 Member attempts decision | Request is denied |
| EX-ACC-15 | Trusted group primary | G03 Observer attempts decision | Request is denied |
| EX-ACC-16 | Trusted group primary | Trust or policy becomes invalid before decision | Decision is denied and Exchange remains pending |

## Suite F: Additional participant permutations

| ID | How added | Participant | Role | Expected |
|---|---|---|---|---|
| EX-PAR-01 | During initiation | P07 registered user | Participant | Immediate read access; no decision rights |
| EX-PAR-02 | During initiation | P07 registered user | Editor | Editor document actions; no decision or access-management rights |
| EX-PAR-03 | During initiation | Same user as primary | Any | Creation is blocked |
| EX-PAR-04 | During initiation | Same participant twice | Any | Creation is blocked |
| EX-PAR-05 | During initiation | New external email | Any | Creation is blocked because external email participants are unsupported here |
| EX-PAR-06 | Manage access | Existing registered user by email | Viewer | Direct user Share is created and invitation is sent |
| EX-PAR-07 | Manage access | New external email | Participant | External participant Share is created and invitation is sent |
| EX-PAR-08 | Manage access | Existing user by UUID | Reviewer | Direct user Share is created |
| EX-PAR-09 | Manage access | Personal group | Viewer | Group and inherited member access are created |
| EX-PAR-10 | Manage access | Internal group | Commenter | Group members inherit Commenter access |
| EX-PAR-11 | Manage access | Trusted external group | Viewer | Only eligible published trusted group can be added |
| EX-PAR-12 | Manage access | Exchange owner | Any | Self-add is blocked |
| EX-PAR-13 | Manage access | Existing participant | Change role | Permissions change immediately after refresh |
| EX-PAR-14 | Manage access | Existing participant | Revoke | Access disappears immediately and direct URL fails |
| EX-PAR-15 | Manage access | Current caller's own entry | Change or revoke | Operation is blocked |
| EX-PAR-16 | Non-owner participant | Any person | Attempt Manage access | Panel is hidden or request is denied |
| EX-PAR-17 | Unknown user | Direct Exchange URL | None | Exchange is not visible and documents cannot be fetched |

Important current-model distinction:

- A new external primary email creates or reuses a temporary `AppUser`.
- A new email added through Manage access creates or reuses an `ExternalParticipant`.

Test both paths because registration reconciliation and sign-in behavior are not identical data paths.

## Suite G: Group access and membership changes

| ID | Setup and action | Expected |
|---|---|
| EX-GRP-01 | Add G02 as primary and inspect P06 and P07 | Active members receive inherited Shares |
| EX-GRP-02 | G02 Owner accepts | Exchange becomes Active |
| EX-GRP-03 | G02 Member attempts acceptance | Request is denied |
| EX-GRP-04 | Add a new member to G02 after Share creation | New member receives inherited access after reconciliation |
| EX-GRP-05 | Remove P07 from G02 | P07 inherited access is revoked |
| EX-GRP-06 | P07 also has a direct Share, then is removed from G02 | Direct access remains |
| EX-GRP-07 | Revoke the parent G02 Share | All access inherited only from that parent is revoked |
| EX-GRP-08 | Deactivate G02 | New access is denied and existing inherited behavior matches reconciliation policy |
| EX-GRP-09 | Add G03 trusted group as participant | Current eligible members inherit access |
| EX-GRP-10 | Suspend trust after accepted Exchange | Existing materialized access remains |
| EX-GRP-11 | Add member to G03 while trust is suspended | New member does not inherit access |
| EX-GRP-12 | Remove member from G03 while trust is suspended | Removed member loses inherited access |
| EX-GRP-13 | Resume trust | Currently eligible members are reconciled |
| EX-GRP-14 | End trust after accepted Exchange | Existing materialized access remains, but future materialization is blocked |
| EX-GRP-15 | Unpublish G03 before pending acceptance | Acceptance is denied |
| EX-GRP-16 | Demote accepting Owner or Manager to Member before decision | Acceptance is denied |
| EX-GRP-17 | G06 has no Owner or Manager | Exchange cannot be accepted by Member or Observer |

## Suite H: Constraint permutations

Run constraints on both Viewer and Participant roles. Also issue direct API requests so hidden UI
controls are not the only enforcement being tested.

| ID | Constraint setup | Expected |
|---|---|---|
| EX-CON-01 | `can_download=true` | Zip or permitted download action succeeds |
| EX-CON-02 | `can_download=false` | Download is hidden or denied; preview remains available |
| EX-CON-03 | `can_reshare=false` | Reshare capability is removed; current non-owner roles still cannot manage access |
| EX-CON-04 | `watermark=true` | Preview or downstream renderer receives and applies watermark obligation |
| EX-CON-05 | `require_mfa=true`, MFA satisfied | Access succeeds |
| EX-CON-06 | `require_mfa=true`, MFA not satisfied | Access is denied until MFA is satisfied |
| EX-CON-07 | Allowed IP list contains client IP | Access succeeds |
| EX-CON-08 | Allowed IP list excludes client IP | Access is denied |
| EX-CON-09 | Allowed IP list set but client IP unavailable | Access fails closed |
| EX-CON-10 | Share expiry is in the future | Access works before expiry |
| EX-CON-11 | Share expiry is in the past | Access is denied |
| EX-CON-12 | Malformed constraints stored through a controlled test fixture | Access fails closed |
| EX-CON-13 | Unsupported `max_views` sent on write | Request is rejected |
| EX-CON-14 | Allowed download formats include requested type | Download succeeds |
| EX-CON-15 | Allowed download formats exclude requested type | Download is denied |
| EX-CON-16 | Viewer with document upload flag enabled | Upload capability is added while unrelated write actions remain denied |
| EX-CON-17 | Participant with document deletion flag enabled | Delete capability is added while Manage access remains denied |

## Suite I: Multiple-Share and precedence permutations

These tests catch privilege changes caused by capability unions and group inheritance.

| ID | Shares held by same user | Expected |
|---|---|---|
| EX-MUL-01 | Direct Viewer plus inherited Editor | Effective capabilities include Editor capabilities |
| EX-MUL-02 | Direct Participant plus inherited Commenter | User can comment |
| EX-MUL-03 | Direct Viewer with no download plus inherited Reviewer | Verify whether the Reviewer grant restores download; record as capability-union behavior |
| EX-MUL-04 | Two Shares, either requires watermark | Watermark obligation applies |
| EX-MUL-05 | Two format-restricted Shares | Allowed formats are the intersection |
| EX-MUL-06 | One Share expired and one active | Only the active Share contributes access |
| EX-MUL-07 | One Share revoked and one active | Active Share preserves its own capabilities |
| EX-MUL-08 | Group Share removed but direct Share remains | Direct access continues |
| EX-MUL-09 | Direct Share revoked but group membership remains | Inherited access continues |
| EX-MUL-10 | Same user is in two shared groups | No duplicate UI identity; effective capabilities are the union |

EX-MUL-03 is especially important. A restrictive Share is not necessarily a global deny when another
valid Share independently grants the capability.

## Suite J: Lifecycle, revocation, and stale-session permutations

| ID | Starting state | Action | Expected |
|---|---|---|---|
| EX-LIF-01 | Draft | Owner edits documents | Allowed |
| EX-LIF-02 | Draft | Primary views and accepts | Allowed when primary Share is pending approval |
| EX-LIF-03 | Active | Owner ends Exchange | Exchange becomes Ended |
| EX-LIF-04 | Active | Owner rescinds Exchange | Exchange becomes Rescinded |
| EX-LIF-05 | Draft | Owner rescinds Exchange | Exchange becomes Rescinded |
| EX-LIF-06 | Rejected | Participant attempts document update | Denied |
| EX-LIF-07 | Ended | Participant attempts document update | Denied |
| EX-LIF-08 | Rescinded | Owner attempts access change | Denied |
| EX-LIF-09 | Ended | Owner deletes Exchange | Deleted and absent from normal retrieval |
| EX-LIF-10 | Rejected | Owner deletes Exchange | Deleted and absent from normal retrieval |
| EX-LIF-11 | Active | Owner revokes participant while participant page is open | Next action and refresh are denied |
| EX-LIF-12 | Active | Participant Share expires while page is open | Next protected request is denied |
| EX-LIF-13 | Active | User signs out but keeps stale tab | Protected requests require authentication |
| EX-LIF-14 | Active | User changes active organization | Exchange Share access remains scoped to the user and resource |
| EX-LIF-15 | Any | Guess another Exchange UUID | Response does not reveal inaccessible Exchange details |

## Suite K: Visibility, notifications, contacts, and audit

| ID | Action | Expected |
|---|---|---|
| EX-OBS-01 | Create Exchange for registered primary user | Recipient gets the configured email and in-app notification |
| EX-OBS-02 | Create no-auth Exchange for new email | Email contains secure link and access code with the correct validity label |
| EX-OBS-03 | Add participant through Manage access | Added person receives an invitation |
| EX-OBS-04 | Primary accepts | Both users become mutual contacts after acceptance |
| EX-OBS-05 | Temporary primary accepts before registration | Sender-side contact is created; recipient-side contact is seeded after registration |
| EX-OBS-06 | Primary never accepts | Initiation alone does not create mutual contacts |
| EX-OBS-07 | Participant is merely added | Participant does not gain primary-decision contact side effects |
| EX-OBS-08 | Accept, reject, add, role change, revoke, end, rescind | Corresponding audit entries identify actor, target, outcome, and owner scope |
| EX-OBS-09 | Unauthorized Manage access attempt | Denial is recorded without exposing the access list |
| EX-OBS-10 | External-customer share from an organization | Allowed B2C bypass is audit recorded |
| EX-OBS-11 | Notification delivery fails in a controlled environment | Successful domain transaction is not incorrectly rolled back unless designed fail-closed |
| EX-OBS-12 | Open Summary tab | Requester, primary recipient, and additional participants are categorized correctly |

## Suite L: UI and direct API consistency

For each denied action in Suites D through J:

1. Confirm the UI hides or disables the action when appropriate.
2. Send the corresponding request directly using browser developer tools or an API client.
3. Confirm the backend denies the request.
4. Refresh the Exchange and confirm no partial mutation occurred.
5. Confirm the error does not disclose inaccessible user, group, or Exchange details.

At minimum, directly test:

- View Exchange.
- Accept or reject.
- Add, update, upload, download, and delete a document.
- List access.
- Grant access.
- Change role.
- Revoke access.
- End, rescind, and delete.

## Known high-risk checks

Prioritize these before broad regression:

1. New external primary accepts by OTP, then registers using the same email.
2. Additional participant added by unknown email through Manage access.
3. Additional participant attempts to accept a primary recipient's Exchange.
4. Trusted group Member attempts acceptance while Owner and Manager are the only valid deciders.
5. Trust is suspended between Exchange creation and group acceptance.
6. Group member loses group membership but retains a separate direct Share.
7. Viewer has `can_download=false` but also inherits Reviewer from a group.
8. Access is revoked while the user has the Exchange open in another browser.
9. Terminal Exchange is mutated through a direct API request.
10. Existing registered email is entered through the external-email path and does not create a
    duplicate identity.
11. Trusted Organization exact-email person selection is exercised end to end. A resolved verified
    member is a submittable primary recipient: the Exchange is created with a forced sign-in and
    forced acceptance, and acceptance revalidates the attested account, membership, relationship,
    and both directional policies before the Share activates.

## Test result template

Copy this block for every failed or ambiguous case:

```text
Test ID:
Date and tester:
Environment and build:
Sender account and active organization:
Exchange ID:
Exchange owner:
Primary recipient purpose, selection type, and role:
Additional participants and roles:
Acceptance policy and workflow:
Recipient sign-in setting:
Share source, status, constraints, and expiry:
Preconditions:
Steps performed:
Expected:
Actual:
HTTP request and response:
Screenshot or recording:
Audit event:
Notification received:
Pass, fail, or blocked:
Defect link:
```

## Completion criteria

The access test pass is complete when:

- Every row in Suites A through L has a recorded result or documented reason it is not applicable.
- Every role has been tested as both a primary recipient and an additional participant.
- Every primary recipient selection type has been tested with acceptance required and bypassed,
  except Trusted Organization groups, which always require acceptance.
- Both registration-required and no-sign-in paths have been tested with a new email.
- Direct and inherited access have both been granted, changed, revoked, and expired.
- At least one negative direct API test exists for every protected action.
- No denied action produces a partial mutation or leaks protected resource details.
- Audit and notification side effects have been checked for all lifecycle decisions.
