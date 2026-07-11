import {makeStyles, tokens} from "@fluentui/react-components";

export const useNotificationListItemStyles = makeStyles({
    container: {
        marginBottom: tokens.spacingVerticalS,
        padding: `${tokens.spacingVerticalS} ${tokens.spacingHorizontalM}`,
        cursor: 'pointer',
        '&:hover': {
            backgroundColor: tokens.colorNeutralBackground1Hover,
        },
        boxSizing: "border-box",
        maxWidth: "100%"
    },

    notificationHeader: {
        display: 'flex',
        justifyContent: 'space-between',
        marginBottom: tokens.spacingVerticalXS,
    },
    isUnread: {
        borderLeft: '3px solid',
        borderLeftColor: tokens.colorBrandForeground1,
    },
});
