import {makeStyles, tokens} from '@fluentui/react-components';

export const useExchangeDocumentPreviewerStyles = makeStyles({
    previewContainer: {
        position: "relative",
        display: "flex",
        flexDirection: "column",
        flex: "1",
        background: tokens.colorNeutralBackground1,
        overflow: "hidden",
        maxWidth: "100%",
        margin: "0 auto",
        minHeight: 0,
        minWidth: 0,
        width: "100%",
        boxSizing: "border-box",
    },

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

    fullscreenPreviewContainer: {
        background: tokens.colorNeutralBackground1,
    },

    enlargedReaderLayout: {
        display: "flex",
        flexDirection: "row",
        alignItems: "stretch",
        gap: tokens.spacingHorizontalS,
        height: "100%",
        width: "100%",
        minHeight: 0,
        minWidth: 0,
        overflow: "hidden",
        "@media (max-width: 768px)": {
            flexDirection: "column",
        },
    },
});
