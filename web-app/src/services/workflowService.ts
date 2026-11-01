import apiClient from './apiClient';
import {requestFailureOf} from './requestFailure';
import {
    WorkflowDefinitionDto,
    WorkflowDefinitionSummaryDto,
    WorkflowEntityRefDto,
    WorkflowInstanceDetailDto,
    WorkflowInstanceSummaryDto,
    WorkflowTriggerEventDto,
} from '../app/models/models';

export interface CreateWorkflowDefinitionRequest
{
    name: string;
    summary?: string;
    triggerEvent: string;
    stepsJson: string;
    generalTags?: string[];
    isActive?: boolean;
    isTemplate?: boolean;
    scope?: 'PERSONAL' | 'ORG' | 'APP';
}

export interface UpdateWorkflowDefinitionRequest
{
    name?: string;
    summary?: string;
    stepsJson?: string;
    generalTags?: string[];
    isActive?: boolean;
}

export interface PatchWorkflowStatusRequest
{
    isActive: boolean;
}

export interface PatchWorkflowPublishedRequest
{
    isPublished: boolean;
}

export interface CloneWorkflowRequest
{
    newName?: string;
}

export interface ListDefinitionsParams
{
    scope?: 'APP' | 'ORG' | 'PERSONAL';
    tag?: string;
    triggerEvent?: string;
    isTemplate?: boolean;
}

export interface ListInstancesParams
{
    status?: string;
    subjectResourceType?: string;
    page?: number;
    pageSize?: number;
}

const executeRequest = async <T>(fn: () => Promise<{ data: T }>): Promise<T> =>
{
    try
    {
        const { data } = await fn();
        return data;
    }
    catch (error: unknown)
    {
        throw requestFailureOf(error);
    }
};

export const listWorkflowDefinitions = (
    params?: ListDefinitionsParams,
): Promise<WorkflowDefinitionSummaryDto[]> =>
    executeRequest(() => apiClient.get('/workflows/definitions', { params }));

export const getWorkflowDefinition = (id: string): Promise<WorkflowDefinitionDto> =>
    executeRequest(() => apiClient.get(`/workflows/definitions/${id}`));

export const createWorkflowDefinition = (
    request: CreateWorkflowDefinitionRequest,
): Promise<WorkflowDefinitionDto> =>
    executeRequest(() => apiClient.post('/workflows/definitions', request));

export const updateWorkflowDefinition = (
    id: string,
    request: UpdateWorkflowDefinitionRequest,
): Promise<WorkflowDefinitionDto> =>
    executeRequest(() => apiClient.put(`/workflows/definitions/${id}`, request));

export const patchWorkflowDefinitionStatus = (
    id: string,
    request: PatchWorkflowStatusRequest,
): Promise<WorkflowDefinitionDto> =>
    executeRequest(() => apiClient.patch(`/workflows/definitions/${id}/status`, request));

export const patchWorkflowDefinitionPublished = (
    id: string,
    request: PatchWorkflowPublishedRequest,
): Promise<WorkflowDefinitionDto> =>
    executeRequest(() => apiClient.patch(`/workflows/definitions/${id}/published`, request));

export const deleteWorkflowDefinition = (id: string): Promise<void> =>
    executeRequest(() => apiClient.delete(`/workflows/definitions/${id}`));

export const cloneWorkflowDefinition = (
    id: string,
    request: CloneWorkflowRequest,
): Promise<WorkflowDefinitionDto> =>
    executeRequest(() => apiClient.post(`/workflows/definitions/${id}/clone`, request));

export const listWorkflowTriggers = (): Promise<WorkflowTriggerEventDto[]> =>
    executeRequest(() => apiClient.get('/workflows/triggers'));

export const lookupWorkflowEntities = (
    lookupType: string,
    q: string,
): Promise<WorkflowEntityRefDto[]> =>
    executeRequest(() => apiClient.get('/workflows/entity-lookup', {params: {lookupType, q}}));

export const listWorkflowInstances = (
    params?: ListInstancesParams,
): Promise<WorkflowInstanceSummaryDto[]> =>
    executeRequest(() => apiClient.get('/workflows/instances', { params }));

export const getWorkflowInstanceDetail = (id: string): Promise<WorkflowInstanceDetailDto> =>
    executeRequest(() => apiClient.get(`/workflows/instances/${id}`));

