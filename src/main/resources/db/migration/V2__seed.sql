-- V2: Seed data.
--
-- The application-scope workflow definition + notification rules the engine relies on
-- out of the box. Carried over verbatim from the pre-squash V9/V10 migrations (fixed
-- UUIDs + `on conflict do nothing`, so re-runs are no-ops).
--
-- V1 is a pg_dump that sets `search_path` to '' for the session; reset it here so the
-- unqualified table names below resolve.
SET search_path TO public;

------------------------------------------------------------------------------
-- Workflow: session-approval-in-group (APP scope, matches all orgs).
--
-- Single APPROVAL step assigned to MANAGERs of the recipient group; SLA 24h,
-- escalates to ORG_ADMIN of the subject's owning org. onApprove -> session.activated,
-- onReject -> session.rejected. Placeholders ($subject.*) are resolved at instance
-- start against workflow_instance.subject_data_json (see WorkflowAssigneeResolver.kt).
------------------------------------------------------------------------------
insert into workflow_definition (
    id, name, version, scope, organization_id, trigger_event, steps_json,
    description, is_active, created_at
) values (
    '00000000-0000-0000-0000-000000000001',
    'session-approval-in-group',
    1,
    'APP',
    null,
    'session.approval_requested',
    $$
    {
      "steps": [
        {
          "type": "APPROVAL",
          "assignees": [
            {
              "kind": "GROUP_ROLE",
              "groupIdRef": "$subject.recipientGroupId",
              "groupRole": "MANAGER"
            }
          ],
          "quorum": { "kind": "ANY" },
          "slaMinutes": 1440,
          "escalation": {
            "afterSlaBreach": "ESCALATE",
            "escalateTo": [
              {
                "kind": "ROLE",
                "roleName": "ORG_ADMIN",
                "scopeType": "ORG",
                "scopeIdRef": "$subject.orgId"
              }
            ]
          },
          "onApprove": { "nextStep": "END", "emit": "session.activated" },
          "onReject":  { "nextStep": "END", "emit": "session.rejected"  }
        }
      ]
    }
    $$,
    'Approval by MANAGERs of the recipient group before a SharingSession is activated.',
    true,
    now()
)
on conflict (name, version) do nothing;

------------------------------------------------------------------------------
-- App-level notification rules for the events the engine emits.
------------------------------------------------------------------------------

-- workflow.step_assigned -> notify the assignee(s) of the new step.
insert into notification_rule (id, scope, organization_id, event_pattern, predicate_json, assignees_json, action, priority, is_active, created_at)
values (
    '00000000-0000-0000-0000-000000000101',
    'APP',
    null,
    'workflow.step_assigned',
    null,
    $$
    [
      { "kind": "EVENT_PAYLOAD", "field": "assignees" }
    ]
    $$,
    'NOTIFY',
    100,
    true,
    now()
)
on conflict do nothing;

-- workflow.escalated -> notify the new escalation targets.
insert into notification_rule (id, scope, organization_id, event_pattern, predicate_json, assignees_json, action, priority, is_active, created_at)
values (
    '00000000-0000-0000-0000-000000000102',
    'APP',
    null,
    'workflow.escalated',
    null,
    $$
    [
      { "kind": "EVENT_PAYLOAD", "field": "assignees" }
    ]
    $$,
    'NOTIFY',
    100,
    true,
    now()
)
on conflict do nothing;

-- session.activated -> notify the session initiator.
insert into notification_rule (id, scope, organization_id, event_pattern, predicate_json, assignees_json, action, priority, is_active, created_at)
values (
    '00000000-0000-0000-0000-000000000103',
    'APP',
    null,
    'session.activated',
    null,
    $$
    [
      { "kind": "EVENT_PAYLOAD", "field": "initiator" }
    ]
    $$,
    'NOTIFY',
    100,
    true,
    now()
)
on conflict do nothing;

-- session.rejected -> notify the session initiator.
insert into notification_rule (id, scope, organization_id, event_pattern, predicate_json, assignees_json, action, priority, is_active, created_at)
values (
    '00000000-0000-0000-0000-000000000104',
    'APP',
    null,
    'session.rejected',
    null,
    $$
    [
      { "kind": "EVENT_PAYLOAD", "field": "initiator" }
    ]
    $$,
    'NOTIFY',
    100,
    true,
    now()
)
on conflict do nothing;
