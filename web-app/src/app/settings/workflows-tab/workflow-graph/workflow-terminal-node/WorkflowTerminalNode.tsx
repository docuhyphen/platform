import {Handle, Position} from "@xyflow/react";
import {mergeClasses} from "@fluentui/react-components";
import {useWorkflowTerminalNodeStyles} from "./WorkflowTerminalNodeStyles.tsx";
import {nodeStateClass} from "../workflowNodeStyleFragments.ts";
import {WorkflowRFNodeProps} from "../workflowGraphRfTypes.ts";

export function WorkflowTerminalNode({data}: WorkflowRFNodeProps)
{
    const styles = useWorkflowTerminalNodeStyles();
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

            <span id={"workflow-terminal-node-span-1"}>{node.label}</span>

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
