# Information Requests release notes

## Phase 12: completion and compatibility (2026-09-30)

### For people who use Information Requests

- No plan change hides a request. Everything recorded stays readable, and permitted exports stay
  available, after a trial ends, a plan lapses, the feature is removed, an account is suspended, or
  a request is stopped. Each request shows its standing: Continuing as issued, Read only, Changes
  paused, or Stopped. Only the people who manage it see the owner's reason.
- Settings shows Information Requests to every signed-in user. Someone without the feature is told
  they can still respond to and review requests shared with them.
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
