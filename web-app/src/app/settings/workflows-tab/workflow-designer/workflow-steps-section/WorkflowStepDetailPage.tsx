import {mergeClasses} from "@fluentui/react-components";
import {
    WorkflowStepSpecDraft,
    WorkflowSubjectFieldDto,
    WorkflowTriggerEventDto,
} from "../../../../models/models.tsx";
import StepCard from "../../step-card/StepCard.tsx";
import {useWorkflowStepsSectionStyles} from "./WorkflowStepsSectionStyles.tsx";

type TransitionDirection = "forward" | "back" | null;

interface Props
{
    selectedStepIndex: number;
    step: WorkflowStepSpecDraft;
    steps: WorkflowStepSpecDraft[];
    triggers: WorkflowTriggerEventDto[];
    subjectFields: WorkflowSubjectFieldDto[];
    transitionDirection: TransitionDirection;
    onUpdate: (index: number, step: WorkflowStepSpecDraft) => void;
    onRemove: (index: number) => void;
    onBack: () => void;
    platformMode?: boolean;
}

const WorkflowStepDetailPage = ({
    selectedStepIndex,
    step,
    steps,
    triggers,
    subjectFields,
    transitionDirection,
    onUpdate,
    onRemove,
    onBack,
    platformMode = false,
}: Props) =>
{
    const styles = useWorkflowStepsSectionStyles();

    return (
        <div
            id={`workflow-step-detail-page-${selectedStepIndex}`}
            key={`step-detail-${selectedStepIndex}`}
            className={mergeClasses(
                styles.transitionFrame,
                transitionDirection === "forward" ? styles.slideLeft : undefined,
                transitionDirection === "back" ? styles.slideRight : undefined,
                styles.stepDetail,
            )}
        >
            <StepCard
                index={selectedStepIndex}
                step={step}
                steps={steps}
                onChange={nextStep => onUpdate(selectedStepIndex, nextStep)}
                onRemove={() => onRemove(selectedStepIndex)}
                triggers={triggers}
                subjectFields={subjectFields}
                onBack={onBack}
                platformMode={platformMode}
            />
        </div>
    );
};

export default WorkflowStepDetailPage;
