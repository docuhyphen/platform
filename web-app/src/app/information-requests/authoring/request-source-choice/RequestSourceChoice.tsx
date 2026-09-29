import {Field, Radio, RadioGroup} from "@fluentui/react-components";
import {RequestSource} from "../create-information-request-dialog/useCreateInformationRequest.ts";

interface Props
{
    value: RequestSource;
    disabled: boolean;
    onChange: (value: RequestSource) => void;
}

const SOURCES: {value: RequestSource; label: string}[] = [
    {value: "template", label: "From a Template"},
    {value: "blueprint", label: "From a Blueprint"},
    {value: "adhoc", label: "Write a one-off request"},
];

const RequestSourceChoice = ({value, disabled, onChange}: Props) => (
    <Field id={"create-information-request-source-field"}
           label={"Start from"}>
        <RadioGroup id={"create-information-request-source"}
                    value={value}
                    disabled={disabled}
                    layout={"vertical"}
                    onChange={(_, data) =>
                    {
                        const chosen = SOURCES.find(source => source.value === data.value);
                        if (chosen) onChange(chosen.value);
                    }}>
            {SOURCES.map(source => (
                <Radio key={source.value}
                       id={`create-information-request-source-${source.value}`}
                       value={source.value}
                       label={source.label}/>
            ))}
        </RadioGroup>
    </Field>
);

export default RequestSourceChoice;
