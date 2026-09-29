import {Text} from "@fluentui/react-components";
import EditorDialog from "../../shared/editor-dialog/EditorDialog.tsx";

interface SendRemindersDialogProps
{
    requestCount: number;
    busy: boolean;
    onConfirm: () => void;
    onDismiss: () => void;
}

const SendRemindersDialog = ({requestCount, busy, onConfirm, onDismiss}: SendRemindersDialogProps) => (
    <EditorDialog id={"information-request-send-reminders-dialog"}
                  title={"Send reminders"}
                  confirmLabel={"Send reminders"}
                  busy={busy}
                  onConfirm={onConfirm}
                  onDismiss={onDismiss}>
        <Text id={"information-request-send-reminders-explanation"}>
            {`Each responding party of the ${requestCount === 1 ? "selected request" : `${requestCount} selected requests`} is owed a reminder notice. `}
            If any selected request is no longer open, nothing is sent.
        </Text>
    </EditorDialog>
);

export default SendRemindersDialog;
