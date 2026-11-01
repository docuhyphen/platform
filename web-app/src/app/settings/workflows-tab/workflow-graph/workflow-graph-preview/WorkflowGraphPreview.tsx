import {lazy, Suspense, useEffect, useState} from "react";
import {Spinner} from "@fluentui/react-components";
import {WorkflowGraph, WorkflowGraphDirection, WorkflowGraphMode} from "../workflowGraphModels.ts";
import {WorkflowGraphErrorBoundary} from "../workflow-graph-error-boundary/WorkflowGraphErrorBoundary.tsx";
import {useWorkflowGraphPreviewStyles} from "./WorkflowGraphPreviewStyles.tsx";

const WorkflowGraphCanvas = lazy(() => import("../workflow-graph-canvas/WorkflowGraphCanvas.tsx"));

interface WorkflowGraphPreviewProps
{
    graph: WorkflowGraph;
    mode: WorkflowGraphMode;
    ariaLabel?: string;
    emptyMessage?: string;
    onViewTimeline?: () => void;
    defaultDirection?: WorkflowGraphDirection;
    allowDirectionToggle?: boolean;
    directionToggleId?: string;
    fillHeight?: boolean;
}

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
            <div id={"workflow-graph-preview-div-1"}
                 className={fillHeight ? `${styles.preview} ${styles.previewFillHeight}` : styles.preview}>
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
