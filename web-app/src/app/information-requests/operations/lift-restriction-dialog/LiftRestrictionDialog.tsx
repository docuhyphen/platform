import {useState} from "react";
import {MessageBar, MessageBarBody, Text} from "@fluentui/react-components";
import EditorDialog from "../../shared/editor-dialog/EditorDialog.tsx";
import {MACHINE_KEY_MESSAGE} from "../../shared/machineKey.ts";
import TextField from "../../shared/text-field/TextField.tsx";

interface LiftRestrictionDialogProps
{
    busy: boolean;
    onConfirm: (reasonCode: string) => Promise<string | null>;
    onDone: () => void;
}

const REASON_CODE = /^[a-z0-9][a-z0-9._-]{0,127}$/;

const LiftRestrictionDialog = ({busy, onConfirm, onDone}: LiftRestrictionDialogProps) =>
{
    const [reason, setReason] = useState("");
    const [refusal, setRefusal] = useState<string | null>(null);
    const reasonCode = reason.trim().toLowerCase();
    const reasonValid = REASON_CODE.test(reasonCode);

    const confirm = async () =>
    {
        setRefusal(null);
        const refused = await onConfirm(reasonCode);
        if (refused) setRefusal(refused);
        else onDone();
    };

    return (
        <EditorDialog id={"information-request-lift-restriction-dialog"}
                      title={"Lift this restriction"}
                      confirmLabel={"Lift restriction"}
                      dismissLabel={"Keep restriction"}
                      busy={busy}
                      confirmDisabled={!reasonValid}
                      onConfirm={() => void confirm()}
                      onDismiss={onDone}>
            {refusal && (
                <MessageBar id={"information-request-lift-restriction-refusal"}
                            intent={"error"}
                            role={"alert"}>
                    <MessageBarBody id={"information-request-lift-restriction-refusal-body"}>{refusal}</MessageBarBody>
                </MessageBar>
            )}
            <Text id={"information-request-lift-restriction-explanation"}>
                Answers about this subject can be promoted and reused again once the restriction is lifted. The
                restriction and its lifting stay on record.
            </Text>
            <TextField id={"information-request-lift-restriction-reason"}
                       label={"Reason code"}
                       hint={"A short code recorded with the restriction, for example resolved.by.subject."}
                       validationMessage={reason.trim() && !reasonValid ? MACHINE_KEY_MESSAGE : undefined}
                       required={true}
                       value={reason}
                       maxLength={128}
                       onChange={setReason}/>
        </EditorDialog>
    );
};

export default LiftRestrictionDialog;
