import {makeStyles, shorthands} from "@fluentui/react-components";

export const useSessionDocumentCommentsStyles = makeStyles({

    container: {
        display: "flex",
        flexDirection: "column",
        height: "100%",
        position: "relative"
    },

    list: {
        padding: "4px 8px",
        display: "flex",
        flexDirection: "column",
        flexGrow: 1,
        gap: "8px",
        overflowY: "auto",
    },

    noComments: {
        textAlign: "center",
        color: "#666",
        ...shorthands.padding("16px")
    },

    commentFieldContainer: {
        display: "flex",
        gap: "4px",
        flexDirection: "column"
    },

    commentFieldContainerField: {
        display: "flex",
        paddingTop: "8px",
        gap: "4px",
        flex: "1"
    },

    commentField: {
        alignItems: "start",
        gap: "4px",
        width: "100%",
        transition: "* 0.2s ease",
    },

    commentCounterSend: {
        display: "flex",
        justifyContent: "space-between",
        flexDirection: "row",
        alignItems: "center"
    }
});