import {makeStyles, tokens} from "@fluentui/react-components";

export const useAuditWorkspaceStyles = makeStyles({
    container: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingVerticalM,
        width: "100%",
        minWidth: 0,
        height: "100%",
        minHeight: 0,
        overflow: "hidden",
    },
    scopeNote: {
        color: tokens.colorNeutralForeground3,
    },
    tabPanel: {
        display: "flex",
        flexDirection: "column",
        flex: 1,
        minWidth: 0,
        minHeight: 0,
        overflow: "hidden",
    },
    notAuthorized: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingVerticalS,
        padding: tokens.spacingVerticalXXL,
        textAlign: "center",
    },
});
