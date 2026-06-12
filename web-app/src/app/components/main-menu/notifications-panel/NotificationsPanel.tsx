import React, {useCallback, useEffect, useState} from 'react';
import {useNavigate} from 'react-router-dom';
import {
    Badge,
    Button,
    CounterBadge,
    Divider,
    List,
    ListItem,
    MessageBar,
    MessageBarBody,
    Popover,
    PopoverSurface,
    PopoverTrigger,
    Spinner,
    Tab,
    TabList,
    Text,
    Textarea,
} from '@fluentui/react-components';
import {
    CheckmarkCircleRegular,
    DismissCircleRegular,
} from '@fluentui/react-icons';
import {useNotifications} from '../../../../context/NotificationContext';
import {NotificationDto} from '../../../models/models';
import {NotificationsIcon} from '../../IconBundles';
import {useNotificationsPanelStyles} from './NotificationsPanelStyles';
import NotificationListItem from '../notification/notification-item/NotificationListItem';
import {getMyPendingDecisions, recordWorkflowDecision} from '../../../../services/workflowApi';
import {PendingWorkflowStep} from '../../../../services/types/dtos';
import {realtimeService} from '../../../../services/NotificationService';

const WORKFLOW_NOTIFICATION_TYPES = [
    'workflow.step_assigned', 'WORKFLOW_STEP_ASSIGNED',
    'workflow.escalated', 'WORKFLOW_ESCALATED',
];

type ActiveTab = 'notifications' | 'pending-approvals';

/**
 * Combined notifications and pending approvals popover.
 * A single bell icon in the toolbar opens a panel with two tabs:
 * one for general notifications and one for pending approvals.
 */
const NotificationsPanel: React.FC = () =>
{
    const styles = useNotificationsPanelStyles();
    const navigate = useNavigate();

    // Notifications state
    const {notifications, unreadCount, markAsRead, markAllAsRead} = useNotifications();

    // Pending approvals state
    const [items, setItems] = useState<PendingWorkflowStep[]>([]);
    const [deciding, setDeciding] = useState<string | null>(null);
    const [comments, setComments] = useState<Record<string, string>>({});
    const [decisionError, setDecisionError] = useState<string | null>(null);

    const [activeTab, setActiveTab] = useState<ActiveTab>('notifications');

    // Initial fetch for pending approvals
    useEffect(() =>
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

    // Realtime listener for pending approvals
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
            }
            catch (err: unknown)
            {
                console.error('Decision failed', err);
                const e = err as {errorMessage?: string; message?: string};
                setDecisionError(e?.errorMessage || e?.message || 'Failed to record decision');
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

    const handleNotificationClick = (notification: NotificationDto) =>
    {
        markAsRead(notification.id);

        if (WORKFLOW_NOTIFICATION_TYPES.includes(notification.type as string))
        {
            const sid = notification.exchangeId || notification.data?.exchangeId;
            if (sid)
            {
                navigate(`/exchanges?s=${sid}`);
            }
            return;
        }

        if (notification.exchangeId)
        {
            if (notification.documentId)
            {
                navigate(`/exchanges?s=${notification.exchangeId}&d=${notification.documentId}`);
            }
            else
            {
                navigate(`/exchanges?s=${notification.exchangeId}`);
            }
        }
    };

    return (
        <Popover withArrow>
            <PopoverTrigger disableButtonEnhancement>
                <div className={styles.triggerContainer}>
                    <Button
                        icon={<NotificationsIcon/>}
                        appearance="subtle"
                        shape="circular"
                        aria-label="Notifications and pending approvals"
                    />
                    {unreadCount > 0 && (
                        <CounterBadge
                            className={styles.badge}
                            appearance="filled"
                            color="danger"
                            count={unreadCount}
                        />
                    )}
                </div>
            </PopoverTrigger>

            <PopoverSurface>
                <div className={styles.popoverContent}>
                    <TabList
                        selectedValue={activeTab}
                        onTabSelect={(_e, data) => setActiveTab(data.value as ActiveTab)}
                    >
                        <Tab value="notifications">
                            Notifications
                            {unreadCount > 0 && (
                                <CounterBadge
                                    className={styles.tabCountBadge}
                                    size="small"
                                    appearance="filled"
                                    color="danger"
                                    count={unreadCount}
                                />
                            )}
                        </Tab>
                        <Tab value="pending-approvals">
                            Pending Approvals
                            {items.length > 0 && (
                                <CounterBadge
                                    className={styles.tabCountBadge}
                                    size="small"
                                    appearance="filled"
                                    color="informative"
                                    count={items.length}
                                />
                            )}
                        </Tab>
                    </TabList>

                    <div className={styles.tabContent}>
                        {activeTab === 'notifications' && (
                            <>
                                <List className={styles.notificationList}>
                                    {notifications.length > 0 ? (
                                        notifications.map((notification: NotificationDto) => (
                                            <ListItem key={notification.id}>
                                                <NotificationListItem
                                                    notification={notification}
                                                    onClick={() => handleNotificationClick(notification)}
                                                />
                                                <Divider/>
                                            </ListItem>
                                        ))
                                    ) : (
                                        <Text align="center">No notifications</Text>
                                    )}
                                </List>
                                {notifications.length > 0 && (
                                    <div className={styles.markAllAsRead}>
                                        <Button
                                            size="small"
                                            appearance="primary"
                                            onClick={() => markAllAsRead()}>
                                            Mark all as read
                                        </Button>
                                    </div>
                                )}
                            </>
                        )}

                        {activeTab === 'pending-approvals' && (
                            <div className={styles.approvalsList}>
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
                                    items.map((step) => (
                                        <div key={step.stepInstanceId} className={styles.card}>
                                            <div className={styles.cardHeader}>
                                                <Text weight="semibold">
                                                    {step.name || 'Exchange'}
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
                        )}
                    </div>
                </div>
            </PopoverSurface>
        </Popover>
    );
};

export default NotificationsPanel;


