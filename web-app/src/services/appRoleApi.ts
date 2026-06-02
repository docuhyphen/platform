/**
 * App Admin role management API — calls /admin/roles/app-admins (Plan 04).
 */
import apiClient from './apiClient';
import {AppAdminDto, GrantAppAdminRequest} from './types/dtos';

const executeRequest = async <T>(fn: () => Promise<{ data: T }>): Promise<T> =>
{
    try
    {
        const {data} = await fn();
        return data;
    }
    catch (error: any)
    {
        throw error.response?.data || error.message;
    }
};

export const fetchAppAdmins = (): Promise<AppAdminDto[]> =>
    executeRequest(() => apiClient.get('/admin/roles/app-admins'));

export const grantAppAdmin = (request: GrantAppAdminRequest): Promise<AppAdminDto> =>
    executeRequest(() => apiClient.post('/admin/roles/app-admins', request));

export const revokeAppAdmin = (assignmentId: string): Promise<void> =>
    executeRequest(() => apiClient.delete(`/admin/roles/app-admins/${assignmentId}`));
