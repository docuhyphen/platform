export const recordPreservationArticle = (
    <>
        <p id={"record-preservation-intro"}>
            Record preservation keeps finished Information Requests for as long as they must be kept and then disposes
            of them. Open it with <b>Record preservation</b> on the Information Request operations page.
        </p>

        <h3 id={"record-preservation-access-heading"}>Who can use it</h3>
        <p id={"record-preservation-access-help"}>
            You always manage the records of your personal requests. In an organization, reading this page needs audit
            read access, placing and releasing holds needs legal hold management, and publishing retention needs
            retention management. Organization Owners and Administrators hold all three. Changing holds or retention
            also needs audit governance in the organization&apos;s Business subscription, or Information Requests in
            your plan for personal records. The page stays readable when the plan no longer includes Information
            Requests, and it says why new requests cannot be created. Switching to another organization or to your
            personal workspace shows only that workspace&apos;s holds, retention schedule, and disposals. When the
            holds,
            the retention schedule, or the disposals cannot be read, the page says why and offers <b>Try again</b>.
        </p>

        <h3 id={"record-preservation-holds-heading"}>Preservation holds</h3>
        <p id={"record-preservation-holds-help"}>
            A hold stops a record from being disposed of. Place one from a request&apos;s Records tab, or with
            <b> Place hold</b> on its row in the operations queue, with a reason and an optional case reference; it
            takes effect at once. Reasons allow up to 2048 characters and case
            references up to 256. It covers either the request only, or the request, its descendants, and
            the records it refers to. These pages place holds on requests only. A hold that is on the request&apos;s
            Exchange, its owner, or a subject it concerns also
            protects the request when that hold covers descendants and referenced records. A hold placed directly on
            one of the request&apos;s submissions, files, file versions, sent notices, or record exports also keeps
            the whole request from being disposed of. <b>Change scope</b> on an active hold switches what it covers,
            with a reason. Releasing a hold needs a reason and lifts only that hold. Changing the scope and releasing
            apply only to the hold as you opened it: when it changed in the meantime, the page shows the latest
            version and asks you to try again. The hold and its history stay on record: when it was placed, every
            scope change, and its release. <b>History</b> on a hold lists each of these with who made it, when, and why,
            and a scope change also says what the hold covered before. A hold names one of your own records of a kind
            that can be held; a record
            of another owner, or of a kind that is never disposed of here, is refused. A request&apos;s Records tab
            shows each hold that keeps it, but only people who can read this page see its reason, case reference, and
            who placed it; others see what the hold covers and since when.
        </p>

        <h3 id={"record-preservation-retention-heading"}>Retention schedule</h3>
        <p id={"record-preservation-retention-help"}>
            The Information Request retention schedule states the minimum number of days a finished request is kept
            and, optionally, the number of days after which it is disposed of automatically. A request is finished
            once it is closed, cancelled, superseded, or expired. Each publish adds a new version and the latest
            version applies. A publish applies only over the version you read: when another version was published
            first, the page shows it and asks you to publish again. Without a schedule, or without a disposal age,
            finished requests are kept. Retention and disposal ages are whole days from 0 to 365000, and a disposal age
            needs a minimum no longer than it. Publishing needs the current schedule: while it cannot be read, the page
            says so and no new version can be published.
        </p>

        <h3 id={"record-preservation-disposal-heading"}>Disposal</h3>
        <p id={"record-preservation-disposal-help"}>
            Disposal runs on a schedule. It disposes of a finished request once its disposal age has passed, no hold
            covers it, its owner is not operationally suspended (scheduled disposal waits until a suspension is
            lifted, and a disposal already under way finishes), and no live record still depends on it, such as a
            successor request, a carried-forward answer,
            a reused fact, or an answer another request confirmed from one of its facts. A follow-up that superseded a
            request is disposed of first, and the request it superseded after it. A participant account linked through
            the request keeps its link.
            A subject record export that also covers other requests goes with the first of them to be disposed of, and
            only once each other request it covers is finished, past its minimum retention, and not held, including by
            a hold on one of its records; until then that disposal is refused. While a disposal runs, no hold can be
            placed on the request, on the other requests its shared exports cover, or on any of their records,
            including a record added after the disposal began. Disposal claims the request, deletes its stored files,
            then removes its records, Shares, access links and sessions, and leaves a tombstone that counts what was
            removed. Deleted file locators are cleared. A stored file that another
            record still uses, including a record created while the claim is being made, is kept. An interrupted
            disposal continues from where it stopped. The Disposals list shows each claim&apos;s progress and what it
            deleted or kept.
        </p>

        <h3 id={"record-preservation-privacy-heading"}>Privacy requests</h3>
        <p id={"record-preservation-privacy-help"}>
            Access, export, restriction, and deletion requests about a subject are recorded on the Privacy tab of
            Information Request operations, with their purpose and policy basis; a correction is recorded from the
            request&apos;s page, under Corrections. An access or export request produces a verified record of every
            request about the subject, which <b>Download record</b> on the privacy request saves as a file. A record
            still being read when you leave the Privacy tab or switch workspace, or once the privacy records can no
            longer be read, is not saved. A correction adds a new, audited revision of a submitted answer without
            changing the submission. A restriction stops accepted facts about the subject from being promoted or offered
            for reuse until it is lifted with a short reason code, such as resolved.by.subject. A deletion claims every
            finished request about the subject at once and reports each request that is still open as kept. Requests
            of the deletion that refer to each other do not stop it. If any finished request is held, inside its minimum
            retention, or still needed by a record outside the deletion, the deletion is recorded as refused with that
            reason and nothing about the subject is deleted. A deletion reuses an existing disposal claim with its
            original basis. Repeating a restriction lists its affected requests and the restriction that applied,
            including after it is lifted. A record that includes
            confidential information asks you to verify again before it is read.
        </p>

        <h3 id={"record-preservation-membership-heading"}>When a member leaves</h3>
        <p id={"record-preservation-membership-help"}>
            When someone is removed from an organization, the request roles they hold on the organization&apos;s
            requests, such as contributor or reviewer, stay assigned to them unless the platform is configured to
            revoke them. Requests owned by other organizations are never changed.
        </p>
    </>
);
