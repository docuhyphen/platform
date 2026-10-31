import {useState} from "react";
import {Button, MessageBar, MessageBarBody} from "@fluentui/react-components";
import {InformationRequestReviewItemDto} from "../../../models/models.tsx";
import ReviewAssignmentsPanel from "../review-assignments-panel/ReviewAssignmentsPanel.tsx";
import ReviewCorrectionSummary from "../review-correction-summary/ReviewCorrectionSummary.tsx";
import ReviewDecisionHistory from "../review-decision-history/ReviewDecisionHistory.tsx";
import ReviewFindingDialog from "../review-finding-dialog/ReviewFindingDialog.tsx";
import ReviewHeader from "../review-header/ReviewHeader.tsx";
import ReviewItemCard from "../review-item-card/ReviewItemCard.tsx";
import ReviewLoadState from "../review-load-state/ReviewLoadState.tsx";
import ReviewReasonDialog from "../review-reason-dialog/ReviewReasonDialog.tsx";
import ReviewStagePicker from "../review-stage-picker/ReviewStagePicker.tsx";
import ReviewStageSummary from "../review-stage-summary/ReviewStageSummary.tsx";
import ReviewSupplementAction from "../review-supplement-action/ReviewSupplementAction.tsx";
import ReviewVerificationPanel from "../review-verification-panel/ReviewVerificationPanel.tsx";
import ReviewWorksheetActions from "../review-worksheet-actions/ReviewWorksheetActions.tsx";
import {useInformationRequestReview} from "../useInformationRequestReview.ts";
import {useReviewManagement} from "../useReviewManagement.ts";
import {reviewItemLabel} from "../reviewLabels.ts";
import {useReviewWorkspaceContentStyles} from "./ReviewWorkspaceContentStyles.tsx";

interface Props
{
    requestId: string;
    reviewId: string;
}

const ReviewWorkspaceContent = ({requestId, reviewId}: Props) =>
{
    const styles = useReviewWorkspaceContentStyles();
    const work = useInformationRequestReview(requestId, reviewId);
    const {review, scope, choices, drafts, changed, busy, message, load} = work;
    const management = useReviewManagement(requestId, review, load, work.feedback);
    const [finding, setFinding] = useState<{ item: InformationRequestReviewItemDto; asManager: boolean } | null>(null);
    const [reconsidering, setReconsidering] = useState(false);

    if (!review)
    {
        return (
            <ReviewLoadState message={message}
                             onRetry={() => void load()}/>
        );
    }

    const decidable = new Set(scope?.itemIds ?? []);
    const itemLabel = (submissionItemId: string) =>
    {
        const item = review.items.find(candidate => candidate.submissionItemId === submissionItemId);
        return item ? reviewItemLabel(item) : "Requested item";
    };
    const requirementLabel = (requirementId: string) => {
        const item = review.items.find(candidate => candidate.requirementId === requirementId);
        return item ? reviewItemLabel(item) : "Requested item";
    };

    return (
        <div id={"information-request-review-workspace"}
             className={styles.workspace}>
            <ReviewHeader review={review.review}/>
            <ReviewVerificationPanel awaiting={review.itemsAwaitingStepUp}
                                     onVerified={() => void load()}/>
            {message && !finding && (
                <MessageBar id={"information-request-review-message"}
                            intent={message.intent}>
                    <MessageBarBody id={"information-request-review-message-body"}>{message.text}</MessageBarBody>
                </MessageBar>
            )}
            {review.canReconsider && (
                <Button id={"information-request-review-reconsider"}
                        appearance={"secondary"}
                        shape={"circular"}
                        disabled={management.busy}
                        onClick={() => setReconsidering(true)}>
                    Reconsider
                </Button>
            )}
            {review.canRequestSupplement && review.requestETag && (
                <ReviewSupplementAction requestId={requestId}
                                        requestETag={review.requestETag}
                                        onChanged={() => void load()}/>
            )}
            <ReviewStageSummary stages={review.stages}/>
            <ReviewAssignmentsPanel review={review}
                                    management={management}/>
            {scope && choices.length > 1 && (
                <ReviewStagePicker choices={choices}
                                   chosenId={scope.worksheet.assignmentId}
                                   busy={busy}
                                   onChoose={work.chooseAssignment}/>
            )}
            <div id={"information-request-review-items"}
                 className={styles.items}>
                {review.items.filter(item => item.reviewed).map(item => (
                    <ReviewItemCard key={item.submissionItemId}
                                    requestId={requestId}
                                    item={item}
                                    label={reviewItemLabel(item)}
                                    review={review}
                                    management={management}
                                    draft={drafts[item.submissionItemId]}
                                    assignmentId={scope?.worksheet.assignmentId}
                                    decidable={decidable.has(item.submissionItemId)}
                                    busy={busy}
                                    onChange={change => work.updateDraft(item.submissionItemId, change)}
                                    onAddFinding={() => setFinding({item, asManager: false})}
                                    onAddManagerFinding={() => setFinding({item, asManager: true})}/>
                ))}
            </div>
            {review.correction && (
                <ReviewCorrectionSummary correction={review.correction}
                                         remediations={review.remediations}
                                         labelOf={requirementLabel}/>
            )}
            {scope && (
                <ReviewWorksheetActions busy={busy}
                                        changedCount={changed.length}
                                        onSave={() => void work.saveWorksheet()}
                                        onRecord={() => void work.recordDecisions()}/>
            )}
            <ReviewDecisionHistory review={review}
                                   labelOf={itemLabel}/>
            {finding && (
                <ReviewFindingDialog item={finding.item}
                                     message={message}
                                     label={reviewItemLabel(finding.item)}
                                     earlierFindings={review.earlierFindings.filter(earlier =>
                                         earlier.reviewId === review.review.priorReviewId && earlier.requirementId === finding.item.requirementId)}
                                     busy={busy}
                                     onConfirm={request => void work.recordFinding(request, finding.asManager)
                                         .then(recorded => recorded && setFinding(null))}
                                     onDismiss={() => setFinding(null)}/>
            )}
            {reconsidering && (
                <ReviewReasonDialog id={"information-request-review-reconsider-dialog"}
                                    title={"Reconsider this review"}
                                    explanation={"A reconsideration opens a new review of the same submission. The current decisions stay in the history."}
                                    confirmLabel={"Reconsider"}
                                    busy={management.busy}
                                    message={management.message}
                                    onConfirm={reason => void management.reconsider(reason)
                                        .then(done => done && setReconsidering(false))}
                                    onDismiss={() => setReconsidering(false)}/>
            )}
        </div>
    );
};

export default ReviewWorkspaceContent;
