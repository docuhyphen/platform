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
    },
    header: {
        display: "flex",
        justifyContent: "space-between",
        alignItems: "center",
        gap: tokens.spacingHorizontalL,
        padding: `${tokens.spacingVerticalL} ${tokens.spacingHorizontalXXL}`,
        borderBottom: `${tokens.strokeWidthThin} solid ${tokens.colorNeutralStroke2}`,
        "@media (max-width: 640px)": {
            padding: `${tokens.spacingVerticalM} ${tokens.spacingHorizontalL}`,
        },
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
        overflowY: "auto",
        "@media (max-width: 640px)": {
            padding: tokens.spacingHorizontalL,
        },
    },
    centered: {
        minHeight: "60vh",
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
    },
});
