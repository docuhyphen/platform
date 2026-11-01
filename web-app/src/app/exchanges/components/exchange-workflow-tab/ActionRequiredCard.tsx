import { useState } from "react";
import { Button, Spinner, Text, Textarea } from "@fluentui/react-components";
import { WorkflowInstanceDetailDto } from "../../../models/models.tsx";
import { PendingWorkflowStep } from "../../../../services/types/dtos.ts";
import { recordWorkflowDecision } from "../../../../services/workflowApi.ts";
import { STEP_TYPE_LABELS } from "../../../settings/workflows-tab/workflowUtils.ts";
import { useExchangeWorkflowTabStyles } from "./ExchangeWorkflowTabStyles.tsx";

interface Props
{
    instance: WorkflowInstanceDetailDto;
    pendingStep: PendingWorkflowStep;
    onDecisionMade: () => void;
}

const ActionRequiredCard = ({ instance, pendingStep, onDecisionMade }: Props) =>
{
    const styles = useExchangeWorkflowTabStyles();
    const [submitting, setSubmitting] = useState(false);
    const [showRejectForm, setShowRejectForm] = useState(false);
    const [rejectReason, setRejectReason] = useState("");

    const activeStep = instance.steps.find(s => s.id === pendingStep.stepInstanceId);

    const handleApprove = async () =>
    {
        setSubmitting(true);
        try
        {
            await recordWorkflowDecision(pendingStep.stepInstanceId, { decision: "APPROVE" });
            onDecisionMade();
        }
        catch
        {
        }
        finally
        {
            setSubmitting(false);
        }
    };

    const handleReject = async () =>
    {
        setSubmitting(true);
        try
        {
            await recordWorkflowDecision(pendingStep.stepInstanceId, {
                decision: "REJECT",
                reason: rejectReason.trim() || undefined,
            });
            onDecisionMade();
        }
        catch
        {
        }
        finally
        {
            setSubmitting(false);
        }
    };

    return (
        <div id={"action-required-card-div-1"}
             className={styles.actionCard}>
            <Text id={"action-required-card-text-1"}
                  weight="semibold"
                  size={300}>Action Required</Text>
            {activeStep && (
                <Text id={"action-required-card-text-2"}
                      size={200}>
                    {STEP_TYPE_LABELS[activeStep.stepType] ?? activeStep.stepType}
                </Text>
            )}

            {!showRejectForm && (
                <div id={"action-required-card-div-2"}
                     className={styles.actionButtons}>
                    <Button
                        id={"action-required-approve-btn"}
                        appearance="primary"
                        shape={"circular"}
                        disabled={submitting}
                        icon={submitting ? <Spinner id={"action-required-card-spinner-1"}
                                                    size="tiny" /> : undefined}
                        onClick={handleApprove}
                    >
                        Approve
                    </Button>
                    <Button
                        id={"action-required-reject-btn"}
                        appearance="secondary"
                        shape={"circular"}
                        disabled={submitting}
                        onClick={() => setShowRejectForm(true)}
                    >
                        Reject
                    </Button>
                </div>
            )}

            {showRejectForm && (
                <div id={"action-required-card-div-3"}
                     className={styles.rejectForm}>
                    <Textarea
                        id={"textarea-action-required-reject-reason"}
                        placeholder="Reason for rejection (optional)"
                        value={rejectReason}
                        onChange={(_, d) => setRejectReason(d.value)}
                        disabled={submitting}
                        resize="vertical"
                    />
                    <div id={"action-required-card-div-4"}
                         className={styles.rejectActions}>
                        <Button
                            id={"action-required-confirm-reject-btn"}
                            appearance="primary"
                            shape={"circular"}
                            disabled={submitting}
                            icon={submitting ? <Spinner id={"action-required-card-spinner-2"}
                                                        size="tiny" /> : undefined}
                            onClick={handleReject}
                        >
                            Confirm Reject
                        </Button>
                        <Button
                            id={"action-required-cancel-reject-btn"}
                            appearance="subtle"
                            shape={"circular"}
                            disabled={submitting}
                            onClick={() => { setShowRejectForm(false); setRejectReason(""); }}
                        >
                            Cancel
                        </Button>
                    </div>
                </div>
            )}
        </div>
    );
};

export default ActionRequiredCard;
