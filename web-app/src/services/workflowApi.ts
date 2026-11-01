import apiClient from './apiClient';
import {requestFailureOf} from './requestFailure';
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
        throw requestFailureOf(error);
    }
};

export const recordWorkflowDecision = (
    stepInstanceId: string,
    request: WorkflowDecisionRequest,
): Promise<WorkflowDecisionResponse> =>
    executeRequest(() =>
        apiClient.post(`/workflows/steps/${stepInstanceId}/decision`, request),
    );

export const getMyPendingDecisions = (): Promise<PendingWorkflowStep[]> =>
    executeRequest(() => apiClient.get('/workflows/steps/pending'));

