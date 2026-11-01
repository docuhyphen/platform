import React from "react";
import {
    Accordion,
    AccordionHeader,
    AccordionItem,
    AccordionPanel,
    Button,
    Input,
    mergeClasses,
} from "@fluentui/react-components";
import {SearchRegular} from "@fluentui/react-icons";
import type {HelpDocArticle} from "../helpDocTypes.ts";
import type {HelpDocSections} from "../useHelpDocumentationNavigation.ts";
import HelpDocumentationSearchResults from "./HelpDocumentationSearchResults.tsx";
import {useHelpDocumentationNavStyles} from "./HelpDocumentationNavStyles.tsx";

type HelpDocumentationNavProps = {
    sections: HelpDocSections;
    activeArticleId: string;
    openSection: string;
    onOpenSectionChange: (update: (previous: string) => string) => void;
    searchQuery: string;
    onSearchQueryChange: (value: string) => void;
    searchResults: HelpDocArticle[] | null;
    onSelectArticle: (articleId: string) => void;
};

const HelpDocumentationNav: React.FC<HelpDocumentationNavProps> = (
    {
        sections,
        activeArticleId,
        openSection,
        onOpenSectionChange,
        searchQuery,
        onSearchQueryChange,
        searchResults,
        onSelectArticle,
    }) =>
{
    const styles = useHelpDocumentationNavStyles();

    return (
        <div id={"help-documentation-sidebar-div-7"}
             className={styles.nav}>
            <div id={"help-documentation-sidebar-div-8"}
                 className={styles.searchContainer}>
                <Input
                    id={"help-documentation-sidebar-input-1"}
                    appearance="outline"
                    placeholder="Search articles…"
                    contentBefore={<SearchRegular/>}
                    value={searchQuery}
                    onChange={(_, data) => onSearchQueryChange(data.value)}
                    className={styles.searchInput}
                />
            </div>

            {searchResults ? (
                <HelpDocumentationSearchResults results={searchResults}
                                                activeArticleId={activeArticleId}
                                                onSelectArticle={onSelectArticle}/>
            ) : (
                <Accordion
                    id={"help-documentation-sidebar-accordion-1"}
                    collapsible
                    openItems={openSection ? [openSection] : []}
                    onToggle={(_, data) =>
                        onOpenSectionChange(prev => prev === data.value ? '' : data.value as string)
                    }
                >
                    {sections.map(section => (
                        <AccordionItem id={`help-documentation-sidebar-accordion-item-1-${section.id}`}
                                       key={section.id}
                                       value={section.id}>
                            <AccordionHeader id={"help-documentation-sidebar-accordion-header-1"}>{section.title}</AccordionHeader>
                            <AccordionPanel id={"help-documentation-sidebar-accordion-panel-1"}>
                                <div id={"help-documentation-sidebar-div-10"}
                                     className={styles.navArticleList}>
                                    {section.articles.map(article => (
                                        <Button
                                            id={`help-documentation-sidebar-button-2-${article.id}`}
                                            key={article.id}
                                            appearance="subtle"
                                            shape="circular"
                                            className={mergeClasses(
                                                styles.navArticleItem,
                                                article.id === activeArticleId && styles.navArticleItemActive,
                                            )}
                                            onClick={() => onSelectArticle(article.id)}
                                        >
                                            {article.title}
                                        </Button>
                                    ))}
                                </div>
                            </AccordionPanel>
                        </AccordionItem>
                    ))}
                </Accordion>
            )}
        </div>
    );
};

export default HelpDocumentationNav;
