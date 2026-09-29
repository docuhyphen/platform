import {useState} from "react";
import {Text} from "@fluentui/react-components";
import {RevokeInformationRequestAcceptedFactRequest} from "../../../models/models.tsx";
import EditorDialog from "../../shared/editor-dialog/EditorDialog.tsx";
import TextField from "../../shared/text-field/TextField.tsx";

interface Props
{
    busy: boolean;
    onConfirm: (request: RevokeInformationRequestAcceptedFactRequest) => void;
    onDismiss: () => void;
}

const RevokeFactDialog = ({busy, onConfirm, onDismiss}: Props) =>
{
    const [reason, setReason] = useState("");
    const [note, setNote] = useState("");

    return (
        <EditorDialog id={"information-request-revoke-fact-dialog"}
                      title={"Revoke this accepted fact"}
                      confirmLabel={"Revoke"}
                      dismissLabel={"Keep fact"}
                      busy={busy}
                      confirmDisabled={!reason.trim()}
                      onConfirm={() => onConfirm({reasonCode: reason.trim(), ...(note.trim() ? {narrative: note.trim()} : {})})}
                      onDismiss={onDismiss}>
            <Text id={"information-request-revoke-fact-explanation"}>
                A revoked fact is no longer offered to later requests. It stays in this request&apos;s history.
            </Text>
            <TextField id={"information-request-revoke-fact-reason"}
                       label={"Reason"}
                       hint={"Recorded in the request history."}
                       required={true}
                       value={reason}
                       maxLength={120}
                       onChange={setReason}/>
            <TextField id={"information-request-revoke-fact-note"}
                       label={"Note"}
                       multiline={true}
                       value={note}
                       maxLength={2000}
                       onChange={setNote}/>
        </EditorDialog>
    );
};

export default RevokeFactDialog;
