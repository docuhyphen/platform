import React from 'react';
import {Field, InfoLabel} from "@fluentui/react-components";
import {AppUserPublicDto} from "../../../../models/models.tsx";
import {ExchangeNewMainRecipient} from "../new-recipient/NewRecipient";
import NewRecipient from "../new-recipient/NewRecipient";
import MyOrgRecipients from "../MyOrgRecipients.tsx";
import RecentContacts from "./recent-contacts/RecentContacts.tsx";
import PeopleRecipientSearch from "./people-recipient-search/PeopleRecipientSearch.tsx";
import {usePeopleRecipients} from "./usePeopleRecipients.ts";

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
    const {
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
    } = usePeopleRecipients({
        recipientOrgUser,
        setRecipientOrgUser,
        newRecipient,
        setNewRecipient,
        internalParticipants,
    });

    return (
        <>
            <Field id={"people-recipients-field-1"}
                   label={<InfoLabel id={"people-recipients-info-label-1"}
                                     info="People you've previously shared with and who have accepted.">Recent</InfoLabel>}>
                <RecentContacts
                    isLoading={isLoadingRecents}
                    contacts={visibleRecents}
                    recipientEmail={recipientOrgUser?.email}
                    newRecipientEmail={newRecipient?.email}
                    onSelect={selectContact}
                />
            </Field>

            <PeopleRecipientSearch
                results={visibleResults}
                query={query}
                trimmedQuery={trimmedQuery}
                isSearching={isSearching}
                selectedPersonId={recipientOrgUser?.email ?? newRecipient?.email}
                showSendAsNewOption={showSendAsNewOption}
                isOwnEmailQuery={isOwnEmailQuery}
                onQueryChange={onQueryChange}
                onPersonSelect={onPersonSelect}
                onSpecialOptionSelect={onSpecialOptionSelect}
            />
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
