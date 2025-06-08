import * as React from "react";
import {useEffect, useState} from "react";
import {SelectTabData, SelectTabEvent, Tab, TabList, TabValue,} from "@fluentui/react-components";
import {useSettingsStyles} from "./SettingsStyles.tsx";
import MainMenu from "../components/MainMenu.tsx";
import OrganizationTab from "./organization-tab/OrganizationTab.tsx";
import {
    PairOrgTabIcon,
    SettingsAppSettingsTabIcon,
    SettingsOrganizationGroupsTabIcon,
    SettingsOrganizationPeopleTabIcon,
    SettingsOrganizationTabIcon,
    SettingsProfileTabIcon,
    SettingsTemplatesTabIcon
} from "../components/IconBundles.tsx";
import TemplatesTab from "./templates-tab/TemplatesTab.tsx";
import AppSettingsTab from "./app-settings-tab/AppSettingsTab.tsx";
import ProfileTab from "./profile-tab/ProfileTab.tsx";
import OrganizationGroupsTab from "./organization-groups-tab/OrganizationGroupsTab.tsx";
import OrganizationPeopleTab from "./organization-people-tab/OrganizationPeopleTab.tsx";
import {useAuth} from "../../context/AuthContext.tsx";
import {AppUserRole} from "../models/models.tsx";
import OrganizationPairingTab from "./organization-pairing-tab/OrganizationPairingTab.tsx";

const Settings = () =>
{
    const {appUser, appUserPersonOrganization} = useAuth();
    const styles = useSettingsStyles();
    const [selectedValue, setSelectedValue] = useState<TabValue>("profile");

    const tabIds = {
        profile: "ProfileTab",
        organization: "OrganizationTab",
        appSettings: "AppSettingsTab",
        people: "PeopleTab",
        groups: "GroupsTab",
        organizationPairing: "OrganizationPairingTab",
        templates: "TemplatesTab"
    }

    const onTabSelect = (event: SelectTabEvent, data: SelectTabData) =>
    {
        setSelectedValue(data.value);
    };

    useEffect(() =>
    {
    }, [appUserPersonOrganization]);

    return (
        <>
            <MainMenu/>
            <div className={styles.container}>
                <TabList selectedValue={selectedValue}
                         onTabSelect={onTabSelect}
                         size="medium">
                    <Tab id="ProfileTab"
                         icon={<SettingsProfileTabIcon/>}
                         value={tabIds.profile}>
                        Profile
                    </Tab>
                    {(!appUserPersonOrganization || appUser?.role == AppUserRole.ORG_ADMIN) &&

                        <Tab id="OrganizationTab"
                             icon={<SettingsOrganizationTabIcon/>}
                             value={tabIds.organization}>
                            Your Organization
                        </Tab>
                    }
                    {appUser?.role == AppUserRole.ORG_ADMIN && <>

                        <Tab id="PeopleTab"
                             icon={<SettingsOrganizationPeopleTabIcon/>}
                             value={tabIds.people}>
                            Your People
                        </Tab>
                        <Tab id="GroupsTab"
                             icon={<SettingsOrganizationGroupsTabIcon/>}
                             value={tabIds.groups}>
                            Groups
                        </Tab>
                    </>
                    }
                    <Tab id="AppSettingsTab"
                         icon={<SettingsAppSettingsTabIcon/>}
                         value={tabIds.appSettings}>
                        App Settings
                    </Tab>
                    {(!appUserPersonOrganization || appUser?.role == AppUserRole.ORG_ADMIN) &&

                        <Tab id="OrganiationPairingTab"
                             icon={<PairOrgTabIcon/>}
                             value={tabIds.organizationPairing}>
                            Organization Pairing
                        </Tab>
                    }
                    {appUserPersonOrganization &&
                        <Tab id="TemplatesTab"
                             icon={<SettingsTemplatesTabIcon/>}
                             value={tabIds.templates}>
                            Templates
                        </Tab>
                    }
                </TabList>
                <div className={styles.tabs} id={"settings-tabs"}>
                    {selectedValue === tabIds.profile && <ProfileTab/>}
                    {selectedValue === tabIds.organization && <OrganizationTab/>}
                    {selectedValue === tabIds.appSettings && <AppSettingsTab/>}
                    {selectedValue === tabIds.people && <OrganizationPeopleTab/>}
                    {selectedValue === tabIds.groups &&
                        <OrganizationGroupsTab appUserPersonOrganization={appUserPersonOrganization}/>}
                    {selectedValue === tabIds.organizationPairing && <OrganizationPairingTab/>}
                    {selectedValue === tabIds.templates && <TemplatesTab/>}
                </div>
            </div>
        </>
    );
}

export default Settings;