import {makeStyles, tokens} from "@fluentui/react-components";

export const useLandingStyles = makeStyles({
    sharingSessionsContainer: {
        display: "flex",
        gap: "16px",
        height: "100%",
    },
    sharingSessionsContainerDiv: {
        padding: "18px 0",
    },
    sharingSessionHeadContainer: {
        display: "flex",
        justifyContent: "space-between",
        alignItems: "center",
        gap: "8px",
        background: "white",
        padding: "0 16px",
        height: "100px",
        borderRadius: "4px",
        borderTop: "1px solid rgba(0, 0, 0, .2)",
        borderRight: "1px solid rgba(0, 0, 0, .2)",
        borderBottom: "1px solid rgba(0, 0, 0, .2)",
        boxShadow: "1px 1px 1px rgba(0, 0, 0, 0.1)",
    },

    sharingSessionDocumentsContainer: {
        display: "flex",
        flexDirection: "row",
        gap: "16px",
        height: "100%",
    },
    sharingSessionDocumentsDetails: {
        flex: 1,
    },
    sharingSessionDocumentSidebar: {
        // width: "300px",
        // borderLeft: "1px solid rgba(0, 0, 0, .2)",
    },

    sharingSessionDetailsContainer: {
        flex: 1,
        marginRight: "36px",
        paddingTop: "16px",
        paddingBottom: "16px",
    },
    sharingSessionActions: {
        display: "flex",
        gap: "8px",
    },
    documentsCardList: {
        display: "flex",
        gap: "16px",
        flexWrap: "wrap",
    },
    documentsCardListCard: {
        minWidth: "300px",
        maxWidth: "300px",
    },

    skeletonSessionDetails: {
        display: "flex",
        flexDirection: "column",
        gap: "8px",
    },

    skeletonDates: {
        display: "flex",
        flexDirection: "row",
        gap: "8px",
    },

    skeletonCreatedDate: {
        width: "100px",
    },

    skeletonEndDate: {
        width: "100px",
    },

    skeletonPipe: {
        width: "6px",
    },

    skeletonSessionName: {
        width: "300px",
    },

    skeletonSessionActionsMore: {
        width: "8px",
    },

    skeletonSessionDescription: {
        width: "400px",
    },

    skeletonSessionDocument: {
        display: "flex",
        flexDirection: "row",
        justifyContent: "space-between",
        gap: "8px",
    },

    skeletonSessionDocumentTitle: {
        width: "200px",
    },

    skeletonSessionDocumentUploadDate: {
        width: "100px",
        marginTop: "8px"
    },

    skeletonSessionDocumentMore: {
        width: "8px",
    },

    sessionHeadStatusINITIATED: {
        borderLeft: "5px solid",
        borderLeftColor: tokens.colorPaletteGreenForeground1
    },

    sessionHeadStatusACCEPTED_STARTED: {
        borderLeft: "5px solid",
        borderLeftColor: tokens.colorPaletteBlueForeground2
    },

    sessionHeadStatusENDED: {
        borderLeft: "5px solid",
        borderLeftColor: tokens.colorNeutralForeground4
    },

    sessionHeadStatusREJECTED: {
        borderLeft: "5px solid",
        borderLeftColor: tokens.colorPaletteRedForeground1
    },

    documentListTitle: {
        display: "flex",
        gap: "6px",
        alignItems: "center",
        margin: "16px 0",
    },

    sharingSessionDetailsNoneContainer: {
        display: "flex",
        justifyContent: "center",
        alignItems: "center",
        height: "100%",
        width: "100%",
    }
});