import {useMemo} from "react";
import {Text} from "@fluentui/react-components";
import {WorkflowDesignerState} from "../../../../models/models.tsx";
import {buildDefinitionGraph} from "../../workflow-graph/workflowDefinitionGraphAdapter.ts";
import {WorkflowGraphPreview} from "../../workflow-graph/WorkflowGraphPreview.tsx";
import {useWorkflowDefinitionPreviewStyles} from "./WorkflowDefinitionPreviewStyles.tsx";

interface Props
{
    state: WorkflowDesignerState;
}

const WorkflowDefinitionPreview = ({state}: Props) =>
{
    const styles = useWorkflowDefinitionPreviewStyles();

    const graph = useMemo(() => buildDefinitionGraph(state), [state]);

    const conditionCount = state.applicability?.fieldConditions.length ?? 0;
    const applicabilityText = conditionCount > 0
        ? `This workflow starts only when ${conditionCount} field ${conditionCount === 1 ? "condition" : "conditions"} match.`
        : "This workflow applies to every matching subject.";

    return (
        <div id="workflow-definition-preview"
             className={styles.container}>
            <div id="workflow-definition-preview-applicability"
                 className={styles.applicabilitySummary}>
                <Text size={200}
                      weight="semibold">
                    Applicability
                </Text>
                <Text size={200}
                      className={styles.applicabilityDetail}>
                    {applicabilityText}
                </Text>
            </div>

            <WorkflowGraphPreview
                graph={graph}
                mode="DEFINITION_PREVIEW"
                ariaLabel="Workflow definition diagram"
            />
        </div>
    );
};

export default WorkflowDefinitionPreview;
