import React from "react";

export const variablesOverviewArticle = (
    <>
        <p>
            Variables are named placeholders written as <code>{"{{TOKEN}}"}</code> that are resolved
            to their actual values at exchange creation time. They let blueprints carry
            dynamic content - names, dates, counters - without hardcoding values that
            change from exchange to exchange.
        </p>

        <h3>Four variable types</h3>
        <p>
            Variables fall into four categories. System variables and sequences are
            built in or org-managed. Org variables and personal variables are
            user-defined key/value pairs.
        </p>

        <h3>System variables</h3>
        <p>
            System variables are built in and resolved automatically. No configuration
            is needed. The ten available tokens are:
        </p>
        <ul>
            <li><code>{"{{USER_FIRST_NAME}}"}</code> - first name of the initiating user (e.g. "Alex").</li>
            <li><code>{"{{USER_LAST_NAME}}"}</code> - last name of the initiating user (e.g. "Smith").</li>
            <li><code>{"{{USER_FULL_NAME}}"}</code> - full name of the initiating user (e.g. "Alex Smith").</li>
            <li><code>{"{{USER_EMAIL}}"}</code> - email address of the initiating user (e.g. "alex.smith@acme.com").</li>
            <li><code>{"{{ORG_NAME}}"}</code> - name of the initiating user's organization (e.g. "Acme Corp").</li>
            <li><code>{"{{ORG_REG_NUMBER}}"}</code> - registration number of the organization (e.g. "2024/001234/07").</li>
            <li><code>{"{{CURRENT_DATE}}"}</code> - current date in ISO format at creation time (e.g. "2026-06-21").</li>
            <li><code>{"{{CURRENT_YEAR}}"}</code> - current year (e.g. "2026").</li>
            <li><code>{"{{CURRENT_MONTH}}"}</code> - current month as a full name (e.g. "June").</li>
            <li><code>{"{{CURRENT_MONTH_NUMBER}}"}</code> - current month as a two-digit number (e.g. "06").</li>
        </ul>

        <h3>Organization variables</h3>
        <p>
            Org variables use the syntax <code>{"{{KEY}}"}</code> where <code>KEY</code> is an
            uppercase alphanumeric identifier. They are created and managed by Organization
            Admins. Each variable has an optional default value shared across the org.
            Members can override the default value per exchange at creation time without
            changing the saved definition.
        </p>

        <h3>Personal variables</h3>
        <p>
            Personal variables use the same <code>{"{{KEY}}"}</code> syntax as org variables but
            are private to the user who created them. Only that user can see, create, or
            modify their personal variables. If a key exists in both org variables and
            personal variables, the org variable takes precedence at resolution time.
        </p>

        <h3>Sequences</h3>
        <p>
            Sequences are org-managed auto-incrementing counters referenced by the syntax{" "}
            <code>{"{{SEQ:KEY}}"}</code>. Each time an exchange is created with a sequence token
            in the exchange name field, the counter is atomically incremented by 1 and the
            formatted value is substituted. Sequences support padding, prefix, suffix, and
            optional reset periods (yearly or monthly).
        </p>
        <p>
            Gaps in a sequence are expected and acceptable. If exchange creation fails
            after the counter has already incremented, the counter is not rolled back.
            This is consistent with how invoice numbers work.
        </p>

        <h3>Where to find this feature</h3>
        <p>
            Open <b>Settings</b> and look for the <b>Variables</b> tab and the{" "}
            <b>Sequences</b> tab.
        </p>
        <ul>
            <li>
                <b>Variables tab</b> - three sub-tabs:
                <ul>
                    <li><b>My Variables</b> - your personal variable definitions.</li>
                    <li><b>Organization</b> - org-wide variable definitions (admin-managed).</li>
                    <li><b>Platform</b> - the 10 read-only system variables, shown for reference.</li>
                </ul>
            </li>
            <li>
                <b>Sequences tab</b> - create and manage org-level sequence definitions
                (admin-managed).
            </li>
        </ul>
    </>
);
