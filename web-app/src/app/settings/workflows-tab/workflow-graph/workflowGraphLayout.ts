/**
 * Deterministic in-house layered layout, oriented left-to-right or top-to-bottom.
 *
 * Algorithm:
 *   1. Assign each node to a rank (column, or row for top-to-bottom) by BFS
 *      from sources.
 *   2. Within each rank, order nodes with a Sugiyama-style barycenter sweep
 *      (average position of already-placed neighbours in the adjacent rank,
 *      alternating forward/backward passes) to reduce edge crossings. Nodes
 *      without positioned neighbours fall back to their original input order.
 *   3. Assign a position along the main (flow) axis from the rank, and a
 *      position along the cross axis from where the node sits within its
 *      rank. For "LR" the main axis is x and the cross axis is y; for "TB"
 *      they're swapped, so ranks stack downward instead of rightward.
 *
 * Inputs are sorted before processing, and the barycenter sweep is purely a
 * function of graph topology, so identical topology always yields identical
 * output regardless of insertion order (Decision 22).
 */

import {
    LAYOUT_NODE_SEP,
    LAYOUT_RANK_SEP,
    NODE_HEIGHT,
    NODE_WIDTH,
    START_END_HEIGHT,
    START_END_WIDTH,
    WorkflowGraphDirection,
    WorkflowGraphLayout,
    WorkflowGraphLayoutEdge,
    WorkflowGraphLayoutNode,
    WorkflowGraphPosition,
} from "./workflowGraphModels.ts";

export function layoutWorkflowGraph(
    nodes: WorkflowGraphLayoutNode[],
    edges: WorkflowGraphLayoutEdge[],
    direction: WorkflowGraphDirection = "LR",
): WorkflowGraphLayout
{
    if (nodes.length === 0)
    {
        return { positions: new Map() };
    }

    // Sort inputs for determinism.
    const sortedNodes = [...nodes].sort((a, b) => a.id.localeCompare(b.id));
    const sortedEdges = [...edges].sort((a, b) =>
    {
        const sc = a.source.localeCompare(b.source);
        return sc !== 0 ? sc : a.target.localeCompare(b.target);
    });

    const nodeIds = new Set(sortedNodes.map(n => n.id));

    // Build adjacency.
    const children = new Map<string, string[]>();
    const inDegree = new Map<string, number>();

    for (const n of sortedNodes)
    {
        children.set(n.id, []);
        inDegree.set(n.id, 0);
    }

    for (const e of sortedEdges)
    {
        if (!nodeIds.has(e.source) || !nodeIds.has(e.target)) continue;
        if (e.source === e.target) continue; // self-loops: skip
        children.get(e.source)!.push(e.target);
        inDegree.set(e.target, (inDegree.get(e.target) ?? 0) + 1);
    }

    // Sort children lists for determinism.
    for (const [, ch] of children)
    {
        ch.sort((a, b) => a.localeCompare(b));
    }

    // BFS rank assignment. Nodes unreachable from sources get rank after all reachable ones.
    const rank = new Map<string, number>();
    const queue: string[] = [];

    // Sources are nodes with no in-edges in the valid edge set.
    for (const n of sortedNodes)
    {
        if ((inDegree.get(n.id) ?? 0) === 0)
        {
            queue.push(n.id);
            rank.set(n.id, 0);
        }
    }

    // Longest-path rank assignment. The proposed rank is capped at the node
    // count so a cyclic graph (backward edge / loop) cannot relax ranks forever.
    // A directed acyclic graph never needs a rank at or beyond the node count,
    // so this bound is transparent for valid DAGs and only breaks cycles.
    const rankCap = sortedNodes.length;
    while (queue.length > 0)
    {
        const current = queue.shift()!;
        const currentRank = rank.get(current) ?? 0;
        for (const child of children.get(current) ?? [])
        {
            const proposed = currentRank + 1;
            const existing = rank.get(child);
            if (existing === undefined || (proposed > existing && proposed < rankCap))
            {
                rank.set(child, proposed);
                queue.push(child);
            }
        }
    }

    // Assign unreachable nodes to the next rank.
    const maxRank = rank.size > 0 ? Math.max(...rank.values()) : 0;
    let unreachableOffset = maxRank + 1;
    for (const n of sortedNodes)
    {
        if (!rank.has(n.id))
        {
            rank.set(n.id, unreachableOffset++);
        }
    }

    // Reverse adjacency (parents), used by the crossing-reduction pass below.
    const parents = new Map<string, string[]>();
    for (const n of sortedNodes) parents.set(n.id, []);
    for (const e of sortedEdges)
    {
        if (!nodeIds.has(e.source) || !nodeIds.has(e.target)) continue;
        if (e.source === e.target) continue;
        parents.get(e.target)!.push(e.source);
    }
    for (const [, pa] of parents) pa.sort((a, b) => a.localeCompare(b));

    // Group nodes by rank, starting from sorted order for determinism.
    const rankGroups = new Map<number, WorkflowGraphLayoutNode[]>();
    const nodeById = new Map<string, WorkflowGraphLayoutNode>();
    for (const n of sortedNodes)
    {
        nodeById.set(n.id, n);
        const r = rank.get(n.id)!;
        if (!rankGroups.has(r)) rankGroups.set(r, []);
        rankGroups.get(r)!.push(n);
    }

    const sortedRanks = [...rankGroups.keys()].sort((a, b) => a - b);

    // Order nodes within each rank using the Sugiyama barycenter heuristic:
    // repeatedly reorder each rank by the average position of its already
    // laid-out neighbours in the adjacent rank, sweeping forward then backward.
    // This reduces edge crossings between ranks (React Flow itself has no
    // built-in crossing avoidance; it only draws whatever positions/paths it is
    // given). Nodes with no positioned neighbours keep their relative,
    // deterministic id-sorted order so output never depends on insertion order.
    const order = new Map<number, string[]>();
    for (const r of sortedRanks)
    {
        order.set(r, rankGroups.get(r)!.map(n => n.id));
    }

    const barycenterSort = (ids: string[], neighborsOf: Map<string, string[]>, neighborOrder: string[]): string[] =>
    {
        const neighborIndex = new Map(neighborOrder.map((id, i) => [id, i]));
        return ids
            .map((id, i) =>
            {
                const idxs = (neighborsOf.get(id) ?? [])
                    .map(nid => neighborIndex.get(nid))
                    .filter((idx): idx is number => idx !== undefined);
                const key = idxs.length > 0 ? idxs.reduce((a, b) => a + b, 0) / idxs.length : null;
                return { id, key, i };
            })
            .sort((a, b) =>
            {
                if (a.key === null && b.key === null) return a.i - b.i;
                if (a.key === null) return 1;
                if (b.key === null) return -1;
                if (a.key !== b.key) return a.key - b.key;
                return a.i - b.i;
            })
            .map(w => w.id);
    };

    const SWEEP_PASSES = 4;
    for (let pass = 0; pass < SWEEP_PASSES; pass++)
    {
        const forward = pass % 2 === 0;
        const ranksInPass = forward ? sortedRanks : [...sortedRanks].reverse();
        for (const r of ranksInPass)
        {
            const neighborRank = forward ? r - 1 : r + 1;
            const neighborOrder = order.get(neighborRank);
            if (!neighborOrder) continue;
            order.set(r, barycenterSort(order.get(r)!, forward ? parents : children, neighborOrder));
        }
    }

    // Assign positions using the crossing-reduced order. "Main" is the flow
    // axis (rank progression); "cross" is the perpendicular axis (spread of
    // sibling nodes within a rank). Which dimension (width/height) drives
    // each, and which real axis (x/y) each maps onto, depends on direction.
    const mainExtent = (n: WorkflowGraphLayoutNode) => direction === "TB" ? n.height : n.width;
    const crossExtent = (n: WorkflowGraphLayoutNode) => direction === "TB" ? n.width : n.height;

    const positions = new Map<string, WorkflowGraphPosition>();

    let mainCursor = 0;

    for (const r of sortedRanks)
    {
        const group = order.get(r)!.map(id => nodeById.get(id)!);
        const rankMain = Math.max(...group.map(mainExtent));

        // Centre the group across the cross axis.
        const totalCross = group.reduce((sum, n, i) =>
            sum + crossExtent(n) + (i < group.length - 1 ? LAYOUT_NODE_SEP : 0), 0);
        let crossCursor = -totalCross / 2;

        for (const n of group)
        {
            const main = mainCursor + (rankMain - mainExtent(n)) / 2;
            const cross = crossCursor;
            positions.set(n.id, direction === "TB"
                ? { x: cross, y: main }
                : { x: main, y: cross });
            crossCursor += crossExtent(n) + LAYOUT_NODE_SEP;
        }

        mainCursor += rankMain + LAYOUT_RANK_SEP;
    }

    return { positions };
}

// ── Default dimension helpers ─────────────────────────────────────────────────

export function stepNodeDimensions(): { width: number; height: number }
{
    return { width: NODE_WIDTH, height: NODE_HEIGHT };
}

export function terminalNodeDimensions(): { width: number; height: number }
{
    return { width: START_END_WIDTH, height: START_END_HEIGHT };
}
