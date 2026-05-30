import {makeStyles, tokens} from "@fluentui/react-components";

export const useNotificationListItemStyles = makeStyles({
    container: {
        marginBottom: '8px',
        padding: '8px 12px',
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
        marginBottom: '4px',
    },
    isUnread: {
        borderLeft: '3px solid',
        borderLeftColor: tokens.colorBrandForeground1,
    },
});
