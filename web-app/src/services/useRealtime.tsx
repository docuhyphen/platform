import {useEffect, useState} from 'react';
import {realtimeService} from './NotificationService';

export function usePresence(): { onlineUserIds: Set<string>; isOnline: (userId: string) => boolean }
{
    const [online, setOnline] = useState<Set<string>>(new Set());

    useEffect(() =>
    {
        const off = realtimeService.on('PRESENCE_UPDATE', (msg) =>
        {
            if (!msg.userId) return;
            setOnline((prev) =>
            {
                const next = new Set(prev);
                if (msg.online) next.add(msg.userId!);
                else next.delete(msg.userId!);
                return next;
            });
        });
        return off;
    }, []);

    return {
        onlineUserIds: online,
        isOnline: (userId: string) => online.has(userId),
    };
}

export function useExchangeViewers(exchangeId: string | null | undefined): string[]
{
    const [viewerIds, setViewerIds] = useState<string[]>([]);

    useEffect(() =>
    {
        if (!exchangeId) return;

        const off = realtimeService.on('SHARING_VIEWERS', (msg) =>
        {
            if (msg.exchangeId === exchangeId && msg.viewerUserIds)
            {
                setViewerIds(msg.viewerUserIds);
            }
        });

        realtimeService.subscribeToExchange(exchangeId);

        return () =>
        {
            realtimeService.unsubscribeFromExchange(exchangeId);
            off();
        };
    }, [exchangeId]);

    return viewerIds;
}
