import "@xyflow/react/dist/style.css";

import {useCallback, useEffect, useRef, useState} from "react";
import {Background, ControlButton, Controls, ReactFlow, ReactFlowInstance, useEdgesState, useNodesState} from "@xyflow/react";
import {mergeClasses, MessageBar, MessageBarBody, MessageBarTitle} from "@fluentui/react-components";
import {WorkflowGraph, WorkflowGraphDirection, WorkflowGraphMode} from "./workflowGraphModels.ts";
import {toReactFlow} from "./workflowGraphToReactFlow.ts";
import {WorkflowStepNode} from "./WorkflowStepNode.tsx";
import {WorkflowTerminalNode} from "./WorkflowTerminalNode.tsx";
import {WorkflowInvalidNode} from "./WorkflowInvalidNode.tsx";
import {WorkflowGraphLegend} from "./WorkflowGraphLegend.tsx";
import {useWorkflowGraphCanvasStyles} from "./WorkflowGraphCanvasStyles.tsx";
import {FullScreenEnterIcon, FullScreenExitIcon} from "../../../components/IconBundles.tsx";
import {WorkflowGraphEdge} from "./WorkflowGraphEdge.tsx";

const NODE_TYPES = {
    step: WorkflowStepNode,
    terminal: WorkflowTerminalNode,
    invalid: WorkflowInvalidNode,
};

const EDGE_TYPES = {
    workflowEdge: WorkflowGraphEdge,
};

/**
 * Accessibility descriptions for the diagram. Nodes may be dragged to
 * reorganize the layout for easier viewing, but the underlying workflow data
 * (connections, step definitions) is never mutated, so connect/delete
 * instructions are replaced with wording describing the drag-to-reposition
 * behaviour instead.
 */
const READ_ONLY_ARIA_LABEL_CONFIG = {
    "node.a11yDescription.default": "Workflow step. Drag to reposition it in the diagram, or use the arrow keys to pan and the diagram controls to zoom.",
    "edge.a11yDescription.default": "Read only workflow connection.",
} as const;

/**
 * Practical rendering threshold (Phase 6 / Decision on performance). Beyond this
 * the diagram still renders, but the Timeline view is recommended for smoother
 * interaction rather than hiding any workflow data.
 */
const LARGE_NODE_COUNT = 50;
const LARGE_EDGE_COUNT = 100;

interface WorkflowGraphCanvasProps
{
    graph: WorkflowGraph;
    mode: WorkflowGraphMode;
    ariaLabel: string;
    emptyMessage?: string;
    /** Flow orientation; defaults to left-to-right (the workflow builder's wide preview). */
    direction?: WorkflowGraphDirection;
}

/**
 * Read-only React Flow canvas. This module is the code-split boundary: it is the
 * only place that imports `@xyflow/react` and its base stylesheet, so the graph
 * library stays out of the main bundle (Decision 20). The workflow data itself
 * (steps, connections) can never be mutated here, but nodes are draggable so
 * users can reorganize the layout of the diagram for easier viewing.
 */
export default function WorkflowGraphCanvas(props: WorkflowGraphCanvasProps)
{
    const {graph, mode, ariaLabel, emptyMessage = "This workflow has no steps.", direction = "LR"} = props;
    const styles = useWorkflowGraphCanvasStyles();
    const canvasContainerRef = useRef<HTMLDivElement | null>(null);
    const reactFlowInstanceRef = useRef<ReactFlowInstance | null>(null);
    const [isFullscreen, setIsFullscreen] = useState(false);

    // Nodes/edges are owned as local React Flow state (not derived via useMemo)
    // so that `onNodesChange` can apply drag-position updates. Without this,
    // React Flow has nothing to write dragged positions back to, and every
    // re-render of this component (e.g. toggling full screen) would reset the
    // diagram to its computed layout, making dragging appear broken.
    const initialGraphRef = useRef(toReactFlow(graph, direction));
    const [nodes, setNodes, onNodesChange] = useNodesState(initialGraphRef.current.nodes);
    const [edges, setEdges, onEdgesChange] = useEdgesState(initialGraphRef.current.edges);

    // Recompute the layout whenever the underlying graph (or its orientation)
    // changes, discarding any manual repositioning from the previous graph.
    useEffect(() =>
    {
        const next = toReactFlow(graph, direction);
        setNodes(next.nodes);
        setEdges(next.edges);
    }, [graph, direction, setNodes, setEdges]);

    const isEmpty = !graph.nodes.some(n => n.kind === "STEP");
    const isLarge = graph.nodes.length > LARGE_NODE_COUNT || graph.edges.length > LARGE_EDGE_COUNT;

    // Keep the button state in sync with real browser fullscreen state so it
    // stays correct if the user exits fullscreen with Escape instead of the button.
    // Both entering and exiting fullscreen change the canvas's on-screen size
    // (growing to fill the whole screen, or shrinking back to its normal
    // in-page size), so the previous zoom/pan no longer frames the diagram
    // correctly either way; re-fit it to the new viewport in both directions.
    useEffect(() =>
    {
        const handleFullscreenChange = () =>
        {
            const nowFullscreen = document.fullscreenElement === canvasContainerRef.current;
            setIsFullscreen(nowFullscreen);
            // Wait a tick for the container to resize before fitting, so
            // React Flow measures its final bounds.
            requestAnimationFrame(() =>
            {
                reactFlowInstanceRef.current?.fitView();
            });
        };
        document.addEventListener("fullscreenchange", handleFullscreenChange);
        return () => document.removeEventListener("fullscreenchange", handleFullscreenChange);
    }, []);

    const toggleFullscreen = useCallback(async () =>
    {
        try
        {
            if (document.fullscreenElement)
            {
                await document.exitFullscreen();
            }
            else if (canvasContainerRef.current)
            {
                await canvasContainerRef.current.requestFullscreen();
            }
        }
        catch (err)
        {
            console.warn("Fullscreen request failed:", err);
        }
    }, []);

    return (
        <div id="workflow-graph-canvas"
             className={styles.wrapper}>
            {graph.warnings.length > 0 ? (
                <MessageBar id="workflow-graph-warnings"
                            intent="warning">
                    <MessageBarBody>
                        <MessageBarTitle>Diagram notices</MessageBarTitle>
                        <ul className={styles.warningList}>
                            {graph.warnings.map((warning, index) => (
                                <li key={index}>{warning}</li>
                            ))}
                        </ul>
                    </MessageBarBody>
                </MessageBar>
            ) : null}

            {isEmpty ? (
                <MessageBar id="workflow-graph-empty"
                            intent="info">
                    <MessageBarBody>{emptyMessage}</MessageBarBody>
                </MessageBar>
            ) : null}

            {isLarge ? (
                <MessageBar id="workflow-graph-large"
                            intent="info">
                    <MessageBarBody>
                        This workflow is large. The Timeline view may be easier to follow.
                    </MessageBarBody>
                </MessageBar>
            ) : null}

            <div ref={canvasContainerRef}
                 className={mergeClasses(styles.canvasContainer, isFullscreen ? styles.canvasContainerFullscreen : undefined)}>
                <ReactFlow id="workflow-graph-reactflow"
                           nodes={nodes}
                           edges={edges}
                           nodeTypes={NODE_TYPES}
                           edgeTypes={EDGE_TYPES}
                           fitView={true}
                           onInit={instance => { reactFlowInstanceRef.current = instance; }}
                           minZoom={0.2}
                           maxZoom={1.75}
                           nodesDraggable={true}
                           nodesConnectable={false}
                           nodesFocusable={true}
                           edgesFocusable={true}
                           elementsSelectable={true}
                           deleteKeyCode={null}
                           selectionKeyCode={null}
                           multiSelectionKeyCode={null}
                           disableKeyboardA11y={true}
                           ariaLabelConfig={READ_ONLY_ARIA_LABEL_CONFIG}
                           proOptions={{hideAttribution: true}}
                           aria-label={ariaLabel}>
                    <Background id="workflow-graph-background" />
                    <Controls id="workflow-graph-controls"
                              showInteractive={false}>
                        <ControlButton id="workflow-graph-fullscreen-btn"
                                       onClick={toggleFullscreen}
                                       title={isFullscreen ? "Exit full screen" : "Full screen"}
                                       aria-label={isFullscreen ? "Exit full screen" : "Full screen"}>
                            {isFullscreen ? <FullScreenExitIcon /> : <FullScreenEnterIcon />}
                        </ControlButton>
                    </Controls>
                </ReactFlow>
            </div>

            <WorkflowGraphLegend graph={graph}
                                 mode={mode} />
        </div>
    );
}
