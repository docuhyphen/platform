
export const managingPersonalVariablesArticle = (
    <>
        <p id={"managing-personal-variables-article-p-1"}>
            Personal variables are private key/value definitions that only you can see
            and manage. No other user, including Organization Admins, can view or modify
            your personal variables. They are available on Personal and Business plans.
        </p>

        <h3 id={"managing-personal-variables-article-h3-1"}>How to create a personal variable</h3>
        <ol id={"managing-personal-variables-article-ol-1"}>
            <li id={"managing-personal-variables-article-li-1"}>Open Settings and go to the <b id={"managing-personal-variables-article-b-1"}>Variables</b> tab.</li>
            <li id={"managing-personal-variables-article-li-2"}>Select the <b id={"managing-personal-variables-article-b-2"}>My Variables</b> sub-tab.</li>
            <li id={"managing-personal-variables-article-li-3"}>Click <b id={"managing-personal-variables-article-b-3"}>Add Variable</b>.</li>
            <li id={"managing-personal-variables-article-li-4"}>
                Enter a <b id={"managing-personal-variables-article-b-4"}>KEY</b> (uppercase letters, digits, and underscores only, maximum
                64 characters) and an optional default value.
            </li>
            <li id={"managing-personal-variables-article-li-5"}>Save.</li>
        </ol>
        <p id={"managing-personal-variables-article-p-2"}>
            The new variable is available as <code id={"managing-personal-variables-article-code-1"}>{"{{KEY}}"}</code> only in Exchanges
            and blueprints you create. Other users who receive or view your Exchanges do
            not see the token definition.
        </p>
        <p id={"managing-personal-variables-article-p-3"}>
            When you create, edit, or delete a personal variable, DocuHyphen may ask
            you to enter a 6-digit verification code before the change is saved.
        </p>

        <h3 id={"managing-personal-variables-article-h3-2"}>Overriding at Exchange creation</h3>
        <p id={"managing-personal-variables-article-p-4"}>
            When a blueprint uses personal variable tokens, the variable override panel
            lets you set per-Exchange values without changing the saved default. This is
            useful when the same blueprint is used for different clients or projects that
            each require a different value for the same token.
        </p>

        <h3 id={"managing-personal-variables-article-h3-3"}>Key format and precedence</h3>
        <p id={"managing-personal-variables-article-p-5"}>
            Keys must use uppercase letters, digits, and underscores only, with a maximum
            length of 64 characters. This is the same format as org variable keys.
        </p>
        <p id={"managing-personal-variables-article-p-6"}>
            If the same key exists in both org variables and personal variables, the
            org variable takes precedence at resolution time. To avoid ambiguity, use
            distinct key names for personal variables that do not overlap with any org
            variable keys.
        </p>
    </>
);
