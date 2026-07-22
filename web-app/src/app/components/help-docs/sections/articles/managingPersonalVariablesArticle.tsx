import React from "react";

export const managingPersonalVariablesArticle = (
    <>
        <p>
            Personal variables are private key/value definitions that only you can see
            and manage. No other user, including Organization Admins, can view or modify
            your personal variables.
        </p>

        <h3>How to create a personal variable</h3>
        <ol>
            <li>Open Settings and go to the <b>Variables</b> tab.</li>
            <li>Select the <b>My Variables</b> sub-tab.</li>
            <li>Click <b>Add Variable</b>.</li>
            <li>
                Enter a <b>KEY</b> (uppercase letters, digits, and underscores only, maximum
                64 characters) and an optional default value.
            </li>
            <li>Save.</li>
        </ol>
        <p>
            The new variable is available as <code>{"{{KEY}}"}</code> only in Exchanges
            and blueprints you create. Other users who receive or view your Exchanges do
            not see the token definition.
        </p>
        <p>
            When you create, edit, or delete a personal variable, DocuHyphen may ask
            you to enter a 6-digit verification code before the change is saved.
        </p>

        <h3>Overriding at Exchange creation</h3>
        <p>
            When a blueprint uses personal variable tokens, the variable override panel
            lets you set per-Exchange values without changing the saved default. This is
            useful when the same blueprint is used for different clients or projects that
            each require a different value for the same token.
        </p>

        <h3>Key format and precedence</h3>
        <p>
            Keys must use uppercase letters, digits, and underscores only, with a maximum
            length of 64 characters. This is the same format as org variable keys.
        </p>
        <p>
            If the same key exists in both org variables and personal variables, the
            org variable takes precedence at resolution time. To avoid ambiguity, use
            distinct key names for personal variables that do not overlap with any org
            variable keys.
        </p>
    </>
);
