import {ReactNode, useState} from "react";
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
import {useIsMobile} from "../../../utils/useMediaQuery.ts";
import AuditWorkspaceNavigation from "../audit-workspace-navigation/AuditWorkspaceNavigation.tsx";
import {auditWorkspaceTabLabels} from "../auditWorkspaceTabs.ts";
import {useAuditWorkspaceShellStyles} from "./AuditWorkspaceShellStyles.tsx";

interface AuditWorkspaceShellProps
{
    selectedValue: TabValue;
    onTabSelect: (event: SelectTabEvent, data: SelectTabData) => void;
    children: ReactNode;
}

const AuditWorkspaceShell = ({
    selectedValue,
    onTabSelect,
    children,
}: AuditWorkspaceShellProps) =>
{
    const styles = useAuditWorkspaceShellStyles();
    const isMobile = useIsMobile();
    const [drawerOpen, setDrawerOpen] = useState(false);
    const selectedLabel = auditWorkspaceTabLabels[selectedValue as string] ?? "Audit";
    const selectAndClose = (event: SelectTabEvent, data: SelectTabData) =>
    {
        onTabSelect(event, data);
        setDrawerOpen(false);
    };

    return (
        <div
            id={"audit-workspace-sidebar-layout"}
            className={styles.container}>
            <div
                id={"audit-workspace-mobile-menu-bar"}
                className={styles.mobileMenuBar}>
                <Button
                    id={"button-audit-workspace-mobile-menu"}
                    appearance={"subtle"}
                    shape={"circular"}
                    icon={<Navigation24Regular/>}
                    aria-label={"Open Platform Audit menu"}
                    onClick={() => setDrawerOpen(true)}/>
                <Text
                    id={"audit-workspace-mobile-menu-label"}
                    weight={"semibold"}>
                    {selectedLabel}
                </Text>
            </div>
            <OverlayDrawer
                id={"audit-workspace-mobile-drawer"}
                open={isMobile && drawerOpen}
                position={"start"}
                onOpenChange={(_, {open}) => setDrawerOpen(open)}>
                <DrawerHeader id={"audit-workspace-mobile-drawer-header"}>
                    <DrawerHeaderTitle id={"audit-workspace-mobile-drawer-title"}>
                        Platform Audit
                    </DrawerHeaderTitle>
                </DrawerHeader>
                <DrawerBody id={"audit-workspace-mobile-drawer-body"}>
                    <AuditWorkspaceNavigation
                        idPrefix={"audit-workspace-mobile"}
                        selectedValue={selectedValue}
                        vertical={true}
                        onTabSelect={selectAndClose}/>
                </DrawerBody>
            </OverlayDrawer>
            <div
                id={"audit-workspace-layout"}
                className={styles.layout}>
                <nav
                    id={"audit-workspace-sidebar"}
                    className={styles.sidebar}
                    aria-label={"Platform Audit sections"}>
                    <AuditWorkspaceNavigation
                        idPrefix={"audit-workspace-sidebar"}
                        selectedValue={selectedValue}
                        vertical={true}
                        onTabSelect={onTabSelect}/>
                </nav>
                <section
                    id={"audit-workspace-main-content"}
                    className={styles.content}
                    aria-label={selectedLabel}>
                    {children}
                </section>
            </div>
        </div>
    );
};

export default AuditWorkspaceShell;
