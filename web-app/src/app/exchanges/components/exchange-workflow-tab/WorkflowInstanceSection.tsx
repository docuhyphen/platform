import {WorkflowInstanceDetailDto} from "../../../models/models.tsx";
import {PendingWorkflowStep} from "../../../../services/types/dtos.ts";
import {useExchangeWorkflowTabStyles} from "./ExchangeWorkflowTabStyles.tsx";
import RejectionBanner from "./RejectionBanner.tsx";
import WorkflowSummaryCard from "./WorkflowSummaryCard.tsx";
import ActionRequiredCard from "./ActionRequiredCard.tsx";
import WorkflowTimeline from "./WorkflowTimeline.tsx";
import WorkflowInstanceDiagram from "./workflow-instance-diagram/WorkflowInstanceDiagram.tsx";
import {WorkflowViewMode} from "./workflow-view-toggle/WorkflowViewToggle.tsx";

interface Props
{
    instance: WorkflowInstanceDetailDto;
    pendingSteps: PendingWorkflowStep[];
    onDecisionMade: () => void;
    viewMode: WorkflowViewMode;
    onViewTimeline: () => void;
}

/**
 * One workflow instance. The banner, summary, and action-required decision
 * controls stay unchanged in every view; only the detail region below them
 * changes: full-width Timeline, full-width Diagram, or a
 * side-by-side split showing both at once (detail on the left, diagram
 * preview on the right).
 */
const WorkflowInstanceSection = ({instance, pendingSteps, onDecisionMade, viewMode, onViewTimeline}: Props) =>
{
    const styles = useExchangeWorkflowTabStyles();
    const pendingStep = pendingSteps.find(p => p.workflowInstanceId === instance.id);

    const detail = (
        <>
            <RejectionBanner instance={instance} />
            <WorkflowSummaryCard instance={instance} />
            {pendingStep && (
                <ActionRequiredCard instance={instance}
                                    pendingStep={pendingStep}
                                    onDecisionMade={onDecisionMade} />
            )}
            {viewMode !== "BOTH" && (viewMode === "DIAGRAM" ? (
                <WorkflowInstanceDiagram instance={instance}
                                         onViewTimeline={onViewTimeline} />
            ) : (
                <WorkflowTimeline instance={instance} />
            ))}
        </>
    );

    if (viewMode !== "BOTH")
    {
        return (
            <div id={`workflow-instance-section-${instance.id}`}
                 className={styles.instanceSection}>
                {detail}
            </div>
        );
    }

    return (
        <div id={`workflow-instance-section-${instance.id}`}
             className={styles.instanceSectionSplit}>
            <div id={`workflow-instance-section-${instance.id}-detail`}
                 className={styles.splitDetailColumn}>
                {detail}
                <WorkflowTimeline instance={instance} />
            </div>
            <div id={`workflow-instance-section-${instance.id}-diagram`}
                 className={styles.splitDiagramColumn}>
                <WorkflowInstanceDiagram instance={instance}
                                         onViewTimeline={onViewTimeline} />
            </div>
        </div>
    );
};

export default WorkflowInstanceSection;
