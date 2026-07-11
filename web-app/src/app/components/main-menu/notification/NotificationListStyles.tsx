import {tokens, makeStyles} from "@fluentui/react-components";

export const useNotificationListStyles = makeStyles({

    notificationButtonContainer: {
        position: "relative"
    },
    list: {
        maxHeight: '400px',
        overflowY: 'auto',
        width: '350px',
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingHorizontalXS
    },
    markAllAsRead: {
        paddingTop: tokens.spacingVerticalL,
        display: "flex",
        justifyContent: "end"
    },
    badge: {
        position: 'absolute',
        top: '-5px',
        right: '-5px',
    },
});
