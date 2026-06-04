/**
 * App Admin role management API — calls /admin/roles/app-admins (Plan 04) and
 * /admin/roles/app-admin-candidates (Plan 07 G3b — global user search for the picker).
 */
import apiClient from './apiClient';
import {AppAdminDto, AppUserSearchResult, GrantAppAdminRequest} from './types/dtos';

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

/**
 * Plan 07 G3b: app-admin is a global role, so the picker must reach users outside the
 * caller's org. Backend (AppRoleResource) guards this with the same isAppAdmin check as
 * the rest of /admin/roles, so non-admins get a 403.
 */
export const searchAppAdminCandidates = (q: string, limit = 20): Promise<AppUserSearchResult[]> =>
    executeRequest(() =>
        apiClient.get(`/admin/roles/app-admin-candidates`, {params: {q, limit}}),
    );

