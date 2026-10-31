import {useEffect, useState} from "react";
import {Text} from "@fluentui/react-components";
import {InformationRequestPartyDto, ReassignInformationRequestPartyRequest} from "../../../models/models.tsx";
import {InformationRequestOwnerFields} from "../../shared/ownerWorkspace.ts";
import EditorDialog from "../../shared/editor-dialog/EditorDialog.tsx";
import {shareRoleLabels} from "../../shared/informationRequestLabels.ts";
import InformationRequestLoadError from "../../shared/load-error/InformationRequestLoadError.tsx";
import {usePartyCandidates} from "../add-party-dialog/usePartyCandidates.ts";
import {useTrustedRecipientCandidates} from "../add-party-dialog/useTrustedRecipientCandidates.ts";
import {partyLabel} from "../author-workspace/useAuthorWorkspace.ts";
import PartyHolderFields from "../party-holder-fields/PartyHolderFields.tsx";
import {
    offeredHolders,
    OTHER_WORKSPACE_NOTICE,
    PartyHolder,
    partyHolderProblem,
    PartyHolderSelection,
    trustedHolderAvailable,
} from "../party-holder-fields/partyHolder.ts";
import {emptyReassignPartyForm, REASSIGN_HOLDERS, reassignRequestFrom} from "./reassignPartyForm.ts";
import {useReassignPartyDialogStyles} from "./ReassignPartyDialogStyles.tsx";

interface Props {
    requestId: string;
    owner: InformationRequestOwnerFields | null;
    party: InformationRequestPartyDto;
    busy: boolean;
    commandError?: string | null;
    onReassign: (request: ReassignInformationRequestPartyRequest) => void;
    onDismiss: () => void;
}

const ReassignPartyDialog = ({requestId, owner, party, busy, commandError, onReassign, onDismiss}: Props) => {
    const styles = useReassignPartyDialogStyles();
    const {groups, people, otherWorkspace, error, reload} = usePartyCandidates(owner);
    const trustedRead = useTrustedRecipientCandidates(requestId);
    const trusted = trustedRead.candidates;
    const available: PartyHolder[] = trustedHolderAvailable(trusted) ? [...REASSIGN_HOLDERS, "trusted"] : REASSIGN_HOLDERS;
    const holders = offeredHolders(available, otherWorkspace);
    const [selection, setSelection] = useState<PartyHolderSelection>(emptyReassignPartyForm);

    useEffect(() => {
        if (holders.length > 0 && !holders.includes(selection.holder)) setSelection(previous => ({
            ...previous,
            holder: holders[0]
        }));
    }, [holders, selection.holder]);
    const problem = partyHolderProblem(selection);
    const label = partyLabel(party);

    return (
        <EditorDialog id={"information-request-reassign-party-dialog"}
                      title={"Reassign party"}
                      confirmLabel={"Reassign"}
                      busy={busy}
                      error={commandError}
                      confirmDisabled={Boolean(problem)}
                      onConfirm={() => {
                          if (!problem) onReassign(reassignRequestFrom(selection, trusted));
                      }}
                      onDismiss={onDismiss}>
            {error && (
                <InformationRequestLoadError id={"information-request-reassign-party-candidates-load-error"}
                                             message={error}
                                             onRetry={() => void reload()}/>
            )}
            {trustedRead.error && (
                <InformationRequestLoadError id={"information-request-reassign-party-trusted-load-error"}
                                             message={trustedRead.error}
                                             onRetry={() => void trustedRead.reload()}/>
            )}
            {otherWorkspace && (
                <Text id={"information-request-reassign-party-other-workspace"}>{OTHER_WORKSPACE_NOTICE}</Text>
            )}
            <Text id={"information-request-reassign-party-intro"}
                  className={styles.intro}>
                {`Choose who takes over the ${shareRoleLabels[party.roleKey]} role from ${label}. ${label} loses access to this `
                    + "request, including any access links and sessions; what was already provided stays in the request history."}
            </Text>
            <PartyHolderFields idPrefix={"information-request-reassign-party"}
                               holders={holders}
                               selection={selection}
                               groups={groups}
                               people={people}
                               trusted={trusted}
                               disabled={busy}
                               onChange={change => setSelection(previous => ({...previous, ...change}))}/>
            {problem && (
                <Text id={"information-request-reassign-party-problem"}
                      role={"status"}
                      aria-live={"polite"}
                      className={styles.problem}>
                    {problem}
                </Text>
            )}
        </EditorDialog>
    );
};

export default ReassignPartyDialog;
