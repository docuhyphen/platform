import {
    WorkflowDesignerState,
    WorkflowStepSpecDraft,
    WorkflowSubjectFieldDto,
    WorkflowTriggerEventDto,
} from "../../../../models/models.tsx";
import WorkflowApplicabilityPage from "../workflow-applicability-page/WorkflowApplicabilityPage.tsx";
import WorkflowFormNavigationHeader from "../workflow-form-navigation-header/WorkflowFormNavigationHeader.tsx";
import WorkflowMetadataForm from "../workflow-metadata-form/WorkflowMetadataForm.tsx";
import WorkflowOverviewPage from "../workflow-overview-page/WorkflowOverviewPage.tsx";
import WorkflowStepsSection from "../workflow-steps-section/WorkflowStepsSection.tsx";
import {ApplicabilityTriggerKind} from "../workflowApplicability.ts";
import {FormPage, WorkflowScope} from "../workflowDesignerDefaults.ts";
import {useWorkflowDesignerFormPagesStyles} from "./WorkflowDesignerFormPagesStyles.tsx";

interface Props
{
    formPage: FormPage;
    state: WorkflowDesignerState;
    onPatch: (patch: Partial<WorkflowDesignerState>) => void;
    triggers: WorkflowTriggerEventDto[];
    triggersError: string | null;
    isEdit: boolean;
    subjectFields: WorkflowSubjectFieldDto[];
    defScope?: WorkflowScope;
    triggerKind?: ApplicabilityTriggerKind;
    platformMode: boolean;
    onAddStep: () => void;
    onUpdateStep: (index: number, step: WorkflowStepSpecDraft) => void;
    onRemoveStep: (index: number) => void;
    onNavigate: (page: FormPage) => void;
}

const WorkflowDesignerFormPages = (props: Props) =>
{
    const styles = useWorkflowDesignerFormPagesStyles();
    const {formPage, state, onPatch, onNavigate} = props;
    const backToOverview = () => onNavigate("overview");

    if (formPage === "overview")
    {
        return (
            <WorkflowOverviewPage
                state={state}
                defScope={props.defScope}
                triggerKind={props.triggerKind}
                onNavigate={onNavigate}
            />
        );
    }

    if (formPage === "configuration")
    {
        return (
            <div id={"workflow-designer-form-page"}
                 className={styles.formPage}>
                <WorkflowFormNavigationHeader label="Workflow configuration"
                                              onBack={backToOverview}/>
                <WorkflowMetadataForm
                    state={state}
                    onPatch={onPatch}
                    triggers={props.triggers}
                    triggersError={props.triggersError}
                    triggerDisabled={props.isEdit}
                />
            </div>
        );
    }

    if (formPage === "steps")
    {
        return (
            <WorkflowStepsSection
                steps={state.steps}
                triggers={props.triggers}
                subjectFields={props.subjectFields}
                onAdd={props.onAddStep}
                onUpdate={props.onUpdateStep}
                onRemove={props.onRemoveStep}
                onBack={backToOverview}
                platformMode={props.platformMode}
            />
        );
    }

    return (
        <div id={"workflow-designer-form-page-2"}
             className={styles.formPage}>
            <WorkflowFormNavigationHeader label="Applicability"
                                          onBack={backToOverview}/>
            <WorkflowApplicabilityPage scope={props.defScope}
                                       triggerKind={props.triggerKind}
                                       applicability={state.applicability}
                                       onChange={a => onPatch({applicability: a})}/>
        </div>
    );
};

export default WorkflowDesignerFormPages;
