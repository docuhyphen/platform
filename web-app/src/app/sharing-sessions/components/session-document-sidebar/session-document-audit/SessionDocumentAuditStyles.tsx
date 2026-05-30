import {makeStyles, shorthands, tokens} from "@fluentui/react-components";

export const useSessionDocumentAuditStyles = makeStyles({
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
        ...shorthands.margin("20px", "auto"),
        display: "flex"
    },
    error: {
        color: "var(--colorPaletteRedForeground1)",
        textAlign: "center",
        ...shorthands.margin("16px")
    },
    noLogs: {
        textAlign: "center",
        color: tokens.colorNeutralForeground3,
        ...shorthands.padding("16px")
    }
});