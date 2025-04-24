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
import {AppUserDetailedDto} from "../../../../models/models.tsx";
import {
    fetchMyOrganizationGroups,
    fetchMyOrganizationUsers,
    OrganizationGroupBasicDto
} from "../../../../../services/organizationApi";
import MyOrgRecipients from "../MyOrgRecipients.tsx";
import {useAuth} from "../../../../../context/AuthContext.tsx";

interface MyOrganizationRecipientsProps
{
    setRecipientOrgUser: (user: AppUserDetailedDto | undefined) => void;
    setRecipientOrgGroup: (group: OrganizationGroupBasicDto | undefined) => void;
    recipientOrgUser?: AppUserDetailedDto;
    recipientOrgGroup?: OrganizationGroupBasicDto;
    internalParticipants?: AppUserDetailedDto[];
    setInternalParticipants?: (users: AppUserDetailedDto[]) => void;
}

enum ShareWithMode
{
    INDIVIDUAL = "withIndividual",
    GROUP = "withOrgGroup"
}

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
    const {appUser} = useAuth()
    const [isLoadingUsers, setIsLoadingUsers] = useState<boolean>(false);
    const [isLoadingGroups, setIsLoadingGroups] = useState<boolean>(false);
    const [myOrgUsers, setMyOrgUsers] = useState<AppUserDetailedDto[]>([]);
    const [myOrgGroups, setMyOrgGroups] = useState<OrganizationGroupBasicDto[]>([]);
    const [selectedOrgUser, setSelectedOrgUser] = useState<AppUserDetailedDto | null>(null);
    const [selectedOrgGroup, setSelectedOrgGroup] = useState<OrganizationGroupBasicDto | null>(null);
    const [selectedInternalRecipients, setSelectedInternalParticipants] = useState<AppUserDetailedDto[]>([]);
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

    const formatUserDisplay = (user: AppUserDetailedDto | null | undefined) =>
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
            const groups = await fetchMyOrganizationGroups();
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
        .filter(user => !userSearchQuery ||
            user.email.toLowerCase().includes(userSearchQuery.toLowerCase()) ||
            (user.person?.firstName && user.person.firstName.toLowerCase().includes(userSearchQuery.toLowerCase())) ||
            (user.person?.lastName && user.person.lastName.toLowerCase().includes(userSearchQuery.toLowerCase())))
        .map(user => (
            <Option key={user.id}
                    text={`${user.person?.firstName || ''} ${user.person?.lastName || ''} (${user.email})`}
                    value={user.id || ''}>
                {`${user.person?.firstName || ''} ${user.person?.lastName || ''} (${user.email})`}
            </Option>
        ));

    const filteredGroups = myOrgGroups
        .filter(group => !groupSearchQuery || group.name.toLowerCase().includes(groupSearchQuery.toLowerCase()))
        .map(group => (
            <Option key={group.id}
                    text={group.name}
                    value={group.id || ''}>
                {`${group.name} (${group.memberCount} members)`}
            </Option>
        ));

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

    const setRecipientOrgUserOptionItem: ComboboxProps["onOptionSelect"] = (_, data: OptionOnSelectData) =>
    {
        if (!data || !data.optionValue)
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

        const userId = data.optionValue;
        const selectedUser = myOrgUsers.find(u => u.id === userId);

        if (selectedUser && selectedUser.id !== appUser?.id)
        {
            const firstName = selectedUser.person.firstName;
            const lastName = selectedUser.person.lastName;
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

        return usersToFilter;
    };

    return (
        <>
            <Field>
                <RadioGroup
                    layout={"horizontal"}
                    value={shareWith}
                    onChange={onShareWithChange}>
                    <Radio value={ShareWithMode.INDIVIDUAL} label="Individual"/>
                    <Radio value={ShareWithMode.GROUP} label="Group"/>
                </RadioGroup>
            </Field>

            {shareWith === ShareWithMode.INDIVIDUAL && (
                <Field>
                    {isLoadingUsers ? (
                        <Spinner size="tiny" label="Loading users..."/>
                    ) : (
                        <Combobox
                            onOptionSelect={setRecipientOrgUserOptionItem}
                            placeholder="Select Individual"
                            onChange={(ev) => setUserSearchQuery(ev.target.value)}
                            value={userSearchQuery}>
                            {filteredUsers}
                        </Combobox>
                    )}
                </Field>
            )}

            {shareWith === ShareWithMode.GROUP && (
                <Field>
                    {isLoadingGroups ? (
                        <Spinner size="tiny" label="Loading groups..."/>
                    ) : (
                        <Combobox
                            onOptionSelect={setRecipientOrgGroupOptionItem}
                            placeholder="Select Group/Team/Department"
                            onChange={(ev) => setGroupSearchQuery(ev.target.value)}
                            value={groupSearchQuery}>
                            {filteredGroups}
                        </Combobox>
                    )}
                </Field>
            )}

            {(selectedOrgUser || selectedOrgGroup) && (
                <MyOrgRecipients
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