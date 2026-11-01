import {makeStyles, tokens} from "@fluentui/react-components";

export const useWorkflowDesignerStyles = makeStyles({
    container: {
        display: "flex",
        flexDirection: "column",
        gap: 0,
        height: "100%",
        maxHeight: "100%",
        minHeight: 0,
        overflow: "hidden",
    },

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
});
