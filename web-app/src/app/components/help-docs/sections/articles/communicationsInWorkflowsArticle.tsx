
export const communicationsInWorkflowsArticle = (
    <>
        <p id={"communications-in-workflows-intro-paragraph"}>
            A Notification step in a workflow can use a communication to deliver a
            fully customized subject and body instead of the default system message.
            The workflow engine resolves the linked communication's variable tokens
            against the triggering Exchange at send time and delivers the result
            by email and in-app notification to every assignee on the step.
        </p>

        <h3 id={"communications-in-workflows-linking-a-communication-to-heading"}>Linking a communication to a
            Notification step</h3>
        <ol id={"communications-in-workflows-linking-a-communication-to-list"}>
            <li id={"communications-in-workflows-article-li-1"}>
                Open the workflow in the <b id={"communications-in-workflows-article-b-1"}>Workflow Designer</b> (Settings {">"} Workflows {">"} Edit).
            </li>
            <li id={"communications-in-workflows-article-li-2"}>
                Find and click the Notification step card (or add one by clicking{" "}
                <b id={"communications-in-workflows-article-b-2"}>Add Step</b>, click its card, and select <b id={"communications-in-workflows-article-b-3"}>Notification</b>).
            </li>
            <li id={"communications-in-workflows-article-li-3"}>
                Click <b id={"communications-in-workflows-article-b-4"}>Select communication...</b>. The Communication Picker dialog
                opens.
            </li>
            <li id={"communications-in-workflows-article-li-4"}>
                Browse <b id={"communications-in-workflows-article-b-5"}>My Communications</b>, <b id={"communications-in-workflows-article-b-6"}>Organization</b>, or{" "}
                <b id={"communications-in-workflows-article-b-7"}>Platform</b> tabs and search by name or subject.
            </li>
            <li id={"communications-in-workflows-article-li-5"}>
                Click a communication to select it (highlighted in blue), then click{" "}
                <b id={"communications-in-workflows-article-b-8"}>Select Communication</b>.
            </li>
            <li id={"communications-in-workflows-article-li-6"}>
                The step now shows the communication's name. Add assignees as usual.
            </li>
            <li id={"communications-in-workflows-article-li-7"}>Click <b id={"communications-in-workflows-article-b-9"}>Save workflow</b>.</li>
        </ol>

        <h3 id={"communications-in-workflows-clearing-a-linked-communication-heading"}>Clearing a linked
            communication</h3>
        <p id={"communications-in-workflows-clearing-a-linked-communication-paragraph"}>
            Click <b id={"communications-in-workflows-article-b-10"}>Clear</b> next to the selected communication name. The step
            reverts to the default system message.
        </p>

        <h3 id={"communications-in-workflows-fallback-behaviour-heading"}>Fallback behaviour</h3>
        <p id={"communications-in-workflows-fallback-behaviour-paragraph"}>
            The engine falls back to the generic default message when any of the
            following is true:
        </p>
        <ul id={"communications-in-workflows-fallback-behaviour-list"}>
            <li id={"communications-in-workflows-article-li-8"}>No communication is linked to the step.</li>
            <li id={"communications-in-workflows-article-li-9"}>The linked communication has been deleted.</li>
            <li id={"communications-in-workflows-article-li-10"}>The linked communication is inactive.</li>
            <li id={"communications-in-workflows-article-li-11"}>Token resolution fails (for example, a required variable is missing).</li>
        </ul>
        <p id={"communications-in-workflows-fallback-behaviour-paragraph-2"}>
            Fallback does not fail the workflow; the step completes and the workflow
            advances normally.
        </p>

        <h3 id={"communications-in-workflows-token-resolution-in-notification-heading"}>Token resolution in Notification
            steps</h3>
        <p id={"communications-in-workflows-token-resolution-in-notification-paragraph"}>
            The engine resolves tokens using the workflow instance's subject data
            (Exchange fields, initiator, organization) merged with any custom
            variables. The initiator's user and organization context are used as the
            resolution base. Overrides passed from the trigger event's subject data
            take precedence over defaults.
        </p>

        <h3 id={"communications-in-workflows-which-communications-are-selectable-heading"}>Which communications are
            selectable?</h3>
        <p id={"communications-in-workflows-which-communications-are-selectable-paragraph"}>
            The Communication Picker shows only active communications. The scopes
            available depend on your workflow automation access and role:
        </p>
        <ul id={"communications-in-workflows-which-communications-are-selectable-list"}>
            <li id={"communications-in-workflows-article-li-12"}><b id={"communications-in-workflows-article-b-11"}>Workflow automation users</b> - their own reusable communications.</li>
            <li id={"communications-in-workflows-article-li-13"}><b id={"communications-in-workflows-article-b-12"}>Organization workflow users</b> - published Org communications from their org.</li>
            <li id={"communications-in-workflows-article-li-14"}><b id={"communications-in-workflows-article-b-13"}>Workflow automation users</b> - Platform communications.</li>
        </ul>
        <p id={"communications-in-workflows-which-communications-are-selectable-paragraph-2"}>
            Draft org communications are not shown in the picker until they are
            published by an Organization Admin.
        </p>

        <h3 id={"communications-in-workflows-learn-more-heading"}>Learn more</h3>
        <ul id={"communications-in-workflows-learn-more-list"}>
            <li id={"communications-in-workflows-article-li-15"}>
                <a id={"communications-in-workflows-learn-more-link"}
                   href="#"
                   data-help-article="communications-overview">
                    <b id={"communications-in-workflows-article-b-14"}>Communications overview</b>
                </a>{" "}
                - scopes, tokens, draft/publish lifecycle.
            </li>
            <li id={"communications-in-workflows-article-li-16"}>
                <a id={"communications-in-workflows-learn-more-link-2"}
                   href="#"
                   data-help-article="step-types">
                    <b id={"communications-in-workflows-article-b-15"}>Step types explained</b>
                </a>{" "}
                - full reference for Notification and other step types.
            </li>
        </ul>
    </>
);
