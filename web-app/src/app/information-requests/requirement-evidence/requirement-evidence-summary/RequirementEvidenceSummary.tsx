import {Badge, Text} from "@fluentui/react-components";
import {InformationRequestEvidenceEvaluationDto} from "../../../models/models.tsx";
import {findingMessage, requirementStatePresentation} from "../requirementEvidenceLabels.ts";
import {useRequirementEvidenceSummaryStyles} from "./RequirementEvidenceSummaryStyles.tsx";

interface Props {
    id: string;
    prompt: string;
    required?: boolean;
    helpText?: string | null;
    evaluation?: InformationRequestEvidenceEvaluationDto | null;
}

const RequirementEvidenceSummary = ({id, prompt, required, helpText, evaluation}: Props) => {
    const styles = useRequirementEvidenceSummaryStyles();
    const state = evaluation ? requirementStatePresentation[evaluation.state] : null;

    return (
        <>
            <div id={`${id}-header`}
                 className={styles.header}>
                <Text id={`${id}-prompt`}
                      className={styles.prompt}>
                    {prompt}
                </Text>
                {required !== undefined && (
                    <Badge id={`${id}-requiredness`}
                           appearance={required ? "tint" : "outline"}
                           shape={"circular"}
                           size={"small"}>
                        {required ? "Required" : "Optional"}
                    </Badge>
                )}
                {state && (
                    <Badge id={`${id}-state`}
                           appearance="filled"
                           color={state.color}>
                        {state.label}
                    </Badge>
                )}
            </div>
            {helpText && (
                <Text id={`${id}-help`}
                      size={200}
                      className={styles.note}>
                    {helpText}
                </Text>
            )}
            {evaluation?.satisfiedBySubstitute && (
                <Text id={`${id}-substitute`}
                      className={styles.note}>
                    An alternative Requirement provides this evidence.
                </Text>
            )}
            {evaluation && evaluation.findings.length > 0 && (
                <ul id={`${id}-findings`}
                    className={styles.findings}>
                    {evaluation.findings.map(finding => (
                        <li id={`${id}-finding-${finding.code.toLowerCase()}`}
                            key={finding.code}>
                            {findingMessage(finding)}
                        </li>
                    ))}
                </ul>
            )}
        </>
    );
};

export default RequirementEvidenceSummary;
