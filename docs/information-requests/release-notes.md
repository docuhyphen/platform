# Information Requests release notes

## Completeness re-check P3 repairs (2026-10-31)

- **Template Versions can be viewed.** The Versions tab of a Template offers **View version N** on every
  listed Version, including retired ones; the editor shows the chosen Version read-only and says which
  one is shown. A Template whose Versions are all retired opens its latest retired Version instead of an
  empty editor. `GET /information-request-templates/{id}/versions/{versionNumber}` backs the read.
- **New Information Request keeps its Templates.** When Blueprints cannot be listed, the dialog still
  lists Templates and the one-off choice and says Blueprints could not be listed, with Try again.
- **Respondents before the Exchange is accepted.** The response workspace carries `exchangeAccepted`;
  while the Exchange is still a draft the respond page says answers are accepted once it is accepted and
  keeps the answers read-only, and `INFORMATION_REQUEST_PARENT_STATE_INVALID` is explained on saves,
  uploads and submissions instead of a generic refusal.
- **Create link only where it can succeed.** `authorActions.issueAccessLinks` is true only when the caller
  manages parties and the Exchange does not require recipients to sign in; the request's page offers
  Create link and Resend link only then.
- **Condition clears touch the acting party's files.** A condition that clears or archives a hidden
  answer withdraws or archives only the files the acting party may change; another party's files on the
  same Requirement stay as they are.
- **Size limits in readable units.** The upload size guard and the record export limit state their limit
  in KiB, MiB or GiB instead of raw bytes.
- **Supplement reasons from the list.** The Manage page's **Request a supplement** takes its reason from
  the same list as the review page and sends the reason code.
- **Submissions on the request's page.** People who manage the request's parties see **Submissions** while
  the request is issued or in progress and changes are allowed, and can **Withdraw** a party's submission
  for recovery there.
- **Accepted facts.** When two current facts about the same subject differ, both are marked as differing,
  and the mark clears once one of them is revoked (the conflict is derived when facts are read). A SUBJECT
  party carries `processingRestricted`, and Promote is withheld, with the reason, for a corrected answer
  and for a restricted subject. Facts kept to the requesting side are no longer offered for reuse, since
  recertification never accepted them.
- **Carried-forward answers are formatted.** **From your previous submission** shows a choice by its
  label, a flag as Yes or No, and an answer other than a value with its kind and reason.
- **Health alarm and failed notices.** A notice intent that gave up rendering after five failed tries no
  longer counts towards `NOTICE_INTENTS_OVERDUE`; it stays listed as a failed notice in the operations
  queue.
- **Communication notices as formatted email.** A notice whose body comes from a Communication is sent
  as HTML rendered from its Markdown, in the same wrapper as Workflow notifications; platform default
  notices stay plain text. The stored body remains the text as written.
- **Exceptions on confirmations.** Where a Template permits an exception on a confirmation, the respond
  page offers **Record an exception**: choose the exception and give a reason, and the confirmation counts
  as answered by it. The card states the recorded exception and offers **Change exception**. The exception
  is saved as the confirmation's response through `PATCH .../responses` under the response ETag.
- **Help accuracy.** The due dates article says an unfilled value is sent as its literal token and only a
  failed render is retried; the operations article says reminders go to the responding parties whose role
  still has outstanding items.
- **Configuration declared.** `app.records.storage-location`, `app.records.permitted-transfer-regions`,
  `app.records.member-removal-party-decision` and `app.information-request.export.max-bytes` are in
  `application.properties` and the operator guide; the migration notes name V191 as the program's head.

## Completeness re-check repairs (2026-10-29)

- **Schemas for Information Requests.** The schema editor in Content - Fields and in Platform
  Administration asks what a new schema is **Used for**: Exchanges or Information Requests. The create
  request sends `targetResourceType` (`EXCHANGE` or `INFORMATION_REQUEST`), and the Schemas list marks
  schemas made for Information Requests, so typed-answer Templates can be authored without the API.
- **Conditions over documents.** A condition that reads a document whose respondent chose no answer
  counts the document as `PROVIDED` once its own current files complete it; an answer the respondent
  chose still wins. A condition can no longer read a confirmation: saving or publishing such a
  Template is refused, and the editor no longer offers confirmations as condition sources.
- **Reviewers can ask for more information.** The review page offers **Request more information**
  when the review allows a supplemental request. The review DTO carries `canRequestSupplement` and
  the request's `requestETag` for that command's `If-Match`.
- **Lapsed owners can withdraw a draft.** An unissued draft can be cancelled, and its **Must finish
  before the Exchange can end** switch turned off, even when its owner's plan lapsed or no longer
  includes Information Requests, so a leftover draft no longer stops the Exchange from ending.
  Turning the switch on, superseding and issuing still need the plan; operational suspension still
  refuses all of them.
- **Holds from the operations queue.** People who manage preservation holds see **Place hold** on
  each row of the operations queue, so an organization's hold managers can hold a request they do
  not administer.
- **Disposal waits during operational suspension.** Scheduled disposal skips an owner who is
  operationally suspended until the suspension is lifted; a disposal already under way finishes.

## Follow-ups after the gap analysis (2026-10-29)

- **Exchange updates.** `PUT /exchanges/{id}` no longer takes a rejection reason: a body that names
  `rejectionReason` is refused with `400`, also on a finished Exchange, and the reason is given with the
  recipient's decision. A caller who may only view an Exchange is refused with `403` when changing its
  settings or ending it, instead of `500`.
- **Exchange status changes keep their audit event.** Accepting, rejecting, ending and rescinding an
  Exchange are rolled back with `500` when their `exchange.lifecycle.*` audit event cannot be captured,
  instead of committing without it.
- **An ending Workflow cancels only what was checked.** When an ending Workflow confirms an ending that
  cancels the remaining requests, a request created after the ending was asked for refuses the ending with
  `INFORMATION_REQUEST_REMAINING_REQUESTS_NOT_CANCELLABLE`: the Exchange stays active and the ending
  refused notice goes out, as for any other refused ending.
- **Assigned to you lists open work.** A closed, cancelled, superseded or expired request leaves Assigned
  to you, and finished requests alone no longer show the menu entry.
- **Participants are checked as the person behind the address.** Assigning a participant applies the
  sharing policy to the account its address belongs to, as an email assignment does, instead of always
  treating it as an outside individual. A follow-up no longer copies a Reviewer party held by a participant (it is
  listed in `partiesNotCopied`), and a Blueprint whose default Reviewer is a participant is refused
  with `409 INFORMATION_REQUEST_REVIEWER_SIGN_IN_REQUIRED`.
- **Health report.** Each indicator of `GET /platform/information-request-health` has `key`, `count` and
  `breached`; the fixed `threshold` of zero is removed, and an indicator is breached whenever its count is
  above zero.
- **No subject Share role.** `SUBJECT` is no longer a request Share role: the application grants it
  nothing and V191 refuses it in the database, since a subject party names a Subject Identity and never
  holds a Share.
- **Tests and structure.** New tests cover two simultaneous subject assignments under one
  Idempotency-Key, request read authorization and related request visibility, the reconsideration and
  appeal prior-reviewer exclusion, and review queue membership through a group. The Exchange, acceptance
  and document version resources declare their routes on operations interfaces, and the Fields request
  bodies, value classes and DTO mapping moved out of the Fields services; neither changes behavior.

## Line-by-line gap analysis repairs (2026-10-26)

- **Manage page outcomes and follow-ups.** Promote is offered only for an answer the server can
  accept: the request names one Subject, named before the submission, and a review that must accept
  the package has accepted the item; otherwise the answer says why, and an answer already kept as a
  fact names its purposes and cannot be promoted again for one of them. Corrections ask which subject
  asked when several are named and list the corrections already recorded. A refused dialog stays open
  with what was entered; Refresh, issuing, cancelling and replacing reread every panel, and a stopped
  clock says it has no active deadline. Every request of a series shows the series and offers the next
  request while the series can continue, also after its first request was cancelled or replaced. Each
  refresh rule states when it falls due, and Refresh now is offered only while it is due. The Decision
  Maker warning shows only on drafts, and a draft says who alone can issue it. A supplement request
  clears its reason once created, and the recipient preview offers Try again.
- **Management reads, withdrawals and help.** The Manage page reads its workspace for management, so
  opening it never records a first view for an author who also responds. Withdrawing a submission
  closes its dialog once recorded and keeps it open, with the reason, when the withdrawal is stale or
  refused. Help no longer offers fact replacement on promotion, no longer directs authors to call an
  API for amendments, and describes reassignment as the page offers it.
- **Respondent workspace.** A refused save names and links each refused answer, and Save responses
  moves focus to the first. An answer that needs a reason is held until the reason is typed while other
  answers keep saving, autosave waits for the read that follows a save, and an entry change or Use this
  answer that finds the answers changed elsewhere is not made while autosave continues. Nested entries
  show inside their parent and are numbered by their place; Add is offered only where the server lets
  the respondent add an entry, Add and Remove follow the Template's maximum and minimum, and removing
  an entry asks first. Answers and files are read-only on finished
  requests and while changes are paused or stopped. A stale or interrupted upload is kept with its
  details for Retry, and every unsent file is kept. Preview opens its tab at once and says when the
  browser blocks it. Supporting-evidence links show beside the linked answer and document, reused
  choices show their labels and reused times the viewer's time zone, a typed note survives Use this
  answer, an undecided condition says the item is asked once its answers are given, section progress
  leaves confirmations out, an access link opened without its token says the link is incomplete, and
  loading and nothing-to-answer states are shown. Server size refusals and help state KiB and MiB.
- **Operations and record preservation screens.** Only issued and in progress requests can be selected
  for reminders, at most 100 at a time, and a filter change clears the selection. Queue rows and the CSV
  state when each request was issued, first viewed and started. Every failed read offers Try again and
  is announced, command refusals are announced, and blank or HTML error bodies show the screen's own
  message. Counts and sizes use the viewer's number format and singular forms, page titles are level
  one headings, the hold dialogs are named, and people without operations access see one notice.
  Record exports state their transfer decision and region and that the hash covers the stored record,
  a download says how many related requests it left out, and Create export takes a transfer region.
  Holds have a History; clocks show their policy, urgency, received and stop times and each event's due
  time; due date policies list every version. The clock policy form says what it still needs and marks
  a taken key on the Key field; audit search results can search by their actor or request; the
  retention form names the field at fault. Commands on these pages keep their Idempotency-Key within
  the page, so a retry after reopening a dialog or tab is not applied twice.
- **Exchange tab and ending.** The Exchange tab shows only the selected Exchange's requests, reads
  them again when the Exchange starts, ends or stops, and offers Try again when they cannot be read.
  A caller who also manages a request sees Manage beside Respond or Review, and a reviewer who only
  reviews opens their latest review with View. An ending that a Workflow must approve is stated as
  information with the new `EXCHANGE_ENDING_HELD_BY_WORKFLOW` reason code instead of an error. The End
  dialog formats large counts. A Blueprint whose pinned Template Version was retired is marked as
  unable to start its request. The Templates tab says when the plan does not include typed answers,
  and Template size limits are labelled in MiB.
- **Screen recovery and shared controls.** Busy editor dialogs ignore Escape and outside dismissal;
  a known Subject can be changed back to a new one. File displays consistently use binary units
  and singular counts. Blank or HTML transport failures use readable fallback text. Manage offers
  a read retry and keeps a newly issued link across a temporary reread failure while clearing it
  when access is lost. Edited fact notes and correction subject/policy inputs receive distinct
  command keys; a fingerprint conflict forgets its key and rereads before another attempt.
- **Commands, Fields and Exchange boundaries.** Every command Idempotency-Key longer than 128 characters
  is refused with `COMMAND_IDEMPOTENCY_KEY_INVALID`, also on the response, group entry, access link,
  account link, delegated authority, evidence and record preservation routes. Any signed-in user can read
  a platform Schema and its published or retired versions, and an unknown Field or Schema `scopeKind` is
  refused with the accepted values. Rescinding an Exchange and its document version routes answer 404 for
  an Exchange the caller cannot see and 403 when they can see it without the permission. Approval
  outcomes, approved endings and access-link recipient decisions name the person who decided or asked, and
  personal Workflow events name their owner. An approved ending that open requests refuse is recorded and
  tells the person who asked and the approvers (Exchange Not Ended). Two versions uploaded to one document
  at once get distinct numbers.
- **Record disposal.** A request whose review used a worksheet, that sent numbered notices, or that was
  followed up can be disposed of again; a disposal that kept being retried completes on its next pass.
- **Named sections and copy rule.** The submission, change and earlier-answer sections are named by their
  titles for screen readers, and each Appeal button names the review it appeals. A member who can copy a
  Template only into a place owned by someone else sees why typed answers may refuse the copy.
- **Fields refusals and draft approvals.** A Fields refusal on the request and response workspace reads and
  on group entry commands answers 400 `INFORMATION_REQUEST_FIELD_VALUE_INVALID` or 412 with the current
  ETag instead of 500, and response saves state that code too. A draft approval that starts the Exchange
  without recipient acceptance moves its requests on from the draft as acceptance does, so their clocks
  start from it.
- **Retained accounts and history.** Member account deletion checks retained records before
  removing membership. Malware scan reuse stays within one request. Access-link party lists
  return safe labels and their collection revision, and request headers consistently identify
  the Template Version. Authority reference and reason lengths are validated as bad requests.
  V188 adds archive scope and complete child disposal counts; V189 scopes append-only disposal
  exceptions and checks mapped actor and verification vocabularies for new writes.
- **Contracts and concurrent reads.** Request changes observe Exchange policy committed while a
  parent lock was waiting. Rate-limit responses include their retry delay in the JSON body, and
  authorization refusals consistently carry the forbidden reason code. Public command keys are
  limited to 128 characters. Shared web models match numeric timestamps, session capabilities and
  record-owner roles; signed-in reviewer commands send account credentials. V187 adds query
  indexes, instant storage boundaries and paired execution-grant revocation checks.
- **External records and accepted facts.** Retained reads use read permissions after the parent
  Exchange stops. Every reconciliation has a run audit event; connector claims stop at ten attempts
  and removed occurrences cannot receive values. Deleted-source and corrected-item facts cannot be
  reused; corrected items cannot be promoted again. Waived items persist as declared facts without
  review provenance. Supersession remains recorded after a successor is disposed of. Unknown child
  targets use authorization refusals. Business references and revocation reasons are length-checked.
  Reuse can explicitly confirm clearing hidden answers, and exports withhold their fact metadata.
  External verification timestamps allow up to five minutes of clock skew. V186 supplies the new history.
- **Records and preservation boundaries.** Record exports have a configurable 25 MiB default
  storage limit and subject reads audit every source. Preservation inputs and retention ages are
  bounded. Repeated restrictions retain affected targets; privacy deletion reuses existing claims.
  Fact promotion serializes with subject restriction. Disposal removes request Shares, links and
  sessions and clears deleted object locators. Failed claims and ready archive streams avoid idle
  work starvation. Preservation history remains immutable during disposal, hold keys are normalized,
  personal archive/ledger ownership is checked, and platform export routes exclude personally owned exports.
  Legacy hold responses retain user/organization aliases and disposal races return stable conflicts.
  Preservation audit keeps reasons, effective time, caller sessions and explicit system attribution.
  Repeated restriction records keep their link after lifting, and unknown audit owners are refused.
- **Clocks and notices.** New clocks count from the start command when no received time is supplied.
  Issue/acceptance reset paused draft budgets too, and reminders respect the effective start.
  Overdue times retain the passed deadline, including after a zero-budget pause/resume. Scheduling
  deferral keeps the edit ETag. Input lengths, policy collections and durations are bounded; clock
  changes use lowercase reason codes, and unknown/foreign clock policies have the same refusal.
  Manual reminders dispatch through their event count, identical party principals are deduplicated,
  retry backoff leaves first deliveries eligible, and superseded/paused clock notices are skipped.
  Stored subject/hash now cover the deployment prefix and delivery sends the frozen subject verbatim.
- **Review and submission boundaries.** Review queues list current open stages with request, Exchange
  and stage names and recover failed reads. Reviewer-party revocation removes undecided assignments;
  declared recusals prevent reassignment. Finished requests expose read-only review controls.
  Administrators can read their own findings/comments without gaining submitted content. Review
  inputs have stable malformed-command refusals and item decision refusals name their Requirement.
  Reopening refuses an edited, unsubmitted correction. Audit links identify delegates and correction
  transitions. Review dialogs keep inputs and display refusals, and submitted files preview inline.
  Waiver choices follow each item's policy. Review-before-submit now uses authorized server snapshot
  answers covered by its ETag; confirmations wait for pending work and show strength/validity policy.
  Withdrawal asks for confirmation and an optional reason, including permitted recovery of another
  party's package. Supplement results link to the draft and stale retries preserve the reason.

- **Submission and amendments.** Hidden assertions are omitted and cannot be confirmed. Partial
  answers do not complete an assertion, and exceptions need a reason. Repeating a current assent
  keeps the original role sequence. Preview actions now reflect execution-grant continuation rights.
  Meaning changes require reconfirmation of Field values and file-only answers; new Requirements
  do not reconfirm earlier responses. Eligible group members and linked accounts see their party's
  amendment notices. Overlong amendment or withdrawal reason codes return a malformed-command
  refusal, and privacy corrections give the same refusal for unknown and out-of-scope items.
  Withdrawal remains a recovery right of submitting parties and administrators, including packages
  submitted by someone else, subject to the existing stage, review and request-state restrictions.
- **Exchange status.** `PUT /exchanges/{id}` let anyone who could view an Exchange set its status to
  `INITIATED`, which froze every request on an active Exchange, and let a finished Exchange be reopened.
  The generic update now refuses `INITIATED` (`400`), and every Exchange status follows one transition
  table: from `INITIATED` or `ACCEPTED_STARTED` to an acceptance, rejection, ending or rescission, and
  nothing from `ENDED`, `REJECTED` or `RESCINDED` (`409`). Accepting, rejecting and ending through an
  access link or a Workflow, and a Workflow's automatic acceptance, are now recorded on the audit ledger
  like the same changes made in the app.
- **Assigned to you.** A signed-in party who was not on the Exchange had no way to reach their request.
  A new **Assigned to you** page in the main menu lists every request in which you hold a role, with its
  Exchange and your next action, and the reviews waiting for you (`GET /information-request-assignments`).
  The menu entry appears while something is assigned to you, also through a group.
- **Answered by.** A typed-data or document item could be answered by Attestor, a role that can confirm
  but never answer, so such an item could never be completed. The editor no longer offers Attestor for
  those items, and saving and publication refuse it.
- **Recipient sign-in.** Turning on **Require recipient sign-in** for an Exchange whose recipient had
  verified access could be silently undone in the same save. The setting and the access validity now
  persist.
- **Delegated authority.** A delegate who was not a party could never act, because access to the
  request came only from a Share. An active, unexpired authority now gives its delegate access through
  the central authorization service: reading the request and answering, uploading for or confirming the
  named Requirement (or every Requirement of the party) with the party's role. Granting is refused for a
  party whose role answers nothing and for a Requirement outside the party's role. It remains API only.
- **Reviewers sign in.** A Reviewer added by email became a participant with no review screen, so a
  stage could wait forever. A Reviewer must now be a person with an account or a group: Add party no
  longer offers Reviewer by email, and the server refuses a participant reviewer at party assignment,
  review assignment and delegation (`INFORMATION_REQUEST_REVIEWER_SIGN_IN_REQUIRED`).
- **Reviewer queue.** A reassigned reviewer party, or one whose participant linked an account, now shows
  its review assignments to whoever acts for the party, and no longer to the former holder.
- **Notices to groups.** A reminder, overdue or amendment notice owed to a party held by a group went
  nowhere and was marked undeliverable. It now goes to every active member of the group who has an
  email address, recorded as one notice per member. The Notices tab shows these as sent to a group
  member and never shows a member's address (`recipientWithheld`). Migration V181 allows one notice
  per member.
- **Email addresses of accounts.** Adding a party by the email address of a member of another
  organization skipped the sharing settings that apply to that person's account, such as requiring a
  trusted organization or a suspended trust. The address is now checked as that account first.
- **End-to-end encrypted evidence.** An upload declared as end-to-end encrypted was stored without
  checking its type or scanning it, while other parties could still download it. Such an upload is now
  refused (`INFORMATION_REQUEST_EVIDENCE_OPAQUE_CONTENT_REFUSED`). The app always uploads evidence
  unencrypted end to end, so only direct API callers are affected.
- **Ending an Exchange.** Ending an Exchange with open requests named, and counted, requests the person
  ending it could not see, and cancelling the remaining requests by ending did not check that they may
  cancel them. The refusal now names only requests you can see, and ending cancels the remaining
  requests only for someone who may cancel every one of them; anyone else is told that only the people
  who manage them can cancel them (`INFORMATION_REQUEST_REMAINING_REQUESTS_NOT_CANCELLABLE`).
- **Refresh rules.** The follow-up history listed every refresh rule, naming documents a party could not
  see. People who manage follow-ups still see every rule; anyone else sees only the rules of documents
  they may see.
- **Clocks before acceptance.** A clock on a request whose Exchange was not yet accepted sent reminders
  and overdue notices, and could expire the request, while nobody could answer it. Clocks now wait
  until the Exchange is accepted, and a clock started without a received time then counts its whole
  time from the acceptance.
- **Clocks after a complete submission.** A request waiting only for review was reminded, marked
  overdue and could expire, after which it could no longer be reviewed or closed. A complete submission
  now pauses the request's running clocks; a returned correction or a withdrawal resumes them with the
  time they had left, and a clock paused by hand stays paused. Reminders and overdue notices, including **Send
  reminders**, go only to parties who still have items to provide. See the new help article **Due dates and reminders**.
- **Notices that cannot be prepared.** A notice whose rendering failed, for example a Communication
  naming the sender's email on a request no account created, was retried silently on every pass and
  could hold back every newer notice. Each failure is now recorded, a failing notice waits behind
  newer ones, and after five failures it is shown as failed; the sender's email now renders empty
  when there is no such account. Migration V182 adds the render failure record.
- **Amending drafts.** Amending a draft emailed its parties that what was asked of them had changed,
  although the request had never been sent to them. An amendment of a draft now owes no notices.
- **Send reminders.** Sending reminders from the operations queue reported an error although every
  reminder was queued, because the answer could not be written; listing a request's delegated
  authorities failed the same way. Both now answer a JSON array, so the page reports how many notices
  were queued and when a request can be reminded again.
- **Empty Fields in conditions.** A Field nobody had answered counted as unknown, so a condition testing
  whether it is empty never asked its item until something was typed and cleared. A Field that an item
  asks for and nobody has answered now counts as empty.
- **Repeated groups after an amendment.** After an amendment, adding a row to a repeated group could
  reuse the identity of a row removed earlier and fail every time. New rows now always take a new
  identity.
- **Nested groups answered by different roles.** The party answering a nested group could not see the
  outer rows to add its first entry under, and nobody could remove or reorder the outer rows. The
  outer rows are now shown to that party without their answers, and the party answering the outer
  group can remove and reorder its rows while they hold none of another party's nested answers.
- **Answers given once.** After a withdrawal, an amendment that changed what an answer given once
  meant asked for that answer to be confirmed again, which its lock refused, so the request could not
  be submitted again. Such an amendment is now refused (`INFORMATION_REQUEST_AMENDMENT_SUBMITTED_SCOPE_CHANGED`).
- **Accepted file types.** A Document Requirement could accept a file type such as `PDF`, which is not
  a media type, so every upload for it was refused. The Template editor now offers file types to choose
  from, and saving and publication refuse a value that is not a media type.
- **Field refusals.** A Field value the Fields engine refused on a request command, such as reusing an
  accepted fact whose option a later Field version dropped, answered `500`. It now answers `400`
  (`INFORMATION_REQUEST_FIELD_VALUE_INVALID`), a stale Field precondition `412`, and such a fact is no
  longer offered. Creating a request from a Template Version whose Schema was retired answers `409`
  (`INFORMATION_REQUEST_TEMPLATE_VERSION_SCHEMA_UNAVAILABLE`) instead of `500`.
- **Reviews.** Assigning the reviewer who completes a stage that already held enough decisions now
  settles the review at once; before, the review stayed in review for good. A reason a reviewer types
  before choosing an outcome is no longer thrown away when the worksheet is saved: the save asks for
  the outcome first. Respondents now see reviewer comments on an item that belong to no finding, and
  can comment on any item they can see once the review is decided.
- **Document items.** A document item collecting files showed neither whether it is required nor its
  help text. Both now show above its files, as they do for every other item.
- **Condition values.** A Template condition took every compared value as typed text, so a Yes or No
  Field could not be compared with Yes, a choice needed its internal code, and spaces inside a text
  value were dropped while typing. The value now matches its Field: a Yes or No choice, the Field's
  options by name, date and date-time pickers, and a number hint.
- **Unsaved Template changes.** Going back to Templates or to another Settings tab dropped unsaved
  Template changes without asking, and a draft someone else saved meanwhile could only be recovered
  by opening it again, which lost them. Both now ask first, closing the page warns, and a refused save
  offers **Keep my changes** (saved over the latest draft) or **Open latest draft**.
- **Protected questions.** When verification ran out while a protected question was being answered,
  every later save, also of other answers, was refused with a plain `403` until the page was reloaded
  by hand. Such a save now answers `403 INFORMATION_REQUEST_CONFIDENTIALITY_DENIED`: the workspace
  reloads to hide the question and offer verifying again, keeps the unsaved answer to it in the tab,
  and saves the other answers.
- **Unsaved answers.** Unsaved answers kept in the browser tab did not record the answer they changed,
  so reopening the tab, verifying again or a refresh that failed sent them over an answer saved
  elsewhere in the meantime. Each unsaved answer now remembers the saved answer it started from. When
  that answer changed elsewhere, saving stops and **Answers saved elsewhere** shows the latest saved
  answer, to keep with **Keep saved answer** or replace with **Save responses**. An unsaved answer equal
  to the latest saved one is dropped, and answers already saved no longer stay in the tab.
- **Disposing requests that refer to each other.** A privacy deletion was refused whenever one of the
  subject's finished requests referred to another, for example a follow-up, a carried-forward answer
  or a recertified fact, even though both were being deleted, and a superseding follow-up and the
  request it superseded refused each other for good. A deletion now claims such requests together and
  finishes the referring request first, and disposing a superseding follow-up clears the superseded
  request's link to it (V183), so retention disposes of the follow-up first and the superseded
  request after it.
- **Account links.** A request through which a participant linked an account could never be disposed
  of. Disposal now keeps the link to the account and clears the request it names (V184).
- **Contact code records.** After a verification, the next three codes sent through the same link
  reused earlier audit keys and were not recorded. Every code sent now records its own
  `information_request.contact_challenge.issue` event.
- **Audit archive verification.** Verifying an archived segment of a personal workspace's audit stream
  stopped the pass, so later segments in the batch were neither recorded nor alerted on. Personal
  streams are now recorded under their owner, and one segment that cannot be recorded no longer stops
  the others; it is logged with the alert marker instead.
- **Operations queue.** Each page of the queue read and scored every request the owner had ever had.
  The queue is now filtered by state, Exchange, title, request id and assignee, ordered and paged in
  the database, so a page reads only its own requests; a filter on service level or exceptions reads
  the matching requests in batches.
- **Deleted Exchanges.** The unfinished requests of a deleted Exchange stayed open for good: they could
  not be cancelled, expire or be disposed of, and kept counting toward the owner's open requests and
  evidence allowance until new requests were refused. Deleting an Exchange now cancels them with the
  reason `PARENT_DELETED`. Requests of an Exchange deleted before this change are not cancelled.
- **Parties of another workspace.** Opening a request owned by a workspace other than the active one
  offered people, groups and references from the active workspace, which the server then refused. Add
  party and Reassign now offer only an email address or a trusted organization's published group for
  such a request, and say why.
- **Template Versions.** The Versions panel acted only on the newest published Version, so an older one
  could never be retired, and retiring the last one silently ended the Template. The panel now lists
  every published and retired Version, lets you choose which published Version to start a draft from,
  retire or copy, and says before you retire the only published Version what that ends. A retired or
  earlier Version can be read through `GET /information-request-templates/{id}/versions/{versionNumber}`,
  and a Template whose Versions are all retired says so.
- **Template reads.** An unknown Template id answered `404` while another owner's answered `403`, and a
  member could open a Template that was never published. Both now answer the same `403`, and readers see
  only Templates with a published Version on the detail route as in the list.
- **Template structure.** Saving now refuses more than 100 repeatable groups, groups nested more than
  five deep or with occurrence paths too long to store, occurrence counts above 100 required or 1000
  allowed, a condition that reads an answer the governed Requirement's occurrence can never see, a
  substitute outside the document's own group or the top level, and a collected Field that does not
  exist, each naming the item. Anchor and rule keys are lowercased like the keys they name, lists read
  back in key order so equal content gets the same content hash, and saving a draft no longer overwrites
  a status set by a Version retired at the same time. A one-off request's name and description now have
  the same length limits as a Template's.
- **Before publishing.** The editor's check now also reports conditions that depend on each other, a
  group answered in two submission stages, an alternative document that names its own alternatives or
  sits in another group, a condition reading an answer from a group it cannot see, accepted issuer,
  jurisdiction or language values for an attribute that is not asked, compartments and compared values
  over their limits, condition values that are not a whole number, number, date, yes or no, or option of
  the Field, a retired Request Schema, and the group limits, so these no longer pass the check and fail on
  save.
- **Template editor reads.** A reader no longer reads Schemas they cannot use, a plan or permission
  refusal of the Template list is stated without a Try again that cannot help, a slower list from the
  tab you left no longer replaces the one you chose, and a failed read of the chosen Schema Version's
  Fields offers Try again. A refusal that names no part of the Template no longer offers Show.
- **Copying a Template.** A copy now keeps the source description, which you can edit, and the dialog
  explains that typed answers keep their Schema, so a copy for another owner works only with a Platform
  Schema.
- **Confirmation roles.** The roles that confirm keep the order you choose, a role sequence can be
  reordered, and the number of assents needed follows the roles until you set it. Switching a
  requirement to a document clears supporting-document links it can no longer have, and the waiver hint
  appears only on documents.
- **Template pickers.** The Blueprint and Workflow editors state a failed Template read with Try again
  instead of showing an empty list. The Blueprint editor takes each Template's latest published Version
  from the list (`latestPublishedVersionId`) instead of reading every Template in full, shows the reason
  a save was refused, and a Workflow reopened with requirement conditions finds the Template they belong
  to.
- **Expiry refreshes.** A refresh rule on a request whose Exchange had ended, or was rejected, rescinded or
  deleted, was retried every day for good, while an expired request was never refreshed automatically.
  Automatic refreshes now run only while the Exchange can take requests, and include expired requests.
- **Workflows on requests.** A Workflow running on a request, such as an approval started when it was
  issued, kept running after the request was cancelled, superseded or expired, or its Exchange was
  rejected, rescinded or deleted. It is now cancelled at that point.
- **Supplement reasons.** The reason for a supplement was free text stored and audited as a reason code.
  Request more information now offers reasons to choose from, and the server refuses a reason that is not a
  code (`INFORMATION_REQUEST_REASON_CODE_INVALID`).
- **Starting an Exchange from a Blueprint.** Every Blueprint participant became a party of the request,
  including one removed on the form and the initiator, which could refuse the start. Only participants the
  Exchange has become parties now, never the initiator. A deleted or deactivated Blueprint can no longer
  create a request (`INFORMATION_REQUEST_BLUEPRINT_UNAVAILABLE`), and an unknown Blueprint id is refused like
  one you cannot see.
- **Parties of follow-ups.** A scheduled refresh or a reviewer's supplement dropped the owner's personal
  group from the follow-up, because sharing was checked as the system or the reviewer. It is now checked as
  the person who added the party.
- **Clearer refusals.** Issuing a request with more acting parties than the plan allows now states the
  plan's limit and which roles count, and a recurrence that has created all its requests says it is
  complete (`INFORMATION_REQUEST_RECURRENCE_COMPLETE`) instead of not due.
- **Party and access refusals.** Several refusals on a request the caller can see answered `404 Not
  Found`. A trusted recipient whose Exchange access was withdrawn now answers
  `409 INFORMATION_REQUEST_TRUSTED_RECIPIENT_UNAVAILABLE`; an address named as the subject and a
  delegated authority for an unsupported principal answer `400 INFORMATION_REQUEST_PARTY_ASSIGNMENT_INVALID`;
  an access link for a removed party, the subject party or a party without request access answers `409`
  with its reason; and the party list's refusals carry `INFORMATION_REQUEST_FORBIDDEN` like the other party
  commands. A link of another request answers like an unknown link, and a session of another request
  presented to link an account answers like an unknown request, before anything is locked.
- **Verifying again.** A signed-in caller asked for a protected item's content was sent to verify again
  even when verifying could not let them see it. Such a caller is now refused like for any item they
  cannot see.
- **Access link records.** A replaced access link now records when it was revoked, and a reassignment
  records who held the role before and who holds it now. A repeated account link answers only while the
  party and its account access still hold.
- **Sessions and subjects.** An access session's secret is checked before its request's Exchange is
  locked, a subject named by its kind is created only under the request's lock so a retry replays it, and
  the abuse limit settings refuse a zero or negative value when the application starts using them.
- **Conditional answers.** A save could clear an answer whose condition was about to hold again, because
  removals were decided before answers it restored were back; restorations now settle first. An amendment
  that kept a rule's key but changed its meaning left an answer hidden although its condition held; it is
  now restored. Hiding and restoring a retained answer no longer looks like a change, so an answer returned
  for correction stays returned.
- **Answers without a value.** Switching an answer to not applicable, unavailable or another answer without
  a value kept the earlier Field value, which then counted and was submitted. That value is now cleared,
  with its history kept.
- **Response saves.** A save is limited to 200 answers, 4000-character notes and 500 Field values, returns
  current Field ETags after a conditional clear in the same set, and its audit record names the answered
  Requirements; occurrence changes record what changed. A respondent's workspace no longer carries the
  Template's reuse purpose, content hash or Schema Version id.

- **Evidence inspection.** Every file inspection shares a limit of two concurrent inspections and runs before
  the Exchange and request are locked. Progressive JPEGs over 25 million pixels are unreadable, PDF page
  counts follow the actual pages, and password-protected OpenDocument files are marked encrypted.
- **Evidence uploads and removal.** Uploads without a declared `Content-Length` answer `411` before the body
  is accepted. File names containing invisible formatting characters are refused. Removal uses
  `POST .../evidence-artifacts/{artifactId}/removals` with a JSON body; removal and withdrawal reasons are
  limited to 512 characters. A removed file stays hidden from respondents on mutation as on reads.
- **Evidence checks and reads.** Never-scanned files take priority over rescans; scanner and storage failures
  record an assessment so retry delays apply. Scan reuse reads only the newest matching result.
  An uploader's linked account can open its own file while checks are pending. Missing or changed stored
  content logs `INFORMATION_REQUEST_EVIDENCE_CONTENT_INTEGRITY_FAILED` for operators. Supporting evidence
  follows active group occurrences only.

## Connector work leaves an audited history of every attempt and outcome (2026-10-25, sixth verification remediation)

- **Connector exchanges.** The background worker that calls connectors changed an exchange's attempt
  count, next attempt time, waiting state and failure without recording why or when: only the request
  for the lookup and the values a completed lookup returned were audited. Each attempt it starts,
  each failed call it retries, each wait a connector asks for, the completion and every failure,
  including one before any call, is now a transition of the request with its own audit event
  (`information_request.external.attempt`, `.retry`, `.pending`, `.complete` and `.fail`), committed
  with the change and attributed to the platform service account. Failures and retried calls carry
  the outcome `FAILURE`, and a failure names its code.
- **Nothing unrecorded.** If the audit record of a step, or of a value a connector returned, cannot be
  captured, the step is rolled back: the connector is not called for an unrecorded attempt, and an
  unrecorded outcome is discarded and tried again after the attempt's lease. A step is never recorded
  twice, and the audit reconciliation now covers every one.
- **Payloads.** Each event carries the exchange, connector, Requirement, state, attempt count and the
  next attempt time or failure code. Lookup references, external references and imported values stay
  out, and `nextAttemptAt` joins the values the audit history shows.
- **Migration.** V180 adds the five changes to the request history's list of recorded changes. Existing
  history is unchanged.
- **Help and docs.** The external sources and operations articles, the API reference, the operator
  guide, the migration notes and the recovery runbook describe the new records.

## Audit history and record exports name only the requests the caller may open (2026-10-25, fifth verification remediation)

- **Audit history.** A request's audit history showed the `successorRequestId` of each follow-up made
  from it and the `supersededByRequestId` of its replacement to anyone who manages the request, also
  after that person lost access to the follow-up or replacement, while the header and the follow-up
  history already withheld them. Each id is now checked through the central authorizer and, when the
  caller may not open that request, withheld and counted with the other withheld values. The event
  itself stays listed. The owner audit search, whose readers may list every request the owner holds,
  is unchanged.
- **Record exports.** A new record export names the replacement, and the request behind each accepted
  fact the request reused, only when the person making it may open that request; otherwise the
  reference is `null` and marked as redacted, and the stored hash covers that content. Reading an
  export leaves out the same way any such request the reader may no longer open, without changing the
  stored export or its hash, and says how many it left out (`withheldRelatedRequestCount`). Subject
  exports read through privacy are unchanged.
- **Help and API reference.** The operations article says which audit values and export references are
  withheld. The API reference and operator guide state the rules.

## Follow-up history names only the requests the caller may open (2026-10-24, fourth verification remediation)

- **Follow-up history.** `GET /information-requests/{id}/successors` checked only that the caller may
  open the request it was read for, then listed the request it follows and every follow-up, with their
  ids, submission package, reason and time, even where the caller may not open them. A party removed
  from a follow-up, or from the request a follow-up came from, could still read those details from the
  other request's history and its Follow-up requests panel. Each related request is now checked
  through the central authorizer and left out, as if it did not exist, when the caller may not open
  it. The recurrence schedule and refresh rules, which belong to the request itself, stay listed.
- **Next due time.** The next request's due time counts every request a schedule has created, so it is
  now given only to a caller who may open all of them. The lineage also says when a limited schedule
  has created all its requests (`everyOccurrenceCreated`); the panel says "Every scheduled request has
  been created." only then, and states no due time when it is withheld, while **Create the next request**
  stays available to those who may create it and the server answers whether it is due.
- **Superseded requests.** A superseded request's header names its replacement (`supersededByRequestId`) only to a
  caller who may open the replacement.
- **Help and API reference.** The managing article says earlier requests and follow-ups are listed only
  when you can open them, and when the next due time is shown. The API reference states both rules.

## A draft can be superseded and scheduled from its Manage page (2026-10-24, third verification remediation)

- **Drafts on the Manage page.** The server lets a request's owner supersede a draft with another
  request of its Exchange and give a draft its recurrence and refresh rules, but the Manage page offered
  neither: **Supersede** appeared only once a request was issued, and the Follow-up requests panel was
  hidden on drafts. A draft now offers both whenever the response workspace's `authorActions` and the
  owner's standing allow them, as an issued request already did. Follow-up requests, supplements, and
  refreshes still start only once the request is issued, and a draft whose owner may not start new
  work, whose changes are paused, or whose Exchange was deleted offers neither. A draft created as a
  follow-up now also shows its link to the earlier request.
- **Help and API reference.** The managing article says a draft can be superseded and take its schedule
  and refresh rules, and that follow-ups start once the request is issued. The API reference states the
  same for supersession, recurrences, and refresh rules, and what `authorActions` permits on a draft.
- **Tests.** Test data classes now live in model packages, in this feature's tests and in the rest of the backend tests;
  no behavior changes.

## Hidden items answer like unknown ones, and the Manage page follows the parent Exchange (2026-10-23, second verification remediation)

- **Unknown and hidden items answer alike.** Inside a request a caller may open, a respondent could
  tell an item hidden from them from an id that does not exist, because the item was looked up, or its
  lock or count checked, before the caller was authorized for it. Saving answers that named a
  Requirement of another party answered `403`, while an unknown Requirement answered `409` naming it.
  The same held for the file routes of a Requirement, for adding, ordering, and removing group
  occurrences, for a file a submission holds, and for a respondent's review comment. Each now checks
  that the caller may see the item first, so both answer the same `403` with the same body. A caller
  who may see an item still gets the specific refusal for it. Requirement ids named for confirmed
  clearing are checked the same way, so an id the caller may not see now saves nothing, where it was
  ignored before, and confirming the clear of another party's answer the caller may not see answers
  `403` instead of `409 INFORMATION_REQUEST_HIDDEN_RESPONSE_CLEAR_NOT_PERMITTED`.
- **Manage page and the parent Exchange.** After its Exchange ended, was rejected or rescinded, or was
  deleted, a request's Manage page still offered actions the server refuses: Promote, Record decision,
  Reconsider and Appeal, follow-up requests and refresh rules, and on a deleted Exchange also party,
  link, clock, completion gate, and lifecycle changes. The response workspace now carries
  `authorActions`, the actions the server permits on the request now, and the page offers only those,
  still applying the owner's standing as before. After an Exchange ends, revoking an accepted fact stays
  available; on a deleted Exchange nothing is offered. **Preview as recipient** is offered only while
  the server allows it, until the request finishes or its Exchange stops.
- **Help.** The overview says what a request page still offers once its Exchange stops, the managing
  article says when Preview as recipient and follow-ups are offered, and the outcomes article says that
  promotion and decisions end with an active Exchange and revocation ends when it is deleted. Two
  sentences that ran into a following bold word now keep their space.

## Unknown request ids, access reductions while paused or stopped, and retries that answer the current state (2026-10-23, verification remediation)

- **Unknown and hidden requests answer alike.** A signed-in caller could tell a request id that does
  not exist (`404`) from a request hidden from them (`403`) on most request routes, because the
  request was looked up or locked before the caller was authorized. Every signed-in route that names
  a request now checks that the caller may view it first, so both answer the same `403` with the same
  body. On the `/no-auth/` surface any request other than the link's own still answers `404`. The
  requests listing of an Exchange likewise answers an Exchange id that does not exist with the same
  `403` an Exchange hidden from the caller gets, where it answered `404` before.
- **Retries answer the current state.** A command retried with the same `Idempotency-Key` after a
  later change could return the current request, party, file, or responses together with the ETag
  recorded by the first command, so the answer and its validator described different revisions. A
  retry now answers with what the command changed as it stands now, and every ETag it returns, in the
  header and in the body, describes that same state. This covers lifecycle commands, the completion
  gate, creation, amendments, follow-ups and their definitions, parties, delegated authority, access
  link revocation, account linking, files, recertification, and answers. A retried answer save now
  shows the answer as it stands, including a later change to it.
- **Access links follow the request's standing.** Creating, resending, or replacing an access link was
  still accepted while the owner's account was suspended or after the request was stopped. These are
  now refused (`403` with `SUBSCRIPTION_SUSPENDED`, or `409 INFORMATION_REQUEST_EXECUTION_GRANT_REVOKED`).
  Revoking a link only reduces access and stays possible.
- **Manage page.** While a request's changes are paused or after it was stopped, the Parties panel hid
  Remove and Revoke link, although the server allows both. They are now offered; Add party, Reassign,
  Create link, and Resend link stay hidden. On a Stopped request a clock can be paused or extended
  again, while starting and resuming a clock stay unavailable. While changes are paused no clock can
  be changed.
- **Outcomes.** Promote and Record decision, with Reconsider and Appeal, are no longer offered on a
  request marked Changes paused or Stopped, where the server refuses them. Revoking an accepted fact
  stays available.
- **Help.** The overview says what a manager can still do on a Stopped request, and the outcomes
  article says that facts are not promoted and decisions are not recorded while changes are paused or
  after a request was stopped.

## Unanswered link changes recover, supersession retries follow their reason, and lifecycle actions follow the request's standing (2026-10-22, code audit)

- **Supersession retries.** A supersession retried after its response was lost reused the earlier
  `Idempotency-Key` even when the retry named another reason, so the server refused it with
  `409 COMMAND_RECEIPT_FINGERPRINT_CONFLICT` and the page kept showing the old request. The retry key
  now follows the whole command, its replacement and its reason: an unchanged retry keeps its key and
  a changed reason gets a key of its own.
- **Unanswered access link changes.** When Create link or Resend link lost its response after the
  server had made the change, every later attempt repeated the completed command's key and was refused
  with `INFORMATION_REQUEST_ACCESS_LINK_ALREADY_ISSUED` or
  `INFORMATION_REQUEST_ACCESS_LINK_ALREADY_ROTATED` until the page was reloaded. The page now shows the
  server's explanation, rereads the parties and their links, and the next Resend link creates a new
  link.
- **Lifecycle actions by standing.** A draft marked Read only or Changes paused still offered an
  enabled Issue, and Cancel request and Supersede stayed on requests in those standings, although the
  server refuses all of them. Issue is now offered only while the owner may start new work. Cancel
  request and Supersede are hidden while a request is Read only or its changes are paused, and stay
  available to the owner of a Stopped request or one continuing as issued.
- **Completion gate on a Stopped request.** Must finish before the Exchange can end was hidden on a
  Stopped request, although the server lets its owner change it there, as it does Cancel request. The
  switch is now shown on a Stopped request too, and stays hidden while a request is Read only or its
  changes are paused.
- **Help.** The managing article states that Cancel request, Supersede, and the Must finish before the
  Exchange can end switch stay available on a Stopped request, but not on one marked Changes paused or
  Read only.

## Authority before state, linked participants, group removal, clocks at issue, disposal-safe privacy exports, and lock order (2026-10-22, deep analysis backlog)

- **Authority comes first.** Evidence, response, group-occurrence, external-source, generated-output,
  recurrence, refresh-rule, review comment, reconsideration, appeal, Business Decision, and
  recertification commands checked the request's state, locks, `If-Match`, or grant standing before
  the caller's authority, so a refusal could disclose them. Each command now authorizes the caller
  first. Respondent-facing commands check that the caller may view the request, then keep their
  specific refusals for respondents who may. Template Version selection resolves only Versions the
  owner holds; any other Version answers as unknown, without its plan or lifecycle.
- **Failed reads are stated.** The request's Reviews panel and several author workspace and Template
  settings reads showed a refused or failed read as an empty list. They now say so with Try again.
- **Review receipts.** A retry of an override, assignment, assignment change, or reopen under the same
  `Idempotency-Key` with a different `If-Match` is now `409 COMMAND_RECEIPT_FINGERPRINT_CONFLICT`.
- **Ending an Exchange.** A request cancelled when its Exchange ended kept its access links. They are
  now revoked with reason `REQUEST_CANCELLED` in the same transaction; an open session keeps reading
  until it expires.
- **Prompts for reviewers.** Review items carry their Requirement's `prompt`, and the amendment summary
  names changed items by prompt instead of a humanized key.
- **Evidence.** Upload refusals state the limit that applied (file size, file count, or allowance)
  instead of always saying the file is too large, and policy names are shown in words. A file still
  being scanned counts toward a minimum and shows Awaiting checks rather than making the Requirement
  deficient. Every missing or unaccepted detail is listed, not only the first of a kind. The item and
  file routes of a Requirement hidden by its condition answer `404`. Reviewer downloads keep the
  file's name and extension, and evidence panel ids no longer collide when one Requirement's key
  extends another's.
- **Fields and Schemas.** Emptying an answer by deselecting every option or blanking the text clears
  it, so the Template decides whether it may be empty, as for every other type of answer.
  Blueprint Field defaults skip read-only bindings, and a default that does not fit is logged instead
  of stopping the Exchange start. Schema draft, publish, retire, and new-draft commands lock the Schema
  first, so a draft update can no longer change a Version being published.
- **Templates.** Template text has stated limits (`400` naming the field): keys 128 characters,
  compartment keys 64, titles and display names 255, descriptions and prompts 1024, help text 2048,
  accepted values 255, and condition values 256. Copy as a new Template is offered only once a Version
  is published. Retiring the only published Version while a draft exists leaves the Template a draft.
  A refusal for a bad reference names the right Requirement, and a refused copy or retire shows its
  message in the open dialog.
- **Parties.** Party and delegated authority refusals carry reason codes instead of a bare `404`:
  `PARTY_ASSIGNMENT_INVALID` (`400`), `PARTY_NOT_REASSIGNABLE`, `PARTY_ALREADY_REVOKED`,
  `DELEGATED_AUTHORITY_PARTY_INELIGIBLE`, `DELEGATED_AUTHORITY_ALREADY_REVOKED` (`409`), and
  `DELEGATED_AUTHORITY_WINDOW_INVALID` (`400`). Parties created with a request (Blueprint defaults,
  follow-up copies, successor subjects) record their assignment in the history and audit. Deleting or
  deactivating a group revokes every active request party it holds (reason `GROUP_REMOVED`), and a
  group that still holds an active request Share is deactivated rather than deleted. An Exchange
  access grant resolves an external participant only when the Exchange's owner holds it.
- **Linked accounts.** Linking an account moves every part the participant holds on the request to
  the account and ends the access links of each part. The participant itself no longer acts for a
  linked part, the conflict check covers every live Share of the account, and the owner's sharing
  policy applies (`409 INFORMATION_REQUEST_PARTY_SHARING_NOT_PERMITTED`). An access link of a linked
  part is refused as linked to an account rather than as revoked. Once the Exchange requires
  recipients to sign in, a participant who never linked cannot link (`INFORMATION_REQUEST_RECIPIENT_SIGN_IN_REQUIRED`)
  and is reassigned to an Exchange recipient
  instead; the Exchange settings say so when sign-in is turned on.
- **Verification codes.** After too many attempts, `429` now sends `Retry-After` with the seconds left
  on the current pause rather than always 60. An empty code is no longer submitted by Enter, and the
  sent notice and the entered code are cleared once a session starts or ends.
- **Structured answers.** Move up and Move down move an entry one place. Section progress no longer
  counts a cleared Field as answered. Reusing an accepted answer is offered only where the
  Requirement permits a provided answer. A confirmation to clear hidden answers is not reused for a
  later clear, and a Requirement the server shows is no longer hidden when its condition result is
  withheld.
- **Confirmations and packages.** A refused confirmation no longer lapses with the policy's validity
  hours; only a confirmation lapses. Withdraw is offered only for a package that can be withdrawn, a
  replaced package is marked Replaced by a later submission (`current`, `withdrawable` on the package),
  and withdrawing it says so. Keep stays available while refusing.
- **Follow-ups.** Recurrence, refresh-rule, and supplement actions are not offered on cancelled,
  superseded, or expired requests.
- **Reviews.** A withdrawn review is not a decision: respondents see no finding from it and cannot
  reply. Override is offered only on an open stage and an undecided item. A manager can add a finding
  from the item. After a stale save the latest review is loaded with unsaved choices kept, and typed
  comments, replies, and reasons stay until their command is recorded.
- **Due dates.** A clock started on a draft counts its budget from the moment the request is issued (a `RESUMED` event
  with reason `REQUEST_ISSUED`); its received time stays as recorded. When several
  reminders fall due at once, only the latest is sent and the earlier ones are recorded as
  `REMINDER_SUPERSEDED`. A paused clock shows the time left when paused instead of an old due time.
- **Privacy.** An access, export, or item correction about a subject whose record is being disposed is
  recorded as refused with `INFORMATION_REQUEST_DISPOSAL_IN_PROGRESS` instead of failing with a
  constraint conflict. A refused deletion records a disposal denial (basis `PRIVACY_DELETION`) against
  each request it could not dispose. A privacy request whose export went with a disposed request stops
  offering the record (`recordExportAvailable`).
- **Suspension.** Linking an account is refused while the owner is operationally suspended (`403`,
  `SUBSCRIPTION_SUSPENDED`), like every other change. Revoking a party, an access link, delegated
  authority, or an accepted fact still works, so access can always be reduced.
- **Starting an Exchange.** After Manage Information Request or View Exchange, and when recreating a
  rejected Exchange, the dialog starts empty and sends no earlier Blueprint. A Blueprint start copies
  Document Library files into storage only after its request is created, so a refused start leaves no
  stored file or thumbnail job.
- **Locks and email.** Removing an organization member or a group locks the Exchanges of the affected
  requests in identifier order before revoking parties, so it no longer deadlocks against bulk
  commands. Contact and step-up codes are emailed after the challenge is committed, so no lock is held
  while the email is sent and no code is sent for a challenge that was not recorded.
- **Records.** The audit ledger files an event by its owner type and owner alone. Every evidence
  version is backed by a Document Version, so scan, inspection, and total size apply to all of them.
- **Help and operator guide.** The evidence article states who can remove a file and that a partial
  answer with at least one accepted file counts as complete; the Templates and platform administration
  articles state when the Settings tab appears; the operator guide covers
  `quarkus.http.limits.max-body-size` against the evidence file limits.

## Replacement authority, clock changes during suspension, overdue notice after expiry, workspace-scoped sources, refused reads in follow-ups and supersession (2026-10-21, deep verification)

- **Superseding.** A manager of one request could supersede it with any open request on the same
  Exchange, including one they could not see, and an unknown id answered differently from a hidden
  one. Superseding now needs the supersede authority on the replacement too, checked before anything
  about the replacement is read: an unknown or hidden replacement answers `403`. Replays and
  changed-reason conflicts behave as before. The Supersede dialog lists only open requests you
  manage.
- **Clock changes while suspended.** Pausing or extending a clock still worked while the owner was
  operationally suspended. Every clock change now waits for the suspension to end (`403`,
  `SUBSCRIPTION_SUSPENDED`); a retry of a change made before the suspension returns that change.
  After an execution grant is revoked, pausing and extending stay possible as before. Clock commands
  authorize the caller before any state or standing check.
- **Overdue notice of an expired request.** When a due time expired its request, the overdue notice
  that due time owed was skipped as `REQUEST_FINISHED`. That notice is now delivered and retried like
  any other; reminders and every other notice of a finished request are still skipped, and removed,
  reassigned, suspended, and revoked cutoffs still apply.
- **Templates, Blueprints, Schemas, and due date policies by workspace.** Creating a request and
  starting a clock offered the active workspace's configuration even for an Exchange or request
  owned elsewhere, and kept an earlier organization's Templates after a direct workspace change. The
  Exchange listing now names `creationOwner` for callers who may create there; sources and due date
  policies are listed only while the active workspace is that owner, are keyed by its identity, and
  are dropped at once when the session leaves it. From another workspace the dialog says so and still
  offers a one-off request.
- **Refused reads.** A refused read of the follow-up lineage looked like a request with no
  follow-ups and still offered a new schedule, and a refused read of replacement candidates said to
  create one first. Both now state the refusal with Try again and withhold the controls that depend
  on the read until it answers.

## Trusted group members held back, scheduled work stopped by owner standing, re-invitations, review feedback and delegation (2026-10-21, current-state audit)

- **Trusted groups.** A member who joined a trusted group while its relationship was suspended or no
  longer eligible got no inherited Share, yet the group's own Share still let them open the request,
  its Exchange, and the group's assigned items, and act for the group party. Group-mediated access
  and the group party's members now follow the same rule as inherited Shares: a member the group
  Share never admitted is held back until trust is eligible again, and members admitted earlier keep
  working on issued requests. Removing a member still ends their access.
- **Scheduled clocks and notices.** Reminders, overdue notices, escalations and expiry ran while the
  owner was operationally suspended or after the request's execution grant was revoked, and could
  expire a request whose manual changes were refused. A due clock point now records nothing in those
  states (or without `SEND_REMINDER` in the grant) and is checked again an hour later; a deadline that
  passed during a suspension takes effect at the first check after it ends. A queued notice or retry
  in those states is recorded as skipped with the reason and is not sent.
- **Re-inviting a trusted participant.** Inviting a trusted participant again after their access was
  removed, or replacing a pending primary recipient with someone who was a participant before, failed
  when a request party recorded the earlier invitation. The existing recipient binding is now reopened
  for the new invitation (a fresh attestation, acceptance pending again), so request parties keep
  their reference and history. A released primary that a request party records stays as a former
  participant.
- **Review messages.** A successful comment or assignment change kept its message on the review page
  and hid every later refusal from recording decisions, saving the worksheet, or recording a finding.
  The page now shows the outcome of the latest command, and starting a command clears the earlier
  one.
- **Delegation.** A reviewer who does not manage the request saw every other reviewer as "Reviewer"
  when delegating. Each delegable assignment now lists `delegationCandidates` with each eligible
  Reviewer party's name, Delegate offers exactly those, and it is not offered when there is none.

## Bounded PDF inspection, file types by family, locked answers shown read-only, Fields only for parties who can answer them (2026-10-21)

- **PDF inspection.** A small PDF built to decompress into gigabytes could exhaust the heap and stop
  the application while it was inspected. PDF reading now decodes at most 16 MiB of stream content,
  at most 500,000 cross-reference entries in 10,000 sections, and at most 1,000,000 objects in object
  streams, and never decodes image data. A PDF past any limit counts as unreadable, like a damaged
  one. At most two PDFs are inspected at a time; others wait. The operator guide states the residual
  memory margins.
- **File types.** Office, CSV, JSON and XML uploads were always a content type mismatch, because
  their content alone detects as a container or as plain text. A text file is now judged as the more
  specific type its declared type or file name gives, and a current Office file as the format the
  parts inside it show, so a Template that accepts those types accepts them. A name never makes a file
  a type its content does not support.
- **Correction resubmissions.** Withdrawing a submission that answered a correction left neither it
  nor the corrected submission active and reopened the whole part. It is now refused with
  `INFORMATION_REQUEST_SUBMISSION_NOT_WITHDRAWABLE`, and the workspace offers no Withdraw on it.
- **Accepted facts.** Revoking a fact, and listing a request's promoted facts, were refused once the
  Exchange ended. The owner can again revoke a fact and read the list after the Exchange ends or the
  request is cancelled, superseded or expired, as documented; promotion still needs an active
  Exchange. The request page offers Revoke on those requests. A fact offered to the requesting side
  of a later request is now offered only to someone who may read its source item.
- **Answers that cannot change.** The response workspace showed submitted answers, and answers an
  open correction did not return, as editable, and one edit to them made every later save fail. Each
  response now carries `editLock` (`SUBMITTED`, `NOT_RETURNED_FOR_CORRECTION` or `GIVEN_ONCE`): its
  answer is shown read-only with the reason and is never sent. A change kept for an answer that
  becomes locked is dropped after the refusal and the other changes save. Choosing the stored answer
  again is no longer a change.
- **Readiness after files change.** Submit stayed unavailable after the last required file was
  uploaded, and could be offered after a withdrawal, until an unrelated save. Uploading, replacing
  and withdrawing a file now reloads the submission summary and the workspace.
- **Amended answers.** An answer an amendment asked to confirm could not be confirmed without
  changing it. **Keep this answer** sends its current answer under the current response ETag; a
  confirmation made against answers that changed elsewhere is refused as stale.
- **Document answers in review.** Review before submitting said "Files attached" from the
  disposition alone and "Not answered yet" for a document answered only by files, and section
  progress counted the same way. A Document response now carries `evidenceFileCount`, and both state
  how many files are attached.
- **Fields for outside parties.** A Field not marked Public cannot be answered by someone outside
  the request's owner, so such a Requirement had no usable editor and blocked submission for good.
  Giving a role that answers such a Field to a participant, a user outside the owning organization (or, on a personal
  request, anyone but its owner) or a group with such a member is refused with the
  new code `INFORMATION_REQUEST_FIELD_OUTSIDE_AUDIENCE`, naming the Requirement, at assignment,
  reassignment, issue and amendment. Publication refuses a Requirement that collects a read-only
  Field. The Template editor notes both under Collected Field and lists the read-only one before
  publishing.
- **Confirming as one party.** A caller who acts for two parties a confirmation names could neither
  confirm nor refuse. The preview's `attestingParties` lists them and the card asks **Confirm as**,
  sending the chosen `partyId`.
- **Condition graphs.** Saving a Template with a layered graph of conditions took exponential time
  under the draft lock. Loop detection is now linear, and a Version holds at most 500 Requirements
  and 500 condition rules, each rule at most 50 predicates.
- **Finished requests.** The owner of a closed, cancelled, superseded or expired request was shown as
  a non-manager: the Exchange tab opened the response page and the request page lost its parties and
  Template details. Manager standing now rests on reading operations, so the owner keeps the
  management page.
- **Nested entries.** Removing an entry inside another entry made removing or reordering the outer
  entry fail. Removed entries are no longer part of that check.
- **Hidden answers after an amendment.** An amendment that made a hidden Requirement unconditional,
  or moved it to a condition that holds, left its answer hidden. The answer now returns with its
  retained content; moved to a condition that does not hold, it stays hidden under that condition.
- **Recurring requests.** A series stopped once its latest request was cancelled or superseded. The
  next request now follows the latest one that can be followed, or the first.
- **Reminders.** A reminder sent from operations showed the literal `{{DUE_AT}}`. It now names the
  earliest due time of the request's running clocks, or uses wording without a due date.
- **Preservation holds.** The Records tab showed no hold for an open request, one in retention, or an
  organization with no schedule. It now lists every hold that covers the request.
- **Creating requests.** Template, Blueprint and one-off creation checked the Exchange state and the
  owner's entitlement before authorizing, so an outsider could learn them. Creation now authorizes
  first and answers `403`.
- **Contact codes.** An access link could open at most three sessions before it had to be resent. A
  successful verification now restores its three codes, so all 25 verifications can be used.
- **Blueprint participants.** Starting an Exchange from a Blueprint with participant defaults sent
  them in the wrong shape and failed, and saving a Blueprint with participants was refused. Defaults
  are now selected as the organization members they name, and saved back in the Blueprint's shape.
  A Blueprint whose text uses organization or personal variables asks for their values again; that step had failed.

## Submitting waits for unsaved work; conditions never clear another party's answers; finished requests issue no new access (2026-10-20)

- **Submitting.** Submit could be confirmed while an answer was still saving, had failed to save, or had
  changed elsewhere, and while a file was still being sent or was kept after a failed upload, so a
  submission could freeze an earlier answer. Opening the review now saves pending answers first, and
  Submit stays unavailable, with the reason beside it, while any answer or file is unresolved. The
  review reloads after a save, so it always shows the answers being submitted. In a request submitted
  in parts, the review lists only the selected part, a choice is stated by its label rather than its
  stored code, and two different valid Requirement keys such as `record--owner` and `record-owner`
  no longer share one input id.
- **Load failures.** Review results, amendments, carried-forward answers, reusable answers, access
  links, expected documents, request outcomes, party candidates and request sources showed an empty
  list when their read failed. Each now states why the read failed and offers Try again.
- **Conditions and other parties.** A condition that clears or archives hidden answers, where the
  answers it governs are given by a different role from the one answering what it reads, made the
  governed party's saves fail with no way forward. Saving and publishing a Template now refuse such
  a rule, naming it in the new refusal property `conditionRuleKey`. At runtime a condition never
  clears, archives or withdraws an answer the caller cannot respond to, or one a submission holds:
  it is kept hidden and returns when the condition holds again, and confirming its clearing is
  refused. Files a condition clears or archives are withdrawn through the evidence collection and
  recorded as evidence transitions. A patch or upload addressing a Requirement its condition hides
  is refused with the new code `INFORMATION_REQUEST_REQUIREMENT_HIDDEN_BY_CONDITION`. Publication
  checks a staged Template's cross-stage conditions again.
- **Access after a request ends.** Verification codes, sessions and step-ups were still issued for a
  cancelled, superseded or closed request and for one whose Exchange had ended. They are now refused
  with `INFORMATION_REQUEST_STATE_INVALID` or `INFORMATION_REQUEST_PARENT_STATE_INVALID`. Cancelling
  or superseding a request revokes its access links, recorded with the new reasons
  `REQUEST_CANCELLED` and `REQUEST_SUPERSEDED`, while sessions already open keep reading until they
  expire. Rejecting, rescinding or deleting the Exchange now revokes access links as well as sessions.
- **Template refusals.** A collected Field the Schema Version does not bind and evidence policy bounds
  that cannot hold were refused only by the database and answered `500`. They are now refused with
  `INFORMATION_REQUEST_TEMPLATE_INVALID` and the section and Requirement keys, at saving and at
  publication, and the editor lists the same evidence problems before saving. On every Information
  Request command, any other change a stored rule refuses answers
  `409 INFORMATION_REQUEST_CONSTRAINT_CONFLICT`, while other storage failures still answer `500`.
  The editor reads the Fields of the exact Schema Version a draft names through the new
  `GET /schemas/versions/{versionId}`. A copy's configuration event names its source Template,
  Version and version number.
- **Commands.** `If-Match: *` is refused with `428` on every Information Request command. Submitting
  and recording a confirmation include the `If-Match` value in the compared body, so a retry under the
  same `Idempotency-Key` with another value is refused. A caller without access is refused with `403`
  before any state, execution grant or submission lock check. A malware scan result is recorded under
  the request lock, so it cannot change what a submission in progress checked. Issuing, cancelling and
  superseding a request, party assignment, reassignment and revocation, and the cancellations made as
  an Exchange ends or is rejected or rescinded fail and are rolled back when their audit record
  cannot be captured. Subject exports count against, and are refused beyond, the owner's daily
  export ceiling.
- **Web app.** Review findings and returned corrections name an item by its prompt. When naming the
  author the Decision Maker fails at creation, the request opens with the reason and **Make me the
  Decision Maker**. Template Version and step-up times use the shared time format. The signed-in
  respondent page follows the user's theme; access-link pages keep the light theme. The help corrects
  the review page name, New Template at the open request limit, and how files are removed.
- No migration. New: `GET /schemas/versions/{versionId}`, the codes
  `INFORMATION_REQUEST_REQUIREMENT_HIDDEN_BY_CONDITION` and `INFORMATION_REQUEST_CONSTRAINT_CONFLICT`,
  the refusal property `conditionRuleKey`, and the cutoff reasons `REQUEST_CANCELLED` and
  `REQUEST_SUPERSEDED`.

## A single choice can be cleared; partial answers are stated in full before submitting (2026-10-20)

- A saved choice from a list could not be taken back to unanswered: the control offered no clear
  action, which mattered for an optional answer chosen by mistake and for conditions that depend on
  whether a Field is answered. It now has the same clear control as a Yes or No field, in Exchange
  Fields, when starting an Exchange, and in Information Request answers; clearing removes the stored
  value. Read-only Fields offer no clear, and a field with several choices is still cleared by
  deselecting them.
- Review before submitting showed a partly provided answer as its value alone, or as Files attached,
  without saying it was partly provided or why. It now reads, for example, Partly provided: Three
  records. Reason: Two records remain unavailable. A provided answer's note is shown the same way.
- One Field answered in several repeated entries gave every entry's input the same HTML id. Each
  input now takes its id from its own entry and item, and stays labelled by that entry's question.
- Web app only; no API change or migration.

## A provided answer takes an optional note (2026-10-20)

- A provided answer offered no box for the note the help describes. A reason given for an earlier
  answer, such as not applicable, also stayed on the answer after switching to provided, unseen by
  the respondent but shown to reviewers. A provided answer now has an optional **Note**, shown with
  the answer, which starts with any such reason so it can be kept, changed or cleared.
- A note on its own does not answer an item: an unanswered item stays unanswered until it has a
  value, files or another answer, so section progress and readiness are unchanged.
- Web app only; no API change or migration.

## Edited answers keep their choice; autosave follows the latest answers (2026-10-20)

- Changing only the explanation of a saved answer other than a value, such as not applicable or
  unavailable, or the value of a partly provided answer, saved the answer as provided while the
  workspace still showed the earlier choice. A Template whose only answer is an exception could not
  save it at all. The workspace and the saved change now use one rule for the answer shown: the
  answer chosen, otherwise the saved answer the Template permits, otherwise provided, otherwise the
  Template's first permitted answer. A value kept behind an answer other than a value is not sent.
- Choosing partly provided offered no box for the reason the save requires; the reason is now asked
  for beside the value. An item the party can only see offers no answer to give.
- After the answers changed somewhere else, entering an earlier saved change again showed **All changes saved.** and was
  never sent. A save is now matched to the answers it was made
  against, so that change waits two seconds and saves; a change already saved is still sent once
  while the latest answers load, also after adding, removing, or moving an entry.
- Web app only; no API change or migration.

## Archived conditional answers take their files with them; a changed Field no longer stops reconciliation (2026-10-19)

- When a Template archives the answers a condition hides, the files of a hidden Document answer
  stayed active. Once the condition held again, those files came back in the answer and could
  complete a submission without being provided again, although the archived answer is meant to
  return empty. A file-only answer was not archived at all. Archiving now records the exact version
  of each active file in the archive and withdraws the files from the answer, with the reason
  `archived-by-condition`, including an answer that has only files. The file versions and earlier
  submissions are unchanged. Retaining still returns hidden answers with their files, confirmed
  clearing still asks first, and an answer given once or another party's answer is still refused
  before anything changes.
- After an amendment changed the Field a Requirement collects, an imported value recorded for the
  earlier Field made every reconciliation of the request fail, `500` through the API, including
  for values that matched. Each imported value now records the Requirement revision it was recorded
  against. A value recorded while its Requirement collected another Field comes back as the new
  outcome `FIELD_CHANGED` and is never compared with an answer to the new Field; its type, value,
  provenance, and decision stay recorded. A value that no longer fits its Field comes back as
  `NOT_COMPARABLE`. Other values reconcile as before, and a retried reconciliation still returns its
  stored outcomes. The record export lists each imported value's `requirementRevisionId`.
- Migrations V178 (archived files) and V179 (imported value Requirement revisions; existing values
  are given the revision in effect when they were recorded).

## Reviewers open the submitted files they can read in a review (2026-10-19)

- A reviewer who was neither the party that provided a file nor someone who manages the request's
  evidence saw an item's answer in a review but was refused its submitted files, so **Preview** and **Download** failed
  for most reviewers. Anyone a review shows an item's content to can now open
  that item's submitted files through
  `GET .../submissions/{packageId}/evidence/{versionId}/content` and `.../preview`. The check is the
  one that shows the content: review reading on the request and on the item's Requirement, with its
  confidentiality and recent-verification rules, for a submission that has a review.
- The Requirement's own evidence routes, uploads, replacements, withdrawals, and removals are
  unchanged. A file a manager removed stays hidden from reviewers, and each opening is recorded.
- No migration.

## Submitted files stay readable; removed Requirements no longer stop reconciliation (2026-10-19)

- A file a submission froze could no longer be opened once that submission was withdrawn and the
  entry holding the file was removed: both preview and download read the file through the
  Requirement's current collection, which refuses a removed entry. A submitted file is now read
  through the submission that holds it, with
  `GET .../submissions/{packageId}/evidence/{versionId}/content` and `.../preview`. These open only
  a version that submission froze, for a caller who may view or manage the evidence of that item's
  Requirement, after the usual quarantine, malware-scan, digest, and access session checks, and also
  after an amendment removes the Requirement. The review workspace opens submitted files this way.
  The current collection's own routes still refuse a removed entry.
- Reconciling imported values failed with `404` once an amendment removed a Requirement that still
  had an imported value that was not rejected. A reconciliation now leaves such a value out while
  the request's current Version does not ask for its Requirement. The value, its source,
  provenance, decision, and earlier discrepancies stay recorded, the other values are compared as
  before, and a retried reconciliation still returns its stored outcomes.
- No migration.

## Linking an account keeps the respondent's evidence allowance (2026-10-18)

- The per-respondent evidence allowance counted only the files stored by the exact identity that
  uploaded them. A participant who linked an account therefore started again from an empty
  allowance and could store up to the full file and size allowance a second time on the same
  request. Uploads and replacements now count every stored version provided by the participant, the
  account it linked, and any other participant linked to that account, including withdrawn and
  removed files. A link made through an earlier request counts the same way.
- Each version still records the identity that uploaded it, a different respondent keeps its own
  allowance, and the request allowance and per-file limits are unchanged.
- No migration.

## Amended entries keep a place for Field answers; a first Field item needs a new request (2026-10-18)

- An entry an amendment added while the Version asked for no Field answers had no place to hold
  them. When a later Version asked for Field answers again, that entry could not save them and could
  not be removed while the minimum held it, so the request could not be completed. Each amendment
  now gives every active entry its Field value set whenever the request has a Schema, which also
  repairs entries added earlier.
- A request whose Version asked for no Field answers has no Schema, because a request's Schema can be
  set only while it is a Draft. An amendment that started asking for Field answers on such a request
  either left the new items impossible to answer or failed without a reason code. It is now refused
  with `INFORMATION_REQUEST_AMENDMENT_SCHEMA_CHANGED`, the same refusal as a change of Schema; use a
  supplemental or superseding request instead.
- No migration. A request already amended into the second state stays unable to save those Field
  answers; a supplemental request is the way forward for it.

## Amended group minimums are provisioned and enforced; enclosing group changes respect submitted stages (2026-10-17)

- An amendment that raised a repeatable group's minimum kept the request's existing entries without
  adding the missing ones, and submission readiness only checked the items that existed. A request
  could then close with fewer entries than its Version requires, including an empty package when a
  group became mandatory. Amending now adds the missing entries, with the minimums of the groups
  nested in them, under every active parent entry. Existing entries and their answers keep their
  identities, and a new entry never reuses a removed entry's path.
- Submission readiness now also checks each repeatable group in the submitted scope against its
  configured bounds, independently of the items that exist. The preview and the incomplete
  submission refusal list such shortfalls in `groupProblems`, and **Review and submit** names the
  section and how many entries it needs. A shortfall in a group the caller cannot add to is counted,
  not named.
- A change to an enclosing group's bounds now changes the meaning of every Requirement nested beneath
  it, as a change to the directly anchored group already did. Such an amendment is refused when one
  of those Requirements belongs to a submitted stage, and is still accepted when they are all in
  stages that remain open.
- No migration.

## Alternatives count only while they apply and stay in their document's stage; bundle clock checks match authoring (2026-10-17)

- A required Document Requirement whose only alternative was hidden by a false or unknown condition
  still showed as complete and could be submitted, although the hidden alternative's files were kept
  out of the package. A substitute now completes its primary only while the substitute is shown, so
  that request is incomplete until the alternative applies again or the primary has its own files.
  Retained alternative files that become shown again count with their exact versions.
- In a Template submitted in stages, a document could name an alternative from another stage. The
  earlier stage could then be completed by a file that its reviewed-content hash and frozen evidence
  did not identify. Saving, one-off creation, amendments, publication, and the Template editor's
  Before publishing list now refuse an alternative from another stage. Supporting-evidence links,
  which never complete an item, may still cross stages.
- Configuration bundle validation now applies the clock policy authoring rules: `0` minutes before
  escalation is valid, while overlapping working periods on one day and repeated reminder offsets are
  problems. Clock policy authoring now also refuses working periods on a calendar-time policy, as
  bundle validation already did.
- No migration. A stored draft that names an alternative from another stage is refused at
  publication and must be changed first.

## Every command keeps its own audit record when callers share an Idempotency-Key (2026-10-17)

- Two commands that used the same raw `Idempotency-Key` could both commit while only the first kept
  an audit record. This happened when two respondents saved answers, when two Decision Makers
  changed the completion gate, or when one manager defined a recurrence and then a refresh rule. The
  second command's receipt and transition were kept but its audit event was dropped as a duplicate.
- A request transition made inside a command is now audited under that command's receipt and the
  transition itself. Callers of any kind, operations, and requests that share a key each keep their
  own record, and the audit reconciliation finds a record for every such transition. A retry with
  the same key still replays the original result and adds no record. Transitions made outside a
  command (clocks, scheduled refreshes, cascades) keep their existing once-only audit keys.
- No API change and no migration.

## Connector work stops with its request, and one subject restriction at a time (2026-10-16)

- A queued connector exchange is no longer called, and a connector result is no longer recorded,
  once the request's execution grant is revoked, the grant does not allow responding, or its owner
  is operationally suspended. The background worker now applies the same continuation check as a
  directly recorded imported value, both before it calls the connector and again before it records
  the result, and ends the exchange as failed with `request_not_accepting_values`. Work issued before
  a commercial lapse still completes under its grant.
- Two restriction privacy requests for the same subject, sent at the same time with different
  `Idempotency-Key` values, no longer end one of them in an internal error. The second waits for the
  first, both are recorded and completed, and they share the one active restriction. Retries with the
  same key still replay the first request.
- No API change and no migration.

## Notices are not sent after their request finishes during delivery (2026-10-16)

- A reminder or overdue notice that was being delivered while its request was cancelled, expired,
  submitted, or superseded, or while its Exchange was closed, could still be sent and recorded as
  delivered after that change had committed. Delivery now holds share locks on the Exchange and the
  request while it checks the notice and sends it, so the change either commits first and the notice
  is recorded as skipped with its reason, or waits until the delivery is recorded.
- Rendering a notice checks its request under the same locks, and disposing of a request locks the
  Exchange and the request before it removes the request's notices, so a delivery and a disposal no
  longer interleave.
- Frozen notice content, retry backoff, reassignment handling, and once-only sequence values are
  unchanged. No API change and no migration.

## Subject privacy records capture each request at one moment (2026-10-16)

- An access or export privacy request now holds the requests it records, and their Exchanges, until
  the subject record is saved. Before, a request cancelled or submitted while the record was being
  made could be saved in its earlier state beside the later cancellation or submission in its
  history, and the saved, hashed record kept that mismatch.
- A cancellation, submission, or other command on one of those requests that arrives during the
  capture waits until the subject record is saved or refused, then goes ahead. The requests are
  locked in the same fixed order as a privacy deletion, so the two cannot deadlock.
- Subject records still do not count toward an owner's daily record export limit, and confidential
  items are still withheld from a caller who has not verified recently. No API change and no
  migration.

## Business-hours clocks and working hours across daylight saving and split days (2026-10-15)

- Pausing a business-hours clock records only the working time actually left, and resuming it
  straight away keeps the original due time. Before, on a daylight-saving change, working periods
  that the change moved onto the same time were counted twice: a clock paused at once could record
  90 minutes left of 60 and, on resume, become due a week later. Working periods that a
  daylight-saving change moves out of order are also walked in time order now.
- Each date's working time stays within that date, and periods that resolve to overlapping times
  count once, the same way for due times, pausing, and reminder points.
- The due date policy form lists every working period under **Working hours**, with its day, start,
  and end. A day can have several periods, days can have different hours, and **Until midnight** ends
  a period with its day. **Add working period** adds one on the following day with the same hours.
  Before, the form kept only the first period's hours for every working day, so publishing an
  unrelated change dropped a second period on a day or gave a shorter day the full hours, and a
  policy with a period ending at midnight could not be published again.
- No API change and no migration. Clocks already running keep the policy version they started
  with; a clock paused before this change keeps the time left it recorded.

## Values the server leaves unset no longer show as zero, "null" or 1970 (2026-10-15)

- The Workflow condition editor opens for templates whose Business Fields schema has no draft or
  no published version. Before, building the answer choices failed.
- A Workflow condition on an answer inside a repeated group anchors to that group's first entry,
  for example `lines[0]/items[0]`. Before, a group at the top level produced `null[0]/lines[0]/...`.
- Starting a new Template draft from a version whose document requirement has no file-count or
  file-size limit no longer reports "needs at least as many files allowed as it requires" or "needs
  a positive file size limit".
- Clock history shows **Time left when paused** only for a paused clock. Retention schedules
  without automatic disposal say "never disposed automatically" instead of "disposed null days".
  File sizes that are not known are left out instead of "null B" or "0 bytes". A submitted
  answer that holds only a note shows the note under **Corrections**.
- The web client now types every Information Request response value that the server may send as
  `null` accordingly, and a new check, `npm run typecheck:null-safety`, type-checks Information
  Request code with null checks on. No server or API change, and no migration.

## Publishing a due date policy keeps escalation off (2026-10-15)

- Publishing a new version of a due date policy without escalation keeps escalation off. Before,
  the form filled **Escalate after** with 0 for such a policy, so changing any other field and
  publishing made clocks that use the new version escalate at the due time.
- The policy list no longer says "Escalated 0 hours after due" for a policy that never escalates.
  A policy set to escalate after 0 hours still escalates at the due time and says so.
- No server or API change, and no migration. Clocks already running keep the version they
  started with.

## Unset times stay unset in reusable answers and the queue export (2026-10-15)

- An earlier accepted answer offered for reuse without an end date says **No end date** again.
  Before, it said it was valid until 1 January 1970, and when only one of its validity end and
  reuse cutoff was set, that 1970 date hid the real one.
- **Export CSV** on the operations queue leaves the Due cell empty for a request with no running
  clock, as the queue does. Before, it wrote `1970-01-01T00:00:00.000Z`.
- The server sends an optional time that is not set as an explicit `null`. The web client's
  Information Request types now say so, and the shared time formatting, the queue export, reusable
  answers and access-link expiry read `null` as not set while still reading `0` as a real time. No
  server or API change, and no migration.

## Review history, link expiry and the queue export read the server's times (2026-10-15)

- A review with two or more decisions opens again. Its decision history ordered decisions by
  comparing their times as text, but the server sends every response time as a number of
  milliseconds since the Unix epoch, so the review page failed to show once a second decision was
  recorded. Decisions are now listed from the earliest.
- A party's access link whose expiry time has passed shows as expired, with only **Revoke link**
  offered, even while the server still lists it as active. Before, the party row could not read the
  expiry and called the link active.
- **Export CSV** on the operations queue writes its Due and Created columns as UTC times in
  ISO-8601 form, for example `2026-09-25T08:00:00.000Z`. Before, they held the raw millisecond
  numbers.
- The web client now reads every Information Request response time as such a number; a reminder
  result's `cooldownUntil` stays ISO-8601 text. No server or API change, and no migration.

## Dated commands are read by the server, and the preview shows what a party is asked (2026-10-15)

- Recording a business decision, assigning a reviewer with a due time, and promoting an accepted
  fact with a validity or expiry time work again from the web app. The server reads these times as
  milliseconds since the Unix epoch, but the forms sent them as ISO-8601 text, so every business
  decision and every dated assignment or promotion was refused as an unreadable body (`400 REQUEST_BODY_INVALID`). The
  forms now send the same local time as a number, an empty
  optional time is still left out, and the typed client's explicit clock `receivedAt` is a number
  too. A recurrence's first due time keeps its ISO-8601 text. No server or API change.
- **Preview as recipient** now shows the questions one answering party is asked, chosen under **Preview for**. Before,
  it showed the author's own response view, which is empty for an owner or
  Decision Maker who answers nothing, so the preview often listed no questions. The new
  `GET /information-requests/{id}/parties/{partyId}/recipient-preview` read, for people who manage
  the request's parties, applies the same party rule as response reads to the request's Template
  Version: a Requirement that is not disclosed never appears, condition rules are kept only where
  they refer to that party's own questions, and a protected question says it is shown once the
  party confirms it is them again. Ordinary response reads are unchanged. No migration.

## A refused file says why, and a file no longer shown is not opened late (2026-10-14, evening)

- A file preview or download that the server refuses now shows the server's reason, and one
  refused because the access-link session expired or was revoked returns the respondent to
  verification, as every other refused read already did. Before, a file read asked for its answer
  as a file even when the answer was a refusal, so the refusal's JSON body arrived unread: the page
  said only "The file could not be opened.", kept the ended session's credential, and left the
  workspace open. The web client now reads the body of a refused file request before it handles
  the refusal and uses it when it is a JSON object of at most 64 KiB, whatever type the response
  declares (the server labels a refused `content` or `preview` read with the route's `*/*` type,
  not `application/json`). Its reason code therefore also reaches the signed-in step-up prompt and
  the sign-out of a deprovisioned account. A file that is sent, and a refusal body that is not
  such an object, are passed on as they arrived, and the page then falls back to its own message.
  No server or API change.
- On the respondent page, a file preview or download that is still being read when the evidence
  panel no longer shows that exact file is no longer opened. This covers a reread of the
  Requirement's files that is refused, for example once verification lapses, and a reread that
  shows a newer version of the file or another file in its place. The page now checks, once the
  file has been read and before it creates a link to it, that the panel is still shown for the same
  request and Requirement and still lists that file with that version as its latest. A late file
  still opens when the reread shows it, including after Try again shows it again. The server's
  release checks are unchanged. No migration.

## Late files and records stay with the view that asked, and the export ceiling holds (2026-10-14, afternoon)

- On the review page, a submitted file's preview or download that is still being read when its item
  stops being shown is no longer opened. Before, a reviewer who selected Preview or Download and
  then left the review, or whose review could no longer be read on a reread, still had the file
  opened in a new tab or saved once it arrived. The page now checks, once the file has been read and
  before it creates a link to it, that the item content which asked for it is still shown for the
  same request and item and still lists that exact file version, so a reread that hides the item's
  content discards it too. Ordinary openings, and those that answer after a reread showing the same
  file, open as before. The server's release checks are unchanged.
- A request record download on a request's Records tab, or a subject record download on the Privacy
  tab, that is still being read when its panel closes is no longer saved. This covers opening
  another request, leaving the Records or Privacy tab, switching workspace, and a reread of the
  privacy records that is refused. A request record download also checks that its panel still shows
  the request it was started for. The hash check, the verification asked for confidential content,
  and the audit of each export read are unchanged. No server or API change for these two repairs.
- The daily ceiling on request record exports (`app.information-request.export.daily-ceiling`) now
  holds when one owner's exports are created at the same moment on different Exchanges. Each
  creation takes a transaction-scoped advisory lock for its owner (the organization, or the user for
  personal requests) before it counts the owner's exports of the last day, and holds it until it
  commits or rolls back, so the next creation counts the export just made. Before, two creations
  under separate Exchanges held only their own request locks, both counted the same free place, and
  both were made. Exports of different owners do not wait for each other. A creation refused or
  rolled back after counting takes no place, a repeated `Idempotency-Key` still returns its recorded
  export without counting or waiting, an export older than a day still stops counting, and subject
  record exports remain outside the ceiling. No migration.

## A file still being read is not opened for an earlier respondent (2026-10-14)

- On the respondent page, a file preview or download that is still being read when its evidence
  panel closes is no longer opened. Before, a preview or download started by one respondent whose
  file arrived after the workspace had found another party or account responding in the tab, or
  after the page was left, still opened the earlier respondent's file in a new tab or saved it,
  under the current respondent's view. The page now checks, once the file has been read and before
  it creates a link to it, that the panel which asked for it is still shown for the same request
  and Requirement. Ordinary previews and downloads, and those that answer after a refresh names the
  same respondent, open as before. The server's release checks are unchanged. No server or API
  change and no migration.

## The whole respondent workspace belongs to its respondent (2026-10-13, late night)

- When a respondent workspace that is already open finds, on a reread, that another party or
  account is now responding (its `respondentKey` changed while the request, and possibly the
  response ETag, stayed the same), everything shown for the earlier respondent now closes, not
  only the answer form. This covers earlier answers under From your previous submission, earlier
  accepted answers, What changed in this request, Review results, the Review and submit summary
  with its permission to submit, a submission result, open dialogs, and a file kept to send again.
  The workspace then reads each of them again for the current respondent, and a read or command
  answer still arriving for the earlier respondent is no longer shown. Before, a refresh under
  another signed-in account cleared the answer form but kept the earlier respondent's private
  earlier answers and their submission permission on screen, and a late read could add them after
  the change.
- A file kept to send again after an interrupted upload now belongs to the respondent who chose
  it, like unsaved answers: another respondent in the same tab neither sees nor retries it, and it
  is shown again when its respondent returns. Same-respondent recovery is unchanged: verifying
  again keeps unsaved answers, kept files, and the keys of interrupted commands. No server or API
  change and no migration.

## Unsaved answers belong to their respondent, and interrupted commands keep their key (2026-10-13, night)

- Unsaved answers in a browser tab are kept for the respondent who entered them, not only for the
  request. The response workspace now returns `respondentKey`, an opaque key for the respondent it
  was read for, which stays the same through a renewed session or a newer link for the same
  recipient and differs for a reassigned recipient or another account. Another party's link or
  another account opened in the same tab no longer shows those answers or saves them under its own
  credentials; they stay in the tab for the person who entered them. Linking a participant to an
  account moves the participant's unsaved answers to that account, and the account-link answer
  carries the account's `respondentKey`. A save names the respondent whose answers it carries, and
  one the server finds belongs to someone else is refused with
  `409 INFORMATION_REQUEST_RESPONDENT_CHANGED` and saves nothing. Unsaved answers kept under the
  earlier, request-only browser key are no longer read.
- A save, adding, removing, or reordering an entry, a submission, a confirmation, and withdrawing
  a submission or a file now keep their `Idempotency-Key` until they are answered, also through a
  newly verified session of the same respondent. A retry after a lost answer therefore returns the
  recorded result instead of a stale refusal, and an interrupted confirmation is recorded once
  rather than twice. A changed command, or the same one sent again after it was answered, still gets
  a new key. No migration is added.

## Files opened as a session ends, and work from a closed respondent page (2026-10-13, evening)

- A file downloaded or previewed through an access link is no longer sent when its session ended
  while the file was being read from storage. The session was checked, with its current
  verification, before the file was read but not after, so a session revoked or expired, recipient
  sign-in required, a revoked link, a party passed to another recipient, or a protected question
  whose verification window closed during the read still released the whole file and recorded it as
  opened. The session, link, and party are now checked again, and the file is authorized again with
  the session's current verification, after it has been read and checked and before its opening is
  recorded or any of it is sent; a refusal closes the file and records nothing. Unprotected files
  stay available for the session's lifetime, and signed-in downloads are authorized again the same
  way.
- On the respondent page, work that finishes after its page has closed no longer changes the page
  you are on. A contact-code verification, or a read reporting that its session ended, that answers
  after you left the page, for example after opening the same request's link again in the tab, no
  longer replaces or clears the session verified since, and an account link answered after its page
  closed no longer clears the stored credentials or moves you to your signed-in workspace. Late
  results on an open page keep their earlier rules, and unsaved answers, kept files, and a pending
  account link stay as they were. No migration is added.

## Lapsed verification, reassigned notice recipients, linked reviewers, and late session results (2026-10-13, afternoon)

- A protected item can no longer be answered or shown through an access-link session whose recent
  verification lapsed after the request arrived. The session check inside each change's transaction
  now returns the session's current verification and the change authorizes with it, so a protected
  answer, file, attestation, or submission sent after the 15-minute window is refused while other
  changes still succeed for the session's lifetime. A page or file read through an access link whose
  window closes while it is being read is read again with the current verification, and a file is
  released and recorded as opened only under that verification.
- A rendered notice is no longer delivered to a party's former holder. Delivery now checks that the
  party still names the notice's frozen recipient, under a share lock on the party, and otherwise
  records a skipped attempt with `RECIPIENT_REASSIGNED`; this also applies to a retry after a failed
  attempt. The notice keeps its original recipient and address and is never sent to the new holder.
- A review stage that keeps responders apart now also excludes the account a recorded participant
  linked, even after that participant's party was reassigned to someone else. Assignments,
  decisions, and overrides are refused for that account. Recorded attribution is unchanged and the
  former contributor gets no response access.
- On the respondent page, a contact-code verification or an ended-session report that arrives late
  from an earlier access link or an earlier session of the same request no longer replaces or clears
  the session verified most recently, including its stored credential. Unsaved answers, kept
  uploads, and a pending account link stay as they were. No migration is added.

## Ended access sessions, workspace switches, and refused rereads (2026-10-13)

- A change sent through an access link is now saved only while its session is still current. The
  session was checked before the change started, so a session revoked, or ended by requiring
  recipient sign-in, while a save was on its way could still record that save. Every change sent
  through an access link (answers, repeated entries, files, attestations, submissions and their
  withdrawal, review comments, appeals, and reused answers) now checks its session, link, and party
  again inside the transaction that records it, after locking the request, and is refused with the
  same reason as an ended session. A page or file read through an access link is checked again before
  it is answered, and no first view or file access is recorded for a session that has ended. A change
  recorded before its session ended is still returned when it is sent again under the same key after
  verifying again. Signed-in callers and account linking are unchanged.
- Information Request operations now belongs to the workspace you are signed in to. Switching to
  another organization or to your personal workspace no longer leaves the earlier queue, learned
  names, selection, drafts, or tab content on screen: the page waits for the new workspace and reads
  it again, a late answer for the earlier workspace is ignored, and a CSV export still being made for
  it stops without saving a file. A request's operations page and Record preservation read their
  records again the same way.
- A Requirement's files, the privacy records, a request's due dates, and the due date policies no
  longer stay on screen after a later read of them is refused, for example after your verification
  lapses or your access changes. They and their actions are hidden, the page says why, and Try again
  reads them again without showing the earlier records while it does; an earlier read that answers
  late no longer replaces a newer one. A file kept for Retry stays and can be sent once the files are
  shown again, and a clock change that failed in transit is retried under its original key. No
  migration is added.

## Interrupted account links after the page asks to verify again (2026-10-12, 22:00)

- An account link whose answer was lost can now be finished after the page has asked you to
  verify again. Linking ends the access session, so a summary or other read that arrived after the
  link sent the page back to verification and removed Link to my account, together with the
  attempt it would have repeated and the session token that attempt needs. The page now keeps that
  attempt, with the token it was sent with, until it is answered: Link to my account stays above
  the verification panel and resends exactly that command, so a link already made opens the
  request in your account.
- The ended session's token is used for nothing else. It is removed from the tab as before, the
  request's content stays hidden, and no new link is started without a verified session. If the
  repeat shows that the link was never made, Link to my account disappears and you verify again.
  Verifying again starts a new attempt for the new session, and an attempt is offered only to the
  account that sent it. The server is unchanged. No migration is added.

## Account linking, failed file writes, command identity, and the submission summary (2026-10-12, late night)

- Link to my account works again. The server checked the access session outside the command's
  transaction and failed every valid link with an internal error; it now checks the session
  token, and locks the parent Exchange and session, inside the account link command itself.
- An interrupted account link can be selected again. Linking ends the access session, but
  repeating the same command with the same session token and signed-in account returns the link
  already made, and the page resends that attempt without reading the parties again. The ended
  session still cannot start anything else, and another account cannot use the same command.
- A file stored for a document version whose recording then failed, at any step or at commit, is
  now removed: each write first records a pending cleanup in its own transaction, a committed
  version discharges it, and a rolled-back one deletes its bytes at once. A cleanup left behind,
  for example after a crash or a failed delete, is retried by a scheduled worker with backoff and
  never removes bytes a committed version refers to. This covers evidence uploads and
  replacements and Exchange document versions. Migration V177 adds the cleanup table.
- A repeated command is now compared field by field. Before, two commands whose free-text fields
  differed only in where a separator character fell, such as a decision's reason and external
  references or an uploaded file's issuer and jurisdiction, could replay the first command instead
  of being refused as a different command. A subject named under one command key is told apart
  the same way. Ordinary replays and conflicts behave as before. A command recorded before this
  release and repeated afterwards under its old key is refused as a different command; send it
  under a new key.
- Review and submit no longer keeps an earlier summary after a later read is refused. It shows
  nothing from before, says why, and offers Try again, including when the first read is refused;
  an ended access session sends the page back to verification. The part chosen in Part to submit
  stays chosen while answers save, an earlier part's late answer no longer replaces the chosen
  part, and Submit and the confirmations wait until the chosen part has loaded.

## Assigned to after a refused queue read (2026-10-12, night)

- After the operations queue refuses a read, Assigned to no longer offers the names it learned from
  earlier rows. It offers only Anyone and Me until a read of the queue succeeds, including while
  the next read is still loading, and then offers the learned names again. A party chosen before
  the refusal stays chosen and shows as Chosen party until then.
- Without a refusal nothing changes: the learned names stay available while a page or a filter
  change loads, and a chosen party keeps its name. The server is unchanged. No migration is added.

## Operations lists after a refused read (2026-10-12, evening)

- When a read is refused, for example after your access changes, the operations queue and audit
  search no longer keep the earlier results on screen beside the message. Their rows, Open and
  selection controls, found count, and page buttons are withheld. Searching again, or any other
  read of the same list, shows the loading state rather than the earlier results, and only a read
  that succeeds shows a list again. A request's clocks, notices, audit history, retention standing
  and record exports, and the record preservation lists and retention schedule behave the same
  way: after a refused read, the retention schedule cannot be published until it is read again.
- Selected requests stay selected, as they do across pages. The server is unchanged: it still
  refuses the reads and commands of a caller without access. No migration is added.

## Request operations pages opened for another request (2026-10-12, later)

- A request's operations page now starts afresh when another request is opened, including with
  the browser's Back button. It opens on the Clocks tab and the first page of the audit history.
  The earlier request's retention standing, record exports, execution standing, and audit page are
  no longer shown while the other request loads. A hold dialog opened for the earlier request
  closes with its draft, so its reason can no longer be placed as a hold on the other request. A
  hold or record export already sent for the earlier request is still processed by the server, but
  its answer no longer changes the other request's page.
- Lists that change what they show no longer keep the earlier results on screen while the new ones
  load: the operations queue after a filter changes, audit search after a new search, and the
  preservation hold list after Include released holds is switched. Moving to the next or previous
  page keeps the current page, and its page buttons, until the next page arrives.
- The server is unchanged: request reads, hold placement, and export creation keep their
  authorization and checks. No migration is added.

## Record exports, appeals, and reused answers sent again (2026-10-12)

- Create export keeps the identity of an unanswered attempt. Selecting it again after an interrupted
  attempt sends the same request, so one export is made and counted toward the owner's daily export
  limit. After an export was made, selecting Create export again makes a separate export.
- Send appeal keeps the identity of an unanswered appeal for the same review, reason, and review
  revision. Sending it again after an interrupted attempt returns the appeal already recorded and
  shows it, instead of being refused because the review can no longer be reopened. A changed reason
  is a separate appeal.
- Use this answer keeps the identity of an unanswered confirmation for the same request, earlier
  answer, Requirement, and response revision. Selecting it again after an interrupted attempt
  returns the answer already saved instead of reporting that the response changed. Another earlier
  answer, another Requirement, or a later response revision is a separate confirmation.
- The server's rules are unchanged: a replay still checks the caller's current permission for that
  command (export, read, or answering the Requirement), and the latest-review rule for appeals,
  explicit assent, and the daily export limit still apply. No migration is added.

## Refused reads, supplements, several review stages, and privacy deletion (2026-10-11, later)

- The respondent page now closes a request when a later read of it is refused, for example after
  your access is removed or the Exchange starts requiring recipients to sign in. Earlier answers and
  actions no longer stay on screen. Answers you have not saved stay in the browser tab, and Try
  again opens the request once you can. An access-link session that has ended still returns to
  contact verification.
- Create supplement keeps the identity of an unanswered attempt. Selecting it again after an
  interrupted attempt sends the same request, so one supplement is created. Changing the reason, or
  asking again after a supplement was created, sends a new request.
- A reviewer assigned to more than one stage of a review can now work in each open stage. Stage you
  are deciding chooses the stage; each stage keeps its own unsaved choices and worksheet revision,
  and a finding counts toward the stage it was recorded in. After one stage is recorded, the next
  open stage is shown. A stage the reviewer already decided through another assignment, one not yet
  open, and one that excludes earlier reviewers after the reviewer decided an earlier stage are not
  offered. Before this, the page showed no worksheet once the first assignment was decided.
- A privacy deletion and a reminder batch no longer deadlock when their requests share Exchanges in
  a different order. Privacy deletion now locks its requests' Exchanges in the same fixed order as
  reminders, before any request. No migration is added.

## Reviewer worksheets, access links, and bulk reminders (2026-10-11)

- A review worksheet keeps the newest saved choices, whatever order the page's requests finish in.
  A reload that started before a save no longer brings back the older worksheet, and a save that
  answers after a newer worksheet was loaded no longer replaces it. If the worksheet changed
  elsewhere while your save was on its way, the review says so and shows the latest saved choices,
  and Record decisions stops so you can check them. A choice changed while a save was on its way
  stays unsaved.
- An access link is shown only while you can still open its request. If your access ends, an open
  link closes, and a link created or resent just as your access ended is not shown.
- Sending reminders for several requests at once no longer fails when another batch names requests
  on the same Exchanges in a different order: every batch now locks its Exchanges in one fixed order
  before its requests.
- The reviewer view and a worksheet save now return `draftRevision` beside `draftETag`. It grows
  with every save of that worksheet. No migration is added.

## Finished reviews, respondent pages, and file disposal (2026-10-10)

- A finished request's reviews stay readable. Its reviewers, and the people who manage it, can open
  them after the final decisions complete the request and after the Exchange ends, with the same
  item limits and recent verification as before. Recording the final decisions no longer leaves
  the reviewer on a refused, out-of-date page. Retrying those decisions with the same key returns
  what was recorded. Nothing new can be recorded on a finished request.
- The people who manage a request can list its delegated authorities after it finishes; granting
  and revoking still end with the request.
- The respondent page shows only the request its address names. Moving to another request hides
  the previous one at once, a refused request no longer leaves the previous one on screen, and a
  slow earlier load can no longer replace the current request. Answers and files are sent only to
  the request on screen.
- If an access-link session ends while a file is being sent, replaced, withdrawn, opened, or
  listed, the page returns to contact verification straight away. A file that was being sent is
  kept and offered with Retry after you verify again; a retry sends it once. Submitting,
  confirming, withdrawing a submission, verifying again for protected questions, and linking an
  account also return to contact verification when they find the session ended, instead of only
  showing a message.

### For operators

- `ECSTaskRole` now grants `s3:ListBucketVersions` on the documents bucket and
  `s3:DeleteObjectVersion` on its objects, which disposal needs to remove every version and delete
  marker from the versioned bucket. Deploy the stack before relying on disposal of stored files;
  until then, privacy deletion and scheduled disposal of requests with files stop at `AccessDenied`.
  A backend contract test now fails if the template stops granting an S3 action that version
  storage performs. No migration is added.

## Decisions on access, audiences, and record administration (2026-10-10)

- A file belongs to the party that provided it. Another party on the same Requirement can add its own
  files but no longer replace, withdraw, or remove one that a different party provided; whoever acts
  for the providing party still can, and people who manage the request's evidence can still remove
  any file. The workspace shows such a file without those actions.
- Business decisions are now read only by the people who manage the request. Respondents,
  reviewers, and other parties no longer receive them.
- The reason a reconsideration or appeal gave is shown, as Reason given, to the person who gave it
  and to reviewers who may read every item the review covers; an appeal's reason is also shown to
  respondents who may view every item. A reconsideration's reason is never shown to respondents.
- Adding a person by email on an Exchange that requires sign-in names someone who already has access
  to the Exchange; any other address is refused the same way, whether or not it has an account. An
  email is no longer checked against other organizations' members: only the address of a trusted
  recipient already on the Exchange must be chosen from the trusted recipients.
- Naming a Subject by a reference, or choosing a known one, needs privacy management for the
  request's owner. Without it, a new Subject is named by its kind.
- A participant who linked an account signs in from then on: no new access link can be created,
  resent, or replaced for them, and an older link no longer opens the request.
- Changing a hold's scope, releasing a hold, and publishing a retention version apply only to the
  version that was read; a change made by someone else in the meantime is reported and the latest
  version is shown. A retried change racing its original no longer fails.
- Reviewers see how many protected items wait for verification and can select Verify again; saving
  or recording a decision about a protected item asks for verification instead of refusing it.
- Retrying the creation of a follow-up request returns the request it followed, and a recurring
  series reports its next due time correctly after its second occurrence.

## Holds on the records of requests that share an export (2026-10-10)

- A subject record export that covers several requests is no longer disposed of with one of them
  while another request it covers keeps a held record: a hold on that request's submission, file,
  file version, sent notice, or record export now refuses the disposal, as a hold on the request
  itself does. Privacy deletion follows the same rule, so it is refused and nothing about the
  subject is deleted until the hold is released.
- While a disposal runs, a hold can no longer be placed on any record of the request being disposed
  of or of the other requests its shared exports cover, including a record added after the disposal
  began.

## Disposal references, conditions, parties, and Template audits (2026-10-09)

- A request is no longer disposed of while another request holds an answer confirmed from one of its
  facts; the confirmed answer keeps the fact, its source, and its evidence until the later request is
  disposed of. A fact whose request is being disposed of is no longer offered for reuse.
- A subject record export that covers several requests is disposed of with the first of them, and
  only once every other request it covers is finished, past its minimum retention, and not held.
  Until then the disposal is refused, and while it runs those requests cannot be placed on hold.
- A record that gains a new reference while its disposal is being claimed is now kept: the claim
  waits for the reference, and a reference started after the claim is refused.
- Saving or publishing a Template, or creating a one-off request, refuses a condition whose compared
  value cannot be read as its Field's type, has the wrong shape for its test, names an option the
  Field does not offer, or treats the Field as a type other than its Schema Version's. Publishing
  checks stored conditions again.
- A Schema Version another owner holds is refused as another owner's without describing its state,
  target, or key.
- A delegate must exist and pass the same owner and sharing checks as a party, and a participant
  named as a party must exist and belong to the request's owner.
- A person, participant, or group holds one active party in each role. Giving the same role to the
  same holder again, by assignment or reassignment, is refused before any capacity is used.
- Template changes are refused and rolled back when their required audit record cannot be written.
  Audit records of configuring, publishing, retiring, and starting a Version name that Version, its
  number, and its revision.

## Answers given once (2026-10-09)

- A confirmation given once keeps its decision after its submission is withdrawn: it can be given
  again for changed content only with the same decision.
- Changing another answer can no longer clear or archive an answer given once through a condition;
  the save is refused and names the answer. Hiding it while keeping it is still possible. An entry
  holding a submitted answer given once can no longer be removed after a withdrawal.
- An item never submitted while shown was never answered, so it can still be answered once it
  applies, even when its Template takes it only once.
- A correction reopens an answer given once only when the reviewer returns that answer. A later
  submission that held it hidden no longer reopens it, changing the returned answer cannot clear it
  through a condition, and a confirmation given once keeps its decision unless it is returned.

## Protected items, party Shares, and record integrity (2026-10-08)

- A subject record produced by an access or export privacy request is downloaded from that privacy
  request with **Download record**. A request's own record exports list a subject record only for
  someone who may export every request it covers.
- Imported values, discrepancies, accepted facts, review decisions, findings, and comments about a
  protected item need recent verification; without it they are left out of lists, and changing them
  asks you to verify again. A reconciliation compares a value with an answer only for someone who
  may see that answer; anyone else learns only whether the value expired or cannot be compared.
- Each party now has its own Share: revoking one of a person's parties no longer removes the access
  another of their parties gives. One person decides a review stage once, whichever party they act
  through, and is not assigned to a stage twice.
- Files of a hidden conditional document are no longer frozen into a submission. Under confirmed
  clearing they are listed with the answers to clear and withdrawn once you confirm.
- An answer given once can change until it is first submitted and stays fixed afterwards, even when
  that submission is withdrawn.
- A caller without access to a request is refused before their `If-Match` is compared, and retries
  of party, delegated authority, and file commands need the same authority as the command did. A
  retried file command no longer shows a file a manager has since removed.
- Creating the next occurrence of a recurring request needs authority over its latest occurrence as
  well as its first.
- A Blueprint's default participants must be permitted by the sharing policy, or the request is not
  created. A follow-up leaves out parties the policy no longer permits and records them, and it uses
  its source's Template Version or a later published Version of the same Template.
- A request's Records tab shows a hold's reason, case reference, and author only to people who can
  read the owner's holds. A hold must name one of the owner's own records of a kind that can be
  held. A record export withholds review decisions, findings, and confirmations about items the
  exporter may not see.
- A settled review stage takes no new reviewer. A retest names an earlier finding about the same
  Requirement. An amendment that changes only the review plan is refused while a submission stands
  and is recorded in the audit history.
- A waived answer is promoted as declared rather than reviewed. A fact replaces a current fact from
  another request only for someone who could revoke it there, and is not promoted when the request's
  subject changed after submission. A fact can now be revoked after its request finishes or its
  Exchange ends.
- Queued reminders and notices are no longer sent to a removed party, for a finished request, or for
  an Exchange that was rejected, rescinded, or deleted; they are marked undeliverable with the reason.
- Retrying a Template publication, review assignment change, or fact revocation with the same key
  and different details is refused. `If-Match: *` no longer satisfies party, review, or Template draft
  commands. Concurrent retries of a response save, occurrence change, or recertification wait for the
  first and replay it.
- Once a request's execution grant is revoked, imported values, generated outputs, fact promotion,
  business decisions, and starting or resuming a clock are refused; revoking a fact, pausing or
  extending a clock, and changing the completion gate (when the grant permits cancelling) stay
  possible.
- Access link responses that carry a token are no longer cached, and an evidence route for an
  unknown request answers not found.

## Earlier answers, review notes, and follow-up retries (2026-10-08)

- In a follow-up, **From your previous submission** lists an earlier answer only while you can still
  see its question on the earlier request. An unassigned question, a removed Share or group
  membership, or a protected question you have not verified recently keeps the earlier answer
  hidden; the follow-up's question can still be answered.
- A review shows the findings, comments, decision reasons, worksheet notes, and returned file names
  of an item only to someone who may review that item's content. People who manage reviews without
  reviewing an item, and reviewers who have not verified recently for a protected item, see where
  the item stands without what was written about it. Saving a worksheet answers only the entries you
  may still review.
- Retrying a follow-up, recurring request, or expiry refresh with the same key is refused once you
  can no longer view the request it created, and it never creates another one.

## Follow-up schedules, refresh retries, and delegated groups (2026-10-07)

- A recurrence repeats every 1 to 1,000 days, weeks, months, or years, from a first due time between
  1970 and 9999, and a set number of requests is at least one. Refresh rules ask again zero or more
  days before expiry. The page offers **Schedule** only within these bounds, and the API refuses
  other values with `INFORMATION_REQUEST_FOLLOW_UP_SCHEDULE_INVALID` instead of failing.
- A requested document takes one refresh rule; adding a second one is refused.
- An expiry refresh that cannot be created yet, for example because the owner's plan no longer
  allows new requests or the Exchange has ended, is tried again later, waiting longer each time up
  to a day. Other refreshes are no longer held up behind it.
- Scheduling a recurrence or adding a refresh rule now changes the request's version, and the
  response carries the new `ETag` for the next change. Retrying such a change after losing access to
  the request is refused.
- When delegated authority is given to a group, its active members, and accounts linked to a
  delegated participant, can answer, upload files, and confirm for the assigned party, provided they
  have access to the request.

## Subject identity policy (2026-10-07)

- Subject identifies who or what an Information Request is about. Answers and confirmations use
  Contributor, Preparer, or Attestor; Subject is no longer offered for these duties.
- Saving configuration and publishing a draft refuse Subject as a Requirement contributor or
  confirmation role. The same checks apply to one-off requests and private amendments.
- A stored draft that names Subject asks you to choose an acting role and identifies the affected
  Requirement before publishing.

## Gap remediation (2026-10-05)

### For people who use Information Requests

- A request created from a Blueprint lists the Blueprint's documents on its page under **Expected documents**, as they
  were when the request was created.
- When a save would clear answers that no longer apply, the response workspace lists each one,
  including answers whose condition can no longer be decided. Confirm them and save again. If
  clearing them hides further answers, those are added to the list.
- A change that would clear another party's answers is refused instead of clearing them.
- Removing or reassigning a party now also ends the access of an account its participant linked
  from an access link, unless that account holds another party on the request.
- When an evidence policy asks for details such as the issuer, dates, or the period covered,
  choosing a file opens **File details**, and each file shows the details it stated.
- A part returned for correction can be submitted again from **Review and submit**.
- Expiry refreshes are created automatically when a submitted document nears the expiry date it
  states, and only once for each submission.
- The request page lists its **Reviews**, including one still waiting for its first reviewer.
- Requiring recipients to sign in now ends every access link and session on the Exchange's
  requests.
- Protected questions (those in a confidentiality compartment) now work for access links: they stay
  open for 15 minutes after verifying, and the workspace offers **Send verification code** to verify
  again. Signed-in parties select **Verify again**.
- Older Office files and ZIP-based files are now checked for password protection and whether they
  can be read, as PDF files already were. A file over the upload limit is refused before it is
  stored, with a message to choose a smaller file.
- When parts are submitted in order, a later submitted part must be withdrawn before an earlier
  one.
- Cancelling or superseding a request asks for a reason from a short list, recorded in the request
  history.
- When a save or a change to repeated entries is refused, the message names each affected answer
  and says how many others cannot be shown to you.
- An answer returned for correction must be changed before the part can be submitted again.
- A Template can archive answers a condition hides: the answer is kept in the request's record but
  not restored if the condition holds again. Keeping and clearing answers work as before.
- At the plan's limit of open Information Requests, the Information Requests pages say how many are
  open; Templates can still be created and edited.
- Ending an Exchange whose ending waits for a Workflow keeps your choice to cancel its open requests
  until the Workflow confirms the ending.
- A personally owned request now starts your own Workflows for its events.
- The Exchange Details tab shows schema controls only to people who may use them, offers **Remove schema**, and the
  schema editor keeps each question's section, visibility, and default
  value.
- The operations queue, the request page, and the review queue show when a request continues after
  the owner's plan lapsed, is suspended, or lost its execution grant. Reviewers and respondents see
  only that the request continues or is suspended, not why.
- Issuing a request is refused while one of its parties is bound to a trusted relationship that is
  suspended or can no longer be used.
- When roles confirm in order, **Confirm** waits until every earlier role has confirmed and the card
  names the roles it is waiting for; refusing stays available.
- The Template editor drops a question's condition when the question stops being asked only when
  the condition is true, and reports a group that holds no question.
- PNG, JPEG, and GIF files are decoded to check they can be read; an image larger than 100 million
  pixels counts as unreadable.
- A request's Audit history says who acted in plain words: a person, an access-link respondent, an
  application, a Workflow, or the system.

### For operators

- `GET /information-requests/{id}/document-placeholders` returns a request's frozen placeholders.
- A refused hidden-answer clear on `PATCH .../responses` is a `409` that names each answer in
  `hiddenResponseClears`. `INFORMATION_REQUEST_HIDDEN_RESPONSE_CLEAR_NOT_PERMITTED` refuses a clear
  the caller cannot make. See `api-reference.md`.
- Delegated authority changes, access-link commands, automatic link and session cutoffs, contact
  proof, session issuance, and participant account links are recorded in request history with
  classified audit and events, and so is access session step-up. The audit catalog is at version 35.
- A participant account upgrade is refused with
  `INFORMATION_REQUEST_PARTICIPANT_ACCOUNT_ACCESS_CONFLICT` while the account already has access to
  the request.
- `GET .../submission-preview` reports `correctionOpen`. Access links, contact proof, and access
  sessions are refused with `INFORMATION_REQUEST_RECIPIENT_SIGN_IN_REQUIRED` while the Exchange
  requires recipient sign-in, and turning that on records each access link it ends.
- `POST /no-auth/information-request-access-links/sessions/current/step-up-challenges` and
  `.../step-ups` renew an access session's verification. The response workspace reports
  `requirementsAwaitingStepUp`. `app.information-request.access-session.step-up-window` (default
  `PT15M`) sets how long a verification counts.
- Template creation, publication, and retirement store the actor's canonical principal kind and
  ID, including private Templates created or amended by applications and service accounts.
- Requests, parties, and access links also store their creator or assigner as a canonical principal
  kind and ID, so a request created or a party assigned by an application or service account keeps
  that actor. Record exports (schema version 3) state the request creator and each party's assigner
  as `...PrincipalKind` and `...PrincipalId` instead of `createdByAppUserId`.
- Access links are only for parties identified by a contact address. Issuing, rotating, replacing,
  and revoking a link advances the party's revision and returns the party ETag; links record their
  verification strength, last use, and revocation time.
- Request headers show owner and Template identifiers only to people who manage the request, and
  request ETags name their family (`aggregate:`, `parties:`, `responses:`) so one family's validator
  never satisfies another's.
- A trusted recipient of the parent Exchange can be chosen as a party (`GET .../trusted-recipient-candidates`); a person
  or group holding a trusted recipient binding is
  always bound to it, and a verified member of a Trusted Organization cannot be added by email.
- Privacy deletion claims every finished request at once and reports open ones as kept (`SKIPPED_OPEN`); privacy
  requests, restriction lifts, preservation holds, and retention schedule
  publications take an `Idempotency-Key`; record exports (schema version 4) add item corrections and
  withhold confidential Requirements from callers without recent verification.
- Submitting and recording a confirmation need the exact submission ETag; `If-Match: *` is
  refused with `428`. A confirmation also covers notes and each file's inspection and malware
  assessment. Evidence uploads declared larger than the file limit answer `413`
  `INFORMATION_REQUEST_EVIDENCE_UPLOAD_LIMIT_EXCEEDED` before the body is read; a received file over
  a limit answers `409` with the same reason. Evidence versions no longer report
  external-reference fields; every version is an uploaded file.
- Ending, rejecting, rescinding, or deleting an Exchange gives each Exchange owner without a Share on
  a request a read-only `RECORD_OWNER` request Share, for organization-owned and personal requests
  alike. `RECORD_OWNER` is not a party role; assigning it to a party is `400`.
- `GET /information-request-capabilities` adds `quotas` and the reason `OPEN_REQUEST_LIMIT_REACHED`.
  Cancellation, supersession, and superseding follow-up reasons are machine codes
  (`INFORMATION_REQUEST_REASON_CODE_INVALID` otherwise). Clock policy definition and version
  publication take an `Idempotency-Key`; reconciliations are stored and replayed by key. Refused
  response and occurrence changes about specific items return the item refusal body, and the
  submission preview reports `STRUCTURED_VALIDATION_FAILED`. See `api-reference.md`.
- `GET /exchanges/{id}/schema` always answers `200` with `assignment`, `canManageSchema`, and
  `canEditValues`; `PUT /exchanges/{id}/schema` accepts `If-None-Match: *` and refuses a second
  assignment with `FIELDS_SCHEMA_ALREADY_ASSIGNED`. Every profile's CORS configuration now allows
  the `If-None-Match` request header.
- An access link and its session open only the request the link was issued for; a route naming
  another request answers `404 INFORMATION_REQUEST_NOT_FOUND` and does not count as a session use.
  A request the service cannot find now answers `404` on the signed-in request, response, and
  repeated-entry routes too (previously `409`). Request bodies that are missing on the recurrence,
  refresh rule, and successor routes answer `400`.
- Each issued request's execution grant now decides which continuation actions it allows and
  whether typed Field answers follow the grant or the owner's live plan; a change the grant does not
  list is `409 INFORMATION_REQUEST_CONTINUATION_NOT_GRANTED`. Grants issued today list every action.
- Request headers, operations queue rows, and review queue entries report `executionStanding`
  (`kind`, and for people who manage the request its `reason`). The submission preview reports
  `waitingForRoles` for each confirmation.
- Every request audit record names its actor, label, and session explicitly; access-link
  respondents appear as `PUBLIC_LINK` with a `participant:<id>` label and their access session,
  and system work as the platform service account. Each request transition records the
  Idempotency-Key and command receipt of the command that made it.
- Migrations V153 to V172 (see `migration-notes.md`). V167 removes Field values held
  outside their assignment's Schema Version. V170 drops the domain event outbox's unread
  organization column and requires every access session to hold a credential and an expiry, deleting
  sessions that never did. V172 closes the open revisions of requirements removed before V168.

### Defects fixed

- A save refused for unconfirmed hidden-answer clears never said which answers to confirm, and
  answers whose condition could no longer be decided could not be confirmed in the workspace.
- A confirmed clear could empty another party's response without checking that the caller could
  answer it.
- Verifying a contact code locked its access link without first locking the parent Exchange and
  request, unlike every other access-link command.
- Parties could be added to an organization's request even when its sharing settings would refuse
  them on an Exchange, including groups that were inactive, unpublished, or did not exist.
- An account linked to a participant kept its access to the request after the party was removed
  or reassigned.
- Required evidence details could not be given in the workspace, so a policy requiring them could
  never be met.
- A part returned for correction could not be confirmed or submitted again from the workspace.
- A new review that had no reviewer could not be reached from the request page.
- A retried group occurrence command skipped the access and state checks of the original and could
  answer with occurrences the caller could not see.
- Access links and sessions kept working after an Exchange was set to require recipient sign-in.
- A hold placed directly on a request's submission, file, notice, or export did not stop the
  request from being disposed of.
- Expiry refresh rules never acted on their lead time, and the manual refresh could be repeated
  for the same submission.
- A party answering through an access link could never see or answer a protected question.
- Private Template creation and publication discarded attribution for an actor without an App User
  identity. Template creator and publisher provenance now remains frozen through retirement.
- Submitting or confirming with `If-Match: *` skipped the check that the content was the content
  reviewed, and a confirmation stayed valid after a note or a file's checks changed.
- A Template could make a question in one part depend on an answer in another part, which could
  change after the first part was submitted; publication now refuses it.
- Reading a stored file from object storage left a temporary copy behind, and each storage call
  opened a new storage client that was never closed.
- A file inside a ZIP-based or older Office container was never checked for password protection
  or damage.
- Once a request had closed, its managers could not request more information, set up a recurrence,
  or create its next occurrence or refresh: each follow-up was refused as if the record were
  archived, although the request's lifecycle allows follow-ups from a closed request.
- Due date clocks kept running after their request finished, and a stopped clock could report the
  wrong standing.
- After the execution grant was revoked, the owner could not cancel or supersede the request.
- Ending an Exchange cancelled its requests before a Workflow that gates the ending had confirmed it.
- A follow-up kept only the latest of several submitted parts as its source.
- A misconfigured Workflow could be partly started by a request event; the whole start is now
  refused and recorded as skipped.
- Answers to hidden questions still counted toward other conditions, and a condition rule could
  govern a question that was not conditional.
- A draft could not clear a required Field, and changes to repeated entries were not checked against
  every question inside them.
- Review stages were shown to participants, and a save answered with answers the caller had not
  changed.
- The schema editor wiped each question's section, visibility, and default value; a Workflow could
  read answers left behind by an earlier Schema Version; two people choosing a schema at once could
  get a server error; and default values were written without an audit record.
- The submission preview offered a confirmation that the server then refused because an earlier
  role had not confirmed yet.
- A damaged image was accepted as readable evidence.
- A requirement removed by an amendment, or with its repeated entry, before V168 kept an open
  revision.
- Each no-auth route compared the requested id with the link's request on its own, and a call
  naming another request still counted as a use of the session.
- A missing request body on the recurrence, refresh rule, and successor routes failed with a server
  error.
- Answers, submissions, and files from access-link respondents were audited without a session or a
  label, and owner changes relied on the sign-in context for theirs; request transitions never
  recorded the command that made them.

## Phase 13: Exchanges started from Blueprints (2026-10-01)

### For people who use Information Requests

- Starting an Exchange from a Blueprint that names a Template Version now creates that Information
  Request as a draft, with you as its Decision Maker. Before, the Exchange's Information Requests tab
  stayed empty.
- Such an Exchange can start without documents, because the request collects them. This includes a
  locked Blueprint with no document slots. The documents tab says so.
- Every other Exchange still needs at least one document ("Add at least one document").
- Blueprint cards in the picker show **Starts an Information Request**. After the start, the summary
  names the request and offers **Manage Information Request**.
- If the request cannot be created, the Exchange does not start, and the dialog shows why.

### For operators

- `POST /exchanges` accepts `blueprintDefinitionId`. See `api-reference.md` for the new `409`
  refusals, including `INFORMATION_REQUEST_TEMPLATE_VERSION_OWNER_MISMATCH`.
- Migration V152 (see `migration-notes.md`).

### Defects fixed

- After one request had been created from a Blueprint with document slots, every later save of that
  Blueprint failed with a server error.
- A request created from a Blueprint was refused with `403` whenever a Blueprint default value
  belonged to a field of the request's Schema.
- A refused Exchange start always showed "An unknown error occurred while starting the Exchange",
  never the server's reason.

## Phase 12: completion and compatibility (2026-09-30)

### For people who use Information Requests

- No plan change hides a request. Everything recorded stays readable, and permitted exports stay
  available, after a trial ends, a plan lapses, the feature is removed, an account is suspended, or
  a request is stopped. Each request shows its standing: Continuing as issued, Read only, Changes
  paused, or Stopped. Only the people who manage it see the owner's reason.
- Settings shows Information Requests when your plan includes them, when your active organization
  does, or when an organization sponsors your personal Templates; otherwise the tab is hidden. If you
  see it without being able to create requests, it says you can still respond to and review requests
  shared with you, which everyone can. (Corrected on 2026-10-10: this note first said the tab was
  shown to every signed-in user, which a later change on 2026-09-30 replaced.)
- Plans now state their Information Request allowances in Billing and on the pricing page:
    - Personal: 25 open requests, 10 acting parties, 100 evidence files and 250 MiB per request, and
      5 GiB of evidence across requests.
    - Business: open requests are not capped; 100 acting parties, 200 files and 500 MiB per request,
      and 100 GiB of evidence across requests.
    - An issued request keeps the allowances it was issued with.
- Personal owners can create and issue requests on their own Exchanges. A platform Template is a
  starting point: copy it into My Templates or the organization, then create requests from the copy.
- Access links created without their own limits work for 30 days and for 25 verifications, and the
  party list shows when each active link expires.
- A request reminded in the last 24 hours is skipped, and the operations page says when it can be
  reminded again.
- A party whose trusted relationship is suspended is marked, keeps answering what was issued, and
  cannot be newly assigned.
- Choosing or removing an Exchange Schema now states the version it read, so a removal cannot
  discard answers changed in the meantime.

### For operators

- New endpoints:
    - `GET /information-request-capabilities`.
    - `GET /platform/information-request-health`, for platform administrators and audited.
- New alarms on the existing log group: `InformationRequestHealthBreachAlarm` and
  `InformationRequestAbuseRefusalAlarm`.
- New configuration keys for no-auth rate limits, access link defaults, the reminder cooldown, the
  daily export ceiling, and the health windows (see `operator-guide.md`).
- Migrations V149 and V150 (see `migration-notes.md`).
- An ended trial is now refused as `TRIAL_ENDED`, no longer as a suspension.

### Defects fixed

- Issuing a request with any acting party failed with a server error, and so did assigning a party
  after issuance. A newly written grant or reservation was locked before it had been flushed to the
  database.
- Revoking a subject party after issuance failed.
- Trust refusals during party assignment answered `500`. They now answer `409` with a stable code.
- Platform Template Versions could not be read while enforcement evaluated decisions. The owner
  API could create a platform Template while enforcement was off.
- Reads of an issued request were refused under suspension or revocation, and the Exchange listing
  dropped a draft whose owner had lost the feature.
- The Exchange's Information Requests tab disappeared whenever its list failed to load. It now
  stays and says so, and hides only when the caller is refused (`INFORMATION_REQUEST_FORBIDDEN`).

### Known limitations

- The Exchange's Information Requests tab authorizes each request separately and costs about 60 ms
  per request on one Exchange. A separate task tracks batching those decisions.
- External sources have an API but no screen yet.
- Personal Fields and Schemas cannot be authored yet.
