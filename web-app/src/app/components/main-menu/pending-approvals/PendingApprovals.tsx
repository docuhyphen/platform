import React from 'react';
import {
    Accordion,
    Button,
    CounterBadge,
    MessageBar,
    MessageBarBody,
    Popover,
    PopoverSurface,
    PopoverTrigger,
    Text,
} from '@fluentui/react-components';
import {TaskListSquareLtrRegular} from '@fluentui/react-icons';
import {usePendingApprovalsStyles} from './PendingApprovalsStyles';
import {usePendingApprovalsPopover} from './usePendingApprovalsPopover.ts';
import PendingApprovalItem from './pending-approval-item/PendingApprovalItem.tsx';

const PendingApprovals: React.FC = () =>
{
    const styles = usePendingApprovalsStyles();
    const {
        items,
        deciding,
        comments,
        decisionError,
        dismissError,
        open,
        setOpen,
        openItemId,
        handleDecision,
        updateComment,
        handleToggle,
    } = usePendingApprovalsPopover();

    return (
        <Popover withArrow
                 open={open}
                 onOpenChange={(_, d) => setOpen(d.open)}>
            <PopoverTrigger disableButtonEnhancement>
                <div id={"pending-approvals-div-1"}
                     className={styles.triggerContainer}>
                    <Button
                        id={"pending-approvals-trigger-btn"}
                        icon={<TaskListSquareLtrRegular/>}
                        appearance="subtle"
                        shape="circular"
                    />
                    {items.length > 0 && (
                        <CounterBadge
                            id={"pending-approvals-counter-badge-1"}
                            className={styles.badge}
                            appearance="filled"
                            color="informative"
                            count={items.length}
                        />
                    )}
                </div>
            </PopoverTrigger>
            <PopoverSurface id={"pending-approvals-popover-surface-1"}>
                <div id={"pending-approvals-div-2"}
                     className={styles.container}>
                    {decisionError && (
                        <MessageBar id={"pending-approvals-message-bar-1"}
                                    intent="error"
                                    onClick={dismissError}>
                            <MessageBarBody id={"pending-approvals-message-bar-body-1"}>{decisionError}</MessageBarBody>
                        </MessageBar>
                    )}
                    {items.length === 0 ? (
                        <div id={"pending-approvals-div-3"}
                             className={styles.emptyState}>
                            <Text id={"pending-approvals-text-1"}>No pending approvals</Text>
                        </div>
                    ) : (
                        <Accordion
                            id={"pending-approvals-accordion-1"}
                            collapsible
                            openItems={openItemId ? [openItemId] : []}
                            onToggle={handleToggle}
                        >
                            {items.map((step) => (
                                <PendingApprovalItem key={step.stepInstanceId}
                                                     step={step}
                                                     comment={comments[step.stepInstanceId] || ''}
                                                     deciding={deciding === step.stepInstanceId}
                                                     onCommentChange={(value) => updateComment(step.stepInstanceId, value)}
                                                     onDecision={(decision) => handleDecision(step, decision)}/>
                            ))}
                        </Accordion>
                    )}
                </div>
            </PopoverSurface>
        </Popover>
    );
};

export default PendingApprovals;
