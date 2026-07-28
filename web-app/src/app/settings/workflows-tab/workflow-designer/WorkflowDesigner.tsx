import {lazy, Suspense, useCallback, useEffect, useMemo, useRef, useState} from "react";
import {
    Button,
    Dialog,
    DialogActions,
    DialogBody,
    DialogContent,
    DialogSurface,
    DialogTitle,
    mergeClasses,
    MessageBar,
    MessageBarBody,
    Spinner,
    Text,
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
import WorkflowSectionCard from "./workflow-steps-section/step-summary-card/WorkflowSectionCard.tsx";
import {
    BackIcon,
    WorkflowApplicabilityIcon,
    WorkflowConfigurationIcon,
    WorkflowStepsIcon,
} from "../../../components/IconBundles.tsx";
import {formatTriggerName} from "../workflowUtils.ts";
import {appendStep, deleteStepAndRemap, replaceStep} from "./stepMutations.ts";
import {buildDefinitionGraph} from "../workflow-graph/workflowDefinitionGraphAdapter.ts";
import {validateWorkflowGraph} from "../workflow-graph/workflowGraphValidation.ts";

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
    enforcedScope?: 'APP';
    createAsTemplate?: boolean;
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

type FormPage = "overview" | "configuration" | "applicability" | "steps";
type FormPageTransitionDirection = "forward" | "back" | null;

const WorkflowDesigner = ({
    definitionId,
    scope,
    backDestinationLabel,
    onBack,
    onSaved,
    enforcedScope,
    createAsTemplate = false,
}: Props) =>
{
    const styles = useWorkflowDesignerStyles();
    const {openHelpArticle} = useHelpSidebar();
    const [state, setState] = useState<WorkflowDesignerState>(defaultState());
    const [triggers, setTriggers] = useState<WorkflowTriggerEventDto[]>([]);
    const [triggersError, setTriggersError] = useState<string | null>(null);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [scopeViolation, setScopeViolation] = useState(false);
    const [showDiscardDialog, setShowDiscardDialog] = useState(false);
    const [showSaveDialog, setShowSaveDialog] = useState(false);
    const [defScope, setDefScope] = useState<'PERSONAL' | 'ORG' | 'APP' | undefined>(scope);
    const [formPage, setFormPage] = useState<FormPage>("overview");
    const [formPageTransitionDirection, setFormPageTransitionDirection] =
        useState<FormPageTransitionDirection>(null);
    const initialStateRef = useRef<string | null>(null);

    const isDirty = () =>
        initialStateRef.current !== null &&
        initialStateRef.current !== JSON.stringify(state);

    const patch = (p: Partial<WorkflowDesignerState>) => setState(s => ({...s, ...p}));

    const selectedTrigger = triggers.find(t => t.eventName === state.triggerEvent);
    const subjectFields = selectedTrigger?.subjectFields ?? [];

    const load = useCallback(async () =>
    {
        setScopeViolation(false);
        setFormPage("overview");
        setFormPageTransitionDirection(null);
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
    }, [definitionId, enforcedScope]);

    useEffect(() => { load(); }, [load]);

    const updateStep = (index: number, step: WorkflowStepSpecDraft) =>
        patch({steps: replaceStep(state.steps, index, step)});

    const removeStep = (index: number) =>
        patch({steps: deleteStepAndRemap(state.steps, index)});

    const graphWarnings = useMemo(
        () => validateWorkflowGraph(buildDefinitionGraph(state)).warnings,
        [state],
    );

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
        if (enforcedScope && defScope !== enforcedScope)
        {
            throw new Error("Cannot save a workflow outside the Platform Administration scope.");
        }
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
                isActive: state.isActive, stepsJson, scope: enforcedScope ?? scope,
                isTemplate: createAsTemplate,
            });
        }
        onSaved();
    };

    const handleBack = () => isDirty() ? setShowDiscardDialog(true) : onBack();

    const navigateFormPage = (nextPage: FormPage) =>
    {
        setFormPageTransitionDirection(nextPage === "overview" ? "back" : "forward");
        setFormPage(nextPage);
    };

    const formBackButton = (label: string, onBackClick: () => void) => (
        <div className={styles.formNavigationHeader}>
            <Button
                id={`workflow-form-back-${label.toLowerCase().replaceAll(" ", "-")}`}
                size="small"
                appearance="subtle"
                shape="circular"
                icon={<BackIcon/>}
                aria-label={`Back from ${label}`}
                onClick={onBackClick}
            />
            <Text
                id={`workflow-form-heading-${label.toLowerCase().replaceAll(" ", "-")}`}
                weight="semibold"
                size={400}
            >
                {label}
            </Text>
        </div>
    );

    const applicabilityCount = state.applicability?.fieldConditions.length ?? 0;
    const applicabilitySummary = defScope === "ORG"
        ? applicabilityCount === 0
            ? "Applies to every matching Exchange"
            : `${applicabilityCount} ${applicabilityCount === 1 ? "condition" : "conditions"} configured`
        : "Only organization workflows support applicability conditions";
    const stepsSummary = state.steps.length === 0
        ? "No steps configured"
        : `${state.steps.length} ${state.steps.length === 1 ? "step" : "steps"} configured`;
    const configurationSummary = state.name.trim()
        ? `${state.name}${state.triggerEvent ? `, ${formatTriggerName(state.triggerEvent)}` : ""}`
        : "Workflow name and trigger are not configured";

    const formContent = formPage === "overview" ? (
        <div className={styles.formSectionCards}>
            <WorkflowSectionCard
                id="workflow-configuration-card"
                title="Workflow configuration"
                description="Name, trigger event, summary, tags, and active status."
                summary={configurationSummary}
                icon={<WorkflowConfigurationIcon/>}
                onClick={() => navigateFormPage("configuration")}
            />
            <WorkflowSectionCard
                id="workflow-applicability-card"
                title="Applicability"
                description="Choose which matching Exchanges can start this workflow."
                summary={applicabilitySummary}
                icon={<WorkflowApplicabilityIcon/>}
                onClick={() => navigateFormPage("applicability")}
            />
            <WorkflowSectionCard
                id="workflow-steps-card"
                title="Steps"
                description="Build the approvals, notifications, conditions, and actions in this workflow."
                summary={stepsSummary}
                icon={<WorkflowStepsIcon/>}
                onClick={() => navigateFormPage("steps")}
            />
        </div>
    ) : formPage === "configuration" ? (
        <div className={styles.formPage}>
            {formBackButton("Workflow configuration", () => navigateFormPage("overview"))}
            <WorkflowMetadataForm
                state={state}
                onPatch={patch}
                triggers={triggers}
                triggersError={triggersError}
                triggerDisabled={!!definitionId}
            />
        </div>
    ) : formPage === "steps" ? (
            <WorkflowStepsSection
                steps={state.steps}
                triggers={triggers}
                subjectFields={subjectFields}
                onAdd={() => patch({steps: appendStep(state.steps, defaultStep())})}
                onUpdate={updateStep}
                onRemove={removeStep}
                onBack={() => navigateFormPage("overview")}
                platformMode={enforcedScope === "APP"}
            />
    ) : (
        <div className={styles.formPage}>
            {formBackButton("Applicability", () => navigateFormPage("overview"))}
            {defScope === "ORG" ? (
                <ApplicabilityEditor
                    applicability={state.applicability}
                    onChange={a => patch({applicability: a})}
                />
            ) : (
                <Text id="workflow-applicability-unavailable-message">
                    Applicability conditions are available only for organization workflows.
                </Text>
            )}
        </div>
    );

    const diagramContent = (
        <Suspense
            fallback={
                <div className={styles.previewLoading}>
                    <Spinner
                        id="workflow-designer-preview-spinner"
                        label="Loading preview"
                    />
                </div>
            }
        >
            <WorkflowDefinitionPreview
                state={state}
                defaultDirection="TB"
                fillHeight={true}
            />
        </Suspense>
    );

    if (loading) return <Spinner label="Loading workflow..." size="small"/>;
    if (scopeViolation)
    {
        return (
            <div
                id={"platform-workflow-scope-violation"}
                className={styles.container}>
                <MessageBar intent={"error"}>
                    <MessageBarBody>{error}</MessageBarBody>
                </MessageBar>
                <Button
                    id={"platform-workflow-scope-violation-back"}
                    appearance={"secondary"}
                    shape={"circular"}
                    onClick={onBack}>
                    Back to Platform Content
                </Button>
            </div>
        );
    }

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

            </div>

            <div className={styles.splitContentShell}>
                {error && (
                    <MessageBar intent="error">
                        <MessageBarBody>{error}</MessageBarBody>
                    </MessageBar>
                )}

                <div className={styles.splitContent}>
                    <div className={styles.splitDiagramColumn}>
                        <div className={styles.diagramPanel}>
                            {diagramContent}
                        </div>
                    </div>
                    <div className={styles.splitFormColumn}>
                        <div
                            id={`workflow-designer-form-page-${formPage}`}
                            key={formPage}
                            className={mergeClasses(
                                styles.formPageTransitionFrame,
                                formPageTransitionDirection === "forward" ? styles.formPageSlideLeft : undefined,
                                formPageTransitionDirection === "back" ? styles.formPageSlideRight : undefined,
                            )}
                        >
                            {formContent}
                        </div>
                    </div>
                </div>
            </div>

            <WorkflowDesignerActionBar
                onCancel={handleBack}
                onSave={requestSave}
            />

            <SaveWorkflowDialog
                open={showSaveDialog}
                onClose={() => setShowSaveDialog(false)}
                onConfirm={performSave}
                isEdit={!!definitionId}
                state={state}
                triggers={triggers}
                warnings={graphWarnings}
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
                                id="workflow-designer-discard-btn"
                                appearance="primary"
                                shape={"circular"}
                                onClick={onBack}
                            >
                                Discard
                            </Button>
                            <Button
                                id="workflow-designer-keep-editing-btn"
                                appearance="secondary"
                                shape={"circular"}
                                onClick={() => setShowDiscardDialog(false)}
                            >
                                Keep editing
                            </Button>
                        </DialogActions>
                    </DialogBody>
                </DialogSurface>
            </Dialog>
        </div>
    );
};

export default WorkflowDesigner;
