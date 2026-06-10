import React, {useEffect, useState} from "react";

import {
    SelectTabData,
    Drawer,
    DrawerBody,
    DrawerHeader,
    DrawerHeaderTitle,
    InlineDrawer,
    OverlayDrawer,
    SelectTabEvent, Tab, TabList, TabValue,} from "@fluentui/react-components";
import {useSettingsStyles} from "./SettingsStyles.tsx";
import OrganizationDetailsTab from "./organization-tab/OrganizationTab.tsx";
import {
    PairOrgTabIcon, SettingsAppAdminsIcon,
    SettingsAppSettingsTabIcon, SettingsDeviceSessionsTabIcon,
    SettingsExchangeTemplatesTabIcon, SettingsLinkedAccountsTabIcon, SettingsMyGroupsTabIcon,
    SettingsOrganizationGroupsTabIcon,
    SettingsOrganizationPeopleTabIcon,
    SettingsOrganizationTabIcon,
    SettingsProfileTabIcon,
} from "../components/IconBundles.tsx";
import TemplatesTab from "./templates-tab/TemplatesTab.tsx";
import AppSettingsTab from "./app-settings-tab/AppSettingsTab.tsx";
import ProfileTab from "./profile-tab/ProfileTab.tsx";
import OrganizationGroupsTab from "./organization-groups-tab/OrganizationGroupsTab.tsx";
import OrganizationPeopleTab from "./organization-people-tab/OrganizationPeopleTab.tsx";
import {useAuth} from "../../context/AuthContext.tsx";
import {AppUserRole} from "../models/models.tsx";
import OrganizationPairingTab from "./organization-pairing-tab/OrganizationPairingTab.tsx";
import LinkedAccountsTab from "./linked-accounts-tab/LinkedAccountsTab.tsx";
import SessionsTab from "./sessions-tab/SessionsTab.tsx";
import MyGroupsTab from "./my-groups-tab/MyGroupsTab.tsx";
import AppAdminsTab from "./app-admins-tab/AppAdminsTab.tsx";
import OrganizationTab from "./organization-tab/OrganizationTab.tsx";

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
        appAdmins: "AppAdminsTab"
    }

    const [isMenuDrawerOpen, setIsMenuDrawerOpen] = React.useState(true);
    const [menuDrawerType, setMenuDrawerType] = React.useState<"overlay" | "inline">("inline");
    const {appUser, appUserPersonOrganization} = useAuth();
    const styles = useSettingsStyles();
    const [selectedValue, setSelectedValue] = useState<TabValue>(tabIds.profile);
    const roleValue = `${appUser?.role ?? ''}`;

    const canManageOrganization =  () =>
    {
        return appUserPersonOrganization?.isActive && (roleValue === AppUserRole.ORG_ADMIN || roleValue === 'APP_ADMIN')
    }

    const onTabSelect = (_event: SelectTabEvent, data: SelectTabData) =>
    {
        setSelectedValue(data.value);
    };

    return (
        <>
            <div className={styles.container}>
                <Drawer
                    type={menuDrawerType}
                    open={isMenuDrawerOpen}
                    onOpenChange={(_, { open }) => setIsMenuDrawerOpen(open)}
                >
                    <TabList selectedValue={selectedValue}
                             appearance="subtle-circular"
                             onTabSelect={onTabSelect}
                             vertical
                             size="medium">
                        <Tab id="ProfileTab"
                             icon={<SettingsProfileTabIcon/>}
                             value={tabIds.profile}>
                            Profile
                        </Tab>
                        <Tab id="AppSettingsTab"
                             icon={<SettingsAppSettingsTabIcon/>}
                             value={tabIds.appSettings}>
                            App Preferences
                        </Tab>
                        <Tab id="LinkedAccountsTab"
                             icon={<SettingsLinkedAccountsTabIcon/>}
                             value={tabIds.linkedAccounts}>
                            Linked Accounts
                        </Tab>
                        <Tab id="SessionsTab"
                             icon={<SettingsDeviceSessionsTabIcon/>}
                             value={tabIds.sessions}>
                            Device Sessions
                        </Tab>
                        <Tab id="MyGroupsTab"
                             icon={<SettingsMyGroupsTabIcon/>}
                             value={tabIds.myGroups}>
                            My Groups
                        </Tab>
                        <Tab id="OrganizationTab"
                             icon={<SettingsOrganizationTabIcon/>}
                             value={tabIds.organization}>
                            Your Organization
                        </Tab>
                        <Tab id="TemplatesTab"
                             icon={<SettingsExchangeTemplatesTabIcon/>}
                             value={tabIds.templates}>
                            Exchange Templates
                        </Tab>
                    </TabList>
                </Drawer>
                <div className={styles.tabsContainer}
                     id={"settings-tabs"}>
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
                </div>
            </div>
        </>
    );
}

export default Settings;