import {useState} from "react";
import {
    InformationRequestBusinessDecisionDto,
    InformationRequestBusinessDecisionKind,
    RecordInformationRequestBusinessDecisionRequest,
} from "../../../models/models.tsx";
import DateTimeField from "../../shared/date-time-field/DateTimeField.tsx";
import {instantFromLocalInput, localDateTimeInputValue} from "../../shared/dateTimeInput.ts";
import EditorDialog from "../../shared/editor-dialog/EditorDialog.tsx";
import {isMachineKey, machineKeyProblem} from "../../shared/machineKey.ts";
import TextField from "../../shared/text-field/TextField.tsx";
import {humanizedKey} from "../../submission/submissionLabels.ts";

interface Props
{
    kind: InformationRequestBusinessDecisionKind;
    prior?: InformationRequestBusinessDecisionDto;
    busy: boolean;
    onConfirm: (request: RecordInformationRequestBusinessDecisionRequest) => void;
    onDismiss: () => void;
}

const titleOf = (kind: InformationRequestBusinessDecisionKind, prior?: InformationRequestBusinessDecisionDto): string =>
{
    if (!prior || kind === InformationRequestBusinessDecisionKind.ORIGINAL) return "Record a business decision";
    const process = humanizedKey(prior.owningProcessKey);
    return kind === InformationRequestBusinessDecisionKind.APPEAL
        ? `Appeal the ${process} decision`
        : `Reconsider the ${process} decision`;
};

const RecordDecisionDialog = ({kind, prior, busy, onConfirm, onDismiss}: Props) =>
{
    const [process, setProcess] = useState(prior?.owningProcessKey ?? "");
    const [outcome, setOutcome] = useState("");
    const [decided, setDecided] = useState(() => localDateTimeInputValue(new Date()));
    const [reasonReference, setReasonReference] = useState("");
    const [externalReference, setExternalReference] = useState("");
    const decidedAt = instantFromLocalInput(decided);
    const future = Boolean(decidedAt && new Date(decidedAt) > new Date());
    const ready = isMachineKey(process) && isMachineKey(outcome) && Boolean(decidedAt) && !future;

    const confirm = () =>
    {
        if (!decidedAt) return;
        onConfirm({
            owningProcessKey: process.trim(),
            outcomeCode: outcome.trim(),
            kind,
            decidedAt,
            ...(prior && kind !== InformationRequestBusinessDecisionKind.ORIGINAL ? {priorDecisionId: prior.id} : {}),
            ...(reasonReference.trim() ? {reasonReference: reasonReference.trim()} : {}),
            ...(externalReference.trim() ? {externalReference: externalReference.trim()} : {}),
        });
    };

    return (
        <EditorDialog id={"information-request-record-decision-dialog"}
                      title={titleOf(kind, prior)}
                      confirmLabel={"Record decision"}
                      busy={busy}
                      confirmDisabled={!ready}
                      onConfirm={confirm}
                      onDismiss={onDismiss}>
            <TextField id={"information-request-record-decision-process"}
                       label={"Process"}
                       hint={"A short lowercase key naming the process that decided, such as intake."}
                       required={true}
                       disabled={Boolean(prior)}
                       value={process}
                       maxLength={128}
                       validationMessage={machineKeyProblem(process)}
                       onChange={setProcess}/>
            <TextField id={"information-request-record-decision-outcome"}
                       label={"Outcome"}
                       hint={"A short lowercase key, such as approved or declined."}
                       required={true}
                       value={outcome}
                       maxLength={128}
                       validationMessage={machineKeyProblem(outcome)}
                       onChange={setOutcome}/>
            <DateTimeField id={"information-request-record-decision-decided"}
                           label={"Decided"}
                           required={true}
                           value={decided}
                           validationMessage={future ? "A decision cannot be recorded for a time that has not happened yet." : undefined}
                           onChange={setDecided}/>
            <TextField id={"information-request-record-decision-reason"}
                       label={"Reason reference"}
                       hint={"Where the reasons are kept, if anywhere."}
                       value={reasonReference}
                       maxLength={500}
                       onChange={setReasonReference}/>
            <TextField id={"information-request-record-decision-external"}
                       label={"External reference"}
                       hint={"An identifier in another system, if any."}
                       value={externalReference}
                       maxLength={500}
                       onChange={setExternalReference}/>
        </EditorDialog>
    );
};

export default RecordDecisionDialog;
