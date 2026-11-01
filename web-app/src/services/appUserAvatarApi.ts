import apiClient, {addBearerToHeaderToken} from './apiClient';

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

