import {Field, Radio, RadioGroup} from "@fluentui/react-components";
import {InformationRequestResponseDisposition} from "../../../models/models.tsx";
import {dispositionLabels} from "../../shared/informationRequestLabels.ts";

interface Props
{
    elementId: string;
    value: InformationRequestResponseDisposition;
    options: InformationRequestResponseDisposition[];
    onChange: (value: InformationRequestResponseDisposition) => void;
}

const ResponseDispositionChoice = ({elementId, value, options, onChange}: Props) => (
    <Field id={`information-request-response-disposition-field-${elementId}`}
           label={"Your answer"}>
        <RadioGroup id={`information-request-response-disposition-${elementId}`}
                    value={value}
                    layout={"horizontal"}
                    onChange={(_, data) =>
                    {
                        const chosen = options.find(option => option === data.value);
                        if (chosen) onChange(chosen);
                    }}>
            {options.map(option => (
                <Radio key={option}
                       id={`information-request-response-disposition-${elementId}-${option}`}
                       value={option}
                       label={dispositionLabels[option]}/>
            ))}
        </RadioGroup>
    </Field>
);

export default ResponseDispositionChoice;
