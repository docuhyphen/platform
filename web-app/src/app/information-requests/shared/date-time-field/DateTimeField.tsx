import {Field, Input} from "@fluentui/react-components";

interface Props
{
    id: string;
    label: string;
    value: string;
    hint?: string;
    required?: boolean;
    disabled?: boolean;
    validationMessage?: string;
    onChange: (value: string) => void;
}

const DateTimeField = ({id, label, value, hint, required, disabled, validationMessage, onChange}: Props) => (
    <Field id={`${id}-field`}
           label={label}
           hint={hint}
           required={required}
           validationState={validationMessage ? "error" : "none"}
           validationMessage={validationMessage}>
        <Input id={id}
               type={"datetime-local"}
               value={value}
               disabled={disabled}
               onChange={(_, data) => onChange(data.value)}/>
    </Field>
);

export default DateTimeField;
