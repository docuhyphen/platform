import {Button, Text} from "@fluentui/react-components";
import {
    WorkflowStepSpecDraft,
    WorkflowSubjectFieldDto,
    WorkflowTriggerEventDto,
} from "../../../../models/models.tsx";
import {AddIcon} from "../../../../components/IconBundles.tsx";
import StepCard from "../../step-card/StepCard.tsx";
import {useWorkflowStepsSectionStyles} from "./WorkflowStepsSectionStyles.tsx";

interface Props
{
    steps: WorkflowStepSpecDraft[];
    triggers: WorkflowTriggerEventDto[];
    subjectFields: WorkflowSubjectFieldDto[];
    onAdd: () => void;
    onUpdate: (index: number, step: WorkflowStepSpecDraft) => void;
    onRemove: (index: number) => void;
}

const WorkflowStepsSection = ({steps, triggers, subjectFields, onAdd, onUpdate, onRemove}: Props) =>
{
    const styles = useWorkflowStepsSectionStyles();

    return (
        <div className={styles.stepList}>
            <div className={styles.stepListHeader}>
                <Text weight="semibold">Steps ({steps.length})</Text>
                <Button
                    id="workflow-designer-add-step-btn"
                    size="small"
                    appearance="secondary"
                    shape={"circular"}
                    icon={<AddIcon/>}
                    onClick={onAdd}
                >
                    Add Step
                </Button>
            </div>

            {steps.map((step, i) => (
                <StepCard
                    key={i}
                    index={i}
                    step={step}
                    steps={steps}
                    onChange={s => onUpdate(i, s)}
                    onRemove={() => onRemove(i)}
                    triggers={triggers}
                    subjectFields={subjectFields}
                />
            ))}

            {steps.length === 0 && (
                <Text className={styles.noStepsText}>
                    No steps yet. Add a step to define the workflow logic.
                </Text>
            )}
        </div>
    );
};

export default WorkflowStepsSection;
