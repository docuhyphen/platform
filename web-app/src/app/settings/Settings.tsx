import {useState} from "react";
import {
    Button,
    DrawerBody,
    DrawerHeader,
    DrawerHeaderTitle,
    OverlayDrawer,
    SelectTabData,
    SelectTabEvent,
    Tab,
    TabList,
    TabValue,
    Text,
} from "@fluentui/react-components";
import {Navigation24Regular} from "@fluentui/react-icons";
import {useSettingsStyles} from "./SettingsStyles.tsx";
import {
    SettingsAppSettingsTabIcon, SettingsDeviceSessionsTabIcon,
    SettingsExchangeTemplatesTabIcon, SettingsLinkedAccountsTabIcon, SettingsMyGroupsTabIcon,
    SettingsOrganizationTabIcon,
    SettingsProfileTabIcon,
    SettingsWorkflowsTabIcon,
} from "../components/IconBundles.tsx";
import TemplatesTab from "./templates-tab/TemplatesTab.tsx";
import WorkflowsTab from "./workflows-tab/WorkflowsTab.tsx";
import AppSettingsTab from "./app-settings-tab/AppSettingsTab.tsx";
import ProfileTab from "./profile-tab/ProfileTab.tsx";
import OrganizationGroupsTab from "./organization-groups-tab/OrganizationGroupsTab.tsx";
import OrganizationPeopleTab from "./organization-people-tab/OrganizationPeopleTab.tsx";
import {useAuth} from "../../context/AuthContext.tsx";
import OrganizationPairingTab from "./organization-pairing-tab/OrganizationPairingTab.tsx";
import LinkedAccountsTab from "./linked-accounts-tab/LinkedAccountsTab.tsx";
import SessionsTab from "./sessions-tab/SessionsTab.tsx";
import MyGroupsTab from "./my-groups-tab/MyGroupsTab.tsx";
import AppAdminsTab from "./app-admins-tab/AppAdminsTab.tsx";
import OrganizationTab from "./organization-tab/OrganizationTab.tsx";
import {useIsMobile} from "../../utils/useMediaQuery.ts";
import {AppUserRole} from "../models/models.tsx";

const Settings = () =>
{
    const tabIds = {
        profile: "ProfileTab",
        linkedAccounts: "LinkedAccountsTab",
        sessions: "SessionsTab",
        organization: "OrganizationDetailsTab",
        appSettings: "AppSettingsTab",
        people: "PeopleTab",
        groups: "GroupsTab",
        organizationPairing: "OrganizationPairingTab",
        templates: "TemplatesTab",
        myGroups: "MyGroupsTab",
        appAdmins: "AppAdminsTab",
        workflows: "WorkflowsTab",
    }

    const tabLabels: Record<string, string> = {
        [tabIds.profile]: "Profile",
        [tabIds.linkedAccounts]: "Linked Accounts",
        [tabIds.sessions]: "Device Sessions",
        [tabIds.organization]: "Your Organization",
        [tabIds.appSettings]: "App Preferences",
        [tabIds.myGroups]: "My Groups",
        [tabIds.templates]: "Exchange Templates",
    };

    const {appUser, appUserPersonOrganization} = useAuth();
    const styles = useSettingsStyles();
    const isMobile = useIsMobile();
    const [selectedValue, setSelectedValue] = useState<TabValue>(tabIds.profile);
    const [isMobileDrawerOpen, setIsMobileDrawerOpen] = useState(false);

    const onTabSelect = (_event: SelectTabEvent, data: SelectTabData) =>
    {
        setSelectedValue(data.value);
        setIsMobileDrawerOpen(false);
    };

    const currentTabLabel = tabLabels[selectedValue as string] ?? "Settings";

    const roleValue = `${appUser?.role ?? ''}`;
    const canManageOrganization =
        appUserPersonOrganization?.isActive &&
        (roleValue === AppUserRole.ORG_ADMIN || roleValue === 'APP_ADMIN');

    const tabListContent = (
        <TabList
            selectedValue={selectedValue}
            appearance="subtle-circular"
            onTabSelect={onTabSelect}
            vertical
            size="medium"
        >
            <Tab id="ProfileTab" icon={<SettingsProfileTabIcon/>} value={tabIds.profile}>
                Profile
            </Tab>
            <Tab id="AppSettingsTab" icon={<SettingsAppSettingsTabIcon/>} value={tabIds.appSettings}>
                App Preferences
            </Tab>
            <Tab id="LinkedAccountsTab" icon={<SettingsLinkedAccountsTabIcon/>} value={tabIds.linkedAccounts}>
                Linked Accounts
            </Tab>
            <Tab id="SessionsTab" icon={<SettingsDeviceSessionsTabIcon/>} value={tabIds.sessions}>
                Device Sessions
            </Tab>
            <Tab id="MyGroupsTab" icon={<SettingsMyGroupsTabIcon/>} value={tabIds.myGroups}>
                My Groups
            </Tab>
            {canManageOrganization && (
                <Tab id="OrganizationTab" icon={<SettingsOrganizationTabIcon/>} value={tabIds.organization}>
                    Your Organization
                </Tab>
            )}
            <Tab id="TemplatesTab" icon={<SettingsExchangeTemplatesTabIcon/>} value={tabIds.templates}>
                Exchange Templates
            </Tab>
            {canManageOrganization && (
                <Tab id="WorkflowsTab" icon={<SettingsWorkflowsTabIcon/>} value={tabIds.workflows}>
                    Workflows
                </Tab>
            )}
        </TabList>
    );

    return (
        <div className={styles.container} id="settings-container">

            <div className={styles.mobileMenuBar}>
                <Button
                    appearance="subtle"
                    shape="circular"
                    icon={<Navigation24Regular/>}
                    aria-label="Open settings menu"
                    onClick={() => setIsMobileDrawerOpen(true)}
                />
                <Text weight="semibold">{currentTabLabel}</Text>
            </div>

            {/* ── Mobile-only: overlay drawer ── */}
            <OverlayDrawer
                open={isMobile && isMobileDrawerOpen}
                onOpenChange={(_, {open}) => setIsMobileDrawerOpen(open)}
                position="start"
            >
                <DrawerHeader>
                    <DrawerHeaderTitle>Settings</DrawerHeaderTitle>
                </DrawerHeader>
                <DrawerBody>
                    {tabListContent}
                </DrawerBody>
            </OverlayDrawer>

            {/* ── Main flex layout ── */}
            <div className={styles.layout}>

                {/* Desktop sticky sidebar */}
                <div className={styles.sidebarWrapper}>
                    {tabListContent}
                </div>

                {/* Tab content area */}
                <div className={styles.tabsContainer} id="settings-tabs">
                    {selectedValue === tabIds.profile && <ProfileTab/>}
                    {selectedValue === tabIds.linkedAccounts && <LinkedAccountsTab/>}
                    {selectedValue === tabIds.sessions && <SessionsTab/>}
                    {selectedValue === tabIds.organization && <OrganizationTab/>}
                    {selectedValue === tabIds.appSettings && <AppSettingsTab/>}
                    {selectedValue === tabIds.myGroups && <MyGroupsTab/>}
                    {selectedValue === tabIds.people && <OrganizationPeopleTab/>}
                    {selectedValue === tabIds.groups &&
                        <OrganizationGroupsTab appUserPersonOrganization={appUserPersonOrganization}/>}
                    {selectedValue === tabIds.organizationPairing && <OrganizationPairingTab/>}
                    {selectedValue === tabIds.appAdmins && <AppAdminsTab/>}
                    {selectedValue === tabIds.templates && <TemplatesTab/>}
                    {selectedValue === tabIds.workflows && <WorkflowsTab/>}
                </div>

            </div>
        </div>
    );
}

export default Settings;