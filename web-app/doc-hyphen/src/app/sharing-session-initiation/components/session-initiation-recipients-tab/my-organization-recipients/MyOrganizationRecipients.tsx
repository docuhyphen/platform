import React, {useEffect, useState} from 'react';
import {Combobox, Field, Option, Spinner} from "@fluentui/react-components";
import {AppUserBasicDto, OrganizationGroupBasicDto} from "../../../../models/models.tsx";
import {fetchMyOrganizationGroups, fetchMyOrganizationUsers} from "../../../../../services/organizationApi";

interface MyOrganizationRecipientsProps
{
    onSelectUser: (user: AppUserBasicDto | undefined) => void;
    onSelectGroup: (group: OrganizationGroupBasicDto | undefined) => void;
}

const MyOrganizationRecipients: React.FC<MyOrganizationRecipientsProps> = (
    {
        onSelectUser,
        onSelectGroup
    }) =>
{
    const [isLoadingUsers, setIsLoadingUsers] = useState<boolean>(false);
    const [isLoadingGroups, setIsLoadingGroups] = useState<boolean>(false);
    const [myOrgUsers, setMyOrgUsers] = useState<AppUserBasicDto[]>([]);
    const [myOrgGroups, setMyOrgGroups] = useState<OrganizationGroupBasicDto[]>([]);
    const [searchQuery, setSearchQuery] = useState<string>("");

    useEffect(() =>
    {
        loadMyOrganizationUsers();
        loadMyOrganizationGroups();
    }, []);

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

    const filteredItems = [
        ...myOrgUsers.map(user => ({
            id: user.id || "",
            text: `${user.firstName} ${user.lastName} (${user.email})`,
            value: user,
            type: "user"
        })),
        ...myOrgGroups.map(group => ({
            id: group.id || "",
            text: `${group.name} (Group)`,
            value: group,
            type: "group"
        }))
    ].filter(item =>
        !searchQuery || item.text.toLowerCase().includes(searchQuery.toLowerCase())
    );

    return (
        <Field>
            {isLoadingUsers || isLoadingGroups ? (
                <Spinner size="tiny" label="Loading..."/>
            ) : (
                <Combobox
                    placeholder="Find users or groups within your organization..."
                    onChange={(ev) => setSearchQuery(ev.target.value)}
                    value={searchQuery}
                    onOptionSelect={(_, data) =>
                    {
                        if (data.optionValue && typeof data.optionValue === 'object')
                        {
                            const selected = data.optionValue as { type: string; value: any };
                            if (selected.type === "user")
                            {
                                onSelectUser(selected.value);
                                onSelectGroup(undefined);
                            }
                            else
                            {
                                onSelectGroup(selected.value);
                                onSelectUser(undefined);
                            }
                        }
                    }}>
                    {filteredItems.map(item => (
                        <Option key={`${item.type}-${item.id}`} text={item.text} value={item}>
                            {item.text}
                        </Option>
                    ))}
                </Combobox>
            )}
        </Field>
    );
};

export default MyOrganizationRecipients;