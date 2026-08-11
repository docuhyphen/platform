import {
    SelectTabData,
    SelectTabEvent,
    Tab,
    TabList,
    TabValue,
} from "@fluentui/react-components";
import {
    SettingsAppAdminsIcon,
    SettingsBlueprintsTabIcon,
    SettingsOrganizationTabIcon,
} from "../../components/IconBundles.tsx";
import {platformAdministrationTabIds} from "../platformAdministrationTabs.ts";

interface PlatformAdministrationMenuProps
{
    idPrefix: string;
    selectedValue: TabValue;
    onTabSelect: (event: SelectTabEvent, data: SelectTabData) => void;
}

const PlatformAdministrationMenu = ({
    idPrefix,
    selectedValue,
    onTabSelect,
}: PlatformAdministrationMenuProps) =>
{
    return (
        <TabList
            id={`${idPrefix}-menu`}
            selectedValue={selectedValue}
            appearance={"subtle-circular"}
            onTabSelect={onTabSelect}
            vertical={true}
            size={"medium"}>
            <Tab
                id={`${idPrefix}-organizations-tab`}
                icon={<SettingsOrganizationTabIcon/>}
                value={platformAdministrationTabIds.organizations}>
                Organizations
            </Tab>
            <Tab
                id={`${idPrefix}-user-subscriptions-tab`}
                icon={<SettingsAppAdminsIcon/>}
                value={platformAdministrationTabIds.userSubscriptions}>
                User Subscriptions
            </Tab>
            <Tab
                id={`${idPrefix}-content-tab`}
                icon={<SettingsBlueprintsTabIcon/>}
                value={platformAdministrationTabIds.platformContent}>
                Platform Content
            </Tab>
            <Tab
                id={`${idPrefix}-app-administrators-tab`}
                icon={<SettingsAppAdminsIcon/>}
                value={platformAdministrationTabIds.appAdministrators}>
                App Administrators
            </Tab>
        </TabList>
    );
};

export default PlatformAdministrationMenu;
