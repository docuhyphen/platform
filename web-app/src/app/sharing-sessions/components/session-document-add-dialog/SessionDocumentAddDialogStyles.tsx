import {makeStyles} from "@fluentui/react-components";

export const useSessionDocumentAddDialogStyles = makeStyles({
    dialogContentContainer: {
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        justifyContent: 'center',
        padding: "8px 0",
        gap: "16px"
    },

    documentRestriction: {
        display: 'flex',
        flexDirection: 'row',
        alignItems: 'center',
        gap: '8px',
    },
    documentTitleField: {
        width: '100%',
    },
});