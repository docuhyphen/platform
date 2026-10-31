import {Field, Radio, RadioGroup} from "@fluentui/react-components";
import {InformationRequestResponseDisposition} from "../../../models/models.tsx";
import {dispositionLabels} from "../../shared/informationRequestLabels.ts";
import {useResponseDispositionChoiceStyles} from "./ResponseDispositionChoiceStyles.tsx";

interface Props
{
    elementId: string;
    promptId: string;
    value: InformationRequestResponseDisposition;
    options: InformationRequestResponseDisposition[];
    disabled?: boolean;
    onChange: (value: InformationRequestResponseDisposition) => void;
}

const ResponseDispositionChoice = ({elementId, promptId, value, options, disabled, onChange}: Props) => {
    const styles = useResponseDispositionChoiceStyles();
    const labelId = `information-request-response-disposition-label-${elementId}`;

    return (
        <Field id={`information-request-response-disposition-field-${elementId}`}
               label={{id: labelId, children: "Your answer"}}>
            <RadioGroup id={`information-request-response-disposition-${elementId}`}
                        aria-labelledby={`${labelId} ${promptId}`}
                        value={value}
                        disabled={disabled}
                        layout={"horizontal"}
                        className={styles.choices}
                        onChange={(_, data) => {
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
};

export default ResponseDispositionChoice;
