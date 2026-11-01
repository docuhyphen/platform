import {AccordionHeader, AccordionItem, AccordionPanel, Badge, Text } from "@fluentui/react-components";
import { WorkflowStepInstanceDto } from "../../../models/models.tsx";
import { useExchangeWorkflowTabStyles } from "./ExchangeWorkflowTabStyles.tsx";
import { STEP_TYPE_LABELS } from "../../../settings/workflows-tab/workflowUtils.ts";
import {collapsedBadgeLabel, collapsedSummaryText, STEP_STATUS_COLORS} from "./workflowTimelineItemFormat.ts";
import WorkflowStepIcon from "./workflow-step-icon/WorkflowStepIcon.tsx";
import WorkflowStepDetail from "./workflow-step-detail/WorkflowStepDetail.tsx";

interface Props
{
    step: WorkflowStepInstanceDto;
    isActivePending: boolean;
}

const WorkflowTimelineItem = ({ step, isActivePending }: Props) =>
{
    const styles = useExchangeWorkflowTabStyles();
    const collapsedSummary = collapsedSummaryText(step, isActivePending);

    return (
        <AccordionItem id={"workflow-timeline-item-accordion-item-1"}
                       value={step.id}>
            <AccordionHeader id={"workflow-timeline-item-accordion-header-1"}
                             icon={<WorkflowStepIcon step={step}
                                                     isActivePending={isActivePending} />}>
                <div id={"workflow-timeline-item-div-1"}
                     className={styles.stepHeaderContent}>
                    <Text id={`workflow-step-title-${step.id}`}
                          size={300}
                          weight={isActivePending ? "semibold" : "regular"}
                          className={styles.stepHeaderTitle}>
                        Step {step.stepIndex + 1}: {STEP_TYPE_LABELS[step.stepType] ?? step.stepType}
                    </Text>
                    <div id={`workflow-step-meta-${step.id}`}
                         className={styles.stepHeaderMeta}>
                        {collapsedSummary && (
                            <Text id={`workflow-step-summary-${step.id}`}
                                  size={200}
                                  className={styles.stepHeaderMetaText}>
                                {collapsedSummary}
                            </Text>
                        )}
                        <Badge id={"workflow-timeline-item-badge-1"}
                               color={STEP_STATUS_COLORS[step.status] ?? "subtle"}
                               appearance="outline"
                               size="small">
                            {collapsedBadgeLabel(step, isActivePending)}
                        </Badge>
                    </div>
                </div>
            </AccordionHeader>
            <AccordionPanel id={"workflow-timeline-item-accordion-panel-1"}>
                <WorkflowStepDetail step={step} />
            </AccordionPanel>
        </AccordionItem>
    );
};

export default WorkflowTimelineItem;
