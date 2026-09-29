import {useState} from "react";
import {Badge, Button, Text} from "@fluentui/react-components";
import {InformationRequestReviewDto, InformationRequestReviewItemDto} from "../../../models/models.tsx";
import {occurrenceLabel} from "../../structured-response-workspace/responseAnswerState.ts";
import ReviewCommentDialog from "../review-comment-dialog/ReviewCommentDialog.tsx";
import ReviewItemContent from "../review-item-content/ReviewItemContent.tsx";
import ReviewItemConversation from "../review-item-conversation/ReviewItemConversation.tsx";
import ReviewItemDecisionControls from "../review-item-decision/ReviewItemDecisionControls.tsx";
import ReviewItemFindings from "../review-item-findings/ReviewItemFindings.tsx";
import ReviewOverrideDialog from "../review-override-dialog/ReviewOverrideDialog.tsx";
import {reviewOutcomeLabels} from "../reviewLabels.ts";
import {ReviewWorksheetDraft} from "../useInformationRequestReview.ts";
import {ReviewManagement} from "../useReviewManagement.ts";
import {useReviewItemCardStyles} from "./ReviewItemCardStyles.tsx";

interface Props
{
    requestId: string;
    item: InformationRequestReviewItemDto;
    label: string;
    review: InformationRequestReviewDto;
    management: ReviewManagement;
    draft?: ReviewWorksheetDraft;
    decidable: boolean;
    busy: boolean;
    onChange: (change: Partial<ReviewWorksheetDraft>) => void;
    onAddFinding: () => void;
}

const ReviewItemCard = ({requestId, item, label, review, management, draft, decidable, busy, onChange, onAddFinding}: Props) =>
{
    const styles = useReviewItemCardStyles();
    const [dialog, setDialog] = useState<"override" | "comment" | null>(null);
    const id = `information-request-review-item-${item.submissionItemId}`;
    const findings = review.findings.filter(finding => finding.submissionItemId === item.submissionItemId);
    const overrideStage = review.canManage
        ? review.stages.find(stage => stage.overridePermitted && stage.items.some(standing => standing.submissionItemId === item.submissionItemId))
        : undefined;

    return (
        <article id={id}
                 aria-labelledby={`${id}-title`}
                 className={styles.card}>
            <div id={`${id}-header`}
                 className={styles.header}>
                <div id={`${id}-title-group`}
                     className={styles.titleGroup}>
                    <Text id={`${id}-title`}
                          as={"h3"}
                          weight={"semibold"}>
                        {label}
                    </Text>
                    {occurrenceLabel(item.occurrencePath) && (
                        <Text id={`${id}-occurrence`}
                              className={styles.detail}>
                            {occurrenceLabel(item.occurrencePath)}
                        </Text>
                    )}
                </div>
                {item.finalOutcome && (
                    <Badge id={`${id}-outcome`}
                           appearance={"outline"}
                           color={"informative"}>
                        {reviewOutcomeLabels[item.finalOutcome]}
                    </Badge>
                )}
            </div>
            <ReviewItemContent requestId={requestId}
                               item={item}
                               label={label}/>
            <ReviewItemFindings itemId={item.submissionItemId}
                                findings={findings}/>
            <ReviewItemConversation itemId={item.submissionItemId}
                                    comments={review.comments.filter(comment => comment.submissionItemId === item.submissionItemId)}/>
            {decidable && (
                <ReviewItemDecisionControls itemId={item.submissionItemId}
                                            draft={draft}
                                            busy={busy}
                                            hasFinding={findings.some(finding => finding.recordedByCaller)}
                                            onChange={onChange}
                                            onAddFinding={onAddFinding}/>
            )}
            <div id={`${id}-management`}
                 className={styles.header}>
                <Button id={`${id}-comment`}
                        size={"small"}
                        shape={"circular"}
                        appearance={"subtle"}
                        aria-label={`Comment on ${label}`}
                        disabled={management.busy}
                        onClick={() => setDialog("comment")}>
                    Comment
                </Button>
                {overrideStage && (
                    <Button id={`${id}-override`}
                            size={"small"}
                            shape={"circular"}
                            aria-label={`Override ${label}`}
                            disabled={management.busy}
                            onClick={() => setDialog("override")}>
                        Override
                    </Button>
                )}
            </div>
            {dialog === "comment" && (
                <ReviewCommentDialog label={label}
                                     busy={management.busy}
                                     onConfirm={(body, visibility) =>
                                     {
                                         setDialog(null);
                                         void management.comment({submissionItemId: item.submissionItemId, visibility, body});
                                     }}
                                     onDismiss={() => setDialog(null)}/>
            )}
            {dialog === "override" && overrideStage && (
                <ReviewOverrideDialog busy={management.busy}
                                      onConfirm={(outcome, narrative) =>
                                      {
                                          setDialog(null);
                                          void management.override({stageKey: overrideStage.stageKey, submissionItemId: item.submissionItemId, outcome, narrative});
                                      }}
                                      onDismiss={() => setDialog(null)}/>
            )}
        </article>
    );
};

export default ReviewItemCard;
