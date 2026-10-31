import {Badge, Button, Text} from "@fluentui/react-components";
import {
    InformationRequestCorrectionState,
    InformationRequestRespondentReviewDto,
    InformationRequestReviewFindingDto,
    InformationRequestReviewResultItemDto,
} from "../../../models/models.tsx";
import RespondentFinding from "../respondent-finding/RespondentFinding.tsx";
import RespondentItemThread from "../respondent-item-thread/RespondentItemThread.tsx";
import {returnedFilesText, reviewKindLabels, reviewStatePresentation} from "../reviewLabels.ts";
import {useRespondentReviewCardStyles} from "./RespondentReviewCardStyles.tsx";

interface Props
{
    result: InformationRequestRespondentReviewDto;
    requirementLabels: Record<string, string>;
    busy: boolean;
    onAppeal: () => void;
    onReply: (finding: InformationRequestReviewFindingDto, body: string) => Promise<boolean>;
    onComment: (item: InformationRequestReviewResultItemDto, body: string) => Promise<boolean>;
}

const RespondentReviewCard = ({result, requirementLabels, busy, onAppeal, onReply, onComment}: Props) =>
{
    const styles = useRespondentReviewCardStyles();
    const {review, findings, correction} = result;
    const itemComments = result.comments.filter(comment => !comment.findingId);
    const commentsOn = (item: InformationRequestReviewResultItemDto) =>
        itemComments.filter(comment => comment.submissionItemId === item.submissionItemId);
    const threads = (result.items ?? []).filter(item => result.canComment || commentsOn(item).length > 0);
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
            {review.openingReason && (
                <Text id={`${id}-reason`}
                      className={styles.reason}>
                    {`Reason given: ${review.openingReason}`}
                </Text>
            )}
            {findings.length > 0 && (
                <ul id={`${id}-findings`}
                    className={styles.list}>
                    {findings.map(finding => (
                        <RespondentFinding key={finding.id}
                                           idPrefix={id}
                                           finding={finding}
                                           label={labelOf(finding.requirementId)}
                                           comments={result.comments.filter(comment => comment.findingId === finding.id)}
                                           canReply={result.canComment}
                                           busy={busy}
                                           onReply={onReply}/>
                    ))}
                </ul>
            )}
            {threads.length > 0 && (
                <ul id={`${id}-items`}
                    className={styles.list}>
                    {threads.map(item => (
                        <RespondentItemThread key={item.submissionItemId}
                                              idPrefix={id}
                                              item={item}
                                              label={labelOf(item.requirementId)}
                                              comments={commentsOn(item)}
                                              canComment={result.canComment}
                                              busy={busy}
                                              onComment={onComment}/>
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
                    {correction.evidence.length > 0 && (
                        <Text id={`${id}-correction-files`}
                              className={styles.detail}>
                            {returnedFilesText(correction.evidence)}
                        </Text>
                    )}
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
                            aria-labelledby={`${id}-appeal ${id}-title`}
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
