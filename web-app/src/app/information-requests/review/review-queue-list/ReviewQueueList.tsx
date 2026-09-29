import {useEffect, useState} from "react";
import {Badge, Button, MessageBar, MessageBarBody, Spinner, Text} from "@fluentui/react-components";
import {useNavigate} from "react-router-dom";
import {getInformationRequestReviewQueue} from "../../../../services/informationRequestReviewService.ts";
import {InformationRequestReviewQueueEntryDto} from "../../../models/models.tsx";
import {humanizedKey, submissionErrorMessage} from "../../submission/submissionLabels.ts";
import {reviewStatePresentation} from "../reviewLabels.ts";
import {useReviewQueueListStyles} from "./ReviewQueueListStyles.tsx";
import {formatInformationRequestTime} from "../../shared/informationRequestFormatting.ts";

const ReviewQueueList = () =>
{
    const styles = useReviewQueueListStyles();
    const navigate = useNavigate();
    const [entries, setEntries] = useState<InformationRequestReviewQueueEntryDto[] | null>(null);
    const [error, setError] = useState<string | null>(null);

    useEffect(() =>
    {
        getInformationRequestReviewQueue()
            .then(setEntries)
            .catch((caught: unknown) => setError(submissionErrorMessage(caught, "Your reviews could not be loaded.")));
    }, []);

    if (error)
    {
        return (
            <MessageBar id={"information-request-review-queue-error"}
                        intent={"error"}>
                <MessageBarBody>{error}</MessageBarBody>
            </MessageBar>
        );
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
                return (
                    <li id={id}
                        key={entry.assignmentId}
                        className={styles.entry}>
                        <div id={`${id}-summary`}
                             className={styles.summary}>
                            <Text id={`${id}-title`}
                                  weight={"semibold"}>
                                {entry.submissionStageKey
                                    ? `Submission ${entry.packageNumber}, ${humanizedKey(entry.submissionStageKey)}`
                                    : `Submission ${entry.packageNumber}`}
                            </Text>
                            <Text id={`${id}-stage`}
                                  className={styles.detail}>
                                {`${humanizedKey(entry.reviewStageKey)}, ${entry.itemCount} items`}
                            </Text>
                            {entry.dueAt && (
                                <Text id={`${id}-due`}
                                      className={styles.detail}>
                                    {`Due ${formatInformationRequestTime(entry.dueAt)}`}
                                </Text>
                            )}
                        </div>
                        <Badge id={`${id}-state`}
                               appearance={"outline"}
                               color={state.color}>
                            {state.label}
                        </Badge>
                        <Button id={`${id}-open`}
                                appearance={"primary"}
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
