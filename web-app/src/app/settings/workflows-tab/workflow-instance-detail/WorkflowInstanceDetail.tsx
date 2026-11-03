import {useEffect, useState} from "react";
import {DrawerBody, DrawerHeader, DrawerHeaderTitle, OverlayDrawer, Spinner, Text} from "@fluentui/react-components";
import {WorkflowInstanceDetailDto} from "../../../models/models.tsx";
import {getWorkflowInstanceDetail} from "../../../../services/workflowService.ts";
import {useWorkflowInstanceDetailStyles} from "./WorkflowInstanceDetailStyles.tsx";
import {INSTANCE_FAILURE_MESSAGES, INSTANCE_STATUS_LABELS} from "../workflowUtils.ts";
import StepTimelineItem from "./step-timeline-item/StepTimelineItem.tsx";

interface Props
{
    instanceId: string | null;
    onDismiss: () => void;
}

const WorkflowInstanceDetail = ({instanceId, onDismiss}: Props) =>
{
    const styles = useWorkflowInstanceDetailStyles();
    const [detail, setDetail] = useState<WorkflowInstanceDetailDto | null>(null);
    const [loading, setLoading] = useState(false);

    useEffect(() =>
    {
        if (!instanceId) return;
        setLoading(true);
        setDetail(null);
        getWorkflowInstanceDetail(instanceId)
            .then(setDetail)
            .catch((): undefined => undefined)
            .finally(() => setLoading(false));
    }, [instanceId]);

    return (
        <OverlayDrawer id={"workflow-instance-detail-overlay-drawer-1"}
                       open={!!instanceId}
                       onOpenChange={(_, {open}) => !open && onDismiss()}
                       position="end">
            <DrawerHeader id={"workflow-instance-detail-drawer-header-1"}>
                <DrawerHeaderTitle id={"workflow-instance-detail-drawer-header-title-1"}>Workflow Instance</DrawerHeaderTitle>
            </DrawerHeader>
            <DrawerBody id={"workflow-instance-detail-drawer-body-1"}>
                {loading && <Spinner id={"workflow-instance-detail-spinner-1"}
                                     size="small"
                                     label="Loading..."/>}
                {detail && !loading && (
                    <>
                        <Text id={"workflow-instance-detail-text-8"}
                              weight="semibold"
                              size={400}
                              block>{detail.definitionName}</Text>
                        {detail.exchangeName && (
                            <Text
                                id={"workflow-instance-detail-text-9"}
                                size={200}
                                block
                                className={styles.exchangeName}
                            >
                                Exchange: {detail.exchangeName}
                            </Text>
                        )}
                        <Text
                            id={"workflow-instance-detail-text-10"}
                            size={200}
                            block
                            className={styles.instanceStatus}
                        >
                            Status: {INSTANCE_STATUS_LABELS[detail.status] ?? detail.status}
                        </Text>
                        {detail.status === "FAILED" && detail.failureCode && (
                            <Text
                                id="workflow-instance-failure-message"
                                size={200}
                                block
                                className={styles.failureMessage}
                            >
                                {INSTANCE_FAILURE_MESSAGES[detail.failureCode] ?? detail.failureCode}
                            </Text>
                        )}
                        <div id={"workflow-instance-detail-div-5"}
                             className={styles.stepTimeline}>
                            {detail.steps.map(step => (
                                <StepTimelineItem
                                    key={step.id}
                                    step={step}
                                    isCurrent={step.stepIndex === detail.currentStepIndex && detail.status === "RUNNING"}
                                />
                            ))}
                        </div>
                    </>
                )}
            </DrawerBody>
        </OverlayDrawer>
    );
};

export default WorkflowInstanceDetail;
