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
import {useIsMobile} from "../../utils/useMediaQuery.ts";
import AppAdministrators from "./app-administrators/AppAdministrators.tsx";
import Organizations from "./organizations/Organizations.tsx";
import PlatformContent from "./platform-content/PlatformContent.tsx";
import PlatformAdministrationMenu from "./platform-administration-menu/PlatformAdministrationMenu.tsx";
import {
    platformAdministrationTabIds,
    platformAdministrationTabLabels,
} from "./platformAdministrationTabs.ts";
import {usePlatformAdministrationStyles} from "./PlatformAdministrationStyles.tsx";

const PlatformAdministration = () =>
{
    const styles = usePlatformAdministrationStyles();
    const isMobile = useIsMobile();
    const [selectedValue, setSelectedValue] = useState<TabValue>(
        platformAdministrationTabIds.organizations,
    );
    const [isMobileDrawerOpen, setIsMobileDrawerOpen] = useState(false);
    const selectedLabel =
        platformAdministrationTabLabels[selectedValue as string] ?? "Platform Administration";

    const onTabSelect = (_event: SelectTabEvent, data: SelectTabData) =>
    {
        setSelectedValue(data.value);
        setIsMobileDrawerOpen(false);
    };

    return (
        <main
            id={"platform-administration-workspace"}
            className={styles.container}>
            <div
                id={"platform-administration-mobile-menu-bar"}
                className={styles.mobileMenuBar}>
                <Button
                    id={"button-platform-administration-mobile-menu"}
                    appearance={"subtle"}
                    shape={"circular"}
                    icon={<Navigation24Regular/>}
                    aria-label={"Open Platform Administration menu"}
                    onClick={() => setIsMobileDrawerOpen(true)}/>
                <Text
                    id={"platform-administration-mobile-menu-label"}
                    weight={"semibold"}>
                    {selectedLabel}
                </Text>
            </div>

            <OverlayDrawer
                id={"platform-administration-mobile-drawer"}
                open={isMobile && isMobileDrawerOpen}
                onOpenChange={(_, {open}) => setIsMobileDrawerOpen(open)}
                position={"start"}>
                <DrawerHeader id={"platform-administration-mobile-drawer-header"}>
                    <DrawerHeaderTitle id={"platform-administration-mobile-drawer-title"}>
                        Platform Administration
                    </DrawerHeaderTitle>
                </DrawerHeader>
                <DrawerBody id={"platform-administration-mobile-drawer-body"}>
                    <PlatformAdministrationMenu
                        idPrefix={"platform-administration-mobile"}
                        selectedValue={selectedValue}
                        onTabSelect={onTabSelect}/>
                </DrawerBody>
            </OverlayDrawer>

            <div
                id={"platform-administration-layout"}
                className={styles.layout}>
                <nav
                    id={"platform-administration-sidebar"}
                    className={styles.sidebar}
                    aria-label={"Platform Administration sections"}>
                    <PlatformAdministrationMenu
                        idPrefix={"platform-administration"}
                        selectedValue={selectedValue}
                        onTabSelect={onTabSelect}/>
                </nav>

                <section
                    id={`platform-administration-${selectedValue}-section`}
                    className={styles.content}
                    aria-label={selectedLabel}>
                    <div
                        id={"platform-administration-section-content"}
                        className={selectedValue === platformAdministrationTabIds.platformContent
                            ? styles.managedContent
                            : styles.contentScroller}>
                        {selectedValue === platformAdministrationTabIds.organizations && <Organizations/>}
                        {selectedValue === platformAdministrationTabIds.platformContent && <PlatformContent/>}
                        {selectedValue === platformAdministrationTabIds.appAdministrators && <AppAdministrators/>}
                    </div>
                </section>
            </div>
        </main>
    );
};

export default PlatformAdministration;
