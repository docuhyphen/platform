import {makeStyles, shorthands, tokens} from "@fluentui/react-components";

export const useLandingStyles = makeStyles({

    sharingSessionsContainer: {
        display: "flex",
        gap: "16px",
        height: "100%",
        width: "100%",
        padding: "76px 16px 16px 16px",
        boxSizing: "border-box"
    },

    // sharingSessionsContainerDiv: {
    //     padding: "16px 0",
    // },

    sharingSessionHeadContainer: {
        display: "flex",
        justifyContent: "space-between",
        alignItems: "center",
        gap: "8px",
        background: "white",
        padding: "8px 16px",
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

    sharingSessionDocumentSidebar: {
        // width: "300px",
        // borderLeft: "1px solid rgba(0, 0, 0, .2)",
    },

    sharingSessionDetailsContainer: {
        flex: 1,
        display: "flex",
        flexDirection: "column",
        gap: "16px",
        width: "480px"
    },

    sharingSessionActions: {
        display: "flex",
        gap: "8px",
    },

    documentsCardList: {
        width: "100%",
        overflow: "hidden",
    },

    documentsCardList2: {
        display: "flex",
        overflowX: "hidden",
        whiteSpace: "nowrap",
        ...shorthands.padding("2px"),
        gap: "16px",
        scrollbarWidth: "none", // Firefox

        "&::-webkit-scrollbar": {
            display: "none", // Chrome, Safari, Edge
        },

        // Show scrollbar on hover
        "&:hover": {
            overflowX: "auto",
            scrollbarWidth: "thin", // Firefox
        },

        "&:hover::-webkit-scrollbar": {
            display: "block",
            height: "8px",
        },

        "&:hover::-webkit-scrollbar-thumb": {
            backgroundColor: tokens.colorNeutralStroke1Hover,
            borderRadius: "4px",
        },

        "&:hover::-webkit-scrollbar-track": {
            backgroundColor: tokens.colorNeutralBackground1,
        },
    },

    documentsCardListCard: {
        minWidth: "300px",
        maxWidth: "300px",
        flex : "0 0 auto"
    },

    documentsCardListCardSelected: {
        borderLeft: "5px solid",
        borderLeftColor: tokens.colorBrandForeground1
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
    },

    documentSearchField: {
        flex: "1"
    },

    sharingSessionDetailsNoneContainer: {
        display: "flex",
        justifyContent: "center",
        alignItems: "center",
        height: "100%",
        width: "100%",
    },
});