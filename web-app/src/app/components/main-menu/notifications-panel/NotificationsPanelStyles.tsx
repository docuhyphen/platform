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
        top: tokens.spacingVerticalSNudge,
        right: tokens.spacingHorizontalSNudge,
        width: '8px',
        height: '8px',
        borderRadius: tokens.borderRadiusCircular,
        backgroundColor: tokens.colorPaletteRedBackground3,
        boxShadow: `0 0 0 ${tokens.strokeWidthThick} ${tokens.colorNeutralBackground1}`,
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

