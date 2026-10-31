import {
    InformationRequestEvidenceAttribute,
    InformationRequestEvidenceAttributeRequirement,
    InformationRequestEvidenceVersionDto,
    InformationRequestTemplateEvidencePolicyDto,
} from "../../models/models.tsx";
import {InformationRequestEvidenceAttributesInput} from "../../../services/informationRequestEvidenceService.ts";

export type EvidenceDetails = InformationRequestEvidenceAttributesInput;

export type EvidenceDetailKey = keyof EvidenceDetails;

export interface CapturedEvidenceDetail {
    key: EvidenceDetailKey;
    elementSuffix: string;
    label: string;
    required: boolean;
    kind: "text" | "date";
    maxLength?: number;
    acceptedValues: string[];
}

interface DetailDefinition {
    key: EvidenceDetailKey;
    elementSuffix: string;
    label: string;
    kind: "text" | "date";
    maxLength?: number;
    accepted?: InformationRequestEvidenceAttribute;
    requirement: (policy: InformationRequestTemplateEvidencePolicyDto) => InformationRequestEvidenceAttributeRequirement;
}

type DetailSource = Partial<Record<EvidenceDetailKey, string | null>>;

const TEXT_LIMIT = 255;

const definitions: DetailDefinition[] = [
    {
        key: "issuer",
        elementSuffix: "issuer",
        label: "Issuer",
        kind: "text",
        maxLength: TEXT_LIMIT,
        accepted: InformationRequestEvidenceAttribute.ISSUER,
        requirement: policy => policy.issuerRequirement,
    },
    {
        key: "jurisdiction",
        elementSuffix: "jurisdiction",
        label: "Jurisdiction",
        kind: "text",
        maxLength: 64,
        accepted: InformationRequestEvidenceAttribute.JURISDICTION,
        requirement: policy => policy.jurisdictionRequirement,
    },
    {
        key: "language",
        elementSuffix: "language",
        label: "Language",
        kind: "text",
        maxLength: 35,
        accepted: InformationRequestEvidenceAttribute.LANGUAGE,
        requirement: policy => policy.languageRequirement,
    },
    {
        key: "issuedOn",
        elementSuffix: "issued-on",
        label: "Issue date",
        kind: "date",
        requirement: policy => policy.issueDateRequirement
    },
    {
        key: "expiresOn",
        elementSuffix: "expires-on",
        label: "Expiry date",
        kind: "date",
        requirement: policy => policy.expiryDateRequirement
    },
    {
        key: "coverageStartsOn",
        elementSuffix: "coverage-starts-on",
        label: "Covers from",
        kind: "date",
        requirement: policy => policy.coveragePeriodRequirement,
    },
    {
        key: "coverageEndsOn",
        elementSuffix: "coverage-ends-on",
        label: "Covers until",
        kind: "date",
        requirement: policy => policy.coveragePeriodRequirement,
    },
    {
        key: "certificationReference",
        elementSuffix: "certification-reference",
        label: "Certification reference",
        kind: "text",
        maxLength: TEXT_LIMIT,
        requirement: policy => policy.certificationRequirement,
    },
    {
        key: "signatureReference",
        elementSuffix: "signature-reference",
        label: "Signature reference",
        kind: "text",
        maxLength: TEXT_LIMIT,
        requirement: policy => policy.signatureRequirement,
    },
];

export const capturedEvidenceDetails = (policy?: InformationRequestTemplateEvidencePolicyDto | null): CapturedEvidenceDetail[] => {
    if (!policy) return [];
    return definitions
        .filter(definition => definition.requirement(policy) !== InformationRequestEvidenceAttributeRequirement.NOT_CAPTURED)
        .map(definition => ({
            key: definition.key,
            elementSuffix: definition.elementSuffix,
            label: definition.label,
            required: definition.requirement(policy) === InformationRequestEvidenceAttributeRequirement.REQUIRED,
            kind: definition.kind,
            maxLength: definition.maxLength,
            acceptedValues: policy.acceptedValues
                .filter(accepted => accepted.attribute === definition.accepted)
                .map(accepted => accepted.acceptedValue),
        }));
};

const pickDetails = (captured: CapturedEvidenceDetail[], source?: DetailSource): EvidenceDetails =>
    captured.reduce<EvidenceDetails>((picked, detail) => {
        const value = source?.[detail.key]?.trim();
        return value ? {...picked, [detail.key]: value} : picked;
    }, {});

export const detailsOfVersion = (
    captured: CapturedEvidenceDetail[],
    version?: InformationRequestEvidenceVersionDto,
): EvidenceDetails => pickDetails(captured, version);

export const submittedDetails = (captured: CapturedEvidenceDetail[], details: EvidenceDetails): EvidenceDetails =>
    pickDetails(captured, details);

export const evidenceDetailsProblem = (captured: CapturedEvidenceDetail[], details: EvidenceDetails): string | null => {
    const values = submittedDetails(captured, details);
    if ((values.coverageStartsOn === undefined) !== (values.coverageEndsOn === undefined))
        return "State both the start and the end of the coverage period.";
    if (values.coverageStartsOn && values.coverageEndsOn && values.coverageEndsOn < values.coverageStartsOn)
        return "The coverage period cannot end before it starts.";
    if (values.issuedOn && values.expiresOn && values.expiresOn < values.issuedOn)
        return "The expiry date cannot be before the issue date.";
    return null;
};

export const evidenceDetailsComplete = (captured: CapturedEvidenceDetail[], details: EvidenceDetails): boolean => {
    const values = submittedDetails(captured, details);
    return captured.every(detail => !detail.required || values[detail.key] !== undefined) &&
        evidenceDetailsProblem(captured, details) === null;
};

export const statedEvidenceDetails = (version: InformationRequestEvidenceVersionDto): string[] =>
    [
        version.issuer && `Issuer: ${version.issuer}`,
        version.jurisdiction && `Jurisdiction: ${version.jurisdiction}`,
        version.language && `Language: ${version.language}`,
        version.issuedOn && `Issued on ${version.issuedOn}`,
        version.expiresOn && `Expires on ${version.expiresOn}`,
        version.coverageStartsOn && version.coverageEndsOn && `Covers ${version.coverageStartsOn} to ${version.coverageEndsOn}`,
        version.certificationReference && `Certification: ${version.certificationReference}`,
        version.signatureReference && `Signature: ${version.signatureReference}`,
    ].filter((detail): detail is string => Boolean(detail));
