import {PlanFeature} from "../../models/models.tsx";

export const featureOverrideLabels: Record<PlanFeature, string> = {
    [PlanFeature.EXCHANGE_CREATE]: "Exchange creation",
    [PlanFeature.MULTIPLE_PARTICIPANTS]: "Multiple participants",
    [PlanFeature.BLUEPRINT_USE]: "Blueprint use",
    [PlanFeature.BLUEPRINT_MANAGE]: "Blueprint management",
    [PlanFeature.DOCUMENT_LIBRARY_USE]: "Document Library use",
    [PlanFeature.DOCUMENT_LIBRARY_MANAGE]: "Document Library management",
    [PlanFeature.DOCUMENT_COMMENTS]: "Document comments",
    [PlanFeature.DOCUMENT_VERSION_HISTORY]: "Document version history",
    [PlanFeature.ADVANCED_ACCESS_CONTROLS]: "Advanced access controls",
    [PlanFeature.VARIABLES_AND_SEQUENCES]: "Variables and sequences",
    [PlanFeature.PERSONAL_REMINDERS]: "Personal reminders",
    [PlanFeature.BUSINESS_FIELDS_AND_SCHEMAS]: "Business fields and schemas",
    [PlanFeature.WORKFLOW_AUTOMATION]: "Workflow automation",
    [PlanFeature.ORGANIZATION_ADMINISTRATION]: "Organization administration",
    [PlanFeature.AUDIT_GOVERNANCE]: "Audit governance",
    [PlanFeature.IDENTITY_AND_INTEGRATIONS]: "Identity and integrations",
    [PlanFeature.INFORMATION_REQUESTS]: "Information requests",
};

export const featureOverrideLabel = (featureCode: string): string => {
    const feature = Object.values(PlanFeature).find(candidate => candidate === featureCode);
    return feature ? featureOverrideLabels[feature] : featureCode;
};

export const INDIVIDUAL_ACCOUNT_FEATURES: PlanFeature[] = Object.values(PlanFeature)
    .filter(feature => feature !== PlanFeature.PERSONAL_REMINDERS);
