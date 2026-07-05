import {lazy, Suspense} from "react";
import {Spinner} from "@fluentui/react-components";
import {WorkflowGraph, WorkflowGraphDirection, WorkflowGraphMode} from "./workflowGraphModels.ts";
import {WorkflowGraphErrorBoundary} from "./WorkflowGraphErrorBoundary.tsx";
import {useWorkflowGraphPreviewStyles} from "./WorkflowGraphPreviewStyles.tsx";

const WorkflowGraphCanvas = lazy(() => import("./WorkflowGraphCanvas.tsx"));

interface WorkflowGraphPreviewProps
{
    graph: WorkflowGraph;
    mode: WorkflowGraphMode;
    ariaLabel?: string;
    emptyMessage?: string;
    onViewTimeline?: () => void;
    /** Flow orientation; defaults to left-to-right (the workflow builder's wide preview). */
    direction?: WorkflowGraphDirection;
}

/**
 * Public, shared, read-only workflow graph renderer. Consumes any
 * `WorkflowGraph` and renders it through React Flow behind a code-split
 * boundary (Decision 20) and a rendering-error boundary (Decision 16). The
 * heavy graph library is only downloaded when a diagram is actually shown.
 */
export function WorkflowGraphPreview(props: WorkflowGraphPreviewProps)
{
    const {graph, mode, ariaLabel = "Workflow diagram", emptyMessage, onViewTimeline, direction} = props;
    const styles = useWorkflowGraphPreviewStyles();

    return (
        <WorkflowGraphErrorBoundary onViewTimeline={onViewTimeline}>
            <Suspense fallback={
                <div id="workflow-graph-preview-loading"
                     className={styles.loading}>
                    <Spinner id="workflow-graph-preview-spinner"
                             label="Loading diagram" />
                </div>
            }>
                <WorkflowGraphCanvas graph={graph}
                                     mode={mode}
                                     ariaLabel={ariaLabel}
                                     emptyMessage={emptyMessage}
                                     direction={direction} />
            </Suspense>
        </WorkflowGraphErrorBoundary>
    );
}
