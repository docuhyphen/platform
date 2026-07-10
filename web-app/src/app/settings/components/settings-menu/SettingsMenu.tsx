import {
    Divider,
    SelectTabData,
    SelectTabEvent,
    Tab,
    TabList,
    TabValue,
} from "@fluentui/react-components";
import {
    AuditIcon,
    SettingsAppSettingsTabIcon,
    SettingsCommunicationsTabIcon,
    SettingsDeviceSessionsTabIcon,
    SettingsDocumentsTabIcon,
    SettingsExchangeBlueprintsTabIcon,
    SettingsFieldsTabIcon,
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
import {SettingsTabIds} from "../settings-tab-content/SettingsTabContent.tsx";

interface SettingsMenuProps
{
    selectedValue: TabValue;
    tabIds: SettingsTabIds;
    hasOrg: boolean;
    canManageOrganization: boolean | undefined;
    canSeeOrganizationAdminTab: boolean | undefined;
    canSeeAuditTab: boolean;
    onTabSelect: (event: SelectTabEvent, data: SelectTabData) => void;
}

const SettingsMenu = ({
    selectedValue,
    tabIds,
    hasOrg,
    canManageOrganization,
    canSeeOrganizationAdminTab,
    canSeeAuditTab,
    onTabSelect,
}: SettingsMenuProps) =>
{
    const styles = useSettingsStyles();

    return (
        <TabList
            selectedValue={selectedValue}
            appearance="subtle-circular"
            onTabSelect={onTabSelect}
            vertical
            size="medium"
        >
            <Divider appearance={"brand"} alignContent={"start"} className={styles.tabSettingDivider}>Personal</Divider>
            <Tab id="ProfileTab" icon={<SettingsProfileTabIcon/>} value={tabIds.profile}>
                Profile
            </Tab>
            <Tab id="MyGroupsTab" icon={<SettingsMyGroupsTabIcon/>} value={tabIds.myGroups}>
                Groups
            </Tab>
            <Tab id="LinkedAccountsTab" icon={<SettingsLinkedAccountsTabIcon/>} value={tabIds.linkedAccounts}>
                Linked Accounts
            </Tab>
            <Tab id="SessionsTab" icon={<SettingsDeviceSessionsTabIcon/>} value={tabIds.sessions}>
                Device Sessions
            </Tab>
            <Tab id="AppSettingsTab" icon={<SettingsAppSettingsTabIcon/>} value={tabIds.appSettings}>
                Preferences
            </Tab>
            <Divider appearance={"brand"} alignContent={"start"} className={styles.tabSettingDivider}>Content</Divider>
            <Tab id="DocumentsTab" icon={<SettingsDocumentsTabIcon/>} value={tabIds.documents}>
                Document Library
            </Tab>
            <Tab id="BlueprintsTab" icon={<SettingsExchangeBlueprintsTabIcon/>} value={tabIds.blueprints}>
                Blueprints
            </Tab>
            {canManageOrganization && (
                <Tab id="FieldsTab" icon={<SettingsFieldsTabIcon/>} value={tabIds.fields}>
                    Fields
                </Tab>
            )}
            <Divider appearance={"brand"} alignContent={"start"} className={styles.tabSettingDivider}>Automation</Divider>
            <Tab id="WorkflowsTab" icon={<SettingsWorkflowsTabIcon/>} value={tabIds.workflows}>
                Workflows
            </Tab>
            {hasOrg && (
                <Tab id="SequencesTab" icon={<SettingsSequencesTabIcon/>} value={tabIds.sequences}>
                    Sequences
                </Tab>
            )}
            <Tab id="VariablesTab" icon={<SettingsVariablesTabIcon/>} value={tabIds.variables}>
                Variables
            </Tab>
            <Tab id="CommunicationsTab" icon={<SettingsCommunicationsTabIcon/>} value={tabIds.communications}>
                Communications
            </Tab>
            {(canSeeOrganizationAdminTab || canSeeAuditTab) && (
                <>
                    <Divider appearance={"brand"} alignContent={"start"} className={styles.tabSettingDivider}>Organization</Divider>
                    {canSeeOrganizationAdminTab && (
                        <Tab id="OrganizationTab" icon={<SettingsOrganizationTabIcon/>} value={tabIds.organization}>
                            Administration
                        </Tab>
                    )}
                    {canManageOrganization && (
                        <Tab id="OrganizationBillingTab" icon={<SettingsOrganizationBillingTabIcon/>} value={tabIds.organizationBilling}>
                            Billing
                        </Tab>
                    )}
                    {canSeeAuditTab && (
                        <Tab id="AuditTab" icon={<AuditIcon/>} value={tabIds.audit}>
                            Audit
                        </Tab>
                    )}
                </>
            )}
        </TabList>
    );
};

export default SettingsMenu;
