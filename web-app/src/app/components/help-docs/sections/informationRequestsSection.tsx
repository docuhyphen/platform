import {HelpDocSectionInput} from "../helpDocsRegistry";
import {informationRequestAccessArticle} from "./articles/informationRequestAccessArticle";
import {informationRequestEvidenceArticle} from "./articles/informationRequestEvidenceArticle";
import {informationRequestExternalSourcesArticle} from "./articles/informationRequestExternalSourcesArticle";
import {informationRequestManagingArticle} from "./articles/informationRequestManagingArticle";
import {informationRequestOperationsArticle} from "./articles/informationRequestOperationsArticle";
import {informationRequestOutcomesArticle} from "./articles/informationRequestOutcomesArticle";
import {informationRequestReviewArticle} from "./articles/informationRequestReviewArticle";
import {informationRequestsOverviewArticle} from "./articles/informationRequestsOverviewArticle";
import {informationRequestSubmissionArticle} from "./articles/informationRequestSubmissionArticle";
import {informationRequestTemplatesArticle} from "./articles/informationRequestTemplatesArticle";
import {recordPreservationArticle} from "./articles/recordPreservationArticle";

export const informationRequestsSection: HelpDocSectionInput = {
    id: "information-requests",
    title: "Information Requests",
    articles: [
        {id: "information-requests-overview", title: "Information Requests overview", content: informationRequestsOverviewArticle},
        {id: "request-templates", title: "Information Request Templates", content: informationRequestTemplatesArticle},
        {id: "request-creating", title: "Creating and managing a request", content: informationRequestManagingArticle},
        {id: "request-access", title: "Access links and respondent sessions", content: informationRequestAccessArticle},
        {id: "request-submission", title: "Answering and submitting a request", content: informationRequestSubmissionArticle},
        {id: "request-evidence", title: "Information Request evidence files", content: informationRequestEvidenceArticle},
        {id: "request-review", title: "Reviewing Information Request submissions", content: informationRequestReviewArticle},
        {id: "request-outcomes", title: "Accepted facts, decisions, and corrections", content: informationRequestOutcomesArticle},
        {id: "request-external-sources", title: "External sources and imported values", content: informationRequestExternalSourcesArticle},
        {id: "request-operations", title: "Information Request operations", content: informationRequestOperationsArticle},
        {id: "record-preservation", title: "Record preservation, retention, and disposal", content: recordPreservationArticle},
    ],
};
