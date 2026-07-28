import React from "react";

export const blueprintPlatformArticle = (
    <>
        <p>
            Platform blueprints are ready-made Exchange configurations provided by
            DocuHyphen. They are available to all users across all organizations
            and cover common document exchange patterns.
        </p>

        <h3>Browsing platform blueprints</h3>
        <ol>
            <li>Click <b>Start Exchange</b> and choose <b>From Blueprint</b>.</li>
            <li>In the picker, click the <b>Platform</b> tab.</li>
            <li>Browse the available blueprints by name, summary, or tags.</li>
            <li>Click <b>Use Blueprint</b> on any card to pre-fill your Exchange immediately.</li>
        </ol>
        <p>
            Platform blueprints can also be viewed in Settings under the{" "}
            <b>Blueprints - Platform</b> tab.
        </p>

        <h3>Cloning a platform blueprint</h3>
        <p>
            If you want to customise a platform blueprint and save it as your own,
            use the <b>Duplicate</b> action in the three-dot menu on the Platform
            tab in Settings. The clone:
        </p>
        <ul>
            <li>Lands in your <b>My Blueprints</b> scope.</li>
            <li>Starts <b>inactive</b> so you can review and adjust it before use.</li>
            <li>Records the source platform blueprint ID for traceability.</li>
            <li>Is fully independent - changes to the original platform blueprint do not affect your clone.</li>
        </ul>

        <h3>Who manages platform blueprints</h3>
        <p>
            Only App Admins can create, edit, activate, deactivate, publish, and
            delete platform blueprints in <b>Platform Administration - Platform
            Content - Blueprints</b>. Regular users and Organization Admins can
            view and clone platform Blueprints from Settings but cannot modify the
            originals.
        </p>
        <p>
            The platform editor is fixed to APP scope. Its document picker requests
            only platform library documents, its Business Fields tab requests only
            platform schemas, and it does not load organization or personal variable
            definitions.
        </p>
        <p>
            Platform blueprint management can require a 6-digit verification code
            before the change is applied.
        </p>

        <h3>Related articles</h3>
        <ul>
            <li>
                <a href="#"
                   data-help-article="using-blueprints"><b>Starting an Exchange from a blueprint</b></a> - how to use any blueprint in the picker.
            </li>
            <li>
                <a href="#"
                   data-help-article="managing-blueprints"><b>Creating and managing blueprints</b></a> - personalise a cloned blueprint in the editor.
            </li>
        </ul>
    </>
);
