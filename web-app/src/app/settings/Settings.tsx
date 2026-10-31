import {useEffect, useState} from "react";
import {
    Button,
    DrawerBody,
    DrawerHeader,
    DrawerHeaderTitle,
    OverlayDrawer,
    SelectTabData,
    SelectTabEvent,
    Text,
} from "@fluentui/react-components";
import {Navigation24Regular} from "@fluentui/react-icons";
import {useSettingsStyles} from "./SettingsStyles.tsx";
import {useIsMobile} from "../../utils/useMediaQuery.ts";
import SettingsTabContent from "./components/settings-tab-content/SettingsTabContent.tsx";
import SettingsMenu from "./components/settings-menu/SettingsMenu.tsx";
import SettingsLeaveDialog from "./settings-leave-guard/SettingsLeaveDialog.tsx";
import {SettingsLeaveGuardContext} from "./settings-leave-guard/settingsLeaveGuard.ts";
import {useSettingsTabSelection} from "./settings-leave-guard/useSettingsTabSelection.ts";
import {settingsTabOrder, tabIds, tabLabels} from "./settingsTabs.ts";
import {useSettingsPlanAvailability} from "./useSettingsPlanAvailability.ts";

const Settings = () =>
{
    const styles = useSettingsStyles();
    const isMobile = useIsMobile();
    const requestedTab = new URLSearchParams(window.location.search).get("tab");
    const initialTab = requestedTab && settingsTabOrder.includes(requestedTab)
        ? requestedTab
        : tabIds.profile;
    const tabs = useSettingsTabSelection(initialTab);
    const {selectedValue, reset} = tabs;
    const [isMobileDrawerOpen, setIsMobileDrawerOpen] = useState(false);
    const planAvailability = useSettingsPlanAvailability();

    const onTabSelect = (_event: SelectTabEvent, data: SelectTabData) =>
    {
        tabs.select(data.value);
        setIsMobileDrawerOpen(false);
    };

    const currentTabLabel = tabLabels[selectedValue as string] ?? "Settings";
    useEffect(() =>
    {
        if (!planAvailability.visibleTabs.has(selectedValue as string)) reset(tabIds.profile);
    }, [planAvailability.visibleTabs, reset, selectedValue]);

    const settingsMenu = (
        <SettingsMenu
            selectedValue={selectedValue}
            tabIds={tabIds}
            hasOrg={planAvailability.hasOrg}
            canManageOrganization={planAvailability.canManageOrganization}
            canSeeBillingTab={planAvailability.canSeeBillingTab}
            canSeeOrganizationAdminTab={planAvailability.canSeeOrganizationAdminTab}
            canSeeAuditTab={planAvailability.canSeeAuditTab}
            canUseDocumentLibrary={planAvailability.canUseDocumentLibrary}
            canUseBlueprints={planAvailability.canUseBlueprints}
            canUseBusinessFields={planAvailability.canUseBusinessFields}
            canUseWorkflows={planAvailability.canUseWorkflows}
            canUseVariables={planAvailability.canUseVariables}
            canUseInformationRequests={planAvailability.canUseInformationRequests}
            registersOrganization={planAvailability.registersOrganization}
            onTabSelect={onTabSelect}
        />
    );

    return (
        <div className={styles.container}
             id="settings-container">

            <div id={"settings-mobile-menu-bar"}
                 className={styles.mobileMenuBar}>
                <Button
                    id={"button-settings-mobile-menu"}
                    appearance="subtle"
                    shape="circular"
                    icon={<Navigation24Regular/>}
                    aria-label="Open settings menu"
                    onClick={() => setIsMobileDrawerOpen(true)}
                />
                <Text id={"settings-current-tab-label-text"}
                      weight="semibold">{currentTabLabel}</Text>
            </div>

            <OverlayDrawer
                id={"settings-container-overlay-drawer"}
                open={isMobile && isMobileDrawerOpen}
                onOpenChange={(_, {open}) => setIsMobileDrawerOpen(open)}
                position="start"
            >
                <DrawerHeader id={"settings-container-drawer-header"}>
                    <DrawerHeaderTitle id={"settings-container-drawer-header-title"}>Settings</DrawerHeaderTitle>
                </DrawerHeader>
                <DrawerBody id={"settings-menu-drawer-body"}>
                    {settingsMenu}
                </DrawerBody>
            </OverlayDrawer>

            <div id={"settings-layout"}
                 className={styles.layout}>

                <div id={"settings-sidebar-wrapper"}
                     className={styles.sidebarWrapper}>
                    {settingsMenu}
                </div>

                <div className={styles.tabsContainer}
                     id="settings-tabs">
                    <SettingsLeaveGuardContext.Provider value={tabs.guard}>
                        <SettingsTabContent
                            selectedValue={selectedValue}
                            tabIds={tabIds}
                            direction={tabs.direction}
                        />
                    </SettingsLeaveGuardContext.Provider>
                </div>

            </div>
            <SettingsLeaveDialog open={tabs.pendingTab !== null}
                                 onLeave={tabs.leave}
                                 onStay={tabs.stay}/>
        </div>
    );
}

export default Settings;
