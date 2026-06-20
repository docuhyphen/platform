import {useCallback, useEffect, useState} from "react";
import {Badge, Button, Select, Spinner, Text} from "@fluentui/react-components";
import {WorkflowInstanceSummaryDto} from "../../models/models.tsx";
import {listWorkflowInstances} from "../../../services/workflowService.ts";
import {useWorkflowInstanceDashboardStyles} from "./WorkflowInstanceDashboardStyles.tsx";
import {INSTANCE_STATUS_LABELS} from "./workflowUtils.ts";

type InstanceStatus = WorkflowInstanceSummaryDto["status"] | "";

const STATUS_COLORS: Record<string, "success" | "warning" | "danger" | "informative" | "subtle"> = {
    RUNNING: "informative",
    COMPLETED: "success",
    REJECTED: "danger",
    CANCELLED: "subtle",
    ESCALATED: "warning",
};

interface Props
{
    onSelectInstance?: (id: string) => void;
}

const PAGE_SIZE = 15;

const WorkflowInstanceDashboard = ({onSelectInstance}: Props) =>
{
    const styles = useWorkflowInstanceDashboardStyles();
    const [instances, setInstances] = useState<WorkflowInstanceSummaryDto[]>([]);
    const [statusFilter, setStatusFilter] = useState<InstanceStatus>("");
    const [loading, setLoading] = useState(false);
    const [page, setPage] = useState(0);

    const load = useCallback(async () =>
    {
        setLoading(true);
        try
        {
            const data = await listWorkflowInstances({
                status: statusFilter || undefined,
                page,
                pageSize: PAGE_SIZE,
            });
            setInstances(data);
        }
        catch
        { /* ignore */
        }
        finally
        {
            setLoading(false);
        }
    }, [statusFilter, page]);

    useEffect(() =>
    {
        load();
    }, [load]);

    const formatDate = (iso: string) => new Date(iso).toLocaleDateString(undefined, {
        year: "numeric", month: "short", day: "numeric",
    });

    const hasResults = instances.length > 0;
    const isFiltered = statusFilter !== "";
    const showControls = hasResults || isFiltered;

    return (
        <div className={styles.container}>
            {showControls && (
                <div className={styles.filterBar}>
                    <Text weight="semibold">Filter by status:</Text>
                    <Select
                        value={statusFilter}
                        onChange={(_, d) =>
                        {
                            setStatusFilter(d.value as InstanceStatus);
                            setPage(0);
                        }}
                        size="small"
                        style={{minWidth: "10rem"}}
                    >
                        <option value="">All</option>
                        <option value="RUNNING">In Progress</option>
                        <option value="COMPLETED">Completed</option>
                        <option value="REJECTED">Rejected</option>
                        <option value="CANCELLED">Cancelled</option>
                        <option value="ESCALATED">Escalated</option>
                    </Select>
                    <Button size="small"
                            shape={"circular"}
                            appearance="outline"
                            onClick={load}>Refresh</Button>
                </div>
            )}

            {loading && <Spinner size="small" label="Loading instances..."/>}

            {!loading && instances.length === 0 && (
                <div className={styles.emptyState}>
                    <Text>{isFiltered ? "No instances match the selected filter." : "No workflow instances found."}</Text>
                </div>
            )}

            {!loading && instances.map(inst => (
                <div
                    key={inst.id}
                    className={styles.row}
                    onClick={() => onSelectInstance?.(inst.id)}
                >
                    <div className={styles.rowInfo}>
                        <Text weight="semibold">{inst.definitionName ?? inst.definitionId}</Text>
                        {inst.exchangeName && <Text size={200} block>Exchange: {inst.exchangeName}</Text>}
                        <Text size={200} block style={{color: "inherit"}}>
                            Step {inst.currentStepIndex + 1} &middot; Started {formatDate(inst.createdAt)}
                        </Text>
                    </div>
                    <Badge color={STATUS_COLORS[inst.status] ?? "subtle"} appearance="filled" size="small">
                        {INSTANCE_STATUS_LABELS[inst.status] ?? inst.status}
                    </Badge>
                </div>
            ))}

            {!loading && hasResults && (
                <div className={styles.pagination}>
                    <Button size="small" disabled={page === 0} onClick={() => setPage(p => p - 1)}>
                        Previous
                    </Button>
                    <Text size={200}>Page {page + 1}</Text>
                    <Button size="small" disabled={instances.length < PAGE_SIZE}
                            onClick={() => setPage(p => p + 1)}>
                        Next
                    </Button>
                </div>
            )}
        </div>
    );
};

export default WorkflowInstanceDashboard;


