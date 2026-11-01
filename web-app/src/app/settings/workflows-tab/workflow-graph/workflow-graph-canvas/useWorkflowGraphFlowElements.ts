import {useEffect, useRef} from "react";
import {useEdgesState, useNodesState} from "@xyflow/react";
import {WorkflowGraph, WorkflowGraphDirection} from "../workflowGraphModels.ts";
import {toReactFlow} from "../workflowGraphToReactFlow.ts";

export function useWorkflowGraphFlowElements(graph: WorkflowGraph, direction: WorkflowGraphDirection, onSynced: () => void)
{
    const initialGraphRef = useRef(toReactFlow(graph, direction));
    const [nodes, setNodes] = useNodesState(initialGraphRef.current.nodes);
    const [edges, setEdges] = useEdgesState(initialGraphRef.current.edges);

    useEffect(() =>
    {
        const next = toReactFlow(graph, direction);
        setNodes(next.nodes);
        setEdges(next.edges);
        onSynced();
    }, [graph, direction, onSynced, setNodes, setEdges]);

    return {nodes, edges};
}
