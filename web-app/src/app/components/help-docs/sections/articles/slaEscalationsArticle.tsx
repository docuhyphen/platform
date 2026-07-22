import React from "react";

export const slaEscalationsArticle = (
    <>
        <p>
            Approval steps support deadline management through SLA settings,
            escalation actions, and reminder addons. These features ensure that
            pending approvals do not stall indefinitely.
        </p>

        <h3>SLA (service-level agreement)</h3>
        <p>
            The SLA field on an Approval step sets a deadline in minutes from
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
                <b>Escalate</b> - the workflow instance transitions to Escalated
                status and the pending step is reassigned to the escalation
                targets you choose in the designer. Use this when a stalled
                approval should move to another approver rather than resolving
                automatically.
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

        <h3>How the scheduler works</h3>
        <p>
            The escalation scheduler runs automatically in the background every
            60 seconds. For each pending Approval step it checks whether the SLA
            deadline has passed and applies the configured escalation action if so.
            No manual action is required - it runs for all active workflow instances
            across your organization.
        </p>

        <h3>Recommendations</h3>
        <ul>
            <li>
                Use <b>Auto-reject</b> escalation for strict compliance scenarios
                where inaction should block the Exchange.
            </li>
            <li>
                Use <b>Auto-approve</b> escalation for low-risk gates where
                silence implies consent.
            </li>
            <li>
                Use <b>Escalate</b> when a stalled approval needs an administrator
                or another escalation target to step in rather than resolving
                automatically.
            </li>
            <li>
                Leave the SLA field empty on steps where no deadline is appropriate.
            </li>
        </ul>
    </>
);
