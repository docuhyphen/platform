import type {ReactNode} from "react";

/** One article as the sidebar lists and renders it, carrying the section it was composed from. */
export type HelpDocArticle = {
    id: string;
    sectionId: string;
    sectionTitle: string;
    title: string;
    content: ReactNode;
};

/** One section as its own file declares it, before the registry flattens it into articles. */
export type HelpDocSectionInput = {
    id: string;
    title: string;
    articles: Array<{
        id: string;
        title: string;
        content: ReactNode;
    }>;
};
