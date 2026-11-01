import type {ReactNode} from "react";

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
