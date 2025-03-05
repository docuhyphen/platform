import {makeStyles} from "@fluentui/react-components";

export const useNoAuthSessionDocumentListStyles = makeStyles({

    container: {
        display: "flex",
        flexDirection: "column",
        justifyContent: "space-between",
        gap: "16px",
    },

    documentContainer: {
        background: "white",
    },

    documentCard: {
        flex: 1,
        padding: "16px",
    },

    documentCardHeader: {
        display: "flex",
        flexDirection: "column",
        gap: "8px",
        alignItems: "normal",
        border: "4px"
    },

    documentName: {
        display: "flex",
        justifyContent: "space-between",
        alignItems: "center",
        flex: 1
    },

    documentActions: {
        display: "flex",
        gap: "8px",
        flexDirection: "column",
    },

    documentActionsLine1: {
        display: "flex",
        gap: "8px",
        justifyContent: "space-between",
    },

    documentActionsLine2: {},

    uploadButton1: {
        position: "relative",
    },

    uploadButton2: {
        position: "absolute",
        opacity: 0,
        top: 0,
        left: 0,
        right: 0,
        bottom: 0,
        cursor: "pointer",
    }

})