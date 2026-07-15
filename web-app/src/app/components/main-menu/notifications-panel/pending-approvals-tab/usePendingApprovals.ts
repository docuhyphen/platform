import {useCallback, useEffect, useState} from 'react';
import {getMyPendingDecisions, recordWorkflowDecision} from '../../../../../services/workflowApi';
import {PendingWorkflowStep} from '../../../../../services/types/dtos';
import {realtimeService} from '../../../../../services/NotificationService';

export interface PendingApprovalsState
{
    items: PendingWorkflowStep[];
    deciding: string | null;
    comments: Record<string, string>;
    decisionError: string | null;
    decide: (step: PendingWorkflowStep, decision: 'APPROVE' | 'REJECT') => Promise<void>;
    updateComment: (stepId: string, value: string) => void;
    dismissError: () => void;
}

export const usePendingApprovals = (): PendingApprovalsState =>
{
    const [items, setItems] = useState<PendingWorkflowStep[]>([]);
    const [deciding, setDeciding] = useState<string | null>(null);
    const [comments, setComments] = useState<Record<string, string>>({});
    const [decisionError, setDecisionError] = useState<string | null>(null);

    useEffect(() =>
    {
        let cancelled = false;
        getMyPendingDecisions()
            .then((data) =>
            {
                if (!cancelled) setItems(data ?? []);
            })
            .catch((error: unknown) => console.warn('Failed to load pending approvals', error));
        return () =>
        {
            cancelled = true;
        };
    }, []);

    useEffect(() =>
    {
        const removeListener = realtimeService.on('NOTIFICATION', (message) =>
        {
            const notification = message.notification;
            if (!notification) return;

            if (
                notification.type as string === 'workflow.step_assigned' ||
                notification.type as string === 'WORKFLOW_STEP_ASSIGNED'
            )
            {
                const step: PendingWorkflowStep = {
                    stepInstanceId: notification.data?.stepInstanceId || notification.id,
                    workflowInstanceId: notification.data?.workflowInstanceId || '',
                    stepType: notification.data?.stepType || 'APPROVAL',
                    exchangeId: notification.exchangeId || notification.data?.exchangeId,
                    name: notification.data?.name,
                    requestedByEmail: notification.data?.requestedByEmail,
                    requestedByName: notification.data?.requestedByName,
                    groupName: notification.data?.groupName,
                    createdAt: notification.timestamp,
                };
                setItems((current) => current.some((item) => item.stepInstanceId === step.stepInstanceId)
                    ? current
                    : [step, ...current]);
            }

            if (
                notification.type as string === 'session.activated' ||
                notification.type as string === 'EXCHANGE_ACTIVATED' ||
                notification.type as string === 'session.rejected' ||
                notification.type as string === 'EXCHANGE_REJECTED'
            )
            {
                const exchangeId = notification.exchangeId || notification.data?.exchangeId;
                if (exchangeId)
                {
                    setItems((current) => current.filter((item) => item.exchangeId !== exchangeId));
                }
            }
        });
        return removeListener;
    }, []);

    const decide = useCallback(async (step: PendingWorkflowStep, decision: 'APPROVE' | 'REJECT') =>
    {
        setDeciding(step.stepInstanceId);
        setDecisionError(null);
        try
        {
            await recordWorkflowDecision(step.stepInstanceId, {
                decision,
                reason: comments[step.stepInstanceId]?.trim() || undefined,
            });
            setItems((current) => current.filter((item) => item.stepInstanceId !== step.stepInstanceId));
        }
        catch (error: unknown)
        {
            console.error('Decision failed', error);
            const detail = error as {errorMessage?: string; message?: string};
            setDecisionError(detail.errorMessage || detail.message || 'Failed to record decision');
        }
        finally
        {
            setDeciding(null);
        }
    }, [comments]);

    return {
        items,
        deciding,
        comments,
        decisionError,
        decide,
        updateComment: (stepId, value) => setComments((current) => ({...current, [stepId]: value})),
        dismissError: () => setDecisionError(null),
    };
};
