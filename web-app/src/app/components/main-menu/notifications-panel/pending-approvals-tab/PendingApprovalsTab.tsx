import React, {useState} from 'react';
import {
    Accordion,
    AccordionHeader,
    AccordionItem,
    AccordionPanel,
    Badge,
    Button,
    MessageBar,
    MessageBarBody,
    Spinner,
    Text,
    Textarea,
} from '@fluentui/react-components';
import {CheckmarkCircleRegular, DismissCircleRegular} from '@fluentui/react-icons';
import {PendingApprovalsState} from './usePendingApprovals';
import {usePendingApprovalsTabStyles} from './PendingApprovalsTabStyles';

interface PendingApprovalsTabProps
{
    state: PendingApprovalsState;
}

const PendingApprovalsTab: React.FC<PendingApprovalsTabProps> = ({state}) =>
{
    const styles = usePendingApprovalsTabStyles();
    const [openItemId, setOpenItemId] = useState<string | null>(null);

    if (state.items.length === 0)
    {
        return (
            <div
                id="pending-approvals-tab-empty"
                className={styles.emptyState}
            >
                <Text id="pending-approvals-tab-empty-text">No pending approvals</Text>
            </div>
        );
    }

    return (
        <div
            id="pending-approvals-tab-list"
            className={styles.list}
        >
            {state.decisionError && (
                <MessageBar
                    id="pending-approvals-tab-error"
                    intent="error"
                    onClick={state.dismissError}
                >
                    <MessageBarBody>{state.decisionError}</MessageBarBody>
                </MessageBar>
            )}
            <Accordion
                id="pending-approvals-tab-accordion"
                collapsible
                openItems={openItemId ? [openItemId] : []}
                onToggle={(_event, data) => setOpenItemId(data.openItems[0]?.toString() ?? null)}
            >
                {state.items.map((step) => (
                    <AccordionItem
                        key={step.stepInstanceId}
                        value={step.stepInstanceId}
                    >
                        <AccordionHeader>
                            <div
                                id={`pending-approval-header-${step.stepInstanceId}`}
                                className={styles.header}
                            >
                                <Text weight="semibold">{step.name || 'Exchange'}</Text>
                                <Badge
                                    appearance="outline"
                                    color="warning"
                                >
                                    Pending
                                </Badge>
                            </div>
                        </AccordionHeader>
                        <AccordionPanel>
                            <div
                                id={`pending-approval-panel-${step.stepInstanceId}`}
                                className={styles.panel}
                            >
                                {step.requestedByName && (
                                    <Text size={200}>
                                        Requested by {step.requestedByName}
                                        {step.requestedByEmail ? ` (${step.requestedByEmail})` : ''}
                                    </Text>
                                )}
                                {step.groupName && <Text size={200}>Group: {step.groupName}</Text>}
                                <Textarea
                                    id={`pending-approval-comment-${step.stepInstanceId}`}
                                    className={styles.comment}
                                    placeholder="Optional comment..."
                                    size="small"
                                    value={state.comments[step.stepInstanceId] || ''}
                                    onChange={(_event, data) => state.updateComment(step.stepInstanceId, data.value)}
                                />
                                <div
                                    id={`pending-approval-actions-${step.stepInstanceId}`}
                                    className={styles.actions}
                                >
                                    <Button
                                        id={`pending-approval-approve-${step.stepInstanceId}`}
                                        appearance="primary"
                                        size="small"
                                        shape="circular"
                                        icon={<CheckmarkCircleRegular/>}
                                        disabled={state.deciding === step.stepInstanceId}
                                        onClick={() => void state.decide(step, 'APPROVE')}
                                    >
                                        {state.deciding === step.stepInstanceId ? <Spinner size="tiny"/> : 'Approve'}
                                    </Button>
                                    <Button
                                        id={`pending-approval-reject-${step.stepInstanceId}`}
                                        appearance="secondary"
                                        size="small"
                                        shape="circular"
                                        icon={<DismissCircleRegular/>}
                                        disabled={state.deciding === step.stepInstanceId}
                                        onClick={() => void state.decide(step, 'REJECT')}
                                    >
                                        Reject
                                    </Button>
                                </div>
                            </div>
                        </AccordionPanel>
                    </AccordionItem>
                ))}
            </Accordion>
        </div>
    );
};

export default PendingApprovalsTab;
