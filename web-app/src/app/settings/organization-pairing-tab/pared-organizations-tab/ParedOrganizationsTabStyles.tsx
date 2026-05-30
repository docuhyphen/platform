import {makeStyles} from "@fluentui/react-components";

export const useOrganizationParingTabStyles = makeStyles({
    tabContainer: {
        display: "flex",
        flexDirection: "row",
        gap: "16px",
        flexWrap: "wrap",
    },
    header: {
        display: "flex",
        marginBottom: "16px",
        justifyContent: "space-between",
        flexWrap: "wrap",
        gap: "8px",
    },
    container: {
        display: "flex",
        flexDirection: "column",
        width: "100%",
        minWidth: 0,
        // Wide tables scroll horizontally inside their own container on
        // small screens so they don't stretch the settings card.
        overflowX: "auto",
    },
    tabListContainer: {
        display: "flex",
        flexDirection: "row",
        gap: "36px"
    },
    tabs: {
        flexGrow: "1"
    }
});