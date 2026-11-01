
export const workflowActivityMonitoringArticle = (
    <>
        <p id={"workflow-activity-monitoring-intro-paragraph"}>
            The Activity tab in the Workflows section gives Organization Admins a real-time view of all running,
            completed, rejected, cancelled, and failed workflow instances across the organization's Exchanges.
        </p>

        <h3 id={"workflow-activity-monitoring-opening-the-activity-dashboard-heading"}>Opening the activity
            dashboard</h3>
        <ol id={"workflow-activity-monitoring-opening-the-activity-dashboard-list"}>
            <li id={"workflow-activity-monitoring-article-li-1"}>Open Settings and go to the <b id={"workflow-activity-monitoring-article-b-1"}>Workflows</b> tab.</li>
            <li id={"workflow-activity-monitoring-article-li-2"}>Click the <b id={"workflow-activity-monitoring-article-b-2"}>Activity</b> sub-tab at the top of the panel.</li>
        </ol>

        <h3 id={"workflow-activity-monitoring-reading-the-instance-list-heading"}>Reading the instance list</h3>
        <p id={"workflow-activity-monitoring-reading-the-instance-list-paragraph"}>
            Each row represents one workflow instance and shows:
        </p>
        <ul id={"workflow-activity-monitoring-reading-the-instance-list"}>
            <li id={"workflow-activity-monitoring-article-li-3"}><b id={"workflow-activity-monitoring-article-b-3"}>Workflow name</b> - the definition that created this instance.</li>
            <li id={"workflow-activity-monitoring-article-li-4"}>
                <b id={"workflow-activity-monitoring-article-b-4"}>Exchange</b> - the Exchange this instance is running against (shown when available).
            </li>
            <li id={"workflow-activity-monitoring-article-li-5"}>
                <b id={"workflow-activity-monitoring-article-b-5"}>Step and started date</b> - the current step number and when the instance was created (for example,
                "Step 2 - Started Jun 21, 2026").
            </li>
            <li id={"workflow-activity-monitoring-article-li-6"}>
                <b id={"workflow-activity-monitoring-article-b-6"}>Status badge</b> - the instance status: In Progress, Completed, Rejected, Cancelled, Escalated, or
                Failed.
            </li>
        </ul>

        <h3 id={"workflow-activity-monitoring-filtering-instances-heading"}>Filtering instances</h3>
        <p id={"workflow-activity-monitoring-filtering-instances-paragraph"}>
            Use the <b id={"workflow-activity-monitoring-article-b-7"}>Filter by status</b> dropdown to narrow the list. Options are: All, In Progress, Completed,
            Rejected, Cancelled, Escalated, and Failed. For example, select <b id={"workflow-activity-monitoring-article-b-8"}>In Progress</b> to see all instances
            currently waiting for human decisions. Use the <b id={"workflow-activity-monitoring-article-b-9"}>Refresh</b> button to reload the list after filtering.
        </p>

        <h3 id={"workflow-activity-monitoring-viewing-the-step-timeline-heading"}>Viewing the step timeline</h3>
        <p id={"workflow-activity-monitoring-viewing-the-step-timeline-paragraph"}>
            Click any row to open the instance detail drawer. This shows a full timeline of every step in the workflow:
        </p>
        <ul id={"workflow-activity-monitoring-viewing-the-step-timeline-list"}>
            <li id={"workflow-activity-monitoring-article-li-7"}>
                <b id={"workflow-activity-monitoring-article-b-10"}>Step type and status</b> - the step type (Approval, Notification, Condition, Action, Wait for
                Counterparty Clearance) and its status: Waiting, Awaiting Counterparty, Completed, Rejected, Escalated,
                or Skipped.
            </li>
            <li id={"workflow-activity-monitoring-article-li-8"}>
                <b id={"workflow-activity-monitoring-article-b-11"}>Assignees</b> - who was assigned to this step.
            </li>
            <li id={"workflow-activity-monitoring-article-li-9"}>
                <b id={"workflow-activity-monitoring-article-b-12"}>SLA deadline</b> - the due date and time for the step, if an SLA was configured.
            </li>
            <li id={"workflow-activity-monitoring-article-li-10"}>
                <b id={"workflow-activity-monitoring-article-b-13"}>Decision entries</b> - for Approval steps, a list of every decision recorded: who decided, what their
                decision was (Approve or Reject), when they decided, and any reason they provided.
            </li>
            <li id={"workflow-activity-monitoring-article-li-11"}>
                <b id={"workflow-activity-monitoring-article-b-14"}>Completed at</b> - when the step finished.
            </li>
        </ul>
        <p id={"workflow-activity-monitoring-viewing-the-step-timeline-paragraph-2"}>
            The currently active step is highlighted in the timeline so you can identify at a glance which step is
            blocking progress.
        </p>

        <h3 id={"workflow-activity-monitoring-instance-statuses-explained-heading"}>Instance statuses explained</h3>
        <ul id={"workflow-activity-monitoring-instance-statuses-explained-list"}>
            <li id={"workflow-activity-monitoring-article-li-12"}>
                <b id={"workflow-activity-monitoring-article-b-15"}>In Progress</b> - at least one step is still waiting for a decision. The workflow has not yet reached
                a terminal state and runs against the structure frozen when the instance started, so later definition
                edits never affect it.
            </li>
            <li id={"workflow-activity-monitoring-article-li-13"}>
                <b id={"workflow-activity-monitoring-article-b-16"}>Completed</b> - all steps finished on the approval/true path. The Exchange lifecycle transition was
                unblocked.
            </li>
            <li id={"workflow-activity-monitoring-article-li-14"}>
                <b id={"workflow-activity-monitoring-article-b-17"}>Rejected</b> - a step was rejected (either by an assignee or by an Auto-reject escalation), causing
                the workflow to terminate early.
            </li>
            <li id={"workflow-activity-monitoring-article-li-15"}>
                <b id={"workflow-activity-monitoring-article-b-18"}>Cancelled</b> - the instance was cancelled before completion, typically because the underlying
                Exchange was cancelled or the definition was deactivated.
            </li>
            <li id={"workflow-activity-monitoring-article-li-16"}>
                <b id={"workflow-activity-monitoring-article-b-19"}>Escalated</b> - an SLA breach triggered the Escalate action and the step was reassigned to the
                escalation targets. The instance is still active and pending: reminders continue, it keeps blocking the
                Exchange decision it gates, its definition cannot be edited or deleted, and cancelling or rescinding the
                Exchange still cancels it.
            </li>
            <li id={"workflow-activity-monitoring-article-li-17"}>
                <b id={"workflow-activity-monitoring-article-b-20"}>Failed</b> - the engine could not run the instance safely (for example its saved execution snapshot
                is missing or unreadable). The Exchange is left unchanged for manual review, and the detail drawer shows
                a short reason.
            </li>
        </ul>

        <h3 id={"workflow-activity-monitoring-workflows-on-an-heading"}>Monitoring workflows on an Exchange</h3>
        <p id={"workflow-activity-monitoring-workflows-on-an-paragraph"}>
            Participants with workflow automation in their current plan also monitor an Exchange's own workflows from
            the <b id={"workflow-activity-monitoring-article-b-21"}>Workflow</b> tab on that Exchange. A circular menu control at the top right
            chooses <b id={"workflow-activity-monitoring-article-b-22"}>Timeline</b>, <b id={"workflow-activity-monitoring-article-b-23"}>Diagram</b>,
            or <b id={"workflow-activity-monitoring-article-b-24"}>Both</b> for the whole tab; Timeline is the default and the control changes every workflow shown in
            the tab at once. <b id={"workflow-activity-monitoring-article-b-25"}>Both</b> splits the tab into a detail column (summary, action required, and Timeline) on
            the left and a live diagram preview on the right. Each diagram also has a bottom-right orientation button so
            viewers can switch between vertical and horizontal layouts. The canvas controls include fit to window,
            enlarge, and full screen buttons for focused viewing. The Diagram overlays runtime state and the traversed
            route onto the frozen workflow structure. If a diagram cannot render, that section offers a <b id={"workflow-activity-monitoring-article-b-26"}>View
            timeline</b> button and falls back to the Timeline. See
            <a id={"workflow-activity-monitoring-workflows-on-an-link"}
               href="#"
               data-help-article="workflow-diagram-preview">
                Workflow diagrams and preview
            </a>
            .
        </p>

        <h3 id={"workflow-activity-monitoring-using-the-activity-view-heading"}>Using the activity view for
            compliance</h3>
        <p id={"workflow-activity-monitoring-using-the-activity-view-paragraph"}>
            The step timeline serves as a built-in audit trail. Each decision entry records a timestamp, the decision
            maker's identity, and any reason they provided. This data is preserved even after the workflow instance is
            complete, making it suitable for compliance reviews and record retention.
        </p>
    </>
);
