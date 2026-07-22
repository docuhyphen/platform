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
                    <p>
                        This guide explains the typical Exchange flow from creation to closure,
                        including validation points that keep teams aligned.
                    </p>

                    <h3>When to use this guide</h3>
                    <ul>
                        <li>Onboard a new operations or compliance team member.</li>
                        <li>Standardize how Exchanges are created and closed.</li>
                        <li>Troubleshoot status transition confusion.</li>
                    </ul>

                    <h3>Exchange statuses</h3>
                    <ul>
                        <li><b>Draft:</b> Exchange is created and prepared by the initiator.</li>
                        <li><b>Active:</b> Recipients can interact with the Exchange.</li>
                        <li><b>Completed/Ended:</b> Exchange is closed and no further changes should occur.</li>
                        <li><b>Rejected:</b> A recipient declined the Exchange, or an approval workflow rejected it.</li>
                        <li><b>Rescinded:</b> The initiator cancelled the Exchange without deleting it. It moves to archive and becomes read-only.</li>
                    </ul>

                    <h3>Standard lifecycle steps</h3>
                    <ol>
                        <li>Create an Exchange with a clear title and intended recipients.</li>
                        <li>Upload required documents and verify file quality.</li>
                        <li>Send the Exchange and monitor recipient acceptance or rejection.</li>
                        <li>Address comments and updates while the Exchange is Active.</li>
                        <li>Rescind the Exchange if it was sent in error or needs to be withdrawn before normal completion.</li>
                        <li>End the Exchange once the business outcome is achieved.</li>
                        <li>Review audit history for compliance and record retention.</li>
                    </ol>

                    <h3>Validation checklist</h3>
                    <ul>
                        <li>Correct recipients and permissions before the Exchange goes Active.</li>
                        <li>No sensitive files shared to unintended users.</li>
                        <li>Closure reasons are documented for completed Exchanges.</li>
                    </ul>
                </>
            ),
        },
    ],
};
