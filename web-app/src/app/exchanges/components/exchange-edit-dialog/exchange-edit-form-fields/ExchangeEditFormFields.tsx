import React from "react";
import {Field, Input, InputOnChangeData, Textarea} from "@fluentui/react-components";

interface ExchangeEditFormFieldsProps
{
    name: string;
    description: string;
    onNameChange: (name: string) => void;
    onDescriptionChange: (description: string) => void;
}

const ExchangeEditFormFields: React.FC<ExchangeEditFormFieldsProps> = (
    {
        name,
        description,
        onNameChange,
        onDescriptionChange
    }) =>
{
    const handleNameChange = (_e: React.ChangeEvent<HTMLInputElement>, newValue: InputOnChangeData) =>
    {
        onNameChange(newValue.value || '');
    };

    const handleDescriptionChange = (_e: React.ChangeEvent<HTMLTextAreaElement>, newValue: { value: string }) =>
    {
        onDescriptionChange(newValue.value || '');
    };

    return (
        <>
            <Field id={"exchange-edit-dialog-field-1"}
                   label={"Exchange name"}>
                <Input id={"input-edit-exchange-name"}
                       type={"text"}
                       value={name}
                       onChange={handleNameChange}/>
            </Field>
            <Field id={"exchange-edit-dialog-field-2"}
                   label={"Description"}>
                <Textarea id={"textarea-edit-exchange-description"}
                          value={description}
                          onChange={handleDescriptionChange}/>
            </Field>
        </>
    );
};

export default ExchangeEditFormFields;
