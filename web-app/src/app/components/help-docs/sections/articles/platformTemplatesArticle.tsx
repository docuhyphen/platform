import React from "react";

export const platformTemplatesArticle = (
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
                Select the <b>Platform</b> sub-tab.
            </li>
            <li>
                Search by name, summary, trigger, or tag. Use the tag filter,
                sorting menu, and card or table layout control to narrow and
                arrange the results.
            </li>
        </ol>

        <h3>Managing platform templates</h3>
        <p>
            App Admins create, edit, publish, activate, and delete platform
            workflow templates in <b>Platform Administration - Platform Content</b>.
            This workspace is fixed to platform scope and does not load
            organization workflows, directory users, groups, or organization
            communications.
        </p>

        <h3>Adding a template to your workflows</h3>
        <ol>
            <li>
                Find the template you want and click <b>Add to my workflows</b>.
            </li>
            <li>
                A copy of the template is cloned into your personal workflow collection. The copy
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
                All hardcoded Specific User assignees are replaced with
                portable Organization Role placeholders. Review and adjust these after cloning
                to ensure they target the correct roles for your organization.
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
                Find the cloned workflow in the <b>My Workflows</b> sub-tab and
                click <b>Edit</b>.
            </li>
            <li>
                Review all assignee entries. The replaced Organization Role placeholders may
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
            <b>Duplicate</b> action in the My Workflows list. The duplicate
            starts inactive and is a full independent copy that you can edit
            without affecting the original.
        </p>

        <h3>Tags</h3>
        <p>
            Tags are free-form strings, not a fixed list. You can add any tag when
            creating or editing a workflow definition, which means new industries
            are supported without any platform updates. The Platform
            sub-tab uses these tags to help you filter relevant templates.
        </p>
    </>
);
