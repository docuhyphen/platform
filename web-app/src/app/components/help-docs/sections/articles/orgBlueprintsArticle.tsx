import React from "react";

export const orgBlueprintsArticle = (
    <>
        <p id={"org-blueprints-intro-paragraph"}>
            Organization blueprints are shared configurations that Organization
            Admins create and publish for all members of the organization. Members
            see them in the <b>Organization</b> tab of the blueprint picker when
            starting an Exchange.
        </p>

        <h3 id={"org-blueprints-who-can-manage-organization-heading"}>Who can manage organization blueprints</h3>
        <p id={"org-blueprints-who-can-manage-organization-paragraph"}>
            Only Organization Admins can create, edit, publish, and delete
            organization blueprints. An App Admin can manage them only when the same
            account also holds the required role in that organization. Regular members
            can view and use published blueprints but cannot manage them.
        </p>

        <h3 id={"org-blueprints-creating-an-organization-blueprint-heading"}>Creating an organization blueprint</h3>
        <ol id={"org-blueprints-creating-an-organization-blueprint-list"}>
            <li>Open Settings and go to the <b>Blueprints</b> tab.</li>
            <li>Click the <b>Organization</b> inner tab.</li>
            <li>Click <b>Create</b> in the top-right corner.</li>
            <li>Fill in the blueprint editor - the same Details, Documents, Business Fields, Information Request, and
                Permissions tabs as personal blueprints.
            </li>
            <li>Click <b>Create Blueprint</b>.</li>
        </ol>
        <p id={"org-blueprints-creating-an-organization-blueprint-paragraph"}>
            Newly created organization blueprints start as <b>Draft</b> and are
            not yet visible to other members.
        </p>
        <p id={"org-blueprints-creating-an-organization-blueprint-paragraph-2"}>
            Creating, editing, publishing, activating, duplicating into the org, or
            deleting an organization blueprint can require a 6-digit verification code.
        </p>

        <h3 id={"org-blueprints-publishing-and-unpublishing-heading"}>Publishing and unpublishing</h3>
        <p id={"org-blueprints-publishing-and-unpublishing-paragraph"}>
            A blueprint must be <b>Published</b> and <b>Active</b> to appear in
            the Organization tab of the picker for regular members.
        </p>
        <ul id={"org-blueprints-publishing-and-unpublishing-list"}>
            <li>
                Open the three-dot menu on a blueprint and click <b>Publish</b> to
                make it available to all members.
            </li>
            <li>
                Click <b>Unpublish</b> to revert it to Draft status. It will no
                longer appear in the picker for regular members, but Admins can
                still see and edit it.
            </li>
        </ul>

        <h3 id={"org-blueprints-draft-vs-published-heading"}>Draft vs Published</h3>
        <ul id={"org-blueprints-draft-vs-published-list"}>
            <li><b>Draft</b> - visible only to Organization Admins in Settings. Not shown in the picker to regular
                members.
            </li>
            <li><b>Published</b> - visible to all active members in the Organization picker tab, provided the blueprint
                is also Active.
            </li>
        </ul>

        <h3 id={"org-blueprints-active-vs-inactive-heading"}>Active vs Inactive</h3>
        <p id={"org-blueprints-active-vs-inactive-paragraph"}>
            Both Published and Active must be true for a blueprint to appear in the
            picker. Deactivating a blueprint hides it from the picker without
            unpublishing it - useful for temporarily suspending a blueprint while
            keeping it in a published state.
        </p>

        <h3 id={"org-blueprints-managing-organization-blueprints-heading"}>Managing organization blueprints</h3>
        <p id={"org-blueprints-managing-organization-blueprints-paragraph"}>
            Each blueprint in the Organization tab has a three-dot menu with:
        </p>
        <ul id={"org-blueprints-managing-organization-blueprints-list"}>
            <li><b>Edit</b> - modify any field in the blueprint editor.</li>
            <li><b>Publish / Unpublish</b> - toggle visibility for org members.</li>
            <li><b>Activate / Deactivate</b> - toggle whether the blueprint is usable.</li>
            <li><b>Duplicate</b> - creates a copy in your personal blueprints (My Blueprints scope), starting inactive.
            </li>
            <li><b>Delete</b> - permanently removes the blueprint.</li>
        </ul>

        <h3 id={"org-blueprints-org-admin-visibility-heading"}>Org admin visibility</h3>
        <p id={"org-blueprints-org-admin-visibility-paragraph"}>
            Admins see all organization blueprints in Settings regardless of their
            Published or Active state. Regular members only see blueprints that are
            both Published and Active in the picker. Admins have zero visibility
            into other users' personal blueprints.
        </p>
    </>
);
