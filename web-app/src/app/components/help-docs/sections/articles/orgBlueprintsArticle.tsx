
export const orgBlueprintsArticle = (
    <>
        <p id={"org-blueprints-intro-paragraph"}>
            Organization blueprints are shared configurations that Organization
            Admins create and publish for all members of the organization. Members
            see them in the <b id={"org-blueprints-article-b-1"}>Organization</b> tab of the blueprint picker when
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
            <li id={"org-blueprints-article-li-1"}>Open Settings and go to the <b id={"org-blueprints-article-b-2"}>Blueprints</b> tab.</li>
            <li id={"org-blueprints-article-li-2"}>Click the <b id={"org-blueprints-article-b-3"}>Organization</b> inner tab.</li>
            <li id={"org-blueprints-article-li-3"}>Click <b id={"org-blueprints-article-b-4"}>Create</b> in the top-right corner.</li>
            <li id={"org-blueprints-article-li-4"}>Fill in the blueprint editor - the same Details, Documents, Business Fields, Information Request, and
                Permissions tabs as personal blueprints.
            </li>
            <li id={"org-blueprints-article-li-5"}>Click <b id={"org-blueprints-article-b-5"}>Create Blueprint</b>.</li>
        </ol>
        <p id={"org-blueprints-creating-an-organization-blueprint-paragraph"}>
            Newly created organization blueprints start as <b id={"org-blueprints-article-b-6"}>Draft</b> and are
            not yet visible to other members.
        </p>
        <p id={"org-blueprints-creating-an-organization-blueprint-paragraph-2"}>
            Creating, editing, publishing, activating, duplicating into the org, or
            deleting an organization blueprint can require a 6-digit verification code.
        </p>

        <h3 id={"org-blueprints-publishing-and-unpublishing-heading"}>Publishing and unpublishing</h3>
        <p id={"org-blueprints-publishing-and-unpublishing-paragraph"}>
            A blueprint must be <b id={"org-blueprints-article-b-7"}>Published</b> and <b id={"org-blueprints-article-b-8"}>Active</b> to appear in
            the Organization tab of the picker for regular members.
        </p>
        <ul id={"org-blueprints-publishing-and-unpublishing-list"}>
            <li id={"org-blueprints-article-li-6"}>
                Open the three-dot menu on a blueprint and click <b id={"org-blueprints-article-b-9"}>Publish</b> to
                make it available to all members.
            </li>
            <li id={"org-blueprints-article-li-7"}>
                Click <b id={"org-blueprints-article-b-10"}>Unpublish</b> to revert it to Draft status. It will no
                longer appear in the picker for regular members, but Admins can
                still see and edit it.
            </li>
        </ul>

        <h3 id={"org-blueprints-draft-vs-published-heading"}>Draft vs Published</h3>
        <ul id={"org-blueprints-draft-vs-published-list"}>
            <li id={"org-blueprints-article-li-8"}><b id={"org-blueprints-article-b-11"}>Draft</b> - visible only to Organization Admins in Settings. Not shown in the picker to regular
                members.
            </li>
            <li id={"org-blueprints-article-li-9"}><b id={"org-blueprints-article-b-12"}>Published</b> - visible to all active members in the Organization picker tab, provided the blueprint
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
            <li id={"org-blueprints-article-li-10"}><b id={"org-blueprints-article-b-13"}>Edit</b> - modify any field in the blueprint editor.</li>
            <li id={"org-blueprints-article-li-11"}><b id={"org-blueprints-article-b-14"}>Publish / Unpublish</b> - toggle visibility for org members.</li>
            <li id={"org-blueprints-article-li-12"}><b id={"org-blueprints-article-b-15"}>Activate / Deactivate</b> - toggle whether the blueprint is usable.</li>
            <li id={"org-blueprints-article-li-13"}><b id={"org-blueprints-article-b-16"}>Duplicate</b> - creates a copy in your personal blueprints (My Blueprints scope), starting inactive.
            </li>
            <li id={"org-blueprints-article-li-14"}><b id={"org-blueprints-article-b-17"}>Delete</b> - permanently removes the blueprint.</li>
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
