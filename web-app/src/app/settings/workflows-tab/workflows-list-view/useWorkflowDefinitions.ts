import {useCallback, useEffect, useState} from "react";
import {WorkflowDefinitionSummaryDto} from "../../../models/models.tsx";
import {
    listWorkflowDefinitions,
    patchWorkflowDefinitionPublished,
    patchWorkflowDefinitionStatus,
} from "../../../../services/workflowService.ts";
import {WorkflowListTab} from "./workflowListTypes.ts";

export const useWorkflowDefinitions = (activeTab: WorkflowListTab) =>
{
    const [definitions, setDefinitions] = useState<WorkflowDefinitionSummaryDto[]>([]);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<string | null>(null);

    const load = useCallback(async (tab: WorkflowListTab) =>
    {
        setLoading(true);
        setError(null);
        try
        {
            setDefinitions(await listWorkflowDefinitions({scope: tab}));
        }
        catch (e: unknown)
        {
            setError(typeof e === "string" ? e : "Failed to load workflows");
        }
        finally
        {
            setLoading(false);
        }
    }, []);

    useEffect(() =>
    {
        load(activeTab);
    }, [load, activeTab]);

    const reload = () => load(activeTab);

    const toggleActive = async (def: WorkflowDefinitionSummaryDto) =>
    {
        try
        {
            await patchWorkflowDefinitionStatus(def.id, {isActive: !def.isActive});
            await load(activeTab);
        }
        catch
        {
            return;
        }
    };

    const togglePublished = async (def: WorkflowDefinitionSummaryDto) =>
    {
        try
        {
            await patchWorkflowDefinitionPublished(def.id, {isPublished: !def.isPublished});
            await load(activeTab);
        }
        catch
        {
            return;
        }
    };

    return {definitions, loading, error, reload, toggleActive, togglePublished};
};
