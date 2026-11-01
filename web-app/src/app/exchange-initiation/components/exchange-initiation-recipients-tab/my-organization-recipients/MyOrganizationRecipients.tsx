import React from 'react';
import {Field, Radio, RadioGroup, Spinner} from "@fluentui/react-components";
import {AppUserPublicDto} from "../../../../models/models.tsx";
import {OrganizationGroupBasicDto} from "../../../../../services/organizationApi";
import MyOrgRecipients from "../MyOrgRecipients.tsx";
import SinglePersonPicker from "../../../../components/person-picker/single-person-picker/SinglePersonPicker.tsx";
import OrganizationGroupCombobox from "./organization-group-combobox/OrganizationGroupCombobox.tsx";
import {useMyOrganizationRecipients} from "./useMyOrganizationRecipients.ts";
import {ShareWithMode} from "./myOrganizationRecipientsUtils.ts";

interface MyOrganizationRecipientsProps
{
    setRecipientOrgUser: (user: AppUserPublicDto | undefined) => void;
    setRecipientOrgGroup: (group: OrganizationGroupBasicDto | undefined) => void;
    recipientOrgUser?: AppUserPublicDto;
    recipientOrgGroup?: OrganizationGroupBasicDto;
    internalParticipants?: AppUserPublicDto[];
    setInternalParticipants?: (users: AppUserPublicDto[]) => void;
    allowAdditionalParticipants: boolean;
}

const MyOrganizationRecipients: React.FC<MyOrganizationRecipientsProps> = (
    {
        recipientOrgUser,
        setRecipientOrgUser,
        recipientOrgGroup,
        setRecipientOrgGroup,
        internalParticipants,
        setInternalParticipants,
        allowAdditionalParticipants,
    }) =>
{
    const {
        isLoadingUsers,
        isLoadingGroups,
        myOrgGroups,
        filteredUsers,
        internalUsers,
        selectedOrgUser,
        selectedOrgGroup,
        selectedInternalRecipients,
        setSelectedInternalParticipants,
        shareWith,
        onShareWithChange,
        userSearchQuery,
        setUserSearchQuery,
        selectRecipientOrgUser,
        groupSearchQuery,
        setGroupSearchQuery,
        selectRecipientOrgGroup,
    } = useMyOrganizationRecipients({
        recipientOrgUser,
        setRecipientOrgUser,
        recipientOrgGroup,
        setRecipientOrgGroup,
        internalParticipants,
        setInternalParticipants,
    });

    return (
        <>
            <Field id={"my-organization-recipients-field-1"}>
                <RadioGroup
                    id={"my-org-share-with-group"}
                    layout={"horizontal"}
                    value={shareWith}
                    onChange={onShareWithChange}>
                    <Radio id={"my-organization-recipients-radio-1"}
                           value={ShareWithMode.INDIVIDUAL}
                           label="Person"/>
                    <Radio id={"my-organization-recipients-radio-2"}
                           value={ShareWithMode.GROUP}
                           label="Group"/>
                </RadioGroup>
            </Field>

            {shareWith === ShareWithMode.INDIVIDUAL && (
                <Field id={"my-organization-recipients-field-2"}>
                    {isLoadingUsers ? (
                        <Spinner id={"my-organization-recipients-spinner-1"}
                                 size="tiny"
                                 label="Loading users..."/>
                    ) : (
                        <SinglePersonPicker
                            id={"my-org-user-combobox"}
                            people={filteredUsers}
                            query={userSearchQuery}
                            onQueryChange={setUserSearchQuery}
                            onPersonSelect={selectRecipientOrgUser}
                            placeholder="Select or find person"
                            selectedPersonId={selectedOrgUser?.id}
                            noResultsText="No matching organization users found"
                        />
                    )}
                </Field>
            )}

            {shareWith === ShareWithMode.GROUP && (
                <Field id={"my-organization-recipients-field-3"}>
                    {isLoadingGroups ? (
                        <Spinner id={"my-organization-recipients-spinner-2"}
                                 size="tiny"
                                 label="Loading groups..."/>
                    ) : (
                        <OrganizationGroupCombobox
                            groups={myOrgGroups}
                            query={groupSearchQuery}
                            onQueryChange={setGroupSearchQuery}
                            onOptionSelect={selectRecipientOrgGroup}
                        />
                    )}
                </Field>
            )}

            {allowAdditionalParticipants && (selectedOrgUser || selectedOrgGroup) && (
                <MyOrgRecipients
                    id={"my-organization-recipients-internal-participants"}
                    orgUsers={internalUsers}
                    isLoadingUsers={isLoadingUsers}
                    selectedInternalRecipients={selectedInternalRecipients}
                    setSelectedInternalParticipants={setSelectedInternalParticipants}
                    setInternalParticipants={setInternalParticipants}
                />
            )}
        </>
    );
};

export default MyOrganizationRecipients;
