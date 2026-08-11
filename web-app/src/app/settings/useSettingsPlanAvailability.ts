import {useMemo} from "react";
import {Capability, PlanCode, PlanFeature, SubscriptionOwnerType} from "../models/models.tsx";
import {useAuth} from "../../context/AuthContext.tsx";
import {useCurrentSubscription} from "../../hooks/subscription/useCurrentSubscription.ts";
import {usePlanFeature} from "../../hooks/subscription/usePlanFeature.ts";
import {tabIds} from "./settingsTabs.ts";

export const useSettingsPlanAvailability = () =>
{
    const {appUserPersonOrganization, currentSession, hasCapability} = useAuth();
    const subscription = useCurrentSubscription();
    const documents = usePlanFeature(PlanFeature.DOCUMENT_LIBRARY_USE);
    const blueprints = usePlanFeature(PlanFeature.BLUEPRINT_USE);
    const fields = usePlanFeature(PlanFeature.BUSINESS_FIELDS_AND_SCHEMAS);
    const workflows = usePlanFeature(PlanFeature.WORKFLOW_AUTOMATION);
    const variables = usePlanFeature(PlanFeature.VARIABLES_AND_SEQUENCES);
    const administration = usePlanFeature(PlanFeature.ORGANIZATION_ADMINISTRATION);
    const audit = usePlanFeature(PlanFeature.AUDIT_GOVERNANCE);
    const hasOrg = appUserPersonOrganization?.isActive === true
        || (currentSession?.availableOrganizations?.length ?? 0) > 0
        || subscription?.ownerType === SubscriptionOwnerType.ORGANIZATION;
    const canManageOrganization = hasOrg && hasCapability(Capability.ORG_POLICY_MANAGE);
    const canSeeBillingTab = !hasOrg || hasCapability(Capability.ORG_BILLING_MANAGE);
    const canSeeOrganizationAdminTab = !hasOrg || (
        canManageOrganization && administration.isDiscoverable
    );
    const canSeeAuditTab = hasCapability(Capability.APP_AUDIT_READ) || (
        hasCapability(Capability.ORG_AUDIT_READ) && audit.isDiscoverable
    );
    const canDiscoverWorkflows = subscription?.planCode === PlanCode.PERSONAL
        ? false
        : workflows.isDiscoverable;
    const visibleTabs = useMemo(() => new Set<string>([
        tabIds.profile,
        tabIds.myGroups,
        tabIds.linkedAccounts,
        tabIds.sessions,
        tabIds.appSettings,
        ...(canSeeBillingTab ? [tabIds.organizationBilling] : []),
        ...(documents.isDiscoverable ? [tabIds.documents] : []),
        ...(blueprints.isDiscoverable ? [tabIds.blueprints] : []),
        ...(canManageOrganization && fields.isDiscoverable ? [tabIds.fields] : []),
        ...(canDiscoverWorkflows ? [tabIds.workflows, tabIds.communications] : []),
        ...(variables.isDiscoverable ? [tabIds.variables] : []),
        ...(hasOrg && variables.isDiscoverable ? [tabIds.sequences] : []),
        ...(canSeeOrganizationAdminTab ? [tabIds.organization] : []),
        ...(canSeeAuditTab ? [tabIds.audit] : []),
    ]), [
        blueprints.isDiscoverable,
        canDiscoverWorkflows,
        canManageOrganization,
        canSeeBillingTab,
        canSeeAuditTab,
        canSeeOrganizationAdminTab,
        documents.isDiscoverable,
        fields.isDiscoverable,
        hasOrg,
        variables.isDiscoverable,
    ]);

    return {
        hasOrg,
        canManageOrganization,
        canSeeBillingTab,
        canSeeOrganizationAdminTab,
        canSeeAuditTab,
        canUseDocumentLibrary: documents.isDiscoverable,
        canUseBlueprints: blueprints.isDiscoverable,
        canUseBusinessFields: fields.isDiscoverable,
        canUseWorkflows: canDiscoverWorkflows,
        canUseVariables: variables.isDiscoverable,
        visibleTabs,
    };
};
