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

/**
 * Convert a library-independent `WorkflowGraph` into deterministically
 * positioned React Flow nodes and edges. Layout depends only on frozen topology
 * and fixed node dimensions, so runtime state never rearranges the diagram.
 * Nodes are draggable so users can reorganize the diagram for easier viewing;
 * this only changes on-screen position and never mutates workflow data.
 */
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
            // Draggable so users can reorganize the diagram layout (the
            // underlying workflow topology stays read-only regardless).
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

        // Edges that skip over one or more ranks (e.g. an APPROVAL step's
        // Reject outcome jumping straight to End past a NOTIFICATION step in
        // between) would otherwise bend at the midpoint of the whole span,
        // which frequently lands inside an unrelated node's rank and routes
        // the edge through/behind it. Ranks are always separated by a fixed,
        // node-free `LAYOUT_RANK_SEP` gap along the flow axis (x for "LR",
        // y for "TB"), so forcing the bend into the gap immediately before
        // the target (or after the source, for edges that go backwards)
        // guarantees it never crosses another node's bounds, regardless of
        // how many ranks it skips.
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
            // The custom "workflowEdge" type routes edges as orthogonal
            // smoothstep segments with rounded corners (like the library's
            // built-in "smoothstep" type) rather than free-form bezier
            // curves, which keeps multiple connectors visually separated
            // instead of overlapping as they cross between ranks. It also
            // renders the label as an HTML pill instead of measured SVG
            // text, so the label background always fits its text.
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
