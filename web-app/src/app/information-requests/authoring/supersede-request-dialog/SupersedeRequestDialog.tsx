import {useEffect, useState} from "react";
import {Text} from "@fluentui/react-components";
import {getExchangeInformationRequests} from "../../../../services/informationRequestAuthoringService.ts";
import {InformationRequestState, InformationRequestSummaryDto} from "../../../models/models.tsx";
import ChoiceSelect from "../../shared/choice-select/ChoiceSelect.tsx";
import EditorDialog from "../../shared/editor-dialog/EditorDialog.tsx";
import TextField from "../../shared/text-field/TextField.tsx";

interface Props
{
    requestId: string;
    exchangeId: string;
    busy: boolean;
    onConfirm: (replacementId: string, reasonCode: string) => void;
    onDismiss: () => void;
}

const REPLACEABLE_STATES = new Set([InformationRequestState.DRAFT, InformationRequestState.ISSUED, InformationRequestState.IN_PROGRESS]);

const SupersedeRequestDialog = ({requestId, exchangeId, busy, onConfirm, onDismiss}: Props) =>
{
    const [candidates, setCandidates] = useState<InformationRequestSummaryDto[]>([]);
    const [replacementId, setReplacementId] = useState("");
    const [reason, setReason] = useState("");

    useEffect(() =>
    {
        let active = true;
        getExchangeInformationRequests(exchangeId)
            .then(listing =>
            {
                if (active) setCandidates(listing.requests.filter(summary => summary.id !== requestId && REPLACEABLE_STATES.has(summary.state)));
            })
            .catch(() => undefined);
        return () =>
        {
            active = false;
        };
    }, [exchangeId, requestId]);

    return (
        <EditorDialog id={"information-request-supersede-dialog"}
                      title={"Supersede this request"}
                      confirmLabel={"Supersede"}
                      busy={busy}
                      confirmDisabled={!replacementId || !reason.trim()}
                      onConfirm={() => onConfirm(replacementId, reason.trim())}
                      onDismiss={onDismiss}>
            <Text id={"information-request-supersede-explanation"}>
                The replacement request takes over. This request stops accepting answers and keeps its history.
            </Text>
            <ChoiceSelect id={"information-request-supersede-replacement"}
                          label={"Replacement request"}
                          value={replacementId}
                          placeholder={"Choose the replacement"}
                          hint={candidates.length === 0 ? "Create the replacement request in this Exchange first." : undefined}
                          options={candidates.map(candidate => ({value: candidate.id, label: candidate.title}))}
                          onChange={setReplacementId}/>
            <TextField id={"information-request-supersede-reason"}
                       label={"Reason"}
                       value={reason}
                       maxLength={120}
                       onChange={setReason}/>
        </EditorDialog>
    );
};

export default SupersedeRequestDialog;
