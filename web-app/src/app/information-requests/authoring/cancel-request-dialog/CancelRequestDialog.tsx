import {useState} from "react";
import {Text} from "@fluentui/react-components";
import EditorDialog from "../../shared/editor-dialog/EditorDialog.tsx";
import TextField from "../../shared/text-field/TextField.tsx";

interface Props
{
    busy: boolean;
    onConfirm: (reasonCode: string) => void;
    onDismiss: () => void;
}

const CancelRequestDialog = ({busy, onConfirm, onDismiss}: Props) =>
{
    const [reason, setReason] = useState("");

    return (
        <EditorDialog id={"information-request-cancel-dialog"}
                      title={"Cancel this request"}
                      confirmLabel={"Cancel request"}
                      dismissLabel={"Keep request"}
                      busy={busy}
                      confirmDisabled={!reason.trim()}
                      onConfirm={() => onConfirm(reason.trim())}
                      onDismiss={onDismiss}>
            <Text id={"information-request-cancel-explanation"}>
                Respondents can no longer answer a cancelled request. Its history is kept.
            </Text>
            <TextField id={"information-request-cancel-reason"}
                       label={"Reason"}
                       hint={"Recorded in the request history."}
                       value={reason}
                       maxLength={120}
                       onChange={setReason}/>
        </EditorDialog>
    );
};

export default CancelRequestDialog;
