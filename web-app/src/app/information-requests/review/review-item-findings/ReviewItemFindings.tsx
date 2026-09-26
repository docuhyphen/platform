import {Badge, Text} from "@fluentui/react-components";
import {InformationRequestFindingSeverity, InformationRequestReviewFindingDto} from "../../../models/models.tsx";
import {correctionScopeLabels, findingSeverityLabels, visibilityLabels} from "../reviewLabels.ts";
import {useReviewItemFindingsStyles} from "./ReviewItemFindingsStyles.tsx";

interface Props
{
    itemId: string;
    findings: InformationRequestReviewFindingDto[];
}

const severityColor = (severity: InformationRequestFindingSeverity) =>
    severity === InformationRequestFindingSeverity.CRITICAL || severity === InformationRequestFindingSeverity.MAJOR ? "danger" : "warning";

const ReviewItemFindings = ({itemId, findings}: Props) =>
{
    const styles = useReviewItemFindingsStyles();
    const id = `information-request-review-item-${itemId}-findings`;

    if (findings.length === 0) return null;

    return (
        <ul id={id}
            className={styles.findings}>
            {findings.map(finding => (
                <li id={`${id}-${finding.id}`}
                    key={finding.id}
                    className={styles.finding}>
                    <div id={`${id}-${finding.id}-header`}
                         className={styles.header}>
                        <Badge id={`${id}-${finding.id}-severity`}
                               appearance={"tint"}
                               color={severityColor(finding.severity)}>
                            {findingSeverityLabels[finding.severity]}
                        </Badge>
                        <Text id={`${id}-${finding.id}-reason`}
                              weight={"semibold"}>
                            {finding.reasonCode}
                        </Text>
                        <Text id={`${id}-${finding.id}-scope`}
                              className={styles.detail}>
                            {`${correctionScopeLabels[finding.correctionScope]}, ${visibilityLabels[finding.visibility].toLowerCase()}`}
                        </Text>
                    </div>
                    <Text id={`${id}-${finding.id}-narrative`}>
                        {finding.narrative}
                    </Text>
                    {finding.retestResult && (
                        <Text id={`${id}-${finding.id}-retest`}
                              className={styles.detail}>
                            {`Retest of an earlier finding: ${finding.retestResult.toLowerCase()}`}
                        </Text>
                    )}
                </li>
            ))}
        </ul>
    );
};

export default ReviewItemFindings;
