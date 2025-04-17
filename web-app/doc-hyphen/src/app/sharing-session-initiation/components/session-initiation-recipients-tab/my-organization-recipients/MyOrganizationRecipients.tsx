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

interface MyOrganizationRecipientsProps {
    setRecipientOrgUser: (user: AppUserDetailedDto | undefined) => void;
    setRecipientOrgGroup: (group: OrganizationGroupBasicDto | undefined) => void;
    recipientOrgUser?: AppUserDetailedDto;
    recipientOrgGroup?: OrganizationGroupBasicDto;
    internalRecipients?: AppUserDetailedDto[];
    setInternalRecipients?: (users: AppUserDetailedDto[]) => void;
}

enum ShareWithMode {
    INDIVIDUAL = "withIndividual",
    GROUP = "withOrgGroup"
}

const MyOrganizationRecipients: React.FC<MyOrganizationRecipientsProps> = ({
    setRecipientOrgUser,
    setRecipientOrgGroup,
    recipientOrgUser,
    recipientOrgGroup,
    internalRecipients = [],
    setInternalRecipients = () => {}
}) => {
    const [isLoadingUsers, setIsLoadingUsers] = useState<boolean>(false);
    const [isLoadingGroups, setIsLoadingGroups] = useState<boolean>(false);
    const [myOrgUsers, setMyOrgUsers] = useState<AppUserDetailedDto[]>([]);
    const [myOrgGroups, setMyOrgGroups] = useState<OrganizationGroupBasicDto[]>([]);

    // Determine initial mode based on props
    const initialShareWith = recipientOrgUser
        ? ShareWithMode.INDIVIDUAL
        : recipientOrgGroup ? ShareWithMode.GROUP : ShareWithMode.INDIVIDUAL;

    const [shareWith, setShareWith] = useState<ShareWithMode>(initialShareWith);
    const [selectedOrgUser, setSelectedOrgUser] = useState<AppUserDetailedDto | null>(recipientOrgUser || null);
    const [selectedOrgGroup, setSelectedOrgGroup] = useState<OrganizationGroupBasicDto | null>(recipientOrgGroup || null);

    // Format initial search strings safely
    const formatUserDisplay = (user: AppUserDetailedDto | null | undefined) => {
        if (!user) return "";
        const firstName = user.person?.firstName || '';
        const lastName = user.person?.lastName || '';
        return `${firstName} ${lastName} (${user.email})`;
    };

    const [userSearchQuery, setUserSearchQuery] = useState<string>(formatUserDisplay(recipientOrgUser));
    const [groupSearchQuery, setGroupSearchQuery] = useState<string>(recipientOrgGroup?.name || "");

    // Load data on initial mount
    useEffect(() => {
        loadMyOrganizationUsers();
        loadMyOrganizationGroups();
    }, []);

    // Update local state when props change
    useEffect(() => {
        if (recipientOrgUser) {
            setSelectedOrgUser(recipientOrgUser);
            setShareWith(ShareWithMode.INDIVIDUAL);
            setUserSearchQuery(formatUserDisplay(recipientOrgUser));

            // Clear group selection when user is selected
            setSelectedOrgGroup(null);
            setGroupSearchQuery("");
        } else if (recipientOrgGroup) {
            setSelectedOrgGroup(recipientOrgGroup);
            setShareWith(ShareWithMode.GROUP);
            setGroupSearchQuery(recipientOrgGroup.name);

            // Clear user selection when group is selected
            setSelectedOrgUser(null);
            setUserSearchQuery("");
        }
    }, [recipientOrgUser, recipientOrgGroup]);

    const loadMyOrganizationUsers = async () => {
        setIsLoadingUsers(true);
        try {
            const users = await fetchMyOrganizationUsers();
            setMyOrgUsers(users);
        } catch (error) {
            console.error("Error loading my organization users:", error);
        } finally {
            setIsLoadingUsers(false);
        }
    };

    const loadMyOrganizationGroups = async () => {
        setIsLoadingGroups(true);
        try {
            const groups = await fetchMyOrganizationGroups();
            setMyOrgGroups(groups);
        } catch (error) {
            console.error("Error loading my organization groups:", error);
        } finally {
            setIsLoadingGroups(false);
        }
    };

    const filteredUsers = myOrgUsers
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

    const onShareWithChange = (_: React.FormEvent<HTMLDivElement>, data: { value: string }) => {
        const newMode = data.value as ShareWithMode;
        setShareWith(newMode);

        // Clear selections based on mode change
        if (newMode === ShareWithMode.INDIVIDUAL) {
            setSelectedOrgGroup(null);
            setGroupSearchQuery("");
            setRecipientOrgGroup(undefined);
        } else {
            setSelectedOrgUser(null);
            setUserSearchQuery("");
            setRecipientOrgUser(undefined);
        }
    };

    const setRecipientOrgUserOptionItem: ComboboxProps["onOptionSelect"] = (_, data: OptionOnSelectData) => {
        if (!data || !data.optionValue) {
            setUserSearchQuery("");
            setSelectedOrgUser(null);
            setRecipientOrgUser(undefined);
            return;
        }

        const userId = data.optionValue;
        const user = myOrgUsers.find(u => u.id === userId);

        if (user) {
            setUserSearchQuery(formatUserDisplay(user));
            setSelectedOrgUser(user);
            setRecipientOrgUser(user);
            setRecipientOrgGroup(undefined); // Clear group when user is selected
        }
    };

    const setRecipientOrgGroupOptionItem: ComboboxProps["onOptionSelect"] = (_, data: OptionOnSelectData) => {
        if (!data || !data.optionValue) {
            setGroupSearchQuery("");
            setSelectedOrgGroup(null);
            setRecipientOrgGroup(undefined);
            return;
        }

        const groupId = data.optionValue;
        const group = myOrgGroups.find(g => g.id === groupId);

        if (group) {
            setGroupSearchQuery(group.name);
            setSelectedOrgGroup(group);
            setRecipientOrgGroup(group);
            setRecipientOrgUser(undefined); // Clear user when group is selected
        }
    };

    const getFilteredInternalUsers = () => {
        if (selectedOrgUser) {
            // Exclude the selected user from internal recipients
            return myOrgUsers.filter(user => user.id !== selectedOrgUser.id);
        }
        return myOrgUsers;
    };

    // Handler for internal recipients changes
    const handleInternalRecipientsChange = (recipients: AppUserDetailedDto[]) => {
        if (setInternalRecipients) {
            setInternalRecipients(recipients);
        }
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

            {/* Show internal recipients section only when a user or group is selected */}
            {(selectedOrgUser || selectedOrgGroup) && (
                <MyOrgRecipients
                    orgUsers={getFilteredInternalUsers()}
                    isLoadingUsers={isLoadingUsers}
                    selectedInternalRecipients={internalRecipients}
                    setSelectedInternalRecipients={handleInternalRecipientsChange}
                />
            )}
        </>
    );
};

export default MyOrganizationRecipients;