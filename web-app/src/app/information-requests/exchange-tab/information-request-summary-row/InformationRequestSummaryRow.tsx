import {Badge, Button, ProgressBar, Spinner, Text} from "@fluentui/react-components";
import {InformationRequestNextAction, InformationRequestSummaryDto} from "../../../models/models.tsx";
import {requestStateLabels} from "../../operations/operationsLabels.ts";
import {executionStandingBadge} from "../../shared/executionStandingText.ts";
import {formatInformationRequestTime} from "../../shared/informationRequestFormatting.ts";
import {nextActionLabels, rolesSentence} from "../../shared/informationRequestLabels.ts";
import {useInformationRequestSummaryRowStyles} from "./InformationRequestSummaryRowStyles.tsx";
import {formatInformationRequestCount} from "../../shared/informationRequestFormatting.ts";

interface Props
{
    summary: InformationRequestSummaryDto;
    opening: boolean;
    onOpen: (summary: InformationRequestSummaryDto) => void;
}

const PRIMARY_ACTIONS = new Set([
    InformationRequestNextAction.COMPLETE_SETUP,
    InformationRequestNextAction.RESPOND,
    InformationRequestNextAction.REVIEW,
]);

const InformationRequestSummaryRow = ({summary, opening, onOpen}: Props) =>
{
    const styles = useInformationRequestSummaryRowStyles();
    const actionLabel = nextActionLabels[summary.nextAction];
    const idPrefix = `information-request-summary-${summary.id}`;
    const standingBadge = executionStandingBadge(summary.executionStanding);

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
                            {`${formatInformationRequestCount(summary.completedCount)} of ${formatInformationRequestCount(summary.requiredCount)} required answers`}
                        </Text>
                        <ProgressBar id={`${idPrefix}-progress-bar`}
                                     aria-label={`${formatInformationRequestCount(summary.completedCount)} of ${formatInformationRequestCount(summary.requiredCount)} required answers`}
                                     value={summary.completedCount / summary.requiredCount}/>
                    </div>
                )}
            </div>
            <Button id={`${idPrefix}-action`}
                    className={styles.action}
                    shape={"circular"}
                    appearance={PRIMARY_ACTIONS.has(summary.nextAction) ? "primary" : "secondary"}
                    aria-label={`${actionLabel}: ${summary.title}`}
                    icon={opening ? <Spinner size={"tiny"}/> : undefined}
                    disabled={opening}
                    onClick={() => onOpen(summary)}>
                {actionLabel}
            </Button>
        </li>
    );
};

export default InformationRequestSummaryRow;
