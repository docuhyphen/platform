import {makeStyles, tokens} from "@fluentui/react-components";

export const useWorkflowGraphCanvasStyles = makeStyles({
    wrapper: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingHorizontalS,
        width: "100%",
    },
    wrapperFillHeight: {
        flex: 1,
        minHeight: 0,
    },

    canvasContainer: {
        position: "relative",
        width: "100%",
        height: "clamp(240px, 45vh, 600px)",
        border: `1px solid ${tokens.colorNeutralStroke2}`,
        borderRadius: tokens.borderRadiusMedium,
        backgroundColor: tokens.colorNeutralBackground2,
        overflow: "hidden",
        "@media (max-width: 480px)": {
            height: "clamp(240px, 60vh, 480px)",
        },

        // ── Theme the React Flow controls (zoom/fit/lock) to match the app's
        // secondary button appearance instead of the library's hard-coded white
        // defaults, in both light and dark themes.
        "--xy-controls-button-background-color": tokens.colorNeutralBackground1,
        "--xy-controls-button-background-color-hover": tokens.colorNeutralBackground1Hover,
        "--xy-controls-button-color": tokens.colorNeutralForeground1,
        "--xy-controls-button-color-hover": tokens.colorNeutralForeground1Hover,
        "--xy-controls-button-border-color": tokens.colorNeutralStroke1,
        "--xy-controls-box-shadow": tokens.shadow4,
    },
    directionToggle: {
        position: "absolute",
        right: tokens.spacingHorizontalM,
        bottom: tokens.spacingVerticalM,
        zIndex: 2,
    },
    directionToggleButton: {
        boxShadow: tokens.shadow4,
    },
    directionToggleIcon: {
        fontSize: "18px",
    },
    canvasContainerFillHeight: {
        flex: 1,
        minHeight: "240px",
        height: "100%",
        "@media (max-width: 480px)": {
            height: "100%",
        },
    },
    canvasContainerEnlarged: {
        position: "fixed",
        inset: tokens.spacingHorizontalL,
        width: "auto",
        height: "auto",
        zIndex: 1000,
        borderRadius: tokens.borderRadiusLarge,
        boxShadow: tokens.shadow64,
        "@media (max-width: 600px)": {
            inset: 0,
            borderRadius: 0,
        },
    },

    warningList: {
        margin: 0,
        paddingLeft: tokens.spacingHorizontalL,
    },

    // Applied in addition to canvasContainer while the browser Fullscreen API
    // is active on this element, so the diagram fills the whole screen instead
    // of staying clamped to its normal in-page size.
    canvasContainerFullscreen: {
        height: "100vh",
        borderRadius: 0,
    },
});
