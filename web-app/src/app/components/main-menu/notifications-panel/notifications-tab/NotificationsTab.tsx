import React, {useEffect, useRef} from 'react';
import {Button, Divider, List, ListItem, Spinner, Text} from '@fluentui/react-components';
import {useNavigate} from 'react-router-dom';
import {useNotifications} from '../../../../../context/NotificationContext';
import {NotificationDto} from '../../../../models/models';
import {getNotificationTarget} from '../../../../../services/notificationNavigation';
import NotificationListItem from '../../notification/notification-item/NotificationListItem';
import {useNotificationsTabStyles} from './NotificationsTabStyles';

const NotificationsTab: React.FC = () =>
{
    const styles = useNotificationsTabStyles();
    const navigate = useNavigate();
    const listRef = useRef<HTMLDivElement>(null);
    const loadMoreRef = useRef<HTMLDivElement>(null);
    const {
        notifications,
        hasMoreNotifications,
        isLoadingMoreNotifications,
        loadMoreNotifications,
        markAsRead,
        markAllAsRead,
    } = useNotifications();

    useEffect(() =>
    {
        const target = loadMoreRef.current;
        if (!target || !hasMoreNotifications) return;

        const observer = new IntersectionObserver(
            (entries) =>
            {
                if (entries[0]?.isIntersecting)
                {
                    void loadMoreNotifications();
                }
            },
            {root: listRef.current, rootMargin: '0px 0px 120px 0px'},
        );
        observer.observe(target);
        return () => observer.disconnect();
    }, [hasMoreNotifications, loadMoreNotifications]);

    const handleNotificationClick = (notification: NotificationDto) =>
    {
        markAsRead(notification.id);
        const target = getNotificationTarget(notification);
        if (target)
        {
            navigate(target);
        }
    };

    if (notifications.length === 0)
    {
        return (
            <div
                id="notifications-tab-empty-state"
                className={styles.emptyState}
            >
                <Text id="notifications-tab-empty-text">No notifications</Text>
            </div>
        );
    }

    return (
        <>
            <List
                id="notifications-tab-list"
                ref={listRef}
                className={styles.list}
            >
                {notifications.map((notification) => (
                    <ListItem
                        id={`notifications-tab-list-item-${notification.id}`}
                        key={notification.id}
                    >
                        <NotificationListItem
                            notification={notification}
                            onClick={() => handleNotificationClick(notification)}
                        />
                        <Divider id={`notifications-tab-divider-${notification.id}`}/>
                    </ListItem>
                ))}
                {hasMoreNotifications && (
                    <div
                        id="notifications-tab-load-more-sentinel"
                        ref={loadMoreRef}
                        className={styles.loadMore}
                    >
                        {isLoadingMoreNotifications ? (
                            <Spinner
                                id="notifications-tab-loading-more"
                                size="tiny"
                                label="Loading more notifications"
                            />
                        ) : (
                            <Button
                                id="notifications-tab-load-more-btn"
                                appearance="subtle"
                                shape="circular"
                                size="small"
                                onClick={() => void loadMoreNotifications()}
                            >
                                Load more
                            </Button>
                        )}
                    </div>
                )}
            </List>
            <div
                id="notifications-tab-actions"
                className={styles.actions}
            >
                <Button
                    id="notifications-panel-mark-all-read-btn"
                    size="small"
                    shape="circular"
                    appearance="primary"
                    onClick={markAllAsRead}
                >
                    Mark all as read
                </Button>
            </div>
        </>
    );
};

export default NotificationsTab;
