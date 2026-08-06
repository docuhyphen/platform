import {useEffect, useState} from "react";
import {useAuth} from "../../../../context/AuthContext.tsx";
import {realtimeService} from "../../../../services/NotificationService.tsx";

export const useDocumentCommentUnreadCount = (
    exchangeId: string,
    documentId: string | undefined,
    isNotesPanelOpen: boolean,
) =>
{
    const {appUser} = useAuth();
    const currentUserId = appUser?.id;
    const [unreadCount, setUnreadCount] = useState(0);

    useEffect(() =>
    {
        if (isNotesPanelOpen) setUnreadCount(0);
    }, [isNotesPanelOpen]);

    useEffect(() => realtimeService.on("DOCUMENT_COMMENT_ADDED", message =>
    {
        if (message.userId && message.userId === currentUserId) return;
        if (message.exchangeId !== exchangeId || message.documentId !== documentId) return;
        if (!isNotesPanelOpen) setUnreadCount(currentCount => currentCount + 1);
    }), [currentUserId, documentId, exchangeId, isNotesPanelOpen]);

    return unreadCount;
};
