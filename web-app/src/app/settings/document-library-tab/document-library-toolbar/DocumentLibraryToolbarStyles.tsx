import {makeStyles, tokens} from '@fluentui/react-components';

export const useDocumentLibraryToolbarStyles = makeStyles({
    searchRow: {
        display: 'flex',
        flexDirection: 'row',
        justifyContent: 'space-between',
        alignItems: 'center',
        gap: tokens.spacingHorizontalS,
        width: '100%',
        '@media screen and (max-width: 600px)': {
            alignItems: 'stretch',
            flexDirection: 'column',
        },
    },
    searchRowInputs: {
        display: 'flex',
        alignItems: 'center',
        gap: tokens.spacingVerticalS,
        flex: 1,
        minWidth: 0,
    },
    searchField: {
        flex: 1,
        minWidth: 0,
    },
});
