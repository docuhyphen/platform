import {makeStyles} from "@fluentui/react-components";

export const useSessionsTabStyles = makeStyles({
    container: {
        display: "flex",
        flexDirection: "column",
        gap: "16px",
    },
    header: {
        display: "flex",
        justifyContent: "space-between",
        alignItems: "center",
    },
    sessionCard: {
        display: "flex",
        flexDirection: "row",
        justifyContent: "space-between",
        alignItems: "flex-start",
        padding: "12px 16px",
        borderRadius: "8px",
        border: "1px solid #e0e0e0",
        gap: "16px",
    },
    sessionMeta: {
        display: "flex",
        flexDirection: "column",
        gap: "4px",
        flex: 1,
    },
});
