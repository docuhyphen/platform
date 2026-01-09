import React from "react";
import {NotificationDto, NotificationType} from "../../../../models/models.tsx";
import {Text} from "@fluentui/react-components";
import {formatDateTimeWithOrdinal} from "../../../../helpers.ts";
import {useNotificationListItemStyles} from "./NotificationListItemStyles.tsx";

interface NotificationListItemProps
{
    notification: NotificationDto;
    onClick: () => void;
}

const NotificationListItem: React.FC<NotificationListItemProps> = (
    {
        notification,
        onClick
    }) =>
{
    const styles = useNotificationListItemStyles();

    return (
        <div className={`${styles.container} ${!notification.isRead ? styles.isUnread : ''}`}
             onClick={onClick}>
            <div className={styles.notificationHeader}>
                <Text weight="semibold">
                    {notification.type === NotificationType.NEW_COMMENT ? 'New Comment' :
                        notification.type === NotificationType.NEW_SESSION ? 'New Session' :
                            notification.type === NotificationType.DOCUMENT_ADDED ? 'Document Added' :
                                notification.type === NotificationType.DOCUMENT_UPDATED ? 'Document Updated' :
                                    notification.type === NotificationType.SESSION_ENDED ? 'Session Ended' : 'Notification'}
                </Text>
                <Text size={200}>{formatDateTimeWithOrdinal(notification.timestamp)}</Text>
            </div>
            <Text>{notification.message}</Text>
        </div>
    );
};

export default NotificationListItem;