import {Handle, Position} from "@xyflow/react";
import {mergeClasses} from "@fluentui/react-components";
import {nodeStateClass, useWorkflowStepNodeStyles} from "./WorkflowStepNodeStyles.tsx";
import {WorkflowRFNodeProps} from "./workflowGraphRfTypes.ts";

/**
 * Start / End terminal node. Compact pill so it stays visually distinct from
 * step nodes without dominating the diagram. Start has only an outgoing handle;
 * End has only an incoming handle. In instance view the frozen runtime state is
 * surfaced through the same per-state border treatment used by step nodes, so
 * status is never communicated by the accessible label alone (Decision 12). The
 * fixed dimensions are unchanged, so the overlay never shifts layout.
 */
export function WorkflowTerminalNode({data}: WorkflowRFNodeProps)
{
    const styles = useWorkflowStepNodeStyles();
    const node = data.node;
    const isStart = node.kind === "START";
    const isVertical = data.direction === "TB";

    return (
        <div id={`${node.id}-node`}
             role="group"
             aria-label={node.accessibilityLabel}
             className={mergeClasses(styles.terminalNode, nodeStateClass(styles, node.state))}>
            {!isStart ? (
                <Handle id={`${node.id}-target`}
                        type="target"
                        position={isVertical ? Position.Top : Position.Left}
                        isConnectable={false}
                        className={styles.handle} />
            ) : null}

            <span>{node.label}</span>

            {isStart ? (
                <Handle id={`${node.id}-source`}
                        type="source"
                        position={isVertical ? Position.Bottom : Position.Right}
                        isConnectable={false}
                        className={styles.handle} />
            ) : null}
        </div>
    );
}
