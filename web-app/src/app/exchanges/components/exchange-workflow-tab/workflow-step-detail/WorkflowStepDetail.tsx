import {Text} from "@fluentui/react-components";
import {WorkflowStepInstanceDto} from "../../../../models/models.tsx";
import {DECISION_LABELS} from "../../../../settings/workflows-tab/workflowUtils.ts";
import {useExchangeWorkflowTabStyles} from "../ExchangeWorkflowTabStyles.tsx";
import {formatDateTime, formatEpoch, principalLabel} from "../workflowTimelineItemFormat.ts";
import {useWorkflowStepDetailStyles} from "./WorkflowStepDetailStyles.tsx";

interface WorkflowStepDetailProps
{
    step: WorkflowStepInstanceDto;
}

const WorkflowStepDetail = ({step}: WorkflowStepDetailProps) =>
{
    const styles = useWorkflowStepDetailStyles();
    const tabStyles = useExchangeWorkflowTabStyles();
    const decisionClassName = (decision: string) =>
    {
        if (decision === "APPROVE") return styles.decisionApproveText;
        if (decision === "REJECT") return styles.decisionRejectText;
        return undefined;
    };

    return (
        <div id={"workflow-timeline-item-div-2"}
             className={styles.stepDetail}>
            {step.assignees.length > 0 && (
                <div id={"workflow-timeline-item-div-3"}>
                    <Text id={"workflow-timeline-item-text-1"}
                          size={200}
                          className={tabStyles.metaText}
                          block>Assignees</Text>
                    {step.assignees.map((a, i) => (
                        <Text id={`workflow-timeline-item-text-2-${i}`}
                              key={i}
                              size={200}
                              block>
                            {principalLabel(a.displayName, a.email, a.id)}
                        </Text>
                    ))}
                </div>
            )}

            {step.decisions.length > 0 && (
                <div id={"workflow-timeline-item-div-4"}>
                    <Text id={"workflow-timeline-item-text-3"}
                          size={200}
                          className={tabStyles.metaText}
                          block>Decisions</Text>
                    {step.decisions.map((d, i) => (
                        <div id={`workflow-timeline-item-div-5-${i}`}
                             key={i}
                             className={styles.decisionRow}>
                            <Text id={"workflow-timeline-item-text-4"}
                                  size={200}
                                  weight="semibold"
                                  className={decisionClassName(d.decision)}>
                                {DECISION_LABELS[d.decision] ?? d.decision}
                            </Text>
                            <Text id={"workflow-timeline-item-text-5"}
                                  size={200}
                                  className={tabStyles.metaText}>
                                {principalLabel(d.displayName, d.email, d.principalId)}
                                {" · "}{formatEpoch(d.atEpochMillis)}
                            </Text>
                            {d.reason && <Text id={"workflow-timeline-item-text-6"}
                                               size={200}>{d.reason}</Text>}
                        </div>
                    ))}
                </div>
            )}

            {step.dueAt && (
                <Text id={"workflow-timeline-item-text-7"}
                      size={200}
                      className={tabStyles.metaText}>
                    Due: {formatDateTime(step.dueAt)}
                    {new Date(step.dueAt) < new Date() && step.status === "PENDING" && " (overdue)"}
                </Text>
            )}

            {step.escalatedAt && (
                <Text id={"workflow-timeline-item-text-8"}
                      size={200}
                      className={tabStyles.metaText}>
                    Escalated: {formatDateTime(step.escalatedAt)}
                </Text>
            )}

            {step.completedAt && (
                <Text id={"workflow-timeline-item-text-9"}
                      size={200}
                      className={tabStyles.metaText}>
                    Completed: {formatDateTime(step.completedAt)}
                </Text>
            )}
        </div>
    );
};

export default WorkflowStepDetail;
