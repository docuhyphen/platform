import {useEffect, useState} from "react";
import {Text} from "@fluentui/react-components";
import {
    AssignInformationRequestPartyRequest,
    AssignInformationRequestSubjectRequest,
    InformationRequestShareRoleKey,
} from "../../../models/models.tsx";
import {InformationRequestOwnerFields} from "../../shared/ownerWorkspace.ts";
import ChoiceSelect from "../../shared/choice-select/ChoiceSelect.tsx";
import {optionsFrom} from "../../shared/choice-select/choiceOptions.ts";
import EditorDialog from "../../shared/editor-dialog/EditorDialog.tsx";
import {shareRoleLabels} from "../../shared/informationRequestLabels.ts";
import InformationRequestLoadError from "../../shared/load-error/InformationRequestLoadError.tsx";
import PartyHolderFields from "../party-holder-fields/PartyHolderFields.tsx";
import {
    offeredHolders,
    OTHER_WORKSPACE_NOTICE,
    PartyHolder,
    trustedHolderAvailable,
} from "../party-holder-fields/partyHolder.ts";
import SubjectPartyFields from "../subject-party-fields/SubjectPartyFields.tsx";
import {
    AddPartyForm,
    addPartyProblem,
    emptyAddPartyForm,
    holdersForRole,
    isSubjectRole,
    partyRequestFrom,
    subjectRequestFrom,
    withRole,
} from "./addPartyForm.ts";
import {usePartyCandidates} from "./usePartyCandidates.ts";
import {useTrustedRecipientCandidates} from "./useTrustedRecipientCandidates.ts";

interface Props
{
    requestId: string;
    owner: InformationRequestOwnerFields | null;
    busy: boolean;
    commandError?: string | null;
    onAssign: (request: AssignInformationRequestPartyRequest) => void;
    onAssignSubject: (request: AssignInformationRequestSubjectRequest) => void;
    onDismiss: () => void;
}

const ADD_HOLDERS: PartyHolder[] = ["email", "user", "group"];

const AddPartyDialog = ({requestId, owner, busy, commandError, onAssign, onAssignSubject, onDismiss}: Props) =>
{
    const {
        groups,
        people,
        subjects,
        subjectRegistryAvailable,
        otherWorkspace,
        error,
        reload
    } = usePartyCandidates(owner);
    const trustedRead = useTrustedRecipientCandidates(requestId);
    const trusted = trustedRead.candidates;
    const available: PartyHolder[] = trustedHolderAvailable(trusted) ? [...ADD_HOLDERS, "trusted"] : ADD_HOLDERS;
    const [form, setForm] = useState<AddPartyForm>(emptyAddPartyForm);
    const holders = holdersForRole(form.roleKey, offeredHolders(available, otherWorkspace));
    const problem = addPartyProblem(form);
    const change = (update: Partial<AddPartyForm>) => setForm(previous => ({...previous, ...update}));

    useEffect(() => {
        if (holders.length > 0 && !holders.includes(form.holder)) setForm(previous => ({
            ...previous,
            holder: holders[0]
        }));
    }, [holders, form.holder]);

    const confirm = () =>
    {
        if (problem) return;
        if (isSubjectRole(form) && !form.subjectId) onAssignSubject(subjectRequestFrom(form));
        else onAssign(partyRequestFrom(form, trusted));
    };

    return (
        <EditorDialog id={"information-request-add-party-dialog"}
                      title={"Add party"}
                      confirmLabel={"Add"}
                      busy={busy}
                      error={commandError}
                      confirmDisabled={Boolean(problem)}
                      onConfirm={confirm}
                      onDismiss={onDismiss}>
            {error && (
                <InformationRequestLoadError id={"information-request-add-party-candidates-load-error"}
                                             message={error}
                                             onRetry={() => void reload()}/>
            )}
            {trustedRead.error && (
                <InformationRequestLoadError id={"information-request-add-party-trusted-load-error"}
                                             message={trustedRead.error}
                                             onRetry={() => void trustedRead.reload()}/>
            )}
            {otherWorkspace && (
                <Text id={"information-request-add-party-other-workspace"}>{OTHER_WORKSPACE_NOTICE}</Text>
            )}
            <ChoiceSelect id={"information-request-add-party-role"}
                          label={"Role"}
                          value={form.roleKey}
                          disabled={busy}
                          options={optionsFrom<InformationRequestShareRoleKey>(shareRoleLabels)}
                          onChange={roleKey => change(withRole(form, roleKey))}/>
            {isSubjectRole(form)
                ? (
                    <SubjectPartyFields form={form}
                                        subjects={subjects}
                                        referencesAllowed={subjectRegistryAvailable || otherWorkspace}
                                        disabled={busy}
                                        onChange={change}/>
                )
                : (
                    <PartyHolderFields idPrefix={"information-request-add-party"}
                                       holders={holders}
                                       selection={form}
                                       groups={groups}
                                       people={people}
                                       trusted={trusted}
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
