import type {ReactNode} from "react";
import {Divider, Tab, TabList, TabValue} from "@fluentui/react-components";
import {
    AuditIcon,
    SettingsAppSettingsTabIcon,
    SettingsCommunicationsTabIcon,
    SettingsDeviceSessionsTabIcon,
    SettingsDocumentsTabIcon,
    SettingsExchangeBlueprintsTabIcon,
    SettingsFieldsTabIcon,
    SettingsInformationRequestsTabIcon,
    SettingsLinkedAccountsTabIcon,
    SettingsMyGroupsTabIcon,
    SettingsOrganizationBillingTabIcon,
    SettingsOrganizationTabIcon,
    SettingsProfileTabIcon,
    SettingsSequencesTabIcon,
    SettingsVariablesTabIcon,
    SettingsWorkflowsTabIcon,
} from "../../../components/IconBundles.tsx";
import {useSettingsStyles} from "../../SettingsStyles.tsx";
import type {SettingsMenuProps} from "./SettingsMenu.tsx";

interface MenuItemDefinition
{
    id: string;
    icon: ReactNode;
    value: TabValue;
    label: string;
    disabled?: boolean;
}

const SettingsMenuTabs = (props: SettingsMenuProps) =>
{
    const styles = useSettingsStyles();
    const personalItems: MenuItemDefinition[] = [
        {id: "ProfileTab", icon: <SettingsProfileTabIcon/>, value: props.tabIds.profile, label: "Profile"},
        {id: "MyGroupsTab", icon: <SettingsMyGroupsTabIcon/>, value: props.tabIds.myGroups, label: "Groups"},
        {id: "LinkedAccountsTab", icon: <SettingsLinkedAccountsTabIcon/>, value: props.tabIds.linkedAccounts, label: "Linked Accounts"},
        {id: "SessionsTab", icon: <SettingsDeviceSessionsTabIcon/>, value: props.tabIds.sessions, label: "Device Sessions"},
        {id: "AppSettingsTab", icon: <SettingsAppSettingsTabIcon/>, value: props.tabIds.appSettings, label: "Preferences"},
        ...(!props.hasOrg && props.canSeeBillingTab
            ? [{id: "PersonalBillingTab", icon: <SettingsOrganizationBillingTabIcon/>, value: props.tabIds.organizationBilling, label: "Billing"}]
            : []),
    ];
    const contentItems: MenuItemDefinition[] = [
        ...(props.canUseDocumentLibrary
            ? [{id: "DocumentsTab", icon: <SettingsDocumentsTabIcon/>, value: props.tabIds.documents, label: "Document Library"}]
            : []),
        ...(props.canUseBlueprints
            ? [{id: "BlueprintsTab", icon: <SettingsExchangeBlueprintsTabIcon/>, value: props.tabIds.blueprints, label: "Blueprints"}]
            : []),
        ...(props.canManageOrganization && props.canUseBusinessFields
            ? [{id: "FieldsTab", icon: <SettingsFieldsTabIcon/>, value: props.tabIds.fields, label: "Fields"}]
            : []),
        ...(props.canUseInformationRequests
            ? [{
                id: "InformationRequestTemplatesTab",
                icon: <SettingsInformationRequestsTabIcon/>,
                value: props.tabIds.informationRequestTemplates,
                label: "Information Requests",
            }]
            : []),
    ];
    const automationItems: MenuItemDefinition[] = [
        ...(props.canUseWorkflows
            ? [{id: "WorkflowsTab", icon: <SettingsWorkflowsTabIcon/>, value: props.tabIds.workflows, label: "Workflows"}]
            : []),
        ...(props.hasOrg && props.canUseVariables
            ? [{id: "SequencesTab", icon: <SettingsSequencesTabIcon/>, value: props.tabIds.sequences, label: "Sequences"}]
            : []),
        ...(props.canUseVariables
            ? [{id: "VariablesTab", icon: <SettingsVariablesTabIcon/>, value: props.tabIds.variables, label: "Variables"}]
            : []),
        ...(props.canUseWorkflows
            ? [{id: "CommunicationsTab", icon: <SettingsCommunicationsTabIcon/>, value: props.tabIds.communications, label: "Communications"}]
            : []),
    ];
    const organizationItems: MenuItemDefinition[] = [
        ...(props.canSeeOrganizationAdminTab
            ? [{id: "OrganizationTab", icon: <SettingsOrganizationTabIcon/>, value: props.tabIds.organization, label: "Administration"}]
            : []),
        ...(props.hasOrg && props.canSeeBillingTab
            ? [{id: "OrganizationBillingTab", icon: <SettingsOrganizationBillingTabIcon/>, value: props.tabIds.organizationBilling, label: "Billing"}]
            : []),
        ...(props.canSeeAuditTab
            ? [{id: "AuditTab", icon: <AuditIcon/>, value: props.tabIds.audit, label: "Audit"}]
            : []),
    ];

    const renderGroup = (title: string, items: MenuItemDefinition[]) => items.length > 0 && (
        <>
            <Divider
                id={`settings-menu-${title.toLowerCase()}-divider`}
                appearance={"brand"}
                alignContent={"start"}
                className={styles.tabSettingDivider}
            >
                {title}
            </Divider>
            {items.map(item => (
                <Tab
                    id={item.id}
                    key={item.id}
                    icon={item.icon}
                    value={item.value}
                    disabled={item.disabled}
                >
                    {item.label}
                </Tab>
            ))}
        </>
    );

    return (
        <TabList
            id={"settings-menu-tabs"}
            selectedValue={props.selectedValue}
            appearance={"subtle-circular"}
            onTabSelect={props.onTabSelect}
            vertical
            size={"medium"}
        >
            {renderGroup("Personal", personalItems)}
            {renderGroup("Content", contentItems)}
            {renderGroup("Automation", automationItems)}
            {renderGroup("Organization", organizationItems)}
        </TabList>
    );
};

export default SettingsMenuTabs;
