import {makeStyles, tokens} from "@fluentui/react-components";

export const useInformationRequestRespondentWorkspaceStyles = makeStyles({
    themeProvider: {
        width: "100%",
        height: "100%",
    },
    page: {
        display: "flex",
        flexDirection: "column",
        minHeight: "100%",
        height: "100%",
        backgroundColor: tokens.colorNeutralBackground2,
        color: tokens.colorNeutralForeground1,
        overflow: "hidden",
    },
    header: {
        display: "flex",
        justifyContent: "space-between",
        alignItems: "center",
        gap: tokens.spacingHorizontalL,
        padding: `${tokens.spacingVerticalL} ${tokens.spacingHorizontalXXL}`,
        borderBottom: `1px solid ${tokens.colorNeutralStroke2}`,
        backgroundColor: tokens.colorNeutralBackground1,
        flexWrap: "wrap",
    },
    titleGroup: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingVerticalXS,
        minWidth: 0,
    },
    mutedText: {
        color: tokens.colorNeutralForeground3,
    },
    content: {
        flex: 1,
        minHeight: 0,
        padding: tokens.spacingHorizontalXXL,
        boxSizing: "border-box",
        overflow: "hidden",
    },
    centered: {
        minHeight: "60vh",
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
    },
    proofPanel: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingVerticalM,
        width: "min(100%, 520px)",
        padding: tokens.spacingHorizontalXL,
        borderRadius: tokens.borderRadiusMedium,
        border: `1px solid ${tokens.colorNeutralStroke2}`,
        backgroundColor: tokens.colorNeutralBackground1,
        boxSizing: "border-box",
    },
    proofActions: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingVerticalM,
    },
    workspaceShell: {
        height: "100%",
        minHeight: 0,
    },
    error: {
        color: tokens.colorPaletteRedForeground1,
    },
});
