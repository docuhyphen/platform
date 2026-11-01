import {Badge, Text} from "@fluentui/react-components";
import {WorkflowInstanceSummaryDto} from "../../../../models/models.tsx";
import {INSTANCE_STATUS_LABELS} from "../../workflowUtils.ts";
import {formatInstanceDate, INSTANCE_STATUS_COLORS} from "../workflowInstancePresentation.ts";
import {useWorkflowInstanceRowStyles} from "./WorkflowInstanceRowStyles.tsx";

interface Props
{
    instance: WorkflowInstanceSummaryDto;
    onSelect?: (id: string) => void;
}

const WorkflowInstanceRow = ({instance, onSelect}: Props) =>
{
    const styles = useWorkflowInstanceRowStyles();

    return (
        <div
            id={`workflow-instance-dashboard-div-5-${instance.id}`}
            className={styles.row}
            onClick={() => onSelect?.(instance.id)}
        >
            <div id={"workflow-instance-dashboard-div-6"}
                 className={styles.rowInfo}>
                <Text id={"workflow-instance-dashboard-text-3"}
                      weight="semibold">{instance.definitionName ?? instance.definitionId}</Text>
                {instance.exchangeName && <Text id={"workflow-instance-dashboard-text-4"}
                                                size={200}
                                                block>Exchange: {instance.exchangeName}</Text>}
                <Text
                    id={"workflow-instance-dashboard-text-5"}
                    size={200}
                    block
                    className={styles.rowMetaInherit}
                >
                    Step {instance.currentStepIndex + 1} &middot; Started {formatInstanceDate(instance.createdAt)}
                </Text>
            </div>
            <Badge id={"workflow-instance-dashboard-badge-1"}
                   color={INSTANCE_STATUS_COLORS[instance.status] ?? "subtle"}
                   appearance="filled"
                   size="small">
                {INSTANCE_STATUS_LABELS[instance.status] ?? instance.status}
            </Badge>
        </div>
    );
};

export default WorkflowInstanceRow;
