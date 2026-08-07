import apiClient, {addBearerToHeaderToken} from './apiClient';

/**
 * Uploads (or replaces) the current user's profile picture. The file extension is sent
 * alongside the binary so the backend can validate the image type and pick a storage key.
 */
export const uploadAppUserAvatar = async (file: File, token: string | null): Promise<void> =>
{
    try
    {
        const extension = file.name.split('.').pop()?.toLowerCase() ?? '';
        const formData = new FormData();
        formData.append('file', file);
        formData.append('extension', extension);

        await apiClient.post(`/app-user/avatar`, formData, {
            headers: {
                Authorization: addBearerToHeaderToken(token)
            }
        });
    }
    catch (error: unknown)
    {
        throw (error as {response?: {data?: unknown}, message?: string})?.response?.data
            ?? (error as {message?: string})?.message;
    }
};

/**
 * Removes the current user's profile picture.
 */
export const deleteAppUserAvatar = async (token: string | null): Promise<void> =>
{
    try
    {
        await apiClient.delete(`/app-user/avatar`, {
            headers: {
                Authorization: addBearerToHeaderToken(token)
            }
        });
    }
    catch (error: unknown)
    {
        throw (error as {response?: {data?: unknown}, message?: string})?.response?.data
            ?? (error as {message?: string})?.message;
    }
};

/**
 * Fetches the current user's profile picture as an authenticated blob and returns a
 * browser object URL that can be used as an image source. Returns null when the user
 * has no stored profile picture.
 */
export const fetchAppUserAvatarObjectUrl = async (token: string | null): Promise<string | null> =>
{
    try
    {
        const response = await apiClient.get(`/app-user/avatar`, {
            responseType: 'blob',
            headers: {
                Authorization: addBearerToHeaderToken(token)
            }
        });
        return URL.createObjectURL(response.data as Blob);
    }
    catch (error: unknown)
    {
        const status = (error as {response?: {status?: number}})?.response?.status;
        if (status === 404)
        {
            return null;
        }
        throw error;
    }
};

