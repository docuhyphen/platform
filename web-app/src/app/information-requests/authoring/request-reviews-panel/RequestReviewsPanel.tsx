import {Badge, Button, Text, Title3} from "@fluentui/react-components";
import {useNavigate} from "react-router-dom";
import {
    InformationRequestReviewAssignmentState,
    InformationRequestReviewDto,
    InformationRequestReviewState,
} from "../../../models/models.tsx";
import {reviewKindLabels, reviewStatePresentation} from "../../review/reviewLabels.ts";
import {informationRequestReviewPath} from "../../shared/informationRequestWorkspacePaths.ts";
import InformationRequestLoadError from "../../shared/load-error/InformationRequestLoadError.tsx";
import {useRequestReviews} from "./useRequestReviews.ts";
import {useRequestReviewsPanelStyles} from "./RequestReviewsPanelStyles.tsx";

interface Props {
    requestId: string;
}

const reviewerSummary = (review: InformationRequestReviewDto): string | null => {
    const active = review.assignments.filter(assignment => assignment.state === InformationRequestReviewAssignmentState.ACTIVE).length;
    if (active > 0) return active === 1 ? "1 reviewer assigned" : `${active} reviewers assigned`;
    return review.review.state === InformationRequestReviewState.PENDING ? "No reviewer assigned yet" : null;
};

const RequestReviewsPanel = ({requestId}: Props) => {
    const styles = useRequestReviewsPanelStyles();
    const navigate = useNavigate();
    const read = useRequestReviews(requestId);
    const reviews = read.value ?? [];
    if (!read.error && reviews.length === 0) return null;

    return (
        <section id={"information-request-reviews"}
                 aria-labelledby={"information-request-reviews-title"}
                 className={styles.panel}>
            <Title3 id={"information-request-reviews-title"}
                    as={"h2"}>
                Reviews
            </Title3>
            {read.error && (
                <InformationRequestLoadError id={"information-request-reviews-load-error"}
                                             message={read.error}
                                             onRetry={() => void read.reload()}/>
            )}
            {reviews.length > 0 && (
                <ul id={"information-request-reviews-list"}
                    className={styles.list}>
                    {reviews.map(entry => {
                        const review = entry.review;
                        const rowId = `information-request-review-${review.id}`;
                        const state = reviewStatePresentation[review.state];
                        const reviewers = reviewerSummary(entry);
                        return (
                            <li id={rowId}
                                key={review.id}
                                className={styles.item}>
                                <div id={`${rowId}-summary`}
                                     className={styles.summary}>
                                    <Text id={`${rowId}-title`}
                                          weight={"semibold"}>
                                        {`Review ${review.reviewNumber}`}
                                    </Text>
                                    <Text id={`${rowId}-kind`}
                                          size={200}
                                          className={styles.muted}>
                                        {`${reviewKindLabels[review.kind]}, submission ${review.packageNumber}`}
                                    </Text>
                                    {reviewers && (
                                        <Text id={`${rowId}-reviewers`}
                                              size={200}
                                              className={styles.muted}>
                                            {reviewers}
                                        </Text>
                                    )}
                                </div>
                                <div id={`${rowId}-actions`}
                                     className={styles.actions}>
                                    <Badge id={`${rowId}-state`}
                                           appearance={"tint"}
                                           color={state.color}>
                                        {state.label}
                                    </Badge>
                                    <Button id={`${rowId}-open`}
                                            shape={"circular"}
                                            size={"small"}
                                            aria-label={`Open review ${review.reviewNumber}`}
                                            onClick={() => navigate(informationRequestReviewPath(requestId, review.id))}>
                                        Open review
                                    </Button>
                                </div>
                            </li>
                        );
                    })}
                </ul>
            )}
        </section>
    );
};

export default RequestReviewsPanel;
