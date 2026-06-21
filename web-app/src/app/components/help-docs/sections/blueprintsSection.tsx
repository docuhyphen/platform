import {HelpDocSectionInput} from "../helpDocsRegistry";
import {blueprintOverviewArticle} from "./articles/blueprintOverviewArticle";
import {usingBlueprintsArticle} from "./articles/usingBlueprintsArticle";
import {managingBlueprintsArticle} from "./articles/managingBlueprintsArticle";
import {orgBlueprintsArticle} from "./articles/orgBlueprintsArticle";
import {blueprintPlatformArticle} from "./articles/blueprintPlatformArticle";

export const blueprintsSection: HelpDocSectionInput = {
    id: "blueprints",
    title: "Blueprints",
    articles: [
        {id: "blueprint-overview",    title: "Blueprints overview",                   content: blueprintOverviewArticle},
        {id: "using-blueprints",      title: "Starting an exchange from a blueprint", content: usingBlueprintsArticle},
        {id: "managing-blueprints",   title: "Creating and managing blueprints",      content: managingBlueprintsArticle},
        {id: "org-blueprints",        title: "Organization blueprints",               content: orgBlueprintsArticle},
        {id: "blueprint-platform",    title: "Platform blueprints",                   content: blueprintPlatformArticle},
    ],
};
