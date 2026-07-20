import {makeStyles, tokens} from '@fluentui/react-components';

export const useExchangeDocumentPreviewerStyles = makeStyles({
    documentName: {
        display: "flex",
        flexDirection: "column",
        minWidth: 0,
        // Phones (enlarged reader): center the title block to match
        // the stacked column layout of the enlarged header.
        "@media (max-width: 768px)": {
            alignItems: "center",
            textAlign: "center",
            width: "100%",
        },
    },

    previewContainer: {
        display: "flex",
        flexDirection: "column",
        flex: "1",
        background: tokens.colorNeutralBackground1,
        overflow: "hidden",
        maxWidth: "100%",
        margin: "0 auto",
        minHeight: 0,
        // Without min-width: 0 the previewer (which is a flex item itself
        // inside documentsSection) would grow to its intrinsic content
        // width on narrow viewports, pushing the whole page horizontally.
        minWidth: 0,
        width: "100%",
        boxSizing: "border-box",
    },

    previewHeader: {
        display: "flex",
        flexDirection: "column",
        padding: tokens.spacingHorizontalS,
        marginBottom: tokens.spacingVerticalS,
        justifyContent: "center",
        alignItems: "center",
        width: "100%",
        minWidth: 0,
        boxSizing: "border-box",
        "@media (max-width: 768px)": {
            padding: tokens.spacingHorizontalXS,
            marginBottom: tokens.spacingVerticalXS,
        },
    },

    previewHeaderActions: {
        display: "flex",
        flexDirection: "row-reverse",
        justifyContent: "space-between",
        flex: 1,
        // Wrap the toolbar onto multiple rows when there isn't enough
        // horizontal room. This is what makes the preview controls
        // usable on phones / split panes.
        flexWrap: "wrap",
        gap: tokens.spacingHorizontalXS,
        alignItems: "center",
        maxWidth: "100%",
        minWidth: 0,
        "@media (max-width: 768px)": {
            justifyContent: "center",
            // row-reverse plus wrap on a narrow viewport reads oddly
            // (groups appear in reverse order on the new line); use a
            // normal row direction on phones so wrapped controls flow
            // top-to-bottom, left-to-right.
            flexDirection: "row",
            rowGap: tokens.spacingVerticalXS,
        },
    },

    pdfDocumentContainer: {
        justifyContent: "center",
        display: "flex",
        flex: "1",
        background: tokens.colorNeutralBackground1,
        border: `1px solid ${tokens.colorNeutralStroke2}`,
        // overflow: auto so a wider-than-viewport PDF page can scroll
        // horizontally inside the container rather than pushing the
        // sidebar/page out of view.
        overflow: "auto",
        maxWidth: "100%",
        margin: "0 auto",
        minWidth: "480px",
        minHeight: 0,
        padding: tokens.spacingHorizontalL,
        boxSizing: "border-box",
        width: "100%",
        // Drop the 480px floor on phones (smallest viewports are ~360px);
        // otherwise the previewer forces horizontal page scrolling.
        "@media (max-width: 768px)": {
            minWidth: 0,
            padding: tokens.spacingHorizontalS,
            // `pan-x pan-y pinch-zoom` keeps single-finger panning AND
            // pinch-zoom working inside the scroll container. (Plain
            // `pinch-zoom` disables panning, which made the PDF
            // un-scrollable on phones.)
            touchAction: "pan-x pan-y pinch-zoom",
        },
    },

    pdfDocument: {},

    enlargedPreviewContainer: {
        position: "fixed",
        top: 0,
        left: 0,
        right: 0,
        bottom: 0,
        width: "100%",
        height: "100%",
        boxSizing: "border-box",
        background: tokens.colorBackgroundOverlay,
        zIndex: 9999,
        padding: tokens.spacingHorizontalL,
        backgroundPosition: "relative",
        gap: tokens.spacingHorizontalXS,
        display: "flex",
        flexDirection: "column",
        justifyContent: "space-around",
        flex: 1,
        transformOrigin: "center center",
        animationName: {
            from: {
                opacity: 0,
                transform: "scale(0.985)",
            },
            to: {
                opacity: 1,
                transform: "scale(1)",
            },
        },
        animationDuration: "220ms",
        animationTimingFunction: "cubic-bezier(0.2, 0, 0, 1)",
        // Phones: drop all outer padding so the document area uses the
        // full viewport width edge-to-edge. The header and scroll pane
        // bring their own modest internal padding.
        "@media (max-width: 768px)": {
            padding: 0,
            gap: 0,
        },
    },

    enlargedPreviewContainerClosing: {
        animationName: {
            from: {
                opacity: 1,
                transform: "scale(1)",
            },
            to: {
                opacity: 0,
                transform: "scale(0.985)",
            },
        },
        animationDuration: "220ms",
        animationTimingFunction: "cubic-bezier(0.2, 0, 0, 1)",
    },

    enlargedPreviewHeader: {
        zIndex: 9999,
        margin: "0 auto",
        boxSizing: "border-box",
        background: tokens.colorNeutralBackground1,
        width: "100%",
        display: "flex",
        justifyContent: "space-between",
        padding: tokens.spacingHorizontalL,
        borderRadius: tokens.borderRadiusMedium,
        animationName: {
            from: {
                opacity: 0,
                transform: "translateY(-8px)",
            },
            to: {
                opacity: 1,
                transform: "translateY(0)",
            },
        },
        animationDuration: "220ms",
        animationTimingFunction: "cubic-bezier(0.2, 0, 0, 1)",
        // Phones: stack the title above the action toolbar and center
        // both so the enlarged reader header reads cleanly on a narrow
        // viewport (instead of squeezing the title and Exit button to
        // opposite edges of a cramped row).
        "@media (max-width: 768px)": {
            flexDirection: "column",
            alignItems: "center",
            gap: tokens.spacingHorizontalS,
            padding: `${tokens.spacingVerticalMNudge} ${tokens.spacingHorizontalM}`,
        },
    },

    enlargedPreviewHeaderClosing: {
        animationName: {
            from: {
                opacity: 1,
                transform: "translateY(0)",
            },
            to: {
                opacity: 0,
                transform: "translateY(-8px)",
            },
        },
        animationDuration: "180ms",
        animationTimingFunction: "cubic-bezier(0.2, 0, 0, 1)",
    },

    enlargedPreviewHeaderActions: {
        display: "flex",
        alignItems: "center",
        gap: tokens.spacingHorizontalXS,
        flexWrap: "wrap",
        justifyContent: "center",
        maxWidth: "100%",
        minWidth: 0,
    },

    pagesInputContainer: {
        display: "flex",
        alignItems: "center",
        gap: tokens.spacingHorizontalXXS,
        // Keep the page-navigation cluster together; on tight viewports
        // the surrounding toolbar wraps before this group is broken up.
        flexShrink: 0,
    },

    enlargedPdfDocumentContainer: {
        boxSizing: "border-box",
        background: tokens.colorNeutralBackground1,
        margin: "0 auto",
        flex: 1,
        width: "100%",
        overflow: "hidden",
        borderRadius: tokens.borderRadiusMedium,
        animationName: {
            from: {
                opacity: 0,
                transform: "translateY(8px)",
            },
            to: {
                opacity: 1,
                transform: "translateY(0)",
            },
        },
        animationDuration: "260ms",
        animationTimingFunction: "cubic-bezier(0.2, 0, 0, 1)",
    },

    enlargedPdfDocumentContainerClosing: {
        animationName: {
            from: {
                opacity: 1,
                transform: "translateY(0)",
            },
            to: {
                opacity: 0,
                transform: "translateY(8px)",
            },
        },
        animationDuration: "200ms",
        animationTimingFunction: "cubic-bezier(0.2, 0, 0, 1)",
    },

    enlargedPdfDocument: {
        boxSizing: "border-box",
    },

    pagesInput: {
        "& input": {
            width: "50px",
            textAlign: "right",
        },
        // Shrink the page-number field on phones; nobody types a 5-digit
        // page number on mobile and the saved pixels help the cluster
        // fit alongside the prev/next buttons without wrapping.
        "@media (max-width: 768px)": {
            "& input": {
                width: "36px",
            },
        },
    },

    pagesInputAfter: {
        whiteSpace: "nowrap",
        color: tokens.colorNeutralForeground2,
    },

    pdfPagesStack: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingHorizontalL,
        alignItems: "center",
    },

    pdfPageItem: {
        width: "fit-content",
    },

    pdfPagePlaceholder: {
        width: "var(--pdf-page-placeholder-width, 612px)",
        height: "var(--pdf-page-placeholder-height, 792px)",
        boxSizing: "border-box",
        backgroundColor: tokens.colorNeutralBackground3,
        border: `1px solid ${tokens.colorNeutralStroke2}`,
        borderRadius: tokens.borderRadiusSmall,
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        color: tokens.colorNeutralForeground3,
        fontSize: "12px",
    },

    fullscreenPreviewContainer: {
        background: tokens.colorNeutralBackground1,
    },

    enlargedReaderLayout: {
        display: "flex",
        flexDirection: "row",
        alignItems: "stretch",
        height: "100%",
        width: "100%",
        minHeight: 0,
        minWidth: 0,
        overflow: "hidden",
    },

    thumbnailSidebar: {
        width: "180px",
        minWidth: "180px",
        flexShrink: 0,
        borderRight: `1px solid ${tokens.colorNeutralStroke2}`,
        backgroundColor: tokens.colorNeutralBackground2,
        overflowY: "auto",
        overflowX: "hidden",
        padding: `${tokens.spacingVerticalMNudge} ${tokens.spacingHorizontalS}`,
        boxSizing: "border-box",
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingHorizontalMNudge,
        zIndex: 1,
        boxShadow: `inset -1px 0 0 ${tokens.colorNeutralStroke2}`,
        // Hide the thumbnail rail on phones - it would consume half the
        // viewport and isn't usable at that width. Page navigation falls
        // back to the page-number input in the toolbar.
        "@media (max-width: 768px)": {
            display: "none",
        },
    },

    thumbnailSidebarEmpty: {
        padding: `${tokens.spacingVerticalS} ${tokens.spacingHorizontalXS}`,
        color: tokens.colorNeutralForeground3,
        textAlign: "center",
    },

    thumbnailPageButton: {
        border: `1px solid ${tokens.colorNeutralStroke2}`,
        borderRadius: tokens.borderRadiusLarge,
        backgroundColor: tokens.colorNeutralBackground1,
        padding: tokens.spacingHorizontalSNudge,
        cursor: "pointer",
        display: "flex",
        flexDirection: "column",
        alignItems: "center",
        gap: tokens.spacingHorizontalXS,
        transition: "border-color 140ms ease, box-shadow 140ms ease",
        "&:hover": {
            boxShadow: `0 0 0 1px ${tokens.colorBrandStroke1}`,
        },
    },

    thumbnailPageButtonActive: {
        boxShadow: `0 0 0 1px ${tokens.colorBrandStroke1}`,
    },

    thumbnailPageNumber: {
        color: tokens.colorNeutralForeground2,
    },

    thumbnailPagePreview: {
        width: "100%",
        display: "flex",
        justifyContent: "center",
    },

    mainDocumentPane: {
        display: "flex",
        justifyContent: "center",
        flex: 1,
        minWidth: 0,
        minHeight: 0,
        overflow: "auto",
        padding: tokens.spacingHorizontalL,
        boxSizing: "border-box",
        // Phones (enlarged reader): zero out the padding so the page
        // renders at the FULL viewport width, and allow native
        // single-finger pan + pinch-zoom (parity with the inline
        // mobile container).
        "@media (max-width: 768px)": {
            padding: 0,
            touchAction: "pan-x pan-y pinch-zoom",
        },
    },

    pdfPageAnimated: {
        "& canvas": {
            transitionProperty: "width, height, transform",
            transitionDuration: "180ms",
            transitionTimingFunction: "cubic-bezier(0.2, 0, 0, 1)",
            transformOrigin: "top center",
            willChange: "width, height, transform",
        },
        "& .react-pdf__Page__textContent": {
            transition: "transform 180ms cubic-bezier(0.2, 0, 0, 1)",
            transformOrigin: "top center",
        },
        "& .react-pdf__Page__annotations": {
            transition: "transform 180ms cubic-bezier(0.2, 0, 0, 1)",
            transformOrigin: "top center",
        },
    },

    pdfLoadingContainer: {
        display: "flex",
        flexDirection: "column",
        alignItems: "center",
        justifyContent: "center",
        gap: tokens.spacingHorizontalMNudge,
        minHeight: "240px",
        border: `1px dashed ${tokens.colorNeutralStroke2}`,
        borderRadius: tokens.borderRadiusLarge,
        backgroundColor: tokens.colorNeutralBackground1,
        minWidth: "100%",
    },

    pdfLoadingText: {
        color: tokens.colorNeutralForeground2,
    },

    previewEmptyState: {
        display: "flex",
        flexDirection: "column",
        alignItems: "center",
        justifyContent: "center",
        gap: tokens.spacingHorizontalMNudge,
        minHeight: "280px",
        border: `1px dashed ${tokens.colorNeutralStroke2}`,
        borderRadius: tokens.borderRadiusLarge,
        backgroundColor: tokens.colorNeutralBackground1,
        padding: tokens.spacingHorizontalXL,
        textAlign: "center",
        margin: "auto",
        maxWidth: "380px"
    },

    previewEmptySubText: {
        color: tokens.colorNeutralForeground2,
    },

    previewErrorContainer: {
        display: "flex",
        flexDirection: "column",
        alignItems: "center",
        justifyContent: "center",
        gap: tokens.spacingHorizontalM,
        padding: tokens.spacingHorizontalXXXL,
        textAlign: "center",
    },

    dividerFullHeight: {
        height: "100%",
    },

    watermarkOverlay: {
        position: "absolute",
        inset: 0,
        pointerEvents: "none",
        overflow: "hidden",
        zIndex: 5,
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        userSelect: "none",
    },

    watermarkText: {
        transform: "rotate(-30deg)",
        opacity: "0.15",
        fontSize: "4rem",
        fontWeight: 700,
        color: tokens.colorNeutralForeground1,
        whiteSpace: "nowrap",
        letterSpacing: "0.2em",
    },

    pdfRelativeWrapper: {
        position: "relative",
    },
    hiddenControl: {
        display: "none",
    },
});
