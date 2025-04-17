import React, {useEffect} from 'react';
import {Field, InfoLabel, Input} from "@fluentui/react-components";
import {useSessionInitiationRecipientsTabStyles} from "../SessionInitiationRecipientsTabStyles.tsx";

export interface SharingSessionNewMainRecipient
{
    email: string;
    firstName: string;
    lastName: string;
}

interface NewRecipientProps
{
    isRequestingDocuments: boolean | null | undefined;
    setNewRecipient: (recipient: SharingSessionNewMainRecipient) => void;
    newRecipient?: SharingSessionNewMainRecipient;
}

const NewRecipient: React.FC<NewRecipientProps> = (
    {
        isRequestingDocuments,
        setNewRecipient,
        newRecipient
    }) =>
{
    const styles = useSessionInitiationRecipientsTabStyles();

    const [recipient, setRecipient] = React.useState<SharingSessionNewMainRecipient>({
        email: newRecipient?.email || '',
        firstName: newRecipient?.firstName || '',
        lastName: newRecipient?.lastName || ''
    });

    // Sync recipient state when props change
    useEffect(() =>
    {
        if (newRecipient)
        {
            setRecipient({
                email: newRecipient.email || recipient.email,
                firstName: newRecipient.firstName || recipient.firstName,
                lastName: newRecipient.lastName || recipient.lastName
            });
        }
    }, [newRecipient]);

    const updateRecipient = (field: keyof SharingSessionNewMainRecipient, value: string) =>
    {
        const updated = {...recipient, [field]: value};
        setRecipient(updated);
        setNewRecipient(updated);
    };

    return (
        <>
            <Field label={
                <InfoLabel
                    info="The email doesn't have to be from a registered user.">
                    {isRequestingDocuments ?
                        'Email to request documents from' :
                        'Email to send documents to'}
                </InfoLabel>
            }>
                <Input
                    type="email"
                    value={recipient.email}
                    onChange={(e) => updateRecipient('email', e.target.value)}
                    placeholder="Email"
                />
            </Field>
            <div className={styles.recipientEmailFields}>
                <Field className={styles.recipientEmail}>
                    <Input
                        type="text"
                        value={recipient.firstName}
                        onChange={(e) => updateRecipient('firstName', e.target.value)}
                        placeholder="First Name"
                    />
                </Field>
                <Field className={styles.recipientEmail}>
                    <Input
                        type="text"
                        value={recipient.lastName}
                        onChange={(e) => updateRecipient('lastName', e.target.value)}
                        placeholder="Last Name"
                    />
                </Field>
            </div>
        </>
    );
};

export default NewRecipient;