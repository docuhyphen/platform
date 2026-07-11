/**
 * Personal groups API: calls /me/groups endpoints.
 */
import apiClient from './apiClient';
import {
    AddGroupMembersRequest,
    CreatePersonalGroupRequest,
    PrincipalGroupDto,
    UpdatePersonalGroupRequest,
} from './types/dtos';

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

export const fetchPersonalGroups = (): Promise<PrincipalGroupDto[]> =>
    executeRequest(() => apiClient.get('/me/groups'));

export const fetchPersonalGroup = (groupId: string): Promise<PrincipalGroupDto> =>
    executeRequest(() => apiClient.get(`/me/groups/${groupId}`));

export const createPersonalGroup = (request: CreatePersonalGroupRequest): Promise<PrincipalGroupDto> =>
    executeRequest(() => apiClient.post('/me/groups', request));

export const updatePersonalGroup = (groupId: string, request: UpdatePersonalGroupRequest): Promise<PrincipalGroupDto> =>
    executeRequest(() => apiClient.put(`/me/groups/${groupId}`, request));

export const addPersonalGroupMembers = (groupId: string, request: AddGroupMembersRequest): Promise<PrincipalGroupDto> =>
    executeRequest(() => apiClient.post(`/me/groups/${groupId}/members`, request));

export const removePersonalGroupMember = (
    groupId: string,
    principalId: string,
    principalKind: string = 'USER',
): Promise<void> =>
    executeRequest(() =>
        apiClient.delete(`/me/groups/${groupId}/members/${principalId}`, {
            params: {principalKind},
        }),
    );

export const deletePersonalGroup = (groupId: string): Promise<void> =>
    executeRequest(() => apiClient.delete(`/me/groups/${groupId}`));

export const uploadPersonalGroupIcon = (groupId: string, formData: FormData): Promise<PrincipalGroupDto> =>
    executeRequest(() =>
        apiClient.post(`/me/groups/${groupId}/icon`, formData, {
            headers: {'Content-Type': 'multipart/form-data'},
        }),
    );

export const deletePersonalGroupIcon = (groupId: string): Promise<PrincipalGroupDto> =>
    executeRequest(() => apiClient.delete(`/me/groups/${groupId}/icon`));
