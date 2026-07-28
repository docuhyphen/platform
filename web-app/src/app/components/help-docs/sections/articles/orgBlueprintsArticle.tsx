import React from "react";

export const orgBlueprintsArticle = (
    <>
        <p>
            Organization blueprints are shared configurations that Organization
            Admins create and publish for all members of the organization. Members
            see them in the <b>Organization</b> tab of the blueprint picker when
            starting an Exchange.
        </p>

        <h3>Who can manage organization blueprints</h3>
        <p>
            Only Organization Admins can create, edit, publish, and delete
            organization blueprints. An App Admin can manage them only when the same
            account also holds the required role in that organization. Regular members
            can view and use published blueprints but cannot manage them.
        </p>

        <h3>Creating an organization blueprint</h3>
        <ol>
            <li>Open Settings and go to the <b>Blueprints</b> tab.</li>
            <li>Click the <b>Organization</b> inner tab.</li>
            <li>Click <b>Create</b> in the top-right corner.</li>
            <li>Fill in the blueprint editor - the same Details, Documents, Business Fields, and Permissions tabs as personal blueprints.</li>
            <li>Click <b>Create Blueprint</b>.</li>
        </ol>
        <p>
            Newly created organization blueprints start as <b>Draft</b> and are
            not yet visible to other members.
        </p>
        <p>
            Creating, editing, publishing, activating, duplicating into the org, or
            deleting an organization blueprint can require a 6-digit verification code.
        </p>

        <h3>Publishing and unpublishing</h3>
        <p>
            A blueprint must be <b>Published</b> and <b>Active</b> to appear in
            the Organization tab of the picker for regular members.
        </p>
        <ul>
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

        <h3>Draft vs Published</h3>
        <ul>
            <li><b>Draft</b> - visible only to Organization Admins in Settings. Not shown in the picker to regular members.</li>
            <li><b>Published</b> - visible to all active members in the Organization picker tab, provided the blueprint is also Active.</li>
        </ul>

        <h3>Active vs Inactive</h3>
        <p>
            Both Published and Active must be true for a blueprint to appear in the
            picker. Deactivating a blueprint hides it from the picker without
            unpublishing it - useful for temporarily suspending a blueprint while
            keeping it in a published state.
        </p>

        <h3>Managing organization blueprints</h3>
        <p>
            Each blueprint in the Organization tab has a three-dot menu with:
        </p>
        <ul>
            <li><b>Edit</b> - modify any field in the blueprint editor.</li>
            <li><b>Publish / Unpublish</b> - toggle visibility for org members.</li>
            <li><b>Activate / Deactivate</b> - toggle whether the blueprint is usable.</li>
            <li><b>Duplicate</b> - creates a copy in your personal blueprints (My Blueprints scope), starting inactive.</li>
            <li><b>Delete</b> - permanently removes the blueprint.</li>
        </ul>

        <h3>Org admin visibility</h3>
        <p>
            Admins see all organization blueprints in Settings regardless of their
            Published or Active state. Regular members only see blueprints that are
            both Published and Active in the picker. Admins have zero visibility
            into other users' personal blueprints.
        </p>
    </>
);
