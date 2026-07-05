/**
 * Structural graph analysis: detects unreachable nodes, cycles, missing
 * terminal paths, invalid targets, and ambiguous transitions.
 *
 * All checks are informational. None introduce new save-blocking behaviour
 * beyond what the backend already validates. (Decision 11 / Phase 1 rules.)
 */

import { WorkflowGraph } from "./workflowGraphModels.ts";

export interface WorkflowGraphValidationResult
{
    warnings: string[];
}

/**
 * Run all structural checks on a built graph and return the warnings.
 * The graph's own `warnings` array (from the adapter) is merged in.
 */
export function validateWorkflowGraph(graph: WorkflowGraph): WorkflowGraphValidationResult
{
    const warnings: string[] = [...graph.warnings];

    const nodeIds = new Set(graph.nodes.map(n => n.id));
    const endNodes = new Set(graph.nodes.filter(n => n.kind === "END").map(n => n.id));
    const startNodes = new Set(graph.nodes.filter(n => n.kind === "START").map(n => n.id));

    // Build adjacency maps.
    const children = new Map<string, Set<string>>();
    const parents = new Map<string, Set<string>>();
    for (const n of graph.nodes)
    {
        children.set(n.id, new Set());
        parents.set(n.id, new Set());
    }
    for (const e of graph.edges)
    {
        if (nodeIds.has(e.source) && nodeIds.has(e.target))
        {
            children.get(e.source)!.add(e.target);
            parents.get(e.target)!.add(e.source);
        }
    }

    // Unreachable steps: not reachable from any START node.
    const reachable = bfsReachable(startNodes, children);
    for (const n of graph.nodes)
    {
        if (n.kind !== "START" && !reachable.has(n.id))
        {
            warnings.push(`Step "${n.label}" is not reachable from the start of the workflow.`);
        }
    }

    // No path to End: step nodes from which End is not reachable.
    const reverseChildren = parents;
    const reachableFromEnd = bfsReachable(endNodes, reverseChildren);
    for (const n of graph.nodes)
    {
        if (n.kind === "STEP" && !reachableFromEnd.has(n.id))
        {
            warnings.push(`Step "${n.label}" has no path to the end of the workflow.`);
        }
    }

    // Cycles: detect using DFS.
    const cycleNodes = detectCycleNodes(graph.nodes.map(n => n.id), children);
    if (cycleNodes.size > 0)
    {
        const labels = graph.nodes
            .filter(n => cycleNodes.has(n.id))
            .map(n => `"${n.label}"`)
            .join(", ");
        warnings.push(`Cycle detected involving: ${labels}.`);
    }

    // Ambiguous transitions: a single source with two or more outgoing edges
    // that share the same non-default outcome cannot be interpreted reliably.
    // Distinct outcomes pointing at the same target are NOT ambiguous and are
    // intentionally preserved by the adapter.
    const outcomesBySource = new Map<string, Map<string, number>>();
    for (const e of graph.edges)
    {
        if (e.outcome === "DEFAULT") continue;
        if (!outcomesBySource.has(e.source)) outcomesBySource.set(e.source, new Map());
        const byOutcome = outcomesBySource.get(e.source)!;
        byOutcome.set(e.outcome, (byOutcome.get(e.outcome) ?? 0) + 1);
    }
    for (const [sourceId, byOutcome] of outcomesBySource)
    {
        for (const [outcome, count] of byOutcome)
        {
            if (count > 1)
            {
                const label = graph.nodes.find(n => n.id === sourceId)?.label ?? sourceId;
                warnings.push(
                    `Step "${label}" has ${count} conflicting "${outcome}" transitions.`,
                );
            }
        }
    }

    // Invalid targets are already flagged by the adapter via INVALID_TARGET nodes.
    // No additional check needed here.

    return { warnings };
}

// ── Private helpers ───────────────────────────────────────────────────────────

function bfsReachable(
    sources: Set<string>,
    adjacency: Map<string, Set<string>>,
): Set<string>
{
    const visited = new Set<string>(sources);
    const queue = [...sources];
    while (queue.length > 0)
    {
        const current = queue.shift()!;
        for (const next of adjacency.get(current) ?? [])
        {
            if (!visited.has(next))
            {
                visited.add(next);
                queue.push(next);
            }
        }
    }
    return visited;
}

function detectCycleNodes(
    nodeIds: string[],
    children: Map<string, Set<string>>,
): Set<string>
{
    const WHITE = 0, GRAY = 1, BLACK = 2;
    const color = new Map<string, number>();
    const cycleNodes = new Set<string>();

    for (const id of nodeIds) color.set(id, WHITE);

    function dfs(id: string): boolean
    {
        color.set(id, GRAY);
        for (const child of children.get(id) ?? [])
        {
            if (color.get(child) === GRAY)
            {
                cycleNodes.add(id);
                cycleNodes.add(child);
                return true;
            }
            if (color.get(child) === WHITE)
            {
                if (dfs(child))
                {
                    cycleNodes.add(id);
                    return true;
                }
            }
        }
        color.set(id, BLACK);
        return false;
    }

    for (const id of nodeIds)
    {
        if (color.get(id) === WHITE) dfs(id);
    }

    return cycleNodes;
}
