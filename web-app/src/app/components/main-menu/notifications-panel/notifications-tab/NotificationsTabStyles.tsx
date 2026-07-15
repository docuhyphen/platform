import {makeStyles, shorthands, tokens} from '@fluentui/react-components';

export const useNotificationsTabStyles = makeStyles({
    list: {
        maxHeight: '400px',
        overflowY: 'auto',
        width: '350px',
        display: 'flex',
        flexDirection: 'column',
        ...shorthands.gap(tokens.spacingHorizontalXS),
    },
    loadMore: {
        display: 'flex',
        justifyContent: 'center',
        paddingTop: tokens.spacingVerticalS,
        paddingBottom: tokens.spacingVerticalS,
    },
    actions: {
        paddingTop: tokens.spacingVerticalL,
        display: 'flex',
        justifyContent: 'flex-end',
    },
    emptyState: {
        display: 'flex',
        justifyContent: 'center',
        ...shorthands.padding(tokens.spacingHorizontalL),
    },
});
