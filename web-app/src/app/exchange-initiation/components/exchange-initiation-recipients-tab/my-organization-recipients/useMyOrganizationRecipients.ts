import {FormEvent, useEffect, useState} from "react";
import {ComboboxProps, OptionOnSelectData} from "@fluentui/react-components";
import {AppUserPublicDto} from "../../../../models/models.tsx";
import {OrganizationGroupBasicDto} from "../../../../../services/organizationApi";
import {useAuth} from "../../../../../context/AuthContext.tsx";
import {matchesPersonQuery, PersonPickerItem} from "../../../../components/person-picker/personPickerTypes.ts";
import {useMyOrganizationDirectory} from "./useMyOrganizationDirectory.ts";
import {formatUserDisplay, groupMemberIdsOf, ShareWithMode, toPersonPickerItem} from "./myOrganizationRecipientsUtils.ts";

interface UseMyOrganizationRecipientsProps
{
    setRecipientOrgUser: (user: AppUserPublicDto | undefined) => void;
    setRecipientOrgGroup: (group: OrganizationGroupBasicDto | undefined) => void;
    recipientOrgUser?: AppUserPublicDto;
    recipientOrgGroup?: OrganizationGroupBasicDto;
    internalParticipants?: AppUserPublicDto[];
    setInternalParticipants?: (users: AppUserPublicDto[]) => void;
}

export const useMyOrganizationRecipients = (
    {
        recipientOrgUser,
        setRecipientOrgUser,
        recipientOrgGroup,
        setRecipientOrgGroup,
        internalParticipants,
        setInternalParticipants,
    }: UseMyOrganizationRecipientsProps,
) =>
{
    const {appUser} = useAuth();
    const {isLoadingUsers, isLoadingGroups, myOrgUsers, myOrgGroups} = useMyOrganizationDirectory();
    const [selectedOrgUser, setSelectedOrgUser] = useState<AppUserPublicDto | null>(null);
    const [selectedOrgGroup, setSelectedOrgGroup] = useState<OrganizationGroupBasicDto | null>(null);
    const [selectedInternalRecipients, setSelectedInternalParticipants] = useState<AppUserPublicDto[]>([]);
    const [shareWith, setShareWith] = useState<ShareWithMode>(ShareWithMode.INDIVIDUAL);
    const [userSearchQuery, setUserSearchQuery] = useState<string>("");
    const [groupSearchQuery, setGroupSearchQuery] = useState<string>("");

    useEffect(() =>
    {
        if (internalParticipants && internalParticipants.length > 0)
        {
            let filteredParticipants = [...internalParticipants];

            if (appUser)
            {
                filteredParticipants = filteredParticipants.filter(user => user.id !== appUser.id);
            }

            if (recipientOrgUser)
            {
                filteredParticipants = filteredParticipants.filter(user => user.id !== recipientOrgUser.id);
            }

            if (filteredParticipants.length !== selectedInternalRecipients.length)
            {
                setSelectedInternalParticipants(filteredParticipants);
                if (setInternalParticipants)
                {
                    setInternalParticipants(filteredParticipants);
                }
            }
        }
    }, [
        internalParticipants,
        appUser,
        recipientOrgUser,
        recipientOrgGroup,
        selectedInternalRecipients.length,
        setInternalParticipants,
    ]);

    useEffect(() =>
    {
        if (recipientOrgUser)
        {
            setSelectedOrgUser(recipientOrgUser);
            setShareWith(ShareWithMode.INDIVIDUAL);
            setUserSearchQuery(formatUserDisplay(recipientOrgUser));
        }

        if (recipientOrgGroup)
        {
            setSelectedOrgGroup(recipientOrgGroup);
            setShareWith(ShareWithMode.GROUP);
            setGroupSearchQuery(recipientOrgGroup.name);
        }

        if (internalParticipants && internalParticipants.length > 0)
        {
            let filteredParticipants = internalParticipants.filter(user => user.id !== appUser?.id);

            if (recipientOrgUser)
            {
                filteredParticipants = filteredParticipants.filter(user => user.id !== recipientOrgUser.id);
            }

            setSelectedInternalParticipants(filteredParticipants);
        }
    }, [appUser?.id, internalParticipants, recipientOrgGroup, recipientOrgUser]);

    const filteredUsers = myOrgUsers
        .filter(user => user.id !== appUser?.id)
        .map(toPersonPickerItem)
        .filter(person => person.id && matchesPersonQuery(person, userSearchQuery));

    const onShareWithChange = (_: FormEvent<HTMLDivElement>, data: { value: string }) =>
    {
        const newMode = data.value as ShareWithMode;
        setShareWith(newMode);

        if (newMode === ShareWithMode.INDIVIDUAL)
        {
            setSelectedOrgGroup(null);
            setGroupSearchQuery("");
            setRecipientOrgGroup(undefined);
        }
        else
        {
            setSelectedOrgUser(null);
            setUserSearchQuery("");
            setRecipientOrgUser(undefined);
        }
    };

    const selectRecipientOrgUser = (person: PersonPickerItem | null) =>
    {
        if (!person)
        {
            setUserSearchQuery("");
            setSelectedOrgUser(null);
            setRecipientOrgUser(undefined);

            if (internalParticipants && setInternalParticipants)
            {
                const updatedParticipants = internalParticipants.filter(user => user.id !== appUser?.id);
                setSelectedInternalParticipants(updatedParticipants);
                setInternalParticipants(updatedParticipants);
            }
            return;
        }

        const selectedUser = myOrgUsers.find(user => user.id === person.id);

        if (selectedUser && selectedUser.id !== appUser?.id)
        {
            setUserSearchQuery(formatUserDisplay(selectedUser));
            setSelectedOrgUser(selectedUser);
            setRecipientOrgUser(selectedUser);

            if (internalParticipants && setInternalParticipants)
            {
                const updatedParticipants = internalParticipants.filter(
                    user => user.id !== appUser?.id && user.id !== selectedUser.id
                );
                setSelectedInternalParticipants(updatedParticipants);
                setInternalParticipants(updatedParticipants);
            }
        }
    };

    const selectRecipientOrgGroup: ComboboxProps["onOptionSelect"] = (_, data: OptionOnSelectData) =>
    {
        if (!data || !data.optionValue)
        {
            setGroupSearchQuery("");
            setSelectedOrgGroup(null);
            setRecipientOrgGroup(undefined);
            return;
        }

        const groupId = data.optionValue;
        const group = myOrgGroups.find(g => g.id === groupId);

        if (group)
        {
            setGroupSearchQuery(group.name);
            setSelectedOrgGroup(group);
            setRecipientOrgGroup(group);

            const groupMemberIds = groupMemberIdsOf(group);
            if (groupMemberIds.size > 0 && setInternalParticipants)
            {
                const pruned = selectedInternalRecipients.filter(u => !groupMemberIds.has(u.id));
                if (pruned.length !== selectedInternalRecipients.length)
                {
                    setSelectedInternalParticipants(pruned);
                    setInternalParticipants(pruned);
                }
            }
        }
    };

    const internalUsers = (() =>
    {
        let usersToFilter = myOrgUsers.filter(user => user.id !== appUser?.id);

        if (selectedOrgUser)
        {
            usersToFilter = usersToFilter.filter(user => user.id !== selectedOrgUser.id);
        }

        if (selectedOrgGroup)
        {
            const groupMemberIds = groupMemberIdsOf(selectedOrgGroup);
            usersToFilter = usersToFilter.filter(user => !groupMemberIds.has(user.id));
        }

        return usersToFilter;
    })();

    return {
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
    };
};
