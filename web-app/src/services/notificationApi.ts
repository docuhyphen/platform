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
