import {useState} from "react";
import {InformationRequestClockChangePath} from "../../../../services/informationRequestAdministrationService.ts";
import {InformationRequestClockDto} from "../../../models/models.tsx";
import EditorDialog from "../../shared/editor-dialog/EditorDialog.tsx";
import NumberField from "../../shared/number-field/NumberField.tsx";
import TextField from "../../shared/text-field/TextField.tsx";
import {humanizedKey} from "../../submission/submissionLabels.ts";

interface Props
{
    clock: InformationRequestClockDto;
    path: InformationRequestClockChangePath;
    busy: boolean;
    onConfirm: (reasonCode: string, extensionMinutes?: number) => void;
    onDismiss: () => void;
}

const VERBS: Record<InformationRequestClockChangePath, string> = {
    pauses: "Pause",
    resumptions: "Resume",
    extensions: "Extend",
};

const ClockChangeDialog = ({clock, path, busy, onConfirm, onDismiss}: Props) =>
{
    const [reason, setReason] = useState("");
    const [minutes, setMinutes] = useState<number | undefined>(undefined);
    const extending = path === "extensions";
    const ready = Boolean(reason.trim()) && (!extending || (minutes !== undefined && minutes > 0));

    return (
        <EditorDialog id={"information-request-clock-change-dialog"}
                      title={`${VERBS[path]} the ${humanizedKey(clock.clockKey)} clock`}
                      confirmLabel={VERBS[path]}
                      busy={busy}
                      confirmDisabled={!ready}
                      onConfirm={() => onConfirm(reason.trim(), extending ? minutes : undefined)}
                      onDismiss={onDismiss}>
            <TextField id={"information-request-clock-change-reason"}
                       label={"Reason"}
                       hint={"Recorded in the clock history."}
                       value={reason}
                       maxLength={120}
                       onChange={setReason}/>
            {extending && (
                <NumberField id={"information-request-clock-change-minutes"}
                             label={"Minutes to add"}
                             value={minutes}
                             min={1}
                             step={1}
                             onChange={setMinutes}/>
            )}
        </EditorDialog>
    );
};

export default ClockChangeDialog;
