import {
    ArrowForwardFilled,
    CheckmarkCircleFilled,
    CircleRegular,
    DismissCircleFilled,
    RecordFilled,
    WarningFilled,
} from "@fluentui/react-icons";
import {WorkflowStepInstanceDto} from "../../../../models/models.tsx";
import {useWorkflowStepIconStyles} from "./WorkflowStepIconStyles.tsx";

interface WorkflowStepIconProps
{
    step: WorkflowStepInstanceDto;
    isActivePending: boolean;
}

const WorkflowStepIcon = ({step, isActivePending}: WorkflowStepIconProps) =>
{
    const styles = useWorkflowStepIconStyles();
    if (step.status === "COMPLETED" || step.status === "APPROVED")
        return <CheckmarkCircleFilled className={styles.stepIconSuccess} />;
    if (step.status === "REJECTED")
        return <DismissCircleFilled className={styles.stepIconDanger} />;
    if (step.status === "ESCALATED")
        return <WarningFilled className={styles.stepIconWarning} />;
    if (step.status === "SKIPPED")
        return <ArrowForwardFilled className={styles.stepIconNeutral} />;
    if (step.status === "PENDING" && isActivePending)
        return <RecordFilled className={styles.stepIconBrand} />;
    return <CircleRegular className={styles.stepIconNeutral} />;
};

export default WorkflowStepIcon;
