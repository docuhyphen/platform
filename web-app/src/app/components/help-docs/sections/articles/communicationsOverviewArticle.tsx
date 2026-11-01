
export const communicationsOverviewArticle = (
    <>
        <p id={"communications-overview-article-p-1"}>
            Communications are reusable message definitions that can be attached to
            workflow Notification steps. Each communication stores a subject line, a
            body written in Markdown, and optional metadata. When a Notification step
            fires, the engine looks up the linked communication, interpolates any
            variable tokens, and delivers the result as an email and an in-app
            notification to the step's assignees.
        </p>

        <h3 id={"communications-overview-article-h3-1"}>Scopes</h3>
        <p id={"communications-overview-article-p-2"}>
            Every communication belongs to one of three scopes that control who can
            see and use it:
        </p>
        <ul id={"communications-overview-article-ul-1"}>
            <li id={"communications-overview-article-li-1"}>
                <b id={"communications-overview-article-b-1"}>Personal</b> - visible only to you. Use these for drafts or
                one-off messages you have not yet shared with your team. Personal
                communications cannot be published.
            </li>
            <li id={"communications-overview-article-li-2"}>
                <b id={"communications-overview-article-b-2"}>Organization</b> - visible to all members of your organization
                (active and published ones). Organization Admins manage and publish
                these. Draft org communications are hidden from regular members
                until published.
            </li>
            <li id={"communications-overview-article-li-3"}>
                <b id={"communications-overview-article-b-3"}>Platform</b> - provided by DocuHyphen. App Admins manage these
                in <b id={"communications-overview-article-b-4"}>Platform Administration - Platform Content</b>. Other users
                can read them and clone one to create an editable personal copy.
            </li>
        </ul>

        <h3 id={"communications-overview-article-h3-2"}>Variable tokens</h3>
        <p id={"communications-overview-article-p-3"}>
            The subject and body both support <code id={"communications-overview-article-code-1"}>{"{{TOKEN}}"}</code> syntax.
            Tokens are resolved at send time against the triggering Exchange, the
            initiator's organization, and any custom variables you have defined.
            Use <code id={"communications-overview-article-code-2"}>{"{{SEQ:KEY}}"}</code> tokens to insert auto-incrementing
            sequence values (only valid in Exchange name fields, not in
            communications).
        </p>
        <p id={"communications-overview-article-p-4"}>
            Available tokens are listed in the editor's token badge bar and in the
            Settings {">"} Variables section. The platform Communication editor
            does not load organization or personal variable suggestions.
        </p>

        <h3 id={"communications-overview-article-h3-3"}>Draft and published states</h3>
        <p id={"communications-overview-article-p-5"}>
            Organization-scoped communications start as drafts. A draft is only
            visible to org admins in the Communications settings tab. Once an
            org admin publishes a communication it becomes visible to all org
            members and can be selected in the workflow designer.
        </p>

        <h3 id={"communications-overview-article-h3-4"}>Active and inactive</h3>
        <p id={"communications-overview-article-p-6"}>
            All communications have an active/inactive toggle. Only active
            communications are delivered by the workflow engine. Deactivating a
            communication causes the Notification step to fall back to the default
            system message.
        </p>

        <h3 id={"communications-overview-article-h3-5"}>Learn more</h3>
        <ul id={"communications-overview-article-ul-2"}>
            <li id={"communications-overview-article-li-4"}>
                <a id={"communications-overview-article-a-1"}
                   href="#"
                   data-help-article="managing-communications">
                    <b id={"communications-overview-article-b-5"}>Managing communications</b>
                </a>{" "}
                - create, edit, publish, and organise your communications.
            </li>
            <li id={"communications-overview-article-li-5"}>
                <a id={"communications-overview-article-a-2"}
                   href="#"
                   data-help-article="communications-in-workflows">
                    <b id={"communications-overview-article-b-6"}>Using communications in workflows</b>
                </a>{" "}
                - attach a communication to a Notification step.
            </li>
        </ul>
    </>
);
