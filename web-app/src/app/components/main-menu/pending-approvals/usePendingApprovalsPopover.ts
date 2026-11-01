import {useCallback, useEffect, useState} from 'react';
import {normalizeApiError} from '../../../../utils/apiErrorUtils.ts';
import {getMyPendingDecisions, recordWorkflowDecision} from '../../../../services/workflowApi';
import {PendingWorkflowStep} from '../../../../services/types/dtos';
import {realtimeService} from '../../../../services/NotificationService';

export type PendingApprovalDecision = 'APPROVE' | 'REJECT';

const isStepAssigned = (type: string) =>
    type === 'workflow.step_assigned' || type === 'WORKFLOW_STEP_ASSIGNED';

const isExchangeSettled = (type: string) =>
    type === 'session.activated' || type === 'EXCHANGE_ACTIVATED' ||
    type === 'session.rejected' || type === 'EXCHANGE_REJECTED';

export const usePendingApprovalsPopover = () =>
{
    const [items, setItems] = useState<PendingWorkflowStep[]>([]);
    const [deciding, setDeciding] = useState<string | null>(null);
    const [comments, setComments] = useState<Record<string, string>>({});
    const [decisionError, setDecisionError] = useState<string | null>(null);
    const [open, setOpen] = useState(false);
    const [openItemId, setOpenItemId] = useState<string | null>(null);

    const fetchPending = useCallback(() =>
    {
        let cancelled = false;
        getMyPendingDecisions()
            .then((data) =>
            {
                if (cancelled) return;
                setItems(data ?? []);
            })
            .catch((err) =>
            {
                console.warn('Failed to load pending approvals', err);
            });
        return () =>
        {
            cancelled = true;
        };
    }, []);

    useEffect(() => fetchPending(), []);
    useEffect(() =>
    {
        if (open) fetchPending();
    }, [open]);

    useEffect(() =>
    {
        const offAssigned = realtimeService.on('NOTIFICATION', (msg) =>
        {
            const n = msg.notification;
            if (!n) return;

            if (isStepAssigned(n.type as string))
            {
                const step: PendingWorkflowStep = {
                    stepInstanceId: n.data?.stepInstanceId || n.id,
                    workflowInstanceId: n.data?.workflowInstanceId || '',
                    stepType: n.data?.stepType || 'APPROVAL',
                    exchangeId: n.exchangeId || n.data?.exchangeId,
                    name: n.data?.name,
                    requestedByEmail: n.data?.requestedByEmail,
                    requestedByName: n.data?.requestedByName,
                    groupName: n.data?.groupName,
                    createdAt: n.timestamp,
                };
                setItems((prev) =>
                {
                    if (prev.some((p) => p.stepInstanceId === step.stepInstanceId)) return prev;
                    return [step, ...prev];
                });
            }

            if (isExchangeSettled(n.type as string))
            {
                const sid = n.exchangeId || n.data?.exchangeId;
                if (sid)
                {
                    setItems((prev) => prev.filter((p) => p.exchangeId !== sid));
                }
            }
        });

        return () =>
        {
            offAssigned();
        };
    }, []);

    const handleDecision = useCallback(
        async (step: PendingWorkflowStep, decision: PendingApprovalDecision) =>
        {
            setDeciding(step.stepInstanceId);
            setDecisionError(null);
            try
            {
                await recordWorkflowDecision(step.stepInstanceId, {
                    decision,
                    reason: comments[step.stepInstanceId]?.trim() || undefined,
                });
                setItems((prev) => prev.filter((p) => p.stepInstanceId !== step.stepInstanceId));
                setOpenItemId((prev) => prev === step.stepInstanceId ? null : prev);
            }
            catch (err: unknown)
            {
                console.error('Decision failed', err);
                setDecisionError(normalizeApiError(err, 'Failed to record decision').message);
            }
            finally
            {
                setDeciding(null);
            }
        },
        [comments],
    );

    const updateComment = (stepId: string, value: string) =>
    {
        setComments((prev) => ({...prev, [stepId]: value}));
    };

    const handleToggle = (_: unknown, data: { openItems: string[] }) =>
    {
        setOpenItemId(data.openItems[0] ?? null);
    };

    return {
        items,
        deciding,
        comments,
        decisionError,
        dismissError: () => setDecisionError(null),
        open,
        setOpen,
        openItemId,
        handleDecision,
        updateComment,
        handleToggle,
    };
};
