import {makeStyles, tokens} from '@fluentui/react-components';

export const useDocumentLibraryScopeTabsStyles = makeStyles({
    header: {
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        flexWrap: 'wrap',
        gap: tokens.spacingHorizontalS,
    },
});
