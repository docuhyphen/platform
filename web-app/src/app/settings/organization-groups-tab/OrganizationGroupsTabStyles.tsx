import {makeStyles, tokens} from "@fluentui/react-components";

export const useOrganizationGroupTabStyles = makeStyles({
    container: {
        display: "flex",
        flexDirection: "column",
        gap: "16px",
        width: "100%",
        minWidth: 0,
    },
    header: {
        display: "flex",
        marginBottom: "16px",
        justifyContent: "space-between",
        alignItems: "center",
        flexWrap: "wrap",
        gap: "8px",
    },
    searchBox: {
        flex: 1
    },
    error: {
        color: tokens.colorStatusDangerForeground1,
        marginBottom: "16px",
    },
    loading: {
        display: "flex",
        justifyContent: "center",
        padding: "24px",
    },
    // Keep tabular data legible on phones by letting the table scroll
    // horizontally inside its own container instead of forcing the
    // settings card to grow wider than the viewport.
    table: {
        width: "100%",
        overflowX: "auto",
        minWidth: 0,
    },
    actions: {
        display: "flex",
        gap: "8px",
    }
});