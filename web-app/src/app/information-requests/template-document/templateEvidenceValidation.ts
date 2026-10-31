import {
    InformationRequestEvidenceAttribute,
    InformationRequestEvidenceAttributeRequirement,
    InformationRequestEvidenceWaiverPolicy,
    InformationRequestRequirementType,
    InformationRequestResponseDisposition,
    InformationRequestTemplateEvidencePolicyRequest,
    InformationRequestTemplateRequirementRequest,
} from "../../models/models.tsx";
import {requirementLabel} from "./templateDraftRules.ts";

type Report = (message: string) => void;

const REQUIRED = InformationRequestEvidenceAttributeRequirement.REQUIRED;

const isSet = (value: number | null | undefined): value is number => value !== undefined && value !== null;

const askedAttributes: Partial<Record<InformationRequestEvidenceAttribute, {
    name: string;
    plural: string;
    capture: (policy: InformationRequestTemplateEvidencePolicyRequest) => InformationRequestEvidenceAttributeRequirement | undefined;
}>> = {
    [InformationRequestEvidenceAttribute.ISSUER]: {
        name: "issuer",
        plural: "issuers",
        capture: policy => policy.issuerRequirement
    },
    [InformationRequestEvidenceAttribute.JURISDICTION]: {
        name: "jurisdiction",
        plural: "jurisdictions",
        capture: policy => policy.jurisdictionRequirement,
    },
    [InformationRequestEvidenceAttribute.LANGUAGE]: {
        name: "language",
        plural: "languages",
        capture: policy => policy.languageRequirement
    },
};

const acceptedValueProblems = (label: string, policy: InformationRequestTemplateEvidencePolicyRequest, report: Report) => {
    new Set((policy.acceptedValues ?? []).map(accepted => accepted.attribute)).forEach(attribute => {
        const asked = askedAttributes[attribute];
        const capture = asked?.capture(policy) ?? InformationRequestEvidenceAttributeRequirement.NOT_CAPTURED;
        if (asked && capture === InformationRequestEvidenceAttributeRequirement.NOT_CAPTURED) {
            report(`Requirement "${label}" never asks for the ${asked.name}, so it cannot restrict which ${asked.plural} are accepted.`);
        }
    });
};

const fileProblems = (label: string, policy: InformationRequestTemplateEvidencePolicyRequest, report: Report) => {
    const minimumFiles = policy.minimumFileCount ?? 1;
    if (minimumFiles < 1) report(`Requirement "${label}" needs at least one file.`);
    if (isSet(policy.maximumFileCount) && policy.maximumFileCount < minimumFiles) {
        report(`Requirement "${label}" needs at least as many files allowed as it requires.`);
    }
    if (isSet(policy.maximumFileSizeBytes) && policy.maximumFileSizeBytes <= 0) {
        report(`Requirement "${label}" needs a positive file size limit.`);
    }
    if (isSet(policy.maximumTotalSizeBytes) && policy.maximumTotalSizeBytes <= 0) {
        report(`Requirement "${label}" needs a positive total size limit.`);
    } else if (isSet(policy.maximumTotalSizeBytes) && isSet(policy.maximumFileSizeBytes)
        && policy.maximumTotalSizeBytes < policy.maximumFileSizeBytes) {
        report(`Requirement "${label}" needs a total size limit at least as large as its file size limit.`);
    }
};

const pageProblems = (label: string, policy: InformationRequestTemplateEvidencePolicyRequest, report: Report) => {
    if (isSet(policy.minimumPageCount) && policy.minimumPageCount < 1) {
        report(`Requirement "${label}" needs a minimum of at least one page.`);
    }
    if (isSet(policy.maximumPageCount) && policy.maximumPageCount < 1) {
        report(`Requirement "${label}" needs a maximum of at least one page.`);
    } else if (isSet(policy.maximumPageCount) && policy.maximumPageCount < (policy.minimumPageCount ?? 0)) {
        report(`Requirement "${label}" needs at least as many pages allowed as it requires.`);
    }
};

const dateProblems = (label: string, policy: InformationRequestTemplateEvidencePolicyRequest, report: Report) => {
    if (isSet(policy.maximumIssueAgeDays)) {
        if (policy.maximumIssueAgeDays < 1) report(`Requirement "${label}" needs a maximum age since issue of at least one day.`);
        if (policy.issueDateRequirement !== REQUIRED) {
            report(`Requirement "${label}" limits the age since issue, so set its Issue date to Required.`);
        }
    }
    if (isSet(policy.minimumRemainingValidityDays)) {
        if (policy.minimumRemainingValidityDays < 0) {
            report(`Requirement "${label}" needs a minimum validity remaining of zero days or more.`);
        }
        if (policy.expiryDateRequirement !== REQUIRED) {
            report(`Requirement "${label}" asks for validity remaining, so set its Expiry date to Required.`);
        }
    }
    if (isSet(policy.minimumCoverageDays) && policy.minimumCoverageDays < 1) {
        report(`Requirement "${label}" needs a minimum coverage of at least one day.`);
    }
    const setsCoverage = isSet(policy.minimumCoverageDays) || policy.coverageContinuityRequired === true;
    if (setsCoverage && policy.coveragePeriodRequirement !== REQUIRED) {
        report(`Requirement "${label}" sets coverage rules, so set its Coverage period to Required.`);
    }
};

export const evidenceProblems = (requirement: InformationRequestTemplateRequirementRequest, report: Report) => {
    const label = requirementLabel(requirement);
    const policy = requirement.evidencePolicy;
    if (requirement.requirementType === InformationRequestRequirementType.DOCUMENT && !policy) {
        report(`Requirement "${label}" needs an evidence policy to judge its files by.`);
    }
    if (!policy) return;
    fileProblems(label, policy, report);
    pageProblems(label, policy, report);
    dateProblems(label, policy, report);
    acceptedValueProblems(label, policy, report);
    const permitsWaiver = (requirement.permittedDispositions ?? []).includes(InformationRequestResponseDisposition.WAIVED);
    const waiver = policy.waiverPolicy ?? InformationRequestEvidenceWaiverPolicy.NOT_PERMITTED;
    if (waiver !== InformationRequestEvidenceWaiverPolicy.NOT_PERMITTED && !permitsWaiver) {
        report(`Requirement "${label}" states a waiver rule, so it permits a waived answer.`);
    }
    if (waiver === InformationRequestEvidenceWaiverPolicy.NOT_PERMITTED && permitsWaiver) {
        report(`Requirement "${label}" permits a waived answer, so its evidence policy states the waiver rule that reaches it.`);
    }
};
