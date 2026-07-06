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
    alertDot: {
        position: 'absolute',
        top: '6px',
        right: '6px',
        width: '8px',
        height: '8px',
        borderRadius: '999px',
        backgroundColor: tokens.colorPaletteRedBackground3,
        boxShadow: `0 0 0 2px ${tokens.colorNeutralBackground1}`,
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
    alertTab: {
        color: tokens.colorPaletteRedForeground1,
        ':hover': {
            color: tokens.colorPaletteRedForeground1,
        },
    },
});

