import {useState} from "react";
import {Text} from "@fluentui/react-components";
import ChoiceSelect from "../../shared/choice-select/ChoiceSelect.tsx";
import EditorDialog from "../../shared/editor-dialog/EditorDialog.tsx";
import {CANCEL_REASON_OPTIONS} from "../requestReasonCodes.ts";

interface Props
{
    busy: boolean;
    error?: string | null;
    onConfirm: (reasonCode: string) => void;
    onDismiss: () => void;
}

const CancelRequestDialog = ({busy, error, onConfirm, onDismiss}: Props) =>
{
    const [reason, setReason] = useState("");

    return (
        <EditorDialog id={"information-request-cancel-dialog"}
                      title={"Cancel this request"}
                      confirmLabel={"Cancel request"}
                      dismissLabel={"Keep request"}
                      busy={busy}
                      error={error}
                      confirmDisabled={!reason}
                      onConfirm={() => onConfirm(reason)}
                      onDismiss={onDismiss}>
            <Text id={"information-request-cancel-explanation"}>
                Respondents can no longer answer a cancelled request. Its history is kept.
            </Text>
            <ChoiceSelect id={"information-request-cancel-reason"}
                          label={"Reason"}
                          value={reason}
                          placeholder={"Choose a reason"}
                          hint={"Recorded in the request history."}
                          options={CANCEL_REASON_OPTIONS}
                          onChange={setReason}/>
        </EditorDialog>
    );
};

export default CancelRequestDialog;
