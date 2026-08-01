import {NotificationDto} from '../app/models/models';
import apiClient from './apiClient';

export interface NotificationPageCursor
{
    timestamp: number;
    id: string;
}

export interface NotificationPage
{
    notifications: NotificationDto[];
    nextCursor?: NotificationPageCursor;
    hasMore: boolean;
    unreadCount: number;
}

export interface NotificationReadReceiptRequest
{
    all?: boolean;
    notificationIds?: string[];
    eventTypes?: string[];
    data?: Record<string, string>;
}

export interface NotificationReadReceiptResponse
{
    readNotificationIds: string[];
    unreadCount: number;
}

export const fetchNotifications = async (
    limit: number = 20,
    cursor?: NotificationPageCursor,
): Promise<NotificationPage> =>
{
    const response = await apiClient.get<NotificationPage>('/notifications', {
        params: {
            limit,
            beforeTimestamp: cursor?.timestamp,
            beforeId: cursor?.id,
        },
    });
    return response.data;
};

export const createNotificationReadReceipts = async (
    request: NotificationReadReceiptRequest,
): Promise<NotificationReadReceiptResponse> =>
{
    const response = await apiClient.post<NotificationReadReceiptResponse>(
        '/notifications/read-receipts',
        request,
    );
    return response.data;
};
