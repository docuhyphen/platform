import {makeStyles, tokens} from "@fluentui/react-components";

export const useWorkflowDesignerStyles = makeStyles({
    formSectionCards: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingVerticalM,
        minWidth: 0,
        paddingBottom: tokens.spacingVerticalM,
    },
    formPage: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingVerticalM,
        minWidth: 0,
        minHeight: "100%",
    },
    formPageTransitionFrame: {
        minWidth: 0,
        minHeight: "100%",
        animationFillMode: "both",
        willChange: "opacity, transform",
        "@media (prefers-reduced-motion: reduce)": {
            animationName: "none",
            animationDuration: "0ms",
            transform: "none",
        },
    },
    formPageSlideLeft: {
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
    formPageSlideRight: {
        animationName: {
            from: {
                opacity: 0,
                transform: "translateX(-28px)",
            },
            to: {
                opacity: 1,
                transform: "translateX(0)",
            },
        },
        animationDuration: "190ms",
        animationTimingFunction: "cubic-bezier(0.2, 0, 0, 1)",
    },
    formNavigationHeader: {
        position: "sticky",
        top: 0,
        zIndex: 2,
        display: "flex",
        alignItems: "center",
        gap: tokens.spacingHorizontalS,
        paddingBlock: tokens.spacingVerticalS,
        backgroundColor: tokens.colorNeutralBackground2,
        border: `1px solid ${tokens.colorNeutralStroke2}`,
        borderRadius: tokens.borderRadiusMedium,
        paddingInline: tokens.spacingHorizontalS,
        flexShrink: 0,
    },
    container: {
        display: "flex",
        flexDirection: "column",
        gap: 0,
        height: "100%",
        maxHeight: "100%",
        minHeight: 0,
        overflow: "hidden",
    },

    // Keeps the back button row and the Form/Preview tabs visible while the
    // rest of the designer scrolls underneath. `top: 0` (not the settings
    // header height) matches the Document Library tab's sticky pattern: the
    // scrolling ancestor already reserves space for the fixed app header via
    // its own padding, so this only needs to stick once it reaches the top of
    // that scroll area, instead of double-offsetting and overlapping content
    // scrolled beneath it.
    stickyToolbar: {
        background: tokens.colorNeutralBackground1,
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingHorizontalM,
        paddingBottom: tokens.spacingVerticalS,
        flexShrink: 0,
        paddingInline: tokens.spacingHorizontalS,
        boxSizing: "border-box",
    },

    scrollableContent: {
        flex: 1,
        minHeight: 0,
        overflowY: "auto",
        overflowX: "hidden",
        overscrollBehavior: "contain",
        paddingInline: tokens.spacingHorizontalS,
        boxSizing: "border-box",
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingHorizontalXL,
    },
    splitContentShell: {
        flex: 1,
        height: 0,
        minHeight: 0,
        overflow: "hidden",
        paddingLeft: tokens.spacingHorizontalS,
        paddingRight: tokens.spacingHorizontalL,
        boxSizing: "border-box",
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingHorizontalXL,
    },

    applicabilitySection: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingHorizontalM,
    },

    splitContent: {
        display: "flex",
        alignItems: "stretch",
        gap: tokens.spacingHorizontalL,
        flex: 1,
        minHeight: 0,
        "@media (max-width: 1100px)": {
            flexDirection: "column",
        },
    },

    splitFormColumn: {
        flex: "0 0 40%",
        height: "100%",
        minWidth: 0,
        minHeight: 0,
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingHorizontalXL,
        overflowY: "auto",
        overflowX: "hidden",
        overscrollBehavior: "contain",
        paddingRight: tokens.spacingHorizontalXL,
        paddingBottom: tokens.spacingVerticalM,
        boxSizing: "border-box",
        "@media (max-width: 1100px)": {
            flex: "1 1 auto",
            width: "100%",
            height: "auto",
            paddingRight: tokens.spacingHorizontalM,
        },
    },

    splitDiagramColumn: {
        flex: "0 0 60%",
        height: "100%",
        minWidth: 0,
        minHeight: 0,
        display: "flex",
        flexDirection: "column",
        "@media (max-width: 1100px)": {
            flex: "1 1 auto",
            width: "100%",
            height: "auto",
        },
    },

    diagramPanel: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingHorizontalXL,
        flex: 1,
        height: "100%",
        minHeight: 0,
    },

    previewLoading: {
        display: "flex",
        justifyContent: "center",
        padding: tokens.spacingHorizontalXXXL,
    },
});
