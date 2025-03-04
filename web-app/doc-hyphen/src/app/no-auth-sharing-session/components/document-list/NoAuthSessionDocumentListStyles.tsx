import {makeStyles} from "@fluentui/react-components";

export const useNoAuthSessionDocumentListStyles = makeStyles({

    container: {
        display: "flex",
        flexDirection: "column",
        justifyContent: "space-between",
        gap: "16px",
    },

    documentContainer: {
        background: "white"
    },

    documentCard: {
        flex: 1,
    },

    documentCardHeader: {
        display: "flex",
        flexDirection: "column",
        gap: "8px",
        alignItems: "normal",
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
        justifyContent: "space-between",
    }
})