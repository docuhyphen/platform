import {Button, mergeClasses, Text} from "@fluentui/react-components";
import {WorkflowStepSpecDraft} from "../../../../../models/models.tsx";
import {AddIcon, BackIcon} from "../../../../../components/IconBundles.tsx";
import StepSummaryCard from "../step-summary-card/StepSummaryCard.tsx";
import {useWorkflowStepListPageStyles} from "./WorkflowStepListPageStyles.tsx";
import {findStepsWithInvalidRoutes} from "../../stepMutations.ts";

type TransitionDirection = "forward" | "back" | null;

interface Props
{
    steps: WorkflowStepSpecDraft[];
    newStepIndex: number | null;
    transitionDirection: TransitionDirection;
    onAdd: () => void;
    onBack: () => void;
    onOpenStep: (index: number) => void;
}

const WorkflowStepListPage = ({
    steps,
    newStepIndex,
    transitionDirection,
    onAdd,
    onBack,
    onOpenStep,
}: Props) =>
{
    const styles = useWorkflowStepListPageStyles();
    const invalidRouteSteps = findStepsWithInvalidRoutes(steps);

    return (
        <div
            id="workflow-step-list-page"
            key="step-list"
            className={mergeClasses(
                styles.transitionFrame,
                transitionDirection === "back" ? styles.slideRight : undefined,
                transitionDirection === "forward" ? styles.slideLeft : undefined,
                styles.stepList,
            )}
        >
            <div id={"workflow-step-list-page-div-1"}
                 className={styles.stepListHeader}>
                <div id={"workflow-step-list-page-div-2"}
                     className={styles.headerTitle}>
                    <Button
                        id="workflow-steps-back-btn"
                        size="small"
                        appearance="subtle"
                        shape="circular"
                        icon={<BackIcon/>}
                        aria-label="Back to workflow sections"
                        onClick={onBack}
                    />
                    <Text id={"workflow-step-list-page-text-1"}
                          weight="semibold">Steps ({steps.length})</Text>
                </div>
                <Button
                    id="workflow-designer-add-step-btn"
                    size="small"
                    appearance="subtle"
                    shape={"circular"}
                    icon={<AddIcon/>}
                    onClick={onAdd}
                >
                    Add Step
                </Button>
            </div>

            {steps.map((step, index) => (
                <StepSummaryCard
                    key={index}
                    index={index}
                    step={step}
                    isNew={newStepIndex === index}
                    hasRouteWarning={invalidRouteSteps.has(index)}
                    onClick={() => onOpenStep(index)}
                />
            ))}

            {steps.length === 0 && (
                <Text id={"workflow-step-list-page-text-2"}
                      className={styles.noStepsText}>
                    No steps yet. Add a step to define the workflow logic.
                </Text>
            )}
        </div>
    );
};

export default WorkflowStepListPage;
