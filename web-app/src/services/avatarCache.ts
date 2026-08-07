import apiClient from './apiClient';

/**
 * Resolves relative avatar marker paths (e.g. "/app-user/{id}/avatar") returned on DTOs into
 * browser object URLs. The avatar endpoints require the bearer token, so the image cannot be
 * loaded by a plain <img src> and must be fetched through the authenticated API client.
 *
 * Results are cached by marker path so a given user's picture is fetched once and shared across
 * every Avatar/Persona that references it (lists, tables, group stacks). Failed lookups cache a
 * null so a missing picture is not refetched on every render.
 */
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

