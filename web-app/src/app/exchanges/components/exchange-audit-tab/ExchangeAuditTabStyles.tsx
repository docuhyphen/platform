import {makeStyles, tokens} from "@fluentui/react-components";

export const useExchangeAuditTabStyles = makeStyles({
    container: {
        display: "flex",
        flexDirection: "column",
        gap: "8px",
        flex: 1,
        minHeight: 0,
        overflowY: "auto",
        padding: "8px 0",
    },
    spinner: {
        padding: "32px",
        alignSelf: "center",
    },
    errorText: {
        color: tokens.colorPaletteRedForeground1,
        padding: "16px",
    },
    emptyText: {
        color: tokens.colorNeutralForeground4,
        fontStyle: "italic",
        padding: "16px",
    },
    table: {
        width: "100%",
    },
    docLabel: {
        color: tokens.colorNeutralForeground3,
        marginBottom: "2px",
    },
    docGroup: {
        display: "flex",
        flexDirection: "column",
        gap: "4px",
        marginBottom: "16px",
    },
    docGroupHeader: {
        padding: "6px 0 4px 0",
        borderBottom: `1px solid ${tokens.colorNeutralStroke2}`,
        marginBottom: "2px",
    },
});
