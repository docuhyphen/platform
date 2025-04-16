import React, {useEffect, useState} from 'react';
import {
    Combobox,
    ComboboxProps,
    Field,
    InfoLabel,
    Option,
    Radio,
    RadioGroup,
    Spinner
} from "@fluentui/react-components";
import {AppUserBasicDto, OrganizationBasicDto} from "../../../../models/models.tsx";
import {
    fetchOrganizationGroups,
    fetchOrganizationUsers,
    fetchPairedOrganizations,
    OrganizationGroupBasicDto
} from "../../../../../services/organizationApi";

interface ExternalOrganizationRecipientsProps
{
    onSelectOrg: (org: OrganizationBasicDto | undefined) => void;
    onSelectUser: (user: AppUserBasicDto | undefined) => void;
    onSelectGroup: (group: OrganizationGroupBasicDto | undefined) => void;
}

enum ShareWithMode
{
    INDIVIDUAL = "withIndividual",
    GROUP = "withOrgGroup"
}

const ExternalOrganizationRecipients: React.FC<ExternalOrganizationRecipientsProps> = (
    {
        onSelectOrg,
        onSelectUser,
        onSelectGroup
    }) =>
{
    const [isLoadingOrgs, setIsLoadingOrgs] = useState<boolean>(false);
    const [isLoadingUsers, setIsLoadingUsers] = useState<boolean>(false);
    const [isLoadingGroups, setIsLoadingGroups] = useState<boolean>(false);
    const [selectedOrg, setSelectedOrg] = useState<OrganizationBasicDto | null>(null);
    const [shareWith, setShareWith] = useState<ShareWithMode>(ShareWithMode.INDIVIDUAL);
    const [orgSearchQuery, setOrgSearchQuery] = useState<string>("");
    const [orgIndividualSearchQuery, setOrgIndividualSearchQuery] = useState<string>("");
    const [orgGroupSearchQuery, setOrgGroupSearchQuery] = useState<string>("");
    const [pairedOrgs, setPairedOrgs] = useState<OrganizationBasicDto[]>([]);
    const [orgUsers, setOrgUsers] = useState<AppUserBasicDto[]>([]);
    const [orgGroups, setOrgGroups] = useState<OrganizationGroupBasicDto[]>([]);

    const filteredPairedOrgs = pairedOrgs
        .filter(org => !orgSearchQuery || org.name.toLowerCase().includes(orgSearchQuery.toLowerCase()))
        .map(org => (
            <Option key={org.id} text={org.name} value={org}>
                {org.name}
            </Option>
        ));

    const filteredOrgIndividuals = orgUsers
        .filter(user => !orgIndividualSearchQuery || user.email.toLowerCase().includes(orgIndividualSearchQuery.toLowerCase()))
        .map(user => (
            <Option key={user.id} text={user.email} value={user}>
                {user.email}
            </Option>
        ));

    const filteredOrgGroups = orgGroups
        .filter(group => !orgGroupSearchQuery || group.name.toLowerCase().includes(orgGroupSearchQuery.toLowerCase()))
        .map(group => (
            <Option key={group.id} text={group.name} value={group}>
                {group.name}
            </Option>
        ));

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

    const onShareWithChange = (_: React.ChangeEvent<HTMLInputElement>, data: { value: string }) =>
    {
        if (shareWith === ShareWithMode.INDIVIDUAL)
        {
            onSelectUser(undefined);
            setOrgIndividualSearchQuery("");
        }
        else if (shareWith === ShareWithMode.GROUP)
        {
            onSelectGroup(undefined);
            setOrgGroupSearchQuery("");
        }

        setShareWith(data.value as ShareWithMode);
    };

    const onSelectOrgOptionItem: ComboboxProps["onOptionSelect"] = (_, data) =>
    {
        if (!data && !data.optionValue)
        {
            setSelectedOrg(null);
            setOrgSearchQuery("");

            onSelectOrg(undefined);
            onSelectUser(undefined);
            onSelectGroup(undefined);

            return;
        }

        const selectedOrg = pairedOrgs.find(o => o.id === data.optionValue['id']);

        setSelectedOrg(selectedOrg || null);
        setOrgSearchQuery(selectedOrg ? selectedOrg.name : "");

        onSelectOrg(selectedOrg);
        onSelectUser(undefined);
        onSelectGroup(undefined);
    };

    const onSelectOrgIndividualOptionItem: ComboboxProps["onOptionSelect"] = (_, data) =>
    {
        const selected = data.optionValue;
        const query = selected ?
            `${selected.person?.firstName || ''} ${selected.person?.lastName || ''} (${selected.email})` : ""

        setOrgIndividualSearchQuery(query);

        onSelectUser(selected);
        onSelectGroup(undefined);
    };

    const onSelectOrgGroupOptionItem: ComboboxProps["onOptionSelect"] = (_, data) =>
    {
        if (data.optionValue && typeof data.optionValue === 'object')
        {
            const selected = data.optionValue as AppUserBasicDto;
            setOrgGroupSearchQuery(selected ? `${selected.name} (${selected.memberCount} members)` : "");
            onSelectUser(selected);
            onSelectGroup(undefined);
        }

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
    </>
};

export default ExternalOrganizationRecipients;