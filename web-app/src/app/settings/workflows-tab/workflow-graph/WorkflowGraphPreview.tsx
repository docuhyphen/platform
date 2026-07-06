import {lazy, Suspense, useEffect, useState} from "react";
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
    /** Starting orientation for the read-only diagram layout. */
    defaultDirection?: WorkflowGraphDirection;
    allowDirectionToggle?: boolean;
    directionToggleId?: string;
    fillHeight?: boolean;
}

/**
 * Public, shared, read-only workflow graph renderer. Consumes any
 * `WorkflowGraph` and renders it through React Flow behind a code-split
 * boundary (Decision 20) and a rendering-error boundary (Decision 16). The
 * heavy graph library is only downloaded when a diagram is actually shown.
 */
export function WorkflowGraphPreview(props: WorkflowGraphPreviewProps)
{
    const {
        graph,
        mode,
        ariaLabel = "Workflow diagram",
        emptyMessage,
        onViewTimeline,
        defaultDirection = "LR",
        allowDirectionToggle = false,
        directionToggleId,
        fillHeight = false,
    } = props;
    const styles = useWorkflowGraphPreviewStyles();
    const [direction, setDirection] = useState<WorkflowGraphDirection>(defaultDirection);

    useEffect(() =>
    {
        setDirection(defaultDirection);
    }, [defaultDirection]);

    return (
        <WorkflowGraphErrorBoundary onViewTimeline={onViewTimeline}>
            <div className={fillHeight ? `${styles.preview} ${styles.previewFillHeight}` : styles.preview}>
                {allowDirectionToggle && (
                    null
                )}
                <Suspense
                    fallback={
                        <div id="workflow-graph-preview-loading"
                             className={styles.loading}>
                            <Spinner id="workflow-graph-preview-spinner"
                                     label="Loading diagram" />
                        </div>
                    }
                >
                    <WorkflowGraphCanvas graph={graph}
                                         mode={mode}
                                         ariaLabel={ariaLabel}
                                         emptyMessage={emptyMessage}
                                         direction={direction}
                                         fillHeight={fillHeight}
                                         allowDirectionToggle={allowDirectionToggle}
                                         directionToggleId={directionToggleId}
                                         onDirectionToggle={checked => setDirection(checked ? "TB" : "LR")} />
                </Suspense>
            </div>
        </WorkflowGraphErrorBoundary>
    );
}
