import {HelpDocSectionInput} from "../helpDocsRegistry";
import {fieldsOverviewArticle} from "./articles/fieldsOverviewArticle";
import {usingExchangeFieldsArticle} from "./articles/usingExchangeFieldsArticle";

export const fieldsSection: HelpDocSectionInput = {
    id: "fields",
    title: "Fields & Schemas",
    articles: [
        {id: "fields-overview",        title: "Fields & Schemas overview",       content: fieldsOverviewArticle},
        {id: "using-exchange-fields",  title: "Using fields on an exchange",      content: usingExchangeFieldsArticle},
    ],
};
