import React, { ChangeEvent } from 'react';
import {Field, Input, InfoLabel, InputOnChangeData} from "@fluentui/react-components";

interface SessionRecipientsTabProps {
    recipientEmail: string;
    onRecipientEmailChange: (e: ChangeEvent<HTMLInputElement>, newValue: InputOnChangeData) => void;
    requestingDocuments: string | null;
    setMessageGroupMessages: (messages: string[]) => void;
}

const SessionRecipientsTab: React.FC<SessionRecipientsTabProps> = ({ recipientEmail, onRecipientEmailChange, requestingDocuments, setMessageGroupMessages }) => {
    return (
        <div id="recipients-tab-content">
            <InfoLabel
                info={
                    <>
                        The email doesn't have to be a registered user.{" "}
                    </>
                }>
                {requestingDocuments === 'true' ?
                    'Enter email to request documents from' :
                    'Enter email to send documents to'}
            </InfoLabel>
            <Field>
                <Input type="email"
                       value={recipientEmail}
                       onChange={(e, data) => {
                           onRecipientEmailChange(e, data);
                           setMessageGroupMessages([]);
                       }}
                       placeholder={"Recipient email"}/>
            </Field>
        </div>
    );
}

export default SessionRecipientsTab;