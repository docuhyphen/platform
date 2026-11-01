import {Spinner, Text} from "@fluentui/react-components";
import {useWorkflowInstanceDashboardStyles} from "./WorkflowInstanceDashboardStyles.tsx";
import WorkflowsPagination from "../workflows-pagination/WorkflowsPagination.tsx";
import WorkflowInstanceFilterBar from "./workflow-instance-filter-bar/WorkflowInstanceFilterBar.tsx";
import WorkflowInstanceRow from "./workflow-instance-row/WorkflowInstanceRow.tsx";
import {useWorkflowInstances} from "./useWorkflowInstances.ts";
import {INSTANCE_PAGE_SIZE} from "./workflowInstancePresentation.ts";

interface Props
{
    onSelectInstance?: (id: string) => void;
}

const WorkflowInstanceDashboard = ({onSelectInstance}: Props) =>
{
    const styles = useWorkflowInstanceDashboardStyles();
    const {instances, statusFilter, setStatusFilter, loading, page, setPage, reload} = useWorkflowInstances();

    const hasResults = instances.length > 0;
    const isFiltered = statusFilter !== "";
    const showControls = hasResults || isFiltered;

    return (
        <div id={"workflow-instance-dashboard-div-1"}
             className={styles.container}>
            {showControls && (
                <WorkflowInstanceFilterBar
                    statusFilter={statusFilter}
                    onStatusFilterChange={setStatusFilter}
                    onRefresh={reload}
                />
            )}

            <div id={"workflow-instance-dashboard-div-3"}
                 className={styles.listContent}>
                {loading && <Spinner id={"workflow-instance-dashboard-spinner-1"}
                                     size="small"
                                     label="Loading instances..."/>}

                {!loading && instances.length === 0 && (
                    <div id={"workflow-instance-dashboard-div-4"}
                         className={styles.emptyState}>
                        <Text id={"workflow-instance-dashboard-text-2"}>{isFiltered ? "No instances match the selected filter." : "No workflow instances found."}</Text>
                    </div>
                )}

                {!loading && instances.map(inst => (
                    <WorkflowInstanceRow
                        key={inst.id}
                        instance={inst}
                        onSelect={onSelectInstance}
                    />
                ))}
            </div>

            {!loading && hasResults && (
                <WorkflowsPagination
                    currentPage={page}
                    firstItem={page * INSTANCE_PAGE_SIZE + 1}
                    lastItem={page * INSTANCE_PAGE_SIZE + instances.length}
                    itemLabel={"instances"}
                    hasNextPage={instances.length === INSTANCE_PAGE_SIZE}
                    onPageChange={setPage}
                />
            )}
        </div>
    );
};

export default WorkflowInstanceDashboard;
