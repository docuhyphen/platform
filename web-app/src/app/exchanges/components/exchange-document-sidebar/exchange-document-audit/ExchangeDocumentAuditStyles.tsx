import {makeStyles, shorthands, tokens} from "@fluentui/react-components";

export const useExchangeDocumentAuditStyles = makeStyles({
    auditContainer: {
        display: "flex",
        flexDirection: "column",
        height: "100%",
        overflowY: "auto"
    },
    auditTable: {
        width: "100%"
    },
    spinner: {
        ...shorthands.margin(tokens.spacingVerticalXL, "auto"),
        display: "flex"
    },
    error: {
        color: tokens.colorPaletteRedForeground1,
        textAlign: "center",
        ...shorthands.margin(tokens.spacingHorizontalL)
    },
    noLogs: {
        textAlign: "center",
        color: tokens.colorNeutralForeground3,
        ...shorthands.padding(tokens.spacingHorizontalL)
    }
});