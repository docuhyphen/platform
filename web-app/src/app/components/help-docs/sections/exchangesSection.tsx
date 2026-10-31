import {HelpDocSectionInput} from "../helpDocsRegistry";
import {manageAccessArticle} from "./articles/manageAccessArticle";
import {exchangeDocumentCardsArticle} from "./articles/exchangeDocumentCardsArticle";

export const exchangesSection: HelpDocSectionInput = {
    id: "exchanges",
    title: "Exchanges",
    articles: [
        {
            id: "exchange-document-cards",
            title: "Working with Exchange documents",
            content: exchangeDocumentCardsArticle,
        },
        {
            id: "manage-access",
            title: "Managing access & permissions",
            content: manageAccessArticle,
        },
        {
            id: "exchange-lifecycle",
            title: "Exchange Lifecycle",
            content: (
                <>
                    <p id={"exchange-lifecycle-intro-paragraph"}>
                        This guide explains the typical Exchange flow from creation to closure,
                        including validation points that keep teams aligned.
                    </p>

                    <h3 id={"exchange-lifecycle-when-to-use-this-heading"}>When to use this guide</h3>
                    <ul id={"exchange-lifecycle-when-to-use-this-list"}>
                        <li>Onboard a new operations or compliance team member.</li>
                        <li>Standardize how Exchanges are created and closed.</li>
                        <li>Troubleshoot status transition confusion.</li>
                    </ul>

                    <h3 id={"exchange-lifecycle-exchange-statuses-heading"}>Exchange statuses</h3>
                    <ul id={"exchange-lifecycle-exchange-statuses-list"}>
                        <li><b>Draft:</b> Exchange is created and prepared by the initiator.</li>
                        <li><b>Active:</b> Recipients can interact with the Exchange.</li>
                        <li><b>Completed/Ended:</b> Exchange is closed, moves to archive, and becomes read-only.</li>
                        <li><b>Rejected:</b> A recipient declined the Exchange, or an approval workflow rejected it.
                        </li>
                        <li><b>Rescinded:</b> The initiator cancelled the Exchange without deleting it. It moves to
                            archive and becomes read-only.
                        </li>
                    </ul>

                    <h3 id={"exchange-lifecycle-standard-lifecycle-steps-heading"}>Standard lifecycle steps</h3>
                    <ol id={"exchange-lifecycle-standard-lifecycle-steps-list"}>
                        <li>Create an Exchange with a clear title and intended recipients.</li>
                        <li>Upload required documents and verify file quality.</li>
                        <li>Send the Exchange and monitor recipient acceptance or rejection.</li>
                        <li>Address comments and updates while the Exchange is Active.</li>
                        <li>Rescind the Exchange if it was sent in error or needs to be withdrawn before normal
                            completion.
                        </li>
                        <li>End the Exchange once the business outcome is achieved.</li>
                        <li>Review audit history for compliance and record retention.</li>
                    </ol>

                    <h3 id={"exchange-lifecycle-information-requests-heading"}>
                        Ending an Exchange with open Information Requests
                    </h3>
                    <p id={"exchange-lifecycle-information-requests-help"}>
                        An Exchange cannot end while an Information Request that must finish first
                        is still open. The end dialog says how many of those you can see are waiting,
                        and the Exchange stays Active until they complete. When the only open requests
                        are ones that do not have to finish first, the dialog says how many are open
                        and offers <b> Cancel requests and end</b>, which cancels them and ends the
                        Exchange. Only someone who can cancel every one of them can do this; anyone
                        else is told that only the people who manage them can cancel them.
                        A request must finish first while <b>Must finish before the Exchange can
                        end</b> is on, as it is by default when the request is created. Whoever
                        manages the request can turn it off on the request&apos;s page until the
                        request finishes.
                    </p>

                    <h3 id={"exchange-lifecycle-plan-allowances-heading"}>Plan allowances</h3>
                    <p id={"exchange-lifecycle-plan-allowances-paragraph"}>
                        Free can create up to five new Exchanges per calendar month and keep up to
                        three Exchanges open at once. Draft and Active Exchanges count as open.
                        Personal and Business have no commercial Exchange count quota, subject to
                        security, file-size, and reasonable-use controls.
                    </p>
                    <p id={"exchange-lifecycle-plan-allowances-paragraph-2"}>
                        Open Settings, then Billing, to see the current plan. Free shows Exchanges
                        created this month, open Exchanges, and the monthly reset date. Authorized
                        Business billing users also see active seats and purchased capacity. Plans that
                        include Information Requests also list their allowances: open requests, acting
                        parties and evidence per request, and evidence across requests.
                        An active Personal or Business trial is labeled as a trial and shows its
                        end date and whole days remaining. An expired trial shows zero days remaining.
                        Ending a Personal trial changes the current plan to Free. Ending a Business
                        trial preserves readable Business data while its mutation access remains expired.
                        When eligible, use <b>Request Personal trial</b> or <b>Request Business trial</b>{" "}
                        in Billing. A pending request shows its review status and App Administrators are notified.
                        Use <b>Compare plans and features</b> in Billing to open the website pricing
                        comparison. Switching the active organization refreshes this summary without
                        requiring another sign-in.
                    </p>

                    <h3 id={"exchange-lifecycle-validation-checklist-heading"}>Validation checklist</h3>
                    <ul id={"exchange-lifecycle-validation-checklist-list"}>
                        <li>Correct recipients and permissions before the Exchange goes Active.</li>
                        <li>No sensitive files shared to unintended users.</li>
                        <li>Closure reasons are documented for completed Exchanges.</li>
                    </ul>
                </>
            ),
        },
    ],
};
