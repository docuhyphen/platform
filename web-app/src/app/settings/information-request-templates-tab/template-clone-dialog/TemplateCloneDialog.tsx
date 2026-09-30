import {useState} from "react";
import {
    CreateInformationRequestTemplateRequest,
    InformationRequestTemplateDto,
    InformationRequestTemplateScopeKind,
} from "../../../models/models.tsx";
import ChoiceSelect from "../../../information-requests/shared/choice-select/ChoiceSelect.tsx";
import EditorDialog from "../../../information-requests/shared/editor-dialog/EditorDialog.tsx";
import TextField from "../../../information-requests/shared/text-field/TextField.tsx";
import {keyFromLabel} from "../../../information-requests/template-document/templateDraftDocument.ts";

interface TemplateCloneDialogProps
{
    template: InformationRequestTemplateDto;
    targets: InformationRequestTemplateScopeKind[];
    busy: boolean;
    onConfirm: (target: CreateInformationRequestTemplateRequest) => void;
    onDismiss: () => void;
}

const targetLabels: Record<InformationRequestTemplateScopeKind, string> = {
    [InformationRequestTemplateScopeKind.PERSONAL]: "My Templates",
    [InformationRequestTemplateScopeKind.ORGANIZATION]: "Organization",
    [InformationRequestTemplateScopeKind.PLATFORM]: "Platform",
};

const TemplateCloneDialog = ({template, targets, busy, onConfirm, onDismiss}: TemplateCloneDialogProps) =>
{
    const [displayName, setDisplayName] = useState("");
    const [templateKey, setTemplateKey] = useState("");
    const [keyEdited, setKeyEdited] = useState(false);
    const [scopeKind, setScopeKind] = useState(targets.includes(template.scopeKind) ? template.scopeKind : targets[0]);

    return (
        <EditorDialog id={"information-request-template-clone-dialog"}
                      title={"Copy as a new Template"}
                      busy={busy}
                      confirmLabel={"Copy"}
                      confirmDisabled={!displayName.trim() || !templateKey.trim()}
                      onConfirm={() => onConfirm({
                          namespace: template.namespace,
                          templateKey: templateKey.trim(),
                          displayName: displayName.trim(),
                          scopeKind,
                      })}
                      onDismiss={onDismiss}>
            {targets.length > 1 && (
                <ChoiceSelect<InformationRequestTemplateScopeKind> id={"information-request-template-clone-target"}
                                                                   label={"Copy into"}
                                                                   value={scopeKind}
                                                                   options={targets.map(target => ({value: target, label: targetLabels[target]}))}
                                                                   onChange={setScopeKind}/>
            )}
            <TextField id={"information-request-template-clone-name-input"}
                       label={"Name"}
                       value={displayName}
                       onChange={value =>
                       {
                           setDisplayName(value);
                           if (!keyEdited) setTemplateKey(keyFromLabel(value));
                       }}/>
            <TextField id={"information-request-template-clone-key-input"}
                       label={"Key"}
                       value={templateKey}
                       hint={`The copy is saved as ${template.namespace}:${templateKey || "key"} and starts as a new draft.`}
                       onChange={value =>
                       {
                           setKeyEdited(true);
                           setTemplateKey(value);
                       }}/>
        </EditorDialog>
    );
};

export default TemplateCloneDialog;
