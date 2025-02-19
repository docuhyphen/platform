import {makeStyles} from "@fluentui/react-components";

export const useDocumentAddDialogStyles = makeStyles({
    documentAddDialogContainer: {
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        justifyContent: 'center',
        height: '180px',
        gap: "16px"
    },

    documentRestriction: {
        display: 'flex',
        flexDirection: 'row',
        alignItems: 'center',
        gap: '8px'
    },
    documentTitleField: {
        width: '100%',
        maxWidth: '400px',
    },

});