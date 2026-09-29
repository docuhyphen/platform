import {useState} from "react";
import {Text} from "@fluentui/react-components";
import {
    AssignInformationRequestPartyRequest,
    AssignInformationRequestSubjectRequest,
    InformationRequestShareRoleKey,
} from "../../../models/models.tsx";
import ChoiceSelect from "../../shared/choice-select/ChoiceSelect.tsx";
import {optionsFrom} from "../../shared/choice-select/choiceOptions.ts";
import EditorDialog from "../../shared/editor-dialog/EditorDialog.tsx";
import {shareRoleLabels} from "../../shared/informationRequestLabels.ts";
import PartyHolderFields from "../party-holder-fields/PartyHolderFields.tsx";
import SubjectPartyFields from "../subject-party-fields/SubjectPartyFields.tsx";
import {
    addPartyProblem,
    AddPartyForm,
    emptyAddPartyForm,
    isSubjectRole,
    partyRequestFrom,
    subjectRequestFrom,
} from "./addPartyForm.ts";
import {usePartyCandidates} from "./usePartyCandidates.ts";

interface Props
{
    busy: boolean;
    onAssign: (request: AssignInformationRequestPartyRequest) => void;
    onAssignSubject: (request: AssignInformationRequestSubjectRequest) => void;
    onDismiss: () => void;
}

const AddPartyDialog = ({busy, onAssign, onAssignSubject, onDismiss}: Props) =>
{
    const {groups, subjects} = usePartyCandidates();
    const [form, setForm] = useState<AddPartyForm>(emptyAddPartyForm);
    const problem = addPartyProblem(form);
    const change = (update: Partial<AddPartyForm>) => setForm(previous => ({...previous, ...update}));

    const confirm = () =>
    {
        if (problem) return;
        if (isSubjectRole(form) && !form.subjectId) onAssignSubject(subjectRequestFrom(form));
        else onAssign(partyRequestFrom(form));
    };

    return (
        <EditorDialog id={"information-request-add-party-dialog"}
                      title={"Add party"}
                      confirmLabel={"Add"}
                      busy={busy}
                      confirmDisabled={Boolean(problem)}
                      onConfirm={confirm}
                      onDismiss={onDismiss}>
            <ChoiceSelect id={"information-request-add-party-role"}
                          label={"Role"}
                          value={form.roleKey}
                          disabled={busy}
                          options={optionsFrom<InformationRequestShareRoleKey>(shareRoleLabels)}
                          onChange={roleKey => change({roleKey})}/>
            {isSubjectRole(form)
                ? (
                    <SubjectPartyFields form={form}
                                        subjects={subjects}
                                        disabled={busy}
                                        onChange={change}/>
                )
                : (
                    <PartyHolderFields form={form}
                                       groups={groups}
                                       disabled={busy}
                                       onChange={change}/>
                )}
            {problem && (
                <Text id={"information-request-add-party-problem"}
                      role={"status"}
                      aria-live={"polite"}>
                    {problem}
                </Text>
            )}
        </EditorDialog>
    );
};

export default AddPartyDialog;
