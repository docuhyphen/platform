import {useState} from "react";
import {
    Button,
    DrawerBody,
    DrawerHeader,
    DrawerHeaderTitle,
    OverlayDrawer,
    SelectTabData,
    SelectTabEvent,
    TabValue,
    Text,
} from "@fluentui/react-components";
import {Navigation24Regular} from "@fluentui/react-icons";
import {useSettingsStyles} from "./SettingsStyles.tsx";
import {useAuth} from "../../context/AuthContext.tsx";
import {useIsMobile} from "../../utils/useMediaQuery.ts";
import {Capability} from '../../app/models/models.tsx';
import {SettingsPageTransitionDirection} from "./components/settings-page-transition/SettingsPageTransition.tsx";
import SettingsTabContent from "./components/settings-tab-content/SettingsTabContent.tsx";
import SettingsMenu from "./components/settings-menu/SettingsMenu.tsx";
import {settingsTabOrder, tabIds, tabLabels} from "./settingsTabs.ts";

const Settings = () =>
{
    const {appUserPersonOrganization, hasCapability} = useAuth();
    const styles = useSettingsStyles();
    const isMobile = useIsMobile();
    const [selectedValue, setSelectedValue] = useState<TabValue>(tabIds.profile);
    const [pageTransitionDirection, setPageTransitionDirection] =
        useState<SettingsPageTransitionDirection>(null);
    const [isMobileDrawerOpen, setIsMobileDrawerOpen] = useState(false);

    const onTabSelect = (_event: SelectTabEvent, data: SelectTabData) =>
    {
        const nextValue = data.value;
        const currentIndex = settingsTabOrder.indexOf(selectedValue as string);
        const nextIndex = settingsTabOrder.indexOf(nextValue as string);
        const nextDirection = currentIndex >= 0 && nextIndex >= 0 && nextIndex < currentIndex
            ? "back"
            : "forward";
        setPageTransitionDirection(nextValue === selectedValue ? null : nextDirection);
        setSelectedValue(data.value);
        setIsMobileDrawerOpen(false);
    };

    const currentTabLabel = tabLabels[selectedValue as string] ?? "Settings";
    const hasOrg = !!appUserPersonOrganization?.isActive;
    const canManageOrganization =
        appUserPersonOrganization?.isActive &&
        hasCapability(Capability.ORG_POLICY_MANAGE);
    // Show the Administration tab when the user has no org (to register) or a pending org (to
    // view status), in addition to the normal case of an active org with management capabilities.
    const canSeeOrganizationAdminTab = !appUserPersonOrganization?.isActive || canManageOrganization;
    const canSeeAuditTab = hasCapability(Capability.ORG_AUDIT_READ) || hasCapability(Capability.APP_AUDIT_READ);

    const settingsMenu = (
        <SettingsMenu
            selectedValue={selectedValue}
            tabIds={tabIds}
            hasOrg={hasOrg}
            canManageOrganization={canManageOrganization}
            canSeeOrganizationAdminTab={canSeeOrganizationAdminTab}
            canSeeAuditTab={canSeeAuditTab}
            onTabSelect={onTabSelect}
        />
    );

    return (
        <div className={styles.container} id="settings-container">

            <div className={styles.mobileMenuBar}>
                <Button
                    id={"button-settings-mobile-menu"}
                    appearance="subtle"
                    shape="circular"
                    icon={<Navigation24Regular/>}
                    aria-label="Open settings menu"
                    onClick={() => setIsMobileDrawerOpen(true)}
                />
                <Text weight="semibold">{currentTabLabel}</Text>
            </div>

            <OverlayDrawer
                open={isMobile && isMobileDrawerOpen}
                onOpenChange={(_, {open}) => setIsMobileDrawerOpen(open)}
                position="start"
            >
                <DrawerHeader>
                    <DrawerHeaderTitle>Settings</DrawerHeaderTitle>
                </DrawerHeader>
                <DrawerBody>
                    {settingsMenu}
                </DrawerBody>
            </OverlayDrawer>

            <div className={styles.layout}>

                <div className={styles.sidebarWrapper}>
                    {settingsMenu}
                </div>

                <div className={styles.tabsContainer} id="settings-tabs">
                    <SettingsTabContent
                        selectedValue={selectedValue}
                        tabIds={tabIds}
                        direction={pageTransitionDirection}
                    />
                </div>

            </div>
        </div>
    );
}

export default Settings;
