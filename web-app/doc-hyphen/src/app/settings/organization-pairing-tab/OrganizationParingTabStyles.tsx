import {makeStyles, tokens} from "@fluentui/react-components";

export const useOrganizationParingTabStyles = makeStyles({
    tabContainer: {
        display: "flex",
        flexDirection: "row",
        gap: "16px"
    },
    header: {
        display: "flex",
        marginBottom: "16px",
        justifyContent: "end",
    },
    container: {
        display: "flex",
        flexDirection: "column",
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