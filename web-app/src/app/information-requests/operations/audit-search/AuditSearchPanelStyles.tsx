import {makeStyles, tokens} from "@fluentui/react-components";

export const useAuditSearchPanelStyles = makeStyles({
    panel: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingVerticalM,
        maxWidth: "1100px",
        minWidth: 0,
    },
    heading: {
        marginTop: 0,
        marginBottom: 0,
    },
    muted: {
        color: tokens.colorNeutralForeground2,
    },
    list: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingVerticalS,
        margin: 0,
        padding: 0,
        listStyleType: "none",
    },
    pager: {
        display: "flex",
        alignItems: "center",
        justifyContent: "flex-end",
        gap: tokens.spacingHorizontalM,
        flexWrap: "wrap",
    },
});
