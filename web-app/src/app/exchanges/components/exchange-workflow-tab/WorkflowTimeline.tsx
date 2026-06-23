import { useState } from "react";
import { Accordion, AccordionToggleData } from "@fluentui/react-components";
import { WorkflowInstanceDetailDto } from "../../../models/models.tsx";
import { useExchangeWorkflowTabStyles } from "./ExchangeWorkflowTabStyles.tsx";
import WorkflowTimelineItem from "./WorkflowTimelineItem.tsx";

interface Props
{
    instance: WorkflowInstanceDetailDto;
}

const WorkflowTimeline = ({ instance }: Props) =>
{
    const styles = useExchangeWorkflowTabStyles();

    const activeStep = instance.steps.find(
        s => s.stepIndex === instance.currentStepIndex && s.status === "PENDING",
    );
    const [openItems, setOpenItems] = useState<string[]>(
        activeStep ? [activeStep.id] : [],
    );

    return (
        <div className={styles.timeline}>
            <Accordion
                openItems={openItems}
                onToggle={(_: unknown, data: AccordionToggleData<string>) =>
                    setOpenItems(data.openItems as string[])
                }
                multiple
                collapsible
            >
                {instance.steps.map(step => (
                    <WorkflowTimelineItem
                        key={step.id}
                        step={step}
                        isActivePending={
                            step.stepIndex === instance.currentStepIndex &&
                            step.status === "PENDING" &&
                            instance.status === "RUNNING"
                        }
                    />
                ))}
            </Accordion>
        </div>
    );
};

export default WorkflowTimeline;
