import {HelpDocSectionInput} from "../helpDocsRegistry";
import {informationRequestAccessArticle} from "./articles/informationRequestAccessArticle";
import {informationRequestConfirmationsArticle} from "./articles/informationRequestConfirmationsArticle";
import {informationRequestDueDatesArticle} from "./articles/informationRequestDueDatesArticle";
import {informationRequestEvidenceArticle} from "./articles/informationRequestEvidenceArticle";
import {informationRequestExternalSourcesArticle} from "./articles/informationRequestExternalSourcesArticle";
import {informationRequestIssuingArticle} from "./articles/informationRequestIssuingArticle";
import {informationRequestManagingArticle} from "./articles/informationRequestManagingArticle";
import {informationRequestOperationsArticle} from "./articles/informationRequestOperationsArticle";
import {informationRequestOperationsRecordsArticle} from "./articles/informationRequestOperationsRecordsArticle";
import {informationRequestOutcomesArticle} from "./articles/informationRequestOutcomesArticle";
import {informationRequestReviewArticle} from "./articles/informationRequestReviewArticle";
import {informationRequestReviewFindingsArticle} from "./articles/informationRequestReviewFindingsArticle";
import {informationRequestsOverviewArticle} from "./articles/informationRequestsOverviewArticle";
import {informationRequestSubmissionArticle} from "./articles/informationRequestSubmissionArticle";
import {informationRequestTemplatePublishingArticle} from "./articles/informationRequestTemplatePublishingArticle";
import {informationRequestTemplatesArticle} from "./articles/informationRequestTemplatesArticle";
import {informationRequestTemplateVersionsArticle} from "./articles/informationRequestTemplateVersionsArticle";
import {recordPreservationArticle} from "./articles/recordPreservationArticle";

export const informationRequestsSection: HelpDocSectionInput = {
    id: "information-requests",
    title: "Information Requests",
    articles: [
        {id: "information-requests-overview", title: "Information Requests overview", content: informationRequestsOverviewArticle},
        {id: "request-templates", title: "Information Request Templates", content: informationRequestTemplatesArticle},
        {
            id: "request-template-publishing",
            title: "Publishing Templates and conditional answers",
            content: informationRequestTemplatePublishingArticle
        },
        {
            id: "request-template-versions",
            title: "Template Versions and copies",
            content: informationRequestTemplateVersionsArticle
        },
        {id: "request-creating", title: "Creating and managing a request", content: informationRequestManagingArticle},
        {id: "request-issuing", title: "Issuing and following up a request", content: informationRequestIssuingArticle},
        {id: "request-due-dates", title: "Due dates and reminders", content: informationRequestDueDatesArticle},
        {id: "request-access", title: "Access links and respondent sessions", content: informationRequestAccessArticle},
        {id: "request-submission", title: "Answering and submitting a request", content: informationRequestSubmissionArticle},
        {
            id: "request-submission-confirmations",
            title: "Confirmations, partial submissions, and amendments",
            content: informationRequestConfirmationsArticle
        },
        {id: "request-evidence", title: "Information Request evidence files", content: informationRequestEvidenceArticle},
        {id: "request-review", title: "Reviewing Information Request submissions", content: informationRequestReviewArticle},
        {
            id: "request-review-findings",
            title: "Review findings, corrections, and appeals",
            content: informationRequestReviewFindingsArticle
        },
        {id: "request-outcomes", title: "Accepted facts, decisions, and corrections", content: informationRequestOutcomesArticle},
        {id: "request-external-sources", title: "External sources and imported values", content: informationRequestExternalSourcesArticle},
        {id: "request-operations", title: "Information Request operations", content: informationRequestOperationsArticle},
        {
            id: "request-operations-records",
            title: "Clocks, notices, and audit records",
            content: informationRequestOperationsRecordsArticle
        },
        {id: "record-preservation", title: "Record preservation, retention, and disposal", content: recordPreservationArticle},
    ],
};
