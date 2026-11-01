import {useEffect, useMemo, useRef, useState} from "react";
import {Spinner} from "@fluentui/react-components";
import {WorkflowInstanceDetailDto} from "../../../../models/models.tsx";
import {buildInstanceGraph} from "../../../../settings/workflows-tab/workflow-graph/workflowInstanceGraphAdapter.ts";
import {WorkflowGraphPreview} from "../../../../settings/workflows-tab/workflow-graph/workflow-graph-preview/WorkflowGraphPreview.tsx";
import {WorkflowGraphErrorBoundary} from "../../../../settings/workflows-tab/workflow-graph/workflow-graph-error-boundary/WorkflowGraphErrorBoundary.tsx";
import {useWorkflowInstanceDiagramStyles} from "./WorkflowInstanceDiagramStyles.tsx";

interface Props
{
    instance: WorkflowInstanceDetailDto;
    onViewTimeline: () => void;
}

const InstanceGraphBody = ({instance, onViewTimeline}: Props) =>
{
    const graph = useMemo(() => buildInstanceGraph(instance), [instance]);
    return (
        <WorkflowGraphPreview graph={graph}
                              mode="INSTANCE_VIEW"
                              ariaLabel={`Workflow diagram for ${instance.definitionName ?? "workflow"}`}
                              onViewTimeline={onViewTimeline}
                              defaultDirection="TB"
                              allowDirectionToggle={true}
                              directionToggleId={`workflow-instance-orientation-toggle-${instance.id}`} />
    );
};

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
                <div id={"workflow-instance-diagram-div-1"}
                     className={styles.placeholder}>
                    <Spinner id={"workflow-instance-diagram-spinner-1"}
                             size="small"
                             label="Preparing diagram" />
                </div>
            )}
        </div>
    );
};

export default WorkflowInstanceDiagram;
