import {useState} from "react";
import {Text} from "@fluentui/react-components";
import EditorDialog from "../../shared/editor-dialog/EditorDialog.tsx";
import TextField from "../../shared/text-field/TextField.tsx";

interface Props
{
    id: string;
    title: string;
    explanation: string;
    confirmLabel: string;
    busy: boolean;
    onConfirm: (reason: string) => void;
    onDismiss: () => void;
}

const ReviewReasonDialog = ({id, title, explanation, confirmLabel, busy, onConfirm, onDismiss}: Props) =>
{
    const [reason, setReason] = useState("");

    return (
        <EditorDialog id={id}
                      title={title}
                      confirmLabel={confirmLabel}
                      busy={busy}
                      confirmDisabled={!reason.trim()}
                      onConfirm={() => onConfirm(reason.trim())}
                      onDismiss={onDismiss}>
            <Text id={`${id}-explanation`}>{explanation}</Text>
            <TextField id={`${id}-reason`}
                       label={"Reason"}
                       multiline={true}
                       maxLength={2000}
                       value={reason}
                       onChange={setReason}/>
        </EditorDialog>
    );
};

export default ReviewReasonDialog;
