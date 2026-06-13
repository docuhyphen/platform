/**
 * Typed Axios wrappers for all /workflows/* management endpoints.
 *
 * Covers:
 *   - Workflow definition CRUD (list, get, create, update, patch status, delete, clone)
 *   - Trigger event registry lookup
 *   - Workflow instance queries (paginated list, detail)
 *
 * Decision endpoints (approve / reject a step) remain in workflowApi.ts.
 */
import apiClient from './apiClient';
import {
    WorkflowDefinitionDto,
    WorkflowDefinitionSummaryDto,
    WorkflowInstanceDetailDto,
    WorkflowInstanceSummaryDto,
    WorkflowTriggerEventDto,
} from '../app/models/models';

// ── Request shapes ────────────────────────────────────────────────────────────

export interface CreateWorkflowDefinitionRequest
{
    name: string;
    summary?: string;
    triggerEvent: string;
    stepsJson: string;
    industryTags?: string[];
    isActive?: boolean;
    isTemplate?: boolean;
}

export interface UpdateWorkflowDefinitionRequest
{
    name?: string;
    summary?: string;
    stepsJson?: string;
    industryTags?: string[];
    isActive?: boolean;
}

export interface PatchWorkflowStatusRequest
{
    isActive: boolean;
}

export interface CloneWorkflowRequest
{
    newName?: string;
}

export interface ListDefinitionsParams
{
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

// ── Internal helper ───────────────────────────────────────────────────────────

const executeRequest = async <T>(fn: () => Promise<{ data: T }>): Promise<T> =>
{
    try
    {
        const { data } = await fn();
        return data;
    }
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    catch (error: any)
    {
        throw error.response?.data || error.message;
    }
};

// ── Definitions ───────────────────────────────────────────────────────────────

/**
 * List workflow definitions accessible to the caller.
 * Returns the org's own definitions plus platform templates.
 * Supports optional filters: tag, triggerEvent, isTemplate.
 */
export const listWorkflowDefinitions = (
    params?: ListDefinitionsParams,
): Promise<WorkflowDefinitionSummaryDto[]> =>
    executeRequest(() => apiClient.get('/workflows/definitions', { params }));

/**
 * Retrieve the full definition, including the stepsJson DSL blob.
 */
export const getWorkflowDefinition = (id: string): Promise<WorkflowDefinitionDto> =>
    executeRequest(() => apiClient.get(`/workflows/definitions/${id}`));

/**
 * Create a new ORG-scoped workflow definition.
 * Requires org admin role.
 */
export const createWorkflowDefinition = (
    request: CreateWorkflowDefinitionRequest,
): Promise<WorkflowDefinitionDto> =>
    executeRequest(() => apiClient.post('/workflows/definitions', request));

/**
 * Replace a definition's editable fields.
 * Blocked by the server while RUNNING instances reference this definition.
 */
export const updateWorkflowDefinition = (
    id: string,
    request: UpdateWorkflowDefinitionRequest,
): Promise<WorkflowDefinitionDto> =>
    executeRequest(() => apiClient.put(`/workflows/definitions/${id}`, request));

/**
 * Toggle a definition active or inactive without a full PUT body.
 */
export const patchWorkflowDefinitionStatus = (
    id: string,
    request: PatchWorkflowStatusRequest,
): Promise<WorkflowDefinitionDto> =>
    executeRequest(() => apiClient.patch(`/workflows/definitions/${id}/status`, request));

/**
 * Soft-delete a definition (sets isActive=false).
 * Blocked by the server while RUNNING instances reference it.
 */
export const deleteWorkflowDefinition = (id: string): Promise<void> =>
    executeRequest(() => apiClient.delete(`/workflows/definitions/${id}`));

/**
 * Clone a definition into the caller's org.
 * Scrubs hardcoded principal UUIDs for portability.
 * The clone starts as inactive.
 */
export const cloneWorkflowDefinition = (
    id: string,
    request: CloneWorkflowRequest,
): Promise<WorkflowDefinitionDto> =>
    executeRequest(() => apiClient.post(`/workflows/definitions/${id}/clone`, request));

// ── Triggers ──────────────────────────────────────────────────────────────────

/**
 * List active trigger event registry entries.
 * Powers the trigger dropdown and subject-field auto-complete in the designer.
 */
export const listWorkflowTriggers = (): Promise<WorkflowTriggerEventDto[]> =>
    executeRequest(() => apiClient.get('/workflows/triggers'));

// ── Instances ─────────────────────────────────────────────────────────────────

/**
 * Paginated list of workflow instances for the caller's org.
 * Supports optional filters: status, subjectResourceType, page, pageSize.
 */
export const listWorkflowInstances = (
    params?: ListInstancesParams,
): Promise<WorkflowInstanceSummaryDto[]> =>
    executeRequest(() => apiClient.get('/workflows/instances', { params }));

/**
 * Full instance detail including all step instances, assignee snapshots, and decision timelines.
 */
export const getWorkflowInstanceDetail = (id: string): Promise<WorkflowInstanceDetailDto> =>
    executeRequest(() => apiClient.get(`/workflows/instances/${id}`));


