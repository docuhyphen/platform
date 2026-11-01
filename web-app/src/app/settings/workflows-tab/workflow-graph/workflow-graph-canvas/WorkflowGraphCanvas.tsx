import "@xyflow/react/dist/style.css";

import {Background, ReactFlow} from "@xyflow/react";
import {mergeClasses} from "@fluentui/react-components";
import {WorkflowGraph, WorkflowGraphDirection, WorkflowGraphMode} from "../workflowGraphModels.ts";
import {WorkflowStepNode} from "../workflow-step-node/WorkflowStepNode.tsx";
import {WorkflowTerminalNode} from "../workflow-terminal-node/WorkflowTerminalNode.tsx";
import {WorkflowInvalidNode} from "../workflow-invalid-node/WorkflowInvalidNode.tsx";
import {WorkflowGraphLegend} from "../workflow-graph-legend/WorkflowGraphLegend.tsx";
import {WorkflowGraphEdge} from "../workflow-graph-edge/WorkflowGraphEdge.tsx";
import {useWorkflowGraphCanvasStyles} from "./WorkflowGraphCanvasStyles.tsx";
import {useWorkflowGraphCanvasView} from "./useWorkflowGraphCanvasView.ts";
import {useWorkflowGraphFlowElements} from "./useWorkflowGraphFlowElements.ts";
import {WorkflowGraphCanvasNotices} from "./workflow-graph-canvas-notices/WorkflowGraphCanvasNotices.tsx";
import {WorkflowGraphCanvasControls} from "./workflow-graph-canvas-controls/WorkflowGraphCanvasControls.tsx";
import {WorkflowGraphDirectionToggle} from "./workflow-graph-direction-toggle/WorkflowGraphDirectionToggle.tsx";

const NODE_TYPES = {
    step: WorkflowStepNode,
    terminal: WorkflowTerminalNode,
    invalid: WorkflowInvalidNode,
};

const EDGE_TYPES = {
    workflowEdge: WorkflowGraphEdge,
};

const READ_ONLY_ARIA_LABEL_CONFIG = {
    "node.a11yDescription.default": "Workflow step. Drag to reposition it in the diagram, or use the arrow keys to pan and the diagram controls to zoom.",
    "edge.a11yDescription.default": "Read only workflow connection.",
} as const;

const LARGE_NODE_COUNT = 50;
const LARGE_EDGE_COUNT = 100;

interface WorkflowGraphCanvasProps
{
    graph: WorkflowGraph;
    mode: WorkflowGraphMode;
    ariaLabel: string;
    emptyMessage?: string;
    direction?: WorkflowGraphDirection;
    fillHeight?: boolean;
    allowDirectionToggle?: boolean;
    directionToggleId?: string;
    onDirectionToggle?: (checked: boolean) => void;
}

export default function WorkflowGraphCanvas(props: WorkflowGraphCanvasProps)
{
    const {
        graph,
        mode,
        ariaLabel,
        emptyMessage = "This workflow has no steps.",
        direction = "LR",
        fillHeight = false,
        allowDirectionToggle = false,
        directionToggleId,
        onDirectionToggle,
    } = props;
    const styles = useWorkflowGraphCanvasStyles();
    const view = useWorkflowGraphCanvasView();
    const {nodes, edges} = useWorkflowGraphFlowElements(graph, direction, view.scheduleFitView);

    const isEmpty = !graph.nodes.some(n => n.kind === "STEP");
    const isLarge = graph.nodes.length > LARGE_NODE_COUNT || graph.edges.length > LARGE_EDGE_COUNT;

    return (
        <div id="workflow-graph-canvas"
             className={mergeClasses(styles.wrapper, fillHeight ? styles.wrapperFillHeight : undefined)}>
            <WorkflowGraphCanvasNotices warnings={graph.warnings}
                                        isEmpty={isEmpty}
                                        emptyMessage={emptyMessage}
                                        isLarge={isLarge} />

            <div id={"workflow-graph-canvas-div-1"}
                 ref={view.canvasContainerRef}
                 className={mergeClasses(
                     styles.canvasContainer,
                     fillHeight ? styles.canvasContainerFillHeight : undefined,
                     view.isEnlarged ? styles.canvasContainerEnlarged : undefined,
                     view.isFullscreen ? styles.canvasContainerFullscreen : undefined,
                 )}>
                <ReactFlow id="workflow-graph-reactflow"
                           nodes={nodes}
                           edges={edges}
                           nodeTypes={NODE_TYPES}
                           edgeTypes={EDGE_TYPES}
                           fitView={true}
                           onInit={view.registerInstance}
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
                    <WorkflowGraphCanvasControls isEnlarged={view.isEnlarged}
                                                 isFullscreen={view.isFullscreen}
                                                 onFitView={view.scheduleFitView}
                                                 onToggleEnlarged={view.toggleEnlarged}
                                                 onToggleFullscreen={view.toggleFullscreen} />
                </ReactFlow>
                {allowDirectionToggle && (
                    <WorkflowGraphDirectionToggle direction={direction}
                                                  directionToggleId={directionToggleId}
                                                  onDirectionToggle={onDirectionToggle} />
                )}
            </div>

            <WorkflowGraphLegend graph={graph}
                                 mode={mode} />
        </div>
    );
}
