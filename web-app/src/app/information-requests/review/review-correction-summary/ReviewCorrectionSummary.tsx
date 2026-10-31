import {Badge, Text} from "@fluentui/react-components";
import {InformationRequestCorrectionDto, InformationRequestRemediationDto} from "../../../models/models.tsx";
import {correctionStateLabels, returnedFilesText} from "../reviewLabels.ts";
import {useReviewCorrectionSummaryStyles} from "./ReviewCorrectionSummaryStyles.tsx";

interface Props
{
    correction: InformationRequestCorrectionDto;
    remediations: InformationRequestRemediationDto[];
    labelOf: (requirementId: string) => string;
}

const ReviewCorrectionSummary = ({correction, remediations, labelOf}: Props) =>
{
    const styles = useReviewCorrectionSummaryStyles();
    const id = "information-request-review-correction";

    return (
        <section id={id}
                 className={styles.section}>
            <div id={`${id}-header`}
                 className={styles.header}>
                <Text id={`${id}-title`}
                      weight={"semibold"}>
                    Returned for correction
                </Text>
                <Badge id={`${id}-state`}
                       appearance={"outline"}
                       color={"warning"}>
                    {correctionStateLabels[correction.state]}
                </Badge>
            </div>
            <ul id={`${id}-requirements`}
                className={styles.list}>
                {correction.requirementIds.map(requirementId => (
                    <li id={`${id}-requirement-${requirementId}`}
                        key={requirementId}>
                        <Text id={`${id}-requirement-${requirementId}-label`}>
                            {labelOf(requirementId)}
                        </Text>
                    </li>
                ))}
            </ul>
            {correction.evidence.length > 0 && (
                <Text id={`${id}-files`}
                      className={styles.detail}>
                    {returnedFilesText(correction.evidence)}
                </Text>
            )}
            {correction.undisclosedItemCount > 0 && (
                <Text id={`${id}-undisclosed`}
                      className={styles.detail}>
                    {`${correction.undisclosedItemCount} other returned items concern parts you cannot view.`}
                </Text>
            )}
            {remediations.length > 0 && (
                <Text id={`${id}-remediations`}
                      className={styles.detail}>
                    {`${remediations.length} findings were addressed by a later submission.`}
                </Text>
            )}
        </section>
    );
};

export default ReviewCorrectionSummary;
