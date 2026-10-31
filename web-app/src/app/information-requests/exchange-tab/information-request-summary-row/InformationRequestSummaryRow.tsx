import {Badge, Button, ProgressBar, Spinner, Text} from "@fluentui/react-components";
import {InformationRequestNextAction, InformationRequestSummaryDto} from "../../../models/models.tsx";
import {requestStateLabels} from "../../operations/operationsLabels.ts";
import {executionStandingBadge} from "../../shared/executionStandingText.ts";
import {
    formatInformationRequestCount,
    formatInformationRequestTime
} from "../../shared/informationRequestFormatting.ts";
import {nextActionLabels, rolesSentence} from "../../shared/informationRequestLabels.ts";
import {useInformationRequestSummaryRowStyles} from "./InformationRequestSummaryRowStyles.tsx";

interface Props
{
    summary: InformationRequestSummaryDto;
    context?: string;
    opening: boolean;
    onOpen: (summary: InformationRequestSummaryDto) => void;
    onManage?: (summary: InformationRequestSummaryDto) => void;
}

const PRIMARY_ACTIONS = new Set([
    InformationRequestNextAction.COMPLETE_SETUP,
    InformationRequestNextAction.RESPOND,
    InformationRequestNextAction.REVIEW,
]);

const SECONDARY_MANAGE_ACTIONS = new Set([InformationRequestNextAction.RESPOND, InformationRequestNextAction.REVIEW]);

const InformationRequestSummaryRow = ({summary, context, opening, onOpen, onManage}: Props) =>
{
    const styles = useInformationRequestSummaryRowStyles();
    const actionLabel = nextActionLabels[summary.nextAction];
    const idPrefix = `information-request-summary-${summary.id}`;
    const standingBadge = executionStandingBadge(summary.executionStanding);
    const offersManage = Boolean(onManage) && summary.permissions.canManage && SECONDARY_MANAGE_ACTIONS.has(summary.nextAction);
    const openingIcon = opening
        ? (
            <Spinner id={`${idPrefix}-action-spinner`}
                     size={"tiny"}/>
        )
        : undefined;

    return (
        <li id={idPrefix}
            className={styles.row}>
            <div id={`${idPrefix}-details`}
                 className={styles.summary}>
                <div id={`${idPrefix}-heading`}
                     className={styles.heading}>
                    <Text id={`${idPrefix}-title`}
                          weight={"semibold"}
                          className={styles.title}>
                        {summary.title}
                    </Text>
                    <Badge id={`${idPrefix}-state`}
                           appearance={"tint"}
                           shape={"circular"}>
                        {requestStateLabels[summary.state]}
                    </Badge>
                    {standingBadge && (
                        <Badge id={`${idPrefix}-standing`}
                               appearance={"outline"}
                               color={"warning"}
                               shape={"circular"}>
                            {standingBadge}
                        </Badge>
                    )}
                </div>
                <div id={`${idPrefix}-meta`}
                     className={styles.meta}>
                    {context && (
                        <Text id={`${idPrefix}-context`}
                              size={200}>
                            {context}
                        </Text>
                    )}
                    {summary.issuedAt && (
                        <Text id={`${idPrefix}-issued`}
                              size={200}>
                            {`Issued ${formatInformationRequestTime(summary.issuedAt)}`}
                        </Text>
                    )}
                    {summary.nextDueAt && (
                        <Text id={`${idPrefix}-due`}
                              size={200}>
                            {`Due ${formatInformationRequestTime(summary.nextDueAt)}`}
                        </Text>
                    )}
                    {summary.callerRoles.length > 0 && (
                        <Text id={`${idPrefix}-roles`}
                              size={200}>
                            {`Your role: ${rolesSentence(summary.callerRoles)}`}
                        </Text>
                    )}
                </div>
                {summary.requiredCount > 0 && (
                    <div id={`${idPrefix}-progress`}
                         className={styles.progress}>
                        <Text id={`${idPrefix}-progress-text`}
                              size={200}>
                            {`${formatInformationRequestCount(summary.completedCount)} of ${formatInformationRequestCount(summary.requiredCount)} answers complete`}
                        </Text>
                        <ProgressBar id={`${idPrefix}-progress-bar`}
                                     aria-label={`${formatInformationRequestCount(summary.completedCount)} of ${formatInformationRequestCount(summary.requiredCount)} answers complete`}
                                     value={summary.completedCount / summary.requiredCount}/>
                    </div>
                )}
            </div>
            <div id={`${idPrefix}-actions`}
                 className={styles.action}>
                {offersManage && (
                    <Button id={`${idPrefix}-manage`}
                            shape={"circular"}
                            appearance={"secondary"}
                            aria-label={`Manage: ${summary.title}`}
                            disabled={opening}
                            onClick={() => onManage?.(summary)}>
                        Manage
                    </Button>
                )}
                <Button id={`${idPrefix}-action`}
                        shape={"circular"}
                        appearance={PRIMARY_ACTIONS.has(summary.nextAction) ? "primary" : "secondary"}
                        aria-label={`${actionLabel}: ${summary.title}`}
                        icon={openingIcon}
                        disabled={opening}
                        onClick={() => onOpen(summary)}>
                    {actionLabel}
                </Button>
            </div>
        </li>
    );
};

export default InformationRequestSummaryRow;
