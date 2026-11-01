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
import {getNotificationTarget} from '../../../../services/notificationNavigation';

const NotificationList: React.FC = () =>
{
    const {notifications, unreadCount, markAsRead, markAllAsRead} = useNotifications();
    const styles = useNotificationListStyles();
    const navigate = useNavigate();

    const handleNotificationClick = (notification: NotificationDto) =>
    {
        markAsRead(notification.id);
        const target = getNotificationTarget(notification);
        if (target)
        {
            navigate(target);
        }
    };

    return (
        <Popover withArrow>
            <PopoverTrigger disableButtonEnhancement>
                <div id={"notification-list-div-1"}
                     className={styles.notificationButtonContainer}>
                    <Button
                        id={"notification-list-bell-btn"}
                        icon={<NotificationsIcon/>}
                        appearance="subtle"
                        shape={"circular"}>
                    </Button>
                    {unreadCount > 0 && (
                        <CounterBadge
                            id={"notification-list-counter-badge-1"}
                            className={styles.badge}
                            appearance="filled"
                            color="danger"
                            count={unreadCount}/>
                    )}
                </div>
            </PopoverTrigger>
            <PopoverSurface id={"notification-list-popover-surface-1"}>
                <List id={"notification-list-list-1"}
                      className={styles.list}>
                    {notifications.length > 0 ? (<>
                            {notifications.map((notification: NotificationDto) => (
                                <ListItem id={"notification-list-list-item-1"}>
                                    <NotificationListItem
                                        key={notification.id}
                                        notification={notification}
                                        onClick={() => handleNotificationClick(notification)}
                                    />
                                    <Divider id={"notification-list-divider-1"}/>
                                </ListItem>
                            ))}
                        </>
                    ) : (
                        <Text id={"notification-list-text-1"}
                              align={"center"}>
                            No notifications
                        </Text>
                    )}
                </List>
                {notifications.length > 0 &&
                    <div id={"notification-list-div-2"}
                         className={styles.markAllAsRead}>
                        <Button
                            id={"notification-list-mark-all-read-btn"}
                            size="small"
                            shape={"circular"}
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
