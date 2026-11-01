import {useMemo} from "react";
import {mergeClasses} from "@fluentui/react-components";
import {legendSampleStateClass, useWorkflowGraphLegendStyles} from "./WorkflowGraphLegendStyles.tsx";
import {stepKindIcon} from "../workflowGraphPresentation.tsx";
import {nodeStatePresentation, stepKindLabel} from "../workflowGraphUtils.ts";
import {WorkflowGraph, WorkflowGraphMode, WorkflowGraphStepKind} from "../workflowGraphModels.ts";
import {CheckmarkIcon} from "../../../../components/IconBundles.tsx";

interface WorkflowGraphLegendProps
{
    graph: WorkflowGraph;
    mode: WorkflowGraphMode;
}

export function WorkflowGraphLegend({graph, mode}: WorkflowGraphLegendProps)
{
    const styles = useWorkflowGraphLegendStyles();

    const stepKinds = useMemo(() =>
    {
        const kinds = new Set<WorkflowGraphStepKind>();
        for (const n of graph.nodes)
        {
            if (n.kind === "STEP" && n.stepKind) kinds.add(n.stepKind);
        }
        return [...kinds];
    }, [graph]);

    const runtimeStates = useMemo(() =>
    {
        if (mode !== "INSTANCE_VIEW") return [];
        const states = new Set(graph.nodes
            .filter(n => n.kind === "STEP" && n.state !== "DEFINITION")
            .map(n => n.state));
        return [...states];
    }, [graph, mode]);

    if (stepKinds.length === 0 && runtimeStates.length === 0) return null;

    return (
        <section id="workflow-graph-legend"
                 aria-label="Diagram legend"
                 className={styles.legend}>
            {stepKinds.length > 0 ? (
                <div id={"workflow-graph-legend-div-1"}
                     className={styles.group}>
                    <span id={"workflow-graph-legend-span-1"}
                          className={styles.groupTitle}>Step types</span>
                    {stepKinds.map(kind =>
                    {
                        const Icon = stepKindIcon(kind);
                        return (
                            <span id={`workflow-graph-legend-kind-${kind}`}
                                  key={kind}
                                  className={styles.item}>
                                <Icon aria-hidden={true} />
                                {stepKindLabel(kind)}
                            </span>
                        );
                    })}
                </div>
            ) : null}
            {runtimeStates.length > 0 ? (
                <div id={"workflow-graph-legend-div-2"}
                     className={styles.group}>
                    <span id={"workflow-graph-legend-span-2"}
                          className={styles.groupTitle}>Status</span>
                    {runtimeStates.map(state => (
                        <span id={`workflow-graph-legend-state-${state}`}
                              key={state}
                              className={styles.item}>
                            {state === "COMPLETED" ? (
                                <CheckmarkIcon aria-hidden={true}
                                               className={styles.completedIcon} />
                            ) : (
                                <span id={"workflow-graph-legend-span-3"}
                                      aria-hidden={true}
                                      className={mergeClasses(styles.sample, legendSampleStateClass(styles, state))} />
                            )}
                            {nodeStatePresentation(state).label}
                        </span>
                    ))}
                </div>
            ) : null}
        </section>
    );
}
