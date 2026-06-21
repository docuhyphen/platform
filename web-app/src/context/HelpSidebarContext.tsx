import {createContext, useContext} from "react";

type HelpSidebarContextValue = {
    openHelpArticle: (articleId: string) => void;
};

export const HelpSidebarContext = createContext<HelpSidebarContextValue>({
    openHelpArticle: () => {},
});

export const useHelpSidebar = () => useContext(HelpSidebarContext);
