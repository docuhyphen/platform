import React, {useEffect, useState} from 'react';
import {Field, InfoLabel, Input} from "@fluentui/react-components";
import {useSessionInitiationRecipientsTabStyles} from "../SessionInitiationRecipientsTabStyles.tsx";
import {AppUserDetailedDto} from "../../../../models/models.tsx";
import MyOrgRecipients from "../MyOrgRecipients.tsx";
import {fetchMyOrganizationUsers} from "../../../../../services/organizationApi";
import {useAuth} from "../../../../../context/AuthContext.tsx";

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

    const {appUser, appUserPersonOrganization} = useAuth()
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

    const onEmailChange = (e: React.ChangeEvent<HTMLInputElement>) =>
    {
        if (e.target.value && e.target.value.length)
        {
            updateRecipient('email', e.target.value.trim().toLowerCase())
        }
        else
        {
            updateRecipient('email', '')
        }
    }
    const onFirstNameChange = (e: React.ChangeEvent<HTMLInputElement>) =>
    {
        if (e.target.value && e.target.value.length)
        {
            updateRecipient('firstName', e.target.value.trim())
        }
        else
        {
            updateRecipient('firstName', '')
        }
    }

    const onLastNameChange = (e: React.ChangeEvent<HTMLInputElement>) =>
    {
        if (e.target.value && e.target.value.length)
        {
            updateRecipient('lastName', e.target.value.trim())
        }
        else
        {
            updateRecipient('lastName', '')
        }
    }

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
                    onChange={onEmailChange}
                    placeholder="Email"
                />
            </Field>
            <div className={styles.recipientEmailFields}>
                <Field className={styles.recipientEmail}>
                    <Input
                        type="text"
                        value={recipient.firstName}
                        onChange={onFirstNameChange}
                        placeholder="First Name"
                    />
                </Field>
                <Field className={styles.recipientEmail}>
                    <Input
                        type="text"
                        value={recipient.lastName}
                        onChange={onLastNameChange}
                        placeholder="Last Name"
                    />
                </Field>
            </div>

            {appUserPersonOrganization && isRecipientDataValid() && (
                <MyOrgRecipients
                    orgUsers={orgUsers.filter(u => u.id !== appUser?.id)}
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