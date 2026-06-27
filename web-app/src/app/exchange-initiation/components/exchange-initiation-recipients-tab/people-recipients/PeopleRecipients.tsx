import React, {useEffect, useMemo, useRef, useState} from 'react';
import {
    Badge,
    Button,
    Combobox,
    ComboboxProps,
    Field,
    InfoLabel,
    Option,
    OptionOnSelectData,
    Spinner,
    Text
} from "@fluentui/react-components";
import {AppUserDetailedDto} from "../../../../models/models.tsx";
import {fetchRecentContacts, searchContacts, UserContactDto} from "../../../../../services/personalContactsApi";
import {ExchangeNewMainRecipient} from "../new-recipient/NewRecipient";
import NewRecipient from "../new-recipient/NewRecipient";
import {fetchMyOrganizationUsers} from "../../../../../services/organizationApi";
import MyOrgRecipients from "../MyOrgRecipients.tsx";
import {useAuth} from "../../../../../context/AuthContext.tsx";
import {useExchangeInitiationRecipientsTabStyles} from "../ExchangeInitiationRecipientsTabStyles.tsx";

interface PeopleRecipientsProps
{
    isRequestingDocuments: boolean | null | undefined;
    recipientOrgUser: AppUserDetailedDto | undefined;
    setRecipientOrgUser: (user: AppUserDetailedDto | undefined) => void;
    newRecipient: ExchangeNewMainRecipient | undefined;
    setNewRecipient: (recipient: ExchangeNewMainRecipient) => void;
    internalParticipants: AppUserDetailedDto[] | undefined;
    setInternalParticipants?: (users: AppUserDetailedDto[]) => void;
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

/**
 * Personal-contacts-based recipient picker. Surfaces:
 *   - Recent contacts as quick-pick chips (top).
 *   - Debounced typeahead against /me/contacts (middle).
 *   - "Send to {email} as a new recipient" fallback (bottom), renders the existing
 *     NewRecipient form so the initiator can supply first/last name for the no-auth
 *     email flow.
 *
 * Selection semantics:
 *   - Picking a contact with contactAppUserId != null sets recipientOrgUser, which the
 *     parent maps to recipientType=APP_USER on submit.
 *   - Picking a contact with contactAppUserId == null (the other party hasn't signed up
 *     yet, but a prior exchange was accepted) OR a fresh email pre-fills newRecipient and
 *     leaves recipientOrgUser undefined, so the parent maps to recipientType=EMAIL.
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
    const [orgUsers, setOrgUsers] = useState<AppUserDetailedDto[]>([]);
    const [isLoadingUsers, setIsLoadingUsers] = useState<boolean>(false);
    const [usersLoaded, setUsersLoaded] = useState<boolean>(false);
    const [selectedInternalRecipients, setSelectedInternalParticipants] = useState<AppUserDetailedDto[]>([]);
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
            // Existing real user, set recipientOrgUser (recipientType=APP_USER on submit).
            const fauxAppUser = {
                id: contact.contactAppUserId,
                email: contact.email,
                person: {
                    firstName: contact.firstName ?? '',
                    lastName: contact.lastName ?? '',
                },
            } as unknown as AppUserDetailedDto;
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

    const onComboboxChange = (ev: React.ChangeEvent<HTMLInputElement>) =>
    {
        const value = ev.target.value;
        setQuery(value);
        if (value.length === 0)
        {
            setRecipientOrgUser(undefined);
            setShowEmailFallback(false);
        }
    };

    const onComboboxSelect: ComboboxProps["onOptionSelect"] = (_, data: OptionOnSelectData) =>
    {
        if (!data.optionValue) return;
        if (data.optionValue === '__send_as_new__')
        {
            startEmailFallback(query.trim().toLowerCase());
            return;
        }
        const match = visibleResults.find(r => r.email === data.optionValue) ??
            visibleRecents.find(r => r.email === data.optionValue);
        if (match)
        {
            selectContact(match);
        }
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
                {visibleRecents.map(c => (
                    <Button
                        key={c.email}
                        id={`recent-contact-btn-${c.email}`}
                        size="small"
                        shape="circular"
                        appearance={recipientOrgUser?.email === c.email || newRecipient?.email === c.email ? 'primary' : 'outline'}
                        onClick={() => selectContact(c)}>
                        {[c.firstName, c.lastName].filter(Boolean).join(' ') || c.email}
                    </Button>
                ))}
            </div>
        );
    };

    return (
        <>
            <Field label={<InfoLabel info="People you've previously shared with and who have accepted.">Recent</InfoLabel>}>
                {renderRecents()}
            </Field>

            <Field label="Find a person or type an email">
                <Combobox
                    id={"people-recipients-combobox"}
                    placeholder="Type a name or email"
                    value={query}
                    onChange={onComboboxChange}
                    onOptionSelect={onComboboxSelect}
                    freeform>
                    {isSearching && (
                        <Option key="__loading__" text="" value="__loading__" disabled>
                            Searching...
                        </Option>
                    )}
                    {!isSearching && visibleResults.map(r => (
                        <Option key={r.email}
                                text={formatDisplayName(r)}
                                value={r.email}>
                            <span>
                                {formatDisplayName(r)}
                                {r.shareCount > 1 &&
                                    <Badge size="small" appearance="tint" className={styles.shareCountBadge}>
                                        {r.shareCount} shares
                                    </Badge>}
                            </span>
                        </Option>
                    ))}
                    {!isSearching && queryLooksLikeEmail && !hasExactEmailMatch && !isOwnEmailQuery && (
                        <Option key="__send_as_new__"
                                text={`Send to ${trimmedQuery} as a new recipient`}
                                value="__send_as_new__">
                            Send to <strong>{trimmedQuery}</strong> as a new recipient
                        </Option>
                    )}
                </Combobox>
                {isOwnEmailQuery && (
                    <Text size={200}>You cannot share with your own email address.</Text>
                )}
            </Field>

            {showEmailFallback && (
                <NewRecipient
                    isRequestingDocuments={isRequestingDocuments}
                    setNewRecipient={setNewRecipient}
                    newRecipient={newRecipient}
                    internalParticipants={internalParticipants}
                    setInternalParticipants={setInternalParticipants}
                />
            )}

            {recipientOrgUser && appUserPersonOrganization && (
                <MyOrgRecipients
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
