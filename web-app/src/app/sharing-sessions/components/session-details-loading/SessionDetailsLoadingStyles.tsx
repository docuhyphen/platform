import {makeStyles, tokens} from "@fluentui/react-components";

export const useSessionDetailsLoadingStyles = makeStyles({

    container: {
        display: "flex",
        flexDirection: "column",
        gap: "16px",
        flex: 1,
    },

    heading: {
        display: "flex",
        flexDirection: "column",
        gap: "8px",
        border: `1px solid ${tokens.colorNeutralStroke2}`,
        width: "100%",
        padding: "16px",
        boxSizing: "border-box",
        borderRadius: "4px"
    },

    headerLine1: {
        display: "flex",
        flexDirection: "row",
        gap: "8px",
        justifyContent: "space-between",
    },

    headerLineDates: {
        display: "flex",
        gap: "8px"
    },

    createdDate: {
        width: "100px",
    },

    datesPipe: {
        width: "6px",
    },

    endDate: {
        width: "100px",
    },

    collapseIcon: {
        width: "24px",
    },

    headerLine2: {
        display: "flex",
        flexDirection: "row",
        gap: "8px",
        justifyContent: "space-between"
    },

    sessionName: {
        width: "300px",
    },

    sessionActions: {
        display: "flex",
        flexDirection: "row",
        gap: "8px",
    },

    sessionActionsMore: {
        width: "8px",
    },

    sessionDescription: {
        width: "400px",
    },

    documentSearch: {

        display: "flex",
        flexDirection: "row",
        gap: "8px",
    },

    documentSearchInput: {
        width: "300px",
    },

    documentCardListContainer: {
        overflow: "hidden",
        boxSizing: "border-box",
    },

    documentCardList: {
        display: "flex",
        flexDirection: "row",
        gap: "8px",
        padding: "2px 2px"
    },

    documentCard: {
        minWidth: "230px",
        display: "flex",
        flexDirection: "row",
        justifyContent: "space-between",
        gap: "8px",
        height: "70px",
    },

    documentTitle: {
        width: "150px",
    },

    documentUploadDate: {
        width: "100px",
        marginTop: "8px"
    },

    documentMoreOptions: {
        width: "8px",
    },

    pdfPreviewSection: {
        border: `1px solid ${tokens.colorNeutralStroke2}`,
        borderRadius: "4px",
        margin: "auto",
        width: "100%",
        maxWidth: "500px",
        flex: 1,
    },
});