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
        <Card id={"workflow-summary-card-card-1"}
              className={styles.summaryCard}>
            <div id={"workflow-summary-card-div-1"}
                 className={styles.summaryNameRow}>
                <Text id={"workflow-summary-card-text-1"}
                      weight="semibold"
                      size={400}>{instance.definitionName ?? "Workflow"}</Text>
                <Badge
                    id={"workflow-summary-card-badge-1"}
                    color={INSTANCE_STATUS_COLORS[instance.status] ?? "subtle"}
                    appearance="outline"
                    size="small"
                >
                    {INSTANCE_STATUS_LABELS[instance.status] ?? instance.status}
                </Badge>
            </div>

            {activeStep && (
                <div id={"workflow-summary-card-div-2"}
                     className={styles.summaryMetaRow}>
                    <Text id={"workflow-summary-card-text-2"}
                          size={200}
                          className={styles.metaText}>
                        Current step:
                    </Text>
                    <Text id={"workflow-summary-card-text-3"}
                          size={200}>
                        {STEP_TYPE_LABELS[activeStep.stepType] ?? activeStep.stepType}
                    </Text>
                </div>
            )}

            {waitingOn && (
                <div id={"workflow-summary-card-div-3"}
                     className={styles.summaryMetaRow}>
                    <Text id={"workflow-summary-card-text-4"}
                          size={200}
                          className={styles.metaText}>Waiting on:</Text>
                    <Text id={"workflow-summary-card-text-5"}
                          size={200}>{waitingOn}</Text>
                </div>
            )}

            {totalSteps > 0 && (
                <div id={"workflow-summary-card-div-4"}
                     className={styles.progressRow}>
                    <ProgressBar
                        id={"workflow-summary-card-progress-bar-1"}
                        className={styles.progressBar}
                        value={totalSteps > 0 ? stepsCompleted / totalSteps : 0}
                        color={instance.status === "REJECTED" ? "error" : undefined}
                    />
                    <Text id={"workflow-summary-card-text-6"}
                          size={200}
                          className={styles.metaText}>
                        {stepsCompleted} of {totalSteps} {totalSteps === 1 ? "Step" : "Steps"} Complete
                    </Text>
                </div>
            )}

            {isOverdue && (
                <div id={"workflow-summary-card-div-5"}
                     className={styles.slaRow}>
                    <Badge
                        id={"workflow-summary-card-badge-2"}
                        color="warning"
                        appearance="outline"
                        size="small"
                    >
                        Approval overdue by {overdueDuration}
                    </Badge>
                </div>
            )}

            {!isOverdue && activeStep?.escalatedAt && (
                <div id={"workflow-summary-card-div-6"}
                     className={styles.slaRow}>
                    <Badge
                        id={"workflow-summary-card-badge-3"}
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
