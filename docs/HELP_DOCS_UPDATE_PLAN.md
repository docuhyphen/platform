# Help Docs Update Plan — Variables, Sequences & Authorization

## Before Starting

Read `AGENTS.md` in the project root before touching any file. Pay particular attention to:
- The 150-line component limit and the single-responsibility rule.
- The `makeStyles` / separate styles file requirement.
- The no-em-dash rule.
- The no-inline-style rule (except purely dynamic values).

---

## Step 1: Decouple helpDocsRegistry.tsx into per-section files

`web-app/src/app/components/help-docs/helpDocsRegistry.tsx` is currently 1730 lines — a single
monolithic object that holds every article in every section. This violates the 150-line component
limit and makes the file unusable to work in without constantly scrolling. Split it before adding
any new content.

### Target structure

```
web-app/src/app/components/help-docs/
  helpDocsRegistry.tsx            <- thin index: imports sections, exports helpers
  sections/
    startHereSection.tsx          <- "Start here" section + articles
    identitySection.tsx           <- "Identity & access" section + articles
    exchangesSection.tsx          <- "Exchanges" section + articles
    adminOperationsSection.tsx    <- "Admin operations" section + articles
    workflowsSection.tsx          <- "Workflows" section + articles
    blueprintsSection.tsx         <- "Blueprints" section + articles
    variablesSection.tsx          <- NEW: "Variables & Sequences" section + articles
```

### How to split

Each section file exports a single `HelpDocSectionInput` constant, e.g.:

```tsx
// workflowsSection.tsx
import React from "react";
import {HelpDocSectionInput} from "../helpDocsRegistry";

export const workflowsSection: HelpDocSectionInput = {
    id: "workflows",
    title: "Workflows",
    articles: [ ... ],
};
```

The root `helpDocsRegistry.tsx` becomes:

```tsx
import {startHereSection} from "./sections/startHereSection";
import {identitySection}   from "./sections/identitySection";
// ... etc.

const helpDocSections: HelpDocSectionInput[] = [
    startHereSection,
    identitySection,
    exchangesSection,
    adminOperationsSection,
    workflowsSection,
    blueprintsSection,
    variablesSection,
];

export const HELP_DOC_ARTICLES: HelpDocArticle[] = helpDocSections.flatMap(...);
// ... utility functions unchanged
```

`HelpDocSectionInput` must be exported from `helpDocsRegistry.tsx` so section files can import
the type.

### Size check

After splitting, each section file should be well under 300 lines. The root registry file should
be under 50 lines. If any single article is longer than ~150 lines of JSX, extract it into its
own article file inside `sections/articles/`.

---

## Step 2: Add the "Variables & Sequences" section

Create `web-app/src/app/components/help-docs/sections/variablesSection.tsx`.

The section id is `"variables"` and the title is `"Variables & Sequences"`. It contains the
following articles:

### Article: variables-overview (id: "variables-overview")
Title: "Variables & Sequences overview"

Cover:
- What variables are: named placeholders (`{{TOKEN}}`) resolved at exchange creation time.
- Why they exist: let blueprints carry dynamic content without hardcoding names, dates, or counters.
- Three variable types and where each one lives:
  - System variables: built-in, automatic, no configuration needed. List all 10 tokens
    with their description and an example value.
  - Org variables (`{{KEY}}`): org-admin-managed key/value defaults shared across the org.
    Members can override them per exchange.
  - Personal variables (`{{KEY}}`): private to the creating user. Only that user can see and
    override them.
- Sequences (`{{SEQ:KEY}}`): org-managed auto-incrementing counters. Each use during exchange
  creation atomically increments the counter. Gaps in the sequence are expected and acceptable
  (same behaviour as invoice numbers).
- Where to find this feature: Settings -> Variables tab (sub-tabs: My Variables, Organization,
  Platform).

### Article: using-variable-tokens (id: "using-variable-tokens")
Title: "Using variable tokens in blueprints and exchanges"

Cover:
- Which fields support tokens: exchange name, description, initial share message, and
  document titles.
- How to insert a token: type `{{` in any supported field to open the token picker popover.
- The picker is grouped into four sections: System, Sequences, Org, Personal.
- Token chips appear inline in the field with colour coding:
  - Brand (blue): system tokens.
  - Success (green): sequence tokens.
  - Warning (amber): org variable tokens.
  - Informative (teal): personal variable tokens.
- Hovering a chip shows a tooltip with the resolved preview value.
- At exchange creation, all tokens are resolved. Sequence tokens (`{{SEQ:KEY}}`) increment
  the counter once per exchange, on the name field only.
- Org/personal tokens that have no matching variable definition remain as-is and are logged
  as unresolved.
- Variable overrides: if a blueprint uses org or personal variable tokens, a "Fill in
  variables" panel appears after blueprint selection so the user can supply or confirm
  per-exchange values before proceeding.

### Article: managing-org-variables (id: "managing-org-variables")
Title: "Managing organization variables"

Cover:
- Who can manage org variables: Organization Admin and App Admin only. Regular members can
  see org variables resolved in their exchanges but cannot create or edit the definitions.
- How to create an org variable:
  1. Open Settings and go to the Variables tab.
  2. Select the Organization sub-tab.
  3. Click Add Variable (top right, visible to admins only).
  4. Enter a KEY (uppercase alphanumeric and underscores, max 64 chars) and an optional
     default value.
  5. Save.
- Editing and deleting: use the three-dot menu on each variable row (visible to admins only).
  The key cannot be changed after creation; only the default value can be updated.
- The Platform sub-tab shows the 10 read-only system variables for reference. No configuration
  is needed for system variables.

### Article: managing-personal-variables (id: "managing-personal-variables")
Title: "Managing personal variables"

Cover:
- Personal variables are private to the user who creates them. No one else can see or modify
  them.
- How to create a personal variable:
  1. Open Settings and go to the Variables tab.
  2. Select the My Variables sub-tab.
  3. Click Add Variable.
  4. Enter a KEY and an optional default value.
  5. Save.
- Overriding at exchange creation: when a blueprint uses personal variable tokens, the
  variable override panel lets the user set per-exchange values without changing the saved
  default.
- Keys must be uppercase alphanumeric with underscores (same format as org variables). If
  the same key exists in both org variables and personal variables, the personal variable
  takes precedence at resolution time.

### Article: managing-sequences (id: "managing-sequences")
Title: "Managing sequences"

Cover:
- What a sequence is: a named counter stored in the org, referenced by `{{SEQ:KEY}}`. Each
  time an exchange is created with that token in the name field, the counter increments by 1
  and the formatted value is substituted.
- Who can manage sequences: Organization Admin and App Admin only. Regular members can use
  `{{SEQ:KEY}}` tokens in blueprints/exchanges but cannot create or configure sequences.
- How to create a sequence:
  1. Open Settings and go to the Sequences tab.
  2. Click New Sequence (visible to admins only).
  3. Fill in: Name (display label), Key (uppercase, used in `{{SEQ:KEY}}`), Pad Width (0 =
     no padding; 3 gives 007), Prefix, Suffix, Reset Period (Never / Yearly / Monthly).
  4. The live preview shows what the next formatted value will look like.
  5. Save.
- Editing: the Key cannot be changed after creation. All other fields are editable.
- Reset Counter: sets the counter back to 0. The next exchange using this sequence will get
  value 1 (or formatted equivalent). Gaps in the sequence before the reset are normal.
- Deleting: soft-deletes the sequence. Any blueprint or exchange that still contains
  `{{SEQ:KEY}}` for a deleted sequence will leave the token unresolved.
- Note on gaps: if exchange creation fails after the counter is incremented, the counter is
  not rolled back. This is by design, consistent with how invoice numbers work.

---

## Step 3: Update the "Start here" starter guide

In `startHereSection.tsx`, add a "Variables & Sequences quick links" block below the
Blueprints quick links block:

```tsx
<h3>Variables & Sequences quick links</h3>
<ul>
    <li>
        <a href="#" data-help-article="variables-overview">
            <b>Variables & Sequences overview</b>
        </a> - what tokens are and how they work.
    </li>
    <li>
        <a href="#" data-help-article="using-variable-tokens">
            <b>Using variable tokens</b>
        </a> - insert and preview tokens in blueprints and exchanges.
    </li>
    <li>
        <a href="#" data-help-article="managing-sequences">
            <b>Managing sequences</b>
        </a> - create auto-incrementing counters for exchange names.
    </li>
</ul>
```

Also add "Variables & Sequences" to the recommended reading order for Organization Admins:
"Start with IdP and Role & Permission Matrix, then read the full Workflows section and
Variables & Sequences overview."

---

## Step 4: Update the "Role & Permission Matrix" article

In `adminOperationsSection.tsx`, extend the permission matrix article to cover the new
authorization rules for variables and sequences.

Add under "Permission matrix (high level)":

```tsx
<li><b>Create and manage org variables:</b> Admin only.</li>
<li><b>View org variables (resolved in exchanges):</b> All members.</li>
<li><b>Create and manage personal variables:</b> Any authenticated user (own variables only).</li>
<li><b>Create and manage sequences:</b> Admin only.</li>
<li><b>Use variable tokens in blueprints and exchanges:</b> Any authenticated user.</li>
```

---

## Step 5: Register new articles in the "Learn more" links of related articles

In `blueprintsSection.tsx`, in the "Blueprint overview" article under "What a blueprint
stores", add a note:

> Exchange name, description, and document titles can include variable tokens
> (`{{TOKEN}}`) that are resolved at exchange creation time. See
> [Using variable tokens](#) for details.

Link the anchor `data-help-article="using-variable-tokens"`.

In the "Starting an exchange from a blueprint" article, add a step under "After selecting
a blueprint":

> If the blueprint contains org or personal variable tokens, a variable override panel
> appears. Review or edit each value before proceeding to the Recipients tab.

---

## Step 6: Verify

After all changes:
1. Run `npx tsc --noEmit` inside `web-app/` to confirm no TypeScript errors.
2. Check that every new article id referenced in `data-help-article` attributes matches an
   id in `variablesSection.tsx`.
3. Confirm no file in `help-docs/sections/` exceeds 300 lines.
4. Confirm `helpDocsRegistry.tsx` itself is under 60 lines.
5. Re-read the final output of each article to ensure there are no em dashes.
