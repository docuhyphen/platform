import {BadgeProps} from "@fluentui/react-components";
import {
    InformationRequestAmendmentChangeKind,
    InformationRequestAttestationState,
    InformationRequestCarryForwardDecision,
    InformationRequestContributorRole,
    InformationRequestNoticeDeliveryState,
    InformationRequestSubmissionProblemCode,
    ResponseError,
} from "../../models/models.tsx";

type BadgeColor = NonNullable<BadgeProps["color"]>;

export const problemLabels: Record<InformationRequestSubmissionProblemCode, string> = {
    [InformationRequestSubmissionProblemCode.REQUIREMENT_INCOMPLETE]: "Still needs an answer",
    [InformationRequestSubmissionProblemCode.EVIDENCE_NOT_CONFORMING]: "Its files do not meet the request yet",
    [InformationRequestSubmissionProblemCode.ATTESTATION_MISSING]: "Waiting for a confirmation",
    [InformationRequestSubmissionProblemCode.ATTESTATION_REFUSED]: "A confirmation was refused",
    [InformationRequestSubmissionProblemCode.RECONFIRMATION_REQUIRED]: "Changed by an amendment; save it again to confirm",
};

export const attestationStatePresentation: Record<InformationRequestAttestationState, {label: string; color: BadgeColor}> = {
    [InformationRequestAttestationState.SATISFIED]: {label: "Confirmed", color: "success"},
    [InformationRequestAttestationState.PENDING]: {label: "Waiting", color: "informative"},
    [InformationRequestAttestationState.REFUSED]: {label: "Refused", color: "danger"},
};

export const changeKindLabels: Record<InformationRequestAmendmentChangeKind, string> = {
    [InformationRequestAmendmentChangeKind.ADDED]: "Newly requested",
    [InformationRequestAmendmentChangeKind.REMOVED]: "No longer requested",
    [InformationRequestAmendmentChangeKind.PRESENTATION_CHANGED]: "Reworded",
    [InformationRequestAmendmentChangeKind.MEANING_CHANGED]: "Changed",
};

export const noticeStateLabels: Record<InformationRequestNoticeDeliveryState, string> = {
    [InformationRequestNoticeDeliveryState.PENDING]: "Notice pending",
};

export const carryForwardLabels: Record<InformationRequestCarryForwardDecision, string> = {
    [InformationRequestCarryForwardDecision.OFFERED]: "Previous answer offered",
    [InformationRequestCarryForwardDecision.INVALIDATED]: "Needs a new answer",
};

export const invalidationReasonLabels: Record<string, string> = {
    EVIDENCE_REQUIRES_FRESH_COLLECTION: "Files are always collected again.",
    ATTESTATION_REQUIRES_FRESH_ASSENT: "Confirmations are always given again.",
    SOURCE_NOT_ANSWERED: "This was not answered last time.",
};

export const roleLabel = (role: InformationRequestContributorRole): string =>
    role.charAt(0) + role.slice(1).toLowerCase().replace(/_/g, " ");

export const humanizedKey = (key: string): string => key.replace(/[-_]/g, " ");

export const submissionErrorMessage = (error: unknown, fallback: string): string =>
{
    if (typeof error === "string") return error;
    if (error instanceof Error) return error.message;
    return (error as ResponseError)?.errorMessage ?? fallback;
};
