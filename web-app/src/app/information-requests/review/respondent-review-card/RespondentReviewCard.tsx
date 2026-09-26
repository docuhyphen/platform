import {Badge, Button, Text} from "@fluentui/react-components";
import {InformationRequestCorrectionState, InformationRequestRespondentReviewDto} from "../../../models/models.tsx";
import {findingSeverityLabels, reviewKindLabels, reviewStatePresentation} from "../reviewLabels.ts";
import {useRespondentReviewCardStyles} from "./RespondentReviewCardStyles.tsx";

interface Props
{
    result: InformationRequestRespondentReviewDto;
    requirementLabels: Record<string, string>;
    busy: boolean;
    onAppeal: () => void;
}

const RespondentReviewCard = ({result, requirementLabels, busy, onAppeal}: Props) =>
{
    const styles = useRespondentReviewCardStyles();
    const {review, findings, correction} = result;
    const state = reviewStatePresentation[review.state];
    const id = `information-request-review-result-${review.id}`;
    const labelOf = (requirementId: string) => requirementLabels[requirementId] ?? "Requested item";

    return (
        <article id={id}
                 className={styles.card}>
            <div id={`${id}-header`}
                 className={styles.header}>
                <Text id={`${id}-title`}
                      weight={"semibold"}>
                    {`${reviewKindLabels[review.kind]} of submission ${review.packageNumber}`}
                </Text>
                <Badge id={`${id}-state`}
                       appearance={"filled"}
                       color={state.color}>
                    {state.label}
                </Badge>
            </div>
            {findings.length > 0 && (
                <ul id={`${id}-findings`}
                    className={styles.list}>
                    {findings.map(finding => (
                        <li id={`${id}-finding-${finding.id}`}
                            key={finding.id}
                            className={styles.finding}>
                            <Text id={`${id}-finding-${finding.id}-requirement`}
                                  weight={"semibold"}>
                                {`${labelOf(finding.requirementId)} (${findingSeverityLabels[finding.severity].toLowerCase()})`}
                            </Text>
                            <Text id={`${id}-finding-${finding.id}-narrative`}>{finding.narrative}</Text>
                        </li>
                    ))}
                </ul>
            )}
            {correction?.state === InformationRequestCorrectionState.OPEN && (
                <div id={`${id}-correction`}
                     className={styles.correction}>
                    <Text id={`${id}-correction-title`}>Correct and resubmit these items:</Text>
                    <ul id={`${id}-correction-items`}
                        className={styles.list}>
                        {correction.requirementIds.map(requirementId => (
                            <li id={`${id}-correction-${requirementId}`}
                                key={requirementId}>
                                <Text id={`${id}-correction-${requirementId}-label`}>{labelOf(requirementId)}</Text>
                            </li>
                        ))}
                    </ul>
                    {correction.undisclosedItemCount > 0 && (
                        <Text id={`${id}-correction-undisclosed`}
                              className={styles.detail}>
                            {`${correction.undisclosedItemCount} other returned items concern parts handled by other parties.`}
                        </Text>
                    )}
                </div>
            )}
            {result.remediations.length > 0 && (
                <Text id={`${id}-remediations`}
                      className={styles.detail}>
                    {`${result.remediations.length} findings were addressed by your later submission.`}
                </Text>
            )}
            {result.canAppeal && (
                <div id={`${id}-actions`}
                     className={styles.actions}>
                    <Button id={`${id}-appeal`}
                            appearance={"secondary"}
                            shape={"circular"}
                            disabled={busy}
                            onClick={onAppeal}>
                        Appeal
                    </Button>
                </div>
            )}
        </article>
    );
};

export default RespondentReviewCard;
