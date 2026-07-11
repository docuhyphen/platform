import React, {useCallback, useEffect, useState} from 'react';
import {
    Accordion,
    AccordionHeader,
    AccordionItem,
    AccordionPanel,
    Badge,
    Button,
    CounterBadge,
    MessageBar,
    MessageBarBody,
    Popover,
    PopoverSurface,
    PopoverTrigger,
    Spinner,
    Text,
    Textarea,
} from '@fluentui/react-components';
import {
    CheckmarkCircleRegular,
    DismissCircleRegular,
    TaskListSquareLtrRegular,
} from '@fluentui/react-icons';
import {usePendingApprovalsStyles} from './PendingApprovalsStyles';
import {getMyPendingDecisions, recordWorkflowDecision} from '../../../../services/workflowApi';
import {PendingWorkflowStep} from '../../../../services/types/dtos';
import {realtimeService} from '../../../../services/NotificationService';

/**
 * Pending approvals popover  -  surfaces workflow.step_assigned items so an assignee can
 * approve/reject directly from the top bar. Items are shown as a single-open accordion
 * to keep the list compact when there are many pending steps.
 */
const PendingApprovals: React.FC = () =>
{
    const styles = usePendingApprovalsStyles();
    const [items, setItems] = useState<PendingWorkflowStep[]>([]);
    const [deciding, setDeciding] = useState<string | null>(null);
    const [comments, setComments] = useState<Record<string, string>>({});
    const [decisionError, setDecisionError] = useState<string | null>(null);
    const [open, setOpen] = React.useState(false);
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

    // Fetch on mount and every time the popover is opened so the list is always current
    // (the WS push channel is not yet wired, so we rely on polling-on-open instead).
    useEffect(() => fetchPending(), []);
    useEffect(() =>
    {
        if (open) fetchPending();
    }, [open]);

    // Listen for realtime workflow events
    useEffect(() =>
    {
        const offAssigned = realtimeService.on('NOTIFICATION', (msg) =>
        {
            const n = msg.notification;
            if (!n) return;

            if (n.type as string === 'workflow.step_assigned' || n.type as string === 'WORKFLOW_STEP_ASSIGNED')
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

            // Remove items when session is activated or rejected (decided elsewhere)
            if (
                n.type as string === 'session.activated' || n.type as string === 'EXCHANGE_ACTIVATED' ||
                n.type as string === 'session.rejected' || n.type as string === 'EXCHANGE_REJECTED'
            )
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
        async (step: PendingWorkflowStep, decision: 'APPROVE' | 'REJECT') =>
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
                setDecisionError(err?.errorMessage || err?.message || 'Failed to record decision');
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

    return (
        <Popover withArrow open={open} onOpenChange={(_, d) => setOpen(d.open)}>
            <PopoverTrigger disableButtonEnhancement>
                <div className={styles.triggerContainer}>
                    <Button
                        id={"pending-approvals-trigger-btn"}
                        icon={<TaskListSquareLtrRegular/>}
                        appearance="subtle"
                        shape="circular"
                    />
                    {items.length > 0 && (
                        <CounterBadge
                            className={styles.badge}
                            appearance="filled"
                            color="informative"
                            count={items.length}
                        />
                    )}
                </div>
            </PopoverTrigger>
            <PopoverSurface>
                <div className={styles.container}>
                    {decisionError && (
                        <MessageBar intent="error" onClick={() => setDecisionError(null)}>
                            <MessageBarBody>{decisionError}</MessageBarBody>
                        </MessageBar>
                    )}
                    {items.length === 0 ? (
                        <div className={styles.emptyState}>
                            <Text>No pending approvals</Text>
                        </div>
                    ) : (
                        <Accordion
                            collapsible
                            openItems={openItemId ? [openItemId] : []}
                            onToggle={handleToggle}
                        >
                            {items.map((step) => (
                                <AccordionItem key={step.stepInstanceId} value={step.stepInstanceId}>
                                    <AccordionHeader>
                                        <div className={styles.accordionHeader}>
                                            <Text weight="semibold">{step.name || 'Exchange'}</Text>
                                            <Badge appearance="outline" color="warning">Pending</Badge>
                                        </div>
                                    </AccordionHeader>
                                    <AccordionPanel>
                                        <div className={styles.panelContent}>
                                            {step.requestedByName && (
                                                <Text size={200}>
                                                    Requested by {step.requestedByName}
                                                    {step.requestedByEmail ? ` (${step.requestedByEmail})` : ''}
                                                </Text>
                                            )}
                                            {step.groupName && (
                                                <Text size={200}>Group: {step.groupName}</Text>
                                            )}
                                            <Textarea
                                                id={`pending-approval-comment-${step.stepInstanceId}`}
                                                className={styles.commentField}
                                                placeholder="Optional comment..."
                                                size="small"
                                                value={comments[step.stepInstanceId] || ''}
                                                onChange={(_e, d) => updateComment(step.stepInstanceId, d.value)}
                                            />
                                            <div className={styles.actions}>
                                                <Button
                                                    id={`pending-approval-approve-btn-${step.stepInstanceId}`}
                                                    appearance="primary"
                                                    size="small"
                                                    shape="circular"
                                                    icon={<CheckmarkCircleRegular/>}
                                                    disabled={deciding === step.stepInstanceId}
                                                    onClick={() => handleDecision(step, 'APPROVE')}
                                                >
                                                    {deciding === step.stepInstanceId ? (
                                                        <Spinner size="tiny"/>
                                                    ) : (
                                                        'Approve'
                                                    )}
                                                </Button>
                                                <Button
                                                    id={`pending-approval-reject-btn-${step.stepInstanceId}`}
                                                    appearance="secondary"
                                                    size="small"
                                                    shape="circular"
                                                    icon={<DismissCircleRegular/>}
                                                    disabled={deciding === step.stepInstanceId}
                                                    onClick={() => handleDecision(step, 'REJECT')}
                                                >
                                                    Reject
                                                </Button>
                                            </div>
                                        </div>
                                    </AccordionPanel>
                                </AccordionItem>
                            ))}
                        </Accordion>
                    )}
                </div>
            </PopoverSurface>
        </Popover>
    );
};

export default PendingApprovals;
