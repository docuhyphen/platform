import * as React from "react";
import {SelectTabData, SelectTabEvent, Tab, TabList, TabValue, Text,} from "@fluentui/react-components";
import {useSettingsStyles} from "./SettingsStyles.tsx";
import MainMenu from "../components/MainMenu.tsx";
import OrganizationTab from "./organization-tab/OrganizationTab.tsx";
import {
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
import OrganizationPeopleTab from "./organization-peopls-tab/OrganizationPeopleTab.tsx";
import {useState} from "react";

const Settings = () =>
{
    const styles = useSettingsStyles();
    const [selectedValue, setSelectedValue] = useState<TabValue>("profile");

    const onTabSelect = (event: SelectTabEvent, data: SelectTabData) =>
    {
        setSelectedValue(data.value);
    };

    return (
        <>
            <MainMenu/>
            <div className={styles.container}>
                <TabList selectedValue={selectedValue} onTabSelect={onTabSelect} size="medium">
                    <Tab id="ProfileTab"
                         icon={<SettingsProfileTabIcon/>}
                         value="profile">
                        Profile
                    </Tab>
                    <Tab id="OrganizationTab"
                         icon={<SettingsOrganizationTabIcon/>}
                         value="organization">
                        Your Organization
                    </Tab>
                    <Tab id="PeopleTab"
                         icon={<SettingsOrganizationPeopleTabIcon/>}
                         value="people">
                        Your People
                    </Tab>
                    <Tab id="GroupsTab"
                         icon={<SettingsOrganizationGroupsTabIcon/>}
                         value="groups">
                        Groups
                    </Tab>
                    <Tab id="AppSettingsTab"
                         icon={<SettingsAppSettingsTabIcon/>}
                         value="appSettings">
                        App Settings
                    </Tab>
                    <Tab id="TemplatesTab"
                         icon={<SettingsTemplatesTabIcon/>}
                         value="templates">
                        Templates
                    </Tab>
                </TabList>
                <div className={styles.tabs} id={"settings-tabs"}>
                    {selectedValue === "profile" && <ProfileTab/>}
                    {selectedValue === "organization" && <OrganizationTab/>}
                    {selectedValue === "appSettings" && <AppSettingsTab/>}
                    {selectedValue === "people" && <OrganizationPeopleTab/>}
                    {selectedValue === "groups" && <OrganizationGroupsTab/>}
                    {selectedValue === "templates" && <TemplatesTab/>}
                </div>
            </div>
        </>
    );
}

export default Settings;