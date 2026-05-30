import {makeStyles, tokens} from "@fluentui/react-components";

export const useOrganizationParingTabStyles = makeStyles({
    tabContainer: {
        display: "flex",
        flexDirection: "column",
        gap: "26px",
        width: "100%",
        minWidth: 0,
        // Tables inside this section can be wide; let them scroll
        // horizontally rather than expanding the parent settings card.
        overflowX: "auto",
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
        width: "300px",
        maxWidth: "100%",
        "@media (max-width: 768px)": {
            width: "100%",
        },
    }
});