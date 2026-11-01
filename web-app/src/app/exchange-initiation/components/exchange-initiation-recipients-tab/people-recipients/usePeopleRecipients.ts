import {useEffect, useMemo, useState} from "react";
import {AppUserPublicDto} from "../../../../models/models.tsx";
import {UserContactDto} from "../../../../../services/personalContactsApi";
import {ExchangeNewMainRecipient} from "../new-recipient/NewRecipient";
import {useAuth} from "../../../../../context/AuthContext.tsx";
import {PersonPickerItem} from "../../../../components/person-picker/personPickerTypes.ts";
import {
    EMAIL_PATTERN,
    formatDisplayName,
    normalizeEmail,
    SEND_AS_NEW_OPTION_VALUE,
} from "./peopleRecipientContacts.ts";
import {useRecentContacts} from "./useRecentContacts.ts";
import {useContactSearch} from "./useContactSearch.ts";
import {useMyOrganizationUsers} from "./useMyOrganizationUsers.ts";

interface UsePeopleRecipientsProps
{
    recipientOrgUser: AppUserPublicDto | undefined;
    setRecipientOrgUser: (user: AppUserPublicDto | undefined) => void;
    newRecipient: ExchangeNewMainRecipient | undefined;
    setNewRecipient: (recipient: ExchangeNewMainRecipient) => void;
    internalParticipants: AppUserPublicDto[] | undefined;
}

export const usePeopleRecipients = (
    {
        recipientOrgUser,
        setRecipientOrgUser,
        newRecipient,
        setNewRecipient,
        internalParticipants,
    }: UsePeopleRecipientsProps,
) =>
{
    const {appUser, appUserPersonOrganization} = useAuth();
    const {recents, isLoadingRecents} = useRecentContacts();
    const {query, setQuery, results, isSearching} = useContactSearch();
    const {orgUsers, isLoadingUsers} = useMyOrganizationUsers(!!appUserPersonOrganization);
    const [showEmailFallback, setShowEmailFallback] = useState<boolean>(false);
    const [selectedInternalRecipients, setSelectedInternalParticipants] = useState<AppUserPublicDto[]>([]);

    const selfEmail = normalizeEmail(appUser?.email);
    const isOwnEmail = (email?: string | null) =>
    {
        const normalized = normalizeEmail(email);
        return normalized.length > 0 && normalized === selfEmail;
    };

    const isSelfContact = (contact: UserContactDto) =>
        isOwnEmail(contact.email) || (!!appUser?.id && contact.contactAppUserId === appUser.id);

    const myOrgUserIds = useMemo(() => new Set(orgUsers.map(u => u.id)), [orgUsers]);
    const myOrgUserEmails = useMemo(() => new Set(orgUsers.map(u => normalizeEmail(u.email))), [orgUsers]);
    const isMyOrganizationContact = (contact: UserContactDto) =>
    {
        if (contact.contactAppUserId && myOrgUserIds.has(contact.contactAppUserId))
        {
            return true;
        }
        return myOrgUserEmails.has(normalizeEmail(contact.email));
    };

    const visibleRecents = recents.filter(c => !isSelfContact(c) && !isMyOrganizationContact(c));
    const visibleResults = results.filter(c => !isSelfContact(c) && !isMyOrganizationContact(c));

    useEffect(() =>
    {
        if (recipientOrgUser)
        {
            const firstName = recipientOrgUser.person?.firstName ?? '';
            const lastName = recipientOrgUser.person?.lastName ?? '';
            const display = `${firstName} ${lastName}`.trim();
            setQuery(display ? `${display} (${recipientOrgUser.email})` : recipientOrgUser.email);
            setShowEmailFallback(false);
            return;
        }

        const hasEmailRecipient = !!newRecipient?.email;
        if (hasEmailRecipient)
        {
            const display = `${newRecipient?.firstName ?? ''} ${newRecipient?.lastName ?? ''}`.trim();
            setQuery(display ? `${display} (${newRecipient?.email})` : (newRecipient?.email || ''));
            setShowEmailFallback(true);
        }
    }, [recipientOrgUser, newRecipient?.email, newRecipient?.firstName, newRecipient?.lastName, setQuery]);

    useEffect(() =>
    {
        if (!internalParticipants)
        {
            setSelectedInternalParticipants([]);
            return;
        }

        const filtered = internalParticipants
            .filter(user => user.id !== appUser?.id)
            .filter(user => user.id !== recipientOrgUser?.id);
        setSelectedInternalParticipants(filtered);
    }, [internalParticipants, appUser?.id, recipientOrgUser?.id]);

    const selectContact = (contact: UserContactDto) =>
    {
        if (isSelfContact(contact) || isMyOrganizationContact(contact))
        {
            return;
        }

        setShowEmailFallback(false);
        if (contact.contactAppUserId)
        {
            const fauxAppUser: AppUserPublicDto = {
                id: contact.contactAppUserId,
                email: contact.email,
                person: {
                    firstName: contact.firstName ?? '',
                    lastName: contact.lastName ?? '',
                },
                avatarUrl: contact.avatarUrl,
                isActive: true,
                appRoles: [],
                organizationRoles: [],
            };
            setRecipientOrgUser(fauxAppUser);
            setNewRecipient({email: '', firstName: '', lastName: ''});
            setQuery(formatDisplayName(contact));
        }
        else
        {
            setRecipientOrgUser(undefined);
            setNewRecipient({
                email: contact.email,
                firstName: contact.firstName ?? '',
                lastName: contact.lastName ?? '',
            });
            setQuery(formatDisplayName(contact));
        }
    };

    const startEmailFallback = (typedEmail: string) =>
    {
        if (isOwnEmail(typedEmail))
        {
            return;
        }
        setRecipientOrgUser(undefined);
        setNewRecipient({email: typedEmail, firstName: '', lastName: ''});
        setShowEmailFallback(true);
    };

    const onQueryChange = (value: string) =>
    {
        setQuery(value);
        if (value.length === 0)
        {
            setRecipientOrgUser(undefined);
            setShowEmailFallback(false);
        }
    };

    const onPersonSelect = (person: PersonPickerItem | null) =>
    {
        if (!person)
        {
            setRecipientOrgUser(undefined);
            setShowEmailFallback(false);
            return;
        }
        const match = visibleResults.find(result => result.email === person.id) ??
            visibleRecents.find(result => result.email === person.id);
        if (match)
        {
            selectContact(match);
        }
    };

    const onSpecialOptionSelect = (value: string): boolean =>
    {
        if (value !== SEND_AS_NEW_OPTION_VALUE) return false;
        startEmailFallback(query.trim().toLowerCase());
        return true;
    };

    const trimmedQuery = query.trim();
    const normalizedQuery = normalizeEmail(trimmedQuery);
    const hasExactEmailMatch = visibleResults.some(r => normalizeEmail(r.email) === normalizedQuery);
    const queryLooksLikeEmail = EMAIL_PATTERN.test(trimmedQuery);
    const isOwnEmailQuery = isOwnEmail(trimmedQuery);
    const showSendAsNewOption = !isSearching && queryLooksLikeEmail && !hasExactEmailMatch && !isOwnEmailQuery;

    return {
        appUser,
        appUserPersonOrganization,
        visibleRecents,
        isLoadingRecents,
        visibleResults,
        isSearching,
        orgUsers,
        isLoadingUsers,
        query,
        trimmedQuery,
        showEmailFallback,
        showSendAsNewOption,
        isOwnEmailQuery,
        selectedInternalRecipients,
        setSelectedInternalParticipants,
        selectContact,
        onQueryChange,
        onPersonSelect,
        onSpecialOptionSelect,
    };
};
