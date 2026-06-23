import {makeStyles, shorthands} from '@fluentui/react-components';

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

