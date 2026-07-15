import React from "react";
import {NotificationDto} from "../../../../models/models.tsx";
import {Text} from "@fluentui/react-components";
import {formatDateTimeWithOrdinal} from "../../../../helpers.ts";
import {useNotificationListItemStyles} from "./NotificationListItemStyles.tsx";
import {getNotificationMessage, getNotificationTitle} from "./notificationPresentation.ts";

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
    const handleKeyDown = (event: React.KeyboardEvent<HTMLDivElement>) =>
    {
        if (event.key === 'Enter' || event.key === ' ')
        {
            event.preventDefault();
            onClick();
        }
    };

    return (
        <div
            id={`notification-item-${notification.id}`}
            className={`${styles.container} ${!notification.isRead ? styles.isUnread : ''}`}
            role="button"
            tabIndex={0}
            onClick={onClick}
            onKeyDown={handleKeyDown}
        >
            <div
                id={`notification-item-header-${notification.id}`}
                className={styles.notificationHeader}
            >
                <Text
                    id={`notification-item-title-${notification.id}`}
                    weight="semibold"
                >
                    {getNotificationTitle(notification.type)}
                </Text>
                <Text
                    id={`notification-item-time-${notification.id}`}
                    size={200}
                >
                    {formatDateTimeWithOrdinal(notification.timestamp)}
                </Text>
            </div>
            <Text id={`notification-item-message-${notification.id}`}>
                {getNotificationMessage(notification)}
            </Text>
        </div>
    );
};

export default NotificationListItem;
