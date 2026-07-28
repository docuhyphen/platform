import {useState} from "react";
import {
    Dropdown,
    Option,
    OptionOnSelectData,
    SelectTabData,
    SelectTabEvent,
    SelectionEvents,
    TabValue,
    Text,
} from "@fluentui/react-components";
import {useAuth} from "../../context/AuthContext.tsx";
import {Capability} from "../models/models.tsx";
import AuditEventsSection from "./audit-events-section/AuditEventsSection.tsx";
import AuditIntegritySection from "./audit-integrity-section/AuditIntegritySection.tsx";
import AuditExportsSection from "./audit-exports-section/AuditExportsSection.tsx";
import AuditWorkspaceNavigation from "./audit-workspace-navigation/AuditWorkspaceNavigation.tsx";
import AuditWorkspaceShell from "./audit-workspace-shell/AuditWorkspaceShell.tsx";
import {auditWorkspaceTabIds} from "./auditWorkspaceTabs.ts";
import {useAuditWorkspaceStyles} from "./AuditWorkspaceStyles.tsx";
import {AuditScope} from "./auditScope.ts";

type ScopeSelection = "organization" | "platform";
type NavigationMode = "tabs" | "sidebar";

interface AuditWorkspaceProps
{
    fixedScope?: ScopeSelection;
    navigationMode?: NavigationMode;
}

const AuditWorkspace = ({
    fixedScope,
    navigationMode = "tabs",
}: AuditWorkspaceProps) =>
{
    const {hasCapability, currentSession} = useAuth();
    const styles = useAuditWorkspaceStyles();
    const [selectedTab, setSelectedTab] = useState<TabValue>(auditWorkspaceTabIds.events);
    const [scopeSelection, setScopeSelection] = useState<ScopeSelection>("organization");

    const hasOrgAudit = hasCapability(Capability.ORG_AUDIT_READ);
    const hasPlatformAudit = hasCapability(Capability.APP_AUDIT_READ);
    const isAuthorized = hasOrgAudit || hasPlatformAudit;
    const organizationId = currentSession?.activeOrganizationId ?? null;
    const canSelectScope = !fixedScope && hasOrgAudit && hasPlatformAudit && organizationId !== null;
    // A user holding only one of the two capabilities has no scope to choose - fall back to
    // whichever surface their capability actually grants, ignoring the (stale-by-default) toggle.
    const effectiveScopeSelection = fixedScope ?? scopeSelection;
    const scope: AuditScope = hasOrgAudit && organizationId &&
    (!hasPlatformAudit || effectiveScopeSelection === "organization")
        ? {kind: "organization", organizationId}
        : {kind: "platform"};

    const onScopeSelect = (_event: SelectionEvents, data: OptionOnSelectData) =>
    {
        if (data.optionValue === "organization" || data.optionValue === "platform")
        {
            setScopeSelection(data.optionValue);
        }
    };
    const onTabSelect = (_event: SelectTabEvent, data: SelectTabData) =>
    {
        setSelectedTab(data.value);
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

    const selectedSection = (
        <div
            id={"audit-workspace-selected-section"}
            className={styles.tabPanel}>
            {selectedTab === auditWorkspaceTabIds.events && <AuditEventsSection scope={scope}/>}
            {selectedTab === auditWorkspaceTabIds.integrity && <AuditIntegritySection scope={scope}/>}
            {selectedTab === auditWorkspaceTabIds.exports && <AuditExportsSection scope={scope}/>}
        </div>
    );

    if (navigationMode === "sidebar")
    {
        return (
            <div
                id={"audit-workspace-container"}
                className={styles.container}>
                <AuditWorkspaceShell
                    selectedValue={selectedTab}
                    onTabSelect={onTabSelect}>
                    {selectedSection}
                </AuditWorkspaceShell>
            </div>
        );
    }

    return (
        <div
            id={"audit-workspace-container"}
            className={styles.container}>
            <Text
                id={"audit-workspace-scope-note"}
                size={200}
                className={styles.scopeNote}>
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
            <AuditWorkspaceNavigation
                idPrefix={"audit-workspace"}
                selectedValue={selectedTab}
                onTabSelect={onTabSelect}/>
            {selectedSection}
        </div>
    );
};

export default AuditWorkspace;
