import {makeStyles, tokens,} from "@fluentui/react-components";

export const useSharingSessionDetailsStyles = makeStyles({

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
});