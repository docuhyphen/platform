import {WorkflowDesignerState} from "../../../../models/models.tsx";
import {
    WorkflowApplicabilityIcon,
    WorkflowConfigurationIcon,
    WorkflowStepsIcon,
} from "../../../../components/IconBundles.tsx";
import WorkflowSectionCard from "../workflow-steps-section/workflow-section-card/WorkflowSectionCard.tsx";
import {ApplicabilityTriggerKind, applicabilitySummaryOf} from "../workflowApplicability.ts";
import {FormPage, WorkflowScope} from "../workflowDesignerDefaults.ts";
import {configurationSummaryOf, stepsSummaryOf} from "../workflowDesignerSummaries.ts";
import {useWorkflowOverviewPageStyles} from "./WorkflowOverviewPageStyles.tsx";

interface Props
{
    state: WorkflowDesignerState;
    defScope?: WorkflowScope;
    triggerKind?: ApplicabilityTriggerKind;
    onNavigate: (page: FormPage) => void;
}

const WorkflowOverviewPage = ({state, defScope, triggerKind, onNavigate}: Props) =>
{
    const styles = useWorkflowOverviewPageStyles();

    return (
        <div id={"workflow-designer-form-section-cards"}
             className={styles.formSectionCards}>
            <WorkflowSectionCard
                id="workflow-configuration-card"
                title="Workflow configuration"
                description="Name, trigger event, summary, tags, and active status."
                summary={configurationSummaryOf(state)}
                icon={<WorkflowConfigurationIcon/>}
                onClick={() => onNavigate("configuration")}
            />
            <WorkflowSectionCard
                id="workflow-applicability-card"
                title="Applicability"
                description="Choose which matching Exchanges can start this workflow."
                summary={applicabilitySummaryOf(state.applicability, defScope, triggerKind)}
                icon={<WorkflowApplicabilityIcon/>}
                onClick={() => onNavigate("applicability")}
            />
            <WorkflowSectionCard
                id="workflow-steps-card"
                title="Steps"
                description="Build the approvals, notifications, conditions, and actions in this workflow."
                summary={stepsSummaryOf(state.steps)}
                icon={<WorkflowStepsIcon/>}
                onClick={() => onNavigate("steps")}
            />
        </div>
    );
};

export default WorkflowOverviewPage;
