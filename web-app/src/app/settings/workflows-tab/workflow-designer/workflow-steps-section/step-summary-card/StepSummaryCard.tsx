import {Badge, Card, mergeClasses, Text} from "@fluentui/react-components";
import {WorkflowStepSpecDraft, WorkflowStepType} from "../../../../../models/models.tsx";
import {stepKindIcon} from "../../../workflow-graph/workflowGraphPresentation.tsx";
import {useStepSummaryCardStyles} from "./StepSummaryCardStyles.tsx";
import {humanizeConditionExpression} from "../../../step-card/condition-expression/conditionExpression.ts";
import {formatTriggerName} from "../../../workflowUtils.ts";

const STEP_TYPE_LABELS: Record<WorkflowStepType, string> = {
    APPROVAL: "Approval",
    NOTIFICATION: "Notification",
    CONDITION: "Condition",
    ACTION: "Action",
    WAIT_FOR_COUNTERPARTY_CLEARANCE: "Wait for Counterparty Clearance",
};

interface Props
{
    index: number;
    step: WorkflowStepSpecDraft;
    isNew: boolean;
    hasRouteWarning?: boolean;
    onClick: () => void;
}

const getStepDetail = (step: WorkflowStepSpecDraft): string =>
{
    if (step.type === "APPROVAL")
    {
        const assigneeCount = step.assignees.length;
        return `${assigneeCount} ${assigneeCount === 1 ? "assignee" : "assignees"}, ${step.quorum.kind.toLowerCase().replaceAll("_", " ")} quorum`;
    }

    if (step.type === "NOTIFICATION")
    {
        const assigneeCount = step.assignees.length;
        return `${assigneeCount} ${assigneeCount === 1 ? "recipient" : "recipients"}${step.communicationId ? ", communication selected" : ""}`;
    }

    if (step.type === "CONDITION")
    {
        return humanizeConditionExpression(step.predicateExpression) ?? "Condition not configured";
    }
    if (step.type === "ACTION")
    {
        return step.actionHandlerKey ? formatTriggerName(step.actionHandlerKey) : "Action not configured";
    }
    return "Waits for the other party's workflows";
};

const StepSummaryCard = ({index, step, isNew, hasRouteWarning = false, onClick}: Props) =>
{
    const styles = useStepSummaryCardStyles();
    const StepIcon = stepKindIcon(step.type);

    return (
        <Card
            id={`workflow-step-summary-card-${index}`}
            className={mergeClasses(styles.card, isNew && styles.newCard)}
            appearance="outline"
            onClick={onClick}
            tabIndex={0}
            role="button"
            aria-label={`Edit step ${index + 1}: ${step.name || STEP_TYPE_LABELS[step.type]}`}
            onKeyDown={event =>
            {
                if (event.key === "Enter" || event.key === " ")
                {
                    event.preventDefault();
                    onClick();
                }
            }}
        >
            <div
                id={`workflow-step-summary-heading-${index}`}
                className={styles.firstRow}
            >
                <StepIcon
                    id={`workflow-step-summary-icon-${index}`}
                    className={styles.stepIcon}
                    aria-hidden={true}
                />
                <Text
                    id={`workflow-step-summary-number-${index}`}
                    size={200}
                    className={styles.stepNumber}
                >
                    Step {index + 1}
                </Text>
                <Badge
                    id={`workflow-step-summary-type-${index}`}
                    className={styles.typeBadge}
                    appearance="outline"
                    size={"small"}
                    color="informative"
                >
                    {STEP_TYPE_LABELS[step.type]}
                </Badge>
                {hasRouteWarning && (
                    <Badge
                        id={`workflow-step-summary-route-warning-${index}`}
                        appearance="filled"
                        size={"small"}
                        color="warning"
                        aria-label={`Step ${index + 1} routes to a step that no longer exists`}
                    >
                        Invalid route
                    </Badge>
                )}
            </div>
            <Text
                id={`workflow-step-summary-name-${index}`}
                weight="semibold"
                className={styles.name}
            >
                {step.name || "Unnamed step"}
            </Text>
            <Text
                id={`workflow-step-summary-detail-${index}`}
                size={200}
                className={styles.detail}
            >
                {getStepDetail(step)}
            </Text>
        </Card>
    );
};

export default StepSummaryCard;
