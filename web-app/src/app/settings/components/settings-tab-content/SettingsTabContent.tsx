import {TabValue} from "@fluentui/react-components";
import BlueprintsTab from "../../blueprints-tab/BlueprintsTab.tsx";
import WorkflowsTab from "../../workflows-tab/WorkflowsTab.tsx";
import AppSettingsTab from "../../app-settings-tab/AppSettingsTab.tsx";
import ProfileTab from "../../profile-tab/ProfileTab.tsx";
import OrganizationGroupsTab from "../../organization-groups-tab/OrganizationGroupsTab.tsx";
import OrganizationPeopleTab from "../../organization-people-tab/OrganizationPeopleTab.tsx";
import LinkedAccountsTab from "../../linked-accounts-tab/LinkedAccountsTab.tsx";
import SessionsTab from "../../sessions-tab/SessionsTab.tsx";
import MyGroupsTab from "../../my-groups-tab/MyGroupsTab.tsx";
import AppAdminsTab from "../../app-admins-tab/AppAdminsTab.tsx";
import OrganizationTab from "../../organization-tab/OrganizationTab.tsx";
import OrganizationSequencesTab from "../../organization-sequences-tab/OrganizationSequencesTab.tsx";
import VariablesTab from "../../variables-tab/VariablesTab.tsx";
import FieldsTab from "../../fields-tab/FieldsTab.tsx";
import CommunicationsTab from "../../communications-tab/CommunicationsTab.tsx";
import DocumentLibraryTab from "../../document-library-tab/DocumentLibraryTab.tsx";
import BillingTab from "../../billing-tab/BillingTab.tsx";
import AuditWorkspace from "../../../audit/AuditWorkspace.tsx";
import {useAuth} from "../../../../context/AuthContext.tsx";
import {useSettingsStyles} from "../../SettingsStyles.tsx";
import SettingsPageTransition, {
    SettingsPageTransitionDirection,
} from "../settings-page-transition/SettingsPageTransition.tsx";

export interface SettingsTabIds
{
    profile: string;
    linkedAccounts: string;
    sessions: string;
    organization: string;
    organizationBilling: string;
    appSettings: string;
    people: string;
    groups: string;
    organizationTrusted: string;
    blueprints: string;
    myGroups: string;
    appAdmins: string;
    workflows: string;
    sequences: string;
    variables: string;
    fields: string;
    communications: string;
    documents: string;
    audit: string;
}

interface SettingsTabContentProps
{
    selectedValue: TabValue;
    tabIds: SettingsTabIds;
    direction: SettingsPageTransitionDirection;
}

const SettingsTabContent = ({selectedValue, tabIds, direction}: SettingsTabContentProps) =>
{
    const {appUserPersonOrganization} = useAuth();
    const styles = useSettingsStyles();
    const selectedPageId = selectedValue as string;
    const managesOwnContentScroll = [
        tabIds.blueprints,
        tabIds.workflows,
        tabIds.variables,
        tabIds.fields,
        tabIds.communications,
        tabIds.documents,
        tabIds.audit,
    ].includes(selectedPageId) || (
        selectedValue === tabIds.organization && !!appUserPersonOrganization?.isActive
    );

    return (
        <SettingsPageTransition
            pageId={selectedPageId}
            direction={direction}
        >
            <div
                id={`settings-page-panel-${selectedPageId.toLowerCase()}`}
                className={managesOwnContentScroll ? styles.managedTabPanel : styles.tabPanelScroller}
            >
                {selectedValue === tabIds.profile && <ProfileTab/>}
                {selectedValue === tabIds.linkedAccounts && <LinkedAccountsTab/>}
                {selectedValue === tabIds.sessions && <SessionsTab/>}
                {selectedValue === tabIds.organization && <OrganizationTab/>}
                {selectedValue === tabIds.organizationBilling && <BillingTab/>}
                {selectedValue === tabIds.appSettings && <AppSettingsTab/>}
                {selectedValue === tabIds.myGroups && <MyGroupsTab/>}
                {selectedValue === tabIds.people && <OrganizationPeopleTab/>}
                {selectedValue === tabIds.groups &&
                    <OrganizationGroupsTab appUserPersonOrganization={appUserPersonOrganization}/>}
                {selectedValue === tabIds.appAdmins && <AppAdminsTab/>}
                {selectedValue === tabIds.blueprints && <BlueprintsTab/>}
                {selectedValue === tabIds.workflows && <WorkflowsTab/>}
                {selectedValue === tabIds.sequences && <OrganizationSequencesTab/>}
                {selectedValue === tabIds.variables && <VariablesTab/>}
                {selectedValue === tabIds.fields && <FieldsTab/>}
                {selectedValue === tabIds.communications && <CommunicationsTab/>}
                {selectedValue === tabIds.documents && <DocumentLibraryTab/>}
                {selectedValue === tabIds.audit && <AuditWorkspace/>}
            </div>
        </SettingsPageTransition>
    );
};

export default SettingsTabContent;
