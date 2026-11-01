import apiClient from './apiClient';

const cache = new Map<string, Promise<string | null>>();

export const resolveAvatarObjectUrl = (markerPath: string): Promise<string | null> =>
{
    const existing = cache.get(markerPath);
    if (existing)
    {
        return existing;
    }

    const request = apiClient
        .get(markerPath, {responseType: 'blob'})
        .then(response => URL.createObjectURL(response.data as Blob))
        .catch((): string | null => null);

    cache.set(markerPath, request);
    return request;
};

