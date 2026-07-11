import apiClient from './apiClient';
import {
    BlueprintDefinitionDto,
    BlueprintDefinitionSummaryDto,
    CreateBlueprintRequest,
    UpdateBlueprintRequest,
} from '../app/models/models';

// ── Request shapes ────────────────────────────────────────────────────────────

export interface PatchBlueprintStatusRequest
{
    isActive: boolean;
}

export interface PatchBlueprintPublishedRequest
{
    isPublished: boolean;
}

export interface CloneBlueprintRequest
{
    newName?: string;
    targetScope?: 'PERSONAL' | 'ORG';
}

export interface ListBlueprintsParams
{
    scope?: string;
    tag?: string;
    isTemplate?: boolean;
}

// ── Internal helper ───────────────────────────────────────────────────────────

const executeRequest = async <T>(fn: () => Promise<{ data: T }>): Promise<T> =>
{
    try
    {
        const { data } = await fn();
        return data;
    }
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    catch (error: unknown)
    {
        throw error.response?.data || error.message;
    }
};

// ── Blueprints ────────────────────────────────────────────────────────────────

export const listBlueprints = (
    params?: ListBlueprintsParams,
): Promise<BlueprintDefinitionSummaryDto[]> =>
    executeRequest(() => apiClient.get('/blueprints', { params }));

export const getBlueprint = (id: string): Promise<BlueprintDefinitionDto> =>
    executeRequest(() => apiClient.get(`/blueprints/${id}`));

export const createBlueprint = (
    request: CreateBlueprintRequest,
): Promise<BlueprintDefinitionDto> =>
    executeRequest(() => apiClient.post('/blueprints', request));

export const updateBlueprint = (
    id: string,
    request: UpdateBlueprintRequest,
): Promise<BlueprintDefinitionDto> =>
    executeRequest(() => apiClient.put(`/blueprints/${id}`, request));

export const patchBlueprintStatus = (
    id: string,
    request: PatchBlueprintStatusRequest,
): Promise<BlueprintDefinitionDto> =>
    executeRequest(() => apiClient.patch(`/blueprints/${id}/status`, request));

export const patchBlueprintPublished = (
    id: string,
    request: PatchBlueprintPublishedRequest,
): Promise<BlueprintDefinitionDto> =>
    executeRequest(() => apiClient.patch(`/blueprints/${id}/published`, request));

export const deleteBlueprint = (id: string): Promise<void> =>
    executeRequest(() => apiClient.delete(`/blueprints/${id}`));

export const cloneBlueprint = (
    id: string,
    request: CloneBlueprintRequest,
): Promise<BlueprintDefinitionDto> =>
    executeRequest(() => apiClient.post(`/blueprints/${id}/clone`, request));
