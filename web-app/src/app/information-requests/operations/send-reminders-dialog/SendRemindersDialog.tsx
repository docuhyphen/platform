import {Text} from "@fluentui/react-components";
import EditorDialog from "../../shared/editor-dialog/EditorDialog.tsx";
import {formatInformationRequestCount} from "../../shared/informationRequestFormatting.ts";

interface SendRemindersDialogProps
{
    requestCount: number;
    busy: boolean;
    onConfirm: () => void;
    onDismiss: () => void;
}

const selectedRequests = (requestCount: number): string =>
    requestCount === 1 ? "selected request" : `${formatInformationRequestCount(requestCount)} selected requests`;

const SendRemindersDialog = ({requestCount, busy, onConfirm, onDismiss}: SendRemindersDialogProps) => (
    <EditorDialog id={"information-request-send-reminders-dialog"}
                  title={"Send reminders"}
                  confirmLabel={"Send reminders"}
                  busy={busy}
                  onConfirm={onConfirm}
                  onDismiss={onDismiss}>
        <Text id={"information-request-send-reminders-explanation"}>
            {`Each responding party of the ${selectedRequests(requestCount)} is owed a reminder notice. `}
            If any selected request is no longer issued or in progress, nothing is sent.
        </Text>
    </EditorDialog>
);

export default SendRemindersDialog;
