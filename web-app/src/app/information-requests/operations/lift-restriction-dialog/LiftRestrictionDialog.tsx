import {useState} from "react";
import {MessageBar, MessageBarBody, Text} from "@fluentui/react-components";
import EditorDialog from "../../shared/editor-dialog/EditorDialog.tsx";
import TextField from "../../shared/text-field/TextField.tsx";

interface LiftRestrictionDialogProps
{
    busy: boolean;
    onConfirm: (reasonCode: string) => Promise<string | null>;
    onDone: () => void;
}

const LiftRestrictionDialog = ({busy, onConfirm, onDone}: LiftRestrictionDialogProps) =>
{
    const [reason, setReason] = useState("");
    const [refusal, setRefusal] = useState<string | null>(null);

    const confirm = async () =>
    {
        setRefusal(null);
        const refused = await onConfirm(reason.trim());
        if (refused) setRefusal(refused);
        else onDone();
    };

    return (
        <EditorDialog id={"information-request-lift-restriction-dialog"}
                      title={"Lift this restriction"}
                      confirmLabel={"Lift restriction"}
                      dismissLabel={"Keep restriction"}
                      busy={busy}
                      confirmDisabled={!reason.trim()}
                      onConfirm={() => void confirm()}
                      onDismiss={onDone}>
            {refusal && (
                <MessageBar id={"information-request-lift-restriction-refusal"}
                            intent={"error"}
                            role={"alert"}>
                    <MessageBarBody>{refusal}</MessageBarBody>
                </MessageBar>
            )}
            <Text id={"information-request-lift-restriction-explanation"}>
                Answers about this subject can be promoted and reused again once the restriction is lifted. The
                restriction and its lifting stay on record.
            </Text>
            <TextField id={"information-request-lift-restriction-reason"}
                       label={"Reason"}
                       hint={"Recorded with the restriction."}
                       required={true}
                       value={reason}
                       maxLength={120}
                       onChange={setReason}/>
        </EditorDialog>
    );
};

export default LiftRestrictionDialog;
