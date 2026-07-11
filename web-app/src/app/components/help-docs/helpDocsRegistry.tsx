import type {ReactNode} from "react";
import {startHereSection}       from "./sections/startHereSection";
import {identitySection}        from "./sections/identitySection";
import {exchangesSection}       from "./sections/exchangesSection";
import {adminOperationsSection} from "./sections/adminOperationsSection";
import {workflowsSection}       from "./sections/workflowsSection";
import {blueprintsSection}      from "./sections/blueprintsSection";
import {variablesSection}       from "./sections/variablesSection";
import {fieldsSection}          from "./sections/fieldsSection";
import {communicationsSection}  from "./sections/communicationsSection";
import {documentLibrarySection}  from "./sections/documentLibrarySection";
export type HelpDocArticle = {
    id: string;
    sectionId: string;
    sectionTitle: string;
    title: string;
    content: ReactNode;
};
export type HelpDocSectionInput = {
    id: string;
    title: string;
    articles: Array<{
        id: string;
        title: string;
        content: ReactNode;
    }>;
};
const helpDocSections: HelpDocSectionInput[] = [
    startHereSection,
    identitySection,
    exchangesSection,
    adminOperationsSection,
    workflowsSection,
    blueprintsSection,
    variablesSection,
    fieldsSection,
    communicationsSection,
    documentLibrarySection,
];
export const HELP_DOC_ARTICLES: HelpDocArticle[] = helpDocSections.flatMap((section) =>
    section.articles.map((article) => ({
        id: article.id,
        sectionId: section.id,
        sectionTitle: section.title,
        title: article.title,
        content: article.content,
    })),
);
export function getDefaultHelpDocArticle(): HelpDocArticle
{
    return HELP_DOC_ARTICLES[0];
}
export function getHelpDocArticleById(articleId: string): HelpDocArticle | undefined
{
    return HELP_DOC_ARTICLES.find((article) => article.id === articleId);
}
export function getHelpDocSections()
{ return helpDocSections; }
