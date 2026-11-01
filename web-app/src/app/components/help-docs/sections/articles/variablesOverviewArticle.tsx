
export const variablesOverviewArticle = (
    <>
        <p id={"variables-overview-article-p-1"}>
            Variables are named placeholders written as <code id={"variables-overview-article-code-1"}>{"{{TOKEN}}"}</code> that are resolved
            to their actual values at Exchange creation time. They let blueprints carry
            dynamic content - names, dates, counters - without hardcoding values that
            change from Exchange to Exchange.
        </p>

        <h3 id={"variables-overview-article-h3-1"}>Four variable types</h3>
        <p id={"variables-overview-article-p-2"}>
            Variables fall into four categories. System variables and sequences are
            built in or org-managed. Org variables and personal variables are
            user-defined key/value pairs.
        </p>

        <h3 id={"variables-overview-article-h3-2"}>System variables</h3>
        <p id={"variables-overview-article-p-3"}>
            System variables are built in and resolved automatically. No configuration
            is needed. The ten available tokens are:
        </p>
        <ul id={"variables-overview-article-ul-1"}>
            <li id={"variables-overview-article-li-1"}><code id={"variables-overview-article-code-2"}>{"{{USER_FIRST_NAME}}"}</code> - first name of the initiating user (e.g. "Alex").</li>
            <li id={"variables-overview-article-li-2"}><code id={"variables-overview-article-code-3"}>{"{{USER_LAST_NAME}}"}</code> - last name of the initiating user (e.g. "Smith").</li>
            <li id={"variables-overview-article-li-3"}><code id={"variables-overview-article-code-4"}>{"{{USER_FULL_NAME}}"}</code> - full name of the initiating user (e.g. "Alex Smith").</li>
            <li id={"variables-overview-article-li-4"}><code id={"variables-overview-article-code-5"}>{"{{USER_EMAIL}}"}</code> - email address of the initiating user (e.g. "alex.smith@acme.com").</li>
            <li id={"variables-overview-article-li-5"}><code id={"variables-overview-article-code-6"}>{"{{ORG_NAME}}"}</code> - name of the initiating user's organization (e.g. "Acme Corp").</li>
            <li id={"variables-overview-article-li-6"}><code id={"variables-overview-article-code-7"}>{"{{ORG_REG_NUMBER}}"}</code> - registration number of the organization (e.g. "2024/001234/07").</li>
            <li id={"variables-overview-article-li-7"}><code id={"variables-overview-article-code-8"}>{"{{CURRENT_DATE}}"}</code> - current date in ISO format at creation time (e.g. "2026-06-21").</li>
            <li id={"variables-overview-article-li-8"}><code id={"variables-overview-article-code-9"}>{"{{CURRENT_YEAR}}"}</code> - current year (e.g. "2026").</li>
            <li id={"variables-overview-article-li-9"}><code id={"variables-overview-article-code-10"}>{"{{CURRENT_MONTH}}"}</code> - current month as a full name (e.g. "June").</li>
            <li id={"variables-overview-article-li-10"}><code id={"variables-overview-article-code-11"}>{"{{CURRENT_MONTH_NUMBER}}"}</code> - current month as a two-digit number (e.g. "06").</li>
        </ul>

        <h3 id={"variables-overview-article-h3-3"}>Organization variables</h3>
        <p id={"variables-overview-article-p-4"}>
            Org variables use the syntax <code id={"variables-overview-article-code-12"}>{"{{KEY}}"}</code> where <code id={"variables-overview-article-code-13"}>KEY</code> is an
            uppercase alphanumeric identifier. They are created and managed by Organization
            Admins. Each variable has an optional default value shared across the org.
            Members can override the default value per Exchange at creation time without
            changing the saved definition.
        </p>

        <h3 id={"variables-overview-article-h3-4"}>Personal variables</h3>
        <p id={"variables-overview-article-p-5"}>
            Personal variables use the same <code id={"variables-overview-article-code-14"}>{"{{KEY}}"}</code> syntax as org variables but
            are private to the user who created them. Only that user can see, create, or
            modify their personal variables. If a key exists in both org variables and
            personal variables, the org variable takes precedence at resolution time.
        </p>

        <h3 id={"variables-overview-article-h3-5"}>Sequences</h3>
        <p id={"variables-overview-article-p-6"}>
            Sequences are org-managed auto-incrementing counters referenced by the syntax{" "}
            <code id={"variables-overview-article-code-15"}>{"{{SEQ:KEY}}"}</code>. Each time an Exchange is created with a sequence token
            in the Exchange name field, the counter is atomically incremented by 1 and the
            formatted value is substituted. Sequences support padding, prefix, suffix, and
            optional reset periods (yearly or monthly).
        </p>
        <p id={"variables-overview-article-p-7"}>
            Gaps in a sequence are expected and acceptable. If Exchange creation fails
            after the counter has already incremented, the counter is not rolled back.
            This is consistent with how invoice numbers work.
        </p>

        <h3 id={"variables-overview-article-h3-6"}>Where to find this feature</h3>
        <p id={"variables-overview-article-p-8"}>
            Open <b id={"variables-overview-article-b-1"}>Settings</b> and look for the <b id={"variables-overview-article-b-2"}>Variables</b> tab and the{" "}
            <b id={"variables-overview-article-b-3"}>Sequences</b> tab. These entries appear when the current Personal or
            Business context includes variables and sequences; they are hidden on Free.
        </p>
        <ul id={"variables-overview-article-ul-2"}>
            <li id={"variables-overview-article-li-11"}>
                <b id={"variables-overview-article-b-4"}>Variables tab</b> - three sub-tabs:
                <ul id={"variables-overview-article-ul-3"}>
                    <li id={"variables-overview-article-li-12"}><b id={"variables-overview-article-b-5"}>My Variables</b> - your personal variable definitions.</li>
                    <li id={"variables-overview-article-li-13"}><b id={"variables-overview-article-b-6"}>Organization</b> - org-wide variable definitions (admin-managed).</li>
                    <li id={"variables-overview-article-li-14"}><b id={"variables-overview-article-b-7"}>Platform</b> - the 10 read-only system variables, shown for reference.</li>
                </ul>
            </li>
            <li id={"variables-overview-article-li-15"}>
                <b id={"variables-overview-article-b-8"}>Sequences tab</b> - create and manage org-level sequence definitions
                (admin-managed).
            </li>
        </ul>
    </>
);
