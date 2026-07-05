import { describe, expect, it } from "vitest";
import { layoutWorkflowGraph, stepNodeDimensions, terminalNodeDimensions } from "../workflowGraphLayout.ts";
import { WorkflowGraphLayoutEdge, WorkflowGraphLayoutNode } from "../workflowGraphModels.ts";

// ── helpers ───────────────────────────────────────────────────────────────────

const step = (id: string): WorkflowGraphLayoutNode => ({
    id,
    ...stepNodeDimensions(),
});

const terminal = (id: string): WorkflowGraphLayoutNode => ({
    id,
    ...terminalNodeDimensions(),
});

const edge = (source: string, target: string): WorkflowGraphLayoutEdge => ({
    id: `${source}->${target}`,
    source,
    target,
});

// ── empty input ───────────────────────────────────────────────────────────────

describe("layoutWorkflowGraph: empty input", () =>
{
    it("returns an empty position map for zero nodes", () =>
    {
        const layout = layoutWorkflowGraph([], []);
        expect(layout.positions.size).toBe(0);
    });
});

// ── single node ───────────────────────────────────────────────────────────────

describe("layoutWorkflowGraph: single node", () =>
{
    it("places a single node at an assigned position", () =>
    {
        const layout = layoutWorkflowGraph([step("a")], []);
        expect(layout.positions.has("a")).toBe(true);
    });
});

// ── linear chain ─────────────────────────────────────────────────────────────

describe("layoutWorkflowGraph: linear chain", () =>
{
    it("assigns non-decreasing x positions left to right", () =>
    {
        const nodes = [terminal("start"), step("s0"), terminal("end")];
        const edges = [edge("start", "s0"), edge("s0", "end")];
        const layout = layoutWorkflowGraph(nodes, edges);

        const xStart = layout.positions.get("start")!.x;
        const xStep = layout.positions.get("s0")!.x;
        const xEnd = layout.positions.get("end")!.x;

        expect(xStart).toBeLessThan(xStep);
        expect(xStep).toBeLessThan(xEnd);
    });
});

// ── determinism ───────────────────────────────────────────────────────────────

describe("layoutWorkflowGraph: determinism", () =>
{
    it("returns identical positions for identical input regardless of insertion order", () =>
    {
        const nodes = [terminal("start"), step("s0"), step("s1"), terminal("end")];
        const edgesA = [edge("start", "s0"), edge("s0", "s1"), edge("s1", "end")];
        const edgesB = [edge("s1", "end"), edge("start", "s0"), edge("s0", "s1")];

        const layoutA = layoutWorkflowGraph(nodes, edgesA);
        const layoutB = layoutWorkflowGraph(nodes, edgesB);

        for (const [id, posA] of layoutA.positions)
        {
            const posB = layoutB.positions.get(id)!;
            expect(posA.x).toBe(posB.x);
            expect(posA.y).toBe(posB.y);
        }
    });

    it("positions are stable when only runtime metadata would change (topology unchanged)", () =>
    {
        const nodes = [terminal("start"), step("s0"), terminal("end")];
        const edges = [edge("start", "s0"), edge("s0", "end")];

        const layout1 = layoutWorkflowGraph(nodes, edges);
        const layout2 = layoutWorkflowGraph(nodes, edges);

        for (const [id, pos1] of layout1.positions)
        {
            const pos2 = layout2.positions.get(id)!;
            expect(pos1.x).toBe(pos2.x);
            expect(pos1.y).toBe(pos2.y);
        }
    });
});

// ── branches ─────────────────────────────────────────────────────────────────

describe("layoutWorkflowGraph: branching", () =>
{
    it("handles condition branches without crashing", () =>
    {
        const nodes = [terminal("start"), step("cond"), step("a"), step("b"), terminal("end")];
        const edges = [
            edge("start", "cond"),
            edge("cond", "a"),
            edge("cond", "b"),
            edge("a", "end"),
            edge("b", "end"),
        ];
        const layout = layoutWorkflowGraph(nodes, edges);
        expect(layout.positions.size).toBe(nodes.length);
    });
});

// ── backward edge (cycle) ─────────────────────────────────────────────────────

describe("layoutWorkflowGraph: backward edge", () =>
{
    it("does not crash when a backward edge exists", () =>
    {
        const nodes = [terminal("start"), step("s0"), step("s1"), terminal("end")];
        const edges = [
            edge("start", "s0"),
            edge("s0", "s1"),
            edge("s1", "s0"), // backward
            edge("s1", "end"),
        ];
        expect(() => layoutWorkflowGraph(nodes, edges)).not.toThrow();
    });
});

// ── unreachable nodes ─────────────────────────────────────────────────────────

describe("layoutWorkflowGraph: unreachable nodes", () =>
{
    it("still assigns positions to nodes not reachable from sources", () =>
    {
        const nodes = [terminal("start"), step("reachable"), step("orphan"), terminal("end")];
        const edges = [edge("start", "reachable"), edge("reachable", "end")];
        const layout = layoutWorkflowGraph(nodes, edges);
        expect(layout.positions.has("orphan")).toBe(true);
    });
});

// ── crossing reduction ───────────────────────────────────────────────────────

describe("layoutWorkflowGraph: crossing reduction", () =>
{
    it("reorders a rank by barycenter so parallel connectors do not cross", () =>
    {
        // Rank 0 keeps its id-sorted order: a (top), b (bottom).
        // With id-sorted rank 1 (x, y) the edges a->y and b->x would cross.
        // The barycenter sweep should reorder rank 1 to [y, x] so a lines up
        // with y and b lines up with x, matching their vertical order.
        const nodes = [step("a"), step("b"), step("x"), step("y")];
        const edges = [edge("a", "y"), edge("b", "x")];
        const layout = layoutWorkflowGraph(nodes, edges);

        const yA = layout.positions.get("a")!.y;
        const yB = layout.positions.get("b")!.y;
        const yX = layout.positions.get("x")!.y;
        const yY = layout.positions.get("y")!.y;

        expect(yA).toBeLessThan(yB);
        expect(yY).toBeLessThan(yX);
    });

    it("stays deterministic regardless of input order even with crossing reduction", () =>
    {
        const nodesA = [step("a"), step("b"), step("x"), step("y")];
        const nodesB = [step("y"), step("x"), step("b"), step("a")];
        const edgesA = [edge("a", "y"), edge("b", "x")];
        const edgesB = [edge("b", "x"), edge("a", "y")];

        const layoutA = layoutWorkflowGraph(nodesA, edgesA);
        const layoutB = layoutWorkflowGraph(nodesB, edgesB);

        for (const [id, posA] of layoutA.positions)
        {
            const posB = layoutB.positions.get(id)!;
            expect(posA.x).toBe(posB.x);
            expect(posA.y).toBe(posB.y);
        }
    });
});
