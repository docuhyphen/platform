/**
 * Workflow decision API: calls POST /workflows/steps/{stepInstanceId}/decision and
 * GET /workflows/steps/pending. Decider identity comes from the auth session, never the body.
 */
import apiClient from './apiClient';
import {PendingWorkflowStep, WorkflowDecisionRequest, WorkflowDecisionResponse} from './types/dtos';

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

/**
 * Record an APPROVE or REJECT decision on a pending workflow step.
 * The decider identity comes from the auth session, never the body.
 */
export const recordWorkflowDecision = (
    stepInstanceId: string,
    request: WorkflowDecisionRequest,
): Promise<WorkflowDecisionResponse> =>
    executeRequest(() =>
        apiClient.post(`/workflows/steps/${stepInstanceId}/decision`, request),
    );

/**
 * List PENDING workflow steps where the current user is an assignee.
 * Called on PendingApprovals mount so a page refresh doesn't drop missed realtime
 * pushes. Backend resolves "current user" from the auth session.
 */
export const getMyPendingDecisions = (): Promise<PendingWorkflowStep[]> =>
    executeRequest(() => apiClient.get('/workflows/steps/pending'));

