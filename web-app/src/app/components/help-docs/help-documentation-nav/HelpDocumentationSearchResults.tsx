import React from "react";
import {Button, mergeClasses, Text} from "@fluentui/react-components";
import type {HelpDocArticle} from "../helpDocTypes.ts";
import {useHelpDocumentationNavStyles} from "./HelpDocumentationNavStyles.tsx";

type HelpDocumentationSearchResultsProps = {
    results: HelpDocArticle[];
    activeArticleId: string;
    onSelectArticle: (articleId: string) => void;
};

const HelpDocumentationSearchResults: React.FC<HelpDocumentationSearchResultsProps> = ({results, activeArticleId, onSelectArticle}) =>
{
    const styles = useHelpDocumentationNavStyles();

    return (
        <div id={"help-documentation-sidebar-div-9"}
             className={styles.navArticleList}>
            {results.length === 0 ? (
                <Text id={"help-documentation-sidebar-text-7"}
                      className={styles.searchNoResults}>No articles found</Text>
            ) : (
                results.map(article => (
                    <Button
                        id={`help-documentation-sidebar-button-1-${article.id}`}
                        key={article.id}
                        appearance="subtle"
                        shape="circular"
                        className={mergeClasses(
                            styles.navArticleItem,
                            styles.searchResultItem,
                            article.id === activeArticleId && styles.navArticleItemActive,
                        )}
                        onClick={() => onSelectArticle(article.id)}
                    >
                        <span id={`help-documentation-sidebar-span-2-${article.id}`}
                              className={styles.searchResultTitle}>{article.title}</span>
                        <span id={`help-documentation-sidebar-span-3-${article.id}`}
                              className={styles.searchResultSection}>{article.sectionTitle}</span>
                    </Button>
                ))
            )}
        </div>
    );
};

export default HelpDocumentationSearchResults;
