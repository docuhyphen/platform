import {makeStyles, tokens} from "@fluentui/react-components";

export const useOrganizationParingTabStyles = makeStyles({
    tabContainer: {
        display: "flex",
        flexDirection: "column",
        gap: "26px"
    },
    header: {
        display: "flex",
        marginBottom: "16px",
        justifyContent: "space-between",
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
    },
    acceptButtonIcon: {
        color: tokens.colorBrandForeground1
    },
    divider: {
        width: "300px"
    }
});