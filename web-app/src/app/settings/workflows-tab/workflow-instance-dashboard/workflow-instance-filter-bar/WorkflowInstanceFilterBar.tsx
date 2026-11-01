import {Button, Select, Text} from "@fluentui/react-components";
import {ArrowSyncRegular} from "@fluentui/react-icons";
import {InstanceStatus} from "../workflowInstancePresentation.ts";
import {useWorkflowInstanceFilterBarStyles} from "./WorkflowInstanceFilterBarStyles.tsx";

interface Props
{
    statusFilter: InstanceStatus;
    onStatusFilterChange: (status: InstanceStatus) => void;
    onRefresh: () => void;
}

const WorkflowInstanceFilterBar = ({statusFilter, onStatusFilterChange, onRefresh}: Props) =>
{
    const styles = useWorkflowInstanceFilterBarStyles();

    return (
        <div id={"workflow-instance-dashboard-div-2"}
             className={styles.filterBar}>
            <Text id={"workflow-instance-dashboard-text-1"}
                  weight="semibold">Filter by status:</Text>
            <Select
                id="workflow-instance-status-filter-select"
                value={statusFilter}
                onChange={(_, d) => onStatusFilterChange(d.value as InstanceStatus)}
                size="small"
                className={styles.statusFilterSelect}
            >
                <option id={"workflow-instance-dashboard-option-1"}
                        value="">All</option>
                <option id={"workflow-instance-dashboard-option-2"}
                        value="RUNNING">In Progress</option>
                <option id={"workflow-instance-dashboard-option-3"}
                        value="COMPLETED">Completed</option>
                <option id={"workflow-instance-dashboard-option-4"}
                        value="REJECTED">Rejected</option>
                <option id={"workflow-instance-dashboard-option-5"}
                        value="CANCELLED">Cancelled</option>
                <option id={"workflow-instance-dashboard-option-6"}
                        value="ESCALATED">Escalated</option>
                <option id={"workflow-instance-dashboard-option-7"}
                        value="FAILED">Failed</option>
            </Select>
            <Button
                id="workflow-instance-refresh-btn"
                size="small"
                shape={"circular"}
                icon={<ArrowSyncRegular/>}
                appearance="secondary"
                onClick={onRefresh}
            >
                Refresh
            </Button>
        </div>
    );
};

export default WorkflowInstanceFilterBar;
