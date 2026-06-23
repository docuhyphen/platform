import {HelpDocSectionInput} from "../helpDocsRegistry";
import {communicationsOverviewArticle} from "./articles/communicationsOverviewArticle";
import {managingCommunicationsArticle} from "./articles/managingCommunicationsArticle";
import {communicationsInWorkflowsArticle} from "./articles/communicationsInWorkflowsArticle";

export const communicationsSection: HelpDocSectionInput = {
    id: "communications",
    title: "Communications",
    articles: [
        {id: "communications-overview",       title: "Communications overview",                   content: communicationsOverviewArticle},
        {id: "managing-communications",       title: "Managing communications",                   content: managingCommunicationsArticle},
        {id: "communications-in-workflows",   title: "Using communications in workflows",         content: communicationsInWorkflowsArticle},
    ],
};
