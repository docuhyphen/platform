import {Handle, Position} from "@xyflow/react";
import {mergeClasses} from "@fluentui/react-components";
import {nodeStateClass, useWorkflowStepNodeStyles} from "./WorkflowStepNodeStyles.tsx";
import {stepKindIcon} from "./workflowGraphPresentation.tsx";
import {nodeStatePresentation, stepKindLabel} from "./workflowGraphUtils.ts";
import {WorkflowRFNodeProps} from "./workflowGraphRfTypes.ts";

/**
 * Read-only presentation of a single workflow step node. Fixed dimensions so
 * runtime state changes can never alter layout (Decision 12). Handles are
 * non-connectable because the preview never mutates the graph.
 */
export function WorkflowStepNode({data}: WorkflowRFNodeProps)
{
    const styles = useWorkflowStepNodeStyles();
    const node = data.node;
    const kindLabel = stepKindLabel(node.stepKind ?? "UNKNOWN");
    const Icon = stepKindIcon(node.stepKind);
    const statePresentation = nodeStatePresentation(node.state);
    const primaryDetail = node.details[0];
    const isVertical = data.direction === "TB";

    return (
        <div id={`${node.id}-node`}
             role="group"
             aria-label={node.accessibilityLabel}
             className={mergeClasses(styles.stepNode, nodeStateClass(styles, node.state))}>
            <Handle id={`${node.id}-target`}
                    type="target"
                    position={isVertical ? Position.Top : Position.Left}
                    isConnectable={false}
                    className={styles.handle} />

            <div className={styles.headerRow}>
                <Icon aria-hidden={true} />
                <span className={styles.kindLabel}>{kindLabel}</span>
            </div>

            <span className={styles.title}
                  title={node.label}>
                {node.label}
            </span>

            {statePresentation.label ? (
                <span className={styles.stateBadge}>{statePresentation.label}</span>
            ) : primaryDetail ? (
                <span className={styles.details}>{primaryDetail}</span>
            ) : null}

            <Handle id={`${node.id}-source`}
                    type="source"
                    position={isVertical ? Position.Bottom : Position.Right}
                    isConnectable={false}
                    className={styles.handle} />
        </div>
    );
}
