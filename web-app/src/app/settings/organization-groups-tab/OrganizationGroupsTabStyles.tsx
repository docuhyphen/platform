import {makeStyles} from "@fluentui/react-components";

export const useOrganizationGroupTabStyles = makeStyles({
    container: {
        display: "flex",
        flexDirection: "column",
        gap: "16px",
    },
    header: {
        display: "flex",
        marginBottom: "16px",
        justifyContent: "space-between",
    },
    searchBox: {
        flex: 1
    },
    error: {
        color: "red",
        marginBottom: "16px",
    },
    loading: {
        display: "flex",
        justifyContent: "center",
        padding: "24px",
    },
    table: {
        width: "100%",
    },
    actions: {
        display: "flex",
        gap: "8px",
    }
});