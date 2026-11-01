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
    locked?: boolean;
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
        locked,
    }) =>
{
    const handleExchangeNameChange = (e: ChangeEvent<HTMLInputElement>, data: InputOnChangeData) =>
    {
        onExchangeNameChange(e, data);
        setMessageGroupMessages([]);
    };

    const styles = useExchangeInitiationStyles();

    return (
        <div id={"exchange-initiation-details-tab-div-1"}
             className={styles.exchangeDetailsTap}>
            <Field id={"exchange-initiation-details-tab-field-1"}
                   label="Exchange Name"
                   required>
                {availableVariables && onNameChange ? (
                    <VariableTokenInput
                        value={name}
                        onChange={v => { onNameChange(v); setMessageGroupMessages([]); }}
                        availableVariables={availableVariables}
                        resolvedPreview={resolvedPreview}
                        placeholder="Exchange name, type {{ to insert a variable"
                        disabled={locked}
                    />
                ) : (
                    <Input
                        id={"exchange-name-input"}
                        type="text"
                        value={name}
                        required
                        onChange={handleExchangeNameChange}
                        placeholder=""
                        disabled={locked}
                    />
                )}
            </Field>
            <Field id={"exchange-initiation-details-tab-field-2"}
                   label="Description">
                {availableVariables && onDescChange ? (
                    <VariableTokenInput
                        value={description}
                        onChange={onDescChange}
                        availableVariables={availableVariables}
                        resolvedPreview={resolvedPreview}
                        multiline
                        placeholder="Add context about this Exchange (optional)"
                        disabled={locked}
                    />
                ) : (
                    <Textarea
                        id={"exchange-description-textarea"}
                        onChange={onDescriptionChange}
                        value={description}
                        placeholder="Add context about this Exchange (optional)"
                        disabled={locked}
                    />
                )}
            </Field>
            <Field id={"exchange-initiation-details-tab-field-3"}
                   label="Message to Recipients">
                {availableVariables && onMessageChange ? (
                    <VariableTokenInput
                        value={initialShareMessage}
                        onChange={onMessageChange}
                        availableVariables={availableVariables}
                        resolvedPreview={resolvedPreview}
                        multiline
                        placeholder="Include any instructions or details recipients should know (optional)"
                        disabled={locked}
                    />
                ) : (
                    <Textarea
                        id={"exchange-share-message-textarea"}
                        onChange={onInitialShareMessageChange}
                        value={initialShareMessage}
                        placeholder="Include any instructions or details recipients should know (optional)"
                        disabled={locked}
                    />
                )}
            </Field>
        </div>
    );
};

export default ExchangeInitiationDetailsTab;