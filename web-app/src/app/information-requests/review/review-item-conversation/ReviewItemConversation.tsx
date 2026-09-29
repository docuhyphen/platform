import {Text} from "@fluentui/react-components";
import {InformationRequestReviewCommentDto, InformationRequestReviewCommentRole, InformationRequestReviewVisibility} from "../../../models/models.tsx";
import {useReviewAssignmentsPanelStyles} from "../review-assignments-panel/ReviewAssignmentsPanelStyles.tsx";

interface Props
{
    itemId: string;
    comments: InformationRequestReviewCommentDto[];
}

const AUTHORS: Record<InformationRequestReviewCommentRole, string> = {
    [InformationRequestReviewCommentRole.REVIEWER]: "Reviewer",
    [InformationRequestReviewCommentRole.RESPONDENT]: "Respondent",
    [InformationRequestReviewCommentRole.ADMINISTRATOR]: "Administrator",
};

const ReviewItemConversation = ({itemId, comments}: Props) =>
{
    const styles = useReviewAssignmentsPanelStyles();

    if (comments.length === 0) return null;

    return (
        <ul id={`information-request-review-item-${itemId}-conversation`}
            aria-label={"Comments"}
            className={styles.list}>
            {comments.map(comment => (
                <li key={comment.id}
                    id={`information-request-review-item-${itemId}-comment-${comment.id}`}>
                    <Text>{`${comment.authoredByCaller ? "You" : AUTHORS[comment.authorRole]}: ${comment.body}`}</Text>
                    {comment.visibility === InformationRequestReviewVisibility.REVIEWERS_ONLY && (
                        <Text size={200}
                              className={styles.detail}>
                            {" (reviewers only)"}
                        </Text>
                    )}
                </li>
            ))}
        </ul>
    );
};

export default ReviewItemConversation;
