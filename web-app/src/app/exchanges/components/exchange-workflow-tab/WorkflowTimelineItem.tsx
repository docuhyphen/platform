import {AccordionHeader, AccordionItem, AccordionPanel, Badge, Text } from "@fluentui/react-components";
import {
    ArrowForwardFilled,
    CheckmarkCircleFilled,
    CircleRegular,
    DismissCircleFilled,
    RecordFilled,
    WarningFilled,
} from "@fluentui/react-icons";
import { WorkflowStepInstanceDto } from "../../../models/models.tsx";
import { useExchangeWorkflowTabStyles } from "./ExchangeWorkflowTabStyles.tsx";
import {
    DECISION_LABELS,
    STEP_STATUS_LABELS,
    STEP_TYPE_LABELS,
} from "../../../settings/workflows-tab/workflowUtils.ts";

const STEP_STATUS_COLORS: Record<string, "success" | "warning" | "danger" | "informative" | "subtle"> = {
    APPROVED: "success",
    COMPLETED: "success",
    PENDING: "informative",
    REJECTED: "danger",
    ESCALATED: "warning",
    SKIPPED: "subtle",
};

const formatDateTime = (iso?: string) =>
    iso ? new Date(iso).toLocaleString(undefined, { dateStyle: "short", timeStyle: "short" }) : "";

const formatStepStatus = (status: string) =>
{
    const label = (STEP_STATUS_LABELS[status] ?? status).toLowerCase();
    return label.charAt(0).toUpperCase() + label.slice(1);
};

const formatEpoch = (ms: number) =>
    new Date(ms).toLocaleString(undefined, { dateStyle: "short", timeStyle: "short" });

const StepIcon = ({ step, isActivePending }: { step: WorkflowStepInstanceDto; isActivePending: boolean }) =>
{
    const styles = useExchangeWorkflowTabStyles();
    if (step.status === "COMPLETED" || step.status === "APPROVED")
        return <CheckmarkCircleFilled className={styles.stepIconSuccess} />;
    if (step.status === "REJECTED")
        return <DismissCircleFilled className={styles.stepIconDanger} />;
    if (step.status === "ESCALATED")
        return <WarningFilled className={styles.stepIconWarning} />;
    if (step.status === "SKIPPED")
        return <ArrowForwardFilled className={styles.stepIconNeutral} />;
    if (step.status === "PENDING" && isActivePending)
        return <RecordFilled className={styles.stepIconBrand} />;
    // PENDING future step
    return <CircleRegular className={styles.stepIconNeutral} />;
};

const principalLabel = (displayName?: string, email?: string, fallback?: string): string =>
{
    if (displayName && email) return `${displayName} (${email})`;
    return displayName || email || fallback || "";
};

const collapsedBadgeLabel = (step: WorkflowStepInstanceDto, isActivePending: boolean): string =>
{
    if (isActivePending) return "Waiting";
    if (step.decisions.some(d => d.decision === "APPROVE")) return "Approved";
    if (step.decisions.some(d => d.decision === "REJECT")) return "Rejected";
    return formatStepStatus(step.status);
};

const collapsedSummaryText = (step: WorkflowStepInstanceDto, isActivePending: boolean): string =>
{
    if (isActivePending) return "";
    const latestApprove = step.decisions.find(d => d.decision === "APPROVE");
    if (latestApprove)
        return `by ${principalLabel(latestApprove.displayName, '', latestApprove.principalId)}`;
    const latestReject = step.decisions.find(d => d.decision === "REJECT");
    if (latestReject)
        return `by ${principalLabel(latestReject.displayName, '', latestReject.principalId)} · ${formatEpoch(latestReject.atEpochMillis)}`;
    return "";
};

interface Props
{
    step: WorkflowStepInstanceDto;
    isActivePending: boolean;
}

const WorkflowTimelineItem = ({ step, isActivePending }: Props) =>
{
    const styles = useExchangeWorkflowTabStyles();
    const collapsedSummary = collapsedSummaryText(step, isActivePending);
    const decisionClassName = (decision: string) =>
    {
        if (decision === "APPROVE") return styles.decisionApproveText;
        if (decision === "REJECT") return styles.decisionRejectText;
        return undefined;
    };

    return (
        <AccordionItem value={step.id}>
            <AccordionHeader
                icon={<StepIcon step={step} isActivePending={isActivePending} />}
            >
                <div className={styles.stepHeaderContent}>
                    <Text
                        id={`workflow-step-title-${step.id}`}
                        size={300}
                        weight={isActivePending ? "semibold" : "regular"}
                        className={styles.stepHeaderTitle}
                    >
                        Step {step.stepIndex + 1}: {STEP_TYPE_LABELS[step.stepType] ?? step.stepType}
                    </Text>
                    <div
                        id={`workflow-step-meta-${step.id}`}
                        className={styles.stepHeaderMeta}
                    >
                        {collapsedSummary && (
                            <Text
                                id={`workflow-step-summary-${step.id}`}
                                size={200}
                                className={styles.stepHeaderMetaText}
                            >
                                {collapsedSummary}
                            </Text>
                        )}
                        <Badge
                            color={STEP_STATUS_COLORS[step.status] ?? "subtle"}
                            appearance="outline"
                            size="small"
                        >
                            {collapsedBadgeLabel(step, isActivePending)}
                        </Badge>
                    </div>
                </div>
            </AccordionHeader>
            <AccordionPanel>
                <div className={styles.stepDetail}>
                    {step.assignees.length > 0 && (
                        <div>
                            <Text size={200} className={styles.metaText} block>Assignees</Text>
                            {step.assignees.map((a, i) => (
                                <Text key={i} size={200} block>
                                    {principalLabel(a.displayName, a.email, a.id)}
                                </Text>
                            ))}
                        </div>
                    )}

                    {step.decisions.length > 0 && (
                        <div>
                            <Text size={200} className={styles.metaText} block>Decisions</Text>
                            {step.decisions.map((d, i) => (
                                <div key={i} className={styles.decisionRow}>
                                    <Text
                                        size={200}
                                        weight="semibold"
                                        className={decisionClassName(d.decision)}
                                    >
                                        {DECISION_LABELS[d.decision] ?? d.decision}
                                    </Text>
                                    <Text size={200} className={styles.metaText}>
                                        {principalLabel(d.displayName, d.email, d.principalId)}
                                        {" · "}{formatEpoch(d.atEpochMillis)}
                                    </Text>
                                    {d.reason && <Text size={200}>{d.reason}</Text>}
                                </div>
                            ))}
                        </div>
                    )}

                    {step.dueAt && (
                        <Text size={200} className={styles.metaText}>
                            Due: {formatDateTime(step.dueAt)}
                            {new Date(step.dueAt) < new Date() && step.status === "PENDING" && " (overdue)"}
                        </Text>
                    )}

                    {step.escalatedAt && (
                        <Text size={200} className={styles.metaText}>
                            Escalated: {formatDateTime(step.escalatedAt)}
                        </Text>
                    )}

                    {step.completedAt && (
                        <Text size={200} className={styles.metaText}>
                            Completed: {formatDateTime(step.completedAt)}
                        </Text>
                    )}
                </div>
            </AccordionPanel>
        </AccordionItem>
    );
};

export default WorkflowTimelineItem;
