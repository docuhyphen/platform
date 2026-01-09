import {makeStyles} from "@fluentui/react-components";

export const useSharingSessionsStyles = makeStyles({

    container: {
        display: "flex",
        gap: "16px",
        height: "100%",
        width: "100%",
        padding: "76px 16px 16px 16px",
        boxSizing: "border-box"
    },
    containerNoSessions: {
        display: "flex",
        gap: "16px",
        height: "100%",
        width: "100%",
        padding: "76px 16px 16px 16px",
        boxSizing: "border-box",
        flexDirection: "column",
        justifyContent: "center",
        alignItems: "center",
    },
    sharingSessionDocumentsContainer: {
        display: "flex",
        flexDirection: "row",
        gap: "16px",
        flex: 1
    },

    sharingSessionDocumentsDetails: {
        width: "100%",
        border: "1px solid red"
    },

    sharingSessionDocumentsDetailsList: {
        width: "100%",
        border: "2px solid green"
    },

    detailsContainer: {
        flex: 1,
        display: "flex",
        flexDirection: "column",
        gap: "16px",
        width: "480px",
    },

    noSessionSelectedSection: {
        display: "flex",
        justifyContent: "center",
        alignItems: "center",
        height: "100%",
        width: "100%",
    },
    documentsSectionContainer: {
        display: "flex",
        minHeight: "200px",
        height: "100%"
    },
    documentsSection: {
        display: "flex",
        flexDirection: "column",
        gap: "8px",
        flex: "1",
        minWidth: "200px"
    },
});