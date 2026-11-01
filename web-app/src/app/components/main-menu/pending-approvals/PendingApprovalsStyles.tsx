import {makeStyles, shorthands, tokens} from '@fluentui/react-components';

export const usePendingApprovalsStyles = makeStyles({
    container: {
        display: 'flex',
        flexDirection: 'column',
        ...shorthands.gap(tokens.spacingHorizontalS),
        maxHeight: '400px',
        overflowY: 'auto',
        minWidth: '340px',
    },
    emptyState: {
        display: 'flex',
        justifyContent: 'center',
        ...shorthands.padding(tokens.spacingHorizontalL),
    },
    badge: {
        position: 'absolute',
        top: '-4px',
        right: '-4px',
    },
    triggerContainer: {
        position: 'relative',
        display: 'inline-flex',
    },
});
