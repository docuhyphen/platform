import {Field, Radio, RadioGroup} from "@fluentui/react-components";
import {InformationRequestAttestingPartyDto} from "../../../models/models.tsx";
import {attestingPartyLabel} from "../submissionLabels.ts";

interface Props {
    id: string;
    parties: InformationRequestAttestingPartyDto[];
    value: string | undefined;
    disabled: boolean;
    onChange: (partyId: string) => void;
}

const AttestingPartyChoice = ({id, parties, value, disabled, onChange}: Props) => (
    <Field id={`${id}-party-field`}
           label={"Confirm as"}
           hint={"You take part in this request in more than one way. Choose the one you are confirming for."}
           required>
        <RadioGroup id={`${id}-party`}
                    value={value ?? ""}
                    disabled={disabled}
                    onChange={(_, data) => onChange(data.value)}>
            {parties.map(party => (
                <Radio key={party.partyId}
                       id={`${id}-party-${party.partyId}`}
                       value={party.partyId}
                       label={attestingPartyLabel(party)}/>
            ))}
        </RadioGroup>
    </Field>
);

export default AttestingPartyChoice;
