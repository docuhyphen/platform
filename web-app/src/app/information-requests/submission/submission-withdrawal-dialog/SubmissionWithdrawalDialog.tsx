import {useState} from "react";
import {Text} from "@fluentui/react-components";
import EditorDialog from "../../shared/editor-dialog/EditorDialog.tsx";
import TextField from "../../shared/text-field/TextField.tsx";
import ReviewDialogFeedback from "../../review/review-dialog-feedback/ReviewDialogFeedback.tsx";

interface Props {
    packageId: string;
    packageNumber: number;
    busy: boolean;
    error?: string | null;
    onWithdraw: (packageId: string, reasonCode?: string) => Promise<boolean> | void;
    onDismiss: () => void;
}

const SubmissionWithdrawalDialog = ({packageId, packageNumber, busy, error, onWithdraw, onDismiss}: Props) => {
    const [reason, setReason] = useState("");
    const normalized = reason.trim();
    const invalid = normalized !== "" && !/^[a-z0-9]+([._-][a-z0-9]+)*$/.test(normalized);
    const confirm = async () => {
        if (await onWithdraw(packageId, normalized || undefined) === true) onDismiss();
    };
    return (
        <EditorDialog id={"information-request-withdrawal-dialog"}
                      title={`Withdraw submission ${packageNumber}`}
                      busy={busy}
                      confirmLabel={"Withdraw submission"}
                      confirmDisabled={invalid}
                      onConfirm={() => void confirm()}
                      onDismiss={onDismiss}>
            <Text id={"information-request-withdrawal-explanation"}>
                Withdrawal reopens this submitted part for changes. Its recorded submission remains in the history.
            </Text>
            <TextField id={"information-request-withdrawal-reason"}
                       label={"Reason code"}
                       value={reason}
                       maxLength={128}
                       disabled={busy}
                       hint={"Optional lowercase code, using letters, numbers, dots, hyphens or underscores."}
                       validationMessage={invalid ? "Enter a lowercase reason code with separators between words." : undefined}
                       onChange={setReason}/>
            <ReviewDialogFeedback id={"information-request-withdrawal-error"}
                                  message={error ? {intent: "error", text: error} : null}/>
        </EditorDialog>
    );
};

export default SubmissionWithdrawalDialog;