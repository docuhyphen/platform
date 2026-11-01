import {useCallback, useMemo, useRef, useState} from "react";
import {WorkflowDesignerState, WorkflowStepSpecDraft, WorkflowTriggerEventDto} from "../../../models/models.tsx";
import {
    createWorkflowDefinition,
    getWorkflowDefinition,
    listWorkflowTriggers,
    updateWorkflowDefinition,
} from "../../../../services/workflowService.ts";
import {appendStep, deleteStepAndRemap, replaceStep} from "./stepMutations.ts";
import {applicabilityTriggerKindOf, normalizedApplicability, savedApplicability} from "./workflowApplicability.ts";
import {buildDefinitionGraph} from "../workflow-graph/workflowDefinitionGraphAdapter.ts";
import {validateWorkflowGraph} from "../workflow-graph/workflowGraphValidation.ts";
import {defaultState, defaultStep, WorkflowScope} from "./workflowDesignerDefaults.ts";

interface Options
{
    definitionId?: string;
    scope?: WorkflowScope;
    enforcedScope?: 'APP';
    createAsTemplate: boolean;
    onSaved: () => void;
}

export const useWorkflowDefinitionEditor = ({definitionId, scope, enforcedScope, createAsTemplate, onSaved}: Options) =>
{
    const [state, setState] = useState<WorkflowDesignerState>(defaultState());
    const [triggers, setTriggers] = useState<WorkflowTriggerEventDto[]>([]);
    const [triggersError, setTriggersError] = useState<string | null>(null);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [scopeViolation, setScopeViolation] = useState(false);
    const [defScope, setDefScope] = useState<WorkflowScope | undefined>(scope);
    const initialStateRef = useRef<string | null>(null);

    const isDirty = () =>
        initialStateRef.current !== null &&
        initialStateRef.current !== JSON.stringify(state);

    const patch = (p: Partial<WorkflowDesignerState>) => setState(s => ({...s, ...p}));

    const selectedTrigger = triggers.find(t => t.eventName === state.triggerEvent);
    const triggerKind = applicabilityTriggerKindOf(selectedTrigger);
    const subjectFields = selectedTrigger?.subjectFields ?? [];

    const load = useCallback(async () =>
    {
        setScopeViolation(false);
        try
        {
            setTriggers(await listWorkflowTriggers());
        }
        catch
        {
            setTriggersError("Could not load trigger events. Check your connection and reload.");
        }

        if (definitionId)
        {
            setLoading(true);
            try
            {
                const def = await getWorkflowDefinition(definitionId);
                if (enforcedScope && def.scope !== enforcedScope)
                {
                    setError("This workflow is outside the Platform Administration scope.");
                    setScopeViolation(true);
                    return;
                }
                const parsed = JSON.parse(def.stepsJson || '{"steps":[]}');
                const loaded: WorkflowDesignerState = {
                    id: def.id,
                    name: def.name,
                    summary: def.summary ?? "",
                    generalTags: def.generalTags ?? [],
                    triggerEvent: def.triggerEvent,
                    isActive: def.isActive,
                    steps: parsed.steps ?? [],
                    applicability: normalizedApplicability(parsed.applicability),
                };
                setState(loaded);
                setDefScope(def.scope);
                initialStateRef.current = JSON.stringify(loaded);
            }
            catch (e: unknown)
            {
                setError(typeof e === "string" ? e : "Failed to load workflow");
            }
            finally
            {
                setLoading(false);
            }
        }
        else
        {
            const fresh = defaultState();
            setState(fresh);
            initialStateRef.current = JSON.stringify(fresh);
        }
    }, [definitionId, enforcedScope]);

    const addStep = () => patch({steps: appendStep(state.steps, defaultStep())});

    const updateStep = (index: number, step: WorkflowStepSpecDraft) =>
        patch({steps: replaceStep(state.steps, index, step)});

    const removeStep = (index: number) =>
        patch({steps: deleteStepAndRemap(state.steps, index)});

    const graphWarnings = useMemo(
        () => validateWorkflowGraph(buildDefinitionGraph(state)).warnings,
        [state],
    );

    const validateForSave = (): boolean =>
    {
        if (!state.name.trim())
        {
            setError("Workflow name is required.");
            return false;
        }
        if (!state.triggerEvent)
        {
            setError("Please select a trigger event.");
            return false;
        }
        setError(null);
        return true;
    };

    const performSave = async () =>
    {
        if (enforcedScope && defScope !== enforcedScope)
        {
            throw new Error("Cannot save a workflow outside the Platform Administration scope.");
        }
        const applicability = savedApplicability(state.applicability, triggerKind);
        const stepsJson = JSON.stringify({steps: state.steps, applicability});
        if (definitionId)
        {
            await updateWorkflowDefinition(definitionId, {
                name: state.name, summary: state.summary || undefined,
                generalTags: state.generalTags, isActive: state.isActive, stepsJson,
            });
        }
        else
        {
            await createWorkflowDefinition({
                name: state.name, summary: state.summary || undefined,
                triggerEvent: state.triggerEvent, generalTags: state.generalTags,
                isActive: state.isActive, stepsJson, scope: enforcedScope ?? scope,
                isTemplate: createAsTemplate,
            });
        }
        onSaved();
    };

    return {
        state,
        patch,
        triggers,
        triggersError,
        loading,
        error,
        scopeViolation,
        defScope,
        triggerKind,
        subjectFields,
        isDirty,
        load,
        addStep,
        updateStep,
        removeStep,
        graphWarnings,
        validateForSave,
        performSave,
    };
};
