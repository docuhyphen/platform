import {Badge, Text} from "@fluentui/react-components";
import {
    InformationRequestCorrectionDto,
    InformationRequestRemediationDto,
    InformationRequestReviewItemDto,
} from "../../../models/models.tsx";
import {humanizedKey} from "../../submission/submissionLabels.ts";
import {correctionStateLabels} from "../reviewLabels.ts";
import {useReviewCorrectionSummaryStyles} from "./ReviewCorrectionSummaryStyles.tsx";

interface Props
{
    correction: InformationRequestCorrectionDto;
    items: InformationRequestReviewItemDto[];
    remediations: InformationRequestRemediationDto[];
}

const ReviewCorrectionSummary = ({correction, items, remediations}: Props) =>
{
    const styles = useReviewCorrectionSummaryStyles();
    const keys = new Map(items.map(item => [item.requirementId, item.requirementKey]));
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
                            {humanizedKey(keys.get(requirementId) ?? "requested item")}
                        </Text>
                    </li>
                ))}
            </ul>
            {correction.evidenceVersionIds.length > 0 && (
                <Text id={`${id}-files`}
                      className={styles.detail}>
                    {`${correction.evidenceVersionIds.length} returned file versions may be replaced or withdrawn.`}
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
