import React from "react";

export type HelpDocArticle = {
    id: string;
    sectionId: string;
    sectionTitle: string;
    title: string;
    content: React.ReactNode;
};

type HelpDocSectionInput = {
    id: string;
    title: string;
    articles: Array<{
        id: string;
        title: string;
        content: React.ReactNode;
    }>;
};

const helpDocSections: HelpDocSectionInput[] = [
    {
        id: "start-here",
        title: "Start here",
        articles: [
            {
                id: "help-starter-guide",
                title: "Help starter guide",
                content: (
                    <>
                        <p>
                            Welcome to DocuHyphen Help. If you are not sure where to begin,
                            use this recommended reading order.
                        </p>

                        <h3>Recommended reading order</h3>
                        <ol>
                            <li>
                                <a href="#"
                                   data-help-article="idp-setup-guide"><b>IdP setup guide</b></a> - configure sign-in and organization access.
                            </li>
                            <li>
                                <a href="#"
                                   data-help-article="role-permission-matrix"><b>Role & Permission Matrix</b></a> - assign least-privilege roles.
                            </li>
                            <li>
                                <a href="#"
                                   data-help-article="exchange-lifecycle"><b>Exchange Lifecycle</b></a> - standardize the end-to-end Exchange flow.
                            </li>
                            <li>
                                <a href="#"
                                   data-help-article="workflow-overview"><b>Workflow overview</b></a> - automate and gate lifecycle transitions with configurable workflows.
                            </li>
                        </ol>

                        <h3>Who should read what</h3>
                        <ul>
                            <li><b>Organization Admin:</b> Start with IdP and Role & Permission Matrix, then read the full Workflows section.</li>
                            <li><b>Operations/Managers:</b> Start with Exchange Lifecycle and Workflow overview.</li>
                            <li><b>New team members:</b> Read all three core articles once for shared context.</li>
                        </ul>

                        <h3>Workflow quick links</h3>
                        <ul>
                            <li>
                                <a href="#" data-help-article="building-a-workflow"><b>Building a workflow</b></a> - create your first workflow definition step by step.
                            </li>
                            <li>
                                <a href="#" data-help-article="step-types"><b>Step types explained</b></a> - APPROVAL, NOTIFICATION, CONDITION, and ACTION.
                            </li>
                            <li>
                                <a href="#" data-help-article="platform-templates"><b>Platform templates</b></a> - clone ready-made workflows into your organization.
                            </li>
                            <li>
                                <a href="#" data-help-article="workflow-activity-monitoring"><b>Workflow activity and monitoring</b></a> - track running instances and review audit timelines.
                            </li>
                        </ul>
                    </>
                ),
            },
        ],
    },
    {
        id: "identity",
        title: "Identity & access",
        articles: [
            {
                id: "idp-setup-guide",
                title: "IdP setup guide",
                content: (
                    <>
                        <p>
                            Configure Microsoft Entra ID or Google as your identity provider
                            for single sign-on and organization-level access control.
                        </p>

                        <h3>Before you begin</h3>
                        <ul>
                            <li>Confirm you have admin access to your identity provider.</li>
                            <li>Collect redirect URIs for each deployment environment.</li>
                            <li>Decide which email domains are allowed for your organization.</li>
                            <li>Define who owns rollout, testing, and change approval.</li>
                        </ul>

                        <h3>Microsoft (Entra ID)</h3>
                        <ol>
                            <li>Create or select an Entra ID app registration.</li>
                            <li>Add the DocuHyphen redirect URI(s) as web redirects.</li>
                            <li>Capture the client ID and tenant ID.</li>
                            <li>Create a client secret and store it in your secret manager.</li>
                            <li>
                                Include claims for email, given name, family name, and subject
                                or object ID.
                            </li>
                            <li>Grant scopes: openid, profile, and email.</li>
                            <li>Save values in DocuHyphen settings and run a sign-in test.</li>
                        </ol>

                        <h3>Google</h3>
                        <ol>
                            <li>Create or select a Google Cloud project.</li>
                            <li>Configure the OAuth consent screen for your org.</li>
                            <li>Create web OAuth credentials and set redirect URI(s).</li>
                            <li>Capture the client ID and client secret.</li>
                            <li>Grant scopes: openid, profile, and email.</li>
                            <li>Configure domain restrictions if required.</li>
                            <li>Save configuration in DocuHyphen and run a test login.</li>
                        </ol>

                        <h3>Troubleshooting checklist</h3>
                        <ul>
                            <li>Redirect URI mismatch between provider and DocuHyphen.</li>
                            <li>Missing consent for required scopes.</li>
                            <li>Invalid tenant or project configuration.</li>
                            <li>User email domain blocked by policy.</li>
                        </ul>
                    </>
                ),
            },
        ],
    },
    {
        id: "exchanges",
        title: "Exchanges",
        articles: [
            {
                id: "exchange-lifecycle",
                title: "Exchange Lifecycle",
                content: (
                    <>
                        <p>
                            This guide explains the typical Exchange flow from creation to closure,
                            including validation points that keep teams aligned.
                        </p>

                        <h3>When to use this guide</h3>
                        <ul>
                            <li>Onboard a new operations or compliance team member.</li>
                            <li>Standardize how Exchanges are created and closed.</li>
                            <li>Troubleshoot status transition confusion.</li>
                        </ul>

                        <h3>Exchange statuses</h3>
                        <ul>
                            <li><b>Draft:</b> Exchange is created and prepared by the initiator.</li>
                            <li><b>Active:</b> Recipients can interact with the shared documents.</li>
                            <li><b>Completed/Ended:</b> Exchange is closed and no further changes should occur.</li>
                            <li><b>Rejected:</b> A recipient declined the Exchange, or an approval workflow rejected it.</li>
                        </ul>

                        <h3>Standard lifecycle steps</h3>
                        <ol>
                            <li>Create an Exchange with a clear title and intended recipients.</li>
                            <li>Upload required documents and verify file quality.</li>
                            <li>Send the Exchange and monitor recipient acceptance or rejection.</li>
                            <li>Address comments and updates while the Exchange is Active.</li>
                            <li>End the Exchange once the business outcome is achieved.</li>
                            <li>Review audit history for compliance and record retention.</li>
                        </ol>

                        <h3>Validation checklist</h3>
                        <ul>
                            <li>Correct recipients and permissions before the Exchange goes Active.</li>
                            <li>No sensitive files shared to unintended users.</li>
                            <li>Closure reasons are documented for completed Exchanges.</li>
                        </ul>
                    </>
                ),
            },
        ],
    },
    {
        id: "admin-operations",
        title: "Admin operations",
        articles: [
            {
                id: "role-permission-matrix",
                title: "Role & Permission Matrix",
                content: (
                    <>
                        <p>
                            Use this matrix as the baseline for assigning least-privilege access
                            across your organization.
                        </p>

                        <h3>Role overview</h3>
                        <ul>
                            <li><b>Organization Admin:</b> Full org-level configuration and access governance.</li>
                            <li><b>Manager:</b> Exchange-level operational control within assigned scope.</li>
                            <li><b>Member:</b> Participant access to assigned Exchanges and documents.</li>
                        </ul>

                        <h3>Permission matrix (high level)</h3>
                        <ul>
                            <li><b>Manage organization settings:</b> Admin only.</li>
                            <li><b>Manage users and roles:</b> Admin only.</li>
                            <li><b>Create and manage Exchanges:</b> Admin and Manager.</li>
                            <li><b>Upload/edit Exchange documents:</b> Admin and Manager (or delegated Member where allowed).</li>
                            <li><b>Close/end Exchanges:</b> Admin and Manager.</li>
                            <li><b>View broad audit records:</b> Admin (Manager may see scoped Exchange history).</li>
                        </ul>

                        <h3>Assignment best practices</h3>
                        <ol>
                            <li>Assign the minimum role required for each person.</li>
                            <li>Use time-bound elevated access for temporary admin work.</li>
                            <li>Review role assignments on a recurring cadence.</li>
                            <li>Revoke unused or stale access promptly during offboarding.</li>
                        </ol>

                        <h3>Common pitfalls</h3>
                        <ul>
                            <li>Granting admin rights for routine Exchange tasks.</li>
                            <li>Forgetting to remove inherited access after team changes.</li>
                            <li>Assuming Exchange access implies org-level permissions.</li>
                        </ul>
                    </>
                ),
            },
        ],
    },
    {
        id: "workflows",
        title: "Workflows",
        articles: [
            {
                id: "workflow-overview",
                title: "Workflow overview",
                content: (
                    <>
                        <p>
                            Workflows let your organization gate every stage of an Exchange lifecycle
                            with configurable steps: approvals, notifications, conditions, and
                            automated actions. Instead of hard-coded status transitions, each stage
                            can require human sign-off or automated logic before the Exchange
                            advances.
                        </p>

                        <h3>How workflows fit into the Exchange lifecycle</h3>
                        <p>
                            Every Exchange moves through four statuses: <b>Draft</b>,{" "}
                            <b>Active</b>, <b>Completed</b>, and <b>Rejected</b>. Workflows sit
                            at the transitions between these statuses and gate them:
                        </p>
                        <ul>
                            <li>
                                <b>Draft submitted</b> - fires when an Exchange is first created.
                                Use this to run internal pre-send approvals before recipients see
                                anything.
                            </li>
                            <li>
                                <b>Acceptance pending</b> - fires after the draft stage when
                                recipient acceptance is required (the org-level "require recipient acceptance" setting is on). The Exchange stays in Draft until
                                the workflow completes.
                            </li>
                            <li>
                                <b>Activated</b> - fires when the Exchange transitions to Active.
                                Use this for post-activation tasks such as notifications or
                                automatic actions.
                            </li>
                            <li>
                                <b>Ending</b> - fires when a user tries to close the Exchange.
                                Use this for completion checklists or sign-off requirements before
                                the Exchange is marked Completed.
                            </li>
                        </ul>

                        <h3>Core concepts</h3>
                        <ul>
                            <li>
                                <b>Workflow definition</b> - a reusable, versioned template that
                                describes which steps to run and in what order. Org admins create
                                and manage definitions in Settings.
                            </li>
                            <li>
                                <b>Workflow instance</b> - one live execution of a definition
                                against a specific Exchange. Created automatically when the matching
                                trigger event fires.
                            </li>
                            <li>
                                <b>Step instance</b> - one step within a running instance.
                                Tracks assignees, decisions, SLA deadlines, and status.
                            </li>
                        </ul>

                        <h3>Who can manage workflows</h3>
                        <p>
                            Only Organization Admins can create, edit, activate, deactivate, clone,
                            or delete workflow definitions. The Workflows tab in Settings is only
                            visible to users with the admin role.
                        </p>

                        <h3>Learn more</h3>
                        <ul>
                            <li>
                                <a href="#" data-help-article="trigger-events">
                                    <b>Trigger events</b>
                                </a>{" "}
                                - what events are available and when they fire.
                            </li>
                            <li>
                                <a href="#" data-help-article="building-a-workflow">
                                    <b>Building a workflow</b>
                                </a>{" "}
                                - step-by-step guide to creating your first workflow.
                            </li>
                            <li>
                                <a href="#" data-help-article="step-types">
                                    <b>Step types explained</b>
                                </a>{" "}
                                - APPROVAL, NOTIFICATION, CONDITION, and ACTION steps.
                            </li>
                            <li>
                                <a href="#" data-help-article="platform-templates">
                                    <b>Platform templates</b>
                                </a>{" "}
                                - ready-made workflows you can clone into your organization.
                            </li>
                        </ul>
                    </>
                ),
            },
            {
                id: "trigger-events",
                title: "Trigger events",
                content: (
                    <>
                        <p>
                            A trigger event is the signal that starts a workflow instance. Each
                            workflow definition is bound to exactly one trigger. When that event
                            fires on an Exchange, the engine looks for an active definition that
                            matches, creates an instance, and begins executing its steps.
                        </p>

                        <h3>Available trigger events</h3>

                        <h3>exchange.draft_submitted</h3>
                        <p>
                            Fires immediately when a new Exchange is created. Use this trigger for
                            internal pre-send approvals that must complete before any recipient is
                            notified.
                        </p>
                        <p><b>Available subject fields:</b></p>
                        <ul>
                            <li><b>initiatorId</b> - the user who created the Exchange.</li>
                            <li><b>orgId</b> - the organization the initiator belongs to.</li>
                        </ul>

                        <h3>exchange.acceptance_pending</h3>
                        <p>
                            Fires after the draft stage when recipient acceptance is required (the
                            org-level "require recipient acceptance" setting is on). The Exchange
                            stays in Draft until this workflow completes. Use this for recipient
                            approval workflows.
                        </p>
                        <p><b>Available subject fields:</b></p>
                        <ul>
                            <li><b>recipientId</b> - the primary recipient user ID.</li>
                            <li>
                                <b>recipientGroupId</b> - the recipient group ID (GROUP recipient
                                type only).
                            </li>
                            <li><b>initiatorId</b> - the initiator user ID.</li>
                            <li><b>orgId</b> - the initiator organization ID.</li>
                            <li>
                                <b>recipientType</b> - one of: <code>EMAIL</code>,{" "}
                                <code>APP_USER</code>, or <code>GROUP</code>.
                            </li>
                        </ul>

                        <h3>exchange.activated</h3>
                        <p>
                            Fires when an Exchange transitions to Active (ACCEPTED_STARTED). Use
                            this for post-activation notifications, automatic actions, or secondary
                            approvals that must happen once the Exchange becomes Active.
                        </p>
                        <p><b>Available subject fields:</b></p>
                        <ul>
                            <li><b>initiatorId</b> - the initiator user ID.</li>
                            <li><b>orgId</b> - the initiator organization ID.</li>
                        </ul>

                        <h3>exchange.ending</h3>
                        <p>
                            Fires when a user requests to close an Exchange. The Exchange is held
                            in its current state until this workflow completes or is rejected. Use
                            this for completion checklists, mandatory sign-off steps, or data
                            archival actions before the Exchange is permanently marked Completed.
                        </p>
                        <p><b>Available subject fields:</b></p>
                        <ul>
                            <li><b>initiatorId</b> - the initiator user ID.</li>
                            <li><b>orgId</b> - the initiator organization ID.</li>
                        </ul>

                        <h3>session.approval_requested (legacy)</h3>
                        <p>
                            The original group-session approval gate used before the workflow
                            redesign. It is preserved for backward compatibility with existing
                            workflow definitions that reference it. New workflows should use{" "}
                            <code>exchange.acceptance_pending</code> instead.
                        </p>
                        <p><b>Available subject fields:</b></p>
                        <ul>
                            <li><b>recipientGroupId</b> - the recipient group ID.</li>
                            <li><b>initiatorId</b> - the initiator user ID.</li>
                            <li><b>orgId</b> - the initiator organization ID.</li>
                        </ul>

                        <h3>Tip: subject field placeholders</h3>
                        <p>
                            When configuring assignees, you can reference subject fields using the
                            syntax <code>$subject.fieldName</code> (for example,{" "}
                            <code>$subject.orgId</code>). This keeps the definition portable across
                            organizations instead of hardcoding specific user or group IDs.
                        </p>

                        <h3>Learn more</h3>
                        <ul>
                            <li>
                                <a href="#" data-help-article="assignees-and-portability">
                                    <b>Assignees and portability</b>
                                </a>{" "}
                                - how to use $subject.* placeholders safely.
                            </li>
                            <li>
                                <a href="#" data-help-article="workflow-org-settings">
                                    <b>Organization workflow settings</b>
                                </a>{" "}
                                - enabling or disabling recipient acceptance.
                            </li>
                        </ul>
                    </>
                ),
            },
            {
                id: "building-a-workflow",
                title: "Building a workflow",
                content: (
                    <>
                        <p>
                            This guide walks you through creating a workflow definition from scratch
                            using the Workflow Designer in Settings.
                        </p>

                        <h3>Before you begin</h3>
                        <ul>
                            <li>You must be an Organization Admin.</li>
                            <li>
                                Decide which lifecycle stage you want to gate and choose the
                                corresponding{" "}
                                <a href="#" data-help-article="trigger-events">
                                    trigger event
                                </a>
                                .
                            </li>
                            <li>
                                Know who the approvers or notification recipients should be. Using
                                role-based or group-role-based assignees is strongly recommended
                                over hardcoded user IDs.
                            </li>
                        </ul>

                        <h3>Step 1: open the Workflow Designer</h3>
                        <ol>
                            <li>Open Settings from the top navigation bar.</li>
                            <li>Click the <b>Workflows</b> tab.</li>
                            <li>
                                Click <b>New workflow</b> to open the designer, or click{" "}
                                <b>Edit</b> on an existing workflow definition row to modify it.
                            </li>
                        </ol>

                        <h3>Step 2: fill in the header fields</h3>
                        <ul>
                            <li>
                                <b>Name</b> - a short, descriptive name shown in the definition
                                list (for example, "Recipient Acceptance Gate" or "Pre-Send Manager
                                Approval").
                            </li>
                            <li>
                                <b>Trigger event</b> - select from the dropdown. The available{" "}
                                <code>$subject.*</code> fields for assignee placeholders update
                                automatically based on your selection.
                            </li>
                            <li>
                                <b>Summary</b> - a plain-language description of what this
                                workflow does and when it applies. Shown in the template library.
                            </li>
                            <li>
                                <b>Tags</b> - optional free-text labels (Legal, Finance,
                                Healthcare, HR, Logistics, etc.) that help teams filter templates.
                                Any tag value is valid.
                            </li>
                            <li>
                                <b>Active</b> - only active definitions are matched when a trigger
                                event fires. Toggle this off to pause a workflow without deleting
                                it.
                            </li>
                        </ul>

                        <h3>Step 3: add steps</h3>
                        <ol>
                            <li>Click <b>Add step</b> below the header section.</li>
                            <li>
                                Choose a{" "}
                                <a href="#" data-help-article="step-types">
                                    step type
                                </a>{" "}
                                from the dropdown: APPROVAL, NOTIFICATION, CONDITION, or ACTION.
                            </li>
                            <li>Configure the step fields for that type (see step types guide).</li>
                            <li>
                                Set the <b>outcome connectors</b> (On Approve / On Reject, or On
                                True / On False for CONDITION steps) to point to the next step
                                index or to END.
                            </li>
                            <li>Repeat for each step in the workflow.</li>
                        </ol>

                        <h3>Step 4: review portability warnings</h3>
                        <p>
                            Before saving, the designer checks whether any step contains a
                            hardcoded principal UUID in an assignee entry. If it does, a portability
                            warning appears in the save bar. Hardcoded UUIDs work in your org but
                            cannot be reused if the definition is cloned by another org. Replace
                            them with{" "}
                            <a href="#" data-help-article="assignees-and-portability">
                                role-based or group-role-based assignees
                            </a>{" "}
                            to make the workflow fully portable.
                        </p>

                        <h3>Step 5: save</h3>
                        <p>
                            Click <b>Save workflow</b>. The definition is created (POST) or updated
                            (PUT) immediately. If the Active toggle is on, the engine will match
                            this definition the next time the trigger event fires for an Exchange
                            in your organization.
                        </p>

                        <h3>Editing a live workflow</h3>
                        <p>
                            You cannot edit a definition while it has at least one running
                            instance. The edit button is disabled and a message explains why. Wait
                            for the running instances to complete or cancel them before editing.
                        </p>

                        <h3>Deleting a workflow</h3>
                        <p>
                            Delete deactivates the definition (soft delete). It cannot be deleted
                            while running instances reference it. Completed historical instances
                            are preserved for audit purposes.
                        </p>
                    </>
                ),
            },
            {
                id: "step-types",
                title: "Step types explained",
                content: (
                    <>
                        <p>
                            Each step in a workflow has a type that determines how the engine
                            processes it. There are four types: APPROVAL, NOTIFICATION, CONDITION,
                            and ACTION.
                        </p>

                        <h3>APPROVAL</h3>
                        <p>
                            An APPROVAL step pauses the workflow and waits for human decisions
                            from the assigned principals. The step advances once the quorum
                            requirement is met.
                        </p>
                        <p><b>Key fields:</b></p>
                        <ul>
                            <li>
                                <b>Assignees</b> - who receives the approval task. See{" "}
                                <a href="#" data-help-article="assignees-and-portability">
                                    Assignees and portability
                                </a>
                                .
                            </li>
                            <li>
                                <b>Quorum</b> - how many approvals are needed:
                                <ul>
                                    <li><b>Any</b> - at least one assignee must approve.</li>
                                    <li><b>All</b> - every assignee must approve.</li>
                                    <li>
                                        <b>N of M</b> - exactly N assignees must approve (N is
                                        configured in the designer).
                                    </li>
                                </ul>
                            </li>
                            <li>
                                <b>SLA (minutes)</b> - optional deadline. If the step is still
                                pending after this many minutes, the escalation action triggers.
                            </li>
                            <li>
                                <b>Escalation action</b> - what happens when the SLA expires:
                                <ul>
                                    <li><b>Escalate</b> - reassign to a different set of assignees.</li>
                                    <li><b>Auto-approve</b> - automatically approve and advance.</li>
                                    <li><b>Auto-reject</b> - automatically reject and advance.</li>
                                </ul>
                            </li>
                            <li>
                                <b>On Approve / On Reject</b> - the next step to go to and an
                                optional domain event to emit when that outcome is reached.
                            </li>
                        </ul>

                        <h3>NOTIFICATION</h3>
                        <p>
                            A NOTIFICATION step sends a message to the assigned principals and
                            then advances automatically without waiting for a decision. Use it to
                            inform stakeholders at key lifecycle points.
                        </p>
                        <p><b>Key fields:</b></p>
                        <ul>
                            <li>
                                <b>Assignees</b> - who receives the notification.
                            </li>
                            <li>
                                <b>Message template key</b> - identifies which email and in-app
                                message template to use. Contact your platform admin for the
                                available template keys.
                            </li>
                            <li>
                                <b>On Approve</b> - the next step after the notification is sent.
                                (There is no "reject" outcome for a notification step.)
                            </li>
                        </ul>

                        <h3>CONDITION</h3>
                        <p>
                            A CONDITION step evaluates an expression against the Exchange subject
                            data and branches the workflow based on the result. It advances
                            automatically without human input.
                        </p>
                        <p><b>Key fields:</b></p>
                        <ul>
                            <li>
                                <b>Predicate expression</b> - a simple boolean expression in the
                                format <code>$subject.fieldName operator 'value'</code>. Supported
                                operators:
                                <ul>
                                    <li><code>==</code> - exact match.</li>
                                    <li><code>!=</code> - not equal.</li>
                                    <li><code>contains</code> - substring check.</li>
                                    <li><code>startsWith</code> - prefix check.</li>
                                </ul>
                                Example: <code>$subject.recipientType == 'GROUP'</code>
                            </li>
                            <li>
                                <b>On True</b> - the next step when the predicate evaluates to
                                true.
                            </li>
                            <li>
                                <b>On False</b> - the next step when the predicate evaluates to
                                false.
                            </li>
                        </ul>
                        <p>
                            Available field names come from the trigger event's subject fields. The
                            designer shows which fields are available based on the selected trigger.
                        </p>

                        <h3>ACTION</h3>
                        <p>
                            An ACTION step runs a built-in automated action and then advances
                            automatically. Use it to trigger system operations as part of the
                            workflow.
                        </p>
                        <p><b>Key fields:</b></p>
                        <ul>
                            <li>
                                <b>Action handler key</b> - selects which registered action to
                                run. Built-in action keys:
                                <ul>
                                    <li>
                                        <b>exchange.auto-accept</b> - sets the Exchange to Active
                                        (ACCEPTED_STARTED) and fires the <code>exchange.activated</code>{" "}
                                        event.
                                    </li>
                                    <li>
                                        <b>exchange.send-reminder</b> - sends a reminder email and
                                        in-app notification to the recipient.
                                    </li>
                                    <li>
                                        <b>exchange.revoke-access</b> - revokes all active shares
                                        on the Exchange.
                                    </li>
                                </ul>
                            </li>
                            <li>
                                <b>Message template key</b> - used by action handlers that send
                                messages.
                            </li>
                            <li>
                                <b>On Approve</b> - the next step after the action completes
                                successfully.
                            </li>
                        </ul>

                        <h3>Step ordering and branching</h3>
                        <p>
                            Steps are stored in a list and referred to by their index (0-based).
                            The "On Approve / On Reject / On True / On False" outcome connectors
                            point to the next step index or to <code>END</code> to terminate the
                            workflow. Branching is supported through CONDITION steps, which route
                            to different step indices on each branch.
                        </p>

                        <h3>Learn more</h3>
                        <ul>
                            <li>
                                <a href="#" data-help-article="sla-escalations-reminders">
                                    <b>SLA, escalations, and reminders</b>
                                </a>{" "}
                                - deadline management for APPROVAL steps.
                            </li>
                        </ul>
                    </>
                ),
            },
            {
                id: "assignees-and-portability",
                title: "Assignees and portability",
                content: (
                    <>
                        <p>
                            Assignees define who participates in a step as approvers, notification
                            recipients, or escalation targets. Configuring assignees correctly is
                            the most important factor in making your workflow definitions portable
                            and reusable.
                        </p>

                        <h3>Assignee kinds</h3>
                        <p>
                            Each assignee entry has a <b>kind</b> that determines how the engine
                            resolves the actual set of users at runtime.
                        </p>

                        <h3>ROLE</h3>
                        <p>
                            Resolves to all users who hold a named role in a given scope. This is
                            the most portable option because it references a logical role rather
                            than specific people.
                        </p>
                        <p><b>Fields:</b></p>
                        <ul>
                            <li>
                                <b>Role name</b> - the role to target (for example, OWNER, EDITOR,
                                REVIEWER, SIGNER).
                            </li>
                            <li>
                                <b>Scope type</b> - either APP (platform-wide) or ORG
                                (organization-scoped).
                            </li>
                            <li>
                                <b>Scope ID ref</b> - the ID of the scope target. Use{" "}
                                <code>$subject.orgId</code> to reference the initiator's
                                organization dynamically instead of hardcoding an org ID.
                            </li>
                        </ul>

                        <h3>GROUP_ROLE</h3>
                        <p>
                            Resolves to members of a specific Principal Group who hold a given
                            role within that group (OWNER, MANAGER, MEMBER, or OBSERVER).
                        </p>
                        <p><b>Fields:</b></p>
                        <ul>
                            <li>
                                <b>Group ID ref</b> - the Principal Group to target. Use{" "}
                                <code>$subject.recipientGroupId</code> to reference the recipient
                                group dynamically.
                            </li>
                            <li>
                                <b>Group role</b> - which role within the group to target
                                (OWNER, MANAGER, MEMBER, or OBSERVER).
                            </li>
                        </ul>

                        <h3>PRINCIPAL</h3>
                        <p>
                            Resolves to a specific user or group by their UUID. This is convenient
                            for one-off customizations but makes the definition non-portable because
                            the UUID is only valid in your organization.
                        </p>
                        <p><b>Fields:</b></p>
                        <ul>
                            <li>
                                <b>Principal kind</b> - APP_USER or GROUP.
                            </li>
                            <li>
                                <b>Principal ID</b> - the exact UUID of the user or group.
                            </li>
                        </ul>

                        <h3>Portability warning</h3>
                        <p>
                            The Workflow Designer checks all assignee entries when you save. If any
                            entry uses a hardcoded UUID (PRINCIPAL kind), a warning appears in the
                            save bar. The definition will still save and work correctly within your
                            organization, but:
                        </p>
                        <ul>
                            <li>
                                If the definition is cloned by another organization, the hardcoded
                                UUID is automatically replaced with a ROLE placeholder pointing to
                                the REVIEWER role in the receiving org.
                            </li>
                            <li>
                                If you plan to publish this definition as a platform template,
                                resolve all portability warnings first by replacing PRINCIPAL
                                entries with ROLE or GROUP_ROLE entries.
                            </li>
                        </ul>

                        <h3>Using $subject.* placeholders</h3>
                        <p>
                            Subject field placeholders are replaced at runtime with the actual
                            values from the Exchange that triggered the workflow. Available fields
                            depend on the trigger event. For example, a workflow on{" "}
                            <code>exchange.acceptance_pending</code> can use:
                        </p>
                        <ul>
                            <li><code>$subject.recipientId</code> - the recipient user.</li>
                            <li><code>$subject.recipientGroupId</code> - the recipient group.</li>
                            <li><code>$subject.initiatorId</code> - the Exchange initiator.</li>
                            <li><code>$subject.orgId</code> - the initiator's organization.</li>
                        </ul>
                        <p>
                            The trigger event dropdown in the designer updates the available
                            placeholder auto-complete list automatically.
                        </p>

                        <h3>Best practices</h3>
                        <ol>
                            <li>
                                Prefer ROLE or GROUP_ROLE assignees over PRINCIPAL to keep
                                definitions reusable.
                            </li>
                            <li>
                                Use <code>$subject.orgId</code> instead of your own org UUID in
                                ROLE scope ID refs.
                            </li>
                            <li>
                                Use <code>$subject.recipientGroupId</code> for GROUP_ROLE entries
                                on acceptance workflows.
                            </li>
                            <li>
                                Reserve PRINCIPAL kind for highly specific one-off use cases where
                                portability is not a concern.
                            </li>
                        </ol>
                    </>
                ),
            },
            {
                id: "sla-escalations-reminders",
                title: "SLA, escalations, and reminders",
                content: (
                    <>
                        <p>
                            APPROVAL steps support deadline management through SLA settings,
                            escalation actions, and reminder addons. These features ensure that
                            pending approvals do not stall indefinitely.
                        </p>

                        <h3>SLA (service-level agreement)</h3>
                        <p>
                            The SLA field on an APPROVAL step sets a deadline in minutes from
                            when the step becomes active. The escalation scheduler checks all
                            pending steps every 60 seconds and triggers the configured escalation
                            action when the deadline passes.
                        </p>
                        <p>
                            Leave the SLA field empty if no deadline is required for a step.
                        </p>

                        <h3>Escalation actions</h3>
                        <p>
                            When an SLA deadline is breached, one of the following happens based
                            on the escalation action you configure:
                        </p>
                        <ul>
                            <li>
                                <b>Escalate</b> - the step is reassigned to a different set of
                                assignees (the "escalate to" assignee list). The original assignees
                                no longer hold pending tasks. Use this to route stalled approvals
                                to a manager or admin.
                            </li>
                            <li>
                                <b>Auto-approve</b> - the step is automatically approved and the
                                workflow continues on the approval path. Use this for low-risk gates
                                where silence implies consent.
                            </li>
                            <li>
                                <b>Auto-reject</b> - the step is automatically rejected. Use this
                                for strict compliance scenarios where inaction should block the
                                Exchange.
                            </li>
                        </ul>

                        <h3>Reminder addons</h3>
                        <p>
                            Reminder addons send notifications to pending assignees before or
                            after certain time thresholds, independently of the SLA escalation.
                            They are configured per step.
                        </p>
                        <p>
                            There are two reminder types:
                        </p>

                        <h3>Reminder before due</h3>
                        <p>
                            Sends a reminder a specified number of minutes before the SLA
                            deadline. For example, set 60 minutes to warn assignees one hour
                            before the step expires.
                        </p>
                        <p><b>Fields:</b></p>
                        <ul>
                            <li>
                                <b>Minutes before due</b> - how far in advance to send the
                                reminder.
                            </li>
                            <li>
                                <b>Recipient ref</b> - who receives the reminder (uses the same
                                assignee builder as the main step).
                            </li>
                            <li>
                                <b>Message template key</b> - optional custom message template.
                            </li>
                        </ul>
                        <p>
                            This reminder fires once. The engine tracks whether it has already
                            been sent to prevent duplicate messages.
                        </p>

                        <h3>Reminder if no decision</h3>
                        <p>
                            Sends a reminder if the step has been pending for a set number of
                            minutes with no decision recorded. Optionally repeats on a cadence
                            until a decision is made.
                        </p>
                        <p><b>Fields:</b></p>
                        <ul>
                            <li>
                                <b>After minutes</b> - how many minutes of inactivity before the
                                first reminder.
                            </li>
                            <li>
                                <b>Repeat every (minutes)</b> - optional. When set, the reminder
                                repeats on this interval after the first send, until a decision is
                                made.
                            </li>
                            <li>
                                <b>Recipient ref</b> - who receives the reminder.
                            </li>
                            <li>
                                <b>Message template key</b> - optional custom message template.
                            </li>
                        </ul>

                        <h3>How the scheduler works</h3>
                        <p>
                            The escalation scheduler runs automatically in the background every
                            60 seconds. For each pending step it:
                        </p>
                        <ol>
                            <li>Checks whether the SLA deadline has passed and applies the escalation action if so.</li>
                            <li>Evaluates each reminder addon against current step state.</li>
                            <li>Sends any reminders that are due and marks them as sent to prevent repetition.</li>
                        </ol>
                        <p>
                            No manual action is required to activate this behavior. It runs for all
                            active workflow instances across your organization.
                        </p>

                        <h3>Recommendations</h3>
                        <ul>
                            <li>
                                Set a "Reminder if no decision" addon before using "Auto-reject"
                                escalation so assignees have a chance to act before the step
                                auto-rejects.
                            </li>
                            <li>
                                Use "Reminder before due" to give assignees advance warning when
                                the SLA is tight.
                            </li>
                            <li>
                                Combine both reminder types for high-stakes approval steps.
                            </li>
                        </ul>
                    </>
                ),
            },
            {
                id: "platform-templates",
                title: "Platform templates",
                content: (
                    <>
                        <p>
                            Platform templates are workflow definitions provided by DocuHyphen that
                            cover common use cases across industries. They are available to all
                            organizations and can be cloned and customized to fit your specific
                            processes.
                        </p>

                        <h3>Browsing platform templates</h3>
                        <ol>
                            <li>Open Settings and go to the <b>Workflows</b> tab.</li>
                            <li>
                                Scroll down to the <b>Platform Templates</b> section below your
                                organization's workflows.
                            </li>
                            <li>
                                Browse by name, description, or tags. Examples of
                                available tags: Legal, Finance, Healthcare, HR, Logistics.
                            </li>
                        </ol>

                        <h3>Adding a template to your organization</h3>
                        <ol>
                            <li>
                                Find the template you want and click <b>Add to my workflows</b>.
                            </li>
                            <li>
                                A copy of the template is cloned into your organization. The copy
                                is independent from the original and can be edited freely.
                            </li>
                            <li>
                                The cloned definition starts <b>inactive</b>. Review it in the
                                designer and activate it when you are ready.
                            </li>
                        </ol>

                        <h3>What happens when a template is cloned</h3>
                        <p>
                            When you clone a platform template, the engine performs the following
                            automatically:
                        </p>
                        <ul>
                            <li>
                                All hardcoded principal UUIDs in assignee entries are replaced with
                                a portable ROLE placeholder pointing to the REVIEWER role in your
                                organization. Review and adjust these after cloning.
                            </li>
                            <li>
                                The cloned definition records the source template ID so you can
                                trace which template it came from.
                            </li>
                            <li>
                                The <code>isTemplate</code> flag is set to false on the clone,
                                marking it as an org-owned definition rather than a platform
                                template.
                            </li>
                        </ul>

                        <h3>Customizing a cloned template</h3>
                        <ol>
                            <li>
                                Find the cloned workflow in the <b>My Workflows</b> section and
                                click <b>Edit</b>.
                            </li>
                            <li>
                                Review all assignee entries. The replaced ROLE placeholders may
                                not match your intended approvers. Update them to the correct
                                roles, groups, or individuals.
                            </li>
                            <li>
                                Adjust SLA deadlines, escalation actions, and reminder addons to
                                match your team's expected response times.
                            </li>
                            <li>
                                Update the name and summary to reflect your org's specific process.
                            </li>
                            <li>
                                Toggle <b>Active</b> on and click <b>Save workflow</b>.
                            </li>
                        </ol>

                        <h3>Duplicating your own workflows</h3>
                        <p>
                            You can also duplicate your own org's workflows using the{" "}
                            <b>Duplicate</b> row action in the My Workflows list. The duplicate
                            starts inactive and is a full independent copy that you can edit
                            without affecting the original.
                        </p>

                        <h3>Tagsags</h3>
                        <p>
                            Tags are free-form strings, not a fixed list. You can add any tag when
                            creating or editing a workflow definition, which means new industries
                            are supported without any platform updates. The Platform Templates
                            section uses these tags to help you filter relevant templates.
                        </p>
                    </>
                ),
            },
            {
                id: "workflow-activity-monitoring",
                title: "Workflow activity and monitoring",
                content: (
                    <>
                        <p>
                            The Activity tab in the Workflows section gives Organization Admins a
                            real-time view of all running, completed, rejected, and cancelled
                            workflow instances across the organization's Exchanges.
                        </p>

                        <h3>Opening the activity dashboard</h3>
                        <ol>
                            <li>Open Settings and go to the <b>Workflows</b> tab.</li>
                            <li>Click the <b>Activity</b> sub-tab at the top of the panel.</li>
                        </ol>

                        <h3>Reading the instance list</h3>
                        <p>
                            Each row represents one workflow instance. The columns show:
                        </p>
                        <ul>
                            <li><b>Workflow name</b> - the definition that created this instance.</li>
                            <li>
                                <b>Exchange</b> - the Exchange this instance is running against.
                                Click the exchange name to navigate to that Exchange.
                            </li>
                            <li><b>Trigger</b> - the event that started the instance.</li>
                            <li>
                                <b>Status</b> - one of: RUNNING, COMPLETED, REJECTED, CANCELLED,
                                or ESCALATED.
                            </li>
                            <li>
                                <b>Current step</b> - the index of the active step within the
                                instance.
                            </li>
                            <li><b>Started</b> - when the instance was created.</li>
                            <li><b>Completed</b> - when the instance finished (if applicable).</li>
                        </ul>

                        <h3>Filtering instances</h3>
                        <p>
                            Use the <b>Status</b> dropdown to filter by instance status. For
                            example, select RUNNING to see all instances currently waiting for
                            human decisions.
                        </p>

                        <h3>Viewing the step timeline</h3>
                        <p>
                            Click any row to open the instance detail drawer. This shows a
                            full timeline of every step in the workflow:
                        </p>
                        <ul>
                            <li>
                                <b>Step type and status</b> - the type (APPROVAL, NOTIFICATION,
                                etc.) and current status (PENDING, COMPLETED, REJECTED, SKIPPED).
                            </li>
                            <li>
                                <b>Assignees</b> - who was assigned to this step.
                            </li>
                            <li>
                                <b>SLA deadline</b> - the due date and time for the step, if an
                                SLA was configured.
                            </li>
                            <li>
                                <b>Decision entries</b> - for APPROVAL steps, a list of every
                                decision recorded: who decided, what their decision was (APPROVE or
                                REJECT), when they decided, and any reason they provided.
                            </li>
                            <li>
                                <b>Completed at</b> - when the step finished.
                            </li>
                        </ul>
                        <p>
                            The currently active step is highlighted in the timeline so you can
                            identify at a glance which step is blocking progress.
                        </p>

                        <h3>Instance statuses explained</h3>
                        <ul>
                            <li>
                                <b>RUNNING</b> - at least one step is still PENDING. The workflow
                                has not yet reached a terminal state.
                            </li>
                            <li>
                                <b>COMPLETED</b> - all steps finished on the approval/true path.
                                The Exchange lifecycle transition was unblocked.
                            </li>
                            <li>
                                <b>REJECTED</b> - a step was rejected (either by an assignee or
                                by an AUTO_REJECT escalation), causing the workflow to terminate
                                early.
                            </li>
                            <li>
                                <b>CANCELLED</b> - the instance was cancelled before completion,
                                typically because the underlying Exchange was cancelled or the
                                definition was deactivated.
                            </li>
                            <li>
                                <b>ESCALATED</b> - an SLA breach triggered the Escalate action
                                and the instance is now assigned to escalation targets.
                            </li>
                        </ul>

                        <h3>Using the activity view for compliance</h3>
                        <p>
                            The step timeline serves as a built-in audit trail. Each decision
                            entry records a timestamp, the decision maker's identity, and any
                            reason they provided. This data is preserved even after the workflow
                            instance is complete, making it suitable for compliance reviews and
                            record retention.
                        </p>
                    </>
                ),
            },
            {
                id: "workflow-org-settings",
                title: "Organization workflow settings",
                content: (
                    <>
                        <p>
                            Organization Admins can configure how workflows interact with the
                            Exchange lifecycle through organization-level settings.
                        </p>

                        <h3>Require recipient acceptance</h3>
                        <p>
                            The <b>Require recipient acceptance</b> toggle controls whether every
                            Exchange created in your organization must wait for at least one
                            recipient to accept before it transitions to Active.
                        </p>
                        <ul>
                            <li>
                                <b>On (default)</b> - newly created Exchanges stay in Draft until
                                a recipient accepts. If a workflow definition for the{" "}
                                <code>exchange.acceptance_pending</code> trigger is active, that
                                workflow gates the acceptance. If no matching definition is active,
                                the platform still requires at least one manual recipient acceptance
                                before the Exchange becomes Active.
                            </li>
                            <li>
                                <b>Off</b> - newly created Exchanges advance to Active immediately
                                on creation. The <code>exchange.activated</code> event fires and
                                the Exchange bypasses the acceptance stage. Use this for internal
                                or automated Exchanges where recipient sign-off is not needed.
                            </li>
                        </ul>
                        <p>
                            Changing this setting affects all new Exchanges created after the
                            change. Exchanges that are already in Draft or Active are not affected.
                        </p>

                        <h3>Where to find this setting</h3>
                        <ol>
                            <li>Open Settings.</li>
                            <li>Go to the <b>Organization</b> tab.</li>
                            <li>
                                Find the <b>Require recipient acceptance</b> toggle in the
                                Exchange settings section.
                            </li>
                            <li>Toggle it on or off and save.</li>
                        </ol>

                        <h3>How this interacts with workflows</h3>
                        <p>
                            The setting and the workflow engine work together:
                        </p>
                        <ul>
                            <li>
                                When the setting is <b>on</b> and a matching{" "}
                                <code>exchange.acceptance_pending</code> workflow is active, the
                                recipient's "Accept" action is routed through the workflow engine.
                                The workflow step records the decision and, once the quorum is met,
                                advances the Exchange to Active.
                            </li>
                            <li>
                                When the setting is <b>on</b> but no matching workflow is active,
                                the platform still guards the transition. A direct recipient
                                acceptance still moves the Exchange to Active, just without a
                                workflow gate.
                            </li>
                            <li>
                                When the setting is <b>off</b>, the{" "}
                                <code>exchange.acceptance_pending</code> trigger never fires and
                                acceptance workflow definitions for that trigger will not run.
                            </li>
                        </ul>

                        <h3>No-account (OTP) recipients</h3>
                        <p>
                            Recipients who do not have a DocuHyphen account and accept via a
                            one-time password link are also routed through the workflow engine when
                            the setting is on. After OTP verification, their acceptance is recorded
                            as a workflow decision, ensuring consistent audit records regardless of
                            recipient account type.
                        </p>

                        <h3>Related articles</h3>
                        <ul>
                            <li>
                                <a href="#" data-help-article="trigger-events">
                                    <b>Trigger events</b>
                                </a>{" "}
                                - how exchange.acceptance_pending works.
                            </li>
                            <li>
                                <a href="#" data-help-article="workflow-overview">
                                    <b>Workflow overview</b>
                                </a>{" "}
                                - the full Exchange lifecycle with workflows.
                            </li>
                        </ul>
                    </>
                ),
            },
        ],
    },
];

export const HELP_DOC_ARTICLES: HelpDocArticle[] = helpDocSections.flatMap((section) =>
    section.articles.map((article) => ({
        id: article.id,
        sectionId: section.id,
        sectionTitle: section.title,
        title: article.title,
        content: article.content,
    })),
);

export function getDefaultHelpDocArticle(): HelpDocArticle
{
    return HELP_DOC_ARTICLES[0];
}

export function getHelpDocArticleById(articleId: string): HelpDocArticle | undefined
{
    return HELP_DOC_ARTICLES.find((article) => article.id === articleId);
}

export function getHelpDocSections()
{
    return helpDocSections;
}


