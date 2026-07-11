import {useState} from "react";
import {Tab, TabList, TabValue, Text} from "@fluentui/react-components";
import {useAuth} from "../../context/AuthContext.tsx";
import {Capability} from "../models/models.tsx";
import AuditEventsSection from "./audit-events-section/AuditEventsSection.tsx";
import AuditIntegritySection from "./audit-integrity-section/AuditIntegritySection.tsx";
import AuditExportsSection from "./audit-exports-section/AuditExportsSection.tsx";
import {useAuditWorkspaceStyles} from "./AuditWorkspaceStyles.tsx";
import {AuditScope} from "./auditScope.ts";

const tabIds = {
    events: "AuditEventsTab",
    integrity: "AuditIntegrityTab",
    exports: "AuditExportsTab",
};

/**
 * Auditor workspace rendered inside the Settings
 * "Audit" tab rather than a standalone top-level route, since the audit workspace is an
 * admin/compliance surface gated the same way as the other Settings administration tabs.
 */
const AuditWorkspace = () =>
{
    const {hasCapability, appUserPersonOrganization} = useAuth();
    const styles = useAuditWorkspaceStyles();
    const [selectedTab, setSelectedTab] = useState<TabValue>(tabIds.events);

    const hasOrgAudit = hasCapability(Capability.ORG_AUDIT_READ);
    const hasPlatformAudit = hasCapability(Capability.APP_AUDIT_READ);
    const isAuthorized = hasOrgAudit || hasPlatformAudit;
    const organizationId = appUserPersonOrganization?.id ?? null;
    // Prefer organization scope when both are available; platform auditors without an org
    // membership fall back to the platform-wide surfaces.
    const scope: AuditScope = hasOrgAudit && organizationId
        ? {kind: "organization", organizationId}
        : {kind: "platform"};

    if (!isAuthorized)
    {
        return (
            <div id={"audit-not-authorized"} className={styles.notAuthorized}>
                <Text weight={"semibold"}>You do not have access to the Audit workspace.</Text>
                <Text size={200} className={styles.scopeNote}>
                    Ask an organization or platform administrator to grant audit read access.
                </Text>
            </div>
        );
    }

    return (
        <div id={"audit-workspace-container"} className={styles.container}>
            <Text size={200} className={styles.scopeNote}>
                Showing {scope.kind === "organization" ? "organization" : "platform"}-scoped audit
                evidence. Sensitive fields are shown only when your access includes them.
            </Text>

            <TabList
                id={"audit-workspace-tabs"}
                selectedValue={selectedTab}
                onTabSelect={(_event, data) => setSelectedTab(data.value)}
            >
                <Tab id={"tab-audit-events"} value={tabIds.events}>Events</Tab>
                <Tab id={"tab-audit-integrity"} value={tabIds.integrity}>Integrity</Tab>
                <Tab id={"tab-audit-exports"} value={tabIds.exports}>Exports</Tab>
            </TabList>

            <div className={styles.tabPanel}>
                {selectedTab === tabIds.events && <AuditEventsSection scope={scope}/>}
                {selectedTab === tabIds.integrity && <AuditIntegritySection scope={scope}/>}
                {selectedTab === tabIds.exports && <AuditExportsSection scope={scope}/>}
            </div>
        </div>
    );
};

export default AuditWorkspace;
