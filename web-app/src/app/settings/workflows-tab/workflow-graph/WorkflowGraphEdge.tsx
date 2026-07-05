import {BaseEdge, EdgeLabelRenderer, getSmoothStepPath} from "@xyflow/react";
import {useWorkflowGraphEdgeStyles} from "./WorkflowGraphEdgeStyles.tsx";
import {WorkflowRFEdgeProps} from "./workflowGraphRfTypes.ts";

/**
 * Custom edge renderer used for every workflow graph connection. React
 * Flow's built-in edge label sizes its background by measuring the SVG
 * text's `getBBox()` once on mount; if that measurement ever runs before the
 * label's final font metrics settle, the background box is permanently too
 * small and the text overflows it (the bug this component fixes). Rendering
 * the label as an HTML pill via `EdgeLabelRenderer` instead lets the browser's
 * normal box layout (padding + `white-space: nowrap`) size the background to
 * the real text every time, so it can never be undersized.
 */
export function WorkflowGraphEdge(props: WorkflowRFEdgeProps)
{
    const {
        id, sourceX, sourceY, targetX, targetY,
        sourcePosition, targetPosition, style, markerEnd, label, data,
    } = props;
    const styles = useWorkflowGraphEdgeStyles();

    const [edgePath, labelX, labelY] = getSmoothStepPath({
        sourceX,
        sourceY,
        sourcePosition,
        targetX,
        targetY,
        targetPosition,
        borderRadius: 8,
        // Forces the bend into the node-free rank gap immediately before
        // (or after, for backwards edges) the target, so edges that skip
        // over ranks never route through/behind an unrelated node in
        // between. See workflowGraphToReactFlow.ts for how this is derived.
        centerX: data?.centerX,
        // Same rank-skip-safe bend forcing as centerX, but along the
        // vertical axis for top-to-bottom (vertical) diagrams.
        centerY: data?.centerY,
        // How far the path travels straight out of the source/target handle
        // before it's allowed to bend. The library default (20) lets edges
        // (and their labels, which sit at the path midpoint) curve almost
        // immediately, so parallel Approve/Reject connectors could bend
        // right next to a node and appear to run behind it. A larger offset
        // keeps every bend well clear of node borders.
        offset: 40,
    });

    return (
        <>
            <BaseEdge id={id}
                      path={edgePath}
                      style={style}
                      markerEnd={markerEnd} />
            {label ? (
                <EdgeLabelRenderer>
                    <div id={`workflow-graph-edge-label-${id}`}
                         className={styles.label}
                         style={{transform: `translate(-50%, -50%) translate(${labelX}px, ${labelY}px)`}}>
                        {label}
                    </div>
                </EdgeLabelRenderer>
            ) : null}
        </>
    );
}
