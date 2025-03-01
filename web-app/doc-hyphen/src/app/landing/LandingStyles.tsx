import {makeStyles} from "@fluentui/react-components";

export const useLandingStyles = makeStyles({

    container: {
        display: "flex",
        gap: "16px",
        height: "100%",
        width: "100%",
        padding: "76px 16px 16px 16px",
        boxSizing: "border-box"
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

    documentInfoSidebar: {
        // width: "300px",
        // borderLeft: "1px solid rgba(0, 0, 0, .2)",
    },

    detailsContainer: {
        flex: 1,
        display: "flex",
        flexDirection: "column",
        gap: "16px",
        width: "480px"
    },

    noSessionSelectedSection: {
        display: "flex",
        justifyContent: "center",
        alignItems: "center",
        height: "100%",
        width: "100%",
    },
});