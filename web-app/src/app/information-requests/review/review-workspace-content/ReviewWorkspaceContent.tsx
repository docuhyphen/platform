import {useState} from "react";
import {Button, MessageBar, MessageBarBody, Spinner} from "@fluentui/react-components";
import {InformationRequestReviewItemDto, InformationRequestReviewState} from "../../../models/models.tsx";
import ReviewAssignmentsPanel from "../review-assignments-panel/ReviewAssignmentsPanel.tsx";
import ReviewCorrectionSummary from "../review-correction-summary/ReviewCorrectionSummary.tsx";
import ReviewDecisionHistory from "../review-decision-history/ReviewDecisionHistory.tsx";
import ReviewFindingDialog from "../review-finding-dialog/ReviewFindingDialog.tsx";
import ReviewHeader from "../review-header/ReviewHeader.tsx";
import ReviewItemCard from "../review-item-card/ReviewItemCard.tsx";
import ReviewReasonDialog from "../review-reason-dialog/ReviewReasonDialog.tsx";
import ReviewStageSummary from "../review-stage-summary/ReviewStageSummary.tsx";
import ReviewWorksheetActions from "../review-worksheet-actions/ReviewWorksheetActions.tsx";
import {useInformationRequestReview} from "../useInformationRequestReview.ts";
import {useReviewManagement} from "../useReviewManagement.ts";
import {useReviewRequirementLabels} from "../useReviewRequirementLabels.ts";
import {useReviewWorkspaceContentStyles} from "./ReviewWorkspaceContentStyles.tsx";

interface Props
{
    requestId: string;
    reviewId: string;
}

const SETTLED = new Set([
    InformationRequestReviewState.CHANGES_REQUESTED,
    InformationRequestReviewState.REJECTED,
    InformationRequestReviewState.SATISFIED,
    InformationRequestReviewState.SATISFIED_WITH_EXCEPTION,
]);

const ReviewWorkspaceContent = ({requestId, reviewId}: Props) =>
{
    const styles = useReviewWorkspaceContentStyles();
    const {review, scope, drafts, changed, busy, message, load, updateDraft, saveWorksheet, recordDecisions, recordFinding} =
        useInformationRequestReview(requestId, reviewId);
    const management = useReviewManagement(requestId, review, load);
    const labelOf = useReviewRequirementLabels(requestId);
    const [findingItem, setFindingItem] = useState<InformationRequestReviewItemDto | null>(null);
    const [reconsidering, setReconsidering] = useState(false);

    if (!review)
    {
        return message ? (
            <MessageBar id={"information-request-review-load-error"}
                        intent={message.intent}>
                <MessageBarBody>{message.text}</MessageBarBody>
            </MessageBar>
        ) : (
            <Spinner id={"information-request-review-loading"}
                     size={"medium"}
                     label={"Loading review"}/>
        );
    }

    const decidable = new Set(scope?.itemIds ?? []);
    const shownMessage = management.message ?? message;
    const itemLabel = (submissionItemId: string) =>
    {
        const item = review.items.find(candidate => candidate.submissionItemId === submissionItemId);
        return item ? labelOf(item) : "Requested item";
    };

    return (
        <div id={"information-request-review-workspace"}
             className={styles.workspace}>
            <ReviewHeader review={review.review}/>
            {shownMessage && (
                <MessageBar id={"information-request-review-message"}
                            intent={shownMessage.intent}>
                    <MessageBarBody>{shownMessage.text}</MessageBarBody>
                </MessageBar>
            )}
            {review.canManage && SETTLED.has(review.review.state) && (
                <Button id={"information-request-review-reconsider"}
                        appearance={"secondary"}
                        shape={"circular"}
                        disabled={management.busy}
                        onClick={() => setReconsidering(true)}>
                    Reconsider
                </Button>
            )}
            <ReviewStageSummary stages={review.stages}/>
            <ReviewAssignmentsPanel review={review}
                                    management={management}/>
            <div id={"information-request-review-items"}
                 className={styles.items}>
                {review.items.filter(item => item.reviewed).map(item => (
                    <ReviewItemCard key={item.submissionItemId}
                                    requestId={requestId}
                                    item={item}
                                    label={labelOf(item)}
                                    review={review}
                                    management={management}
                                    draft={drafts[item.submissionItemId]}
                                    decidable={decidable.has(item.submissionItemId)}
                                    busy={busy}
                                    onChange={change => updateDraft(item.submissionItemId, change)}
                                    onAddFinding={() => setFindingItem(item)}/>
                ))}
            </div>
            {review.correction && (
                <ReviewCorrectionSummary correction={review.correction}
                                         items={review.items}
                                         remediations={review.remediations}/>
            )}
            {scope && (
                <ReviewWorksheetActions busy={busy}
                                        changedCount={changed.length}
                                        onSave={() => void saveWorksheet()}
                                        onRecord={() => void recordDecisions()}/>
            )}
            <ReviewDecisionHistory review={review}
                                   labelOf={itemLabel}/>
            {findingItem && (
                <ReviewFindingDialog item={findingItem}
                                     busy={busy}
                                     onConfirm={request => void recordFinding(request).then(recorded => recorded && setFindingItem(null))}
                                     onDismiss={() => setFindingItem(null)}/>
            )}
            {reconsidering && (
                <ReviewReasonDialog id={"information-request-review-reconsider-dialog"}
                                    title={"Reconsider this review"}
                                    explanation={"A reconsideration opens a new review of the same submission. The current decisions stay in the history."}
                                    confirmLabel={"Reconsider"}
                                    busy={management.busy}
                                    onConfirm={reason =>
                                    {
                                        setReconsidering(false);
                                        void management.reconsider(reason);
                                    }}
                                    onDismiss={() => setReconsidering(false)}/>
            )}
        </div>
    );
};

export default ReviewWorkspaceContent;
