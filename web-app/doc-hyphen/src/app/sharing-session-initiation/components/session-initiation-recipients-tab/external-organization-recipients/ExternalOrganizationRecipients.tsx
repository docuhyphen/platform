import React, {useEffect, useState} from 'react';
import {
    Combobox,
    ComboboxProps,
    Field,
    InfoLabel,
    Option,
    OptionOnSelectData,
    Radio,
    RadioGroup,
    Spinner
} from "@fluentui/react-components";
import {AppUserDetailedDto, OrganizationBasicDto} from "../../../../models/models.tsx";
import {
    fetchOrganizationGroups,
    fetchOrganizationUsers,
    fetchPairedOrganizations,
    OrganizationGroupBasicDto
} from "../../../../../services/organizationApi";
import MyOrgRecipients from "../MyOrgRecipients.tsx";

interface ExternalOrganizationRecipientsProps
{
    recipientOrg: OrganizationBasicDto | undefined;
    recipientOrgUser: AppUserDetailedDto | undefined;
    recipientOrgGroup: OrganizationGroupBasicDto | undefined;
    internalRecipients: AppUserDetailedDto[] | undefined;
    currentUser: AppUserDetailedDto | undefined;

    setRecipientOrg: (org: OrganizationBasicDto | undefined) => void;
    setRecipientOrgUser: (user: AppUserDetailedDto | undefined) => void;
    setRecipientOrgGroup: (group: OrganizationGroupBasicDto | undefined) => void;
    setInternalRecipients?: (users: AppUserDetailedDto[]) => void;
}

enum ShareWithMode
{
    INDIVIDUAL = "withIndividual",
    GROUP = "withOrgGroup"
}

const ExternalOrganizationRecipients: React.FC<ExternalOrganizationRecipientsProps> = (
    {
        recipientOrg,
        recipientOrgUser,
        recipientOrgGroup,
        internalRecipients,
        currentUser,
        setRecipientOrg,
        setRecipientOrgUser,
        setRecipientOrgGroup,
        setInternalRecipients
    }) =>
{
    const [isLoadingOrgs, setIsLoadingOrgs] = useState<boolean>(false);
    const [isLoadingUsers, setIsLoadingUsers] = useState<boolean>(false);
    const [isLoadingGroups, setIsLoadingGroups] = useState<boolean>(false);
    const [selectedOrg, setSelectedOrg] = useState<OrganizationBasicDto | null>(null);
    const [selectedOrgGroup, setSelectedOrgGroup] = useState<OrganizationGroupBasicDto | null>(null);
    const [selectedOrgUser, setSelectedOrgUser] = useState<AppUserDetailedDto | null>(null);
    const [selectedInternalRecipients, setSelectedInternalRecipients] = useState<AppUserDetailedDto[]>([]);
    const [shareWith, setShareWith] = useState<ShareWithMode>(ShareWithMode.INDIVIDUAL);
    const [orgSearchQuery, setOrgSearchQuery] = useState<string>("");
    const [orgIndividualSearchQuery, setOrgIndividualSearchQuery] = useState<string>("");
    const [orgGroupSearchQuery, setOrgGroupSearchQuery] = useState<string>("");
    const [pairedOrgs, setPairedOrgs] = useState<OrganizationBasicDto[]>([]);
    const [orgUsers, setOrgUsers] = useState<AppUserDetailedDto[]>([]);
    const [orgGroups, setOrgGroups] = useState<OrganizationGroupBasicDto[]>([]);

    const filteredPairedOrgs = pairedOrgs
        .filter(org => !orgSearchQuery || org.name.toLowerCase().includes(orgSearchQuery.toLowerCase()))
        .map(org => (
            <Option key={org.id}
                    text={org.name}
                    value={org.id}>
                {org.name}
            </Option>
        ));

    const filteredOrgIndividuals = orgUsers
        .filter(orgUser => orgUser.id !== currentUser?.id)
        .filter(orgUser => !orgIndividualSearchQuery ||
            (orgUser.email.toLowerCase().includes(orgIndividualSearchQuery.toLowerCase()) ||
                orgUser.person.firstName?.toLowerCase().includes(orgIndividualSearchQuery.toLowerCase()) ||
                orgUser.person.lastName?.toLowerCase().includes(orgIndividualSearchQuery.toLowerCase())))
        .map(orgUser => (
            <Option key={orgUser.id}
                    text={orgUser.email}
                    value={orgUser.id}>
                {`${orgUser.person.firstName} ${orgUser.person.lastName} (${orgUser.email})`}
            </Option>
        ));

    const filteredOrgGroups = orgGroups
        .filter(group => !orgGroupSearchQuery || group.name.toLowerCase().includes(orgGroupSearchQuery.toLowerCase()))
        .map(group => (
            <Option key={group.id} text={group.name}
                    value={group.id}>
                {group.name}
            </Option>
        ));

    // Filter internal recipients based on current selections
    useEffect(() =>
    {
        if (internalRecipients && internalRecipients.length > 0)
        {
            let filteredRecipients = [...internalRecipients];

            // Filter out current user
            if (currentUser)
            {
                filteredRecipients = filteredRecipients.filter(user => user.id !== currentUser.id);
            }

            // Filter out selected individual recipient
            if (recipientOrgUser)
            {
                filteredRecipients = filteredRecipients.filter(user => user.id !== recipientOrgUser.id);
            }

            // Update internal recipients if they changed
            if (filteredRecipients.length !== selectedInternalRecipients.length)
            {
                setSelectedInternalRecipients(filteredRecipients);
                if (setInternalRecipients)
                {
                    setInternalRecipients(filteredRecipients);
                }
            }
        }
    }, [internalRecipients, currentUser, recipientOrgUser, recipientOrgGroup]);

    useEffect(() =>
    {
        // Initialize from props if they exist
        if (recipientOrg)
        {
            setSelectedOrg(recipientOrg);
            setOrgSearchQuery(recipientOrg.name);

            // Load users and groups for this organization
            loadOrganizationUsers(recipientOrg.id || "");
            loadOrganizationGroups(recipientOrg.id || "");
        }

        if (recipientOrgUser)
        {
            // Ensure we're not selecting the current user
            if (recipientOrgUser.id !== currentUser?.id)
            {
                setSelectedOrgUser(recipientOrgUser as unknown as AppUserDetailedDto);
                setShareWith(ShareWithMode.INDIVIDUAL);
                setOrgIndividualSearchQuery(`${recipientOrgUser.person?.firstName || ''} ${recipientOrgUser.person?.lastName || ''} (${recipientOrgUser.email})`);
            }
        }

        if (recipientOrgGroup)
        {
            setSelectedOrgGroup(recipientOrgGroup);
            setShareWith(ShareWithMode.GROUP);
            setOrgGroupSearchQuery(recipientOrgGroup.name);
        }

        if (internalRecipients && internalRecipients.length > 0)
        {
            // Filter out the current user and recipient user from internal recipients
            let filteredRecipients = internalRecipients.filter(user => user.id !== currentUser?.id);

            // Filter out the recipient user if present
            if (recipientOrgUser)
            {
                filteredRecipients = filteredRecipients.filter(user => user.id !== recipientOrgUser.id);
            }

            setSelectedInternalRecipients(filteredRecipients);
        }
    }, []);

    useEffect(() =>
    {
        loadPairedOrganizations();
    }, []);

    useEffect(() =>
    {
        if (selectedOrg)
        {
            loadOrganizationUsers(selectedOrg.id || "");
            loadOrganizationGroups(selectedOrg.id || "");

            if (!internalRecipients || internalRecipients.length === 0)
            {
                setSelectedInternalRecipients([]);
            }
        }
    }, [selectedOrg]);

    const loadPairedOrganizations = async () =>
    {
        setIsLoadingOrgs(true);

        try
        {
            const orgs = await fetchPairedOrganizations();
            setPairedOrgs(orgs);
        }
        catch (error)
        {
            console.error("Error loading paired organizations:", error);
        }
        finally
        {
            setIsLoadingOrgs(false);
        }
    };

    const loadOrganizationUsers = async (orgId: string) =>
    {
        setIsLoadingUsers(true);

        try
        {
            const users = await fetchOrganizationUsers(orgId);
            setOrgUsers(users);
        }
        catch (error)
        {
            console.error("Error loading organization users:", error);
        }
        finally
        {
            setIsLoadingUsers(false);
        }
    };

    const loadOrganizationGroups = async (orgId: string) =>
    {
        setIsLoadingGroups(true);

        try
        {
            const groups = await fetchOrganizationGroups(orgId);
            setOrgGroups(groups);
        }
        catch (error)
        {
            console.error("Error loading organization groups:", error);
        }
        finally
        {
            setIsLoadingGroups(false);
        }
    };

    const onShareWithChange = (_: React.FormEvent<HTMLDivElement>, data: { value: string }) =>
    {
        setSelectedOrgUser(null)
        setSelectedOrgGroup(null);
        setRecipientOrgUser(undefined);
        setRecipientOrgGroup(undefined);

        if (shareWith === ShareWithMode.INDIVIDUAL)
        {
            setOrgIndividualSearchQuery("");
        }
        else if (shareWith === ShareWithMode.GROUP)
        {
            setOrgGroupSearchQuery("");
        }

        setShareWith(data.value as ShareWithMode);
    };

    const onSelectOrgOptionItem: ComboboxProps["onOptionSelect"] = (_, data: OptionOnSelectData) =>
    {
        if (!data || !data.optionValue)
        {
            setSelectedOrg(null);
            setOrgSearchQuery("");

            setRecipientOrg(undefined);
            setRecipientOrgUser(undefined);
            setRecipientOrgGroup(undefined);

            return;
        }

        const orgId = data.optionValue
        const org = pairedOrgs.find(o => o.id === orgId);

        if (org)
        {
            setSelectedOrg(org || null);
            setOrgSearchQuery(org ? org.name : "");

            setRecipientOrg(org);
            setRecipientOrgUser(undefined);
            setRecipientOrgGroup(undefined);
        }
    };

    const onSelectOrgIndividualOptionItem: ComboboxProps["onOptionSelect"] = (_, data) =>
    {
        if (!data || !data.optionValue)
        {
            setOrgIndividualSearchQuery("");
            setRecipientOrgUser(undefined);

            // Refresh internal recipients when clearing the individual selection
            if (internalRecipients && setInternalRecipients)
            {
                const updatedRecipients = internalRecipients.filter(user => user.id !== currentUser?.id);
                setSelectedInternalRecipients(updatedRecipients);
                setInternalRecipients(updatedRecipients);
            }
            return;
        }

        const userId = data.optionValue;
        const selectedOrgUser = orgUsers.find(u => u.id === userId);

        // Ensure we're not selecting the current user
        if (selectedOrgUser && selectedOrgUser.id !== currentUser?.id)
        {
            const firstName = selectedOrgUser.person.firstName;
            const lastName = selectedOrgUser.person.lastName;
            const email = selectedOrgUser.email;

            const query = `${firstName} ${lastName} (${email})`;
            setOrgIndividualSearchQuery(query);
            setSelectedOrgUser(selectedOrgUser);
            setRecipientOrgUser(selectedOrgUser);

            // Filter out the selected user from internal recipients
            if (internalRecipients && setInternalRecipients)
            {
                const updatedRecipients = internalRecipients.filter(
                    user => user.id !== currentUser?.id && user.id !== selectedOrgUser.id
                );
                setSelectedInternalRecipients(updatedRecipients);
                setInternalRecipients(updatedRecipients);
            }
        }
    };

    const onSelectOrgGroupOptionItem: ComboboxProps["onOptionSelect"] = (_, data) =>
    {
        if (!data || !data.optionValue)
        {
            setOrgGroupSearchQuery("");
            setRecipientOrgGroup(undefined);
            return;
        }

        const groupId = data.optionValue;
        const selectedOrgGroup = orgGroups.find(g => g.id === groupId);

        if (selectedOrgGroup)
        {
            const query = `${selectedOrgGroup.name}`;
            setOrgGroupSearchQuery(query);
            setSelectedOrgGroup(selectedOrgGroup);
            setRecipientOrgGroup(selectedOrgGroup);
        }
    };

    // Get users that should be filtered from the internal recipients list
    const getFilteredOrgUsers = () =>
    {
        let usersToFilter = [...orgUsers];

        // Always filter the current user
        usersToFilter = usersToFilter.filter(user => user.id !== currentUser?.id);

        // Filter the selected individual user if present
        if (selectedOrgUser)
        {
            usersToFilter = usersToFilter.filter(user => user.id !== selectedOrgUser.id);
        }

        return usersToFilter;
    };

    const renderOrgSelectionSection = () =>
    {
        return <>
            <Field label={
                <InfoLabel info="Only organizations you have paired with will be shown">
                    Organization
                </InfoLabel>}>
                <Combobox
                    onOptionSelect={onSelectOrgOptionItem}
                    placeholder="Select organization"
                    onChange={(ev) => setOrgSearchQuery(ev.target.value)}
                    value={orgSearchQuery}>
                    {filteredPairedOrgs}
                </Combobox>
            </Field>

            {selectedOrg && (
                <Field>
                    <RadioGroup
                        layout={"horizontal"}
                        value={shareWith}
                        onChange={onShareWithChange}>
                        <Radio value={ShareWithMode.INDIVIDUAL} label="Individual"/>
                        <Radio value={ShareWithMode.GROUP} label="Group"/>
                    </RadioGroup>
                </Field>
            )}

            {(selectedOrg && shareWith === ShareWithMode.INDIVIDUAL) && (
                <Field>
                    {isLoadingUsers ? (
                        <Spinner size="tiny" label="Loading users..."/>
                    ) : (
                        <Combobox
                            onOptionSelect={onSelectOrgIndividualOptionItem}
                            placeholder="Select Individual"
                            onChange={(ev) => setOrgIndividualSearchQuery(ev.target.value)}
                            value={orgIndividualSearchQuery}>
                            {filteredOrgIndividuals}
                        </Combobox>
                    )}
                </Field>
            )}

            {(selectedOrg && shareWith === ShareWithMode.GROUP) && (
                <Field>
                    {isLoadingGroups ? (
                        <Spinner size="tiny" label="Loading groups..."/>
                    ) : (
                        <Combobox
                            onOptionSelect={onSelectOrgGroupOptionItem}
                            placeholder="Select Group/Team/Department"
                            onChange={(ev) => setOrgGroupSearchQuery(ev.target.value)}
                            value={orgGroupSearchQuery}>
                            {filteredOrgGroups}
                        </Combobox>
                    )}
                </Field>
            )}
        </>
    }

    return <>
        {isLoadingOrgs && (<Spinner size="tiny" label="Loading organizations..."/>)}
        {!isLoadingOrgs && renderOrgSelectionSection()}
        {(!isLoadingOrgs && selectedOrg && (selectedOrgUser || selectedOrgGroup)) &&
            <MyOrgRecipients
                orgUsers={getFilteredOrgUsers()}
                isLoadingUsers={isLoadingUsers}
                selectedInternalRecipients={selectedInternalRecipients}
                setSelectedInternalRecipients={setSelectedInternalRecipients}
                onAddOrRemoveInternalRecipients={setInternalRecipients}
            />
        }
    </>
};

export default ExternalOrganizationRecipients;