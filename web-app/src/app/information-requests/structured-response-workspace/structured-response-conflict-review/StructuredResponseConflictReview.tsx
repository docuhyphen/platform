import {Button, Text} from "@fluentui/react-components";
import {ResponseDraftConflict} from "../responseDraftReview.ts";
import {useStructuredResponseConflictReviewStyles} from "./StructuredResponseConflictReviewStyles.tsx";

interface Props {
    conflicts: ResponseDraftConflict[];
    onKeepSaved: (key: string) => void;
}

const StructuredResponseConflictReview = ({conflicts, onKeepSaved}: Props) => {
    const styles = useStructuredResponseConflictReviewStyles();
    if (conflicts.length === 0) return null;

    return (
        <section id="information-request-response-conflicts"
                 aria-labelledby="information-request-response-conflicts-title"
                 className={styles.review}>
            <Text id="information-request-response-conflicts-title"
                  className={styles.title}>
                Answers saved elsewhere
            </Text>
            <Text id="information-request-response-conflicts-help"
                  size={200}>
                These answers were saved somewhere else after you started changing them. Keep the saved answer, or
                choose
                Save responses to replace it with yours.
            </Text>
            <ul id="information-request-response-conflict-list"
                className={styles.conflicts}>
                {conflicts.map(conflict => (
                    <li key={conflict.key}
                        id={`information-request-response-conflict-${conflict.elementId}`}
                        className={styles.conflict}>
                        <span id={`information-request-response-conflict-answer-${conflict.elementId}`}
                              className={styles.answer}>
                            <Text id={`structured-response-conflict-review-label-text-${conflict.key}`}
                                  weight={"semibold"}>
                                {conflict.label}
                            </Text>
                            <Text id={`structured-response-conflict-review-answer-text-${conflict.key}`}
                                  size={200}>
                                {`Latest saved answer: ${conflict.latestAnswer}`}
                            </Text>
                        </span>
                        <Button id={`information-request-response-conflict-keep-${conflict.elementId}`}
                                appearance={"secondary"}
                                shape={"circular"}
                                size={"small"}
                                aria-label={`Keep saved answer: ${conflict.label}`}
                                onClick={() => onKeepSaved(conflict.key)}>
                            Keep saved answer
                        </Button>
                    </li>
                ))}
            </ul>
        </section>
    );
};

export default StructuredResponseConflictReview;
