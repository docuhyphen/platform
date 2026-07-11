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
    accordionHeader: {
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        width: '100%',
        ...shorthands.gap(tokens.spacingHorizontalS),
        paddingRight: tokens.spacingHorizontalS,
    },
    panelContent: {
        display: 'flex',
        flexDirection: 'column',
        ...shorthands.gap(tokens.spacingHorizontalS),
        paddingBottom: tokens.spacingVerticalS,
    },
    actions: {
        display: 'flex',
        ...shorthands.gap(tokens.spacingHorizontalS),
        justifyContent: 'flex-end',
    },
    emptyState: {
        display: 'flex',
        justifyContent: 'center',
        ...shorthands.padding(tokens.spacingHorizontalL),
    },
    commentField: {
        width: '100%',
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
