import apiClient from './apiClient';

export interface UserContactDto
{
    contactAppUserId: string | null;
    email: string;
    firstName: string | null;
    lastName: string | null;
    lastSharedAt: string;
    shareCount: number;
    avatarUrl?: string | null;
}

const executeRequest = async <T>(fn: () => Promise<{ data: T }>): Promise<T> =>
{
    try
    {
        const {data} = await fn();
        return data;
    }
    catch (error: unknown)
    {
        throw error.response?.data || error.message;
    }
};

export const searchContacts = (query: string, limit: number = 10): Promise<UserContactDto[]> =>
    executeRequest(() => apiClient.get(`/me/contacts`, {params: {query, limit}}));

export const fetchRecentContacts = (limit: number = 6): Promise<UserContactDto[]> =>
    executeRequest(() => apiClient.get(`/me/contacts/recent`, {params: {limit}}));
