import {useState} from "react";
import {
    AssignInformationRequestReviewerRequest,
    InformationRequestPartyDto,
    InformationRequestReviewStageStandingDto,
    InformationRequestShareRoleKey,
} from "../../../models/models.tsx";
import ChoiceSelect from "../../shared/choice-select/ChoiceSelect.tsx";
import EditorDialog from "../../shared/editor-dialog/EditorDialog.tsx";

interface Props
{
    stages: InformationRequestReviewStageStandingDto[];
    parties: InformationRequestPartyDto[];
    busy: boolean;
    onConfirm: (request: AssignInformationRequestReviewerRequest) => void;
    onDismiss: () => void;
}

const AssignReviewerDialog = ({stages, parties, busy, onConfirm, onDismiss}: Props) =>
{
    const [stageKey, setStageKey] = useState(stages.length === 1 ? stages[0].stageKey : "");
    const [partyId, setPartyId] = useState("");
    const reviewers = parties.filter(party => party.active && party.roleKey === InformationRequestShareRoleKey.REVIEWER);

    return (
        <EditorDialog id={"information-request-assign-reviewer"}
                      title={"Assign a reviewer"}
                      confirmLabel={"Assign"}
                      busy={busy}
                      confirmDisabled={!stageKey || !partyId}
                      onConfirm={() => onConfirm({stageKey, reviewerPartyId: partyId})}
                      onDismiss={onDismiss}>
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
                          hint={reviewers.length === 0 ? "Add a Reviewer party to this request first." : undefined}
                          options={reviewers.map(party => ({value: party.id, label: party.label ?? "Reviewer"}))}
                          onChange={setPartyId}/>
        </EditorDialog>
    );
};

export default AssignReviewerDialog;
