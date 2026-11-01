import {makeStyles, tokens} from '@fluentui/react-components';

export const useDocumentLibraryEntryActionsStyles = makeStyles({
    cardActions: {
        display: 'flex',
        gap: tokens.spacingHorizontalXS,
        alignItems: 'center',
        flexShrink: '0',
    },
});
