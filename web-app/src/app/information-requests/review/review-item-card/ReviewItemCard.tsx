import {Badge, Text} from "@fluentui/react-components";
import {InformationRequestReviewDto, InformationRequestReviewItemDto} from "../../../models/models.tsx";
import {humanizedKey} from "../../submission/submissionLabels.ts";
import ReviewItemContent from "../review-item-content/ReviewItemContent.tsx";
import ReviewItemDecisionControls from "../review-item-decision/ReviewItemDecisionControls.tsx";
import ReviewItemFindings from "../review-item-findings/ReviewItemFindings.tsx";
import {reviewOutcomeLabels} from "../reviewLabels.ts";
import {ReviewWorksheetDraft} from "../useInformationRequestReview.ts";
import {useReviewItemCardStyles} from "./ReviewItemCardStyles.tsx";

interface Props
{
    item: InformationRequestReviewItemDto;
    review: InformationRequestReviewDto;
    draft?: ReviewWorksheetDraft;
    decidable: boolean;
    busy: boolean;
    onChange: (change: Partial<ReviewWorksheetDraft>) => void;
    onAddFinding: () => void;
}

const ReviewItemCard = ({item, review, draft, decidable, busy, onChange, onAddFinding}: Props) =>
{
    const styles = useReviewItemCardStyles();
    const id = `information-request-review-item-${item.submissionItemId}`;
    const findings = review.findings.filter(finding => finding.submissionItemId === item.submissionItemId);
    const decisions = review.decisions.filter(decision => decision.submissionItemId === item.submissionItemId);

    return (
        <article id={id}
                 className={styles.card}>
            <div id={`${id}-header`}
                 className={styles.header}>
                <div id={`${id}-title-group`}
                     className={styles.titleGroup}>
                    <Text id={`${id}-title`}
                          weight={"semibold"}>
                        {humanizedKey(item.requirementKey)}
                    </Text>
                    {item.occurrencePath !== "root" && (
                        <Text id={`${id}-occurrence`}
                              className={styles.detail}>
                            {`Occurrence ${item.occurrencePath}`}
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
            <ReviewItemContent item={item}/>
            {decisions.length > 0 && (
                <Text id={`${id}-decisions`}
                      className={styles.detail}>
                    {`Decisions so far: ${decisions.map(decision => reviewOutcomeLabels[decision.outcome]).join(", ")}`}
                </Text>
            )}
            <ReviewItemFindings itemId={item.submissionItemId}
                                findings={findings}/>
            {decidable && (
                <ReviewItemDecisionControls itemId={item.submissionItemId}
                                            draft={draft}
                                            busy={busy}
                                            hasFinding={findings.some(finding => finding.recordedByCaller)}
                                            onChange={onChange}
                                            onAddFinding={onAddFinding}/>
            )}
        </article>
    );
};

export default ReviewItemCard;
