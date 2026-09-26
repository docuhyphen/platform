import {Text} from "@fluentui/react-components";
import {InformationRequestReviewItemDto} from "../../../models/models.tsx";
import {humanizedKey} from "../../submission/submissionLabels.ts";
import {reviewValueText} from "../reviewLabels.ts";
import {useReviewItemContentStyles} from "./ReviewItemContentStyles.tsx";

interface Props
{
    item: InformationRequestReviewItemDto;
}

const ReviewItemContent = ({item}: Props) =>
{
    const styles = useReviewItemContentStyles();
    const id = `information-request-review-item-${item.submissionItemId}-content`;

    if (!item.contentVisible)
    {
        return (
            <Text id={id}
                  className={styles.detail}>
                Only reviewers of this item can see what was submitted.
            </Text>
        );
    }

    return (
        <div id={id}
             className={styles.content}>
            {item.disposition && (
                <Text id={`${id}-disposition`}
                      className={styles.detail}>
                    {`Answer: ${humanizedKey(item.disposition.toLowerCase())}`}
                </Text>
            )}
            {item.fieldValue !== undefined && (
                <Text id={`${id}-value`}
                      className={styles.value}>
                    {reviewValueText(item.fieldValue)}
                </Text>
            )}
            {item.narrative && (
                <Text id={`${id}-narrative`}>
                    {item.narrative}
                </Text>
            )}
            {item.evidence.length > 0 && (
                <ul id={`${id}-evidence`}
                    className={styles.evidence}>
                    {item.evidence.map(file => (
                        <li id={`${id}-evidence-${file.evidenceVersionId}`}
                            key={file.evidenceVersionId}>
                            <Text id={`${id}-evidence-${file.evidenceVersionId}-label`}>
                                {`File version ${file.versionNumber} (${humanizedKey(file.conformance.toLowerCase())})`}
                            </Text>
                        </li>
                    ))}
                </ul>
            )}
        </div>
    );
};

export default ReviewItemContent;
