import {makeStyles} from "@fluentui/react-components";

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
        gap: "4px"
    },
    markAllAsRead: {
        paddingTop: "16px",
        display: "flex",
        justifyContent: "end"
    },
    badge: {
        position: 'absolute',
        top: '-5px',
        right: '-5px',
    },
});
