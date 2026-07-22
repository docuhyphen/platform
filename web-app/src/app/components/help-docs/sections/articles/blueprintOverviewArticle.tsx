import React from "react";

export const blueprintOverviewArticle = (
    <>
        <p>
            Blueprints are saved Exchange configurations. Instead of filling in
            documents, permissions, recipient settings, and participants from scratch
            every time, you save a blueprint once and reuse it whenever you start a
            new Exchange.
        </p>

        <h3>What a blueprint stores</h3>
        <ul>
            <li><b>Exchange name and description</b> - a pre-filled name and description for the Exchange.</li>
            <li><b>Initial message</b> - the message shown to the recipient when the Exchange is sent.</li>
            <li><b>Documents</b> - a list of document slots, each with a title, an optional upload-type restriction, and an optional required flag.</li>
            <li><b>Permissions</b> - the full set of permission toggles: allow document addition, deletion, download, update, and upload.</li>
            <li><b>Recipient sign-in requirement</b> - whether recipients must sign in to access the Exchange.</li>
            <li><b>Participants</b> - internal participants added to the Exchange, each with a role.</li>
        </ul>
        <p>
            Exchange name, description, and document titles can include variable tokens
            (<code>{"{{TOKEN}}"}</code>) that are resolved at Exchange creation time. See{" "}
            <a href="#"
               data-help-article="using-variable-tokens">
                Using variable tokens
            </a>{" "}
            for details.
        </p>
        <p>
            Blueprints do <b>not</b> store the recipient's identity (email, name, or
            account). You always choose who to send the Exchange to at initiation time.
        </p>

        <h3>Three blueprint scopes</h3>
        <ul>
            <li>
                <b>My Blueprints (Personal)</b> - visible only to you. Any user can
                create personal blueprints. Use these for your own recurring workflows.
            </li>
            <li>
                <b>Organization</b> - shared across your organization. Only
                Organization Admins can create and publish them. A blueprint must be
                both Active and Published before it appears in the picker for other
                members.
            </li>
            <li>
                <b>Platform</b> - provided by DocuHyphen. Available to all users
                across all organizations. Only App Admins can create platform
                blueprints. Any user can clone a platform blueprint into their own
                personal blueprints.
            </li>
        </ul>

        <h3>Where blueprints appear</h3>
        <ul>
            <li>
                <b>Exchange initiation picker</b> - when starting a new Exchange,
                click <b>From Blueprint</b> in the start menu to open the three-tab
                picker and select a blueprint.
            </li>
            <li>
                <b>Settings - Blueprints</b> - manage all your blueprints across
                the three scopes (My Blueprints, Organization, Platform) from a
                single settings page.
            </li>
        </ul>

        <h3>Learn more</h3>
        <ul>
            <li>
                <a href="#"
                   data-help-article="using-blueprints"><b>Starting an Exchange from a blueprint</b></a> - how to use the picker.
            </li>
            <li>
                <a href="#"
                   data-help-article="managing-blueprints"><b>Creating and managing blueprints</b></a> - build and edit your personal blueprints.
            </li>
            <li>
                <a href="#"
                   data-help-article="org-blueprints"><b>Organization blueprints</b></a> - sharing blueprints across your team.
            </li>
            <li>
                <a href="#"
                   data-help-article="blueprint-platform"><b>Platform blueprints</b></a> - ready-made blueprints you can clone.
            </li>
        </ul>
    </>
);
