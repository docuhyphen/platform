import {useState} from "react";
import {WorkflowDefinitionSummaryDto} from "../../../models/models.tsx";
import {cloneWorkflowDefinition} from "../../../../services/workflowService.ts";

export const useWorkflowClone = (onCloned: () => void) =>
{
    const [cloningDef, setCloningDef] = useState<WorkflowDefinitionSummaryDto | null>(null);
    const [cloneNameInput, setCloneNameInput] = useState("");
    const [cloning, setCloning] = useState(false);

    const openCloneDialog = (def: WorkflowDefinitionSummaryDto) =>
    {
        setCloningDef(def);
        setCloneNameInput(`${def.name} (copy)`);
    };

    const closeCloneDialog = () => setCloningDef(null);

    const confirmClone = async () =>
    {
        if (!cloningDef) return;
        setCloning(true);
        try
        {
            await cloneWorkflowDefinition(cloningDef.id, {newName: cloneNameInput.trim() || undefined});
            setCloningDef(null);
            onCloned();
        }
        catch
        {
            return;
        }
        finally
        {
            setCloning(false);
        }
    };

    return {cloningDef, cloneNameInput, setCloneNameInput, cloning, openCloneDialog, closeCloneDialog, confirmClone};
};
