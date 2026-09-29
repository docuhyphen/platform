import {Badge, Text} from "@fluentui/react-components";
import {InformationRequestRequiredness, InformationRequestTemplateRequirementDto} from "../../../models/models.tsx";
import {useResponseRequirementHeaderStyles} from "./ResponseRequirementHeaderStyles.tsx";

interface Props
{
    requirement: InformationRequestTemplateRequirementDto;
    elementId: string;
}

const ResponseRequirementHeader = ({requirement, elementId}: Props) =>
{
    const styles = useResponseRequirementHeaderStyles();
    const required = requirement.requiredness !== InformationRequestRequiredness.OPTIONAL;

    return (
        <div id={`information-request-response-header-${elementId}`}
             className={styles.header}>
            <div id={`information-request-response-heading-line-${elementId}`}
                 className={styles.promptLine}>
                <Text id={`information-request-response-prompt-${elementId}`}
                      className={styles.prompt}>
                    {requirement.prompt}
                </Text>
                <Badge id={`information-request-response-requiredness-${elementId}`}
                       appearance={required ? "tint" : "outline"}
                       shape={"circular"}
                       size={"small"}>
                    {required ? "Required" : "Optional"}
                </Badge>
            </div>
            {requirement.helpText && (
                <Text id={`information-request-response-help-${elementId}`}
                      size={200}
                      className={styles.help}>
                    {requirement.helpText}
                </Text>
            )}
        </div>
    );
};

export default ResponseRequirementHeader;
