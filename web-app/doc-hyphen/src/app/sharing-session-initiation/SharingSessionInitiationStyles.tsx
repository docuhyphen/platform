import {makeStyles, tokens} from "@fluentui/react-components";

export const useSharingSessionInitiationStyles = makeStyles({
    sharingDetails: {
        display: "flex",
        flexDirection: "column",
        gap: "16px",
    },
    sharingDetailsInput: {
        flex: 1,
    },
    sharingSessionInitiationTaps: {
        minHeight: "400px",
    },
    sharingSessionDocumentsTabContent: {
        display: "flex",
        gap: "16px",
        flexDirection: "column",
    },
    sharingSessionDocumentsRestriction: {
        display: "flex",
        gap: "8px",
        flexDirection: "row",
        justifyContent: "space-between"
    },
    sharingSessionDocumentsDropdown: {
        marginRight: "36px",
    },
    shadingSessionDocumentCard: {
        flex: 1,
    },
    dialogTitle: {
        display: "flex",
        gap: "16px",
        flexDirection: "column",
    },
    dialogTitle1: {
        display: "flex",
        justifyContent: "space-between",
    },
    sessionDetailsTap: {
        display: "flex",
        gap: "16px",
        flexDirection: "column",
    },
    sharingOptionsTapContent: {
        display: "flex",
        gap: "16px",
        flexDirection: "column",
    },
    recipientsTabContent: {
        display: "flex",
        flexDirection: "column",
        gap: "16px",
    },
    sharingSessionInitiationSuccess: {
        minHeight: "200px",
        display: "flex",
        justifyContent: "center",
        alignItems: "center",
        flexDirection: "column",
        gap: "16px",
    },
    errorMessagesGroup: {
        display: "flex",
        flexDirection: "column",
        gap: "8px",
    },
    iconDeleteFilled: {
        color: tokens.colorPaletteRedForeground1,
    },
    addDocumentButtonContainer: {
        display: "flex",
        justifyContent: "center",
    },
    dialogContentContainer: {
        minHeight: "460px"
    }
});