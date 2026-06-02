import {makeStyles, shorthands, tokens} from '@fluentui/react-components';

export const usePendingApprovalsStyles = makeStyles({
    container: {
        display: 'flex',
        flexDirection: 'column',
        ...shorthands.gap('8px'),
        maxHeight: '400px',
        overflowY: 'auto',
        minWidth: '340px',
    },
    card: {
        display: 'flex',
        flexDirection: 'column',
        ...shorthands.gap('6px'),
        ...shorthands.padding('12px'),
        ...shorthands.borderRadius('8px'),
        backgroundColor: tokens.colorNeutralBackground1,
        boxShadow: tokens.shadow2,
    },
    cardHeader: {
        display: 'flex',
        justifyContent: 'space-between',
        alignItems: 'center',
    },
    actions: {
        display: 'flex',
        ...shorthands.gap('8px'),
        justifyContent: 'flex-end',
        marginTop: '4px',
    },
    emptyState: {
        display: 'flex',
        justifyContent: 'center',
        ...shorthands.padding('16px'),
    },
    commentField: {
        marginTop: '4px',
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
