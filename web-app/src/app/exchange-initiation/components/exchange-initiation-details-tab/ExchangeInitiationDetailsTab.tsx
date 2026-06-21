import React, {ChangeEvent} from 'react';
import {Field, Input, InputOnChangeData, Textarea} from "@fluentui/react-components";
import {useExchangeInitiationStyles} from "../../ExchangeInitiationStyles.tsx";
import {AvailableVariablesDto} from "../../../models/models.tsx";
import VariableTokenInput from "../../../../components/variable-token-input/VariableTokenInput.tsx";

interface ExchangeDetailsTabProps
{
    name: string;
    description: string;
    setMessageGroupMessages: (messages: string[]) => void;
    initialShareMessage: string;
    onExchangeNameChange: (e: ChangeEvent<HTMLInputElement>, newValue: InputOnChangeData) => void;
    onDescriptionChange: (e: ChangeEvent<HTMLTextAreaElement>, newValue: InputOnChangeData) => void;
    onInitialShareMessageChange: (e: ChangeEvent<HTMLTextAreaElement>, newValue: InputOnChangeData) => void;
    availableVariables?: AvailableVariablesDto;
    onNameChange?: (value: string) => void;
    onDescChange?: (value: string) => void;
    onMessageChange?: (value: string) => void;
    resolvedPreview?: Record<string, string>;
}

const ExchangeInitiationDetailsTab: React.FC<ExchangeDetailsTabProps> = (
    {
        name,
        description,
        initialShareMessage,
        onExchangeNameChange,
        onDescriptionChange,
        onInitialShareMessageChange,
        setMessageGroupMessages,
        availableVariables,
        onNameChange,
        onDescChange,
        onMessageChange,
        resolvedPreview,
    }) =>
{
    const handleExchangeNameChange = (e: ChangeEvent<HTMLInputElement>, data: InputOnChangeData) =>
    {
        onExchangeNameChange(e, data);
        setMessageGroupMessages([]);
    };

    const styles = useExchangeInitiationStyles();

    return (
        <div className={styles.exchangeDetailsTap}>
            <Field label="Exchange Name" required>
                {availableVariables && onNameChange ? (
                    <VariableTokenInput
                        value={name}
                        onChange={v => { onNameChange(v); setMessageGroupMessages([]); }}
                        availableVariables={availableVariables}
                        resolvedPreview={resolvedPreview}
                        placeholder="Exchange name, type {{ to insert a variable"
                    />
                ) : (
                    <Input
                        type="text"
                        value={name}
                        required
                        onChange={handleExchangeNameChange}
                        placeholder=""
                    />
                )}
            </Field>
            <Field label="Description">
                {availableVariables && onDescChange ? (
                    <VariableTokenInput
                        value={description}
                        onChange={onDescChange}
                        availableVariables={availableVariables}
                        resolvedPreview={resolvedPreview}
                        multiline
                        placeholder="Add context about this exchange (optional)"
                    />
                ) : (
                    <Textarea
                        onChange={onDescriptionChange}
                        value={description}
                        placeholder="Add context about this exchange (optional)"
                    />
                )}
            </Field>
            <Field label="Message to Recipients">
                {availableVariables && onMessageChange ? (
                    <VariableTokenInput
                        value={initialShareMessage}
                        onChange={onMessageChange}
                        availableVariables={availableVariables}
                        resolvedPreview={resolvedPreview}
                        multiline
                        placeholder="Include any instructions or details recipients should know (optional)"
                    />
                ) : (
                    <Textarea
                        onChange={onInitialShareMessageChange}
                        value={initialShareMessage}
                        placeholder="Include any instructions or details recipients should know (optional)"
                    />
                )}
            </Field>
        </div>
    );
};

export default ExchangeInitiationDetailsTab;