
export const platformTemplatesArticle = (
    <>
        <p id={"platform-templates-article-p-1"}>
            Platform templates are workflow definitions provided by DocuHyphen that
            cover common use cases across industries. They are available to Business
            organizations with workflow automation and can be cloned and customized to fit their
            processes.
        </p>

        <h3 id={"platform-templates-article-h3-1"}>Browsing platform templates</h3>
        <ol id={"platform-templates-article-ol-1"}>
            <li id={"platform-templates-article-li-1"}>Open Settings and go to the <b id={"platform-templates-article-b-1"}>Workflows</b> tab.</li>
            <li id={"platform-templates-article-li-2"}>
                Select the <b id={"platform-templates-article-b-2"}>Platform</b> sub-tab.
            </li>
            <li id={"platform-templates-article-li-3"}>
                Search by name, summary, trigger, or tag. Use the tag filter,
                sorting menu, and card or table layout control to narrow and
                arrange the results.
            </li>
        </ol>

        <h3 id={"platform-templates-article-h3-2"}>Managing platform templates</h3>
        <p id={"platform-templates-article-p-2"}>
            App Admins create, edit, publish, activate, and delete platform
            workflow templates in <b id={"platform-templates-article-b-3"}>Platform Administration - Platform Content</b>.
            This workspace is fixed to platform scope and does not load
            organization workflows, directory users, groups, or organization
            communications.
        </p>

        <h3 id={"platform-templates-article-h3-3"}>Adding a template to your workflows</h3>
        <ol id={"platform-templates-article-ol-2"}>
            <li id={"platform-templates-article-li-4"}>
                Find the template you want and click <b id={"platform-templates-article-b-4"}>Add to my workflows</b>.
            </li>
            <li id={"platform-templates-article-li-5"}>
                A copy of the template is cloned into your personal workflow collection. The copy
                is independent from the original and can be edited freely.
            </li>
            <li id={"platform-templates-article-li-6"}>
                The cloned definition starts <b id={"platform-templates-article-b-5"}>inactive</b>. Review it in the
                designer and activate it when you are ready.
            </li>
        </ol>

        <h3 id={"platform-templates-article-h3-4"}>What happens when a template is cloned</h3>
        <p id={"platform-templates-article-p-3"}>
            When you clone a platform template, the engine performs the following
            automatically:
        </p>
        <ul id={"platform-templates-article-ul-1"}>
            <li id={"platform-templates-article-li-7"}>
                All hardcoded Specific User assignees are replaced with
                portable Organization Role placeholders. Review and adjust these after cloning
                to ensure they target the correct roles for your organization.
            </li>
            <li id={"platform-templates-article-li-8"}>
                The cloned definition records the source template ID so you can
                trace which template it came from.
            </li>
            <li id={"platform-templates-article-li-9"}>
                The <code id={"platform-templates-article-code-1"}>isTemplate</code> flag is set to false on the clone,
                marking it as an org-owned definition rather than a platform
                template.
            </li>
        </ul>

        <h3 id={"platform-templates-article-h3-5"}>Customizing a cloned template</h3>
        <ol id={"platform-templates-article-ol-3"}>
            <li id={"platform-templates-article-li-10"}>
                Find the cloned workflow in the <b id={"platform-templates-article-b-6"}>My Workflows</b> sub-tab and
                click <b id={"platform-templates-article-b-7"}>Edit</b>.
            </li>
            <li id={"platform-templates-article-li-11"}>
                Review all assignee entries. The replaced Organization Role placeholders may
                not match your intended approvers. Update them to the correct
                roles, groups, or individuals.
            </li>
            <li id={"platform-templates-article-li-12"}>
                Adjust SLA deadlines, escalation actions, and reminder addons to
                match your team's expected response times.
            </li>
            <li id={"platform-templates-article-li-13"}>
                Update the name and summary to reflect your org's specific process.
            </li>
            <li id={"platform-templates-article-li-14"}>
                Toggle <b id={"platform-templates-article-b-8"}>Active</b> on and click <b id={"platform-templates-article-b-9"}>Save workflow</b>.
            </li>
        </ol>

        <h3 id={"platform-templates-article-h3-6"}>Duplicating your own workflows</h3>
        <p id={"platform-templates-article-p-4"}>
            You can also duplicate your own org's workflows using the{" "}
            <b id={"platform-templates-article-b-10"}>Duplicate</b> action in the My Workflows list. The duplicate
            starts inactive and is a full independent copy that you can edit
            without affecting the original.
        </p>

        <h3 id={"platform-templates-article-h3-7"}>Tags</h3>
        <p id={"platform-templates-article-p-5"}>
            Tags are free-form strings, not a fixed list. You can add any tag when
            creating or editing a workflow definition, which means new industries
            are supported without any platform updates. The Platform
            sub-tab uses these tags to help you filter relevant templates.
        </p>
    </>
);
