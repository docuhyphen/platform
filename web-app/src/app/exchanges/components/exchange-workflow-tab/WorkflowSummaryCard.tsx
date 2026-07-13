import { Badge, Card, ProgressBar, Text } from "@fluentui/react-components";
import { WorkflowInstanceDetailDto } from "../../../models/models.tsx";
import { useExchangeWorkflowTabStyles } from "./ExchangeWorkflowTabStyles.tsx";
import {
    INSTANCE_STATUS_LABELS,
    STEP_TYPE_LABELS,
} from "../../../settings/workflows-tab/workflowUtils.ts";
import {formatWorkflowOverdueDuration} from "./workflowOverdueLabel.ts";

const INSTANCE_STATUS_COLORS: Record<string, "informative" | "success" | "danger" | "subtle" | "warning"> = {
    RUNNING: "informative",
    COMPLETED: "success",
    REJECTED: "danger",
    CANCELLED: "subtle",
    ESCALATED: "warning",
    FAILED: "danger",
};

const formatDateTime = (iso?: string) =>
    iso ? new Date(iso).toLocaleString(undefined, { dateStyle: "short", timeStyle: "short" }) : "";

interface Props
{
    instance: WorkflowInstanceDetailDto;
}

const WorkflowSummaryCard = ({ instance }: Props) =>
{
    const styles = useExchangeWorkflowTabStyles();

    const activeStep = instance.steps.find(
        s => s.stepIndex === instance.currentStepIndex && s.status === "PENDING",
    );

    // A step counts as resolved once it has moved past its pending state: APPROVAL steps
    // land on APPROVED, other step types on COMPLETED, and skipped steps on SKIPPED.
    const stepsCompleted = instance.steps.filter(
        s => s.status === "APPROVED" || s.status === "COMPLETED" || s.status === "SKIPPED",
    ).length;
    const totalSteps = instance.steps.length;

    const waitingOn = activeStep?.assignees
        .map(a =>
            a.displayName && a.email
                ? `${a.displayName} (${a.email})`
                : a.displayName || a.email || a.id,
        )
        .join(", ");

    const now = new Date();
    const isOverdue =
        activeStep?.dueAt &&
        new Date(activeStep.dueAt) < now &&
        activeStep.status === "PENDING";
    const overdueDuration = isOverdue
        ? formatWorkflowOverdueDuration(new Date(activeStep!.dueAt!), now)
        : "";

    return (
        <Card className={styles.summaryCard}>
            <div className={styles.summaryNameRow}>
                <Text weight="semibold" size={400}>{instance.definitionName ?? "Workflow"}</Text>
                <Badge
                    color={INSTANCE_STATUS_COLORS[instance.status] ?? "subtle"}
                    appearance="outline"
                    size="small"
                >
                    {INSTANCE_STATUS_LABELS[instance.status] ?? instance.status}
                </Badge>
            </div>

            {activeStep && (
                <div className={styles.summaryMetaRow}>
                    <Text size={200} className={styles.metaText}>
                        Current step:
                    </Text>
                    <Text size={200}>
                        {STEP_TYPE_LABELS[activeStep.stepType] ?? activeStep.stepType}
                    </Text>
                </div>
            )}

            {waitingOn && (
                <div className={styles.summaryMetaRow}>
                    <Text size={200} className={styles.metaText}>Waiting on:</Text>
                    <Text size={200}>{waitingOn}</Text>
                </div>
            )}

            {totalSteps > 0 && (
                <div className={styles.progressRow}>
                    <ProgressBar
                        className={styles.progressBar}
                        value={totalSteps > 0 ? stepsCompleted / totalSteps : 0}
                        color={instance.status === "REJECTED" ? "error" : undefined}
                    />
                    <Text size={200} className={styles.metaText}>
                        {stepsCompleted} of {totalSteps} {totalSteps === 1 ? "Step" : "Steps"} Complete
                    </Text>
                </div>
            )}

            {isOverdue && (
                <div className={styles.slaRow}>
                    <Badge
                        color="warning"
                        appearance="outline"
                        size="small"
                    >
                        Approval overdue by {overdueDuration}
                    </Badge>
                </div>
            )}

            {!isOverdue && activeStep?.escalatedAt && (
                <div className={styles.slaRow}>
                    <Badge
                        color="warning"
                        appearance="outline"
                        size="small"
                    >
                        Escalated · {formatDateTime(activeStep.escalatedAt)}
                    </Badge>
                </div>
            )}
        </Card>
    );
};

export default WorkflowSummaryCard;
