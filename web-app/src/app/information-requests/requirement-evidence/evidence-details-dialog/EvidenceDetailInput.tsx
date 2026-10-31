import {Field, Input, Select} from "@fluentui/react-components";
import TextField from "../../shared/text-field/TextField.tsx";
import {CapturedEvidenceDetail} from "../evidenceDetails.ts";

interface Props {
    id: string;
    detail: CapturedEvidenceDetail;
    value: string;
    onChange: (value: string) => void;
}

const EvidenceDetailInput = ({id, detail, value, onChange}: Props) => {
    if (detail.acceptedValues.length > 0) {
        const choices = value && !detail.acceptedValues.includes(value)
            ? [value, ...detail.acceptedValues]
            : detail.acceptedValues;
        return (
            <Field id={`${id}-field`}
                   label={detail.label}
                   required={detail.required}>
                <Select id={id}
                        value={value}
                        onChange={(_, data) => onChange(data.value)}>
                    <option id={`${id}-none`}
                            value={""}>
                        Choose
                    </option>
                    {choices.map((choice, index) => (
                        <option key={choice}
                                id={`${id}-option-${index}`}
                                value={choice}>
                            {choice}
                        </option>
                    ))}
                </Select>
            </Field>
        );
    }

    if (detail.kind === "date") {
        return (
            <Field id={`${id}-field`}
                   label={detail.label}
                   required={detail.required}>
                <Input id={id}
                       type={"date"}
                       value={value}
                       onChange={(_, data) => onChange(data.value)}/>
            </Field>
        );
    }

    return (
        <TextField id={id}
                   label={detail.label}
                   value={value}
                   required={detail.required}
                   maxLength={detail.maxLength}
                   onChange={onChange}/>
    );
};

export default EvidenceDetailInput;
