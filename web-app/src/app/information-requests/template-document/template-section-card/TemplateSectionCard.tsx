import {Badge, Button, Text} from "@fluentui/react-components";
import {AddIcon} from "../../../components/IconBundles.tsx";
import {InformationRequestSubmissionMode} from "../../../models/models.tsx";
import {problemsAt, useTemplateDocument} from "../TemplateDocumentContext.ts";
import {moveItem, removeItem, replaceItem} from "../templateDraftDocument.ts";
import TemplateItemActions from "../template-item-actions/TemplateItemActions.tsx";
import TemplateRequirementRow from "../template-requirement-row/TemplateRequirementRow.tsx";
import {useTemplateSectionCardStyles} from "./TemplateSectionCardStyles.tsx";

interface TemplateSectionCardProps
{
    sectionIndex: number;
    highlightedRequirement?: number;
    onEditSection: () => void;
    onEditRequirement: (requirementIndex: number) => void;
    onAddRequirement: () => void;
}

const TemplateSectionCard = ({
    sectionIndex,
    highlightedRequirement,
    onEditSection,
    onEditRequirement,
    onAddRequirement,
}: TemplateSectionCardProps) =>
{
    const styles = useTemplateSectionCardStyles();
    const {document, readOnly, problems, update} = useTemplateDocument();
    const section = document.sections[sectionIndex];
    const id = `template-section-${sectionIndex}`;
    const title = section.title.trim() || section.sectionKey || "Untitled section";
    const sectionProblems = problemsAt(problems, sectionIndex);

    const moveRequirement = (requirementIndex: number, offset: number) => update(current => ({
        ...current,
        sections: replaceItem(current.sections, sectionIndex, {
            ...current.sections[sectionIndex],
            requirements: moveItem(current.sections[sectionIndex].requirements, requirementIndex, offset),
        }),
    }));
    const removeRequirement = (requirementIndex: number) => update(current => ({
        ...current,
        sections: replaceItem(current.sections, sectionIndex, {
            ...current.sections[sectionIndex],
            requirements: removeItem(current.sections[sectionIndex].requirements, requirementIndex),
        }),
    }));

    return (
        <section id={id}
                 aria-label={title}
                 className={styles.card}>
            <div id={`${id}-header`}
                 className={styles.header}>
                <div id={`${id}-heading`}
                     className={styles.heading}>
                    <Text id={`${id}-title`}
                          weight={"semibold"}>
                        {title}
                    </Text>
                    <Text id={`${id}-key`}
                          className={styles.key}>
                        {section.sectionKey}
                    </Text>
                    {document.submissionMode === InformationRequestSubmissionMode.STAGED && section.submissionStageKey && (
                        <Badge id={`${id}-stage`}
                               appearance={"tint"}
                               color={"informative"}>
                            {`Stage ${section.submissionStageKey}`}
                        </Badge>
                    )}
                </div>
                <TemplateItemActions id={id}
                                     noun={"section"}
                                     name={title}
                                     readOnly={readOnly}
                                     canMoveUp={sectionIndex > 0}
                                     canMoveDown={sectionIndex < document.sections.length - 1}
                                     onEdit={onEditSection}
                                     onMove={offset => update(current => ({
                                         ...current,
                                         sections: moveItem(current.sections, sectionIndex, offset),
                                     }))}
                                     onRemove={() => update(current => ({
                                         ...current,
                                         sections: removeItem(current.sections, sectionIndex),
                                     }))}/>
            </div>
            {section.helpText && (
                <Text id={`${id}-help`}
                      className={styles.help}>
                    {section.helpText}
                </Text>
            )}
            {sectionProblems.map(problem => (
                <Text key={problem.key}
                      id={`${id}-${problem.key}`}
                      className={styles.problem}>
                    {problem.message}
                </Text>
            ))}
            <ul id={`${id}-requirements`}
                aria-label={`Requirements in ${title}`}
                className={styles.requirements}>
                {section.requirements.map((requirement, requirementIndex) => (
                    <TemplateRequirementRow key={`${requirement.requirementKey}-${requirementIndex}`}
                                            sectionIndex={sectionIndex}
                                            requirementIndex={requirementIndex}
                                            highlighted={highlightedRequirement === requirementIndex}
                                            onEdit={() => onEditRequirement(requirementIndex)}
                                            onMove={offset => moveRequirement(requirementIndex, offset)}
                                            onRemove={() => removeRequirement(requirementIndex)}/>
                ))}
            </ul>
            {!readOnly && (
                <Button id={`${id}-add-requirement`}
                        appearance={"subtle"}
                        shape={"circular"}
                        icon={<AddIcon/>}
                        aria-label={`Add requirement to ${title}`}
                        className={styles.addButton}
                        onClick={onAddRequirement}>
                    Add requirement
                </Button>
            )}
        </section>
    );
};

export default TemplateSectionCard;
