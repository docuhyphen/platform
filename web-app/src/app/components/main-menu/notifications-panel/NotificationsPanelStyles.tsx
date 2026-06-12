import {makeStyles, shorthands, tokens} from '@fluentui/react-components';

export const useNotificationsPanelStyles = makeStyles({
    triggerContainer: {
        position: 'relative',
        display: 'inline-flex',
    },
    badge: {
        position: 'absolute',
        top: '-5px',
        right: '-5px',
    },
    popoverContent: {
        display: 'flex',
        flexDirection: 'column',
        minWidth: '360px',
    },
    tabContent: {
        paddingTop: '8px',
    },

    // Notification list styles
    notificationList: {
        maxHeight: '400px',
        overflowY: 'auto',
        width: '350px',
        display: 'flex',
        flexDirection: 'column',
        gap: '4px',
    },
    markAllAsRead: {
        paddingTop: '16px',
        display: 'flex',
        justifyContent: 'end',
    },

    // Pending approvals styles
    approvalsList: {
        display: 'flex',
        flexDirection: 'column',
        ...shorthands.gap('8px'),
        maxHeight: '400px',
        overflowY: 'auto',
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

    tabCountBadge: {
        marginLeft: '4px',
    },
});

