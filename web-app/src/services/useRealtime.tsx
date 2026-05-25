import {useEffect, useState} from 'react';
import {realtimeService} from './NotificationService';

/**
 * Subscribe to PRESENCE_UPDATE events and expose the live set of online user IDs.
 *
 * The server fans presence transitions out to every connected user; on initial mount we
 * don't have a snapshot, so the set grows as the first few PRESENCE_UPDATE messages
 * arrive (or as users this client cares about come online).
 */
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

/**
 * Subscribe to viewers of a sharing session. While the hook is mounted, this client is
 * marked as viewing the session on the server side; on unmount it unsubscribes.
 */
export function useSharingSessionViewers(sharingSessionId: string | null | undefined): string[]
{
    const [viewerIds, setViewerIds] = useState<string[]>([]);

    useEffect(() =>
    {
        if (!sharingSessionId) return;

        const off = realtimeService.on('SHARING_VIEWERS', (msg) =>
        {
            if (msg.sharingSessionId === sharingSessionId && msg.viewerUserIds)
            {
                setViewerIds(msg.viewerUserIds);
            }
        });

        realtimeService.subscribeToSharingSession(sharingSessionId);

        return () =>
        {
            realtimeService.unsubscribeFromSharingSession(sharingSessionId);
            off();
        };
    }, [sharingSessionId]);

    return viewerIds;
}
