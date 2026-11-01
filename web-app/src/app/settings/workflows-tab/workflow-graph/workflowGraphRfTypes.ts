import {Edge, EdgeProps, Node, NodeProps} from "@xyflow/react";
import {WorkflowGraphDirection, WorkflowGraphNode} from "./workflowGraphModels.ts";

export type WorkflowRFNodeType = "step" | "terminal" | "invalid";

export type WorkflowRFNodeData = { node: WorkflowGraphNode; direction: WorkflowGraphDirection };

export type WorkflowRFNode = Node<WorkflowRFNodeData, WorkflowRFNodeType>;

export type WorkflowRFNodeProps = NodeProps<WorkflowRFNode>;

export type WorkflowRFEdgeType = "workflowEdge";

export type WorkflowRFEdgeData = { centerX?: number; centerY?: number };

export type WorkflowRFEdge = Edge<WorkflowRFEdgeData, WorkflowRFEdgeType>;

export type WorkflowRFEdgeProps = EdgeProps<WorkflowRFEdge>;
