import {HelpDocSectionInput} from "../helpDocsRegistry";
import {documentLibraryOverviewArticle} from "./articles/documentLibraryOverviewArticle";

export const documentLibrarySection: HelpDocSectionInput = {
    id: "document-library",
    title: "Document Library",
    articles: [
        {id: "document-library-overview", title: "Document Library overview", content: documentLibraryOverviewArticle},
    ],
};
