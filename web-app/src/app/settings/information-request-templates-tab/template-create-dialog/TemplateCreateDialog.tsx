import {useState} from "react";
import {Text} from "@fluentui/react-components";
import {CreateInformationRequestTemplateRequest, InformationRequestTemplateScopeKind} from "../../../models/models.tsx";
import EditorDialog from "../../../information-requests/shared/editor-dialog/EditorDialog.tsx";
import TextField from "../../../information-requests/shared/text-field/TextField.tsx";
import {keyFromLabel} from "../../../information-requests/template-document/templateDraftDocument.ts";
import {useTemplateCreateDialogStyles} from "./TemplateCreateDialogStyles.tsx";

interface TemplateCreateDialogProps
{
    scopeKind: InformationRequestTemplateScopeKind;
    onCreate: (request: CreateInformationRequestTemplateRequest) => Promise<string | null>;
    onDismiss: () => void;
}

const NAMESPACE = "process";

const TemplateCreateDialog = ({scopeKind, onCreate, onDismiss}: TemplateCreateDialogProps) =>
{
    const styles = useTemplateCreateDialogStyles();
    const [displayName, setDisplayName] = useState("");
    const [templateKey, setTemplateKey] = useState("");
    const [keyEdited, setKeyEdited] = useState(false);
    const [description, setDescription] = useState("");
    const [busy, setBusy] = useState(false);
    const [refusal, setRefusal] = useState<string | null>(null);

    const create = async () =>
    {
        setBusy(true);
        setRefusal(null);
        const refused = await onCreate({
            namespace: NAMESPACE,
            templateKey: templateKey.trim(),
            displayName: displayName.trim(),
            ...(description.trim() ? {description: description.trim()} : {}),
            scopeKind,
        });
        setBusy(false);
        if (refused) setRefusal(refused);
        else onDismiss();
    };

    return (
        <EditorDialog id={"information-request-template-create-dialog"}
                      title={"New Information Request Template"}
                      busy={busy}
                      confirmLabel={"Create"}
                      confirmDisabled={!displayName.trim() || !templateKey.trim()}
                      onConfirm={() => void create()}
                      onDismiss={onDismiss}>
            {refusal && (
                <Text id={"information-request-template-create-refusal"}
                      role={"alert"}
                      className={styles.refusal}>
                    {refusal}
                </Text>
            )}
            <TextField id={"information-request-template-create-name-input"}
                       label={"Name"}
                       value={displayName}
                       onChange={value =>
                       {
                           setDisplayName(value);
                           if (!keyEdited) setTemplateKey(keyFromLabel(value));
                       }}/>
            <TextField id={"information-request-template-create-key-input"}
                       label={"Key"}
                       value={templateKey}
                       hint={"Lowercase letters, digits, and inner hyphens. The key cannot change later."}
                       onChange={value =>
                       {
                           setKeyEdited(true);
                           setTemplateKey(value);
                       }}/>
            <TextField id={"information-request-template-create-description-input"}
                       label={"Description"}
                       value={description}
                       multiline={true}
                       onChange={setDescription}/>
        </EditorDialog>
    );
};

export default TemplateCreateDialog;
