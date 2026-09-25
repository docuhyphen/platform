export const informationRequestEvidenceArticle = (
    <>
        <p id={"information-request-evidence-intro"}>
            A Document Requirement in an Information Request collects files rather than a
            typed answer. In the response workspace each Document Requirement shows its
            files, their checks, and what is still needed.
        </p>

        <h3 id={"information-request-evidence-providing-heading"}>Providing files</h3>
        <ul id={"information-request-evidence-providing-list"}>
            <li>
                Select <b>Upload file</b> to add one file. Each upload becomes its own item,
                so a Requirement can hold several independent files.
            </li>
            <li>
                Select <b>Replace</b> on an item to add a corrected file as its next version.
                Earlier versions stay on record.
            </li>
            <li>
                Select <b>Withdraw</b> and give a reason when a file should no longer count.
                A withdrawn file stays on record; nothing is deleted.
            </li>
            <li>
                The same file cannot be provided twice for one Requirement.
            </li>
            <li>
                Once the part of the request holding a Requirement is submitted, its files cannot
                be added, replaced, or withdrawn until that submission is withdrawn.
            </li>
        </ul>

        <h3 id={"information-request-evidence-checks-heading"}>Checks on every file</h3>
        <p id={"information-request-evidence-checks-help"}>
            Every file is inspected for its real type, its page count, password protection,
            and whether it can be read. Password-protected, unreadable, and end-to-end
            encrypted files are kept but can never satisfy a Requirement.
        </p>
        <p id={"information-request-evidence-scanning-help"}>
            Files are not scanned for malware unless the deployment adds a malware scanner,
            and the workspace says so. Where a deployment requires scanning, a file counts only
            after it passes the scan and shows <b>Awaiting checks</b> until then. A file in which
            malware is detected is quarantined: nobody can open it. Withdraw it and provide a
            clean copy.
        </p>

        <h3 id={"information-request-evidence-rules-heading"}>What a Requirement accepts</h3>
        <p id={"information-request-evidence-rules-help"}>
            The Template&apos;s evidence policy decides how many files are needed and allowed,
            the accepted file types, size limits, page limits, and which details a file
            must state, such as its issuer, language, issue and expiry dates, the period it
            covers, a certification, or a signature. It can also limit a file&apos;s age or
            require that several files together cover a continuous period. A file the policy
            can never accept, such as the wrong type or one file too many, is refused at upload.
        </p>
        <p id={"information-request-evidence-limits-help"}>
            By default a file can be up to 25 MB when signed in and 10 MB through an access
            link. A request, and each respondent on it, also has an overall file and size
            limit, which counts withdrawn files because they are kept.
        </p>

        <h3 id={"information-request-evidence-status-heading"}>Requirement status</h3>
        <ul id={"information-request-evidence-status-list"}>
            <li><b>No files provided</b>: nothing has been uploaded yet.</li>
            <li><b>Awaiting checks</b>: a file is still being inspected or scanned.</li>
            <li><b>More files needed</b>: the files are acceptable but there are too few.</li>
            <li><b>Needs attention</b>: a file does not meet the policy; each file lists why.</li>
            <li>
                <b>Ready for review</b>: the Template lets a reviewer accept these files even
                though a detail is missing. Unsafe or expired files, and files still waiting for
                a required scan, are never offered for review.
            </li>
            <li><b>Complete</b>: every current file is accepted and enough are provided.</li>
            <li>
                <b>Waived</b> or <b>Waiver requested</b>: the Requirement was waived, either
                directly or pending approval, as the Template allows.
            </li>
        </ul>
        <p id={"information-request-evidence-alternative-help"}>
            When a Template names an alternative Requirement, completing the alternative also
            completes this one. Structured progress counts a Document Requirement complete
            only when its status is Complete, Ready for review, Waived, or Waiver requested,
            or when the respondent saves an allowed response that needs no file.
        </p>

        <h3 id={"information-request-evidence-opening-heading"}>Opening files</h3>
        <p id={"information-request-evidence-opening-help"}>
            PDF and image files can be previewed; every file can be downloaded. Where the
            deployment requires a production malware scan, a file is released only after it
            passes that scan, except to the person who uploaded it. Each download and preview
            is recorded.
        </p>

        <h3 id={"information-request-evidence-links-heading"}>Supporting evidence</h3>
        <p id={"information-request-evidence-links-help"}>
            A Template can say that an answer is supported by a requested document. Each
            answer is linked to the document requested for the same repeated item, or else
            for the nearest enclosing item or the request as a whole. An answer given once
            for the whole request is linked to the document of every repeated item when the
            document is requested only inside those items.
        </p>

        <h3 id={"information-request-evidence-availability-heading"}>When upload is unavailable</h3>
        <p id={"information-request-evidence-availability-help"}>
            Evidence upload is on by default. The workspace says upload is not available when a
            deployment switches it off, or when it requires malware scanning without a production
            scanner. Files already provided can still be viewed where they are released.
        </p>
    </>
);
