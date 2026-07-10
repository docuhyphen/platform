import {makeStyles, tokens} from "@fluentui/react-components";

export const useExchangesStyles = makeStyles({

    container: {
        display: "flex",
        gap: "16px",
        height: "100%",
        width: "100%",
        padding: "76px 16px 16px 16px",
        boxSizing: "border-box",
        // Phones: stack the list and the details pane, and use the
        // `*PaneHidden` modifiers below to swap between them so users
        // see one focused surface at a time. The 60px header is fixed,
        // so we only need a small inline gap on top.
        "@media (max-width: 768px)": {
            flexDirection: "column",
            gap: "8px",
            padding: "68px 8px 8px 8px",
        },
    },

    /**
     * Wraps ExchangeList so we can hide the list pane on mobile when the
     * details pane is showing. On desktop the wrapper is `display: contents`
     * (it disappears for layout purposes) so ExchangeList behaves as a
     * direct flex child exactly like before; on mobile it becomes a real
     * flex item that fills the viewport and can be hidden via the
     * `listPaneHidden` modifier below.
     */
    listPaneWrapper: {
        display: "contents",
        "@media (max-width: 768px)": {
            display: "flex",
            flex: 1,
            minHeight: 0,
            width: "100%",
        },
    },

    listPaneHidden: {
        "@media (max-width: 768px)": {
            display: "none",
        },
    },

    detailsPaneHidden: {
        "@media (max-width: 768px)": {
            display: "none",
        },
    },
    detailsTransitionFrame: {
        flex: 1,
        minWidth: 0,
        minHeight: 0,
        display: "flex",
        animationFillMode: "both",
        willChange: "opacity, transform",
        "@media (prefers-reduced-motion: reduce)": {
            animationName: "none",
            animationDuration: "0ms",
            transform: "none",
        },
    },
    detailsSlideInFromRight: {
        animationName: {
            from: {
                opacity: 0,
                transform: "translateX(28px)",
            },
            to: {
                opacity: 1,
                transform: "translateX(0)",
            },
        },
        animationDuration: "190ms",
        animationTimingFunction: "cubic-bezier(0.2, 0, 0, 1)",
    },
    containerNoExchanges: {
        display: "flex",
        gap: "20px",
        height: "100%",
        width: "100%",
        padding: "76px 16px 16px 16px",
        boxSizing: "border-box",
        flexDirection: "column",
        justifyContent: "center",
        alignItems: "center",
        maxWidth: "640px",
        margin: "auto",
        textAlign: "center"
    },
    noExchangesIllustration: {
        width: "100%",
        maxWidth: "440px",
        height: "auto",
        color: "var(--colorBrandForeground1)",
        marginBottom: "8px"
    },
    exchangeDocumentsContainer: {
        display: "flex",
        flexDirection: "row",
        gap: "16px",
        flex: 1
    },

    exchangeDocumentsDetails: {
        width: "100%",
        border: `1px solid ${tokens.colorNeutralStroke2}`,
    },

    exchangeDocumentsDetailsList: {
        width: "100%",
        border: `1px solid ${tokens.colorNeutralStroke2}`,
    },

    detailsContainer: {
        position: "relative",
        flex: 1,
        display: "flex",
        flexDirection: "column",
        gap: "16px",
        width: "480px",
        minHeight: 0,
        // Take the entire viewport width on phones; the fixed 480px above
        // is only relevant for desktop multi-column flex layouts.
        "@media (max-width: 768px)": {
            width: "100%",
            gap: "8px",
        },
    },

    detailsContent: {
        display: "flex",
        flexDirection: "column",
        gap: "16px",
        transition: "opacity 180ms ease",
        opacity: 1,
        flex: 1,
        minHeight: 0,
    },

    detailsContentLoading: {
        opacity: 0.56,
    },

    detailsLoadingOverlay: {
        position: "absolute",
        inset: 0,
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        pointerEvents: "none",
    },

    noExchangeSelectedSection: {
        display: "flex",
        justifyContent: "center",
        alignItems: "center",
        height: "100%",
        width: "100%",
    },
    inboxEmptyDetailsContent: {
        display: "flex",
        flexDirection: "column",
        gap: "12px",
        alignItems: "center",
        maxWidth: "520px",
        textAlign: "center",
        padding: "16px"
    },
    inboxEmptyIllustration: {
        width: "100%",
        maxWidth: "340px",
        height: "auto",
        color: tokens.colorBrandForeground1
    },
    inboxEmptyActions: {
        display: "flex",
        gap: "8px",
        justifyContent: "center",
        flexWrap: "wrap"
    },
    documentsSectionContainer: {
        display: "flex",
        flex: 1,
        minHeight: 0,
        height: "auto",
        overflow: "hidden",
    },
    documentsSection: {
        display: "flex",
        flexDirection: "column",
        gap: "8px",
        flex: "1",
        minWidth: "200px",
        minHeight: 0,
        overflow: "hidden",
        // On phones drop the 200px min-width: it forces horizontal scroll
        // for narrow viewports otherwise.
        "@media (max-width: 768px)": {
            minWidth: 0,
        },
    },
    scrollableTabContent: {
        overflowY: "auto",
        overflowX: "hidden",
        scrollbarGutter: "stable",
    },
    noExchangeImg: {
        width: "300px"
    },
});
