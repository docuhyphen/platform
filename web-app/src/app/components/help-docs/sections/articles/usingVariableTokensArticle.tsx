
export const usingVariableTokensArticle = (
    <>
        <p id={"using-variable-tokens-article-p-1"}>
            Variable tokens can be inserted into supported text fields in blueprints and
            Exchanges. At Exchange creation time, every token is resolved to its current
            value before the Exchange is saved.
        </p>

        <h3 id={"using-variable-tokens-article-h3-1"}>Which fields support tokens</h3>
        <ul id={"using-variable-tokens-article-ul-1"}>
            <li id={"using-variable-tokens-article-li-1"}>Exchange name</li>
            <li id={"using-variable-tokens-article-li-2"}>Exchange description</li>
            <li id={"using-variable-tokens-article-li-3"}>Initial share message</li>
            <li id={"using-variable-tokens-article-li-4"}>Document titles</li>
        </ul>

        <h3 id={"using-variable-tokens-article-h3-2"}>Inserting a token</h3>
        <p id={"using-variable-tokens-article-p-2"}>
            Type <code id={"using-variable-tokens-article-code-1"}>{"{{"}</code> in any supported field to open the token picker
            popover. The picker is grouped into four sections:
        </p>
        <ul id={"using-variable-tokens-article-ul-2"}>
            <li id={"using-variable-tokens-article-li-5"}><b id={"using-variable-tokens-article-b-1"}>System</b> - the 10 built-in system tokens.</li>
            <li id={"using-variable-tokens-article-li-6"}><b id={"using-variable-tokens-article-b-2"}>Sequences</b> - your organization's sequence definitions.</li>
            <li id={"using-variable-tokens-article-li-7"}><b id={"using-variable-tokens-article-b-3"}>Org</b> - your organization's variable definitions.</li>
            <li id={"using-variable-tokens-article-li-8"}><b id={"using-variable-tokens-article-b-4"}>Personal</b> - your personal variable definitions.</li>
        </ul>
        <p id={"using-variable-tokens-article-p-3"}>
            Select a token from the picker to insert it as a chip in the field.
        </p>

        <h3 id={"using-variable-tokens-article-h3-3"}>Token chip colours</h3>
        <p id={"using-variable-tokens-article-p-4"}>
            Each token chip is colour-coded by type so you can identify it at a glance:
        </p>
        <ul id={"using-variable-tokens-article-ul-3"}>
            <li id={"using-variable-tokens-article-li-9"}><b id={"using-variable-tokens-article-b-5"}>Blue (brand)</b> - system tokens.</li>
            <li id={"using-variable-tokens-article-li-10"}><b id={"using-variable-tokens-article-b-6"}>Green (success)</b> - sequence tokens.</li>
            <li id={"using-variable-tokens-article-li-11"}><b id={"using-variable-tokens-article-b-7"}>Amber (warning)</b> - org variable tokens.</li>
            <li id={"using-variable-tokens-article-li-12"}><b id={"using-variable-tokens-article-b-8"}>Teal (informative)</b> - personal variable tokens.</li>
        </ul>

        <h3 id={"using-variable-tokens-article-h3-4"}>Preview tooltips</h3>
        <p id={"using-variable-tokens-article-p-5"}>
            Hover over a token chip to see a tooltip showing the resolved preview value
            based on your current user, org, and variable defaults. Sequence tokens show
            the next formatted value without incrementing the counter.
        </p>

        <h3 id={"using-variable-tokens-article-h3-5"}>Resolution at Exchange creation</h3>
        <p id={"using-variable-tokens-article-p-6"}>
            When an Exchange is created, every token in the supported fields is resolved
            in this order:
        </p>
        <ol id={"using-variable-tokens-article-ol-1"}>
            <li id={"using-variable-tokens-article-li-13"}>System tokens are resolved first using the creator's profile and the current timestamp.</li>
            <li id={"using-variable-tokens-article-li-14"}>Per-Exchange overrides (values entered in the variable override panel) take priority over saved defaults.</li>
            <li id={"using-variable-tokens-article-li-15"}>Org variable defaults are used if no override was provided.</li>
            <li id={"using-variable-tokens-article-li-16"}>Personal variable defaults are used for personal variable tokens.</li>
            <li id={"using-variable-tokens-article-li-17"}>Sequence tokens (<code id={"using-variable-tokens-article-code-2"}>{"{{SEQ:KEY}}"}</code>) are incremented and substituted in the Exchange name field only. The counter increments once per Exchange regardless of how many times the token appears in the name.</li>
        </ol>
        <p id={"using-variable-tokens-article-p-7"}>
            Org or personal tokens with no matching variable definition remain as-is in the
            resolved text and are logged as unresolved.
        </p>

        <h3 id={"using-variable-tokens-article-h3-6"}>Variable override panel</h3>
        <p id={"using-variable-tokens-article-p-8"}>
            If a blueprint contains org or personal variable tokens, a variable override
            panel appears after blueprint selection. The panel lists each token with its
            saved default value. You can accept the defaults or enter per-Exchange values
            before proceeding to the Recipients tab. Overrides are used only for that
            Exchange and do not change the saved variable definition.
        </p>
    </>
);
