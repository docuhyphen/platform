import type {HelpDocArticle} from "./helpDocTypes";
import {helpDocSections} from "./sections/helpDocSections";

export type {HelpDocArticle, HelpDocSectionInput} from "./helpDocTypes";

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
