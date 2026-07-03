import {useCallback, useEffect, useRef, useState} from "react";
import {
    Button,
    Dialog,
    DialogActions,
    DialogBody,
    DialogContent,
    DialogSurface,
    DialogTitle,
    Divider,
    Input,
    MessageBar,
    MessageBarBody,
    Select,
    Spinner,
    Switch,
    Tag,
    Text,
    Textarea,
    Tooltip,
} from "@fluentui/react-components";
import {WorkflowDesignerState, WorkflowStepSpecDraft, WorkflowTriggerEventDto} from "../../../models/models.tsx";
import {
    createWorkflowDefinition,
    getWorkflowDefinition,
    listWorkflowTriggers,
    updateWorkflowDefinition,
} from "../../../../services/workflowService.ts";
import {useWorkflowDesignerStyles} from "./WorkflowDesignerStyles.tsx";
import {AddIcon, BackIcon, InfoIcon} from "../../../components/IconBundles.tsx";
import {useHelpSidebar} from "../../../../context/HelpSidebarContext.tsx";
import StepCard from "../step-card/StepCard.tsx";
import {formatTriggerName} from "../workflowUtils.ts";
import SaveWorkflowDialog from "../save-workflow-dialog/SaveWorkflowDialog.tsx";
import ApplicabilityEditor from "./applicability-editor/ApplicabilityEditor.tsx";

interface Props
{
    definitionId?: string;
    scope?: 'PERSONAL' | 'ORG' | 'APP';
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

const WorkflowDesigner = ({definitionId, scope, onBack, onSaved}: Props) =>
{
    const styles = useWorkflowDesignerStyles();
    const {openHelpArticle} = useHelpSidebar();
    const [state, setState] = useState<WorkflowDesignerState>(defaultState());
    const [triggers, setTriggers] = useState<WorkflowTriggerEventDto[]>([]);
    const [triggersError, setTriggersError] = useState<string | null>(null);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [tagInput, setTagInput] = useState("");
    const [showDiscardDialog, setShowDiscardDialog] = useState(false);
    const [showSaveDialog, setShowSaveDialog] = useState(false);
    const [defScope, setDefScope] = useState<'PERSONAL' | 'ORG' | 'APP' | undefined>(scope);
    const initialStateRef = useRef<string | null>(null);

    const isDirty = () =>
        initialStateRef.current !== null &&
        initialStateRef.current !== JSON.stringify(state);

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
                const loaded: WorkflowDesignerState = {
                    id: def.id,
                    name: def.name,
                    summary: def.summary ?? "",
                    generalTags: def.generalTags ?? [],
                    triggerEvent: def.triggerEvent,
                    isActive: def.isActive,
                    steps: parsed.steps ?? [],
                    applicability: parsed.applicability,
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

    const requestSave = () =>
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
        setError(null);
        setShowSaveDialog(true);
    };

    const performSave = async () =>
    {
        const applicability = state.applicability && state.applicability.fieldConditions.length > 0
            ? state.applicability
            : undefined;
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
                isActive: state.isActive, stepsJson, scope,
            });
        }
        onSaved();
    };

    if (loading) return <Spinner label="Loading workflow..." size="small"/>;

    return (
        <div className={styles.container}>
            <div className={styles.topBar}>
                <Button
                    id="workflow-designer-back-btn"
                    appearance="subtle"
                    icon={<BackIcon/>}
                    shape={"circular"}
                    onClick={() => isDirty() ? setShowDiscardDialog(true) : onBack()}
                >
                    Back
                </Button>
                <Text size={500} weight="semibold">{definitionId ? "Edit Workflow" : "New Workflow"}</Text>
                <Tooltip content="Workflow help" relationship="label">
                    <Button
                        id="workflow-designer-help-btn"
                        appearance="subtle"
                        shape="circular"
                        size="small"
                        icon={<InfoIcon/>}
                        className={styles.helpButton}
                        onClick={() => openHelpArticle("building-a-workflow")}
                        aria-label="Open workflow help"
                    />
                </Tooltip>
            </div>

            {error && (
                <MessageBar intent="error">
                    <MessageBarBody>{error}</MessageBarBody>
                </MessageBar>
            )}

            <div className={styles.formGrid}>
                <div className={styles.formField}>
                    <Text size={200} weight="semibold">Name *</Text>
                    <Input
                        id="workflow-designer-name-input"
                        value={state.name}
                        onChange={(_, d) => patch({name: d.value})}
                        placeholder="Workflow name"
                    />
                </div>

                <div className={styles.formField}>
                    <Text size={200} weight="semibold">Trigger Event *</Text>
                    {triggersError ? (
                        <Text
                            size={200}
                            className={styles.triggerError}
                        >
                            {triggersError}
                        </Text>
                    ) : (
                        <Select
                            id="workflow-designer-trigger-select"
                            value={state.triggerEvent}
                            onChange={(_, d) => patch({triggerEvent: d.value})}
                            disabled={!!definitionId}
                        >
                            <option value="">When does this workflow run?</option>
                            {triggers.filter(t => t.isActive).map(t => (
                                <option key={t.eventName} value={t.eventName}>
                                    {formatTriggerName(t.eventName)}
                                </option>
                            ))}
                        </Select>
                    )}
                    {selectedTrigger?.description && (
                        <Text
                            size={200}
                            className={styles.triggerDescription}
                        >
                            {selectedTrigger.description}
                        </Text>
                    )}
                </div>

                <div className={`${styles.formField} ${styles.fullWidth}`}>
                    <Text size={200} weight="semibold">Summary</Text>
                    <Textarea
                        id="workflow-designer-summary-textarea"
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
                            <Tag key={tag}
                                 size="small"
                                 shape={"circular"}
                                 dismissible
                                 onClick={() => removeTag(tag)}>{tag}</Tag>
                        ))}
                        <Input
                            id="workflow-designer-tag-input"
                            size="small"
                            appearance="underline"
                            placeholder="Add tag, press Enter"
                            value={tagInput}
                            onChange={(_, d) => setTagInput(d.value)}
                            onKeyDown={e => { if (e.key === "Enter") { e.preventDefault(); addTag(); } }}
                            className={styles.tagInputField}
                        />
                        <Button
                            id="workflow-designer-add-tag-btn"
                            shape="circular"
                            appearance="subtle"
                            size={"medium"}
                            icon={<AddIcon/>}
                            onClick={addTag}
                        />
                    </div>
                </div>

                <div className={styles.formField}>
                    <Switch
                        id="workflow-designer-active-switch"
                        label="Active"
                        checked={state.isActive}
                        onChange={(_, d) => patch({isActive: d.checked})}
                    />
                </div>
            </div>
            <Divider/>
            <div className={styles.stepList}>
                <div className={styles.stepListHeader}>
                    <Text weight="semibold">Steps ({state.steps.length})</Text>
                    <Button
                        id="workflow-designer-add-step-btn"
                        size="small"
                        appearance="secondary"
                        shape={"circular"}
                        icon={<AddIcon/>}
                        onClick={() => patch({steps: [...state.steps, defaultStep()]})}
                    >
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
                    <Text className={styles.noStepsText}>
                        No steps yet. Add a step to define the workflow logic.
                    </Text>
                )}
            </div>

            {defScope === "ORG" && (
                <>
                    <Divider/>
                    <div className={styles.stepList}>
                        <ApplicabilityEditor
                            applicability={state.applicability}
                            onChange={a => patch({applicability: a})}
                        />
                    </div>
                </>
            )}

            <div className={styles.saveBar}>
                {/*{hasHardcodedUUIDs && (*/}
                {/*    <Text className={styles.portabilityWarning} size={200}>*/}
                {/*        Warning: one or more steps use hardcoded Principal UUIDs. These are not portable across organizations.*/}
                {/*    </Text>*/}
                {/*)}*/}
                <Button
                    id="workflow-designer-cancel-btn"
                    appearance="secondary"
                    shape={"circular"}
                    onClick={() => isDirty() ? setShowDiscardDialog(true) : onBack()}
                >
                    Cancel
                </Button>
                <Button
                    id="workflow-designer-save-btn"
                    appearance="primary"
                    shape={"circular"}
                    onClick={requestSave}
                >
                    Save Workflow
                </Button>
            </div>

            <SaveWorkflowDialog
                open={showSaveDialog}
                onClose={() => setShowSaveDialog(false)}
                onConfirm={performSave}
                isEdit={!!definitionId}
                state={state}
                triggers={triggers}
            />

            <Dialog open={showDiscardDialog} onOpenChange={(_, d) => setShowDiscardDialog(d.open)}>
                <DialogSurface>
                    <DialogBody>
                        <DialogTitle>Discard changes?</DialogTitle>
                        <DialogContent>
                            You have unsaved changes. If you leave now they will be lost.
                        </DialogContent>
                        <DialogActions>
                            <Button
                                id="workflow-designer-keep-editing-btn"
                                appearance="secondary"
                                shape={"circular"}
                                onClick={() => setShowDiscardDialog(false)}
                            >
                                Keep editing
                            </Button>
                            <Button
                                id="workflow-designer-discard-btn"
                                appearance="primary"
                                shape={"circular"}
                                onClick={onBack}
                            >
                                Discard changes
                            </Button>
                        </DialogActions>
                    </DialogBody>
                </DialogSurface>
            </Dialog>
        </div>
    );
};

export default WorkflowDesigner;




