import React, {useEffect, useState} from 'react';
import {
    Combobox,
    ComboboxProps,
    Field,
    InfoLabel,
    Option,
    OptionOnSelectData,
    Spinner
} from "@fluentui/react-components";
import {AppUserPublicDto, OrganizationBasicDto} from "../../../../models/models.tsx";
import {
    fetchOrganizationUsers,
    fetchPairedOrganizationGroups,
    fetchPairedOrganizations,
    fetchPairedOrganizationUsers,
    OrganizationGroupBasicDto
} from "../../../../../services/organizationApi";
import MyOrgRecipients from "../MyOrgRecipients.tsx";
import {useAuth} from "../../../../../context/AuthContext.tsx";

interface ExternalOrganizationRecipientsProps
{
    recipientOrg: OrganizationBasicDto | undefined;
    recipientOrgUser: AppUserPublicDto | undefined;
    recipientOrgGroup: OrganizationGroupBasicDto | undefined;
    internalParticipants: AppUserPublicDto[] | undefined;
    setRecipientOrg: (org: OrganizationBasicDto | undefined) => void;
    setRecipientOrgUser: (user: AppUserPublicDto | undefined) => void;
    setRecipientOrgGroup: (group: OrganizationGroupBasicDto | undefined) => void;
    setInternalParticipants?: (users: AppUserPublicDto[]) => void;
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
        internalParticipants,
        setRecipientOrg,
        setRecipientOrgUser,
        setRecipientOrgGroup,
        setInternalParticipants
    }) =>
{
    const {appUser, appUserPersonOrganization, token} = useAuth()
    const [isLoadingOrgs, setIsLoadingOrgs] = useState<boolean>(false);
    const [isLoadingUsers, setIsLoadingUsers] = useState<boolean>(false);
    const [isLoadingGroups, setIsLoadingGroups] = useState<boolean>(false);
    const [selectedOrg, setSelectedOrg] = useState<OrganizationBasicDto | null>(null);
    const [selectedOrgGroup, setSelectedOrgGroup] = useState<OrganizationGroupBasicDto | null>(null);
    const [selectedOrgUser, setSelectedOrgUser] = useState<AppUserPublicDto | null>(null);
    const [selectedInternalRecipients, setSelectedInternalParticipants] = useState<AppUserPublicDto[]>([]);
    // Trusted-org v1: when picking recipients in a paired org, only the org's
    // explicitly published groups are visible, never individual users. The mode
    // is therefore locked to GROUP and the Individual radio is hidden.
    const [shareWith, setShareWith] = useState<ShareWithMode>(ShareWithMode.GROUP);
    const [orgSearchQuery, setOrgSearchQuery] = useState<string>("");
    const [orgIndividualSearchQuery, setOrgIndividualSearchQuery] = useState<string>("");
    const [orgGroupSearchQuery, setOrgGroupSearchQuery] = useState<string>("");
    const [pairedOrgs, setPairedOrgs] = useState<OrganizationBasicDto[]>([]);
    const [orgUsers, setOrgUsers] = useState<AppUserPublicDto[]>([]);
    const [orgGroups, setOrgGroups] = useState<OrganizationGroupBasicDto[]>([]);
    const [myOrgUsers, setMyOrgUsers] = useState<AppUserPublicDto[]>([]);

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
        .filter(orgUser => orgUser.id !== appUser?.id)
        .filter(orgUser => !orgIndividualSearchQuery ||
            (orgUser.email.toLowerCase().includes(orgIndividualSearchQuery.toLowerCase()) ||
                (orgUser.person?.firstName ?? '').toLowerCase().includes(orgIndividualSearchQuery.toLowerCase()) ||
                (orgUser.person?.lastName ?? '').toLowerCase().includes(orgIndividualSearchQuery.toLowerCase())))
        .map(orgUser => (
            <Option key={orgUser.id}
                    text={orgUser.email}
                    value={orgUser.id}>
                {`${orgUser.person?.firstName ?? ''} ${orgUser.person?.lastName ?? ''} (${orgUser.email})`}
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
        if (recipientOrg)
        {
            setSelectedOrg(recipientOrg);
            setOrgSearchQuery(recipientOrg.name);

            loadOrganizationUsers(recipientOrg.id || "");
            loadOrganizationGroups(recipientOrg.id || "");
        }

        if (recipientOrgUser)
        {
            if (recipientOrgUser.id !== appUser?.id)
            {
                setSelectedOrgUser(recipientOrgUser);
                setShareWith(ShareWithMode.INDIVIDUAL);
                setOrgIndividualSearchQuery(`${recipientOrgUser.person?.firstName ?? ''} ${recipientOrgUser.person?.lastName ?? ''} (${recipientOrgUser.email})`);
            }
        }

        if (recipientOrgGroup)
        {
            setSelectedOrgGroup(recipientOrgGroup);
            setShareWith(ShareWithMode.GROUP);
            setOrgGroupSearchQuery(recipientOrgGroup.name);
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
        loadPairedOrganizations();
        loadMyOrgAppUsers()
    }, []);

    useEffect(() =>
    {
        if (selectedOrg)
        {
            loadOrganizationUsers(selectedOrg.id || "");
            loadOrganizationGroups(selectedOrg.id || "");

            if (!internalParticipants || internalParticipants.length === 0)
            {
                setSelectedInternalParticipants([]);
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

    const loadMyOrgAppUsers = async () =>
    {
        setIsLoadingOrgs(true);

        try
        {
            const orgs = await fetchOrganizationUsers(appUserPersonOrganization.id, token);
            setMyOrgUsers(orgs);
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
            const users = await fetchPairedOrganizationUsers(orgId);
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
            const groups = await fetchPairedOrganizationGroups(orgId);
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
            setSelectedOrg(org);
            setOrgSearchQuery(org.name);

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

            if (internalParticipants && setInternalParticipants)
            {
                const updatedParticipants = internalParticipants.filter(user => user.id !== appUser?.id);
                setSelectedInternalParticipants(updatedParticipants);
                setInternalParticipants(updatedParticipants);
            }
            return;
        }

        const userId = data.optionValue;
        const selectedOrgUser = orgUsers.find(u => u.id === userId);

        if (selectedOrgUser && selectedOrgUser.id !== appUser?.id)
        {
            const firstName = selectedOrgUser.person?.firstName ?? '';
            const lastName = selectedOrgUser.person?.lastName ?? '';
            const email = selectedOrgUser.email;

            const query = `${firstName} ${lastName} (${email})`;
            setOrgIndividualSearchQuery(query);
            setSelectedOrgUser(selectedOrgUser);
            setRecipientOrgUser(selectedOrgUser);

            if (internalParticipants && setInternalParticipants)
            {
                const updatedParticipants = internalParticipants.filter(
                    user => user.id !== appUser?.id && user.id !== selectedOrgUser.id
                );
                setSelectedInternalParticipants(updatedParticipants);
                setInternalParticipants(updatedParticipants);
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

    const getFilteredOrgUsers = () =>
    {
        let usersToFilter = [...myOrgUsers];

        usersToFilter = usersToFilter.filter(user => user.id !== appUser?.id);

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
                <InfoLabel info="Only organizations that are public or you have paired with will be shown here">
                    Organization
                </InfoLabel>}>
                <Combobox
                    id={"external-org-combobox"}
                    onOptionSelect={onSelectOrgOptionItem}
                    placeholder="Select organization"
                    onChange={(ev) => setOrgSearchQuery(ev.target.value)}
                    value={orgSearchQuery}>
                    {filteredPairedOrgs}
                </Combobox>
            </Field>

            {(selectedOrg && shareWith === ShareWithMode.GROUP) && (
                <Field>
                    {isLoadingGroups ? (
                        <Spinner size="tiny" label="Loading groups..."/>
                    ) : (
                        <Combobox
                            id={"external-org-group-combobox"}
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
                setSelectedInternalParticipants={setSelectedInternalParticipants}
                setInternalParticipants={setInternalParticipants}
            />
        }
    </>
};

export default ExternalOrganizationRecipients;