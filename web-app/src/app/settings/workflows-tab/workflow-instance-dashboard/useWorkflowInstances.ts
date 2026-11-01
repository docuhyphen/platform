import {useCallback, useEffect, useState} from "react";
import {WorkflowInstanceSummaryDto} from "../../../models/models.tsx";
import {listWorkflowInstances} from "../../../../services/workflowService.ts";
import {INSTANCE_PAGE_SIZE, InstanceStatus} from "./workflowInstancePresentation.ts";

export const useWorkflowInstances = () =>
{
    const [instances, setInstances] = useState<WorkflowInstanceSummaryDto[]>([]);
    const [statusFilter, setStatusFilterState] = useState<InstanceStatus>("");
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
                pageSize: INSTANCE_PAGE_SIZE,
            });
            setInstances(data);
        }
        catch
        {
            return;
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

    const setStatusFilter = (status: InstanceStatus) =>
    {
        setStatusFilterState(status);
        setPage(0);
    };

    return {instances, statusFilter, setStatusFilter, loading, page, setPage, reload: load};
};
