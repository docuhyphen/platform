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
            // errors are non-fatal here; the parent refetch will show current state
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
            // errors are non-fatal here
        }
        finally
        {
            setSubmitting(false);
        }
    };

    return (
        <div className={styles.actionCard}>
            <Text weight="semibold" size={300}>Action Required</Text>
            {activeStep && (
                <Text size={200}>
                    {STEP_TYPE_LABELS[activeStep.stepType] ?? activeStep.stepType}
                </Text>
            )}

            {!showRejectForm && (
                <div className={styles.actionButtons}>
                    <Button
                        id={"action-required-approve-btn"}
                        appearance="primary"
                        shape={"circular"}
                        disabled={submitting}
                        icon={submitting ? <Spinner size="tiny" /> : undefined}
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
                <div className={styles.rejectForm}>
                    <Textarea
                        id={"textarea-action-required-reject-reason"}
                        placeholder="Reason for rejection (optional)"
                        value={rejectReason}
                        onChange={(_, d) => setRejectReason(d.value)}
                        disabled={submitting}
                        resize="vertical"
                    />
                    <div className={styles.rejectActions}>
                        <Button
                            id={"action-required-confirm-reject-btn"}
                            appearance="primary"
                            shape={"circular"}
                            disabled={submitting}
                            icon={submitting ? <Spinner size="tiny" /> : undefined}
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
