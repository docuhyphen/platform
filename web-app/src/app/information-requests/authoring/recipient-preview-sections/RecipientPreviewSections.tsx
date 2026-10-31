import {Badge, Text, Title3} from "@fluentui/react-components";
import {
    InformationRequestRequiredness,
    InformationRequestRequirementType,
    InformationRequestTemplateRequirementDto,
    InformationRequestTemplateSectionDto,
} from "../../../models/models.tsx";
import {dispositionLabels} from "../../shared/informationRequestLabels.ts";
import {useRecipientPreviewSectionsStyles} from "./RecipientPreviewSectionsStyles.tsx";

interface Props {
    sections: InformationRequestTemplateSectionDto[];
}

const REQUIREDNESS_LABELS: Record<InformationRequestRequiredness, string> = {
    [InformationRequestRequiredness.REQUIRED]: "Required",
    [InformationRequestRequiredness.OPTIONAL]: "Optional",
    [InformationRequestRequiredness.CONDITIONAL]: "Required when it applies",
};

const answerKind = (requirement: InformationRequestTemplateRequirementDto): string => {
    if (requirement.requirementType === InformationRequestRequirementType.DOCUMENT) return "Answered by uploading files";
    if (requirement.requirementType === InformationRequestRequirementType.RESPONSE_ATTESTATION) return "Answered by confirming the response";
    return `Answers accepted: ${requirement.permittedDispositions.map(disposition => dispositionLabels[disposition]).join(", ")}`;
};

const RecipientPreviewSections = ({sections}: Props) => {
    const styles = useRecipientPreviewSectionsStyles();

    return (
        <>
            {sections.map(section => (
                <section key={section.id}
                         id={`information-request-preview-section-${section.id}`}
                         aria-labelledby={`information-request-preview-section-${section.id}-title`}
                         className={styles.section}>
                    <Title3 id={`information-request-preview-section-${section.id}-title`}
                            as={"h2"}>
                        {section.title}
                    </Title3>
                    {section.helpText && (
                        <Text id={`information-request-preview-section-${section.id}-help`}
                              className={styles.muted}>
                            {section.helpText}
                        </Text>
                    )}
                    <ul id={`information-request-preview-section-${section.id}-items`}
                        className={styles.list}>
                        {section.requirements.map(requirement => (
                            <li key={requirement.id}
                                id={`information-request-preview-requirement-${requirement.id}`}
                                className={styles.requirement}>
                                <div id={`information-request-preview-requirement-${requirement.id}-prompt`}
                                     className={styles.promptLine}>
                                    <Text id={`information-request-preview-requirement-${requirement.id}-prompt-text`}
                                          weight={"semibold"}>
                                        {requirement.prompt}
                                    </Text>
                                    <Badge id={`information-request-preview-requirement-${requirement.id}-requiredness`}
                                           appearance={"outline"}
                                           shape={"circular"}>
                                        {REQUIREDNESS_LABELS[requirement.requiredness]}
                                    </Badge>
                                </div>
                                {requirement.helpText && (
                                    <Text id={`information-request-preview-requirement-${requirement.id}-help`}
                                          className={styles.muted}>
                                        {requirement.helpText}
                                    </Text>
                                )}
                                <Text id={`information-request-preview-requirement-${requirement.id}-answer-kind`}
                                      size={200}
                                      className={styles.muted}>
                                    {answerKind(requirement)}
                                </Text>
                                {requirement.conditionalRuleKey && (
                                    <Text id={`information-request-preview-requirement-${requirement.id}-condition`}
                                          size={200}
                                          className={styles.muted}>
                                        Asked only when their earlier answers call for it.
                                    </Text>
                                )}
                                {requirement.confidentialityCompartmentKey && (
                                    <Text id={`information-request-preview-requirement-${requirement.id}-protected`}
                                          size={200}
                                          className={styles.muted}>
                                        Shown only after they confirm it is them again.
                                    </Text>
                                )}
                            </li>
                        ))}
                    </ul>
                </section>
            ))}
        </>
    );
};

export default RecipientPreviewSections;
