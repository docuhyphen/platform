import {lazy, Suspense, useCallback, useEffect, useRef, useState} from "react";
import {
    Button,
    Dialog,
    DialogActions,
    DialogBody,
    DialogContent,
    DialogSurface,
    DialogTitle,
    Divider,
    MessageBar,
    MessageBarBody,
    Spinner,
} from "@fluentui/react-components";
import {WorkflowDesignerState, WorkflowStepSpecDraft, WorkflowTriggerEventDto} from "../../../models/models.tsx";
import {
    createWorkflowDefinition,
    getWorkflowDefinition,
    listWorkflowTriggers,
    updateWorkflowDefinition,
} from "../../../../services/workflowService.ts";
import {useWorkflowDesignerStyles} from "./WorkflowDesignerStyles.tsx";
import {useHelpSidebar} from "../../../../context/HelpSidebarContext.tsx";
import SaveWorkflowDialog from "../save-workflow-dialog/SaveWorkflowDialog.tsx";
import ApplicabilityEditor from "./applicability-editor/ApplicabilityEditor.tsx";
import WorkflowDesignerHeader from "./workflow-designer-header/WorkflowDesignerHeader.tsx";
import WorkflowMetadataForm from "./workflow-metadata-form/WorkflowMetadataForm.tsx";
import WorkflowStepsSection from "./workflow-steps-section/WorkflowStepsSection.tsx";
import WorkflowDesignerActionBar from "./workflow-designer-action-bar/WorkflowDesignerActionBar.tsx";
import WorkflowViewSwitch, {WorkflowDesignerView} from "./workflow-view-switch/WorkflowViewSwitch.tsx";

const WorkflowDefinitionPreview = lazy(
    () => import("./workflow-definition-preview/WorkflowDefinitionPreview.tsx"),
);

interface Props
{
    definitionId?: string;
    scope?: 'PERSONAL' | 'ORG' | 'APP';
    backDestinationLabel: string;
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

const WorkflowDesigner = ({definitionId, scope, backDestinationLabel, onBack, onSaved}: Props) =>
{
    const styles = useWorkflowDesignerStyles();
    const {openHelpArticle} = useHelpSidebar();
    const [state, setState] = useState<WorkflowDesignerState>(defaultState());
    const [triggers, setTriggers] = useState<WorkflowTriggerEventDto[]>([]);
    const [triggersError, setTriggersError] = useState<string | null>(null);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [showDiscardDialog, setShowDiscardDialog] = useState(false);
    const [showSaveDialog, setShowSaveDialog] = useState(false);
    const [defScope, setDefScope] = useState<'PERSONAL' | 'ORG' | 'APP' | undefined>(scope);
    const [view, setView] = useState<WorkflowDesignerView>("form");
    const initialStateRef = useRef<string | null>(null);

    const isDirty = () =>
        initialStateRef.current !== null &&
        initialStateRef.current !== JSON.stringify(state);

    const patch = (p: Partial<WorkflowDesignerState>) => setState(s => ({...s, ...p}));

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
        setView("form");
    }, [definitionId]);

    useEffect(() => { load(); }, [load]);

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

    const handleBack = () => isDirty() ? setShowDiscardDialog(true) : onBack();

    if (loading) return <Spinner label="Loading workflow..." size="small"/>;

    return (
        <div className={styles.container}>
            <div className={styles.stickyToolbar}>
                <WorkflowDesignerHeader
                    isEdit={!!definitionId}
                    workflowName={state.name}
                    backDestinationLabel={backDestinationLabel}
                    onBack={handleBack}
                    onHelp={() => openHelpArticle("building-a-workflow")}
                />

                <WorkflowViewSwitch view={view}
                                    onChange={setView} />
            </div>

            <div className={styles.scrollableContent}>
            {error && (
                <MessageBar intent="error">
                    <MessageBarBody>{error}</MessageBarBody>
                </MessageBar>
            )}

            {view === "form" ? (
                <>
                    <WorkflowMetadataForm
                        state={state}
                        onPatch={patch}
                        triggers={triggers}
                        triggersError={triggersError}
                        triggerDisabled={!!definitionId}
                    />
                    <Divider/>
                    <WorkflowStepsSection
                        steps={state.steps}
                        triggers={triggers}
                        subjectFields={subjectFields}
                        onAdd={() => patch({steps: [...state.steps, defaultStep()]})}
                        onUpdate={updateStep}
                        onRemove={removeStep}
                    />

                    {defScope === "ORG" && (
                        <>
                            <Divider/>
                            <div className={styles.applicabilitySection}>
                                <ApplicabilityEditor
                                    applicability={state.applicability}
                                    onChange={a => patch({applicability: a})}
                                />
                            </div>
                        </>
                    )}
                </>
            ) : (
                <Suspense fallback={
                    <div className={styles.previewLoading}>
                        <Spinner id="workflow-designer-preview-spinner"
                                 label="Loading preview" />
                    </div>
                }>
                    <WorkflowDefinitionPreview state={state} />
                </Suspense>
            )}

            <WorkflowDesignerActionBar
                onCancel={handleBack}
                onSave={requestSave}
            />
            </div>

            <SaveWorkflowDialog
                open={showSaveDialog}
                onClose={() => setShowSaveDialog(false)}
                onConfirm={performSave}
                isEdit={!!definitionId}
                state={state}
                triggers={triggers}
            />

            <Dialog open={showDiscardDialog}
                    onOpenChange={(_, d) => setShowDiscardDialog(d.open)}>
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
                                Discard and return to {backDestinationLabel}
                            </Button>
                        </DialogActions>
                    </DialogBody>
                </DialogSurface>
            </Dialog>
        </div>
    );
};

export default WorkflowDesigner;
