import {makeStyles, shorthands, tokens} from '@fluentui/react-components';

export const usePendingApprovalsTabStyles = makeStyles({
    list: {
        display: 'flex',
        flexDirection: 'column',
        ...shorthands.gap(tokens.spacingHorizontalS),
        maxHeight: '400px',
        overflowY: 'auto',
    },
    header: {
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        width: '100%',
        ...shorthands.gap(tokens.spacingHorizontalS),
        paddingRight: tokens.spacingHorizontalS,
    },
    panel: {
        display: 'flex',
        flexDirection: 'column',
        ...shorthands.gap(tokens.spacingHorizontalS),
        paddingBottom: tokens.spacingVerticalS,
    },
    actions: {
        display: 'flex',
        ...shorthands.gap(tokens.spacingHorizontalS),
        justifyContent: 'flex-end',
        marginTop: tokens.spacingVerticalXS,
    },
    emptyState: {
        display: 'flex',
        justifyContent: 'center',
        ...shorthands.padding(tokens.spacingHorizontalL),
    },
    comment: {
        marginTop: tokens.spacingVerticalXS,
    },
});
