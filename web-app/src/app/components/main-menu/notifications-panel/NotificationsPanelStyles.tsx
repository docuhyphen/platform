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
        paddingTop: tokens.spacingVerticalS,
    },

    // Notification list styles
    notificationList: {
        maxHeight: '400px',
        overflowY: 'auto',
        width: '350px',
        display: 'flex',
        flexDirection: 'column',
        gap: tokens.spacingHorizontalXS,
    },
    markAllAsRead: {
        paddingTop: tokens.spacingVerticalL,
        display: 'flex',
        justifyContent: 'end',
    },

    // Pending approvals styles
    approvalsList: {
        display: 'flex',
        flexDirection: 'column',
        ...shorthands.gap(tokens.spacingHorizontalS),
        maxHeight: '400px',
        overflowY: 'auto',
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
        marginTop: tokens.spacingVerticalXS,
    },
    emptyState: {
        display: 'flex',
        justifyContent: 'center',
        ...shorthands.padding(tokens.spacingHorizontalL),
    },
    commentField: {
        marginTop: tokens.spacingVerticalXS,
    },

    tabCountBadge: {
        marginLeft: tokens.spacingHorizontalXS,
    },
    alertTab: {
        color: tokens.colorPaletteRedForeground1,
        ':hover': {
            color: tokens.colorPaletteRedForeground1,
        },
    },
});

