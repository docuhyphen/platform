import {useCallback, useEffect, useRef, useState} from 'react';
import {NotificationDto} from '../app/models/models';
import {useAuth} from './AuthContext.tsx';
import {notificationService} from '../services/NotificationService.tsx';
import {showBrowserNotification} from '../services/BrowserNotificationService.ts';
import {fetchNotifications, NotificationPageCursor} from '../services/notificationApi';

export interface NotificationInboxState
{
    notifications: NotificationDto[];
    unreadCount: number;
    hasMoreNotifications: boolean;
    isLoadingMoreNotifications: boolean;
    loadMoreNotifications: () => Promise<void>;
    markAsRead: (notificationId: string) => void;
    markAllAsRead: () => void;
}

interface NotificationInboxResult
{
    state: NotificationInboxState;
    probeToast: {id: number; message: string} | null;
}

const mergeNotifications = (
    current: NotificationDto[],
    incoming: NotificationDto[],
): NotificationDto[] =>
{
    const byId = new Map(current.map((notification) => [notification.id, notification]));
    incoming.forEach((notification) => byId.set(notification.id, notification));
    return Array.from(byId.values()).sort((left, right) =>
        new Date(right.timestamp).getTime() - new Date(left.timestamp).getTime(),
    );
};

export const useNotificationInbox = (): NotificationInboxResult =>
{
    const [notifications, setNotifications] = useState<NotificationDto[]>([]);
    const [unreadCount, setUnreadCount] = useState(0);
    const [nextCursor, setNextCursor] = useState<NotificationPageCursor>();
    const [hasMoreNotifications, setHasMoreNotifications] = useState(false);
    const [isLoadingMoreNotifications, setIsLoadingMoreNotifications] = useState(false);
    const [probeToast, setProbeToast] = useState<{id: number; message: string} | null>(null);
    const loadingMoreRef = useRef(false);
    const {appUser} = useAuth();

    const markAsRead = (notificationId: string) => setNotifications((current) =>
        current.map((notification) =>
        {
            if (notification.id !== notificationId || notification.isRead) return notification;
            setUnreadCount((count) => Math.max(0, count - 1));
            return {...notification, isRead: true};
        }));

    const markAllAsRead = () =>
    {
        setUnreadCount(0);
        setNotifications((current) => current.map((notification) => ({...notification, isRead: true})));
    };

    const loadMoreNotifications = useCallback(async () =>
    {
        if (!appUser?.id || !nextCursor || !hasMoreNotifications || loadingMoreRef.current) return;
        loadingMoreRef.current = true;
        setIsLoadingMoreNotifications(true);
        try
        {
            const page = await fetchNotifications(20, nextCursor);
            setNotifications((current) => mergeNotifications(current, page.notifications));
            setNextCursor(page.nextCursor);
            setHasMoreNotifications(page.hasMore);
        }
        catch (error: unknown)
        {
            console.warn('Failed to load more notifications', error);
        }
        finally
        {
            loadingMoreRef.current = false;
            setIsLoadingMoreNotifications(false);
        }
    }, [appUser?.id, hasMoreNotifications, nextCursor]);

    useEffect(() =>
    {
        if (!appUser?.id) return;
        let cancelled = false;
        setNotifications([]);
        setUnreadCount(0);
        setNextCursor(undefined);
        setHasMoreNotifications(false);
        loadingMoreRef.current = false;
        notificationService.connect(appUser.id);

        fetchNotifications().then((page) =>
        {
            if (cancelled) return;
            setUnreadCount(page.unreadCount);
            setNextCursor(page.nextCursor);
            setHasMoreNotifications(page.hasMore);
            setNotifications((current) => mergeNotifications(page.notifications, current));
        }).catch((error: unknown) => console.warn('Failed to load notifications', error));

        const removeHandler = notificationService.addMessageHandler((notification) =>
        {
            setNotifications((current) =>
            {
                if (current.some((item) => item.id === notification.id)) return current;
                if (!notification.isRead) setUnreadCount((count) => count + 1);
                return [notification, ...current];
            });
            showBrowserNotification(notification);
        });

        const removeProbeHandler = notificationService.on('REALTIME_PROBE', (message) =>
        {
            const timestamp = message.serverTime ?? Date.now();
            const probeMessage = message.message ?? 'Realtime probe received';
            setProbeToast({id: timestamp, message: probeMessage});
            setNotifications((current) =>
            {
                const id = `realtime-probe-${timestamp}`;
                if (current.some((notification) => notification.id === id)) return current;
                setUnreadCount((count) => count + 1);
                return [{
                    id,
                    type: 'REALTIME_PROBE',
                    message: probeMessage,
                    timestamp: new Date(timestamp).toISOString(),
                    isRead: false,
                    data: {source: 'realtime-probe'},
                }, ...current];
            });
        });

        const reconnectWithRefreshedToken = () => notificationService.connect(appUser.id);
        window.addEventListener('tokens-refreshed', reconnectWithRefreshedToken);
        return () =>
        {
            cancelled = true;
            removeHandler();
            removeProbeHandler();
            window.removeEventListener('tokens-refreshed', reconnectWithRefreshedToken);
            notificationService.disconnect();
        };
    }, [appUser?.id]);

    return {
        state: {
            notifications,
            unreadCount,
            hasMoreNotifications,
            isLoadingMoreNotifications,
            loadMoreNotifications,
            markAsRead,
            markAllAsRead,
        },
        probeToast,
    };
};
