import {Badge, Text, Title3} from "@fluentui/react-components";
import {
    InformationRequestRequiredness,
    InformationRequestRequirementType,
    InformationRequestTemplateRequirementDto,
    InformationRequestTemplateSectionDto,
} from "../../../models/models.tsx";
import EditorDialog from "../../shared/editor-dialog/EditorDialog.tsx";
import {dispositionLabels} from "../../shared/informationRequestLabels.ts";
import {useRecipientPreviewDialogStyles} from "./RecipientPreviewDialogStyles.tsx";

interface Props
{
    sections: InformationRequestTemplateSectionDto[];
    onDismiss: () => void;
}

const REQUIREDNESS_LABELS: Record<InformationRequestRequiredness, string> = {
    [InformationRequestRequiredness.REQUIRED]: "Required",
    [InformationRequestRequiredness.OPTIONAL]: "Optional",
    [InformationRequestRequiredness.CONDITIONAL]: "Required when it applies",
};

const answerKind = (requirement: InformationRequestTemplateRequirementDto): string =>
{
    if (requirement.requirementType === InformationRequestRequirementType.DOCUMENT) return "Answered by uploading files";
    if (requirement.requirementType === InformationRequestRequirementType.RESPONSE_ATTESTATION) return "Answered by confirming the response";
    return `Answers accepted: ${requirement.permittedDispositions.map(disposition => dispositionLabels[disposition]).join(", ")}`;
};

const RecipientPreviewDialog = ({sections, onDismiss}: Props) =>
{
    const styles = useRecipientPreviewDialogStyles();

    return (
        <EditorDialog id={"information-request-recipient-preview"}
                      title={"Preview as recipient"}
                      readOnly={true}
                      wide={true}
                      onConfirm={onDismiss}
                      onDismiss={onDismiss}>
            {sections.map(section => (
                <section key={section.id}
                         id={`information-request-preview-section-${section.id}`}
                         aria-labelledby={`information-request-preview-section-${section.id}-title`}
                         className={styles.section}>
                    <Title3 id={`information-request-preview-section-${section.id}-title`}
                            as={"h2"}>
                        {section.title}
                    </Title3>
                    {section.helpText && <Text className={styles.muted}>{section.helpText}</Text>}
                    <ul id={`information-request-preview-section-${section.id}-items`}
                        className={styles.list}>
                        {section.requirements.map(requirement => (
                            <li key={requirement.id}
                                id={`information-request-preview-requirement-${requirement.id}`}
                                className={styles.requirement}>
                                <div id={`information-request-preview-requirement-${requirement.id}-prompt`}
                                     className={styles.promptLine}>
                                    <Text weight={"semibold"}>{requirement.prompt}</Text>
                                    <Badge appearance={"outline"}
                                           shape={"circular"}>
                                        {REQUIREDNESS_LABELS[requirement.requiredness]}
                                    </Badge>
                                </div>
                                {requirement.helpText && <Text className={styles.muted}>{requirement.helpText}</Text>}
                                <Text size={200}
                                      className={styles.muted}>
                                    {answerKind(requirement)}
                                </Text>
                            </li>
                        ))}
                    </ul>
                </section>
            ))}
        </EditorDialog>
    );
};

export default RecipientPreviewDialog;
