import {useState} from "react";
import {MessageBar, MessageBarBody, Spinner} from "@fluentui/react-components";
import {InformationRequestReviewItemDto} from "../../../models/models.tsx";
import ReviewCorrectionSummary from "../review-correction-summary/ReviewCorrectionSummary.tsx";
import ReviewFindingDialog from "../review-finding-dialog/ReviewFindingDialog.tsx";
import ReviewHeader from "../review-header/ReviewHeader.tsx";
import ReviewItemCard from "../review-item-card/ReviewItemCard.tsx";
import ReviewStageSummary from "../review-stage-summary/ReviewStageSummary.tsx";
import ReviewWorksheetActions from "../review-worksheet-actions/ReviewWorksheetActions.tsx";
import {useInformationRequestReview} from "../useInformationRequestReview.ts";
import {useReviewWorkspaceContentStyles} from "./ReviewWorkspaceContentStyles.tsx";

interface Props
{
    requestId: string;
    reviewId: string;
}

const ReviewWorkspaceContent = ({requestId, reviewId}: Props) =>
{
    const styles = useReviewWorkspaceContentStyles();
    const {review, scope, drafts, changed, busy, message, updateDraft, saveWorksheet, recordDecisions, recordFinding} =
        useInformationRequestReview(requestId, reviewId);
    const [findingItem, setFindingItem] = useState<InformationRequestReviewItemDto | null>(null);

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

    return (
        <div id={"information-request-review-workspace"}
             className={styles.workspace}>
            <ReviewHeader review={review.review}/>
            {message && (
                <MessageBar id={"information-request-review-message"}
                            intent={message.intent}>
                    <MessageBarBody>{message.text}</MessageBarBody>
                </MessageBar>
            )}
            <ReviewStageSummary stages={review.stages}/>
            <div id={"information-request-review-items"}
                 className={styles.items}>
                {review.items.filter(item => item.reviewed).map(item => (
                    <ReviewItemCard key={item.submissionItemId}
                                    item={item}
                                    review={review}
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
            {findingItem && (
                <ReviewFindingDialog item={findingItem}
                                     busy={busy}
                                     onConfirm={request => void recordFinding(request).then(recorded => recorded && setFindingItem(null))}
                                     onDismiss={() => setFindingItem(null)}/>
            )}
        </div>
    );
};

export default ReviewWorkspaceContent;
