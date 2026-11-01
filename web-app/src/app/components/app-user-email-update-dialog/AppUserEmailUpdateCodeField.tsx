import React from "react";
import {Field, Input, Text} from "@fluentui/react-components";

interface AppUserEmailUpdateCodeFieldProps
{
    fieldId: string;
    textId: string;
    inputId: string;
    label: string;
    sentTo?: string;
    value: string;
    onChange: (value: string) => void;
    onSubmit: () => void;
}

const AppUserEmailUpdateCodeField: React.FC<AppUserEmailUpdateCodeFieldProps> = (
    {
        fieldId,
        textId,
        inputId,
        label,
        sentTo,
        value,
        onChange,
        onSubmit
    }) =>
{
    return (
        <Field id={fieldId}
               label={label}>
            <Text id={textId}
                  size={200}
                  block>
                A verification code has been sent to {sentTo}. Please enter it below.
            </Text>
            <Input
                id={inputId}
                type="text"
                value={value}
                onChange={(e) => onChange(e.target.value)}
                onKeyDown={(e) => { if (e.key === "Enter") onSubmit(); }}
                maxLength={10}
            />
        </Field>
    );
};

export default AppUserEmailUpdateCodeField;
