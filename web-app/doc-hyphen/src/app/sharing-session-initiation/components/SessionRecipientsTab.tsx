import React, {ChangeEvent} from 'react';
import {Field, InfoLabel, Input, InputOnChangeData} from "@fluentui/react-components";
import {useSharingSessionInitiationStyles} from "../SharingSessionInitiationStyles.tsx";

interface SessionRecipientsTabProps
{
    recipientEmail: string;
    onRecipientEmailChange: (e: ChangeEvent<HTMLInputElement>, newValue: InputOnChangeData) => void;
    requestingDocuments: boolean | null;
    setMessageGroupMessages: (messages: string[]) => void;
}

const SessionRecipientsTab: React.FC<SessionRecipientsTabProps> = (
    {
        recipientEmail,
        onRecipientEmailChange,
        requestingDocuments,
        setMessageGroupMessages
    }) =>
{
    const styles = useSharingSessionInitiationStyles();

    return (
        <div className={styles.recipientsTabContent}>
            <InfoLabel
                info={
                    <>
                        The email doesn't have to be a registered user.{" "}
                    </>
                }>
                {requestingDocuments ?
                    'Enter email to request documents from' :
                    'Enter email to send documents to'}
            </InfoLabel>
            <Field>
                <Input
                    type="email"
                    value={recipientEmail}
                    onChange={(e, data) =>
                    {
                        onRecipientEmailChange(e, data);
                        setMessageGroupMessages([]);
                    }}
                    placeholder={"Recipient email"}
                />
            </Field>
        </div>
    );
}

export default SessionRecipientsTab;