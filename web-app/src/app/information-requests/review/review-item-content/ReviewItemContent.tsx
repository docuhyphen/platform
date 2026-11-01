import {Button, Text} from "@fluentui/react-components";
import {InformationRequestReviewItemDto} from "../../../models/models.tsx";
import {formatInformationRequestSize} from "../../shared/informationRequestFormatting.ts";
import {dispositionLabels} from "../../shared/informationRequestLabels.ts";
import {humanizedKey} from "../../submission/submissionLabels.ts";
import ReviewEvidencePreviewDialog from "../review-evidence-preview-dialog/ReviewEvidencePreviewDialog.tsx";
import {reviewValueText} from "../reviewLabels.ts";
import {useReviewItemContentStyles} from "./ReviewItemContentStyles.tsx";
import {useReviewEvidenceOpening} from "./useReviewEvidenceOpening.ts";

interface Props
{
    requestId: string;
    packageId: string;
    item: InformationRequestReviewItemDto;
    label: string;
}

const ReviewItemContent = ({requestId, packageId, item, label}: Props) =>
{
    const styles = useReviewItemContentStyles();
    const files = useReviewEvidenceOpening(requestId, packageId, item);
    const id = `information-request-review-item-${item.submissionItemId}-content`;

    if (!item.contentVisible)
    {
        return (
            <Text id={id}
                  className={styles.detail}>
                The submitted content is currently withheld.
            </Text>
        );
    }

    return (
        <div id={id}
             className={styles.content}>
            {item.disposition && (
                <Text id={`${id}-disposition`}
                      className={styles.detail}>
                    {`Answer: ${dispositionLabels[item.disposition]}`}
                </Text>
            )}
            {item.fieldValue !== undefined && item.fieldValue !== null && (
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
                                {[
                                    file.declaredFileName ? `${file.declaredFileName}, version ${file.versionNumber}` : `File version ${file.versionNumber}`,
                                    file.contentLength !== undefined && file.contentLength !== null ? formatInformationRequestSize(file.contentLength) : null,
                                    humanizedKey(file.conformance.toLowerCase()),
                                ].filter(Boolean).join(", ")}
                            </Text>
                            <Button id={`${id}-evidence-${file.evidenceVersionId}-preview`}
                                    size={"small"}
                                    shape={"circular"}
                                    appearance={"subtle"}
                                    aria-label={`Preview version ${file.versionNumber} of ${label}`}
                                    onClick={() => { void files.open(file, "preview"); }}>
                                Preview
                            </Button>
                            <Button id={`${id}-evidence-${file.evidenceVersionId}-download`}
                                    size={"small"}
                                    shape={"circular"}
                                    appearance={"subtle"}
                                    aria-label={`Download version ${file.versionNumber} of ${label}`}
                                    onClick={() => { void files.open(file, "content"); }}>
                                Download
                            </Button>
                        </li>
                    ))}
                </ul>
            )}
            {files.preview && <ReviewEvidencePreviewDialog id={`${id}-preview-dialog`}
                                                           preview={files.preview}
                                                           onClose={files.closePreview}/>}
            {files.failure && (
                <Text id={`${id}-failure`}
                      role={"alert"}>
                    {files.failure}
                </Text>
            )}
        </div>
    );
};

export default ReviewItemContent;
