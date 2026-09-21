import {SelectTabData, SelectTabEvent, TabValue,} from "@fluentui/react-components";
import {SettingsTabIds} from "../settings-tab-content/SettingsTabContent.tsx";
import SettingsMenuTabs from "./SettingsMenuTabs.tsx";

export interface SettingsMenuProps
{
    selectedValue: TabValue;
    tabIds: SettingsTabIds;
    hasOrg: boolean;
    canManageOrganization: boolean | undefined;
    canSeeBillingTab: boolean;
    canSeeOrganizationAdminTab: boolean | undefined;
    canSeeAuditTab: boolean;
    canUseDocumentLibrary: boolean;
    canUseBlueprints: boolean;
    canUseBusinessFields: boolean;
    canUseInformationRequests: boolean;
    canUseWorkflows: boolean;
    canUseVariables: boolean;
    onTabSelect: (event: SelectTabEvent, data: SelectTabData) => void;
}

const SettingsMenu = ({
                          selectedValue,
                          tabIds,
                          hasOrg,
                          canManageOrganization,
                          canSeeBillingTab,
                          canSeeOrganizationAdminTab,
                          canSeeAuditTab,
                          canUseDocumentLibrary,
                          canUseBlueprints,
                          canUseBusinessFields,
                          canUseInformationRequests,
                          canUseWorkflows,
                          canUseVariables,
                          onTabSelect,
                      }: SettingsMenuProps) =>
{
    return (
        <div id={"settings-menu-content"}>
            <SettingsMenuTabs
                selectedValue={selectedValue}
                tabIds={tabIds}
                hasOrg={hasOrg}
                canManageOrganization={canManageOrganization}
                canSeeBillingTab={canSeeBillingTab}
                canSeeOrganizationAdminTab={canSeeOrganizationAdminTab}
                canSeeAuditTab={canSeeAuditTab}
                canUseDocumentLibrary={canUseDocumentLibrary}
                canUseBlueprints={canUseBlueprints}
                canUseBusinessFields={canUseBusinessFields}
                canUseInformationRequests={canUseInformationRequests}
                canUseWorkflows={canUseWorkflows}
                canUseVariables={canUseVariables}
                onTabSelect={onTabSelect}
            />
        </div>
    );
};

export default SettingsMenu;
