import {useEffect, useState} from "react";
import {useAuth} from "../../../../../context/AuthContext.tsx";
import {fetchMyOrganizationUsers} from "../../../../../services/organizationApi.ts";
import type {AppUserPublicDto} from "../../../../models/models.tsx";
import type {ExchangeNewMainRecipient} from "./NewRecipient.tsx";

interface UseNewRecipientProps
{
    newRecipient?: ExchangeNewMainRecipient;
    setNewRecipient: (recipient: ExchangeNewMainRecipient) => void;
    internalParticipants?: AppUserPublicDto[];
}

export const isExchangeNewMainRecipientValid = (recipient: ExchangeNewMainRecipient): boolean =>
    recipient.email.includes("@")
    && recipient.firstName.trim() !== ""
    && recipient.lastName.trim() !== "";

export const useNewRecipient = (
    {newRecipient, setNewRecipient, internalParticipants}: UseNewRecipientProps,
) =>
{
    const {appUser, appUserPersonOrganization} = useAuth();
    const [recipient, setRecipient] = useState<ExchangeNewMainRecipient>({
        email: newRecipient?.email || "",
        firstName: newRecipient?.firstName || "",
        lastName: newRecipient?.lastName || "",
    });
    const [selectedInternalRecipients, setSelectedInternalParticipants] = useState<AppUserPublicDto[]>([]);
    const [orgUsers, setOrgUsers] = useState<AppUserPublicDto[]>([]);
    const [isLoadingUsers, setIsLoadingUsers] = useState(false);
    const [usersLoaded, setUsersLoaded] = useState(false);

    useEffect(() =>
    {
        if (!newRecipient) return;
        setRecipient(previous => ({
            email: newRecipient.email || previous.email,
            firstName: newRecipient.firstName || previous.firstName,
            lastName: newRecipient.lastName || previous.lastName,
        }));
    }, [newRecipient]);

    useEffect(() =>
    {
        if (internalParticipants) setSelectedInternalParticipants([...internalParticipants]);
    }, [internalParticipants]);

    useEffect(() =>
    {
        if (!isExchangeNewMainRecipientValid(recipient) || usersLoaded) return;
        let active = true;
        setIsLoadingUsers(true);
        void fetchMyOrganizationUsers()
            .then(users =>
            {
                if (!active) return;
                setOrgUsers(users);
                setUsersLoaded(true);
            })
            .catch(error => console.error("Error loading my organization users:", error))
            .finally(() =>
            {
                if (active) setIsLoadingUsers(false);
            });
        return () =>
        {
            active = false;
        };
    }, [recipient, usersLoaded]);

    const updateRecipient = (field: keyof ExchangeNewMainRecipient, value: string) =>
    {
        const updated = {...recipient, [field]: value};
        setRecipient(updated);
        setNewRecipient(updated);
        if (!isExchangeNewMainRecipientValid(updated) && usersLoaded) setUsersLoaded(false);
    };

    return {
        appUser,
        appUserPersonOrganization,
        recipient,
        selectedInternalRecipients,
        setSelectedInternalParticipants,
        orgUsers,
        isLoadingUsers,
        isRecipientDataValid: isExchangeNewMainRecipientValid(recipient),
        updateRecipient,
    };
};
