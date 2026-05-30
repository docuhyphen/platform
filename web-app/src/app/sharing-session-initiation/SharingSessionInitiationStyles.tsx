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
        justifyContent: "space-between",
        alignItems: "center"
    },
    sharingSessionDocumentsRestrictionField: {
        flex: 1,
        display: "flex"
    },
    sharingSessionDocumentsDropdown: {
        marginRight: "36px",
        minWidth: "100px"
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
    sharingSessionInitiationSuccess: {
        minHeight: "200px",
        display: "flex",
        justifyContent: "center",
        alignItems: "center",
        flexDirection: "column",
        gap: "16px",
    },
    sharingSessionSuccessDetails: {
        display: "grid",
        gridTemplateColumns: "auto 1fr",
        columnGap: "12px",
        rowGap: "8px",
        width: "100%",
        maxWidth: "440px",
        padding: "12px",
        borderRadius: tokens.borderRadiusMedium,
        backgroundColor: tokens.colorNeutralBackground2,
    },
    sharingSessionSuccessActions: {
        display: "flex",
        gap: "8px",
        alignItems: "center",
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