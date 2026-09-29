import {Field, Input, Textarea} from "@fluentui/react-components";

interface TextFieldProps
{
    id: string;
    label: string;
    value: string;
    disabled?: boolean;
    hint?: string;
    required?: boolean;
    multiline?: boolean;
    maxLength?: number;
    className?: string;
    validationMessage?: string;
    onChange: (value: string) => void;
}

const TextField = ({
    id,
    label,
    value,
    disabled,
    hint,
    required,
    multiline,
    maxLength,
    className,
    validationMessage,
    onChange,
}: TextFieldProps) => (
    <Field id={`${id}-field`}
           label={label}
           hint={hint}
           required={required}
           className={className}
           validationState={validationMessage ? "error" : "none"}
           validationMessage={validationMessage}>
        {multiline
            ? (
                <Textarea id={id}
                          value={value}
                          disabled={disabled}
                          maxLength={maxLength}
                          resize={"vertical"}
                          onChange={(_, data) => onChange(data.value)}/>
            )
            : (
                <Input id={id}
                       value={value}
                       disabled={disabled}
                       maxLength={maxLength}
                       onChange={(_, data) => onChange(data.value)}/>
            )}
    </Field>
);

export default TextField;
