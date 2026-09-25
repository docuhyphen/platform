import {BadgeProps} from "@fluentui/react-components";
import {
    InformationRequestEvidenceConformance,
    InformationRequestEvidenceFindingCode,
    InformationRequestEvidenceFindingDto,
    InformationRequestEvidenceRequirementState,
    ResponseError,
} from "../../models/models.tsx";

type BadgeColor = NonNullable<BadgeProps["color"]>;

export const requirementStatePresentation: Record<InformationRequestEvidenceRequirementState, {label: string; color: BadgeColor}> = {
    [InformationRequestEvidenceRequirementState.NOT_PROVIDED]: {label: "No files provided", color: "subtle"},
    [InformationRequestEvidenceRequirementState.PENDING_ASSESSMENT]: {label: "Awaiting checks", color: "informative"},
    [InformationRequestEvidenceRequirementState.INCOMPLETE]: {label: "More files needed", color: "warning"},
    [InformationRequestEvidenceRequirementState.DEFICIENT]: {label: "Needs attention", color: "danger"},
    [InformationRequestEvidenceRequirementState.REVIEWABLE]: {label: "Ready for review", color: "brand"},
    [InformationRequestEvidenceRequirementState.SATISFIED]: {label: "Complete", color: "success"},
    [InformationRequestEvidenceRequirementState.WAIVED]: {label: "Waived", color: "success"},
    [InformationRequestEvidenceRequirementState.WAIVER_REQUESTED]: {label: "Waiver requested", color: "brand"},
};

export const conformancePresentation: Record<InformationRequestEvidenceConformance, {label: string; color: BadgeColor}> = {
    [InformationRequestEvidenceConformance.PENDING]: {label: "Awaiting checks", color: "informative"},
    [InformationRequestEvidenceConformance.CONFORMING]: {label: "Accepted", color: "success"},
    [InformationRequestEvidenceConformance.DEFICIENT]: {label: "Needs attention", color: "warning"},
    [InformationRequestEvidenceConformance.QUARANTINED]: {label: "Quarantined", color: "danger"},
    [InformationRequestEvidenceConformance.CORRUPT]: {label: "Unreadable", color: "danger"},
    [InformationRequestEvidenceConformance.EXPIRED]: {label: "Expired", color: "danger"},
};

const humanized = (detail?: string): string => (detail ?? "detail").toLowerCase().replace(/_/g, " ");

const findingMessages: Record<InformationRequestEvidenceFindingCode, (detail?: string) => string> = {
    [InformationRequestEvidenceFindingCode.NOT_INSPECTED]: () => "This file has not been inspected yet.",
    [InformationRequestEvidenceFindingCode.NOT_SCANNED]: () => "This file is waiting for a malware scan.",
    [InformationRequestEvidenceFindingCode.SCAN_INCOMPLETE]: () => "The malware scan did not finish and will be retried.",
    [InformationRequestEvidenceFindingCode.SCAN_NOT_PRODUCTION_ELIGIBLE]: () =>
        "This file was scanned by an engine that cannot clear it.",
    [InformationRequestEvidenceFindingCode.MALWARE_DETECTED]: () =>
        "Malware was detected. Withdraw this file and provide a clean copy.",
    [InformationRequestEvidenceFindingCode.CONTENT_OPAQUE]: () =>
        "End-to-end encrypted files cannot be checked, so they cannot satisfy this Requirement.",
    [InformationRequestEvidenceFindingCode.CONTENT_CORRUPT]: () => "This file could not be read.",
    [InformationRequestEvidenceFindingCode.CONTENT_ENCRYPTED]: () =>
        "This file is password protected. Provide a copy without a password.",
    [InformationRequestEvidenceFindingCode.FILE_TOO_LARGE]: () => "This file is larger than this Requirement allows.",
    [InformationRequestEvidenceFindingCode.CONTENT_TYPE_NOT_ACCEPTED]: () =>
        "This type of file is not accepted for this Requirement.",
    [InformationRequestEvidenceFindingCode.CONTENT_TYPE_MISMATCH]: () => "The content of this file does not match its type.",
    [InformationRequestEvidenceFindingCode.PAGE_COUNT_UNKNOWN]: () => "The number of pages could not be determined.",
    [InformationRequestEvidenceFindingCode.PAGE_COUNT_OUT_OF_RANGE]: () => "The number of pages is outside the accepted range.",
    [InformationRequestEvidenceFindingCode.ATTRIBUTE_MISSING]: detail => `A required detail is missing: ${humanized(detail)}.`,
    [InformationRequestEvidenceFindingCode.ATTRIBUTE_NOT_ACCEPTED]: detail =>
        `The ${humanized(detail)} is not one this Requirement accepts.`,
    [InformationRequestEvidenceFindingCode.ISSUED_IN_FUTURE]: () => "The issue date is in the future.",
    [InformationRequestEvidenceFindingCode.ISSUE_TOO_OLD]: () => "This file was issued too long ago.",
    [InformationRequestEvidenceFindingCode.EXPIRED]: () => "This file has expired.",
    [InformationRequestEvidenceFindingCode.VALIDITY_TOO_SHORT]: () => "This file expires too soon.",
    [InformationRequestEvidenceFindingCode.FILE_COUNT_BELOW_MINIMUM]: detail =>
        `At least ${detail ?? "one"} accepted files are needed.`,
    [InformationRequestEvidenceFindingCode.FILE_COUNT_ABOVE_MAXIMUM]: detail => `At most ${detail ?? "a few"} files are accepted.`,
    [InformationRequestEvidenceFindingCode.TOTAL_SIZE_ABOVE_MAXIMUM]: () =>
        "The files together are larger than this Requirement allows.",
    [InformationRequestEvidenceFindingCode.COVERAGE_TOO_SHORT]: () => "The files do not cover a long enough period.",
    [InformationRequestEvidenceFindingCode.COVERAGE_NOT_CONTINUOUS]: () => "The periods the files cover have gaps.",
    [InformationRequestEvidenceFindingCode.WAIVER_NOT_PERMITTED]: () => "This Requirement cannot be waived.",
};

export const findingMessage = (finding: InformationRequestEvidenceFindingDto): string =>
    findingMessages[finding.code]?.(finding.detail) ?? "This file needs attention.";

const refusalMessages: Record<string, string> = {
    INFORMATION_REQUEST_EVIDENCE_UPLOAD_UNAVAILABLE: "Evidence upload is not available right now.",
    INFORMATION_REQUEST_EVIDENCE_DUPLICATE_CONTENT: "This file has already been provided for this Requirement.",
    INFORMATION_REQUEST_EVIDENCE_CONTENT_QUARANTINED: "This file is quarantined and cannot be opened.",
    INFORMATION_REQUEST_EVIDENCE_CONTENT_NOT_RELEASED: "This file can be opened once it has passed a malware scan.",
    INFORMATION_REQUEST_EVIDENCE_PREVIEW_UNAVAILABLE: "This file cannot be previewed. Download it instead.",
    INFORMATION_REQUEST_EVIDENCE_ARTIFACT_INACTIVE: "Withdrawn files cannot be changed.",
};

export const refusalMessage = (refusal: unknown, fallback: string): string =>
{
    if (refusal && typeof refusal === "object")
    {
        const stated = refusal as ResponseError;
        if (stated.reasonCode && refusalMessages[stated.reasonCode]) return refusalMessages[stated.reasonCode];
        if (stated.errorMessage) return stated.errorMessage;
    }
    return typeof refusal === "string" && refusal ? refusal : fallback;
};

export const INLINE_PREVIEW_TYPES = new Set(["application/pdf", "image/png", "image/jpeg", "image/gif", "image/webp"]);

export const formatEvidenceSize = (bytes?: number): string =>
{
    if (bytes === undefined) return "";
    if (bytes < 1024) return `${bytes} B`;
    if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
    return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
};
