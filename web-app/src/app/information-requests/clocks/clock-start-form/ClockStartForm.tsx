import {useState} from "react";
import {Button} from "@fluentui/react-components";
import {InformationRequestClockUrgency} from "../../../models/models.tsx";
import ChoiceSelect from "../../shared/choice-select/ChoiceSelect.tsx";
import TextField from "../../shared/text-field/TextField.tsx";
import {ClockPolicyChoice} from "../useRequestClocks.ts";
import {useClockStartFormStyles} from "./ClockStartFormStyles.tsx";

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

const KEY_PATTERN = /^[a-z0-9._-]+$/;

const ClockStartForm = ({choices, busy, onStart}: Props) =>
{
    const styles = useClockStartFormStyles();
    const [versionId, setVersionId] = useState("");
    const [urgency, setUrgency] = useState(InformationRequestClockUrgency.STANDARD);
    const [clockKey, setClockKey] = useState("response");
    const keyValid = KEY_PATTERN.test(clockKey.trim());

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
                       validationMessage={keyValid ? undefined : "Use lowercase letters, digits, dots, dashes, or underscores."}
                       onChange={setClockKey}/>
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
