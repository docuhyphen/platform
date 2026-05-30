import {makeStyles} from "@fluentui/react-components";

export const useSessionDocumentAddDialogStyles = makeStyles({
    dialogContentContainer: {
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'stretch',
        justifyContent: 'flex-start',
        padding: "8px 0",
        gap: "16px",
        width: "100%",
        minWidth: 0,
        boxSizing: "border-box",
    },

    documentRestriction: {
        display: 'flex',
        flexDirection: 'row',
        alignItems: 'center',
        gap: '12px',
        width: '100%',
        minWidth: 0,
        // Phones: stack the Restrict-type switch and the type Dropdown
        // vertically so each control gets the full row width and the
        // dropdown's placeholder text never gets clipped. This matches
        // the responsive behaviour of the session-initiation document
        // card (SharingSessionInitiationStyles.sharingSessionDocumentsRestriction).
        "@media (max-width: 768px)": {
            flexDirection: 'column',
            alignItems: 'stretch',
            gap: '8px',
        },
        // The Dropdown is the second child; let it take any remaining
        // horizontal space on desktop, or the full row width on mobile
        // (the stacked container does this automatically via stretch).
        "& > :last-child": {
            flex: 1,
            minWidth: 0,
            "@media (max-width: 768px)": {
                width: "100%",
            },
        },
    },
    documentTitleField: {
        width: '100%',
    },
});