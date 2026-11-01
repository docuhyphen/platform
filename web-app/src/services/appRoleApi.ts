import apiClient from './apiClient';
import {AppAdminDto, AppUserSearchResult, GrantAppAdminRequest} from './types/dtos';

const executeRequest = async <T>(fn: () => Promise<{ data: T }>): Promise<T> =>
{
    try
    {
        const {data} = await fn();
        return data;
    }
    catch (error: unknown)
    {
        const axiosLikeError = error as {
            message?: string;
            response?: {
                status?: number;
                data?: { errorMessage?: string; message?: string; [key: string]: unknown };
            };
        };

        const status = axiosLikeError.response?.status;
        const payload = axiosLikeError.response?.data;
        throw {
            status,
            errorMessage: payload?.errorMessage || payload?.message || axiosLikeError.message || 'Request failed',
            ...(payload && typeof payload === 'object' ? payload : {}),
        };
    }
};

export const fetchAppAdmins = (): Promise<AppAdminDto[]> =>
    executeRequest(() => apiClient.get('/admin/roles/app-admins'));

export const grantAppAdmin = (request: GrantAppAdminRequest): Promise<AppAdminDto> =>
    executeRequest(() => apiClient.post('/admin/roles/app-admins', request));

export const revokeAppAdmin = (assignmentId: string): Promise<void> =>
    executeRequest(() => apiClient.delete(`/admin/roles/app-admins/${assignmentId}`));

export const searchAppAdminCandidates = (q: string, limit = 20): Promise<AppUserSearchResult[]> =>
    executeRequest(() =>
        apiClient.get(`/admin/roles/app-admin-candidates`, {params: {q, limit}}),
    );

