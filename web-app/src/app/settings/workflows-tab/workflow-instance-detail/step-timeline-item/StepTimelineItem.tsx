import {Badge, Text} from "@fluentui/react-components";
import {WorkflowStepInstanceDto} from "../../../../models/models.tsx";
import {PRINCIPAL_KIND_LABELS, STEP_STATUS_LABELS, STEP_TYPE_LABELS} from "../../workflowUtils.ts";
import {formatDateTime, STEP_STATUS_COLORS} from "../workflowInstanceDetailFormatting.ts";
import StepDecisionList from "./step-decision-list/StepDecisionList.tsx";
import {useStepTimelineItemStyles} from "./StepTimelineItemStyles.tsx";

interface Props
{
    step: WorkflowStepInstanceDto;
    isCurrent: boolean;
}

const StepTimelineItem = ({step, isCurrent}: Props) =>
{
    const styles = useStepTimelineItemStyles();

    return (
        <div id={"workflow-instance-detail-div-1"}
             className={`${styles.stepCard} ${isCurrent ? styles.currentStepHighlight : ""}`}>
            <div id={"workflow-instance-detail-div-2"}
                 className={styles.stepHeader}>
                <Text id={"workflow-instance-detail-text-1"}
                      weight="semibold"
                      size={300}>Step {step.stepIndex + 1}: {STEP_TYPE_LABELS[step.stepType] ?? step.stepType}</Text>
                <Badge id={"workflow-instance-detail-badge-1"}
                       color={STEP_STATUS_COLORS[step.status] ?? "subtle"}
                       appearance="filled"
                       size="small">
                    {STEP_STATUS_LABELS[step.status] ?? step.status}
                </Badge>
            </div>

            {step.dueAt && (
                <Text id={"workflow-instance-detail-text-2"}
                      className={styles.metaText}
                      size={200}
                      block>
                    Due: {formatDateTime(step.dueAt)}
                    {step.escalatedAt && " (escalated)"}
                </Text>
            )}

            {step.completedAt && (
                <Text id={"workflow-instance-detail-text-3"}
                      className={styles.metaText}
                      size={200}
                      block>
                    Completed: {formatDateTime(step.completedAt)}
                </Text>
            )}

            {step.assignees.length > 0 && (
                <Text id={"workflow-instance-detail-text-4"}
                      className={styles.metaText}
                      size={200}
                      block>
                    Assignees: {step.assignees.map(a => `${PRINCIPAL_KIND_LABELS[a.kind] ?? a.kind}`).join(", ")}
                </Text>
            )}

            {step.decisions.length > 0 && (
                <StepDecisionList decisions={step.decisions}/>
            )}
        </div>
    );
};

export default StepTimelineItem;
