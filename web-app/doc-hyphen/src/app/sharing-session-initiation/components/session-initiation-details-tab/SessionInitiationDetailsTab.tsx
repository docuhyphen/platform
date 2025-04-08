import React, {ChangeEvent} from 'react';
import {Field, Input, InputOnChangeData, Textarea} from "@fluentui/react-components";
import {useSharingSessionInitiationStyles} from "../../SharingSessionInitiationStyles.tsx";

interface SessionDetailsTabProps
{
    sessionName: string;
    description: string;
    setMessageGroupMessages: (messages: string[]) => void;
    initialShareMessage: string;
    onSessionNameChange: (e: ChangeEvent<HTMLInputElement>, newValue: InputOnChangeData) => void;
    onDescriptionChange: (e: ChangeEvent<HTMLTextAreaElement>, newValue: InputOnChangeData) => void;
    onInitialShareMessageChange: (e: ChangeEvent<HTMLTextAreaElement>, newValue: InputOnChangeData) => void;
}

const SessionInitiationDetailsTab: React.FC<SessionDetailsTabProps> = (
    {
        sessionName,
        description,
        initialShareMessage,
        onSessionNameChange,
        onDescriptionChange,
        onInitialShareMessageChange,
        setMessageGroupMessages
    }) =>
{
    const handleSessionNameChange = (e: ChangeEvent<HTMLInputElement>, data: InputOnChangeData) =>
    {
        onSessionNameChange(e, data);
        setMessageGroupMessages([]);
    };

    const styles = useSharingSessionInitiationStyles();

    return (
        <div className={styles.sessionDetailsTap}>
            <Field label="Session Name" required>
                <Input
                    type="text"
                    value={sessionName}
                    required
                    onChange={handleSessionNameChange}
                    placeholder="Required"
                />
            </Field>
            <Field label="Description">
                <Textarea
                    onChange={onDescriptionChange}
                    value={description}
                    placeholder="Optional"
                />
            </Field>
            <Field label="Start message">
                <Textarea
                    onChange={onInitialShareMessageChange}
                    value={initialShareMessage}
                    placeholder="Optional"
                />
            </Field>
        </div>
    );
};

export default SessionInitiationDetailsTab;