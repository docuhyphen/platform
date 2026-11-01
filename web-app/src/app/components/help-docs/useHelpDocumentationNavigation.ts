import React, {useCallback, useEffect, useMemo, useState} from "react";
import {
    getDefaultHelpDocArticle,
    getHelpDocArticleById,
    getHelpDocSections,
    HELP_DOC_ARTICLES,
} from "./helpDocsRegistry.tsx";

export type HelpDocSections = ReturnType<typeof getHelpDocSections>;

export const useHelpDocumentationNavigation = (requestedArticleId?: string) =>
{
    const sections = useMemo(() => getHelpDocSections(), []);
    const [activeArticleId, setActiveArticleId] = useState(getDefaultHelpDocArticle().id);
    const [showNav, setShowNav] = useState(false);
    const [openSection, setOpenSection] = useState<string>(sections[0]?.id ?? '');
    const [searchQuery, setSearchQuery] = useState('');

    useEffect(() =>
    {
        if (requestedArticleId && getHelpDocArticleById(requestedArticleId))
            setActiveArticleId(requestedArticleId);
    }, [requestedArticleId]);

    const activeArticle = getHelpDocArticleById(activeArticleId) ?? getDefaultHelpDocArticle();

    const handleNavToggle = () =>
    {
        if (!showNav)
        {
            const currentSection = sections.find(s => s.articles.some(a => a.id === activeArticleId));
            if (currentSection) setOpenSection(currentSection.id);
        }
        setSearchQuery('');
        setShowNav(prev => !prev);
    };

    const searchResults = useMemo(() =>
    {
        const q = searchQuery.trim().toLowerCase();
        if (!q) return null;
        return HELP_DOC_ARTICLES.filter(a =>
            a.title.toLowerCase().includes(q) || a.sectionTitle.toLowerCase().includes(q),
        );
    }, [searchQuery]);

    const selectArticle = (articleId: string) =>
    {
        setActiveArticleId(articleId);
        setShowNav(false);
    };

    const onContentClick = useCallback((event: React.MouseEvent<HTMLDivElement>) =>
    {
        const target = event.target as HTMLElement | null;
        const articleLink = target?.closest?.("[data-help-article]") as HTMLElement | null;
        const articleId = articleLink?.getAttribute("data-help-article");
        if (!articleId) return;
        event.preventDefault();
        const article = getHelpDocArticleById(articleId);
        if (article) setActiveArticleId(article.id);
    }, []);

    return {
        sections,
        activeArticle,
        activeArticleId,
        showNav,
        openSection,
        setOpenSection,
        searchQuery,
        setSearchQuery,
        searchResults,
        handleNavToggle,
        selectArticle,
        onContentClick,
    };
};
