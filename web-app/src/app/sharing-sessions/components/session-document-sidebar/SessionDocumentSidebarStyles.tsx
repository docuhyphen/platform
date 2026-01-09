import {makeStyles} from "@fluentui/react-components";

export const useSessionDocumentSidebarStyles = makeStyles({
    sidebarContainer: {
        backgroundColor: "transparent",
        minWidth: "400px",
        minHeight: "300px",
    },
    drawerHeader: {
        paddingTop: "0",
    },
    drawerBody: {
        ":last-child": {
            paddingBottom: "0",
        }
    },
    commentFieldContainer: {
        position: "sticky",
        bottom: "0",
        display: "flex",
        flexDirection: "row",
        gap: "4px",
    },
    commentField: {
        flex: 1,
    }
});