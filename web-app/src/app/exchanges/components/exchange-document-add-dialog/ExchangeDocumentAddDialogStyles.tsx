import {tokens, makeStyles} from "@fluentui/react-components";

export const useExchangeDocumentAddDialogStyles = makeStyles({
    dialogContentContainer: {
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'stretch',
        justifyContent: 'flex-start',
        padding: `${tokens.spacingVerticalS} 0`,
        gap: tokens.spacingHorizontalL,
        width: "100%",
        minWidth: 0,
        boxSizing: "border-box",
    },

    documentRestriction: {
        display: 'flex',
        flexDirection: 'row',
        alignItems: 'center',
        gap: tokens.spacingHorizontalM,
        width: '100%',
        minWidth: 0,
        // Phones: stack the Restrict-type switch and the type Dropdown
        // vertically so each control gets the full row width and the
        // dropdown's placeholder text never gets clipped. This matches
        // the responsive behaviour of the exchange-initiation document
        // card (ExchangeInitiationStyles.exchangeDocumentsRestriction).
        "@media (max-width: 768px)": {
            flexDirection: 'column',
            alignItems: 'stretch',
            gap: tokens.spacingHorizontalS,
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