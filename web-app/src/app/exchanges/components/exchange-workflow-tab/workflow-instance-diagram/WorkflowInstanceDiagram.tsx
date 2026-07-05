import {useEffect, useMemo, useRef, useState} from "react";
import {Spinner} from "@fluentui/react-components";
import {WorkflowInstanceDetailDto} from "../../../../models/models.tsx";
import {buildInstanceGraph} from "../../../../settings/workflows-tab/workflow-graph/workflowInstanceGraphAdapter.ts";
import {WorkflowGraphPreview} from "../../../../settings/workflows-tab/workflow-graph/WorkflowGraphPreview.tsx";
import {WorkflowGraphErrorBoundary} from "../../../../settings/workflows-tab/workflow-graph/WorkflowGraphErrorBoundary.tsx";
import {useWorkflowInstanceDiagramStyles} from "./WorkflowInstanceDiagramStyles.tsx";

interface Props
{
    instance: WorkflowInstanceDetailDto;
    onViewTimeline: () => void;
}

/**
 * Builds the frozen instance graph and renders it through the shared read-only
 * renderer. The adapter runs inside the graph error boundary (Decision 16) so a
 * malformed snapshot falls back to Timeline instead of throwing to the tab.
 * Rendered top-to-bottom so it reads naturally in the narrower side-by-side
 * preview column of the Exchange Workflow tab.
 */
const InstanceGraphBody = ({instance, onViewTimeline}: Props) =>
{
    const graph = useMemo(() => buildInstanceGraph(instance), [instance]);
    return (
        <WorkflowGraphPreview graph={graph}
                              mode="INSTANCE_VIEW"
                              ariaLabel={`Workflow diagram for ${instance.definitionName ?? "workflow"}`}
                              onViewTimeline={onViewTimeline}
                              direction="TB" />
    );
};

/**
 * Lazy-mounts each Exchange workflow diagram (Decision 14): the heavy graph
 * library and adapter only run once the section scrolls into view, so stacked
 * off-screen instances do not all mount eagerly. Each diagram is isolated by its
 * own error boundary that directs the user to Timeline.
 */
const WorkflowInstanceDiagram = ({instance, onViewTimeline}: Props) =>
{
    const styles = useWorkflowInstanceDiagramStyles();
    const containerRef = useRef<HTMLDivElement | null>(null);
    const [hasMounted, setHasMounted] = useState(false);

    useEffect(() =>
    {
        if (hasMounted) return;
        const node = containerRef.current;
        if (!node) return;
        if (typeof IntersectionObserver === "undefined")
        {
            setHasMounted(true);
            return;
        }
        const observer = new IntersectionObserver(entries =>
        {
            if (entries.some(entry => entry.isIntersecting))
            {
                setHasMounted(true);
                observer.disconnect();
            }
        }, {rootMargin: "200px"});
        observer.observe(node);
        return () => observer.disconnect();
    }, [hasMounted]);

    return (
        <div id={`workflow-instance-diagram-${instance.id}`}
             ref={containerRef}
             className={styles.container}>
            {hasMounted ? (
                <WorkflowGraphErrorBoundary onViewTimeline={onViewTimeline}>
                    <InstanceGraphBody instance={instance}
                                       onViewTimeline={onViewTimeline} />
                </WorkflowGraphErrorBoundary>
            ) : (
                <div className={styles.placeholder}>
                    <Spinner size="small"
                             label="Preparing diagram" />
                </div>
            )}
        </div>
    );
};

export default WorkflowInstanceDiagram;
