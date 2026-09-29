import {useState} from "react";
import {Button, Text} from "@fluentui/react-components";
import {AddIcon} from "../../../components/IconBundles.tsx";
import {
    FieldOperator,
    InformationRequestConditionHiddenDataPolicy,
    InformationRequestRequirementType,
    InformationRequestTemplateConditionPredicateRequest,
} from "../../../models/models.tsx";
import ChoiceSelect from "../../shared/choice-select/ChoiceSelect.tsx";
import {optionsFrom} from "../../shared/choice-select/choiceOptions.ts";
import EditorDialog from "../../shared/editor-dialog/EditorDialog.tsx";
import TextField from "../../shared/text-field/TextField.tsx";
import {useTemplateDocument} from "../TemplateDocumentContext.ts";
import {hiddenDataPolicyLabels} from "../templateAuthoringLabels.ts";
import {removeItem, replaceItem} from "../templateDraftDocument.ts";
import {requirementLabel} from "../templateDraftRules.ts";
import ConditionPredicateRow, {PredicateSource} from "../condition-predicate-row/ConditionPredicateRow.tsx";
import {useTemplateConditionDialogStyles} from "./TemplateConditionDialogStyles.tsx";

interface TemplateConditionDialogProps
{
    ruleIndex?: number;
    onClose: () => void;
}

const TemplateConditionDialog = ({ruleIndex, onClose}: TemplateConditionDialogProps) =>
{
    const styles = useTemplateConditionDialogStyles();
    const {document, readOnly, fieldBindings, update} = useTemplateDocument();
    const existing = ruleIndex === undefined ? undefined : document.conditionRules[ruleIndex];
    const [ruleKey, setRuleKey] = useState(existing?.ruleKey ?? "");
    const [hiddenDataPolicy, setHiddenDataPolicy] = useState(
        existing?.hiddenDataPolicy ?? InformationRequestConditionHiddenDataPolicy.RETAIN_SECURELY);
    const [predicates, setPredicates] = useState<InformationRequestTemplateConditionPredicateRequest[]>(
        existing?.predicates ?? [{operator: FieldOperator.EQUALS}]);
    const requirements = document.sections.flatMap(section => section.requirements);
    const sources: PredicateSource[] = [
        ...requirements.map(requirement => ({
            value: `requirement:${requirement.requirementKey}`,
            label: `Answer to "${requirementLabel(requirement)}"`,
        })),
        ...requirements
            .filter(requirement => requirement.requirementType === InformationRequestRequirementType.FIELD)
            .flatMap(requirement =>
            {
                const binding = fieldBindings.find(candidate => candidate.fieldDefinitionId === requirement.collectedFieldDefinitionId);
                return binding
                    ? [{value: `field:${binding.fieldDefinitionId}`, label: `Value of ${binding.label}`, valueType: binding.valueType}]
                    : [];
            }),
    ];

    const save = () =>
    {
        const key = ruleKey.trim();
        const rule = {ruleKey: key, expressionVersion: existing?.expressionVersion ?? 1, hiddenDataPolicy, predicates};
        update(current =>
        {
            if (ruleIndex === undefined) return {...current, conditionRules: [...current.conditionRules, rule]};
            const previousKey = current.conditionRules[ruleIndex].ruleKey;
            return {
                ...current,
                conditionRules: replaceItem(current.conditionRules, ruleIndex, rule),
                sections: current.sections.map(section => ({
                    ...section,
                    requirements: section.requirements.map(requirement => requirement.conditionalRuleKey === previousKey
                        ? {...requirement, conditionalRuleKey: key}
                        : requirement),
                })),
            };
        });
        onClose();
    };

    return (
        <EditorDialog id={"template-condition-dialog"}
                      title={readOnly ? "Condition" : existing ? "Edit condition" : "Add condition"}
                      readOnly={readOnly}
                      wide={true}
                      confirmDisabled={!ruleKey.trim() || predicates.length === 0}
                      onConfirm={save}
                      onDismiss={onClose}>
            <TextField id={"template-condition-key-input"}
                       label={"Key"}
                       value={ruleKey}
                       hint={"Requirements that apply only sometimes name the condition by this key."}
                       disabled={readOnly}
                       onChange={setRuleKey}/>
            <ChoiceSelect<InformationRequestConditionHiddenDataPolicy> id={"template-condition-hidden-select"}
                          label={"When the condition turns false"}
                          value={hiddenDataPolicy}
                          options={optionsFrom(hiddenDataPolicyLabels)}
                          disabled={readOnly}
                          onChange={setHiddenDataPolicy}/>
            <Text id={"template-condition-predicates-heading"}
                  weight={"semibold"}>
                The condition is true when every test passes
            </Text>
            <div id={"template-condition-predicates"}
                 className={styles.predicates}>
                {predicates.map((predicate, index) => (
                    <ConditionPredicateRow key={index}
                                           id={`template-condition-predicate-${index}`}
                                           predicate={predicate}
                                           sources={sources}
                                           readOnly={readOnly}
                                           onChange={next => setPredicates(current => replaceItem(current, index, next))}
                                           onRemove={() => setPredicates(current => removeItem(current, index))}/>
                ))}
            </div>
            {!readOnly && (
                <Button id={"template-condition-add-predicate"}
                        appearance={"secondary"}
                        shape={"circular"}
                        icon={<AddIcon/>}
                        className={styles.addButton}
                        onClick={() => setPredicates(current => [...current, {operator: FieldOperator.EQUALS}])}>
                    Add test
                </Button>
            )}
        </EditorDialog>
    );
};

export default TemplateConditionDialog;
