import * as React from "react";
import {
    Button,
    Divider,
    SelectTabData,
    SelectTabEvent,
    Tab,
    TabList,
    TabValue,
    Text,
} from "@fluentui/react-components";
import {
    BookTemplateRegular,
    BuildingPeopleRegular,
    BuildingRegular,
    PeopleTeamRegular,
    PersonSettingsRegular,
    SettingsCogMultipleRegular,
} from "@fluentui/react-icons";
import {useSettingsStyles} from "./SettingsStyles.tsx";
import MainMenu from "../components/MainMenu.tsx";

const Settings = () =>
{
    const styles = useSettingsStyles();

    const [selectedValue, setSelectedValue] =
        React.useState<TabValue>("conditions");

    const onTabSelect = (event: SelectTabEvent, data: SelectTabData) =>
    {
        setSelectedValue(data.value);
    };

    return (
        <>
            <MainMenu/>
            <div className={styles.container}>
                <TabList selectedValue={selectedValue} onTabSelect={onTabSelect} size="medium">

                    <Tab id="ProfileTab" icon={<PersonSettingsRegular/>} value="profile">
                        Profile
                    </Tab>
                    <Tab id="OrganizationTab" icon={<BuildingRegular/>} value="organization">
                        Your Organization
                    </Tab>
                    <Tab id="PeopleTab" icon={<BuildingPeopleRegular/>} value="people">
                        People
                    </Tab>
                    <Tab id="GroupsTab" icon={<PeopleTeamRegular/>} value="groups">
                        Groups
                    </Tab>
                    <Tab id="AppSettingsTab" icon={<SettingsCogMultipleRegular/>} value="appSettings">
                        App Settings
                    </Tab>
                    <Tab id="TemplatesTab" icon={<BookTemplateRegular/>} value="templates">
                        Templates
                    </Tab>
                </TabList>
                <div className={styles.panels}>
                    {selectedValue === "profile" &&
                        <div>
                            <Text> Profile Tab</Text>
                            <p>
                                Notify me with email every time I sign in
                            </p>
                        </div>}
                    {selectedValue === "organization" &&
                        <div>
                            <Text> Organization Tab</Text>
                            <p>
                                Allow other users outside your organization to search for you
                            </p>
                            <Divider/>
                            <h2>
                                Pared Organizations
                            </h2>
                            <Button>
                                Find and Pair
                            </Button>
                        </div>}
                    {selectedValue === "appSettings" && <div><Text> App Settings Tab</Text>
                        <p>
                            Automatically preview documents when they are uploaded
                        </p>
                        <h1>Notifications</h1>
                        <p>Get notifications on document comments</p>
                        <p>Get notifications on document upload</p>
                    </div>}
                    {selectedValue === "people" && <div><Text> People Tab</Text></div>}
                    {selectedValue === "groups" &&
                        <div>
                            <Text> Groups Tab</Text>
                            <p> Groups can be departments, teams, or just a group of users withing a team.</p>
                            <Button>
                                Create Group
                            </Button>
                        </div>}
                    {selectedValue === "templates" && <div><Text> Templates Tab</Text></div>}
                </div>
            </div>
        </>
    );
}

export default Settings;