import React from "react";

export const workflowDiagramPreviewArticle = (
    <>
        <p>
            Two read-only diagram surfaces visualize a workflow: the{" "}
            <b>Diagram</b> in the Workflow Designer and the <b>Diagram</b> view on
            an Exchange's Workflow tab. Both are read-only for workflow data. You
            cannot add, edit, connect, or
            delete anything from a diagram, but you can drag a node to reposition it
            for easier viewing; this only changes the on-screen layout and never
            changes the workflow. The Form pane stays the only workflow editor
            and the Timeline stays the detailed audit view.
        </p>

        <h3>Preview in the Workflow Designer</h3>
        <p>
            Open the designer from Settings. The live Diagram is always visible on
            the left at about 60% width, including unsaved Form changes, while the
            editable Form stays on the right at about 40% width. The Diagram fills
            the available height while the Form pane scrolls. On narrow screens
            the two sections stack.
        </p>
        <p>
            A bottom-right orientation icon switches between horizontal and
            vertical layouts. It starts in its pressed vertical state so the
            workflow fits the split layout.
        </p>
        <p>
            An <b>Applicability</b> summary line above the diagram states whether
            the workflow always runs or only when field conditions match. The
            diagram itself is read-only: pan, zoom, drag a node to reposition it,
            or select a node to read its details, but nothing there changes the
            workflow.
        </p>

        <h3>Reading the diagram</h3>
        <ul>
            <li><b>Start</b> and <b>End</b> mark where the workflow begins and terminates.</li>
            <li>Each step is a node labeled by its type.</li>
            <li>
                Webhook delivery is shown as its own distinct node type, never as a
                generic Action node.
            </li>
            <li>
                Connectors are labeled by outcome: <b>On Approve</b>, <b>On Reject</b>,
                <b>On True</b>, <b>On False</b>, or <b>Continue</b> for a Wait for
                Counterparty Clearance step. Plain sequence connectors are unlabeled.
            </li>
            <li>A legend below the canvas explains the node and connector meanings.</li>
        </ul>

        <h3>Diagram notices</h3>
        <p>
            The Diagram reports structural notices such as an outcome pointing to a
            missing step, an unreachable step, or a branch with no path to End.
            These notices are <b>informational only</b>. They do not block saving,
            and the Save rules are unchanged.
        </p>

        <h3>Diagram view on an Exchange</h3>
        <ol>
            <li>Open an Exchange and select the <b>Workflow</b> tab.</li>
            <li>
                Use the circular menu control at the top right to choose{" "}
                <b>Timeline</b>, <b>Diagram</b>, or <b>Both</b> for the whole tab.
                Timeline is the default. <b>Both</b> splits the tab into a detail
                column on the left (about 40% width, with the summary, any action
                required, and the Timeline) and a live diagram preview on the right
                (about 60% width), so you can read step history and the diagram at
                the same time. On narrow screens the two columns stack instead of
                sitting side by side.
            </li>
        </ol>
        <p>
            The control changes every workflow shown in the tab at once. The tab
            header stays fixed while the content below it scrolls. Each workflow
            diagram also has its own bottom-right orientation icon. Exchange
            diagrams open in vertical mode by default, and pressing the icon
            switches that diagram between vertical and horizontal layout. In Diagram
            (or the diagram half of Both) each workflow instance shows the same
            frozen structure it started with, with runtime state layered on top.
        </p>
        <ul>
            <li>
                <b>Node states:</b> Not reached, In progress, Awaiting counterparty,
                Completed, Skipped, Rejected, Escalated, Cancelled, Failed, Paused,
                and Unknown status.
            </li>
            <li>
                <b>Traversed route:</b> the connector actually taken between steps is
                emphasized; other connectors are dimmed. Traversed routes come from
                recorded execution data and are never guessed from step order.
            </li>
        </ul>
        <p>
            A very large workflow shows a notice suggesting the Timeline for easier
            reading. The diagram is still shown.
        </p>

        <h3>When a diagram cannot be shown</h3>
        <p>
            If a diagram fails to render, that workflow section shows a short message
            with a <b>View timeline</b> button and switches you to the Timeline view.
            Other workflows and the Timeline are unaffected.
        </p>

        <h3>Diagram compared with Timeline</h3>
        <p>
            The Diagram is a compact visual summary. The{" "}
            <a href="#"
               data-help-article="workflow-activity-monitoring">
                Timeline
            </a>{" "}
            remains the full record of assignees, decisions, timestamps, SLA
            deadlines, and statuses. Use the Timeline for detailed audit review.
        </p>
    </>
);
