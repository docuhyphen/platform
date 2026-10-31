import {Badge, Button, Spinner, Text} from "@fluentui/react-components";
import {useNavigate} from "react-router-dom";
import {getInformationRequestReviewQueue} from "../../../../services/informationRequestReviewService.ts";
import {useServerRead} from "../../shared/useServerRead.ts";
import InformationRequestLoadError from "../../shared/load-error/InformationRequestLoadError.tsx";
import {humanizedKey, submissionErrorMessage} from "../../submission/submissionLabels.ts";
import {reviewStatePresentation} from "../reviewLabels.ts";
import {executionStandingBadge, executionStandingNotice} from "../../shared/executionStandingText.ts";
import {useReviewQueueListStyles} from "./ReviewQueueListStyles.tsx";
import {formatInformationRequestTime} from "../../shared/informationRequestFormatting.ts";

const ReviewQueueList = () =>
{
    const styles = useReviewQueueListStyles();
    const navigate = useNavigate();
    const read = useServerRead("review-queue", getInformationRequestReviewQueue,
        caught => submissionErrorMessage(caught, "Your reviews could not be loaded."));
    const entries = read.value;

    if (read.error)
    {
        return <InformationRequestLoadError id={"information-request-review-queue-error"}
                                            message={read.error}
                                            onRetry={() => void read.reload()}/>;
    }
    if (!entries)
    {
        return (
            <Spinner id={"information-request-review-queue-loading"}
                     size={"medium"}
                     label={"Loading your reviews"}/>
        );
    }
    if (entries.length === 0)
    {
        return <Text id={"information-request-review-queue-empty"}>No reviews are waiting for you.</Text>;
    }

    return (
        <ul id={"information-request-review-queue"}
            className={styles.list}>
            {entries.map(entry =>
            {
                const id = `information-request-review-queue-${entry.assignmentId}`;
                const state = reviewStatePresentation[entry.reviewState];
                const standingBadge = executionStandingBadge(entry.executionStanding);
                return (
                    <li id={id}
                        key={entry.assignmentId}
                        className={styles.entry}>
                        <div id={`${id}-summary`}
                             className={styles.summary}>
                            <Text id={`${id}-request`}
                                  weight={"semibold"}>{entry.requestTitle}</Text>
                            {entry.exchangeName && <Text id={`${id}-exchange`}>{entry.exchangeName}</Text>}
                            <Text id={`${id}-title`}
                                  weight={"semibold"}>
                                {entry.submissionStageKey
                                    ? `Submission ${entry.packageNumber}, ${humanizedKey(entry.submissionStageKey)}`
                                    : `Submission ${entry.packageNumber}`}
                            </Text>
                            <Text id={`${id}-stage`}
                                  className={styles.detail}>
                                {`${entry.reviewStageTitle}, ${entry.itemCount} items`}
                            </Text>
                            {entry.dueAt && (
                                <Text id={`${id}-due`}
                                      className={styles.detail}>
                                    {`Due ${formatInformationRequestTime(entry.dueAt)}`}
                                </Text>
                            )}
                        </div>
                        {standingBadge && (
                            <Badge id={`${id}-standing`}
                                   appearance={"tint"}
                                   color={"warning"}
                                   title={executionStandingNotice(entry.executionStanding)?.text}>
                                {standingBadge}
                            </Badge>
                        )}
                        <Badge id={`${id}-state`}
                               appearance={"outline"}
                               color={state.color}>
                            {state.label}
                        </Badge>
                        <Button id={`${id}-open`}
                                appearance={"primary"}
                                aria-label={`Open review of ${entry.requestTitle}, submission ${entry.packageNumber}, ${entry.reviewStageTitle}`}
                                shape={"circular"}
                                onClick={() => navigate(`/information-requests/${entry.informationRequestId}/reviews/${entry.reviewId}`)}>
                            Open review
                        </Button>
                    </li>
                );
            })}
        </ul>
    );
};

export default ReviewQueueList;
