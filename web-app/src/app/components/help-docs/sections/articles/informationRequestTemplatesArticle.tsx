export const informationRequestTemplatesArticle = (
    <>
        <p id={"information-request-templates-intro"}>
            An Information Request Template is a reusable request: its sections, the items each section asks for,
            repeated groups,
            conditions, review stages, and settings. Publishing a Template creates a Version that requests are made
            from; a published
            Version never changes.
        </p>

        <h3 id={"information-request-templates-list-heading"}>Opening the Template list</h3>
        <p id={"information-request-templates-list-help"}>
            Open <b id={"information-request-templates-article-b-1"}>Settings</b>, then select <b id={"information-request-templates-article-b-2"}>Content - Information Requests</b>. Use
            <b id={"information-request-templates-article-b-3"}> My Templates</b> for personally owned Templates, <b id={"information-request-templates-article-b-4"}>Organization</b> for the active
            organization, and <b id={"information-request-templates-article-b-5"}>Platform</b> for platform Templates. Organization Owners and
            Administrators manage the organization&apos;s Templates and are the only members who see its
            unpublished drafts; other members see only published Versions. Platform administrators
            author platform Templates with the same editor; everyone else reads their published
            Versions. Select <b id={"information-request-templates-article-b-6"}>New Template</b> to create a draft in the chosen scope.
        </p>
        <p id={"information-request-templates-availability-help"}>
            The tab appears only when your plan or your active organization includes Information Requests, for platform
            administrators too: select such an organization to author platform Templates. Without it, the tab is not
            shown, and you can still respond to and review Information Requests shared with you. You author personal
            Templates on the Personal plan, or while an active organization whose plan includes Information Requests is
            selected. When new requests cannot be created, Templates stay readable but <b id={"information-request-templates-article-b-7"}>New Template</b> is not
            offered; reaching the open request limit does not stop Template authoring.
        </p>

        <h3 id={"information-request-templates-editor-heading"}>The editor</h3>
        <p id={"information-request-templates-editor-help"}>
            A draft opens in an editor with the tabs Sections, Groups, Conditions, Review, and Settings,
            and a Versions tab for what has been published.
            <b id={"information-request-templates-answer-role-label"}> Answered by</b> offers Contributor and Preparer,
            plus
            Attestor for a confirmation item; <b id={"information-request-templates-confirmation-role-label"}> Roles
            that confirm</b> offer all three.
            Subject identifies who or what the request is about and cannot answer or confirm, and Attestor cannot
            provide typed
            data or files. Saving and publishing refuse a Requirement that gives either duty to Subject or such an
            answer to Attestor.
        </p>
        <ul id={"information-request-templates-editor-list"}>
            <li id={"information-request-templates-article-li-1"}>
                <b id={"information-request-templates-article-b-8"}>Sections</b>: <b id={"information-request-templates-article-b-9"}>Add section</b>, then <b id={"information-request-templates-article-b-10"}>Add requirement</b> in a section. An item is a
                <b id={"information-request-templates-article-b-11"}> Typed answer</b> collected in a Field of the Template&apos;s request schema, a
                <b id={"information-request-templates-article-b-12"}> Document</b> with its evidence policy, or a <b id={"information-request-templates-article-b-13"}>Confirmation</b>. Publishing refuses a typed answer
                whose Field the
                Schema marks read-only. A Field that is not marked <b id={"information-request-templates-article-b-14"}>Public</b> can be answered only by people inside
                the request&apos;s
                owner, so issuing the request, or giving its answering role to someone outside, is refused naming the
                item; the editor
                notes this under <b id={"information-request-templates-article-b-15"}>Collected Field</b>. A document&apos;s accepted file types are picked from a list.
                Each item has a key,
                a prompt, help text, whether it is required, optional, or asked only when a condition is
                true, who answers it, how they may answer, which other answers they may give instead of a
                value, and whether it is reviewed. An answer given once can change until it is first
                submitted and stays fixed afterwards, even if that submission is withdrawn, unless a
                reviewer returns that answer for correction: a confirmation keeps its decision, and changing
                another answer cannot clear it through a condition. An item never submitted while shown can
                still be answered once it applies. Any
                label in <b id={"information-request-templates-article-b-16"}>Confidentiality compartment</b> marks the item as protected: people see and
                answer it only shortly after verifying again.
            </li>
            <li id={"information-request-templates-article-li-2"}>
                <b id={"information-request-templates-article-b-17"}>Groups</b>: a group repeats its items once per entry, with a minimum of zero or more and
                a maximum of at least one and no smaller than the minimum, and can be nested in another
                group. A group must hold at least one item somewhere beneath it. A Version holds at most 100 groups,
                nested at most five deep, each requiring at most 100 entries and allowing at most 1000.
            </li>
            <li id={"information-request-templates-article-li-3"}>
                <b id={"information-request-templates-article-b-18"}>Conditions</b>: a condition tests earlier answers and says what happens to hidden
                answers when it turns false. Only a Conditional item has a condition. The editor refuses
                unknown keys, unsupported tests, conditions that depend on each other in a loop, one that would clear or
                archive the answer
                of a role other than the one answering what it reads, one that reads an answer outside the item&apos;s
                own or an enclosing group and not given once for the whole request, and, when the request is submitted
                in parts, a condition that reads an answer from another part.
                A Version holds at most 500 items and 500 conditions, and a condition at most 50 tests.
                A condition reads an item&apos;s answer or a Field&apos;s value, never a confirmation. A document
                nobody chose an answer for counts as provided once its own files complete it.
                A compared value is entered as its Field&apos;s type: <b id={"information-request-templates-article-b-19"}>Yes</b> or <b id={"information-request-templates-article-b-20"}>No</b>, one of the Field&apos;s
                options (several, ticked, for <b id={"information-request-templates-article-b-21"}>is one of</b> and <b id={"information-request-templates-article-b-22"}>is none of</b>), a date or date and time,
                or a number such as 12 or 4.50. A choice must be one the Field offers; publishing checks this again.
            </li>
            <li id={"information-request-templates-article-li-4"}>
                <b id={"information-request-templates-article-b-23"}>Review</b>: review stages, their order, how each stage decides an item, and who may not
                review, such as the responding parties or reviewers of an earlier stage.
            </li>
            <li id={"information-request-templates-article-li-5"}>
                <b id={"information-request-templates-article-b-24"}>Settings</b>: the request schema, whether the request is submitted whole or in parts,
                and the purpose under which accepted answers may be reused. When it is submitted in parts, a
                document and any alternative that can complete it belong to the same part, so each submitted
                part holds every file that completes it; publishing checks this again. An alternative document is given
                once for the whole request or in the document&apos;s own group, and cannot name alternatives of its own.
                Typed answers are recorded against a Schema made for Information Requests, chosen under
                <b id={"information-request-templates-article-b-25"}> Used for</b> when the Schema is created in <b id={"information-request-templates-article-b-26"}>Content - Fields</b>. A Template can use a platform
                Schema, and an organization Template can also use the organization&apos;s Schemas; personal
                Fields and Schemas cannot be authored yet.
            </li>
        </ul>
    </>
);
