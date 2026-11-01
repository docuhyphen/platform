import {BaseEdge, EdgeLabelRenderer, getSmoothStepPath} from "@xyflow/react";
import {useEffect, useRef} from "react";
import {useWorkflowGraphEdgeStyles, WORKFLOW_GRAPH_EDGE_CORNER_RADIUS} from "./WorkflowGraphEdgeStyles.tsx";
import {WorkflowRFEdgeProps} from "../workflowGraphRfTypes.ts";
import {EDGE_BEND_OFFSET} from "../workflowGraphModels.ts";

export function WorkflowGraphEdge(props: WorkflowRFEdgeProps)
{
    const {
        id, sourceX, sourceY, targetX, targetY,
        sourcePosition, targetPosition, markerEnd, label, data,
    } = props;
    const styles = useWorkflowGraphEdgeStyles();
    const labelRef = useRef<HTMLDivElement | null>(null);

    const [edgePath, labelX, labelY] = getSmoothStepPath({
        sourceX,
        sourceY,
        sourcePosition,
        targetX,
        targetY,
        targetPosition,
        borderRadius: WORKFLOW_GRAPH_EDGE_CORNER_RADIUS,
        centerX: data?.centerX,
        centerY: data?.centerY,
        offset: EDGE_BEND_OFFSET,
    });

    useEffect(() =>
    {
        labelRef.current?.style.setProperty("--workflow-edge-label-x", `${labelX}px`);
        labelRef.current?.style.setProperty("--workflow-edge-label-y", `${labelY}px`);
    }, [labelX, labelY]);

    return (
        <>
            <BaseEdge id={id}
                      path={edgePath}
                      markerEnd={markerEnd} />
            {label ? (
                <EdgeLabelRenderer>
                    <div id={`workflow-graph-edge-label-${id}`}
                         ref={labelRef}
                         className={styles.label}>
                        {label}
                    </div>
                </EdgeLabelRenderer>
            ) : null}
        </>
    );
}
