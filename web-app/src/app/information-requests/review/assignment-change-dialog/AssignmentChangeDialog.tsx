import ReviewDialogFeedback from "../review-dialog-feedback/ReviewDialogFeedback.tsx";
import {ReviewMessage} from "../useReviewFeedback.ts";
import {useState} from "react";
import {InformationRequestReviewAssignmentChange} from "../../../../services/informationRequestReviewService.ts";
import {
    ChangeInformationRequestReviewAssignmentRequest,
    InformationRequestReviewDelegationCandidateDto,
} from "../../../models/models.tsx";
import ChoiceSelect from "../../shared/choice-select/ChoiceSelect.tsx";
import EditorDialog from "../../shared/editor-dialog/EditorDialog.tsx";
import TextField from "../../shared/text-field/TextField.tsx";

interface Props
{
    title: string;
    change: InformationRequestReviewAssignmentChange;
    candidates: InformationRequestReviewDelegationCandidateDto[];
    busy: boolean;
    message?: ReviewMessage | null;
    onConfirm: (request: ChangeInformationRequestReviewAssignmentRequest) => void;
    onDismiss: () => void;
}

const CONFIRM_LABELS: Record<InformationRequestReviewAssignmentChange, string> = {
    recusal: "Recuse",
    delegation: "Delegate",
    revocation: "Revoke",
};

const AssignmentChangeDialog = ({title, change, candidates, busy, message, onConfirm, onDismiss}: Props) =>
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
                              options={candidates.map(candidate => ({
                                  value: candidate.partyId,
                                  label: candidate.label
                              }))}
                              onChange={setDelegateId}/>
            )}
            <TextField id={"information-request-assignment-change-reason"}
                       label={"Reason"}
                       hint={"Recorded in the review history."}
                       value={reason}
                       maxLength={120}
                       onChange={setReason}/>
            <ReviewDialogFeedback id={"information-request-assignment-change-dialog-feedback"}
                                  message={message}/>
        </EditorDialog>
    );
};

export default AssignmentChangeDialog;
