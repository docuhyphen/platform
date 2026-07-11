import React, {useCallback, useEffect, useMemo, useRef, useState} from "react";
import {
    Accordion,
    AccordionHeader,
    AccordionItem,
    AccordionPanel,
    Button,
    Input,
    Portal,
    Text,
} from "@fluentui/react-components";
import {
    ArrowLeftRegular,
    DismissFilled,
    Navigation24Regular,
    ReOrderDotsVertical20Regular,
    SearchRegular,
} from "@fluentui/react-icons";
import {
    HELP_DOC_ARTICLES,
    getDefaultHelpDocArticle,
    getHelpDocArticleById,
    getHelpDocSections,
} from "./helpDocsRegistry.tsx";
import {useHelpDocumentationSidebarStyles} from "./HelpDocumentationSidebarStyles.tsx";

const DEFAULT_PANEL_WIDTH = 400;
const MIN_PANEL_WIDTH = 320;
const WINDOW_PADDING = 80;

function clampPanelWidth(width: number): number
{
    const maxWidth = Math.max(MIN_PANEL_WIDTH, window.innerWidth - WINDOW_PADDING);
    return Math.min(Math.max(width, MIN_PANEL_WIDTH), maxWidth);
}

type HelpDocumentationSidebarProps = {
    isOpen: boolean;
    onOpenChange: (isOpen: boolean) => void;
    requestedArticleId?: string;
};

const HelpDocumentationSidebar: React.FC<HelpDocumentationSidebarProps> = ({isOpen, onOpenChange, requestedArticleId}) =>
{
    const styles = useHelpDocumentationSidebarStyles();
    const sections = useMemo(() => getHelpDocSections(), []);
    const [activeArticleId, setActiveArticleId] = useState(getDefaultHelpDocArticle().id);
    const [showNav, setShowNav] = useState(false);
    const [openSection, setOpenSection] = useState<string>(sections[0]?.id ?? '');
    const [panelWidth, setPanelWidth] = useState(DEFAULT_PANEL_WIDTH);
    const [searchQuery, setSearchQuery] = useState('');
    const dragStartRef = useRef<{ startX: number; startWidth: number } | null>(null);
    const spacerRef = useRef<HTMLDivElement | null>(null);
    const panelRef = useRef<HTMLElement | null>(null);

    React.useEffect(() =>
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

    const onResizePointerMove = useCallback((event: PointerEvent) =>
    {
        if (!dragStartRef.current) return;
        const deltaX = dragStartRef.current.startX - event.clientX;
        setPanelWidth(clampPanelWidth(dragStartRef.current.startWidth + deltaX));
    }, []);

    const stopResize = useCallback(() =>
    {
        dragStartRef.current = null;
        window.removeEventListener("pointermove", onResizePointerMove);
        window.removeEventListener("pointerup", stopResize);
    }, [onResizePointerMove]);

    const onResizePointerDown = useCallback((event: React.PointerEvent<HTMLDivElement>) =>
    {
        event.preventDefault();
        dragStartRef.current = {startX: event.clientX, startWidth: panelWidth};
        window.addEventListener("pointermove", onResizePointerMove);
        window.addEventListener("pointerup", stopResize);
    }, [onResizePointerMove, panelWidth, stopResize]);

    useEffect(() =>
    {
        const onWindowResize = () => setPanelWidth((width) => clampPanelWidth(width));
        window.addEventListener("resize", onWindowResize);
        return () =>
        {
            window.removeEventListener("resize", onWindowResize);
            stopResize();
        };
    }, [stopResize]);

    useEffect(() =>
    {
        const width = `${panelWidth}px`;
        spacerRef.current?.style.setProperty("--help-doc-panel-width", width);
        panelRef.current?.style.setProperty("--help-doc-panel-width", width);
    }, [panelWidth]);

    if (!isOpen) return null;

    return (
        <>
            {/* In-place flex spacer  -  reserves the same width in the layout so content is pushed left */}
            <div
                ref={spacerRef}
                className={styles.spacer}
                aria-hidden
            />

            {/* Portal renders the actual panel above any dialog overlay */}
            <Portal>
        <aside ref={panelRef}
               className={styles.panel}
               role="dialog"
               aria-label="Help documentation">

            <div className={styles.resizeHandle}
                 onPointerDown={onResizePointerDown}
                 aria-label="Drag to resize help panel"
                 title="Drag to resize">
                <span className={styles.resizeGrip} aria-hidden>
                    <ReOrderDotsVertical20Regular/>
                </span>
            </div>

            <div className={styles.header}>
                <div className={styles.breadcrumbRow}>
                    <Text size={200}>Help</Text>
                    <Text size={200} className={styles.crumbSeparator}>/</Text>
                    <Text size={200}>{activeArticle.sectionTitle}</Text>
                    {!showNav && (
                        <>
                            <Text size={200} className={styles.crumbSeparator}>/</Text>
                            <Text size={200}>{activeArticle.title}</Text>
                        </>
                    )}
                </div>

                <div className={styles.titleRow}>
                    <Text className={styles.titleText}>
                        {showNav ? "Contents" : activeArticle.title}
                    </Text>
                    <div className={styles.headerActions}>
                        <Button
                            id={"help-nav-toggle-btn"}
                            appearance="subtle"
                            shape="circular"
                            icon={showNav ? <ArrowLeftRegular/> : <Navigation24Regular/>}
                            aria-label={showNav ? "Back to article" : "Open documentation menu"}
                            onClick={handleNavToggle}
                        />
                        <Button
                            id={"help-close-btn"}
                            appearance="subtle"
                            shape="circular"
                            icon={<DismissFilled/>}
                            aria-label="Close help panel"
                            onClick={() => onOpenChange(false)}
                        />
                    </div>
                </div>
            </div>

            {showNav ? (
                <div className={styles.nav}>
                    <div className={styles.searchContainer}>
                        <Input
                            appearance="outline"
                            placeholder="Search articles…"
                            contentBefore={<SearchRegular/>}
                            value={searchQuery}
                            onChange={(_, data) => setSearchQuery(data.value)}
                            className={styles.searchInput}
                        />
                    </div>

                    {searchResults ? (
                        <div className={styles.navArticleList}>
                            {searchResults.length === 0 ? (
                                <Text className={styles.searchNoResults}>No articles found</Text>
                            ) : (
                                searchResults.map(article => (
                                    <button
                                        key={article.id}
                                        className={`${styles.navArticleItem}${article.id === activeArticleId ? ` ${styles.navArticleItemActive}` : ''}`}
                                        onClick={() => selectArticle(article.id)}
                                    >
                                        <span className={styles.searchResultTitle}>{article.title}</span>
                                        <span className={styles.searchResultSection}>{article.sectionTitle}</span>
                                    </button>
                                ))
                            )}
                        </div>
                    ) : (
                        <Accordion
                            collapsible
                            openItems={openSection ? [openSection] : []}
                            onToggle={(_, data) =>
                                setOpenSection(prev => prev === data.value ? '' : data.value as string)
                            }
                        >
                            {sections.map(section => (
                                <AccordionItem key={section.id} value={section.id}>
                                    <AccordionHeader>{section.title}</AccordionHeader>
                                    <AccordionPanel>
                                        <div className={styles.navArticleList}>
                                            {section.articles.map(article => (
                                                <button
                                                    key={article.id}
                                                    className={`${styles.navArticleItem}${article.id === activeArticleId ? ` ${styles.navArticleItemActive}` : ''}`}
                                                    onClick={() => selectArticle(article.id)}
                                                >
                                                    {article.title}
                                                </button>
                                            ))}
                                        </div>
                                    </AccordionPanel>
                                </AccordionItem>
                            ))}
                        </Accordion>
                    )}
                </div>
            ) : (
                <div className={styles.content} onClick={onContentClick}>
                    {activeArticle.content}
                </div>
            )}
        </aside>
            </Portal>
        </>
    );
};

export default HelpDocumentationSidebar;
