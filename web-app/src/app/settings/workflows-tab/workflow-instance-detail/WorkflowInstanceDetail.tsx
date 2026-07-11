import {useEffect, useState} from "react";
import {
    Badge,
    DrawerBody,
    DrawerHeader,
    DrawerHeaderTitle,
    OverlayDrawer,
    Spinner,
    Text, tokens,
} from "@fluentui/react-components";
import {WorkflowInstanceDetailDto, WorkflowStepInstanceDto} from "../../../models/models.tsx";
import {getWorkflowInstanceDetail} from "../../../../services/workflowService.ts";
import {useWorkflowInstanceDetailStyles} from "./WorkflowInstanceDetailStyles.tsx";
import {DECISION_LABELS, INSTANCE_STATUS_LABELS, PRINCIPAL_KIND_LABELS, STEP_STATUS_LABELS, STEP_TYPE_LABELS} from "../workflowUtils.ts";

const STEP_STATUS_COLORS: Record<string, "success" | "warning" | "danger" | "informative" | "subtle"> = {
    APPROVED: "success",
    COMPLETED: "success",
    PENDING: "informative",
    REJECTED: "danger",
    ESCALATED: "warning",
    SKIPPED: "subtle",
};

const DECISION_COLORS: Record<string, string> = {
    APPROVE: tokens.colorStatusSuccessForeground1,
    REJECT: tokens.colorStatusDangerForeground1,
};

const formatDateTime = (iso?: string) =>
    iso ? new Date(iso).toLocaleString(undefined, {dateStyle: "short", timeStyle: "short"}) : "";

const formatEpoch = (ms: number) => new Date(ms).toLocaleString(undefined, {dateStyle: "short", timeStyle: "short"});

interface StepTimelineItemProps
{
    step: WorkflowStepInstanceDto;
    isCurrent: boolean;
}

const StepTimelineItem = ({step, isCurrent}: StepTimelineItemProps) =>
{
    const styles = useWorkflowInstanceDetailStyles();
    return (
        <div className={`${styles.stepCard} ${isCurrent ? styles.currentStepHighlight : ""}`}>
            <div className={styles.stepHeader}>
                <Text weight="semibold" size={300}>Step {step.stepIndex + 1}: {STEP_TYPE_LABELS[step.stepType] ?? step.stepType}</Text>
                <Badge color={STEP_STATUS_COLORS[step.status] ?? "subtle"} appearance="filled" size="small">
                    {STEP_STATUS_LABELS[step.status] ?? step.status}
                </Badge>
            </div>

            {step.dueAt && (
                <Text className={styles.metaText} size={200} block>
                    Due: {formatDateTime(step.dueAt)}
                    {step.escalatedAt && " (escalated)"}
                </Text>
            )}

            {step.completedAt && (
                <Text className={styles.metaText} size={200} block>
                    Completed: {formatDateTime(step.completedAt)}
                </Text>
            )}

            {step.assignees.length > 0 && (
                <Text className={styles.metaText} size={200} block>
                    Assignees: {step.assignees.map(a => `${PRINCIPAL_KIND_LABELS[a.kind] ?? a.kind}`).join(", ")}
                </Text>
            )}

            {step.decisions.length > 0 && (
                <div className={styles.decisionList}>
                    {step.decisions.map((d, i) => (
                        <div key={i} className={styles.decisionRow}>
                            <Text size={200} style={{color: DECISION_COLORS[d.decision] ?? "inherit"}}
                                  weight="semibold">
                                {DECISION_LABELS[d.decision] ?? d.decision}
                            </Text>
                            <Text className={styles.metaText} size={200}>
                                {PRINCIPAL_KIND_LABELS[d.principalKind] ?? d.principalKind}
                                &nbsp;&middot;&nbsp;{formatEpoch(d.atEpochMillis)}
                            </Text>
                            {d.reason && <Text size={200}>{d.reason}</Text>}
                        </div>
                    ))}
                </div>
            )}
        </div>
    );
};

interface Props
{
    instanceId: string | null;
    onDismiss: () => void;
}

const WorkflowInstanceDetail = ({instanceId, onDismiss}: Props) =>
{
    const styles = useWorkflowInstanceDetailStyles();
    const [detail, setDetail] = useState<WorkflowInstanceDetailDto | null>(null);
    const [loading, setLoading] = useState(false);

    useEffect(() =>
    {
        if (!instanceId) return;
        setLoading(true);
        setDetail(null);
        getWorkflowInstanceDetail(instanceId)
            .then(setDetail)
            .catch(() => { /* ignore */ })
            .finally(() => setLoading(false));
    }, [instanceId]);

    return (
        <OverlayDrawer open={!!instanceId} onOpenChange={(_, {open}) => !open && onDismiss()} position="end">
            <DrawerHeader>
                <DrawerHeaderTitle>Workflow Instance</DrawerHeaderTitle>
            </DrawerHeader>
            <DrawerBody>
                {loading && <Spinner size="small" label="Loading..."/>}
                {detail && !loading && (
                    <>
                        <Text weight="semibold" size={400} block>{detail.definitionName}</Text>
                        {detail.exchangeName && (
                            <Text
                                size={200}
                                block
                                className={styles.exchangeName}
                            >
                                Exchange: {detail.exchangeName}
                            </Text>
                        )}
                        <Text
                            size={200}
                            block
                            className={styles.instanceStatus}
                        >
                            Status: {INSTANCE_STATUS_LABELS[detail.status] ?? detail.status}
                        </Text>
                        <div className={styles.stepTimeline}>
                            {detail.steps.map(step => (
                                <StepTimelineItem
                                    key={step.id}
                                    step={step}
                                    isCurrent={step.stepIndex === detail.currentStepIndex && detail.status === "RUNNING"}
                                />
                            ))}
                        </div>
                    </>
                )}
            </DrawerBody>
        </OverlayDrawer>
    );
};

export default WorkflowInstanceDetail;


