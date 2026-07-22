import React from "react";

export const workflowActivityMonitoringArticle = (
    <>
        <p>
            The Activity tab in the Workflows section gives Organization Admins a
            real-time view of all running, completed, rejected, cancelled, and
            failed workflow instances across the organization's Exchanges.
        </p>

        <h3>Opening the activity dashboard</h3>
        <ol>
            <li>Open Settings and go to the <b>Workflows</b> tab.</li>
            <li>Click the <b>Activity</b> sub-tab at the top of the panel.</li>
        </ol>

        <h3>Reading the instance list</h3>
        <p>
            Each row represents one workflow instance and shows:
        </p>
        <ul>
            <li><b>Workflow name</b> - the definition that created this instance.</li>
            <li>
                <b>Exchange</b> - the Exchange this instance is running against
                (shown when available).
            </li>
            <li>
                <b>Step and started date</b> - the current step number and when
                the instance was created (for example, "Step 2 - Started Jun 21,
                2026").
            </li>
            <li>
                <b>Status badge</b> - the instance status: In Progress, Completed,
                Rejected, Cancelled, Escalated, or Failed.
            </li>
        </ul>

        <h3>Filtering instances</h3>
        <p>
            Use the <b>Filter by status</b> dropdown to narrow the list. Options
            are: All, In Progress, Completed, Rejected, Cancelled, Escalated, and
            Failed.
            For example, select <b>In Progress</b> to see all instances currently
            waiting for human decisions. Use the <b>Refresh</b> button to reload
            the list after filtering.
        </p>

        <h3>Viewing the step timeline</h3>
        <p>
            Click any row to open the instance detail drawer. This shows a
            full timeline of every step in the workflow:
        </p>
        <ul>
            <li>
                <b>Step type and status</b> - the step type (Approval, Notification,
                Condition, Action, Wait for Counterparty Clearance) and its
                status: Waiting, Awaiting Counterparty, Completed, Rejected,
                Escalated, or Skipped.
            </li>
            <li>
                <b>Assignees</b> - who was assigned to this step.
            </li>
            <li>
                <b>SLA deadline</b> - the due date and time for the step, if an
                SLA was configured.
            </li>
            <li>
                <b>Decision entries</b> - for Approval steps, a list of every
                decision recorded: who decided, what their decision was (Approve or
                Reject), when they decided, and any reason they provided.
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
                <b>In Progress</b> - at least one step is still waiting for a
                decision. The workflow has not yet reached a terminal state and
                runs against the structure frozen when the instance started, so
                later definition edits never affect it.
            </li>
            <li>
                <b>Completed</b> - all steps finished on the approval/true path.
                The Exchange lifecycle transition was unblocked.
            </li>
            <li>
                <b>Rejected</b> - a step was rejected (either by an assignee or
                by an Auto-reject escalation), causing the workflow to terminate
                early.
            </li>
            <li>
                <b>Cancelled</b> - the instance was cancelled before completion,
                typically because the underlying Exchange was cancelled or the
                definition was deactivated.
            </li>
            <li>
                <b>Escalated</b> - an SLA breach triggered the Escalate action and
                the step was reassigned to the escalation targets. The instance is
                still active and pending: reminders continue, it keeps blocking the
                Exchange decision it gates, its definition cannot be edited or
                deleted, and cancelling or rescinding the Exchange still cancels it.
            </li>
            <li>
                <b>Failed</b> - the engine could not run the instance safely (for
                example its saved execution snapshot is missing or unreadable). The
                Exchange is left unchanged for manual review, and the detail drawer
                shows a short reason.
            </li>
        </ul>

        <h3>Monitoring workflows on an Exchange</h3>
        <p>
            Participants also monitor an Exchange's own workflows from the{" "}
            <b>Workflow</b> tab on that Exchange. A circular menu control at the top
            right chooses <b>Timeline</b>, <b>Diagram</b>, or <b>Both</b> for the
            whole tab; Timeline is the default and the control changes every
            workflow shown in the tab at once. <b>Both</b> splits the tab into a
            detail column (summary, action required, and Timeline) on the left and
            a live diagram preview on the right. Each diagram also has a{" "}
            bottom-right orientation button so viewers can switch between vertical
            and horizontal layouts. The canvas controls include enlarge and full
            screen buttons for focused viewing. The Diagram overlays runtime state
            and the traversed route onto the frozen workflow structure. If a
            diagram cannot render, that section offers a <b>View timeline</b>
            button and falls back to the Timeline. See{" "}
            <a href="#"
               data-help-article="workflow-diagram-preview">
                Workflow diagrams and preview
            </a>
            .
        </p>

        <h3>Using the activity view for compliance</h3>
        <p>
            The step timeline serves as a built-in audit trail. Each decision
            entry records a timestamp, the decision maker's identity, and any
            reason they provided. This data is preserved even after the workflow
            instance is complete, making it suitable for compliance reviews and
            record retention.
        </p>
    </>
);
