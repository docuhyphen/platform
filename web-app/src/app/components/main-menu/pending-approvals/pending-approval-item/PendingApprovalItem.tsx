import React from 'react';
import {
    AccordionHeader,
    AccordionItem,
    AccordionPanel,
    Badge,
    Button,
    Spinner,
    Text,
    Textarea,
} from '@fluentui/react-components';
import {CheckmarkCircleRegular, DismissCircleRegular} from '@fluentui/react-icons';
import {PendingWorkflowStep} from '../../../../../services/types/dtos';
import {PendingApprovalDecision} from '../usePendingApprovalsPopover.ts';
import {usePendingApprovalItemStyles} from './PendingApprovalItemStyles.tsx';

interface PendingApprovalItemProps
{
    step: PendingWorkflowStep;
    comment: string;
    deciding: boolean;
    onCommentChange: (value: string) => void;
    onDecision: (decision: PendingApprovalDecision) => void;
}

const PendingApprovalItem: React.FC<PendingApprovalItemProps> = ({step, comment, deciding, onCommentChange, onDecision}) =>
{
    const styles = usePendingApprovalItemStyles();

    return (
        <AccordionItem id={`pending-approvals-accordion-item-1-${step.stepInstanceId}`}
                       value={step.stepInstanceId}>
            <AccordionHeader id={"pending-approvals-accordion-header-1"}>
                <div id={"pending-approvals-div-4"}
                     className={styles.accordionHeader}>
                    <Text id={"pending-approvals-text-2"}
                          weight="semibold">{step.name || 'Exchange'}</Text>
                    <Badge id={"pending-approvals-badge-1"}
                           appearance="outline"
                           color="warning">Pending</Badge>
                </div>
            </AccordionHeader>
            <AccordionPanel id={"pending-approvals-accordion-panel-1"}>
                <div id={"pending-approvals-div-5"}
                     className={styles.panelContent}>
                    {step.requestedByName && (
                        <Text id={"pending-approvals-text-3"}
                              size={200}>
                            Requested by {step.requestedByName}
                            {step.requestedByEmail ? ` (${step.requestedByEmail})` : ''}
                        </Text>
                    )}
                    {step.groupName && (
                        <Text id={"pending-approvals-text-4"}
                              size={200}>Group: {step.groupName}</Text>
                    )}
                    <Textarea
                        id={`pending-approval-comment-${step.stepInstanceId}`}
                        className={styles.commentField}
                        placeholder="Optional comment..."
                        size="small"
                        value={comment}
                        onChange={(_e, d) => onCommentChange(d.value)}
                    />
                    <div id={"pending-approvals-div-6"}
                         className={styles.actions}>
                        <Button
                            id={`pending-approval-approve-btn-${step.stepInstanceId}`}
                            appearance="primary"
                            size="small"
                            shape="circular"
                            icon={<CheckmarkCircleRegular/>}
                            disabled={deciding}
                            onClick={() => onDecision('APPROVE')}
                        >
                            {deciding ? (
                                <Spinner id={"pending-approvals-spinner-1"}
                                         size="tiny"/>
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
                            disabled={deciding}
                            onClick={() => onDecision('REJECT')}
                        >
                            Reject
                        </Button>
                    </div>
                </div>
            </AccordionPanel>
        </AccordionItem>
    );
};

export default PendingApprovalItem;
