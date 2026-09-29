import {Badge, mergeClasses, Text} from "@fluentui/react-components";
import {problemsAt, useTemplateDocument} from "../TemplateDocumentContext.ts";
import {requirednessLabels, requirementTypeLabels} from "../templateAuthoringLabels.ts";
import {requirementLabel} from "../templateDraftRules.ts";
import TemplateItemActions from "../template-item-actions/TemplateItemActions.tsx";
import {useTemplateRequirementRowStyles} from "./TemplateRequirementRowStyles.tsx";
import {InformationRequestRequiredness} from "../../../models/models.tsx";

interface TemplateRequirementRowProps
{
    sectionIndex: number;
    requirementIndex: number;
    highlighted: boolean;
    onEdit: () => void;
    onMove: (offset: number) => void;
    onRemove: () => void;
}

const TemplateRequirementRow = ({
    sectionIndex,
    requirementIndex,
    highlighted,
    onEdit,
    onMove,
    onRemove,
}: TemplateRequirementRowProps) =>
{
    const styles = useTemplateRequirementRowStyles();
    const {document, readOnly, problems} = useTemplateDocument();
    const requirements = document.sections[sectionIndex].requirements;
    const requirement = requirements[requirementIndex];
    const id = `template-requirement-${sectionIndex}-${requirementIndex}`;
    const label = requirementLabel(requirement);
    const rowProblems = problemsAt(problems, sectionIndex, requirementIndex);

    return (
        <li id={id}
            aria-current={highlighted ? "true" : undefined}
            className={mergeClasses(styles.row, highlighted && styles.highlighted)}>
            <div id={`${id}-summary`}
                 className={styles.summary}>
                <Text id={`${id}-prompt`}>{label}</Text>
                <div id={`${id}-facts`}
                     className={styles.facts}>
                    <Text id={`${id}-key`}
                          className={styles.key}>
                        {requirement.requirementKey}
                    </Text>
                    <Badge id={`${id}-type`}
                           appearance={"tint"}
                           color={"brand"}>
                        {requirementTypeLabels[requirement.requirementType]}
                    </Badge>
                    <Badge id={`${id}-requiredness`}
                           appearance={"outline"}
                           color={"informative"}>
                        {requirednessLabels[requirement.requiredness ?? InformationRequestRequiredness.OPTIONAL]}
                    </Badge>
                </div>
                {rowProblems.map(problem => (
                    <Text key={problem.key}
                          id={`${id}-${problem.key}`}
                          className={styles.problem}>
                        {problem.message}
                    </Text>
                ))}
            </div>
            <TemplateItemActions id={id}
                                 noun={"requirement"}
                                 name={label}
                                 readOnly={readOnly}
                                 canMoveUp={requirementIndex > 0}
                                 canMoveDown={requirementIndex < requirements.length - 1}
                                 onEdit={onEdit}
                                 onMove={onMove}
                                 onRemove={onRemove}/>
        </li>
    );
};

export default TemplateRequirementRow;
