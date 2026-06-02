/**
 * Workflow decision API — calls POST /workflows/steps/{stepInstanceId}/decision.
 */
import apiClient from './apiClient';
import {WorkflowDecisionRequest, WorkflowDecisionResponse} from './types/dtos';

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

/**
 * Record an APPROVE or REJECT decision on a pending workflow step.
 * The decider identity comes from the auth session — never the body.
 */
export const recordWorkflowDecision = (
    stepInstanceId: string,
    request: WorkflowDecisionRequest,
): Promise<WorkflowDecisionResponse> =>
    executeRequest(() =>
        apiClient.post(`/workflows/steps/${stepInstanceId}/decision`, request),
    );
