import {makeStyles, tokens} from '@fluentui/react-components';

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

