
export const workflowOrgSettingsArticle = (
    <>
        <p id={"workflow-org-settings-article-p-1"}>
            Organization Admins can configure how workflows interact with the
            Exchange lifecycle through organization-level settings.
        </p>

        <h3 id={"workflow-org-settings-article-h3-1"}>Require recipient acceptance</h3>
        <p id={"workflow-org-settings-article-p-2"}>
            The <b id={"workflow-org-settings-article-b-1"}>Require recipient acceptance</b> toggle controls whether every
            Exchange created in your organization must wait for its primary
            recipient to accept before it transitions to Active.
        </p>
        <ul id={"workflow-org-settings-article-ul-1"}>
            <li id={"workflow-org-settings-article-li-1"}>
                <b id={"workflow-org-settings-article-b-2"}>On (default)</b> - newly created Exchanges stay in Draft until
                a recipient accepts. If a workflow definition for the{" "}
                <b id={"workflow-org-settings-article-b-3"}>Acceptance Pending</b> <code id={"workflow-org-settings-article-code-1"}>(exchange.acceptance_pending)</code> trigger is active, that
                workflow gates the acceptance. If no matching definition is active,
                the platform still requires the primary recipient's manual acceptance
                before the Exchange becomes Active.
            </li>
            <li id={"workflow-org-settings-article-li-2"}>
                <b id={"workflow-org-settings-article-b-4"}>Off</b> - most newly created Exchanges advance to Active immediately
                on creation. The <b id={"workflow-org-settings-article-b-5"}>Activated</b> <code id={"workflow-org-settings-article-code-2"}>(exchange.activated)</code> event fires and
                the Exchange bypasses the acceptance stage. Use this for internal
                or automated Exchanges where recipient sign-off is not needed. An Exchange
                sent to a verified person or published group in a Trusted Organization is an
                exception and always requires recipient acceptance.
            </li>
        </ul>
        <p id={"workflow-org-settings-article-p-3"}>
            Changing this setting affects all new Exchanges created after the
            change. Exchanges that are already in Draft or Active are not affected.
        </p>
        <p id={"workflow-org-settings-article-p-4"}>
            Only the primary recipient can accept or reject an Exchange. Additional
            participants cannot make that decision. A trusted additional participant instead decides
            only their own pending access invitation. When the primary recipient is a
            group, an active group Owner or Manager makes the decision for the group.
            For a published group in a Trusted Organization, the current trust relationship,
            directional policies, group publication, and group role must also remain eligible.
        </p>

        <h3 id={"workflow-org-settings-article-h3-2"}>Where to find this setting</h3>
        <ol id={"workflow-org-settings-article-ol-1"}>
            <li id={"workflow-org-settings-article-li-3"}>Open Settings.</li>
            <li id={"workflow-org-settings-article-li-4"}>Go to the <b id={"workflow-org-settings-article-b-6"}>Organization</b> tab.</li>
            <li id={"workflow-org-settings-article-li-5"}>
                Find the <b id={"workflow-org-settings-article-b-7"}>Require recipient acceptance</b> toggle in the
                Exchange settings section.
            </li>
            <li id={"workflow-org-settings-article-li-6"}>Toggle it on or off and save.</li>
        </ol>

        <h3 id={"workflow-org-settings-article-h3-3"}>How this interacts with workflows</h3>
        <p id={"workflow-org-settings-article-p-5"}>
            The setting and the workflow engine work together:
        </p>
        <ul id={"workflow-org-settings-article-ul-2"}>
            <li id={"workflow-org-settings-article-li-7"}>
                When the setting is <b id={"workflow-org-settings-article-b-8"}>on</b> and a matching{" "}
                <b id={"workflow-org-settings-article-b-9"}>Acceptance Pending</b> <code id={"workflow-org-settings-article-code-3"}>(exchange.acceptance_pending)</code> workflow is active, the
                recipient's "Accept" action is routed through the workflow engine.
                The workflow step records the decision and, once the quorum is met,
                advances the Exchange to Active.
            </li>
            <li id={"workflow-org-settings-article-li-8"}>
                When the setting is <b id={"workflow-org-settings-article-b-10"}>on</b> but no matching workflow is active,
                the platform still guards the transition. A direct recipient
                acceptance still moves the Exchange to Active, just without a
                workflow gate.
            </li>
            <li id={"workflow-org-settings-article-li-9"}>
                When the setting is <b id={"workflow-org-settings-article-b-11"}>off</b>, the{" "}
                <b id={"workflow-org-settings-article-b-12"}>Acceptance Pending</b> <code id={"workflow-org-settings-article-code-4"}>(exchange.acceptance_pending)</code> trigger never fires and
                acceptance workflow definitions for that trigger will not run for ordinary
                recipients. Trusted Organization person and group recipients still require a
                direct recipient acceptance decision.
            </li>
        </ul>

        <h3 id={"workflow-org-settings-article-h3-4"}>No-account (OTP) recipients</h3>
        <p id={"workflow-org-settings-article-p-6"}>
            Recipients who do not have a DocuHyphen account and accept via a
            one-time password link are also routed through the workflow engine when
            the setting is on. After OTP verification, their acceptance is recorded
            as a workflow decision, ensuring consistent audit records regardless of
            recipient account type. Immediately before acceptance, DocuHyphen resolves
            the invitation email again and checks the sender's current external-customer
            or organization-sharing policy. The same check runs when an invited recipient
            accepts after signing in.
        </p>

        <h3 id={"workflow-org-settings-article-h3-5"}>Related articles</h3>
        <ul id={"workflow-org-settings-article-ul-3"}>
            <li id={"workflow-org-settings-article-li-10"}>
                <a id={"workflow-org-settings-article-a-1"}
                   href="#"
                   data-help-article="trigger-events">
                    <b id={"workflow-org-settings-article-b-13"}>Trigger events</b>
                </a>{" "}
                - how the Acceptance Pending trigger works.
            </li>
            <li id={"workflow-org-settings-article-li-11"}>
                <a id={"workflow-org-settings-article-a-2"}
                   href="#"
                   data-help-article="workflow-overview">
                    <b id={"workflow-org-settings-article-b-14"}>Workflow overview</b>
                </a>{" "}
                - the full Exchange lifecycle with workflows.
            </li>
        </ul>
    </>
);
