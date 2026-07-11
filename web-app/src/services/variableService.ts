import apiClient from './apiClient';
import {
    AvailableVariablesDto,
    CreateSequenceRequest,
    CreateVariableRequest,
    SequenceDefinitionDto,
    UpdateSequenceRequest,
    UpdateVariableRequest,
    VariableDefinitionDto,
    VariableScope,
} from '../app/models/models';

const executeRequest = async <T>(fn: () => Promise<{ data: T }>): Promise<T> =>
{
    try
    {
        const {data} = await fn();
        return data;
    }
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    catch (error: unknown)
    {
        throw error.response?.data || error.message;
    }
};

// ── Available variables ───────────────────────────────────────────────────────

export const getAvailableVariables = (): Promise<AvailableVariablesDto> =>
    executeRequest(() => apiClient.get('/variables/available'));

// ── Sequences ─────────────────────────────────────────────────────────────────

export const listSequences = (isActive?: boolean): Promise<SequenceDefinitionDto[]> =>
    executeRequest(() => apiClient.get('/sequences', {params: isActive !== undefined ? {isActive} : undefined}));

export const createSequence = (request: CreateSequenceRequest): Promise<SequenceDefinitionDto> =>
    executeRequest(() => apiClient.post('/sequences', request));

export const updateSequence = (id: string, request: UpdateSequenceRequest): Promise<SequenceDefinitionDto> =>
    executeRequest(() => apiClient.put(`/sequences/${id}`, request));

export const deleteSequence = (id: string): Promise<void> =>
    executeRequest(() => apiClient.delete(`/sequences/${id}`));

export const resetSequenceCounter = (id: string): Promise<SequenceDefinitionDto> =>
    executeRequest(() => apiClient.patch(`/sequences/${id}/reset`));

// ── Variables ─────────────────────────────────────────────────────────────────

export const listVariables = (scope: VariableScope): Promise<VariableDefinitionDto[]> =>
    executeRequest(() => apiClient.get('/variables', {params: {scope}}));

export const createVariable = (request: CreateVariableRequest): Promise<VariableDefinitionDto> =>
    executeRequest(() => apiClient.post('/variables', request));

export const updateVariable = (id: string, request: UpdateVariableRequest): Promise<VariableDefinitionDto> =>
    executeRequest(() => apiClient.put(`/variables/${id}`, request));

export const deleteVariable = (id: string): Promise<void> =>
    executeRequest(() => apiClient.delete(`/variables/${id}`));
