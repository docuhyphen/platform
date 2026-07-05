import {Handle, Position} from "@xyflow/react";
import {WorkflowInvalidNodeIcon} from "../../../components/IconBundles.tsx";
import {useWorkflowStepNodeStyles} from "./WorkflowStepNodeStyles.tsx";
import {WorkflowRFNodeProps} from "./workflowGraphRfTypes.ts";

/**
 * Invalid branch-target node. Rendered with an explicit danger treatment so a
 * malformed reference never looks like an executable step.
 */
export function WorkflowInvalidNode({data}: WorkflowRFNodeProps)
{
    const styles = useWorkflowStepNodeStyles();
    const node = data.node;
    const isVertical = data.direction === "TB";

    return (
        <div id={`${node.id}-node`}
             role="group"
             aria-label={node.accessibilityLabel}
             className={styles.invalidNode}>
            <Handle id={`${node.id}-target`}
                    type="target"
                    position={isVertical ? Position.Top : Position.Left}
                    isConnectable={false}
                    className={styles.handle} />

            <WorkflowInvalidNodeIcon aria-hidden={true} />
            <span title={node.label}>{node.label}</span>
        </div>
    );
}
