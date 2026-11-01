import {Badge, Divider, Text} from "@fluentui/react-components";
import TagList from "../../../../components/TagList.tsx";
import {WorkflowDesignerState, WorkflowTriggerEventDto} from "../../../../models/models.tsx";
import {formatTriggerName} from "../../workflowUtils.ts";
import WorkflowReviewStepList from "./workflow-review-step-list/WorkflowReviewStepList.tsx";
import {useWorkflowReviewSummaryStyles} from "./WorkflowReviewSummaryStyles.tsx";

interface Props
{
    isEdit: boolean;
    state: WorkflowDesignerState;
    selectedTrigger?: WorkflowTriggerEventDto;
}

const WorkflowReviewSummary = ({isEdit, state, selectedTrigger}: Props) =>
{
    const styles = useWorkflowReviewSummaryStyles();

    return (
        <>
            <Text
                id={"save-workflow-dialog-text-2"}
                size={200}
                className={styles.mutedText}
            >
                {isEdit
                    ? "Review your changes before updating the workflow."
                    : "Review your workflow before saving."}
            </Text>
            <div id={"save-workflow-dialog-div-2"}
                 className={styles.reviewSection}>
                <div id={"save-workflow-dialog-div-3"}
                     className={styles.reviewRow}>
                    <Text id={"save-workflow-dialog-text-3"}
                          className={styles.reviewLabel}>Name</Text>
                    <Text id={"save-workflow-dialog-text-4"}
                          weight="semibold">{state.name}</Text>
                </div>
                <div id={"save-workflow-dialog-div-4"}
                     className={styles.reviewRow}>
                    <Text id={"save-workflow-dialog-text-5"}
                          className={styles.reviewLabel}>Trigger Event</Text>
                    <Text id={"save-workflow-dialog-text-6"}>{formatTriggerName(state.triggerEvent)}</Text>
                    {selectedTrigger?.description && (
                        <Text
                            id={"save-workflow-dialog-text-7"}
                            size={200}
                            className={styles.mutedText}
                        >
                            {selectedTrigger.description}
                        </Text>
                    )}
                </div>
                {state.summary && (
                    <div id={"save-workflow-dialog-div-5"}
                         className={styles.reviewRow}>
                        <Text id={"save-workflow-dialog-text-8"}
                              className={styles.reviewLabel}>Summary</Text>
                        <Text id={"save-workflow-dialog-text-9"}>{state.summary}</Text>
                    </div>
                )}
                {state.generalTags.length > 0 && (
                    <div id={"save-workflow-dialog-div-6"}
                         className={styles.reviewRow}>
                        <Text id={"save-workflow-dialog-text-10"}
                              className={styles.reviewLabel}>Tags</Text>
                        <TagList tags={state.generalTags}/>
                    </div>
                )}
                <div id={"save-workflow-dialog-div-7"}
                     className={styles.reviewRow}>
                    <Text id={"save-workflow-dialog-text-11"}
                          className={styles.reviewLabel}>Status</Text>
                    <Badge
                        id={"save-workflow-dialog-badge-1"}
                        color={state.isActive ? "success" : "informative"}
                        appearance="filled"
                        size="small"
                    >
                        {state.isActive ? "Active" : "Inactive"}
                    </Badge>
                </div>
                <Divider id={"save-workflow-dialog-divider-1"}/>
                <div id={"save-workflow-dialog-div-8"}
                     className={styles.reviewRow}>
                    <Text id={"save-workflow-dialog-text-12"}
                          className={styles.reviewLabel}>
                        Steps ({state.steps.length})
                    </Text>
                    <WorkflowReviewStepList steps={state.steps}/>
                </div>
            </div>
        </>
    );
};

export default WorkflowReviewSummary;
