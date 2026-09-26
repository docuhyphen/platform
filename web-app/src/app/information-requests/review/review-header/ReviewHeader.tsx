import {Badge, Text, Title2} from "@fluentui/react-components";
import {InformationRequestReviewSummaryDto} from "../../../models/models.tsx";
import {humanizedKey} from "../../submission/submissionLabels.ts";
import {reviewKindLabels, reviewStatePresentation} from "../reviewLabels.ts";
import {useReviewHeaderStyles} from "./ReviewHeaderStyles.tsx";

interface Props
{
    review: InformationRequestReviewSummaryDto;
}

const ReviewHeader = ({review}: Props) =>
{
    const styles = useReviewHeaderStyles();
    const state = reviewStatePresentation[review.state];

    return (
        <header id={"information-request-review-header"}
                className={styles.header}>
            <div id={"information-request-review-title-group"}
                 className={styles.titleGroup}>
                <Title2 id={"information-request-review-title"}>
                    {`${reviewKindLabels[review.kind]} of submission ${review.packageNumber}`}
                </Title2>
                <Text id={"information-request-review-subtitle"}
                      className={styles.detail}>
                    {review.stageKey
                        ? `Submitted part: ${humanizedKey(review.stageKey)}. Opened ${new Date(review.openedAt).toLocaleString()}.`
                        : `Opened ${new Date(review.openedAt).toLocaleString()}.`}
                </Text>
            </div>
            <Badge id={"information-request-review-state"}
                   appearance={"filled"}
                   color={state.color}>
                {state.label}
            </Badge>
        </header>
    );
};

export default ReviewHeader;
