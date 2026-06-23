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
    accordionHeader: {
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        width: '100%',
        ...shorthands.gap('8px'),
        paddingRight: '8px',
    },
    panelContent: {
        display: 'flex',
        flexDirection: 'column',
        ...shorthands.gap('8px'),
        paddingBottom: '8px',
    },
    actions: {
        display: 'flex',
        ...shorthands.gap('8px'),
        justifyContent: 'flex-end',
    },
    emptyState: {
        display: 'flex',
        justifyContent: 'center',
        ...shorthands.padding('16px'),
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
