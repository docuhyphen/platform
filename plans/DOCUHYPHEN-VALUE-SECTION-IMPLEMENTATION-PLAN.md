# DocuHyphen Value Section Implementation Plan

## Status

Paused for later continuation.

No website source files have been changed for this feature. The work completed so far consists of product analysis, messaging recommendations, design decisions, and an interactive concept stored outside the repository.

Current interactive concept:

`C:\Users\Black\.codex\visualizations\2026\08\09\019fe78e-4431-72a1-bbe2-31e88be0f808\docuhyphen-value-section.html`

This plan is the source of truth for continuing the feature. The concept is a reference, not production code.

## Objective

Add a homepage section that gives prospective customers clear business reasons to use and pay for DocuHyphen.

The section must explain the value of DocuHyphen as a platform for solving Document-Driven business processes. It must not position DocuHyphen as another document sharing or file sharing product.

## Core positioning

Use the following positioning as the foundation for all website copy:

> DocuHyphen is a platform that orchestrates Document-Driven business processes by bringing people, documents, decisions, controls, and activity together from initiation to a clear, auditable outcome.

Supporting principles:

- Documents are inputs, outputs, and evidence within a business process.
- An Exchange is the controlled workspace for that business process.
- Workflows coordinate approvals, decisions, notifications, conditions, and handoffs.
- Access controls govern who participates and what each participant can do.
- Audit history records how the process reached its outcome.
- Business outcomes must lead the marketing story. Product features should support and prove those outcomes.

Avoid leading with phrases such as:

- Share files securely.
- Send documents safely.
- A better document sharing site.
- Secure cloud storage for documents.

Those statements make the platform appear narrower and less differentiated than it is.

## Problem being solved on the current homepage

The current homepage explains capabilities and mechanics, but it does not clearly translate them into reasons a business should pay for the platform.

The missing commercial outcomes include:

- Less repetitive setup.
- Less manual follow-up.
- Fewer avoidable delays.
- Reduced operational effort.
- Less inconsistency and rework.
- Clearer participant accountability.
- Better control across the full process lifecycle.
- A stronger record of how an outcome was reached.

The existing risk section already focuses on unauthorized access, uncontrolled distribution, audit gaps, and regulatory exposure. The new value section should not repeat that fear-led framing. It should lead with operational value and use control and evidence as supporting outcomes.

## Recommended homepage order

The current source order is:

1. Hero.
2. Built to adapt to your industry.
3. How it works.
4. Hidden risks.
5. More industry coverage.
6. Contact.

The recommended order is:

1. Hero.
2. How it works.
3. Why DocuHyphen.
4. Built to adapt to your industry.
5. Hidden risks.
6. More industry coverage.
7. Contact.

This order creates a clear narrative:

1. Explain what the platform is.
2. Show how an Exchange works.
3. Explain why the platform is worth using.
4. Demonstrate how it applies to different industries.
5. Reinforce the risks of continuing with weak processes.

Reordering is required because `FeaturesSection`, which contains the industry experience, currently appears before `HowItWorksSection` in `website/src/App.tsx`.

## Agreed section direction

The section should be a rotating, interactive product-value experience.

Layout on desktop:

- Four business outcomes appear in a vertical selector on the left.
- A realistic rendered product demo appears on the right.
- The selected outcome controls which demo is displayed.
- The section rotates through the four outcomes automatically.
- Visitors can select any outcome directly.
- Visitors can pause and resume automatic rotation.

Layout on mobile:

- The introduction appears first.
- The four outcomes stack vertically.
- The selected product demo appears below the outcome selector.
- Demo content must reflow instead of shrinking into an unreadable desktop screenshot.
- No horizontal page overflow is permitted.

## Recommended section copy

### Eyebrow

`Why DocuHyphen`

### Headline

`Run Document-Driven business processes with less operational effort.`

### Supporting copy

`DocuHyphen orchestrates the people, documents, decisions, and controls behind each process, from initiation to a clear, auditable outcome.`

This copy is the current recommendation. Review it once more before implementation, but preserve the process-level positioning even if the final wording changes.

## Rotating business outcomes

### Outcome 1: Run repeatable processes

Short outcome:

`Less setup and fewer inconsistencies`

Business message:

Teams should not rebuild recurring Document-Driven business processes from scratch. DocuHyphen lets them begin from an approved and reusable process configuration.

Product proof:

- Blueprints.
- Business fields and schemas.
- Required document definitions.
- Reusable library documents.
- Default permissions.
- Default participants.
- Variables and sequences.
- Reusable communications.

Recommended demo:

- Render a DocuHyphen Settings view for Organization Blueprints.
- Show a selected `Client onboarding` Blueprint.
- Show other realistic Blueprints such as `Vendor due diligence` and `Annual audit request`.
- Show an Overview, Documents, and Participants tab treatment.
- Show grouped configuration summaries for required documents, business fields, default participants, and process controls.
- Show Active and Published states.

The demo should communicate that a repeatable business process is being configured, not that a file template is being selected.

### Outcome 2: Move decisions and handoffs forward

Short outcome:

`Less chasing and fewer avoidable delays`

Business message:

DocuHyphen keeps the next required action visible and routes decisions to the correct people. Workflow gates, notifications, deadlines, reminders, and escalations reduce the need for manual follow-up.

Product proof:

- Approval workflow steps.
- Notification workflow steps.
- Condition workflow steps.
- Automated actions.
- Approval quorums.
- SLA deadlines.
- Reminders and escalations.
- Workflow activity monitoring.
- Counterparty clearance where applicable.

Recommended demo:

- Render the Workflow tab for an active Exchange.
- Use the app's `Both` view so the timeline and workflow diagram are visible together.
- The timeline should show realistic runtime information:
  - Initial review, completed.
  - Compliance approval, in progress.
  - Assigned Compliance Group.
  - A visible deadline.
  - Notify account owner, waiting.
  - Activate Exchange, waiting.
- The diagram should show the same steps and state:
  - Completed nodes use the app's completed treatment.
  - The current node uses the app's active treatment.
  - Waiting nodes remain neutral.
  - Connectors visibly show process direction.
- Show the Timeline, Diagram, and Both view controls, with Both selected.

This demo is the clearest proof that DocuHyphen orchestrates business processes rather than merely sharing documents.

### Outcome 3: Coordinate every participant

Short outcome:

`Clearer collaboration and less rework`

Business message:

The people involved in the process should work from the same context, understand what is required, and see the current state without relying on fragmented email chains.

Product proof:

- One Exchange workspace per business process.
- Required document slots.
- Upload progress.
- Search, filters, and sorting.
- Document preview.
- Document versions.
- Page-linked comments.
- Internal organization comments.
- Real-time comment updates.
- Participant roles and responsibilities.

Recommended demo:

- Render the Documents tab for a `Client onboarding` Exchange.
- Show progress such as `3 of 5 uploaded`.
- Show a document list with uploaded and not uploaded states.
- Show an open `Engagement letter` document.
- Show that the selected document has more than one version.
- Show a document preview component.
- On wider layouts, show a comments panel with a realistic page-related discussion.
- On narrower layouts, hide or move the comments panel rather than making the document preview unreadable.

The demo should show participants coordinating around a business outcome. It must not be presented as a generic file browser.

### Outcome 4: Control and prove the outcome

Short outcome:

`Lower operational risk and stronger accountability`

Business message:

DocuHyphen applies controls while work is taking place and retains the activity and decision history needed to explain how the result was reached.

Product proof:

- Exchange roles.
- Access constraints.
- MFA requirements.
- Download and resharing controls.
- Watermarked previews.
- Revocable access.
- Exchange lifecycle controls.
- Searchable audit events.
- Workflow decision history.
- Integrity verification.
- Controlled evidence exports.

Recommended demo:

- Render the Audit workspace for a selected Exchange.
- Show realistic events:
  - Exchange created.
  - Recipient accepted.
  - Document uploaded.
  - Workflow decision.
  - Exchange activated.
- Select the Workflow decision event.
- Show an event detail panel containing the decision, actor, recorded time, reason, and integrity state.
- Display `Integrity verified` as a state, not as a marketing claim about legal admissibility.

The demo should connect process control with evidence. It should not imply guaranteed regulatory compliance or guaranteed legal admissibility.

## Rotation and interaction requirements

Recommended behavior:

- Start on Outcome 1.
- Rotate every 7 seconds.
- Selecting an outcome displays its demo immediately and restarts the rotation timer.
- Display a subtle progress indicator on the selected outcome.
- Provide a visible circular Pause rotation control.
- When paused, change the control label to Resume rotation.
- Pause automatic rotation while the browser tab is not visible.
- Consider pausing while keyboard focus is inside the section so content does not change during interaction.
- Do not rotate automatically when `prefers-reduced-motion: reduce` is active.
- Keep the selected demo visible until the visitor chooses another outcome when reduced motion is active.
- Maintain a stable demo-stage height on desktop to avoid layout shifts as panels change.

Accessibility behavior:

- Use a semantic tablist for the outcomes.
- Use native buttons for the outcome selectors.
- Use `role="tab"`, `aria-selected`, and `aria-controls`.
- Use `role="tabpanel"` and `aria-labelledby` on each demo.
- Implement roving tab focus for the tablist.
- Support Up and Down arrow keys on the vertical desktop selector.
- Support Left and Right arrow keys if a horizontal mobile tab treatment is used later.
- Do not rely on color alone to communicate the active outcome or workflow state.
- Use `aria-live="polite"` for the changing demo area without announcing decorative details repeatedly.
- Keep visible focus treatments.

## Visual design requirements

The first concept was rejected because it used a generic six-card SaaS grid, decorative icons, and a dark theme that felt AI produced and did not match the website.

Do not return to that design.

The production section should follow the existing website visual language:

- Fixed light appearance matching the public website.
- Manrope typography.
- White and very light blue surfaces.
- Brand indigo headings and active states.
- Existing brand blue for progress and selection.
- Restrained neutral borders.
- Subtle shadows only around the rendered app demo.
- Existing section padding and content widths.
- Existing circular button treatment.
- Left-aligned headings and supporting copy.
- No unrelated illustrations.
- No oversized icons.
- No multicolored generic feature cards.
- No glossy or decorative gradients that are not already part of the site.
- No dark mode styling for this public website section unless the website gains an intentional dark theme later.

Recommended values to reuse from the current website:

- `WIDTH_CONTENT` for regular content width.
- The wider industry demo width where needed for the rendered app panel.
- `SECTION_PADDING_DESKTOP`.
- `SECTION_PADDING_MOBILE`.
- `BREAKPOINT_MOBILE`.
- Fluent UI theme tokens for colors, spacing, borders, typography, and shadows.

The rendered demo should look like DocuHyphen itself:

- Use the app's neutral surfaces and indigo active states.
- Use realistic tabs, status badges, timeline nodes, panels, document rows, and audit rows.
- Avoid browser-window chrome, fake macOS controls, or generic dashboard decoration.
- Avoid inventing analytics metrics that do not exist in the platform.

## Implementation architecture

Create a dedicated section rather than adding more code to `HowItWorksSection` or `IndustryExperience`.

Recommended structure:

```text
website/src/landing/why-docuhyphen-section/
  WhyDocuHyphenSection.tsx
  WhyDocuHyphenSectionStyles.tsx
  whyDocuHyphenContent.ts
  demos/
    blueprint-process-demo/
      BlueprintProcessDemo.tsx
      BlueprintProcessDemoStyles.tsx
    workflow-process-demo/
      WorkflowProcessDemo.tsx
      WorkflowProcessDemoStyles.tsx
    participant-coordination-demo/
      ParticipantCoordinationDemo.tsx
      ParticipantCoordinationDemoStyles.tsx
    audit-outcome-demo/
      AuditOutcomeDemo.tsx
      AuditOutcomeDemoStyles.tsx
```

Responsibilities:

- `WhyDocuHyphenSection.tsx`
  - Owns the selected outcome.
  - Owns automatic rotation and pause state.
  - Connects outcome tabs to demo panels.
  - Contains no detailed demo markup.
- `whyDocuHyphenContent.ts`
  - Stores outcome labels and supporting copy.
  - Stores stable IDs used for accessibility.
  - Does not store React elements.
- Demo components
  - Render realistic marketing-only versions of app screens.
  - Remain presentational.
  - Receive no live customer data.
  - Do not call backend services.

Keep every TSX component under approximately 150 lines. Split components further if a demo grows beyond that limit.

Do not import components directly from `web-app` into `website`. They are separate applications with different build boundaries. Recreate small, marketing-only demo components using the website's Fluent UI dependency while matching the app's rendered appearance.

All styling must live in co-located `*Styles.tsx` files using Fluent UI `makeStyles` and tokens. Do not use inline styles except for a genuinely dynamic progress value that cannot be expressed through a class.

Every added or updated React or HTML element must have a stable `id` where required by the project rules. All buttons must use `shape="circular"`.

## App integration

Update `website/src/App.tsx` so the landing page renders sections in this order:

```tsx
<HeroSection/>
<HowItWorksSection/>
<WhyDocuHyphenSection/>
<FeaturesSection initialIndustrySlug={industrySlug ?? undefined}/>
<RisksSection/>
<AudienceSection/>
```

Create a dedicated surface wrapper and style in `website/src/AppStyles.tsx` if a background transition is needed between How it works, Why DocuHyphen, and the industry section.

Recommended surface transition:

- How it works ends on the existing white surface.
- Why DocuHyphen begins white and transitions subtly into the existing light blue brand surface.
- Built to adapt to your industry follows naturally on the brand-tinted surface.

Do not nest `WhyDocuHyphenSection` inside `HowItWorksSection` or `FeaturesSection`. Each section has a separate responsibility.

## CTA decision

An earlier concept included a single Start Free CTA below the benefits.

The current rotating concept omits the CTA so the product demonstration remains the focus.

This is still an open decision for implementation:

- Recommended option: add one restrained Start Free button below the rotator if conversion testing shows a mid-page CTA is useful.
- Alternative: omit the CTA because Start Free is already available in the fixed header and hero.

Do not add multiple competing actions to this section. If a CTA is added, use the existing signup URL and circular primary-button treatment.

## Product claims supported by the platform

The following claims are supported and can inform copy:

- Blueprints reduce repeated Exchange setup.
- The Document Library supports reusable standard documents.
- Variables and sequences support repeatable dynamic content and numbering.
- Workflows support approvals, notifications, conditions, actions, deadlines, reminders, and escalations.
- Workflow activity shows current steps, assignees, decisions, and outcomes.
- Exchange documents support progress, search, filtering, previewing, versions, and comments.
- Page-linked and organization-internal comments support contextual collaboration.
- Roles and constraints control participant capabilities.
- MFA, watermarking, download restrictions, resharing restrictions, and revocation provide additional access controls.
- Audit events and workflow decision histories support accountability and evidence review.
- Audit integrity checks and controlled exports are implemented for authorized users.

Prefer qualitative claims such as:

- Helps reduce operational effort.
- Helps reduce repetitive setup.
- Keeps the next action visible.
- Helps teams avoid unnecessary delays and rework.
- Supports consistent processes.
- Supports controlled participation.
- Provides a clear activity and decision record.

## Claims to avoid

Do not make quantitative ROI claims until customer evidence exists.

Avoid claims such as:

- Saves 40 percent of processing time.
- Pays for itself.
- Eliminates all errors.
- Guarantees regulatory compliance.
- Produces legally admissible evidence in every jurisdiction.

Do not market the following as completed product features unless implementation and documentation change:

- Slack delivery.
- Microsoft Teams delivery.
- SMS or WhatsApp delivery.
- Google Drive backups.
- OneDrive backups.
- FTP backups.
- Electronic signatures.
- Passkeys.
- SOC 2 certification or report access.
- A published DPA.
- End-to-end encryption.
- Guaranteed malware scanning.

Use `controlled access`, `activity records`, and `audit support` instead of overstating security or legal guarantees.

## Responsive behavior

Desktop:

- Left outcome selector and right demo remain side by side.
- The workflow Timeline and Diagram should remain side by side when enough width is available.
- The demo stage should have a stable minimum height.

Tablet:

- The overall section may stack when the left selector and demo can no longer remain readable side by side.
- The demo may retain internal columns when they remain readable.
- The workflow Timeline and Diagram may stack if each panel would otherwise become too narrow.

Mobile:

- Use a single-column section layout.
- Keep outcome selectors full width.
- Place the demo below the selector.
- Allow the app demo to become a mobile representation instead of scaling down a desktop canvas.
- Stack sidebars, document previews, comments, workflow panels, audit lists, and detail panels as needed.
- Preserve readable text and touch targets.
- Do not introduce horizontal scrolling for the full page.

## Performance considerations

- Build demos from lightweight HTML and Fluent UI components.
- Do not load screenshots for every rotating state if the same visual can be rendered efficiently.
- Avoid mounting expensive PDF viewers, workflow engines, or production application contexts inside the marketing site.
- Keep all demo data static and local to the website bundle.
- Lazy-load the section or heavy demo assets if bundle analysis shows meaningful impact.
- Avoid timers continuing while the browser tab is hidden.
- Do not recreate all four demos on every rotation if state can be preserved safely.

## SEO considerations

- Keep the headline, supporting copy, and all four outcome labels in the initial HTML.
- Do not hide the business value exclusively inside a canvas or screenshot.
- Use semantic headings and articles so search engines can understand the section.
- Keep demo labels concise and product-realistic.
- Do not add unsupported structured data claims.

## Help documentation impact

This feature presents existing behavior on the public marketing website and does not change product behavior.

No help article update is expected if implementation only adds the marketing section and reorders homepage sections.

During implementation, verify every product statement against the existing help articles. If the implementation introduces or renames actual product behavior, follow the repository's required help documentation update process.

## Implementation sequence

### Step 1: Confirm final copy

- Review the eyebrow, headline, supporting copy, and four outcome labels.
- Confirm that all copy describes business-process outcomes.
- Confirm whether the section includes a Start Free CTA.

### Step 2: Build the section shell

- Create `WhyDocuHyphenSection` and its style file.
- Add the introduction, outcome tablist, rotation state, pause control, and demo container.
- Add reduced-motion behavior.
- Add complete keyboard behavior.

### Step 3: Build demo components

- Build the Blueprint process demo.
- Build the Workflow timeline and diagram demo.
- Build the participant coordination demo.
- Build the audit outcome demo.
- Keep all data fictional and static.

### Step 4: Integrate the homepage

- Import the new section into `website/src/App.tsx`.
- Reorder How it works and Built to adapt to your industry.
- Add any required surface wrapper styles to `website/src/AppStyles.tsx`.
- Verify navigation anchors and scroll behavior.

### Step 5: Responsive and accessibility refinement

- Verify desktop side-by-side behavior.
- Verify workflow Timeline and Diagram behavior.
- Verify tablet stacking.
- Verify mobile stacking at 360 CSS pixels.
- Verify keyboard selection and focus.
- Verify Pause rotation and Resume rotation.
- Verify reduced-motion behavior.
- Verify screen-reader relationships between tabs and panels.

### Step 6: Validation

Run from `website`:

```text
npm run lint
npm run build
```

Run the project-required TypeScript check from `web-app` after edits:

```text
npx tsc --noEmit
```

Visually verify at minimum:

- 1440 pixel desktop viewport.
- 1024 pixel desktop or tablet viewport.
- 768 pixel tablet viewport.
- 360 pixel mobile viewport.

Verify all four demo states and wait through at least one complete automatic rotation cycle.

## Acceptance criteria

- The section positions DocuHyphen as a platform for Document-Driven business processes.
- No primary copy frames the product as a document sharing site.
- The four business outcomes are always visible and understandable.
- Selecting an outcome displays the correct demo.
- Automatic rotation works and can be paused.
- Reduced-motion users are not subjected to automatic rotation.
- The Workflow demo shows both the runtime list and diagram on sufficiently wide screens.
- Every demo resembles the existing DocuHyphen app rather than a generic dashboard.
- The visual style matches the public website.
- The section is responsive without horizontal page overflow.
- All React components remain short and focused.
- Styles are separated and use Fluent UI tokens.
- Buttons are circular.
- Required element IDs are present.
- No unsupported product claims are introduced.
- The website lint and build commands pass.
- The required `web-app` TypeScript check passes.

## Decisions already made

- Use process-level positioning.
- Do not use the generic six-card concept.
- Use a rotating four-outcome section.
- Place outcomes on the left and a rendered app demo on the right.
- Use marketing-only demo components, not static generic illustrations.
- Show both workflow timeline and diagram for the workflow outcome.
- Match the public website's fixed light theme.
- Keep the demo responsive rather than scaling a desktop screenshot down on mobile.

## Open decisions for continuation

- Final approval of the headline and supporting copy.
- Whether the automatic interval remains 7 seconds.
- Whether selecting an outcome should pause rotation or restart the timer.
- Whether the section pauses automatically on hover and keyboard focus.
- Whether a single Start Free CTA appears below the rotator.
- Whether mobile uses stacked outcome buttons or a compact horizontal tab treatment.
- Whether the demo stage should preserve each panel's internal state after rotation.
- Whether the final production demo data uses `Client onboarding` or a more industry-neutral example.

## Continuation prompt

When resuming, use this instruction:

> Continue the DocuHyphen value section from `DOCUHYPHEN-VALUE-SECTION-IMPLEMENTATION-PLAN.md`. Review the plan and current website source, confirm the open copy and CTA decisions, then implement the rotating Why DocuHyphen section with four app-style demos. Preserve the platform positioning and follow all repository frontend rules.
