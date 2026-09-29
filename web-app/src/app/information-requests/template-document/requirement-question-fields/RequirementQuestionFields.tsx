import {useState} from "react";
import {InformationRequestRequirementType} from "../../../models/models.tsx";
import ChoiceSelect from "../../shared/choice-select/ChoiceSelect.tsx";
import {optionsFrom} from "../../shared/choice-select/choiceOptions.ts";
import TextField from "../../shared/text-field/TextField.tsx";
import {useTemplateDocument} from "../TemplateDocumentContext.ts";
import {requirementTypeLabels} from "../templateAuthoringLabels.ts";
import {keyFromLabel} from "../templateDraftDocument.ts";
import {RequirementDraft, RequirementFieldsProps, withRequirementType} from "../template-requirement-dialog/requirementDraft.ts";
import {useRequirementQuestionFieldsStyles} from "./RequirementQuestionFieldsStyles.tsx";

interface RequirementQuestionFieldsProps extends RequirementFieldsProps
{
    keyLocked: boolean;
    onDraftReplaced: (draft: RequirementDraft) => void;
}

const KEY_LENGTH = 60;

const keyFromPrompt = (prompt: string): string => keyFromLabel(prompt).slice(0, KEY_LENGTH).replace(/-+$/, "");

const RequirementQuestionFields = ({id, draft, readOnly, keyLocked, onChange, onDraftReplaced}: RequirementQuestionFieldsProps) =>
{
    const styles = useRequirementQuestionFieldsStyles();
    const {fieldBindings} = useTemplateDocument();
    const [keyEdited, setKeyEdited] = useState(() =>
        keyLocked || (draft.requirementKey !== "" && draft.requirementKey !== keyFromPrompt(draft.prompt)));
    const typed = draft.requirementType === InformationRequestRequirementType.FIELD;

    return (
        <div id={`${id}-question`}
             className={styles.grid}>
            <ChoiceSelect id={`${id}-type-select`}
                          label={"Requirement type"}
                          value={draft.requirementType}
                          options={optionsFrom(requirementTypeLabels)}
                          disabled={readOnly || keyLocked}
                          hint={keyLocked ? "A saved requirement keeps its type. Add a new requirement to ask differently." : undefined}
                          onChange={value => onDraftReplaced(withRequirementType(draft, value))}/>
            <TextField id={`${id}-prompt-input`}
                       label={"Prompt"}
                       value={draft.prompt}
                       multiline={true}
                       disabled={readOnly}
                       className={styles.wide}
                       onChange={prompt => onChange(keyEdited ? {prompt} : {prompt, requirementKey: keyFromPrompt(prompt)})}/>
            <TextField id={`${id}-key-input`}
                       label={"Key"}
                       value={draft.requirementKey}
                       disabled={readOnly}
                       hint={"Identifies this requirement across versions. Changing it asks a new question."}
                       onChange={requirementKey =>
                       {
                           setKeyEdited(true);
                           onChange({requirementKey});
                       }}/>
            {typed && (
                <ChoiceSelect id={`${id}-field-select`}
                              label={"Collected Field"}
                              value={draft.collectedFieldDefinitionId ?? ""}
                              placeholder={"Choose a Field"}
                              options={fieldBindings.map(binding => ({value: binding.fieldDefinitionId, label: binding.label}))}
                              disabled={readOnly}
                              hint={fieldBindings.length === 0 ? "Choose a Request Schema in Settings to list its Fields." : undefined}
                              onChange={collectedFieldDefinitionId => onChange({collectedFieldDefinitionId})}/>
            )}
            <TextField id={`${id}-help-input`}
                       label={"Help text"}
                       value={draft.helpText ?? ""}
                       multiline={true}
                       disabled={readOnly}
                       className={styles.wide}
                       onChange={helpText => onChange({helpText})}/>
        </div>
    );
};

export default RequirementQuestionFields;
