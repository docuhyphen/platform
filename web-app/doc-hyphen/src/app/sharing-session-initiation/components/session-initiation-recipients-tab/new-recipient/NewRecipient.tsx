import React, {useEffect, useState} from 'react';
import {Field, InfoLabel, Input} from "@fluentui/react-components";
import {useSessionInitiationRecipientsTabStyles} from "../SessionInitiationRecipientsTabStyles.tsx";
import {AppUserDetailedDto} from "../../../../models/models.tsx";
import MyOrgRecipients from "../MyOrgRecipients.tsx";
import {fetchMyOrganizationUsers} from "../../../../../services/organizationApi";

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
    internalParticipants?: AppUserDetailedDto[];
    setInternalParticipants?: (users: AppUserDetailedDto[]) => void;
}

const NewRecipient: React.FC<NewRecipientProps> = (
    {
        isRequestingDocuments,
        setNewRecipient,
        newRecipient,
        internalParticipants,
        setInternalParticipants
    }) =>
{
    const styles = useSessionInitiationRecipientsTabStyles();
    const [recipient, setRecipient] = useState<SharingSessionNewMainRecipient>({
        email: newRecipient?.email || '',
        firstName: newRecipient?.firstName || '',
        lastName: newRecipient?.lastName || ''
    });

    const [selectedInternalRecipients, setSelectedInternalParticipants] = useState<AppUserDetailedDto[]>([]);
    const [orgUsers, setOrgUsers] = useState<AppUserDetailedDto[]>([]);
    const [isLoadingUsers, setIsLoadingUsers] = useState<boolean>(false);
    const [usersLoaded, setUsersLoaded] = useState<boolean>(false);

    const isRecipientDataValid = () =>
    {
        return recipient.email && recipient.email.includes('@') &&
            recipient.firstName && recipient.firstName.trim() !== '' &&
            recipient.lastName && recipient.lastName.trim() !== '';
    };

    useEffect(() =>
    {
        if (isRecipientDataValid() && !usersLoaded)
        {
            loadMyOrganizationUsers();
        }
    }, [recipient.email, recipient.firstName, recipient.lastName, usersLoaded]);

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

    useEffect(() =>
    {
        console.log("UseEffect for internalParticipants", internalParticipants);
        if (internalParticipants)
        {
            setSelectedInternalParticipants([...internalParticipants]);
        }
    }, [internalParticipants]);

    const loadMyOrganizationUsers = async () =>
    {
        setIsLoadingUsers(true);
        try
        {
            const users = await fetchMyOrganizationUsers();
            setOrgUsers(users);
            setUsersLoaded(true);
        }
        catch (error)
        {
            console.error("Error loading my organization users:", error);
        }
        finally
        {
            setIsLoadingUsers(false);
        }
    };

    const updateRecipient = (field: keyof SharingSessionNewMainRecipient, value: string) =>
    {
        const updated = {...recipient, [field]: value};
        setRecipient(updated);
        setNewRecipient(updated);

        if (!isRecipientDataValid() && usersLoaded)
        {
            setUsersLoaded(false);
        }
    };

    return (
        <>
            <Field label={
                <InfoLabel info="The email doesn't have to be from a registered user.">
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

            {isRecipientDataValid() && (
                <MyOrgRecipients
                    orgUsers={orgUsers}
                    isLoadingUsers={isLoadingUsers}
                    selectedInternalRecipients={selectedInternalRecipients}
                    setSelectedInternalParticipants={setSelectedInternalParticipants}
                    setInternalParticipants={setInternalParticipants}
                />
            )}
        </>
    );
};

export default NewRecipient;