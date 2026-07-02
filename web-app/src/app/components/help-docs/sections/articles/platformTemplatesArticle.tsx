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
                Scroll down to the <b>Platform Templates</b> section below your
                organization's workflows.
            </li>
            <li>
                Browse by name, description, or tags. Examples of
                available tags: Legal, Finance, Healthcare, HR, Logistics.
            </li>
        </ol>

        <h3>Adding a template to your organization</h3>
        <ol>
            <li>
                Find the template you want and click <b>Add to my workflows</b>.
            </li>
            <li>
                A copy of the template is cloned into your organization. The copy
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
                All hardcoded principal UUIDs in assignee entries are replaced with
                portable ORGANIZATION_ROLE placeholders. Review and adjust these after cloning
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
                Find the cloned workflow in the <b>My Workflows</b> section and
                click <b>Edit</b>.
            </li>
            <li>
                Review all assignee entries. The replaced ORGANIZATION_ROLE placeholders may
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
            <b>Duplicate</b> row action in the My Workflows list. The duplicate
            starts inactive and is a full independent copy that you can edit
            without affecting the original.
        </p>

        <h3>Tags</h3>
        <p>
            Tags are free-form strings, not a fixed list. You can add any tag when
            creating or editing a workflow definition, which means new industries
            are supported without any platform updates. The Platform Templates
            section uses these tags to help you filter relevant templates.
        </p>
    </>
);
