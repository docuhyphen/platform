import {MarkerType} from "@xyflow/react";
import {LAYOUT_RANK_SEP, WorkflowGraph, WorkflowGraphDirection} from "./workflowGraphModels.ts";
import {
    layoutWorkflowGraph,
    stepNodeDimensions,
    terminalNodeDimensions,
} from "./workflowGraphLayout.ts";
import {edgeMarkerColor, edgeVisualStyle} from "./workflowGraphPresentation.tsx";
import {outcomeLabel} from "./workflowGraphUtils.ts";
import {WorkflowRFEdge, WorkflowRFNode, WorkflowRFNodeType} from "./workflowGraphRfTypes.ts";

function rfNodeType(kind: string): WorkflowRFNodeType
{
    if (kind === "START" || kind === "END") return "terminal";
    if (kind === "INVALID_TARGET") return "invalid";
    return "step";
}

export function toReactFlow(
    graph: WorkflowGraph,
    direction: WorkflowGraphDirection = "LR",
): { nodes: WorkflowRFNode[]; edges: WorkflowRFEdge[] }
{
    const layoutNodes = graph.nodes.map(n =>
    {
        const dims = n.kind === "START" || n.kind === "END"
            ? terminalNodeDimensions()
            : stepNodeDimensions();
        return { id: n.id, width: dims.width, height: dims.height };
    });

    const layout = layoutWorkflowGraph(
        layoutNodes,
        graph.edges.map(e => ({ id: e.id, source: e.source, target: e.target })),
        direction,
    );

    const widthById = new Map(layoutNodes.map(n => [n.id, n.width]));
    const heightById = new Map(layoutNodes.map(n => [n.id, n.height]));

    const nodes: WorkflowRFNode[] = graph.nodes.map(n =>
    {
        const position = layout.positions.get(n.id) ?? { x: 0, y: 0 };
        return {
            id: n.id,
            type: rfNodeType(n.kind),
            position,
            data: { node: n, direction },
            draggable: true,
            connectable: false,
            selectable: true,
            deletable: false,
        };
    });

    const edges: WorkflowRFEdge[] = graph.edges.map(e =>
    {
        const color = edgeMarkerColor(e.state);
        const label = e.label ?? (e.outcome !== "DEFAULT" ? outcomeLabel(e.outcome) : undefined);

        const sourcePos = layout.positions.get(e.source);
        const targetPos = layout.positions.get(e.target);
        let centerX: number | undefined;
        let centerY: number | undefined;
        if (sourcePos && targetPos)
        {
            if (direction === "TB")
            {
                const sourceHeight = heightById.get(e.source) ?? 0;
                const sourceHandleY = sourcePos.y + sourceHeight;
                const targetHandleY = targetPos.y;
                if (targetHandleY > sourceHandleY)
                {
                    centerY = targetHandleY - LAYOUT_RANK_SEP / 2;
                }
                else if (targetHandleY < sourceHandleY)
                {
                    centerY = targetHandleY + LAYOUT_RANK_SEP / 2;
                }
            }
            else
            {
                const sourceWidth = widthById.get(e.source) ?? 0;
                const sourceHandleX = sourcePos.x + sourceWidth;
                const targetHandleX = targetPos.x;
                if (targetHandleX > sourceHandleX)
                {
                    centerX = targetHandleX - LAYOUT_RANK_SEP / 2;
                }
                else if (targetHandleX < sourceHandleX)
                {
                    centerX = targetHandleX + LAYOUT_RANK_SEP / 2;
                }
            }
        }

        return {
            id: e.id,
            source: e.source,
            target: e.target,
            label,
            type: "workflowEdge",
            data: { centerX, centerY },
            style: edgeVisualStyle(e.state),
            markerEnd: { type: MarkerType.ArrowClosed, color, width: 18, height: 18 },
            deletable: false,
            reconnectable: false,
            focusable: true,
            selectable: false,
        };
    });

    return { nodes, edges };
}
