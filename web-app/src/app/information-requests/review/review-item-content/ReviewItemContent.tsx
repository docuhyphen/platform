import {useState} from "react";
import {Button, Text} from "@fluentui/react-components";
import {InformationRequestEvidenceContentUse} from "../../../../services/informationRequestEvidenceService.ts";
import {InformationRequestReviewItemDto} from "../../../models/models.tsx";
import {formatInformationRequestSize} from "../../shared/informationRequestFormatting.ts";
import {dispositionLabels} from "../../shared/informationRequestLabels.ts";
import {humanizedKey} from "../../submission/submissionLabels.ts";
import {openReviewEvidence} from "../reviewEvidenceContent.ts";
import {reviewValueText} from "../reviewLabels.ts";
import {useReviewItemContentStyles} from "./ReviewItemContentStyles.tsx";

interface Props
{
    requestId: string;
    item: InformationRequestReviewItemDto;
    label: string;
}

const ReviewItemContent = ({requestId, item, label}: Props) =>
{
    const styles = useReviewItemContentStyles();
    const [failure, setFailure] = useState<string | null>(null);
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

    const open = (evidenceIndex: number, use: InformationRequestEvidenceContentUse) =>
    {
        setFailure(null);
        openReviewEvidence(requestId, item.requirementId, item.evidence[evidenceIndex], use)
            .catch(() => setFailure("The file could not be opened."));
    };

    return (
        <div id={id}
             className={styles.content}>
            {item.disposition && (
                <Text id={`${id}-disposition`}
                      className={styles.detail}>
                    {`Answer: ${dispositionLabels[item.disposition]}`}
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
                    {item.evidence.map((file, index) => (
                        <li id={`${id}-evidence-${file.evidenceVersionId}`}
                            key={file.evidenceVersionId}>
                            <Text id={`${id}-evidence-${file.evidenceVersionId}-label`}>
                                {[
                                    `File version ${file.versionNumber}`,
                                    file.contentLength !== undefined ? formatInformationRequestSize(file.contentLength) : null,
                                    humanizedKey(file.conformance.toLowerCase()),
                                ].filter(Boolean).join(", ")}
                            </Text>
                            <Button id={`${id}-evidence-${file.evidenceVersionId}-preview`}
                                    size={"small"}
                                    shape={"circular"}
                                    appearance={"subtle"}
                                    aria-label={`Preview version ${file.versionNumber} of ${label}`}
                                    onClick={() => open(index, "preview")}>
                                Preview
                            </Button>
                            <Button id={`${id}-evidence-${file.evidenceVersionId}-download`}
                                    size={"small"}
                                    shape={"circular"}
                                    appearance={"subtle"}
                                    aria-label={`Download version ${file.versionNumber} of ${label}`}
                                    onClick={() => open(index, "content")}>
                                Download
                            </Button>
                        </li>
                    ))}
                </ul>
            )}
            {failure && (
                <Text id={`${id}-failure`}
                      role={"alert"}>
                    {failure}
                </Text>
            )}
        </div>
    );
};

export default ReviewItemContent;
