import React, {useCallback, useEffect, useState} from 'react';
import {
    Badge,
    Button,
    CounterBadge,
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
import {recordWorkflowDecision} from '../../../../services/workflowApi';
import {PendingWorkflowStep} from '../../../../services/types/dtos';
import {realtimeService} from '../../../../services/NotificationService';

/**
 * Pending approvals popover — surfaces workflow.step_assigned items so an assignee can
 * approve/reject directly from the top bar. New items arrive via realtime push.
 */
const PendingApprovals: React.FC = () =>
{
    const styles = usePendingApprovalsStyles();
    const [items, setItems] = useState<PendingWorkflowStep[]>([]);
    const [deciding, setDeciding] = useState<string | null>(null);
    const [comments, setComments] = useState<Record<string, string>>({});

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
                    sessionId: n.sessionId || n.data?.sessionId,
                    sessionName: n.data?.sessionName,
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
                n.type as string === 'session.activated' || n.type as string === 'SESSION_ACTIVATED' ||
                n.type as string === 'session.rejected' || n.type as string === 'SESSION_REJECTED'
            )
            {
                const sid = n.sessionId || n.data?.sessionId;
                if (sid)
                {
                    setItems((prev) => prev.filter((p) => p.sessionId !== sid));
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
            try
            {
                await recordWorkflowDecision(step.stepInstanceId, {
                    decision,
                    reason: comments[step.stepInstanceId]?.trim() || undefined,
                });
                setItems((prev) => prev.filter((p) => p.stepInstanceId !== step.stepInstanceId));
            }
            catch (err: any)
            {
                console.error('Decision failed', err);
                alert(err?.errorMessage || err?.message || 'Failed to record decision');
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

    return (
        <Popover withArrow>
            <PopoverTrigger disableButtonEnhancement>
                <div className={styles.triggerContainer}>
                    <Button
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
                    {items.length === 0 ? (
                        <div className={styles.emptyState}>
                            <Text>No pending approvals</Text>
                        </div>
                    ) : (
                        items.map((step) => (
                            <div key={step.stepInstanceId} className={styles.card}>
                                <div className={styles.cardHeader}>
                                    <Text weight="semibold">
                                        {step.sessionName || 'Sharing Session'}
                                    </Text>
                                    <Badge appearance="outline" color="warning">
                                        Pending
                                    </Badge>
                                </div>
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
                                    className={styles.commentField}
                                    placeholder="Optional comment..."
                                    size="small"
                                    value={comments[step.stepInstanceId] || ''}
                                    onChange={(_e, d) => updateComment(step.stepInstanceId, d.value)}
                                />
                                <div className={styles.actions}>
                                    <Button
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
                        ))
                    )}
                </div>
            </PopoverSurface>
        </Popover>
    );
};

export default PendingApprovals;
