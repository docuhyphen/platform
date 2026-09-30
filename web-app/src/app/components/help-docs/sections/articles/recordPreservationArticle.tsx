export const recordPreservationArticle = (
    <>
        <p id={"record-preservation-intro"}>
            Record preservation keeps finished Information Requests for as long as they must be
            kept and then disposes of them. Open it with <b>Record preservation</b> on the
            Information Request operations page.
        </p>

        <h3 id={"record-preservation-access-heading"}>Who can use it</h3>
        <p id={"record-preservation-access-help"}>
            You always manage the records of your personal requests. In an organization, reading
            this page needs audit read access, placing and releasing holds needs legal hold
            management, and publishing retention needs retention management. Organization Owners
            and Administrators hold all three. Changing holds or retention also needs audit
            governance in the organization&apos;s Business subscription, or Information Requests in
            your plan for personal records. The page stays readable when the plan no longer includes
            Information Requests, and it says why new requests cannot be created.
        </p>

        <h3 id={"record-preservation-holds-heading"}>Preservation holds</h3>
        <p id={"record-preservation-holds-help"}>
            A hold stops a record from being disposed of. Place one from a request&apos;s Records
            tab with a reason and an optional case reference. It covers either the request only,
            or the request, its descendants, and the records it refers to. A hold on the
            request&apos;s Exchange, its owner, or a subject it concerns also protects the request
            when that hold covers descendants and referenced records. <b>Change scope</b> on an active
            hold switches what it covers, with a reason. Releasing a hold needs a reason and lifts only
            that hold. The hold and its history stay on record: when it was placed, every scope change,
            and its release.
        </p>

        <h3 id={"record-preservation-retention-heading"}>Retention schedule</h3>
        <p id={"record-preservation-retention-help"}>
            The Information Request retention schedule states the minimum number of days a
            finished request is kept and, optionally, the number of days after which it is disposed
            of automatically. A request is finished once it is closed, cancelled, superseded, or
            expired. Each publish adds a new version and the latest version applies. Without a
            schedule, or without a disposal age, finished requests are kept.
        </p>

        <h3 id={"record-preservation-disposal-heading"}>Disposal</h3>
        <p id={"record-preservation-disposal-help"}>
            Disposal runs on a schedule. It disposes of a finished request once its disposal age
            has passed, no hold covers it, and no live record still depends on it, such as a
            successor request, a carried-forward answer, a reused fact, or a linked participant
            account. Disposal claims the request, deletes its stored files, then removes its
            records and leaves a tombstone that counts what was removed. A stored file that another
            record still uses is kept. An interrupted disposal continues from where it stopped. The
            Disposals list shows each claim&apos;s progress and what it deleted or kept.
        </p>

        <h3 id={"record-preservation-privacy-heading"}>Privacy requests</h3>
        <p id={"record-preservation-privacy-help"}>
            Access, export, restriction, and deletion requests about a subject are recorded on the
            Privacy tab of Information Request operations, with their purpose and policy basis; a
            correction is recorded from the request&apos;s page, under Corrections. An access or export
            request produces a verified record of every request about the subject. A correction
            adds a new, audited revision of a submitted answer without changing the submission. A
            restriction stops accepted facts about the subject from being promoted or offered for
            reuse until it is lifted. A deletion is refused while any request about the subject is
            open, held, inside its minimum retention, or still needed by another record, and then
            nothing about the subject is deleted.
        </p>

        <h3 id={"record-preservation-membership-heading"}>When a member leaves</h3>
        <p id={"record-preservation-membership-help"}>
            When someone is removed from an organization, the request roles they hold on the
            organization&apos;s requests, such as contributor or reviewer, stay assigned to them
            unless the platform is configured to revoke them. Requests owned by other
            organizations are never changed.
        </p>
    </>
);
