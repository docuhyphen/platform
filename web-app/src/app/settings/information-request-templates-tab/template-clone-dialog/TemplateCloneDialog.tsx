import {useState} from "react";
import {MessageBar, MessageBarBody, Text} from "@fluentui/react-components";
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
    refusal: string | null;
    onConfirm: (target: CreateInformationRequestTemplateRequest) => void;
    onDismiss: () => void;
}

const targetLabels: Record<InformationRequestTemplateScopeKind, string> = {
    [InformationRequestTemplateScopeKind.PERSONAL]: "My Templates",
    [InformationRequestTemplateScopeKind.ORGANIZATION]: "Organization",
    [InformationRequestTemplateScopeKind.PLATFORM]: "Platform",
};

const SCHEMA_RULE = "Typed answers keep the Schema this version names. A copy for another owner can keep it only " +
    "when it is a Platform Schema; otherwise the copy is refused.";

const TemplateCloneDialog = ({template, targets, busy, refusal, onConfirm, onDismiss}: TemplateCloneDialogProps) =>
{
    const [displayName, setDisplayName] = useState("");
    const [templateKey, setTemplateKey] = useState("");
    const [keyEdited, setKeyEdited] = useState(false);
    const [description, setDescription] = useState(template.description ?? "");
    const [scopeKind, setScopeKind] = useState(targets.includes(template.scopeKind) ? template.scopeKind : targets[0]);
    const ownedElsewhere = scopeKind !== template.scopeKind && template.scopeKind !== InformationRequestTemplateScopeKind.PLATFORM;

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
                          ...(description.trim() ? {description: description.trim()} : {}),
                          scopeKind,
                      })}
                      onDismiss={onDismiss}>
            {refusal && (
                <MessageBar id={"information-request-template-clone-refusal"}
                            intent={"error"}
                            role={"alert"}>
                    <MessageBarBody id={"information-request-template-clone-refusal-body"}>{refusal}</MessageBarBody>
                </MessageBar>
            )}
            {targets.length > 1 && (
                <ChoiceSelect<InformationRequestTemplateScopeKind> id={"information-request-template-clone-target"}
                                                                   label={"Copy into"}
                                                                   value={scopeKind}
                                                                   options={targets.map(target => ({value: target, label: targetLabels[target]}))}
                                                                   hint={ownedElsewhere ? SCHEMA_RULE : undefined}
                                                                   onChange={setScopeKind}/>
            )}
            {targets.length === 1 && ownedElsewhere && (
                <Text id={"information-request-template-clone-schema-rule"}
                      size={200}>
                    {SCHEMA_RULE}
                </Text>
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
            <TextField id={"information-request-template-clone-description-input"}
                       label={"Description"}
                       value={description}
                       multiline={true}
                       onChange={setDescription}/>
        </EditorDialog>
    );
};

export default TemplateCloneDialog;
