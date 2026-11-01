import {makeStyles, tokens} from '@fluentui/react-components';

export const useDocumentLibraryEntryBadgesStyles = makeStyles({
    badgeRow: {
        display: 'flex',
        gap: tokens.spacingHorizontalSNudge,
        flexWrap: 'wrap',
        alignItems: 'center',
    },
});
