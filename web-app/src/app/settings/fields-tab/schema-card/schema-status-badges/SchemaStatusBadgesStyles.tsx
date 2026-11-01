import {makeStyles, tokens} from '@fluentui/react-components';

export const useSchemaStatusBadgesStyles = makeStyles({
    badgeRow: {
        display: 'flex',
        gap: tokens.spacingHorizontalSNudge,
        alignItems: 'center',
        flexWrap: 'wrap',
    },
});
