import {HelpDocSectionInput} from "../helpDocsRegistry";
import {variablesOverviewArticle} from "./articles/variablesOverviewArticle";
import {usingVariableTokensArticle} from "./articles/usingVariableTokensArticle";
import {managingOrgVariablesArticle} from "./articles/managingOrgVariablesArticle";
import {managingPersonalVariablesArticle} from "./articles/managingPersonalVariablesArticle";
import {managingSequencesArticle} from "./articles/managingSequencesArticle";

export const variablesSection: HelpDocSectionInput = {
    id: "variables",
    title: "Variables & Sequences",
    articles: [
        {id: "variables-overview",          title: "Variables & Sequences overview",    content: variablesOverviewArticle},
        {id: "using-variable-tokens",       title: "Using variable tokens in blueprints and exchanges", content: usingVariableTokensArticle},
        {id: "managing-org-variables",      title: "Managing organization variables",   content: managingOrgVariablesArticle},
        {id: "managing-personal-variables", title: "Managing personal variables",       content: managingPersonalVariablesArticle},
        {id: "managing-sequences",          title: "Managing sequences",                content: managingSequencesArticle},
    ],
};
