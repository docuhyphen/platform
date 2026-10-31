import ReviewDialogFeedback from "../review-dialog-feedback/ReviewDialogFeedback.tsx";
import {ReviewMessage} from "../useReviewFeedback.ts";
import {useState} from "react";
import InformationRequestLoadError from "../../shared/load-error/InformationRequestLoadError.tsx";
import {
    AssignInformationRequestReviewerRequest,
    InformationRequestPartyDto,
    InformationRequestReviewStageStandingDto,
    InformationRequestShareRoleKey,
} from "../../../models/models.tsx";
import ChoiceSelect from "../../shared/choice-select/ChoiceSelect.tsx";
import DateTimeField from "../../shared/date-time-field/DateTimeField.tsx";
import {epochMillisFromLocalInput} from "../../shared/dateTimeInput.ts";
import EditorDialog from "../../shared/editor-dialog/EditorDialog.tsx";

interface Props
{
    stages: InformationRequestReviewStageStandingDto[];
    parties: InformationRequestPartyDto[];
    busy: boolean;
    message?: ReviewMessage | null;
    partiesError?: string | null;
    partiesLoading?: boolean;
    onRetryParties?: () => void;
    onConfirm: (request: AssignInformationRequestReviewerRequest) => void;
    onDismiss: () => void;
}

const AssignReviewerDialog = ({
                                  stages,
                                  parties,
                                  busy,
                                  message,
                                  partiesError,
                                  partiesLoading,
                                  onRetryParties,
                                  onConfirm,
                                  onDismiss
                              }: Props) =>
{
    const [stageKey, setStageKey] = useState(stages.length === 1 ? stages[0].stageKey : "");
    const [partyId, setPartyId] = useState("");
    const [due, setDue] = useState("");
    const reviewers = parties.filter(party => party.active && party.roleKey === InformationRequestShareRoleKey.REVIEWER);
    const dueAt = epochMillisFromLocalInput(due);
    const passed = dueAt !== undefined && dueAt <= Date.now();

    return (
        <EditorDialog id={"information-request-assign-reviewer"}
                      title={"Assign a reviewer"}
                      confirmLabel={"Assign"}
                      busy={busy}
                      confirmDisabled={!stageKey || !partyId || passed || partiesLoading || !!partiesError}
                      onConfirm={() => onConfirm({
                          stageKey,
                          reviewerPartyId: partyId, ...(dueAt !== undefined ? {dueAt} : {})
                      })}
                      onDismiss={onDismiss}>
            {partiesError && <InformationRequestLoadError id={"information-request-assign-reviewer-parties-error"}
                                                          message={partiesError}
                                                          onRetry={() => onRetryParties?.()}/>}
            <ChoiceSelect id={"information-request-assign-reviewer-stage"}
                          label={"Stage"}
                          value={stageKey}
                          placeholder={"Choose a stage"}
                          options={stages.map(stage => ({value: stage.stageKey, label: stage.title}))}
                          onChange={setStageKey}/>
            <ChoiceSelect id={"information-request-assign-reviewer-party"}
                          label={"Reviewer"}
                          value={partyId}
                          placeholder={"Choose a reviewer"}
                          hint={reviewers.length === 0 && !partiesError && !partiesLoading ? "Add a Reviewer party to this request first." : undefined}
                          options={reviewers.map(party => ({value: party.id, label: party.label ?? "Reviewer"}))}
                          onChange={setPartyId}/>
            <DateTimeField id={"information-request-assign-reviewer-due"}
                           label={"Due"}
                           hint={"Optional. When this reviewer should decide by."}
                           value={due}
                           validationMessage={passed ? "Choose a time that has not passed yet." : undefined}
                           onChange={setDue}/>
            <ReviewDialogFeedback id={"information-request-assign-reviewer-dialog-feedback"}
                                  message={message}/>
        </EditorDialog>
    );
};

export default AssignReviewerDialog;
