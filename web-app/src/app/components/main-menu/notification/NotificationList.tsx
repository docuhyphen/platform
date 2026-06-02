import React from 'react';
import {useNavigate} from 'react-router-dom';
import {
    Button,
    CounterBadge,
    Divider,
    List,
    ListItem,
    Popover,
    PopoverSurface,
    PopoverTrigger,
    Text
} from '@fluentui/react-components';
import {useNotifications} from '../../../../context/NotificationContext';
import {NotificationDto} from '../../../models/models';
import {NotificationsIcon} from '../../IconBundles';
import {useNotificationListStyles} from "./NotificationListStyles.tsx";
import NotificationListItem from "./notification-item/NotificationListItem.tsx";

const WORKFLOW_NOTIFICATION_TYPES = [
    'workflow.step_assigned', 'WORKFLOW_STEP_ASSIGNED',
    'workflow.escalated', 'WORKFLOW_ESCALATED',
];

const NotificationList: React.FC = () =>
{
    const {notifications, unreadCount, markAsRead, markAllAsRead} = useNotifications();
    const styles = useNotificationListStyles();
    const navigate = useNavigate();

    const handleNotificationClick = (notification: NotificationDto) =>
    {
        markAsRead(notification.id);

        // Workflow step assigned → navigate to session if available
        if (WORKFLOW_NOTIFICATION_TYPES.includes(notification.type as string))
        {
            const sid = notification.sessionId || notification.data?.sessionId;
            if (sid)
            {
                navigate(`/sharing-sessions?s=${sid}`);
            }
            return;
        }

        if (notification.sessionId)
        {
            if (notification.documentId)
            {
                navigate(`/sharing-sessions?s=${notification.sessionId}&d=${notification.documentId}`);
            }
            else
            {
                navigate(`/sharing-sessions?s=${notification.sessionId}`);
            }
        }
    };

    return (
        <Popover withArrow>
            <PopoverTrigger disableButtonEnhancement>
                <div className={styles.notificationButtonContainer}>
                    <Button
                        icon={<NotificationsIcon/>}
                        appearance="subtle"
                        shape={"circular"}>
                    </Button>
                    {unreadCount > 0 && (
                        <CounterBadge
                            className={styles.badge}
                            appearance="filled"
                            color="danger"
                            count={unreadCount}/>
                    )}
                </div>
            </PopoverTrigger>
            <PopoverSurface>
                <List className={styles.list}>
                    {notifications.length > 0 ? (<>
                            {notifications.map((notification: NotificationDto) => (
                                <ListItem>
                                    <NotificationListItem
                                        key={notification.id}
                                        notification={notification}
                                        onClick={() => handleNotificationClick(notification)}
                                    />
                                    <Divider/>
                                </ListItem>
                            ))}
                        </>
                    ) : (
                        <Text align={"center"}>
                            No notifications
                        </Text>
                    )}
                </List>
                {notifications.length > 0 &&
                    <div className={styles.markAllAsRead}>
                        <Button
                            size="small"
                            appearance="primary"
                            onClick={() => markAllAsRead()}>
                            Mark all as read
                        </Button>
                    </div>
                }
            </PopoverSurface>
        </Popover>
    );
};

export default NotificationList;