import {Handle, Position} from "@xyflow/react";
import {WorkflowInvalidNodeIcon} from "../../../../components/IconBundles.tsx";
import {useWorkflowInvalidNodeStyles} from "./WorkflowInvalidNodeStyles.tsx";
import {WorkflowRFNodeProps} from "../workflowGraphRfTypes.ts";

export function WorkflowInvalidNode({data}: WorkflowRFNodeProps)
{
    const styles = useWorkflowInvalidNodeStyles();
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
            <span id={"workflow-invalid-node-span-1"}
                  title={node.label}>{node.label}</span>
        </div>
    );
}
