import {HelpDocSectionInput} from "../helpDocsRegistry";
import {fieldsOverviewArticle} from "./articles/fieldsOverviewArticle";
import {informationRequestEvidenceArticle} from "./articles/informationRequestEvidenceArticle";
import {informationRequestSubmissionArticle} from "./articles/informationRequestSubmissionArticle";
import {informationRequestTemplatesArticle} from "./articles/informationRequestTemplatesArticle";
import {usingExchangeFieldsArticle} from "./articles/usingExchangeFieldsArticle";

export const fieldsSection: HelpDocSectionInput = {
    id: "fields",
    title: "Fields & Schemas",
    articles: [
        {id: "fields-overview",        title: "Fields & Schemas overview",       content: fieldsOverviewArticle},
        {id: "using-exchange-fields",  title: "Using fields on an exchange",      content: usingExchangeFieldsArticle},
        {id: "request-templates", title: "Information Request Templates", content: informationRequestTemplatesArticle},
        {id: "request-evidence", title: "Information Request evidence files", content: informationRequestEvidenceArticle},
        {id: "request-submission", title: "Submitting and following up Information Requests", content: informationRequestSubmissionArticle},
    ],
};
