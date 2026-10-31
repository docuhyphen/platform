import {useState} from "react";
import {Spinner, Text} from "@fluentui/react-components";
import {getExchangeInformationRequests} from "../../../../services/informationRequestAuthoringService.ts";
import {InformationRequestState, InformationRequestSummaryDto} from "../../../models/models.tsx";
import ChoiceSelect from "../../shared/choice-select/ChoiceSelect.tsx";
import EditorDialog from "../../shared/editor-dialog/EditorDialog.tsx";
import {informationRequestRefusalMessage} from "../../shared/informationRequestRefusal.ts";
import InformationRequestLoadError from "../../shared/load-error/InformationRequestLoadError.tsx";
import {useServerRead} from "../../shared/useServerRead.ts";
import {SUPERSEDE_REASON_OPTIONS} from "../requestReasonCodes.ts";

interface Props
{
    requestId: string;
    exchangeId: string;
    busy: boolean;
    error?: string | null;
    onConfirm: (replacementId: string, reasonCode: string) => void;
    onDismiss: () => void;
}

const REPLACEABLE_STATES = new Set([InformationRequestState.DRAFT, InformationRequestState.ISSUED, InformationRequestState.IN_PROGRESS]);

const LOAD_FAILURE = "The requests that could replace this one could not be loaded.";

const replacementsFor = (requestId: string, requests: InformationRequestSummaryDto[]): InformationRequestSummaryDto[] =>
    requests.filter(summary =>
        summary.id !== requestId && REPLACEABLE_STATES.has(summary.state) && summary.permissions.canManage);

const SupersedeRequestDialog = ({requestId, exchangeId, busy, error, onConfirm, onDismiss}: Props) =>
{
    const listing = useServerRead(
        `${exchangeId}|${requestId}`,
        () => getExchangeInformationRequests(exchangeId),
        refusal => informationRequestRefusalMessage(refusal, LOAD_FAILURE),
    );
    const candidates = listing.value ? replacementsFor(requestId, listing.value.requests) : null;
    const [replacementId, setReplacementId] = useState("");
    const [reason, setReason] = useState("");
    const chosen = candidates?.some(candidate => candidate.id === replacementId) ? replacementId : "";

    return (
        <EditorDialog id={"information-request-supersede-dialog"}
                      title={"Supersede this request"}
                      confirmLabel={"Supersede"}
                      busy={busy}
                      error={error}
                      confirmDisabled={!chosen || !reason}
                      onConfirm={() => onConfirm(chosen, reason)}
                      onDismiss={onDismiss}>
            <Text id={"information-request-supersede-explanation"}>
                The replacement request takes over. This request stops accepting answers and keeps its history.
            </Text>
            {listing.error && (
                <InformationRequestLoadError id={"information-request-supersede-replacement-load-error"}
                                             message={listing.error}
                                             onRetry={() => void listing.reload()}/>
            )}
            {!candidates && !listing.error && (
                <Spinner id={"information-request-supersede-replacement-loading"}
                         size={"tiny"}
                         label={"Loading the requests that could replace this one"}/>
            )}
            {candidates && (
                <ChoiceSelect id={"information-request-supersede-replacement"}
                              label={"Replacement request"}
                              value={chosen}
                              placeholder={"Choose the replacement"}
                              hint={candidates.length === 0
                                  ? "No open request you manage in this Exchange can replace it. Create the replacement request first."
                                  : "Only open requests you manage in this Exchange are listed."}
                              options={candidates.map(candidate => ({value: candidate.id, label: candidate.title}))}
                              onChange={setReplacementId}/>
            )}
            <ChoiceSelect id={"information-request-supersede-reason"}
                          label={"Reason"}
                          value={reason}
                          placeholder={"Choose a reason"}
                          hint={"Recorded in the request history."}
                          options={SUPERSEDE_REASON_OPTIONS}
                          onChange={setReason}/>
        </EditorDialog>
    );
};

export default SupersedeRequestDialog;
