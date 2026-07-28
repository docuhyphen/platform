import {makeStyles, shorthands, tokens} from "@fluentui/react-components";

export const useAuditWorkspaceShellStyles = makeStyles({
    container: {
        display: "flex",
        flexDirection: "column",
        width: "100%",
        height: "100%",
        minHeight: 0,
        overflow: "hidden",
    },
    layout: {
        display: "flex",
        flexDirection: "row",
        flex: 1,
        minHeight: 0,
        ...shorthands.gap(tokens.spacingHorizontalXXXL),
        "@media (max-width: 768px)": {
            flexDirection: "column",
            ...shorthands.gap(0),
        },
    },
    sidebar: {
        flexShrink: 0,
        width: "13.125rem",
        minHeight: 0,
        overflowY: "auto",
        overflowX: "hidden",
        paddingInline: tokens.spacingHorizontalS,
        paddingTop: tokens.spacingVerticalXL,
        boxSizing: "border-box",
        "@media (max-width: 768px)": {
            display: "none",
        },
    },
    mobileMenuBar: {
        display: "none",
        "@media (max-width: 768px)": {
            display: "flex",
            alignItems: "center",
            flexShrink: 0,
            backgroundColor: tokens.colorNeutralBackground1,
            ...shorthands.gap(tokens.spacingHorizontalS),
            ...shorthands.padding(tokens.spacingVerticalSNudge, 0),
            ...shorthands.borderBottom("1px", "solid", tokens.colorNeutralStroke2),
        },
    },
    content: {
        display: "flex",
        flexDirection: "column",
        flex: 1,
        minWidth: 0,
        minHeight: 0,
        overflow: "hidden",
        boxSizing: "border-box",
        backgroundColor: tokens.colorNeutralBackground1,
        ...shorthands.borderRadius(tokens.borderRadiusLarge),
        ...shorthands.padding(tokens.spacingVerticalXL, tokens.spacingHorizontalXL),
        boxShadow: tokens.shadow4,
        "@media (max-width: 768px)": {
            ...shorthands.padding(tokens.spacingVerticalL, tokens.spacingHorizontalM),
        },
    },
});
