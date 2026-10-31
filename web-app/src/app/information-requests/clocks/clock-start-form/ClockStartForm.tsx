import {useState} from "react";
import {Button, Text} from "@fluentui/react-components";
import {InformationRequestClockUrgency} from "../../../models/models.tsx";
import ChoiceSelect from "../../shared/choice-select/ChoiceSelect.tsx";
import TextField from "../../shared/text-field/TextField.tsx";
import {ClockPolicyChoice} from "../useRequestClocks.ts";
import {useClockStartFormStyles} from "./ClockStartFormStyles.tsx";
import {isMachineKey, MACHINE_KEY_MESSAGE} from "../../shared/machineKey.ts";

interface Props
{
    choices: ClockPolicyChoice[];
    busy: boolean;
    onStart: (policyVersionId: string, urgency: InformationRequestClockUrgency, clockKey: string) => void;
}

const URGENCIES = [
    {value: InformationRequestClockUrgency.STANDARD, label: "Standard"},
    {value: InformationRequestClockUrgency.URGENT, label: "Urgent"},
];

const ClockStartForm = ({choices, busy, onStart}: Props) =>
{
    const styles = useClockStartFormStyles();
    const [versionId, setVersionId] = useState("");
    const [urgency, setUrgency] = useState(InformationRequestClockUrgency.STANDARD);
    const [clockKey, setClockKey] = useState("response");
    const keyValid = isMachineKey(clockKey) && clockKey.trim().length <= 64;

    return (
        <div id={"information-request-clock-start"}
             role={"group"}
             aria-label={"Start a clock"}
             className={styles.form}>
            <ChoiceSelect id={"information-request-clock-start-policy"}
                          label={"Due policy"}
                          value={versionId}
                          placeholder={"Choose a due policy"}
                          hint={choices.length === 0 ? "Define a due policy under Information Request operations first." : undefined}
                          disabled={busy}
                          options={choices.map(choice => ({value: choice.versionId, label: choice.label}))}
                          onChange={setVersionId}/>
            <ChoiceSelect<InformationRequestClockUrgency> id={"information-request-clock-start-urgency"}
                                                          label={"Urgency"}
                                                          value={urgency}
                                                          disabled={busy}
                                                          options={URGENCIES}
                                                          onChange={setUrgency}/>
            <TextField id={"information-request-clock-start-key"}
                       label={"Clock key"}
                       value={clockKey}
                       disabled={busy}
                       maxLength={64}
                       validationMessage={keyValid ? undefined : clockKey.trim().length > 64 ? "Use at most 64 characters." : MACHINE_KEY_MESSAGE}
                       onChange={setClockKey}/>
            <Text id={"information-request-clock-start-time"}>
                Time starts when you select Start clock. The saved clock shows its due time.
            </Text>
            <Button id={"information-request-clock-start-submit"}
                    appearance={"secondary"}
                    shape={"circular"}
                    className={styles.submit}
                    disabled={busy || !versionId || !keyValid}
                    onClick={() => onStart(versionId, urgency, clockKey.trim())}>
                Start clock
            </Button>
        </div>
    );
};

export default ClockStartForm;
