import React, {ChangeEvent} from 'react';
import {Button, Field, Input, InputOnChangeData, Textarea} from "@fluentui/react-components";
import {useExchangeInitiationStyles} from "../../ExchangeInitiationStyles.tsx";

interface ExchangeDetailsTabProps
{
    name: string;
    description: string;
    setMessageGroupMessages: (messages: string[]) => void;
    initialShareMessage: string;
    onExchangeNameChange: (e: ChangeEvent<HTMLInputElement>, newValue: InputOnChangeData) => void;
    onDescriptionChange: (e: ChangeEvent<HTMLTextAreaElement>, newValue: InputOnChangeData) => void;
    onInitialShareMessageChange: (e: ChangeEvent<HTMLTextAreaElement>, newValue: InputOnChangeData) => void;
}

const ExchangeInitiationDetailsTab: React.FC<ExchangeDetailsTabProps> = (
    {
        name,
        description,
        initialShareMessage,
        onExchangeNameChange,
        onDescriptionChange,
        onInitialShareMessageChange,
        setMessageGroupMessages
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
                <Input
                    type="text"
                    value={name}
                    required
                    onChange={handleExchangeNameChange}
                    placeholder="Required"
                />
            </Field>
            {/*<Button onClick={ () => {}}> Generate from sequence</Button>*/}
            <Field label="Description">
                <Textarea
                    onChange={onDescriptionChange}
                    value={description}
                    placeholder="Optional"
                />
            </Field>
            <Field label="Custom message">
                <Textarea
                    onChange={onInitialShareMessageChange}
                    value={initialShareMessage}
                    placeholder="Optional"
                />
            </Field>
        </div>
    );
};

export default ExchangeInitiationDetailsTab;