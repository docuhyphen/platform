import {useState} from "react";
import {Text} from "@fluentui/react-components";
import {InformationRequestItemCorrectionRequest, InformationRequestSubmissionItemDto} from "../../../models/models.tsx";
import {reviewValueText} from "../../review/reviewLabels.ts";
import EditorDialog from "../../shared/editor-dialog/EditorDialog.tsx";
import {isLetterKey, letterKeyProblem} from "../../shared/machineKey.ts";
import TextField from "../../shared/text-field/TextField.tsx";
import {CorrectionBasis} from "../request-outcomes-panel/useRequestOutcomes.ts";
import {correctedValueOf} from "./correctionValue.ts";

interface Props
{
    item: InformationRequestSubmissionItemDto;
    label: string;
    busy: boolean;
    onConfirm: (correction: InformationRequestItemCorrectionRequest, basis: CorrectionBasis) => void;
    onDismiss: () => void;
}

const CorrectItemDialog = ({item, label, busy, onConfirm, onDismiss}: Props) =>
{
    const holdsValue = item.fieldValue !== undefined && item.fieldValue !== null;
    const [value, setValue] = useState("");
    const [note, setNote] = useState("");
    const [reason, setReason] = useState("");
    const [purpose, setPurpose] = useState("");
    const [basis, setBasis] = useState("");
    const corrected = correctedValueOf(item.fieldValue, value);
    const changed = corrected.value !== undefined || note.trim().length > 0;
    const ready = changed && !corrected.problem && reason.trim().length > 0 && isLetterKey(purpose) && isLetterKey(basis);

    const confirm = () => onConfirm(
        {
            submissionItemId: item.id,
            ...(corrected.value !== undefined ? {value: corrected.value} : {}),
            ...(note.trim() ? {narrative: note.trim()} : {}),
            reasonCode: reason.trim(),
        },
        {purposeKey: purpose.trim(), policyBasisKey: basis.trim()},
    );

    return (
        <EditorDialog id={"information-request-correct-item-dialog"}
                      title={`Correct ${label}`}
                      confirmLabel={"Record correction"}
                      busy={busy}
                      confirmDisabled={!ready}
                      onConfirm={confirm}
                      onDismiss={onDismiss}>
            <Text id={"information-request-correct-item-explanation"}>
                A correction is recorded beside the submitted answer as a privacy request of its subject. The
                submitted answer itself is kept unchanged in the request history.
            </Text>
            {holdsValue && (
                <TextField id={"information-request-correct-item-value"}
                           label={"Corrected value"}
                           hint={`Submitted: ${reviewValueText(item.fieldValue)}`}
                           value={value}
                           validationMessage={corrected.problem}
                           onChange={setValue}/>
            )}
            <TextField id={"information-request-correct-item-note"}
                       label={"Corrected note"}
                       hint={item.narrative ? `Submitted: ${item.narrative}` : undefined}
                       multiline={true}
                       value={note}
                       maxLength={2000}
                       onChange={setNote}/>
            <TextField id={"information-request-correct-item-reason"}
                       label={"Reason"}
                       required={true}
                       value={reason}
                       maxLength={120}
                       onChange={setReason}/>
            <TextField id={"information-request-correct-item-purpose"}
                       label={"Purpose"}
                       hint={"A short lowercase key naming why the correction is made, such as subject-request."}
                       required={true}
                       value={purpose}
                       maxLength={128}
                       validationMessage={letterKeyProblem(purpose)}
                       onChange={setPurpose}/>
            <TextField id={"information-request-correct-item-basis"}
                       label={"Policy basis"}
                       hint={"A short lowercase key naming the policy that allows it, such as rectification."}
                       required={true}
                       value={basis}
                       maxLength={128}
                       validationMessage={letterKeyProblem(basis)}
                       onChange={setBasis}/>
        </EditorDialog>
    );
};

export default CorrectItemDialog;
