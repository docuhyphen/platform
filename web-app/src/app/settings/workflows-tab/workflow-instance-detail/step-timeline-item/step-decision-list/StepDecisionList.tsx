import {Text} from "@fluentui/react-components";
import {WorkflowStepInstanceDto} from "../../../../../models/models.tsx";
import {DECISION_LABELS, PRINCIPAL_KIND_LABELS} from "../../../workflowUtils.ts";
import {formatEpoch} from "../../workflowInstanceDetailFormatting.ts";
import {useStepDecisionListStyles} from "./StepDecisionListStyles.tsx";

interface Props
{
    decisions: WorkflowStepInstanceDto["decisions"];
}

const StepDecisionList = ({decisions}: Props) =>
{
    const styles = useStepDecisionListStyles();
    const decisionClassName = (decision: string) =>
    {
        if (decision === "APPROVE") return styles.decisionApproveText;
        if (decision === "REJECT") return styles.decisionRejectText;
        return undefined;
    };

    return (
        <div id={"workflow-instance-detail-div-3"}
             className={styles.decisionList}>
            {decisions.map((d, i) => (
                <div id={`workflow-instance-detail-div-4-${i}`}
                     key={i}
                     className={styles.decisionRow}>
                    <Text id={"workflow-instance-detail-text-5"}
                          size={200}
                          className={decisionClassName(d.decision)}
                          weight="semibold">
                        {DECISION_LABELS[d.decision] ?? d.decision}
                    </Text>
                    <Text id={"workflow-instance-detail-text-6"}
                          className={styles.metaText}
                          size={200}>
                        {PRINCIPAL_KIND_LABELS[d.principalKind] ?? d.principalKind}
                        &nbsp;&middot;&nbsp;{formatEpoch(d.atEpochMillis)}
                    </Text>
                    {d.reason && <Text id={"workflow-instance-detail-text-7"}
                                       size={200}>{d.reason}</Text>}
                </div>
            ))}
        </div>
    );
};

export default StepDecisionList;
