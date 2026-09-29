import {useState} from "react";
import {InformationRequestReviewAssignmentChange} from "../../../../services/informationRequestReviewService.ts";
import {ChangeInformationRequestReviewAssignmentRequest, InformationRequestPartyDto} from "../../../models/models.tsx";
import ChoiceSelect from "../../shared/choice-select/ChoiceSelect.tsx";
import EditorDialog from "../../shared/editor-dialog/EditorDialog.tsx";
import TextField from "../../shared/text-field/TextField.tsx";

interface Props
{
    title: string;
    change: InformationRequestReviewAssignmentChange;
    delegates: InformationRequestPartyDto[];
    busy: boolean;
    onConfirm: (request: ChangeInformationRequestReviewAssignmentRequest) => void;
    onDismiss: () => void;
}

const CONFIRM_LABELS: Record<InformationRequestReviewAssignmentChange, string> = {
    recusal: "Recuse",
    delegation: "Delegate",
    revocation: "Revoke",
};

const AssignmentChangeDialog = ({title, change, delegates, busy, onConfirm, onDismiss}: Props) =>
{
    const [reason, setReason] = useState("");
    const [delegateId, setDelegateId] = useState("");
    const delegating = change === "delegation";
    const ready = Boolean(reason.trim()) && (!delegating || Boolean(delegateId));

    return (
        <EditorDialog id={"information-request-assignment-change"}
                      title={title}
                      confirmLabel={CONFIRM_LABELS[change]}
                      busy={busy}
                      confirmDisabled={!ready}
                      onConfirm={() => onConfirm(delegating
                          ? {reasonCode: reason.trim(), delegatePartyId: delegateId}
                          : {reasonCode: reason.trim()})}
                      onDismiss={onDismiss}>
            {delegating && (
                <ChoiceSelect id={"information-request-assignment-change-delegate"}
                              label={"Delegate to"}
                              value={delegateId}
                              placeholder={"Choose a reviewer"}
                              options={delegates.map(party => ({value: party.id, label: party.label ?? "Reviewer"}))}
                              onChange={setDelegateId}/>
            )}
            <TextField id={"information-request-assignment-change-reason"}
                       label={"Reason"}
                       hint={"Recorded in the review history."}
                       value={reason}
                       maxLength={120}
                       onChange={setReason}/>
        </EditorDialog>
    );
};

export default AssignmentChangeDialog;
