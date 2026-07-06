import {Button, Text} from "@fluentui/react-components";
import {
    WorkflowStepSpecDraft,
    WorkflowSubjectFieldDto,
    WorkflowTriggerEventDto,
} from "../../../../models/models.tsx";
import {AddIcon, BackIcon} from "../../../../components/IconBundles.tsx";
import StepCard from "../../step-card/StepCard.tsx";
import {useWorkflowStepsSectionStyles} from "./WorkflowStepsSectionStyles.tsx";
import StepSummaryCard from "./step-summary-card/StepSummaryCard.tsx";
import {useEffect, useState} from "react";

interface Props
{
    steps: WorkflowStepSpecDraft[];
    triggers: WorkflowTriggerEventDto[];
    subjectFields: WorkflowSubjectFieldDto[];
    onAdd: () => void;
    onUpdate: (index: number, step: WorkflowStepSpecDraft) => void;
    onRemove: (index: number) => void;
    onBack: () => void;
}

const WorkflowStepsSection = ({steps, triggers, subjectFields, onAdd, onUpdate, onRemove, onBack}: Props) =>
{
    const styles = useWorkflowStepsSectionStyles();
    const [selectedStepIndex, setSelectedStepIndex] = useState<number | null>(null);
    const [newStepIndex, setNewStepIndex] = useState<number | null>(null);

    useEffect(() =>
    {
        if (selectedStepIndex !== null && selectedStepIndex >= steps.length)
        {
            setSelectedStepIndex(null);
        }
    }, [selectedStepIndex, steps.length]);

    useEffect(() =>
    {
        if (newStepIndex === null || newStepIndex >= steps.length) return;

        const frame = requestAnimationFrame(() =>
        {
            document.getElementById(`workflow-step-summary-card-${newStepIndex}`)?.scrollIntoView({
                behavior: "smooth",
                block: "end",
            });
        });

        return () => cancelAnimationFrame(frame);
    }, [newStepIndex, steps.length]);

    const addStep = () =>
    {
        const addedStepIndex = steps.length;
        onAdd();
        setNewStepIndex(addedStepIndex);
    };

    if (selectedStepIndex !== null)
    {
        const selectedStep = steps[selectedStepIndex];

        return (
            <div className={styles.stepDetail}>
                <StepCard
                    index={selectedStepIndex}
                    step={selectedStep}
                    steps={steps}
                    onChange={step => onUpdate(selectedStepIndex, step)}
                    onRemove={() =>
                    {
                        onRemove(selectedStepIndex);
                        setSelectedStepIndex(null);
                    }}
                    triggers={triggers}
                    subjectFields={subjectFields}
                    onBack={() => setSelectedStepIndex(null)}
                />
            </div>
        );
    }

    return (
        <div className={styles.stepList}>
            <div className={styles.stepListHeader}>
                <div className={styles.headerTitle}>
                    <Button
                        id="workflow-steps-back-btn"
                        size="small"
                        appearance="subtle"
                        shape="circular"
                        icon={<BackIcon/>}
                        aria-label="Back to workflow sections"
                        onClick={onBack}
                    />
                    <Text weight="semibold">Steps ({steps.length})</Text>
                </div>
                <Button
                    id="workflow-designer-add-step-btn"
                    size="small"
                    appearance="subtle"
                    shape={"circular"}
                    icon={<AddIcon/>}
                    onClick={addStep}
                >
                    Add Step
                </Button>
            </div>

            {steps.map((step, i) => (
                <StepSummaryCard
                    key={i}
                    index={i}
                    step={step}
                    isNew={newStepIndex === i}
                    onClick={() =>
                    {
                        if (newStepIndex === i) setNewStepIndex(null);
                        setSelectedStepIndex(i);
                    }}
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
