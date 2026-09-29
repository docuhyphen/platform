import {useState} from "react";
import {InformationRequestSubmissionMode} from "../../../models/models.tsx";
import EditorDialog from "../../shared/editor-dialog/EditorDialog.tsx";
import TextField from "../../shared/text-field/TextField.tsx";
import {useTemplateDocument} from "../TemplateDocumentContext.ts";
import {keyFromLabel, replaceItem} from "../templateDraftDocument.ts";

interface TemplateSectionDialogProps
{
    sectionIndex?: number;
    onClose: () => void;
}

const TemplateSectionDialog = ({sectionIndex, onClose}: TemplateSectionDialogProps) =>
{
    const {document, readOnly, update} = useTemplateDocument();
    const existing = sectionIndex === undefined ? undefined : document.sections[sectionIndex];
    const [title, setTitle] = useState(existing?.title ?? "");
    const [sectionKey, setSectionKey] = useState(existing?.sectionKey ?? "");
    const [keyEdited, setKeyEdited] = useState(existing !== undefined);
    const [helpText, setHelpText] = useState(existing?.helpText ?? "");
    const [stageKey, setStageKey] = useState(existing?.submissionStageKey ?? "");
    const staged = document.submissionMode === InformationRequestSubmissionMode.STAGED;

    const save = () =>
    {
        const section = {
            sectionKey: sectionKey.trim(),
            title: title.trim(),
            ...(helpText.trim() ? {helpText: helpText.trim()} : {}),
            ...(staged && stageKey.trim() ? {submissionStageKey: stageKey.trim()} : {}),
            requirements: existing?.requirements ?? [],
        };
        update(current =>
        {
            if (sectionIndex === undefined) return {...current, sections: [...current.sections, section]};
            const previousKey = current.sections[sectionIndex].sectionKey;
            return {
                ...current,
                sections: replaceItem(current.sections, sectionIndex, section),
                reviewStages: current.reviewStages.map(stage => ({
                    ...stage,
                    sectionKeys: (stage.sectionKeys ?? []).map(key => key === previousKey ? section.sectionKey : key),
                })),
            };
        });
        onClose();
    };

    return (
        <EditorDialog id={"template-section-dialog"}
                      title={readOnly ? "Section" : existing ? "Edit section" : "Add section"}
                      readOnly={readOnly}
                      confirmDisabled={!title.trim() || !sectionKey.trim()}
                      onConfirm={save}
                      onDismiss={onClose}>
            <TextField id={"template-section-title-input"}
                       label={"Title"}
                       value={title}
                       disabled={readOnly}
                       onChange={value =>
                       {
                           setTitle(value);
                           if (!keyEdited) setSectionKey(keyFromLabel(value));
                       }}/>
            <TextField id={"template-section-key-input"}
                       label={"Key"}
                       value={sectionKey}
                       hint={"Lowercase letters, digits, and inner hyphens. Review stages refer to sections by this key."}
                       disabled={readOnly}
                       onChange={value =>
                       {
                           setKeyEdited(true);
                           setSectionKey(value);
                       }}/>
            <TextField id={"template-section-help-input"}
                       label={"Help text"}
                       value={helpText}
                       multiline={true}
                       disabled={readOnly}
                       onChange={setHelpText}/>
            {staged && (
                <TextField id={"template-section-stage-input"}
                           label={"Submission stage"}
                           value={stageKey}
                           hint={"Sections that share a stage key are submitted together."}
                           disabled={readOnly}
                           onChange={setStageKey}/>
            )}
        </EditorDialog>
    );
};

export default TemplateSectionDialog;
