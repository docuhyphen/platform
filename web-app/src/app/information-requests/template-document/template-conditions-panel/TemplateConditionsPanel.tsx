import {useTemplateDocument} from "../TemplateDocumentContext.ts";
import {hiddenDataPolicyLabels} from "../templateAuthoringLabels.ts";
import {removeItem} from "../templateDraftDocument.ts";
import TemplateKeyedList from "../template-keyed-list/TemplateKeyedList.tsx";
import TemplateConditionDialog from "../template-condition-dialog/TemplateConditionDialog.tsx";
import {problemsForItem, useFocusedItem} from "../useFocusedItem.ts";
import {InformationRequestConditionHiddenDataPolicy} from "../../../models/models.tsx";

const TemplateConditionsPanel = () =>
{
    const {document, readOnly, problems, update} = useTemplateDocument();
    const {editing, setEditing, highlighted} = useFocusedItem("conditions");
    const usersOf = (ruleKey: string) => document.sections
        .flatMap(section => section.requirements)
        .filter(requirement => requirement.conditionalRuleKey === ruleKey).length;

    return (
        <>
            <TemplateKeyedList id={"template-conditions-panel"}
                               noun={"condition"}
                               items={document.conditionRules.map((rule, index) => ({
                                   name: rule.ruleKey,
                                   detail: `${rule.predicates.length} tests, decides ${usersOf(rule.ruleKey)} requirements. `
                                       + hiddenDataPolicyLabels[rule.hiddenDataPolicy ?? InformationRequestConditionHiddenDataPolicy.RETAIN_SECURELY],
                                   problems: problemsForItem(problems, "conditions", index),
                               }))}
                               readOnly={readOnly}
                               emptyText={"No conditions. A condition decides when a requirement that applies only sometimes is asked."}
                               addLabel={"Add condition"}
                               highlightedIndex={highlighted}
                               onAdd={() => setEditing("new")}
                               onEdit={index => setEditing(index)}
                               onRemove={index => update(current => ({
                                   ...current,
                                   conditionRules: removeItem(current.conditionRules, index),
                               }))}/>
            {editing !== null && (
                <TemplateConditionDialog ruleIndex={editing === "new" ? undefined : editing}
                                         onClose={() => setEditing(null)}/>
            )}
        </>
    );
};

export default TemplateConditionsPanel;
