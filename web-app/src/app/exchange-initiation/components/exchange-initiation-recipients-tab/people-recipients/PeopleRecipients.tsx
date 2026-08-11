import React, {useEffect, useMemo, useRef, useState} from 'react';
import {
    Button,
    Field,
    InfoLabel,
    Option,
    Spinner,
    Text
} from "@fluentui/react-components";
import {AppUserPublicDto} from "../../../../models/models.tsx";
import {fetchRecentContacts, searchContacts, UserContactDto} from "../../../../../services/personalContactsApi";
import {ExchangeNewMainRecipient} from "../new-recipient/NewRecipient";
import NewRecipient from "../new-recipient/NewRecipient";
import {fetchMyOrganizationUsers} from "../../../../../services/organizationApi";
import MyOrgRecipients from "../MyOrgRecipients.tsx";
import {useAuth} from "../../../../../context/AuthContext.tsx";
import {useExchangeInitiationRecipientsTabStyles} from "../ExchangeInitiationRecipientsTabStyles.tsx";
import SinglePersonPicker from "../../../../components/person-picker/single-person-picker/SinglePersonPicker.tsx";
import PersonOption from "../../../../components/person-picker/person-option/PersonOption.tsx";
import {PersonPickerItem} from "../../../../components/person-picker/personPickerTypes.ts";

interface PeopleRecipientsProps
{
    isRequestingDocuments: boolean | null | undefined;
    recipientOrgUser: AppUserPublicDto | undefined;
    setRecipientOrgUser: (user: AppUserPublicDto | undefined) => void;
    newRecipient: ExchangeNewMainRecipient | undefined;
    setNewRecipient: (recipient: ExchangeNewMainRecipient) => void;
    internalParticipants: AppUserPublicDto[] | undefined;
    setInternalParticipants?: (users: AppUserPublicDto[]) => void;
    allowAdditionalParticipants: boolean;
}

const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
const SEARCH_DEBOUNCE_MS = 250;
const MIN_QUERY_LENGTH = 2;

const formatDisplayName = (c: UserContactDto): string =>
{
    const name = [c.firstName, c.lastName].filter(Boolean).join(' ').trim();
    return name.length > 0 ? `${name} (${c.email})` : c.email;
};

const normalizeEmail = (value?: string | null): string => (value ?? '').trim().toLowerCase();

const toPersonPickerItem = (contact: UserContactDto): PersonPickerItem => ({
    id: contact.email,
    email: contact.email,
    firstName: contact.firstName,
    lastName: contact.lastName,
    avatarUrl: contact.avatarUrl,
});

/**
 * Personal-contacts-based recipient picker. Surfaces:
 *   - Recent contacts as quick-pick chips (top).
 *   - Debounced typeahead against /me/contacts (middle).
 *   - "Send to {email} as a new recipient" fallback (bottom), renders the existing
 *     NewRecipient form so the initiator can supply first/last name for the no-auth
 *     email flow.
 *
 * Selection semantics:
 *   - Picking a contact with contactAppUserId != null selects the registered-user
 *     initiation contract.
 *   - Picking a contact with contactAppUserId == null (the other party hasn't signed up
 *     yet, but a prior exchange was accepted) OR a fresh email pre-fills newRecipient and
 *     leaves recipientOrgUser undefined, so the parent selects the external-email contract.
 */
const PeopleRecipients: React.FC<PeopleRecipientsProps> = (
    {
        isRequestingDocuments,
        recipientOrgUser,
        setRecipientOrgUser,
        newRecipient,
        setNewRecipient,
        internalParticipants,
        setInternalParticipants,
        allowAdditionalParticipants,
    }) =>
{
    const {appUser, appUserPersonOrganization} = useAuth();
    const styles = useExchangeInitiationRecipientsTabStyles();
    const [query, setQuery] = useState<string>('');
    const [recents, setRecents] = useState<UserContactDto[]>([]);
    const [results, setResults] = useState<UserContactDto[]>([]);
    const [isSearching, setIsSearching] = useState<boolean>(false);
    const [isLoadingRecents, setIsLoadingRecents] = useState<boolean>(false);
    const [showEmailFallback, setShowEmailFallback] = useState<boolean>(false);
    const [orgUsers, setOrgUsers] = useState<AppUserPublicDto[]>([]);
    const [isLoadingUsers, setIsLoadingUsers] = useState<boolean>(false);
    const [usersLoaded, setUsersLoaded] = useState<boolean>(false);
    const [selectedInternalRecipients, setSelectedInternalParticipants] = useState<AppUserPublicDto[]>([]);
    const debounceRef = useRef<ReturnType<typeof setTimeout> | null>(null);

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

    // Keep People->Recent focused on outside contacts only.
    const visibleRecents = recents.filter(c => !isSelfContact(c) && !isMyOrganizationContact(c));
    const visibleResults = results.filter(c => !isSelfContact(c) && !isMyOrganizationContact(c));

    useEffect(() =>
    {
        let cancelled = false;
        (async () =>
        {
            setIsLoadingRecents(true);
            try
            {
                const data = await fetchRecentContacts(6);
                if (!cancelled) setRecents(data ?? []);
            }
            catch (err)
            {
                console.error("Failed to load recent contacts:", err);
            }
            finally
            {
                if (!cancelled) setIsLoadingRecents(false);
            }
        })();
        return () => { cancelled = true; };
    }, []);

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
    }, [recipientOrgUser, newRecipient?.email, newRecipient?.firstName, newRecipient?.lastName]);

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

    useEffect(() =>
    {
        if (!appUserPersonOrganization || usersLoaded)
        {
            return;
        }

        let cancelled = false;
        (async () =>
        {
            setIsLoadingUsers(true);
            try
            {
                const users = await fetchMyOrganizationUsers();
                if (!cancelled)
                {
                    setOrgUsers(users ?? []);
                    setUsersLoaded(true);
                }
            }
            catch (error)
            {
                console.error("Error loading my organization users:", error);
            }
            finally
            {
                if (!cancelled)
                {
                    setIsLoadingUsers(false);
                }
            }
        })();

        return () =>
        {
            cancelled = true;
        };
    }, [appUserPersonOrganization, usersLoaded]);

    useEffect(() =>
    {
        if (debounceRef.current) clearTimeout(debounceRef.current);
        const trimmed = query.trim();
        if (trimmed.length < MIN_QUERY_LENGTH)
        {
            setResults([]);
            setIsSearching(false);
            return;
        }
        setIsSearching(true);
        debounceRef.current = setTimeout(async () =>
        {
            try
            {
                const data = await searchContacts(trimmed, 10);
                setResults(data ?? []);
            }
            catch (err)
            {
                console.error("Failed to search contacts:", err);
                setResults([]);
            }
            finally
            {
                setIsSearching(false);
            }
        }, SEARCH_DEBOUNCE_MS);

        return () =>
        {
            if (debounceRef.current) clearTimeout(debounceRef.current);
        };
    }, [query]);

    const selectContact = (contact: UserContactDto) =>
    {
        if (isSelfContact(contact) || isMyOrganizationContact(contact))
        {
            return;
        }

        setShowEmailFallback(false);
        if (contact.contactAppUserId)
        {
            // Existing real user, select the registered-user initiation contract.
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
            // Contact has no real account yet, use email path. Pre-fill names from the
            // contact entry so the initiator doesn't have to retype.
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
        if (value !== "__send_as_new__") return false;
        startEmailFallback(query.trim().toLowerCase());
        return true;
    };

    const trimmedQuery = query.trim();
    const normalizedQuery = normalizeEmail(trimmedQuery);
    const hasExactEmailMatch = visibleResults.some(r => normalizeEmail(r.email) === normalizedQuery);
    const queryLooksLikeEmail = EMAIL_PATTERN.test(trimmedQuery);
    const isOwnEmailQuery = isOwnEmail(trimmedQuery);

    const renderRecents = () =>
    {
        if (isLoadingRecents)
        {
            return <Spinner size="tiny" label="Loading recent contacts..."/>;
        }
        if (visibleRecents.length === 0)
        {
            return (
                <Text size={200} italic>
                    No recent external contacts yet. People will appear here once you've exchanged with them.
                    Users in your organization are shown under My Organization.
                </Text>
            );
        }
        return (
            <div className={styles.recentContactsRow}>
                {visibleRecents.map(c =>
                {
                    const isActive = recipientOrgUser?.email === c.email || newRecipient?.email === c.email;
                    return (
                        <Button
                            key={c.email}
                            id={`recent-contact-btn-${c.email}`}
                            size="small"
                            shape="circular"
                            className={isActive ? styles.activeRecentContact : undefined}
                            appearance={isActive ? 'primary' : 'outline'}
                            onClick={() => selectContact(c)}>
                            <PersonOption
                                size={"extra-small"}
                                id={`recent-contact-persona-${c.email}`}
                                person={toPersonPickerItem(c)}
                            />
                        </Button>
                    );
                })}
            </div>
        );
    };

    return (
        <>
            <Field label={<InfoLabel info="People you've previously shared with and who have accepted.">Recent</InfoLabel>}>
                {renderRecents()}
            </Field>

            <div>
                <Field label="Find a person or type an email">
                <SinglePersonPicker
                    id={"people-recipients-combobox"}
                    placeholder="Type a name or email"
                    people={visibleResults.map(toPersonPickerItem)}
                    query={query}
                    onQueryChange={onQueryChange}
                    onPersonSelect={onPersonSelect}
                    selectedPersonId={recipientOrgUser?.email ?? newRecipient?.email}
                    loading={isSearching}
                    freeform
                    noResultsText="No matching contacts found"
                    onSpecialOptionSelect={onSpecialOptionSelect}
                >
                    {!isSearching && queryLooksLikeEmail && !hasExactEmailMatch && !isOwnEmailQuery && (
                        <Option
                            id="people-recipients-send-new-option"
                            key="__send_as_new__"
                            text={`Send to ${trimmedQuery} as a new recipient`}
                            value="__send_as_new__"
                        >
                            Send to <strong>{trimmedQuery}</strong> as a new recipient
                        </Option>
                    )}
                </SinglePersonPicker>
                {isOwnEmailQuery && (
                    <Text size={200}>You cannot share with your own email address.</Text>
                )}
            </Field>
            </div>
            {showEmailFallback && (
                <NewRecipient
                    isRequestingDocuments={isRequestingDocuments}
                    setNewRecipient={setNewRecipient}
                    newRecipient={newRecipient}
                    internalParticipants={internalParticipants}
                    setInternalParticipants={setInternalParticipants}
                    allowAdditionalParticipants={allowAdditionalParticipants}
                />
            )}

            {allowAdditionalParticipants && recipientOrgUser && appUserPersonOrganization && (
                <MyOrgRecipients
                    id={"people-recipients-internal-participants"}
                    orgUsers={orgUsers.filter(u => u.id !== appUser?.id && u.id !== recipientOrgUser.id)}
                    isLoadingUsers={isLoadingUsers}
                    selectedInternalRecipients={selectedInternalRecipients}
                    setSelectedInternalParticipants={setSelectedInternalParticipants}
                    setInternalParticipants={setInternalParticipants}
                />
            )}
        </>
    );
};

export default PeopleRecipients;
