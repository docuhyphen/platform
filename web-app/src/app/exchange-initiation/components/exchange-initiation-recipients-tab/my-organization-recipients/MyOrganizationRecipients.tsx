import React, {useEffect, useState} from 'react';
import {
    Combobox,
    ComboboxProps,
    Field,
    Option,
    OptionOnSelectData,
    Radio,
    RadioGroup,
    Spinner
} from "@fluentui/react-components";
import {AppUserPublicDto} from "../../../../models/models.tsx";
import {
    fetchMyOrganizationGroups,
    fetchMyOrganizationUsers,
    OrganizationGroupBasicDto
} from "../../../../../services/organizationApi";
import MyOrgRecipients from "../MyOrgRecipients.tsx";
import {useAuth} from "../../../../../context/AuthContext.tsx";
import SinglePersonPicker from "../../../../components/person-picker/single-person-picker/SinglePersonPicker.tsx";
import {useMyOrganizationRecipientsStyles} from "./MyOrganizationRecipientsStyles.tsx";
import {
    matchesPersonQuery,
    PersonPickerItem,
} from "../../../../components/person-picker/personPickerTypes.ts";

const MAX_VISIBLE_GROUPS = 10;

interface OrganizationGroupMember
{
    user?: {id?: string};
}

type SelectableOrganizationGroup = Omit<OrganizationGroupBasicDto, "members"> & {
    members?: OrganizationGroupMember[];
};

interface MyOrganizationRecipientsProps
{
    setRecipientOrgUser: (user: AppUserPublicDto | undefined) => void;
    setRecipientOrgGroup: (group: OrganizationGroupBasicDto | undefined) => void;
    recipientOrgUser?: AppUserPublicDto;
    recipientOrgGroup?: OrganizationGroupBasicDto;
    internalParticipants?: AppUserPublicDto[];
    setInternalParticipants?: (users: AppUserPublicDto[]) => void;
}

enum ShareWithMode
{
    INDIVIDUAL = "withIndividual",
    GROUP = "withOrgGroup"
}

const toPersonPickerItem = (user: AppUserPublicDto): PersonPickerItem => ({
    id: user.id,
    email: user.email,
    firstName: user.person?.firstName,
    lastName: user.person?.lastName,
    avatarUrl: user.avatarUrl,
});

const MyOrganizationRecipients: React.FC<MyOrganizationRecipientsProps> = (
    {
        recipientOrgUser,
        setRecipientOrgUser,
        recipientOrgGroup,
        setRecipientOrgGroup,
        internalParticipants,
        setInternalParticipants
    }) =>
{
    const styles = useMyOrganizationRecipientsStyles();
    const {appUser} = useAuth()
    const [isLoadingUsers, setIsLoadingUsers] = useState<boolean>(false);
    const [isLoadingGroups, setIsLoadingGroups] = useState<boolean>(false);
    const [myOrgUsers, setMyOrgUsers] = useState<AppUserPublicDto[]>([]);
    const [myOrgGroups, setMyOrgGroups] = useState<SelectableOrganizationGroup[]>([]);
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
    }, [internalParticipants, appUser, recipientOrgUser, recipientOrgGroup]);

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
    }, []);

    useEffect(() =>
    {
        loadMyOrganizationUsers();
        loadMyOrganizationGroups();
    }, []);

    const formatUserDisplay = (user: AppUserPublicDto | null | undefined) =>
    {
        if (!user) return "";
        const firstName = user.person?.firstName || '';
        const lastName = user.person?.lastName || '';
        return `${firstName} ${lastName} (${user.email})`;
    };

    const loadMyOrganizationUsers = async () =>
    {
        setIsLoadingUsers(true);
        try
        {
            const users = await fetchMyOrganizationUsers();
            setMyOrgUsers(users);
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

    const loadMyOrganizationGroups = async () =>
    {
        setIsLoadingGroups(true);
        try
        {
            const groups = await fetchMyOrganizationGroups() as SelectableOrganizationGroup[];
            setMyOrgGroups(groups);
        }
        catch (error)
        {
            console.error("Error loading my organization groups:", error);
        }
        finally
        {
            setIsLoadingGroups(false);
        }
    };

    const filteredUsers = myOrgUsers
        .filter(user => user.id !== appUser?.id)
        .map(toPersonPickerItem)
        .filter(person => person.id && matchesPersonQuery(person, userSearchQuery));

    const normalizedGroupQuery = groupSearchQuery.trim().toLocaleLowerCase();
    const matchingGroups = myOrgGroups
        .filter(group => !normalizedGroupQuery || group.name.toLocaleLowerCase().includes(normalizedGroupQuery))
        .sort((left, right) => left.name.localeCompare(right.name, undefined, {sensitivity: "base"}));
    const hiddenGroupCount = Math.max(0, matchingGroups.length - MAX_VISIBLE_GROUPS);
    const filteredGroups = matchingGroups
        .slice(0, MAX_VISIBLE_GROUPS)
        .map(group =>
        {
            const orgGroupCount = group.members?.length ?? 0
            const orgGroupCountText = orgGroupCount > 1 ? "s" : ""

            return (
                <Option key={group.id}
                        text={group.name}
                        value={group.id || ''}>
                    {`${group.name} (${orgGroupCount} member${orgGroupCountText})`}
                </Option>
            )
        });

    const onShareWithChange = (_: React.FormEvent<HTMLDivElement>, data: { value: string }) =>
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
            const firstName = selectedUser.person?.firstName ?? '';
            const lastName = selectedUser.person?.lastName ?? '';
            const email = selectedUser.email;

            const query = `${firstName} ${lastName} (${email})`;
            setUserSearchQuery(query);
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

    const setRecipientOrgGroupOptionItem: ComboboxProps["onOptionSelect"] = (_, data: OptionOnSelectData) =>
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

            // Remove any already-selected participants who are members of this group
            // to prevent a user from appearing as both a group recipient and a participant.
            const groupMemberIds = new Set(
                (group.members || []).map(member => member.user?.id).filter(Boolean)
            );
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

    const getFilteredInternalUsers = () =>
    {
        let usersToFilter = [...myOrgUsers];

        usersToFilter = usersToFilter.filter(user => user.id !== appUser?.id);

        if (selectedOrgUser)
        {
            usersToFilter = usersToFilter.filter(user => user.id !== selectedOrgUser.id);
        }

        // Exclude users who are already members of the selected group -
        // they're covered by the group recipient and should not be addable as participants.
        if (selectedOrgGroup)
        {
            const groupMemberIds = new Set(
                (selectedOrgGroup.members || [])
                    .map((member: OrganizationGroupMember) => member.user?.id)
                    .filter(Boolean)
            );
            usersToFilter = usersToFilter.filter(user => !groupMemberIds.has(user.id));
        }

        return usersToFilter;
    };

    return (
        <>
            <Field>
                <RadioGroup
                    id={"my-org-share-with-group"}
                    layout={"horizontal"}
                    value={shareWith}
                    onChange={onShareWithChange}>
                    <Radio value={ShareWithMode.INDIVIDUAL} label="Person"/>
                    <Radio value={ShareWithMode.GROUP} label="Group"/>
                </RadioGroup>
            </Field>

            {shareWith === ShareWithMode.INDIVIDUAL && (
                <Field>
                    {isLoadingUsers ? (
                        <Spinner size="tiny" label="Loading users..."/>
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
                <Field>
                    {isLoadingGroups ? (
                        <Spinner size="tiny" label="Loading groups..."/>
                    ) : (
                        <Combobox
                            id={"my-org-group-combobox"}
                            listbox={{className: styles.groupListbox}}
                            onOptionSelect={setRecipientOrgGroupOptionItem}
                            placeholder="Select Group/Team/Department"
                            onChange={(ev) => setGroupSearchQuery(ev.target.value)}
                            value={groupSearchQuery}>
                            {filteredGroups}
                            {hiddenGroupCount > 0 && (
                                <Option
                                    disabled
                                    text={`${hiddenGroupCount} more groups. Type to narrow the list.`}
                                >
                                    {hiddenGroupCount} more groups. Type to narrow the list.
                                </Option>
                            )}
                        </Combobox>
                    )}
                </Field>
            )}

            {(selectedOrgUser || selectedOrgGroup) && (
                <MyOrgRecipients
                    id={"my-organization-recipients-internal-participants"}
                    orgUsers={getFilteredInternalUsers()}
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
