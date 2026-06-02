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

function getNotificationTitle(type: NotificationType | string): string
{
    switch (type)
    {
        case NotificationType.NEW_COMMENT:
            return 'New Comment';
        case NotificationType.NEW_SESSION:
            return 'New Session';
        case NotificationType.DOCUMENT_ADDED:
            return 'Document Added';
        case NotificationType.DOCUMENT_UPDATED:
            return 'Document Updated';
        case NotificationType.SESSION_ENDED:
            return 'Session Ended';
        case NotificationType.SESSION_INITIATED:
            return 'Session Initiated';
        case 'workflow.step_assigned':
        case 'WORKFLOW_STEP_ASSIGNED':
            return 'Approval Required';
        case 'workflow.escalated':
        case 'WORKFLOW_ESCALATED':
            return 'Approval Escalated';
        case 'session.activated':
        case 'SESSION_ACTIVATED':
            return 'Session Approved';
        case 'session.rejected':
        case 'SESSION_REJECTED':
            return 'Session Rejected';
        default:
            return 'Notification';
    }
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
                    {getNotificationTitle(notification.type)}
                </Text>
                <Text size={200}>{formatDateTimeWithOrdinal(notification.timestamp)}</Text>
            </div>
            <Text>{notification.message}</Text>
        </div>
    );
};

export default NotificationListItem;