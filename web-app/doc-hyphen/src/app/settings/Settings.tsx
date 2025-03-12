import * as React from "react";
import {SelectTabData, SelectTabEvent, Switch, Tab, TabList, TabValue, Text,} from "@fluentui/react-components";
import {
    AirplaneFilled,
    AirplaneRegular,
    AirplaneTakeOffFilled,
    AirplaneTakeOffRegular,
    BookTemplateRegular, BuildingPeopleRegular, BuildingRegular,
    bundleIcon,
    PersonSettingsRegular,
    SettingsCogMultipleRegular,
    TimeAndWeatherFilled,
    TimeAndWeatherRegular,
} from "@fluentui/react-icons";
import {useSettingsStyles} from "./SettingsStyles.tsx";
import MainMenu from "../components/MainMenu.tsx";

const Airplane = bundleIcon(AirplaneFilled, AirplaneRegular);
const AirplaneTakeOff = bundleIcon(
    AirplaneTakeOffFilled,
    AirplaneTakeOffRegular
);
const TimeAndWeather = bundleIcon(TimeAndWeatherFilled, TimeAndWeatherRegular);

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
                    <Tab id="AppSettingsTab" icon={<SettingsCogMultipleRegular/>} value="appSettings">
                        App Settings
                    </Tab>
                    <Tab id="TemplatesTab" icon={<BookTemplateRegular/>} value="templates">
                        Templates
                    </Tab>
                </TabList>
                <div className={styles.panels}>
                    {selectedValue === "profile" && <div> <Text> Profile Tab</Text></div>}
                    {selectedValue === "organization" && <div><Text> Organization Tab</Text></div>}
                    {selectedValue === "appSettings" && <div><Text> App Settings Tab</Text></div>}
                    {selectedValue === "people" && <div><Text> People Tab</Text></div>}
                    {selectedValue === "templates" && <div><Text> Templates Tab</Text></div>}
                </div>
            </div>
        </>
    );
}

export default Settings;