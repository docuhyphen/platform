import {useState} from "react";
import EditorDialog from "../../shared/editor-dialog/EditorDialog.tsx";
import ChoiceSelect from "../../shared/choice-select/ChoiceSelect.tsx";
import NumberField from "../../shared/number-field/NumberField.tsx";
import TextField from "../../shared/text-field/TextField.tsx";
import {useTemplateDocument} from "../TemplateDocumentContext.ts";
import {replaceItem} from "../templateDraftDocument.ts";

interface TemplateGroupDialogProps
{
    groupIndex?: number;
    onClose: () => void;
}

const DIRECTLY_IN_REQUEST = "";

const TemplateGroupDialog = ({groupIndex, onClose}: TemplateGroupDialogProps) =>
{
    const {document, readOnly, update} = useTemplateDocument();
    const existing = groupIndex === undefined ? undefined : document.groups[groupIndex];
    const [groupKey, setGroupKey] = useState(existing?.groupKey ?? "");
    const [parentGroupKey, setParentGroupKey] = useState(existing?.parentGroupKey ?? DIRECTLY_IN_REQUEST);
    const [minOccurrences, setMinOccurrences] = useState<number | undefined>(existing?.minOccurrences ?? 0);
    const [maxOccurrences, setMaxOccurrences] = useState<number | undefined>(existing?.maxOccurrences);

    const save = () =>
    {
        const key = groupKey.trim();
        const group = {
            groupKey: key,
            ...(parentGroupKey ? {parentGroupKey} : {}),
            minOccurrences: minOccurrences ?? 0,
            ...(maxOccurrences !== undefined ? {maxOccurrences} : {}),
        };
        update(current =>
        {
            if (groupIndex === undefined) return {...current, groups: [...current.groups, group]};
            const previousKey = current.groups[groupIndex].groupKey;
            const renamed = (candidate?: string) => candidate === previousKey ? key : candidate;
            return {
                ...current,
                groups: replaceItem(current.groups, groupIndex, group)
                    .map(other => ({...other, parentGroupKey: renamed(other.parentGroupKey)})),
                sections: current.sections.map(section => ({
                    ...section,
                    requirements: section.requirements.map(requirement => ({
                        ...requirement,
                        occurrenceAnchorKey: renamed(requirement.occurrenceAnchorKey),
                    })),
                })),
            };
        });
        onClose();
    };

    return (
        <EditorDialog id={"template-group-dialog"}
                      title={readOnly ? "Group" : existing ? "Edit group" : "Add group"}
                      readOnly={readOnly}
                      confirmDisabled={!groupKey.trim()}
                      onConfirm={save}
                      onDismiss={onClose}>
            <TextField id={"template-group-key-input"}
                       label={"Key"}
                       value={groupKey}
                       hint={"Names the entry a respondent adds, for example item or person."}
                       disabled={readOnly}
                       onChange={setGroupKey}/>
            <ChoiceSelect id={"template-group-parent-select"}
                          label={"Nested in"}
                          value={parentGroupKey}
                          options={[
                              {value: DIRECTLY_IN_REQUEST, label: "Directly in the request"},
                              ...document.groups
                                  .filter((_, index) => index !== groupIndex)
                                  .map(group => ({value: group.groupKey, label: `Each ${group.groupKey}`})),
                          ]}
                          disabled={readOnly}
                          onChange={setParentGroupKey}/>
            <NumberField id={"template-group-min-input"}
                         label={"Minimum entries"}
                         value={minOccurrences}
                         min={0}
                         disabled={readOnly}
                         onChange={setMinOccurrences}/>
            <NumberField id={"template-group-max-input"}
                         label={"Maximum entries"}
                         value={maxOccurrences}
                         min={1}
                         hint={"Leave empty for no limit."}
                         disabled={readOnly}
                         onChange={setMaxOccurrences}/>
        </EditorDialog>
    );
};

export default TemplateGroupDialog;
