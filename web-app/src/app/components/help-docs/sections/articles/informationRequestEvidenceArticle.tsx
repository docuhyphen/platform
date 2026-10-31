export const informationRequestEvidenceArticle = (
    <>
        <p id={"information-request-evidence-intro"}>
            A Document Requirement in an Information Request collects files rather than a typed answer. In the response
            workspace each Document Requirement shows its files, their checks, and what is still needed. File sizes use
            KiB, MiB, and GiB, with 1,024 bytes in a KiB, in your number format.
        </p>
        <h3 id={"information-request-evidence-providing-heading"}>Providing files</h3>
        <ul id={"information-request-evidence-providing-list"}>
            <li>
                Select <b>Upload file</b> to add one file. Each upload becomes its own item, so a Requirement can hold
                several independent files. A request stores files within the evidence allowance it was issued with and
                the platform&apos;s upload limits.
            </li>
            <li>
                Select <b>Replace</b> on an item to add a corrected file as its next version. Earlier versions stay on
                record.
            </li>
            <li>
                When the evidence policy asks for details such as the issuer, dates, or the period a file covers,
                choosing a file opens <b>File details</b>. Fill in the details the file states; required ones are
                marked, a coverage period needs both its start and its end, and a listed issuer, jurisdiction, or
                language is chosen from the list. A replacement starts from the details of the version it replaces. Each
                item shows the details its latest version stated.
            </li>
            <li>
                Select <b>Withdraw</b> and give a reason when a file should no longer count. A withdrawn file stays on
                record; nothing is deleted. If withdrawing is interrupted, select it again with the same reason: the
                file is withdrawn once.
            </li>
            <li>
                A file belongs to the party that provided it. Anyone acting for that party, such as a member of its
                group, its delegate, or the account its participant linked, can replace or withdraw it; another party on
                the same Requirement adds its own files instead and sees that the file was provided by another party.
                Once the person who provided a file no longer acts for a party that answers the Requirement, any party
                that answers it can change the file. Removing a file is available only through the API, to anyone acting
                for the party that provided it and to people who manage the request&apos;s evidence; a removed file is
                then shown only to people who manage evidence.
            </li>
            <li>The same file cannot be provided twice for one Requirement.</li>
            <li>
                A progress bar shows how much of a file has been sent. If an upload is interrupted, or the
                Requirement&apos;s files changed while it was sent, the file and its details are kept on the page:
                select <b>Retry</b> to send it again against the latest files, which never adds it twice,
                or <b>Discard</b>.
                Each file that was not sent stays kept, also when you send another one. If an access-link session ends
                while it is sent, the file is still kept after you verify again. It stays with the party who chose it:
                another party or account responding in the tab does not see it.
            </li>
            <li>
                If a Requirement&apos;s files can no longer be read, for example after your verification lapses, its
                files and their actions are hidden and the page says why; select <b>Try again</b> to read them again. A
                file kept for <b>Retry</b> stays on the page and can be sent once its files are shown again.
            </li>
            <li>
                Once the part of the request holding a Requirement is submitted, its files cannot be added, replaced, or
                withdrawn until that submission is withdrawn, so <b>Upload file</b>, <b>Replace</b>,
                and <b>Withdraw</b> are
                not offered. The same holds while the request is closed or its changes are paused or stopped.
            </li>
        </ul>
        <h3 id={"information-request-evidence-checks-heading"}>Checks on every file</h3>
        <p id={"information-request-evidence-checks-help"}>
            Every file is inspected for its real type. A text file such as CSV or JSON is taken as the more specific
            type its declared type or file name gives, and a current Office file as the format its contents show; a name
            never makes a file a type its content does not support. PDF files are also checked for their page count,
            password protection, and whether they can be read; one whose content expands beyond 16 MB, or whose internal
            index is unusually large, counts as unreadable. Older Office files and ZIP-based files, including current
            Office formats, are checked for password protection and whether they can be read; one with more than 10,000
            entries or that expands beyond 256 MB counts as unreadable. PNG, JPEG, and GIF images are decoded to check
            they can be read; one larger than 100 million pixels, or a progressive JPEG larger than 25 million pixels,
            counts as unreadable. Other types are checked for their type only. Password-protected and unreadable files
            are kept but can never satisfy a Requirement. End-to-end encrypted files are refused, because their type
            cannot be checked and they cannot be scanned.
        </p>
        <p id={"information-request-evidence-scanning-help"}>
            Files are not scanned for malware unless the deployment adds a malware scanner, and the workspace says so.
            Where a deployment requires scanning, a file counts only after it passes the scan and shows <b>Awaiting
            checks</b> until then. A file in which malware is detected is quarantined: nobody can open it. Withdraw it
            and provide a clean copy.
        </p>
        <h3 id={"information-request-evidence-rules-heading"}>What a Requirement accepts</h3>
        <p id={"information-request-evidence-rules-help"}>
            The Template&apos;s evidence policy decides how many files are needed and allowed, the accepted file types,
            size limits, page limits, and which details a file must state, such as its issuer, language, issue and
            expiry dates, the period it covers, a certification, or a signature. It can also limit a file&apos;s age or
            require that several files together cover a continuous period. A file the policy can never accept, such as
            the wrong type or one file too many, is refused at upload.
        </p>
        <p id={"information-request-evidence-limits-help"}>
            By default a file can be up to 25 MiB when signed in and 10 MiB through an access link. A request, and each
            respondent on it, also has an overall file and size limit, which counts withdrawn files because they are
            kept. Files provided through an access link still count after the participant links an account.
        </p>
        <h3 id={"information-request-evidence-status-heading"}>Requirement status</h3>
        <ul id={"information-request-evidence-status-list"}>
            <li><b>No files provided</b>: nothing has been uploaded yet.</li>
            <li><b>Awaiting checks</b>: a file is still being inspected or scanned.</li>
            <li><b>More files needed</b>: the files are acceptable but there are too few.</li>
            <li><b>Needs attention</b>: a file does not meet the policy; each file lists why.</li>
            <li>
                <b>Ready for review</b>: the Template lets a reviewer accept these files even though a detail is
                missing. Unsafe or expired files, and files still waiting for a required scan, are never offered for
                review.
            </li>
            <li><b>Complete</b>: every current file is accepted and enough are provided.</li>
            <li>
                <b>Waived</b> or <b>Waiver requested</b>: the Requirement was waived, either directly or pending
                approval, as the Template allows.
            </li>
        </ul>
        <p id={"information-request-evidence-alternative-help"}>
            When a Template names an alternative Requirement, completing the alternative also completes this one, but
            only while the alternative is shown: if its condition is false or unknown, its kept files complete nothing.
            Structured progress counts a Document Requirement complete only when its status is Complete, Ready for
            review, Waived, or Waiver requested, when a partial answer has at least one accepted file (More files
            needed), or when the respondent saves an allowed response that needs no file.
        </p>
        <h3 id={"information-request-evidence-opening-heading"}>Opening files</h3>
        <p id={"information-request-evidence-opening-help"}>
            PDF and image files can be previewed; every file can be downloaded. Where the deployment requires a
            production malware scan, a file is released only after it passes that scan, except to the person who
            uploaded it, including after that participant links an account. Through an access link, a file is released
            only if its session is still current once the file has been read and, for a protected question, still
            verified. If the session has ended, the page asks you to verify again. A preview or download that is still
            being read when you leave the page, when another party or account is found responding in the tab, or once
            the Requirement no longer shows that file, for example because its files are hidden or a newer version
            replaced it, is not opened. Each download and preview is recorded. <b>Preview</b> opens a new tab at once
            and shows the file in it once the file has been read; a tab for a file that cannot be shown closes again. If
            your browser blocks the tab, the page says so: allow pop-ups for this site, or select <b>Download</b>.
        </p>
        <h3 id={"information-request-evidence-links-heading"}>Supporting evidence</h3>
        <p id={"information-request-evidence-links-help"}>
            A Template can say that an answer is supported by a requested document. Each answer is linked to the
            document requested for the same repeated item, or else for the nearest enclosing item or the request as a
            whole. An answer given once for the whole request is linked to the document of every repeated item when the
            document is requested only inside those items. In the response workspace, a linked answer names the document
            that supports it, and the document names each answer it supports.
        </p>
        <h3 id={"information-request-evidence-availability-heading"}>When upload is unavailable</h3>
        <p id={"information-request-evidence-availability-help"}>
            Evidence upload is on by default. The workspace says upload is not available when a deployment switches it
            off, or when it requires malware scanning without a production scanner. Files already provided can still be
            viewed where they are released.
        </p>
    </>
);
