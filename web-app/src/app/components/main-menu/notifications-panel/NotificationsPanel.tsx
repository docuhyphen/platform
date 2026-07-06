import React, {useCallback, useEffect, useState} from 'react';
import {useNavigate} from 'react-router-dom';
import {
    Accordion,
    AccordionHeader,
    AccordionItem,
    AccordionPanel,
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
    const [openItemId, setOpenItemId] = useState<string | null>(null);
    const hasBellAlert = notifications.length > 0 || items.length > 0;
    const hasNotificationAlert = notifications.length > 0;
    const hasPendingApprovalAlert = items.length > 0;

    const handleToggle = (_: unknown, data: { openItems: string[] }) =>
    {
        setOpenItemId(data.openItems[0] ?? null);
    };

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
                        id={"notifications-panel-bell-btn"}
                        icon={<NotificationsIcon/>}
                        appearance="subtle"
                        shape="circular"
                        aria-label="Notifications and pending approvals"
                    />
                    {hasBellAlert && (
                        <span
                            id={"notifications-panel-bell-dot"}
                            className={styles.alertDot}
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
                        <Tab
                            id={"notifications-panel-tab-notifications"}
                            value="notifications"
                            className={hasNotificationAlert ? styles.alertTab : undefined}
                        >
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
                        <Tab
                            id={"notifications-panel-tab-pending-approvals"}
                            value="pending-approvals"
                            className={hasPendingApprovalAlert ? styles.alertTab : undefined}
                        >
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
                                            id={"notifications-panel-mark-all-read-btn"}
                                            size="small"
                                            shape={"circular"}
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
                                                            id={`notifications-panel-comment-${step.stepInstanceId}`}
                                                            className={styles.commentField}
                                                            placeholder="Optional comment..."
                                                            size="small"
                                                            value={comments[step.stepInstanceId] || ''}
                                                            onChange={(_e, d) => updateComment(step.stepInstanceId, d.value)}
                                                        />
                                                        <div className={styles.actions}>
                                                            <Button
                                                                id={`notifications-panel-approve-btn-${step.stepInstanceId}`}
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
                                                                id={`notifications-panel-reject-btn-${step.stepInstanceId}`}
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
                        )}
                    </div>
                </div>
            </PopoverSurface>
        </Popover>
    );
};

export default NotificationsPanel;


