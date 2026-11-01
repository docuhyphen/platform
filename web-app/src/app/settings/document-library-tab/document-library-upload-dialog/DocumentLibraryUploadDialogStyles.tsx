import {makeStyles, tokens} from '@fluentui/react-components';
import {documentLibraryDialogBodyStyles} from '../document-library-dialog-actions/DocumentLibraryDialogActionsStyles.tsx';

export const useDocumentLibraryUploadDialogStyles = makeStyles({
    ...documentLibraryDialogBodyStyles,
    selectedFileText: {
        color: tokens.colorNeutralForeground2,
    },
    hiddenFileInput: {
        display: 'none',
    },
});
