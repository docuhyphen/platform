import {useCallback, useEffect, useState} from "react";
import {
    Button,
    Input,
    MessageBar,
    MessageBarBody,
    Select,
    Spinner,
    Switch,
    Tag,
    Text,
    Textarea,
    tokens,
} from "@fluentui/react-components";
import {WorkflowDesignerState, WorkflowStepSpecDraft, WorkflowTriggerEventDto} from "../../models/models.tsx";
import {
    createWorkflowDefinition,
    getWorkflowDefinition,
    listWorkflowTriggers,
    updateWorkflowDefinition,
} from "../../../services/workflowService.ts";
import {useWorkflowDesignerStyles} from "./WorkflowDesignerStyles.tsx";
import {AddIcon, BackIcon} from "../../components/IconBundles.tsx";
import StepCard from "./StepCard.tsx";
import {formatTriggerName} from "./workflowUtils.ts";

interface Props
{
    definitionId?: string;
    onBack: () => void;
    onSaved: () => void;
}

const defaultStep = (): WorkflowStepSpecDraft => ({
    type: "APPROVAL",
    assignees: [],
    quorum: {kind: "ANY"},
    addons: [],
});

const defaultState = (): WorkflowDesignerState => ({
    name: "", summary: "", generalTags: [], triggerEvent: "", isActive: false, steps: [],
});

const WorkflowDesigner = ({definitionId, onBack, onSaved}: Props) =>
{
    const styles = useWorkflowDesignerStyles();
    const [state, setState] = useState<WorkflowDesignerState>(defaultState());
    const [triggers, setTriggers] = useState<WorkflowTriggerEventDto[]>([]);
    const [triggersError, setTriggersError] = useState<string | null>(null);
    const [loading, setLoading] = useState(false);
    const [saving, setSaving] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [tagInput, setTagInput] = useState("");

    const patch = (p: Partial<WorkflowDesignerState>) => setState(s => ({...s, ...p}));

    const hasHardcodedUUIDs = state.steps.some(s =>
        s.assignees.some(a => a.kind === "PRINCIPAL"),
    );

    const selectedTrigger = triggers.find(t => t.eventName === state.triggerEvent);
    const subjectFields = selectedTrigger?.subjectFields ?? [];

    const load = useCallback(async () =>
    {
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
                const parsed = JSON.parse(def.stepsJson || '{"steps":[]}');
                setState({
                    id: def.id,
                    name: def.name,
                    summary: def.summary ?? "",
                    generalTags: def.generalTags ?? [],
                    triggerEvent: def.triggerEvent,
                    isActive: def.isActive,
                    steps: parsed.steps ?? [],
                });
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
    }, [definitionId]);

    useEffect(() => { load(); }, [load]);

    const addTag = () =>
    {
        const tag = tagInput.trim();
        if (tag && !state.generalTags.includes(tag))
            patch({generalTags: [...state.generalTags, tag]});
        setTagInput("");
    };

    const removeTag = (tag: string) =>
        patch({generalTags: state.generalTags.filter(t => t !== tag)});

    const updateStep = (index: number, step: WorkflowStepSpecDraft) =>
        patch({steps: state.steps.map((s, i) => (i === index ? step : s))});

    const removeStep = (index: number) =>
        patch({steps: state.steps.filter((_, i) => i !== index)});

    const save = async () =>
    {
        if (!state.name.trim())
        {
            setError("Workflow name is required.");
            return;
        }
        if (!state.triggerEvent)
        {
            setError("Please select a trigger event.");
            return;
        }
        setSaving(true);
        setError(null);
        const stepsJson = JSON.stringify({steps: state.steps});
        try
        {
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
                    isActive: state.isActive, stepsJson,
                });
            }
            onSaved();
        }
        catch (e: unknown)
        {
            setError(typeof e === "string" ? e : "Failed to save workflow");
        }
        finally
        {
            setSaving(false);
        }
    };

    if (loading) return <Spinner label="Loading workflow..." size="small"/>;

    return (
        <div className={styles.container}>
            <div className={styles.topBar}>
                <Button appearance="subtle"
                        icon={<BackIcon/>}
                        shape={"circular"}
                        onClick={onBack}>
                    Back
                </Button>
                <Text size={500} weight="semibold">{definitionId ? "Edit Workflow" : "New Workflow"}</Text>
            </div>

            {error && (
                <MessageBar intent="error">
                    <MessageBarBody>{error}</MessageBarBody>
                </MessageBar>
            )}

            <div className={styles.formGrid}>
                <div className={styles.formField}>
                    <Text size={200} weight="semibold">Name *</Text>
                    <Input value={state.name} onChange={(_, d) => patch({name: d.value})} placeholder="Workflow name"/>
                </div>

                <div className={styles.formField}>
                    <Text size={200} weight="semibold">Trigger Event *</Text>
                    {triggersError ? (
                        <Text size={200} style={{color: tokens.colorStatusDangerForeground1}}>
                            {triggersError}
                        </Text>
                    ) : (
                        <Select value={state.triggerEvent}
                                onChange={(_, d) => patch({triggerEvent: d.value})}
                                disabled={!!definitionId}>
                            <option value="">When does this workflow run?</option>
                            {triggers.filter(t => t.isActive).map(t => (
                                <option key={t.eventName} value={t.eventName}>
                                    {formatTriggerName(t.eventName)}
                                </option>
                            ))}
                        </Select>
                    )}
                    {selectedTrigger?.description && (
                        <Text size={200} style={{color: "var(--colorNeutralForeground3)"}}>
                            {selectedTrigger.description}
                        </Text>
                    )}
                </div>

                <div className={`${styles.formField} ${styles.fullWidth}`}>
                    <Text size={200} weight="semibold">Summary</Text>
                    <Textarea
                        value={state.summary}
                        onChange={(_, d) => patch({summary: d.value})}
                        placeholder="Brief description of what this workflow does"
                        rows={2}
                    />
                </div>

                <div className={`${styles.formField} ${styles.fullWidth}`}>
                    <Text size={200} weight="semibold">Tags</Text>
                    <div className={styles.tagInput}>
                        {state.generalTags.map(tag => (
                            <Tag key={tag} size="small" dismissible
                                 onClick={() => removeTag(tag)}>{tag}</Tag>
                        ))}
                        <Input
                            size="small"
                            appearance="underline"
                            placeholder="Add tag, press Enter"
                            value={tagInput}
                            onChange={(_, d) => setTagInput(d.value)}
                            onKeyDown={e => { if (e.key === "Enter") { e.preventDefault(); addTag(); } }}
                            style={{border: "none", flexGrow: 1, minWidth: "8rem"}}
                        />
                    </div>
                </div>

                <div className={styles.formField}>
                    <Switch
                        label="Active"
                        checked={state.isActive}
                        onChange={(_, d) => patch({isActive: d.checked})}
                    />
                </div>
            </div>

            <div className={styles.stepList}>
                <div className={styles.stepListHeader}>
                    <Text weight="semibold">Steps ({state.steps.length})</Text>
                    <Button size="small"
                            appearance="outline"
                            shape={"circular"}
                            icon={<AddIcon/>}
                            onClick={() => patch({steps: [...state.steps, defaultStep()]})}>
                        Add Step
                    </Button>
                </div>

                {state.steps.map((step, i) => (
                    <StepCard
                        key={i}
                        index={i}
                        step={step}
                        stepCount={state.steps.length}
                        onChange={s => updateStep(i, s)}
                        onRemove={() => removeStep(i)}
                        triggers={triggers}
                        subjectFields={subjectFields}
                    />
                ))}

                {state.steps.length === 0 && (
                    <Text style={{color: "var(--colorNeutralForeground3)"}}>
                        No steps yet. Add a step to define the workflow logic.
                    </Text>
                )}
            </div>

            <div className={styles.saveBar}>
                {hasHardcodedUUIDs && (
                    <Text className={styles.portabilityWarning} size={200}>
                        Warning: one or more steps use hardcoded Principal UUIDs. These are not portable across organizations.
                    </Text>
                )}
                <Button appearance="secondary"
                        shape={"circular"}
                        onClick={onBack}>
                    Cancel
                </Button>
                <Button appearance="primary"
                        shape={"circular"}
                        onClick={save}
                        disabled={saving}>
                    {saving ? "Saving..." : "Save Workflow"}
                </Button>
            </div>
        </div>
    );
};

export default WorkflowDesigner;




