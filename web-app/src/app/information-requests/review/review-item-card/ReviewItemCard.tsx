import {Badge, Text} from "@fluentui/react-components";
import {InformationRequestReviewDto, InformationRequestReviewItemDto} from "../../../models/models.tsx";
import {occurrenceLabel} from "../../structured-response-workspace/responseAnswerState.ts";
import ReviewItemContent from "../review-item-content/ReviewItemContent.tsx";
import ReviewItemConversation from "../review-item-conversation/ReviewItemConversation.tsx";
import ReviewItemDecisionControls from "../review-item-decision/ReviewItemDecisionControls.tsx";
import ReviewItemFindings from "../review-item-findings/ReviewItemFindings.tsx";
import ReviewItemManagement from "../review-item-management/ReviewItemManagement.tsx";
import {reviewOutcomeLabels} from "../reviewLabels.ts";
import {ReviewWorksheetDraft} from "../useReviewWorksheet.ts";
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
    assignmentId?: string;
    decidable: boolean;
    busy: boolean;
    onChange: (change: Partial<ReviewWorksheetDraft>) => void;
    onAddFinding: () => void;
    onAddManagerFinding: () => void;
}

const ReviewItemCard = ({
                            requestId,
                            item,
                            label,
                            review,
                            management,
                            draft,
                            assignmentId,
                            decidable,
                            busy,
                            onChange,
                            onAddFinding,
                            onAddManagerFinding,
                        }: Props) =>
{
    const styles = useReviewItemCardStyles();
    const id = `information-request-review-item-${item.submissionItemId}`;
    const findings = review.findings.filter(finding => finding.submissionItemId === item.submissionItemId);

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
                               packageId={review.review.packageId}
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
                                            waiverPermitted={item.waiverPermitted}
                                            hasFinding={findings.some(finding => finding.assignmentId === assignmentId)}
                                            onChange={onChange}
                                            onAddFinding={onAddFinding}/>
            )}
            <ReviewItemManagement id={id}
                                  item={item}
                                  label={label}
                                  review={review}
                                  management={management}
                                  decidable={decidable}
                                  onAddFinding={onAddManagerFinding}/>
        </article>
    );
};

export default ReviewItemCard;
