import React, {useCallback, useEffect, useMemo, useRef, useState} from "react";
import {
    Accordion,
    AccordionHeader,
    AccordionItem,
    AccordionPanel,
    Button,
    Text,
    makeStyles,
    tokens,
} from "@fluentui/react-components";
import {
    ArrowLeftRegular,
    DismissFilled,
    Navigation24Regular,
    ReOrderDotsVertical20Regular,
} from "@fluentui/react-icons";
import {
    getDefaultHelpDocArticle,
    getHelpDocArticleById,
    getHelpDocSections,
} from "./helpDocsRegistry.tsx";

const DEFAULT_PANEL_WIDTH = 400;
const MIN_PANEL_WIDTH = 320;
const WINDOW_PADDING = 80;

const useStyles = makeStyles({
    panel: {
        position: "relative",
        height: "100%",
        backgroundColor: tokens.colorNeutralBackground1,
        borderLeft: `1px solid ${tokens.colorNeutralStroke2}`,
        display: "flex",
        flexDirection: "column",
        overflow: "hidden",
    },
    resizeHandle: {
        position: "absolute",
        left: 0,
        top: 0,
        bottom: 0,
        width: "16px",
        cursor: "col-resize",
        zIndex: 2,
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        ":hover > span": {
            color: tokens.colorBrandForeground1,
            backgroundColor: tokens.colorNeutralBackground1Hover,
        },
    },
    resizeGrip: {
        position: "absolute",
        top: "50%",
        left: "50%",
        transform: "translate(-50%, -50%)",
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        width: "16px",
        height: "32px",
        borderRadius: tokens.borderRadiusMedium,
        backgroundColor: tokens.colorNeutralBackground3,
        border: `1px solid ${tokens.colorNeutralStroke2}`,
        color: tokens.colorNeutralForeground3,
        cursor: "col-resize",
        pointerEvents: "none",
        transition: "color 0.15s, background-color 0.15s",
        boxShadow: tokens.shadow2,
    },
    header: {
        position: "sticky",
        top: 0,
        zIndex: 1,
        backgroundColor: tokens.colorNeutralBackground1,
        borderBottom: `1px solid ${tokens.colorNeutralStroke2}`,
        padding: "12px 16px",
        display: "flex",
        flexDirection: "column",
        gap: "8px",
    },
    breadcrumbRow: {
        display: "flex",
        flexWrap: "wrap",
        gap: "6px",
        color: tokens.colorNeutralForeground3,
    },
    crumbSeparator: {
        color: tokens.colorNeutralForeground4,
    },
    titleRow: {
        display: "flex",
        alignItems: "center",
        justifyContent: "space-between",
        gap: "8px",
    },
    titleText: {
        fontWeight: tokens.fontWeightSemibold,
        flex: 1,
        minWidth: 0,
    },
    headerActions: {
        display: "flex",
        alignItems: "center",
        gap: "2px",
        flexShrink: 0,
    },
    content: {
        flex: 1,
        overflowY: "auto",
        padding: "1.8rem",
        color: tokens.colorNeutralForeground2,
        lineHeight: tokens.lineHeightBase300,
        fontSize: tokens.fontSizeBase300,
        "& p": {
            margin: "0 0 12px",
        },
        "& ul, & ol": {
            margin: "0 0 12px",
            paddingLeft: "18px",
        },
        "& li": {
            marginBottom: "6px",
        },
        "& a[data-help-article]": {
            color: tokens.colorBrandForeground1,
            textDecorationLine: "underline",
            cursor: "pointer",
        },
    },
    nav: {
        flex: 1,
        overflowY: "auto",
        display: "flex",
        flexDirection: "column",
    },
    navArticleItem: {
        display: "block",
        width: "100%",
        textAlign: "left",
        padding: "6px 16px 6px 32px",
        border: "none",
        background: "none",
        cursor: "pointer",
        fontSize: tokens.fontSizeBase300,
        color: tokens.colorNeutralForeground2,
        lineHeight: tokens.lineHeightBase300,
        ":hover": {
            backgroundColor: tokens.colorNeutralBackground1Hover,
            color: tokens.colorNeutralForeground1,
        },
    },
    navArticleItemActive: {
        color: tokens.colorBrandForeground1,
        fontWeight: tokens.fontWeightSemibold,
    },
});

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
    const styles = useStyles();
    const sections = useMemo(() => getHelpDocSections(), []);
    const [activeArticleId, setActiveArticleId] = useState(getDefaultHelpDocArticle().id);
    const [showNav, setShowNav] = useState(false);
    const [openSection, setOpenSection] = useState<string>(sections[0]?.id ?? '');
    const [panelWidth, setPanelWidth] = useState(DEFAULT_PANEL_WIDTH);
    const dragStartRef = useRef<{ startX: number; startWidth: number } | null>(null);

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
        setShowNav(prev => !prev);
    };

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

    if (!isOpen) return null;

    return (
        <aside className={styles.panel}
               role="dialog"
               aria-label="Help documentation"
               style={{width: `${panelWidth}px`}}>

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
                            appearance="subtle"
                            shape="circular"
                            icon={showNav ? <ArrowLeftRegular/> : <Navigation24Regular/>}
                            aria-label={showNav ? "Back to article" : "Open documentation menu"}
                            onClick={handleNavToggle}
                        />
                        <Button
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
                                    <div style={{display: 'flex', flexDirection: 'column', paddingBottom: '4px'}}>
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
                </div>
            ) : (
                <div className={styles.content} onClick={onContentClick}>
                    {activeArticle.content}
                </div>
            )}
        </aside>
    );
};

export default HelpDocumentationSidebar;
