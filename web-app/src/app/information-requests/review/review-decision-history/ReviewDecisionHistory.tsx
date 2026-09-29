import {Text, Title3} from "@fluentui/react-components";
import {
    InformationRequestReviewDecisionDto,
    InformationRequestReviewDecisionKind,
    InformationRequestReviewDto,
} from "../../../models/models.tsx";
import {formatInformationRequestTime} from "../../shared/informationRequestFormatting.ts";
import {reviewOutcomeLabels} from "../reviewLabels.ts";
import {useReviewAssignmentsPanelStyles} from "../review-assignments-panel/ReviewAssignmentsPanelStyles.tsx";

interface Props
{
    review: InformationRequestReviewDto;
    labelOf: (submissionItemId: string) => string;
}

const deciderText = (decision: InformationRequestReviewDecisionDto): string =>
{
    if (decision.kind === InformationRequestReviewDecisionKind.OVERRIDE) return decision.decidedByCaller ? "by your override" : "by an override";
    if (decision.kind === InformationRequestReviewDecisionKind.CARRIED) return "carried from an earlier review";
    return decision.decidedByCaller ? "by you" : "by a reviewer";
};

const ReviewDecisionHistory = ({review, labelOf}: Props) =>
{
    const styles = useReviewAssignmentsPanelStyles();
    const stageTitle = (stageKey: string) => review.stages.find(stage => stage.stageKey === stageKey)?.title ?? stageKey;
    const remediated = review.remediations.length;

    if (review.decisions.length === 0 && remediated === 0) return null;

    return (
        <section id={"information-request-review-history"}
                 aria-labelledby={"information-request-review-history-title"}
                 className={styles.panel}>
            <Title3 id={"information-request-review-history-title"}
                    as={"h2"}>
                Decision history
            </Title3>
            <ol id={"information-request-review-history-list"}
                className={styles.list}>
                {[...review.decisions].sort((left, right) => left.decidedAt.localeCompare(right.decidedAt)).map(decision => (
                    <li key={decision.id}
                        id={`information-request-review-history-${decision.id}`}>
                        <Text>
                            {`${labelOf(decision.submissionItemId)}: ${reviewOutcomeLabels[decision.outcome]} ${deciderText(decision)}`}
                        </Text>
                        <Text size={200}
                              className={styles.detail}>
                            {` (${stageTitle(decision.stageKey)}, ${formatInformationRequestTime(decision.decidedAt)})`}
                        </Text>
                        {decision.narrative && (
                            <Text size={200}
                                  className={styles.detail}>
                                {` ${decision.narrative}`}
                            </Text>
                        )}
                    </li>
                ))}
            </ol>
            {remediated > 0 && (
                <Text id={"information-request-review-history-remediations"}
                      className={styles.detail}>
                    {remediated === 1
                        ? "1 finding was addressed by a later submission."
                        : `${remediated} findings were addressed by a later submission.`}
                </Text>
            )}
        </section>
    );
};

export default ReviewDecisionHistory;
