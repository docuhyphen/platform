import React from "react";

export const communicationsOverviewArticle = (
    <>
        <p>
            Communications are reusable message definitions that can be attached to
            workflow Notification steps. Each communication stores a subject line, a
            body written in Markdown, and optional metadata. When a Notification step
            fires, the engine looks up the linked communication, interpolates any
            variable tokens, and delivers the result as an email and an in-app
            notification to the step's assignees.
        </p>

        <h3>Scopes</h3>
        <p>
            Every communication belongs to one of three scopes that control who can
            see and use it:
        </p>
        <ul>
            <li>
                <b>Personal</b> - visible only to you. Use these for drafts or
                one-off messages you have not yet shared with your team. Personal
                communications cannot be published.
            </li>
            <li>
                <b>Organization</b> - visible to all members of your organization
                (active and published ones). Organization Admins manage and publish
                these. Draft org communications are hidden from regular members
                until published.
            </li>
            <li>
                <b>Platform</b> - provided by DocuHyphen. Read-only; you cannot
                edit them directly. Clone a platform communication to create your
                own editable personal copy.
            </li>
        </ul>

        <h3>Variable tokens</h3>
        <p>
            The subject and body both support <code>{"{{TOKEN}}"}</code> syntax.
            Tokens are resolved at send time against the triggering Exchange, the
            initiator's organization, and any custom variables you have defined.
            Use <code>{"{{SEQ:KEY}}"}</code> tokens to insert auto-incrementing
            sequence values (only valid in Exchange name fields, not in
            communications).
        </p>
        <p>
            Available tokens are listed in the editor's token badge bar and in the
            Settings {">"} Variables section.
        </p>

        <h3>Draft and published states</h3>
        <p>
            Organization-scoped communications start as drafts. A draft is only
            visible to org admins in the Communications settings tab. Once an
            org admin publishes a communication it becomes visible to all org
            members and can be selected in the workflow designer.
        </p>

        <h3>Active and inactive</h3>
        <p>
            All communications have an active/inactive toggle. Only active
            communications are delivered by the workflow engine. Deactivating a
            communication causes the Notification step to fall back to the default
            system message.
        </p>

        <h3>Learn more</h3>
        <ul>
            <li>
                <a href="#"
                   data-help-article="managing-communications">
                    <b>Managing communications</b>
                </a>{" "}
                - create, edit, publish, and organise your communications.
            </li>
            <li>
                <a href="#"
                   data-help-article="communications-in-workflows">
                    <b>Using communications in workflows</b>
                </a>{" "}
                - attach a communication to a Notification step.
            </li>
        </ul>
    </>
);
