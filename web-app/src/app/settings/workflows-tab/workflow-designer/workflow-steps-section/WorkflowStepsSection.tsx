import {
    WorkflowStepSpecDraft,
    WorkflowSubjectFieldDto,
    WorkflowTriggerEventDto,
} from "../../../../models/models.tsx";
import {useEffect, useState} from "react";
import WorkflowStepDetailPage from "./WorkflowStepDetailPage.tsx";
import WorkflowStepListPage from "./WorkflowStepListPage.tsx";

interface Props
{
    steps: WorkflowStepSpecDraft[];
    triggers: WorkflowTriggerEventDto[];
    subjectFields: WorkflowSubjectFieldDto[];
    onAdd: () => void;
    onUpdate: (index: number, step: WorkflowStepSpecDraft) => void;
    onRemove: (index: number) => void;
    onBack: () => void;
    platformMode?: boolean;
}

const WorkflowStepsSection = ({
    steps,
    triggers,
    subjectFields,
    onAdd,
    onUpdate,
    onRemove,
    onBack,
    platformMode = false,
}: Props) =>
{
    const [selectedStepIndex, setSelectedStepIndex] = useState<number | null>(null);
    const [newStepIndex, setNewStepIndex] = useState<number | null>(null);
    const [transitionDirection, setTransitionDirection] = useState<"forward" | "back" | null>(null);

    useEffect(() =>
    {
        if (selectedStepIndex !== null && selectedStepIndex >= steps.length)
        {
            setTransitionDirection("back");
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

    const openStep = (index: number) =>
    {
        if (newStepIndex === index) setNewStepIndex(null);
        setTransitionDirection("forward");
        setSelectedStepIndex(index);
    };

    const closeStep = () =>
    {
        setTransitionDirection("back");
        setSelectedStepIndex(null);
    };

    if (selectedStepIndex !== null)
    {
        const selectedStep = steps[selectedStepIndex];

        return (
            <WorkflowStepDetailPage
                selectedStepIndex={selectedStepIndex}
                step={selectedStep}
                steps={steps}
                triggers={triggers}
                subjectFields={subjectFields}
                transitionDirection={transitionDirection}
                onUpdate={onUpdate}
                onRemove={(index) =>
                {
                    onRemove(index);
                    closeStep();
                }}
                onBack={closeStep}
                platformMode={platformMode}
            />
        );
    }

    return (
        <WorkflowStepListPage
            steps={steps}
            newStepIndex={newStepIndex}
            transitionDirection={transitionDirection}
            onAdd={addStep}
            onBack={onBack}
            onOpenStep={openStep}
        />
    );
};

export default WorkflowStepsSection;
