import {makeStyles, tokens} from '@fluentui/react-components';

export const useDocumentLibraryActiveTagsStyles = makeStyles({
    activeTagsRow: {
        display: 'flex',
        flexWrap: 'wrap',
        alignItems: 'center',
        gap: tokens.spacingHorizontalXS,
    },
});
