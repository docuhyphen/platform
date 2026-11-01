import {useState} from "react";
import {SelectTabData, SelectTabEvent, Tab, TabList, TabValue} from "@fluentui/react-components";
import {useAuth} from "../../../../context/AuthContext";
import {OrganizationDetailedDto} from "../../../models/models.tsx";
import OrganizationDetailsTab from "../../organization-details-tab/OrganizationDetailsTab.tsx";
import OrganizationPeopleTab from "../../organization-people-tab/OrganizationPeopleTab.tsx";
import OrganizationGroupsTab from "../../organization-groups-tab/OrganizationGroupsTab.tsx";
import TrustedOrganizationsTab from "../../trusted-organizations-tab/TrustedOrganizationsTab.tsx";
import {SecuritySessionPolicySection} from "../security-session-policy-section/SecuritySessionPolicySection.tsx";
import {useOrganizationSubTabsStyles} from "./OrganizationSubTabsStyles.tsx";

interface OrganizationSubTabsProps
{
    organization: OrganizationDetailedDto;
}

const subTabIds = {
    organization: "OrganizationTab",
    people: "PeopleTab",
    groups: "GroupsTab",
    organizationTrusted: "TrustedOrganizationsTab",
    security: "SecurityTab",
};

const OrganizationSubTabs = ({organization}: OrganizationSubTabsProps) =>
{
    const styles = useOrganizationSubTabsStyles();
    const {appUserPersonOrganization} = useAuth();
    const [selectedValue, setSelectedValue] = useState<TabValue>(subTabIds.organization);

    const onTabSelect = (_event: SelectTabEvent, data: SelectTabData) =>
    {
        setSelectedValue(data.value);
    };

    return (
        <div id={"organization-tab-div-8"}
             className={styles.container}>

            <div id={"organization-tab-div-9"}
                 className={styles.tabListWrapper}>
                <TabList id={"organization-tab-tab-list-1"}
                         selectedValue={selectedValue}
                         onTabSelect={onTabSelect}
                         size="medium">
                    <Tab id="OrganizationTab"
                         value={subTabIds.organization}>
                        Details
                    </Tab>
                    <Tab id="PeopleTab"
                         value={subTabIds.people}>
                        People
                    </Tab>
                    <Tab id="GroupsTab"
                         value={subTabIds.groups}>
                        Groups/Teams
                    </Tab>
                    <Tab id="TrustedOrganizationsTab"
                         value={subTabIds.organizationTrusted}>
                        Trusted Organizations
                    </Tab>
                    <Tab id="SecurityTab"
                         value={subTabIds.security}>
                        Security
                    </Tab>
                </TabList>
            </div>

            <div className={styles.tabsContainer}
                 id={"settings-tabs"}>
                {selectedValue === subTabIds.organization && <OrganizationDetailsTab/>}
                {selectedValue === subTabIds.people && <OrganizationPeopleTab/>}
                {selectedValue === subTabIds.groups &&
                    <OrganizationGroupsTab appUserPersonOrganization={appUserPersonOrganization}/>}
                {selectedValue === subTabIds.organizationTrusted && <TrustedOrganizationsTab/>}
                {selectedValue === subTabIds.security && organization.id && (
                    <SecuritySessionPolicySection organizationId={organization.id}/>
                )}
            </div>
        </div>
    );
};

export default OrganizationSubTabs;
