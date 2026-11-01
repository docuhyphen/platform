import {Text} from "@fluentui/react-components";
import {WorkflowStepSpecDraft} from "../../../../../models/models.tsx";
import {STEP_TYPE_LABELS} from "../../../workflowUtils.ts";
import {useWorkflowReviewStepListStyles} from "./WorkflowReviewStepListStyles.tsx";

interface Props
{
    steps: WorkflowStepSpecDraft[];
}

const WorkflowReviewStepList = ({steps}: Props) =>
{
    const styles = useWorkflowReviewStepListStyles();

    if (steps.length === 0)
    {
        return (
            <Text
                id={"save-workflow-dialog-text-13"}
                size={200}
                className={styles.mutedText}
            >
                No steps configured.
            </Text>
        );
    }

    return (
        <div id={"save-workflow-dialog-div-9"}
             className={styles.stepList}>
            {steps.map((step, i) => (
                <div id={`save-workflow-dialog-div-10-${i}`}
                     key={i}
                     className={styles.stepItem}>
                    <span id={"save-workflow-dialog-span-1"}
                          className={styles.stepNumber}>{i + 1}</span>
                    <Text id={"save-workflow-dialog-text-14"}
                          size={300}>
                        {STEP_TYPE_LABELS[step.type] ?? step.type}
                        {step.assignees.length > 0 && (
                            <span id={"save-workflow-dialog-span-2"}
                                  className={styles.mutedSpan}>
                                {"  -  "}{step.assignees.length} assignee{step.assignees.length !== 1 ? "s" : ""}
                            </span>
                        )}
                    </Text>
                </div>
            ))}
        </div>
    );
};

export default WorkflowReviewStepList;
