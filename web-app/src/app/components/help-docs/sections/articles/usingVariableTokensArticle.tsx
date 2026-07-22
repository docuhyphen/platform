
export const usingVariableTokensArticle = (
    <>
        <p>
            Variable tokens can be inserted into supported text fields in blueprints and
            Exchanges. At Exchange creation time, every token is resolved to its current
            value before the Exchange is saved.
        </p>

        <h3>Which fields support tokens</h3>
        <ul>
            <li>Exchange name</li>
            <li>Exchange description</li>
            <li>Initial share message</li>
            <li>Document titles</li>
        </ul>

        <h3>Inserting a token</h3>
        <p>
            Type <code>{"{{"}</code> in any supported field to open the token picker
            popover. The picker is grouped into four sections:
        </p>
        <ul>
            <li><b>System</b> - the 10 built-in system tokens.</li>
            <li><b>Sequences</b> - your organization's sequence definitions.</li>
            <li><b>Org</b> - your organization's variable definitions.</li>
            <li><b>Personal</b> - your personal variable definitions.</li>
        </ul>
        <p>
            Select a token from the picker to insert it as a chip in the field.
        </p>

        <h3>Token chip colours</h3>
        <p>
            Each token chip is colour-coded by type so you can identify it at a glance:
        </p>
        <ul>
            <li><b>Blue (brand)</b> - system tokens.</li>
            <li><b>Green (success)</b> - sequence tokens.</li>
            <li><b>Amber (warning)</b> - org variable tokens.</li>
            <li><b>Teal (informative)</b> - personal variable tokens.</li>
        </ul>

        <h3>Preview tooltips</h3>
        <p>
            Hover over a token chip to see a tooltip showing the resolved preview value
            based on your current user, org, and variable defaults. Sequence tokens show
            the next formatted value without incrementing the counter.
        </p>

        <h3>Resolution at Exchange creation</h3>
        <p>
            When an Exchange is created, every token in the supported fields is resolved
            in this order:
        </p>
        <ol>
            <li>System tokens are resolved first using the creator's profile and the current timestamp.</li>
            <li>Per-Exchange overrides (values entered in the variable override panel) take priority over saved defaults.</li>
            <li>Org variable defaults are used if no override was provided.</li>
            <li>Personal variable defaults are used for personal variable tokens.</li>
            <li>Sequence tokens (<code>{"{{SEQ:KEY}}"}</code>) are incremented and substituted in the Exchange name field only. The counter increments once per Exchange regardless of how many times the token appears in the name.</li>
        </ol>
        <p>
            Org or personal tokens with no matching variable definition remain as-is in the
            resolved text and are logged as unresolved.
        </p>

        <h3>Variable override panel</h3>
        <p>
            If a blueprint contains org or personal variable tokens, a variable override
            panel appears after blueprint selection. The panel lists each token with its
            saved default value. You can accept the defaults or enter per-Exchange values
            before proceeding to the Recipients tab. Overrides are used only for that
            Exchange and do not change the saved variable definition.
        </p>
    </>
);
