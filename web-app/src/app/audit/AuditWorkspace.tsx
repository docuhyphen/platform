import {useState} from "react";
import {Dropdown, Option, OptionOnSelectData, SelectionEvents, Tab, TabList, TabValue, Text} from "@fluentui/react-components";
import {useAuth} from "../../context/AuthContext.tsx";
import {Capability} from "../models/models.tsx";
import AuditEventsSection from "./audit-events-section/AuditEventsSection.tsx";
import AuditIntegritySection from "./audit-integrity-section/AuditIntegritySection.tsx";
import AuditExportsSection from "./audit-exports-section/AuditExportsSection.tsx";
import {useAuditWorkspaceStyles} from "./AuditWorkspaceStyles.tsx";
import {AuditScope} from "./auditScope.ts";

type ScopeSelection = "organization" | "platform";

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
    const {hasCapability, currentSession} = useAuth();
    const styles = useAuditWorkspaceStyles();
    const [selectedTab, setSelectedTab] = useState<TabValue>(tabIds.events);
    const [scopeSelection, setScopeSelection] = useState<ScopeSelection>("organization");

    const hasOrgAudit = hasCapability(Capability.ORG_AUDIT_READ);
    const hasPlatformAudit = hasCapability(Capability.APP_AUDIT_READ);
    const isAuthorized = hasOrgAudit || hasPlatformAudit;
    const organizationId = currentSession?.activeOrganizationId ?? null;
    const canSelectScope = hasOrgAudit && hasPlatformAudit && organizationId !== null;
    // A user holding only one of the two capabilities has no scope to choose - fall back to
    // whichever surface their capability actually grants, ignoring the (stale-by-default) toggle.
    const scope: AuditScope = hasOrgAudit && organizationId && (!hasPlatformAudit || scopeSelection === "organization")
        ? {kind: "organization", organizationId}
        : {kind: "platform"};

    const onScopeSelect = (_event: SelectionEvents, data: OptionOnSelectData) =>
    {
        if (data.optionValue === "organization" || data.optionValue === "platform")
        {
            setScopeSelection(data.optionValue);
        }
    };

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

            {canSelectScope && (
                <Dropdown
                    id={"audit-workspace-scope-selector"}
                    className={styles.scopeSelector}
                    appearance={"outline"}
                    selectedOptions={[scopeSelection]}
                    value={scopeSelection === "organization" ? "Organization" : "Platform"}
                    onOptionSelect={onScopeSelect}
                >
                    <Option id={"audit-workspace-scope-option-organization"} value={"organization"}>
                        Organization
                    </Option>
                    <Option id={"audit-workspace-scope-option-platform"} value={"platform"}>
                        Platform
                    </Option>
                </Dropdown>
            )}

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
