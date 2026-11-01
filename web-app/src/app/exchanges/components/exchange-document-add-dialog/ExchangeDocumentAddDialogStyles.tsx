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

    documentTitleField: {
        width: '100%',
    },
});