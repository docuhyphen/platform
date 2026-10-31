import {BadgeProps} from "@fluentui/react-components";
import {
    InformationRequestAmendmentChangeKind,
    InformationRequestAttestationState,
    InformationRequestAttestingPartyDto,
    InformationRequestCarryForwardDecision,
    InformationRequestContributorRole,
    InformationRequestNoticeDeliveryState,
    InformationRequestSubmissionGroupProblemDto,
    InformationRequestSubmissionProblemCode,
    InformationRequestSubmissionStageDto,
    ResponseError,
} from "../../models/models.tsx";
import {PendingWorkBlocker} from "../shared/respondentPendingWork.ts";

type BadgeColor = NonNullable<BadgeProps["color"]>;

export const pendingWorkLabels: Record<PendingWorkBlocker, string> = {
    saving: "Your answers are still being saved. You can submit once they are saved.",
    stale: "Your answers changed somewhere else. Review your changes and save them before you submit.",
    failed: "Your latest answers have not been saved. Save them before you submit.",
    sending: "A file is still being sent or changed. You can submit once it finishes.",
    kept: "A file was not sent. Retry or discard it before you submit.",
};

export const problemLabels: Record<InformationRequestSubmissionProblemCode, string> = {
    [InformationRequestSubmissionProblemCode.REQUIREMENT_INCOMPLETE]: "Still needs an answer",
    [InformationRequestSubmissionProblemCode.EVIDENCE_NOT_CONFORMING]: "Its files do not meet the request yet",
    [InformationRequestSubmissionProblemCode.ATTESTATION_MISSING]: "Waiting for a confirmation",
    [InformationRequestSubmissionProblemCode.ATTESTATION_REFUSED]: "A confirmation was refused",
    [InformationRequestSubmissionProblemCode.RECONFIRMATION_REQUIRED]: "Changed by an amendment; save it again to confirm",
    [InformationRequestSubmissionProblemCode.STRUCTURED_VALIDATION_FAILED]: "An answer does not pass the request's checks",
};

export const attestationStatePresentation: Record<InformationRequestAttestationState, {
    label: string;
    color: BadgeColor
}> = {
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
    [InformationRequestNoticeDeliveryState.CLAIMED]: "Notice being prepared",
    [InformationRequestNoticeDeliveryState.RENDERED]: "Notice ready to send",
    [InformationRequestNoticeDeliveryState.RETRYING]: "Notice delivery retrying",
    [InformationRequestNoticeDeliveryState.DELIVERED]: "Notice delivered",
    [InformationRequestNoticeDeliveryState.FAILED]: "Notice delivery failed",
    [InformationRequestNoticeDeliveryState.UNDELIVERABLE]: "Notice undeliverable",
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

export const attestingPartyLabel = (party: InformationRequestAttestingPartyDto): string =>
    [
        roleLabel(party.role),
        ...(party.throughGroup ? ["through a group"] : []),
        ...(party.delegated ? ["with delegated authority"] : []),
    ].join(", ");

export const humanizedKey = (key: string): string => key.replace(/[-_]/g, " ");

const entryCount = (count: number): string => `${count} ${count === 1 ? "entry" : "entries"}`;

export const groupProblemLabel = (problem: InformationRequestSubmissionGroupProblemDto): string => {
    const parentGroupKey = problem.parentOccurrencePath?.split("/").pop()?.replace(/\[\d+]$/, "");
    return parentGroupKey
        ? `${humanizedKey(problem.groupKey)} (in ${humanizedKey(parentGroupKey)})`
        : humanizedKey(problem.groupKey);
};

export const groupProblemDetail = (problem: InformationRequestSubmissionGroupProblemDto): string => {
    const added = `${entryCount(problem.occurrenceCount)} added`;
    return problem.occurrenceCount < problem.minOccurrences
        ? `Needs at least ${entryCount(problem.minOccurrences)}; ${added}`
        : `Allows at most ${entryCount(problem.maxOccurrences ?? problem.occurrenceCount)}; ${added}`;
};

export const stageOptionLabel = (stage: InformationRequestSubmissionStageDto): string => {
    if (stage.correctionOpen) return `${humanizedKey(stage.stageKey)} (returned for correction)`;
    return stage.submitted ? `${humanizedKey(stage.stageKey)} (submitted)` : humanizedKey(stage.stageKey);
};

const PARENT_STATE_SUBMISSION_MESSAGE = "The Exchange holding this request is not accepting submissions now, for example " +
    "because it has not been accepted yet or it has ended.";

export const submissionErrorMessage = (error: unknown, fallback: string): string =>
{
    if (typeof error === "string") return error;
    if (error instanceof Error) return error.message;
    const refusal = error as ResponseError | undefined;
    if (refusal?.reasonCode === "INFORMATION_REQUEST_PARENT_STATE_INVALID") return PARENT_STATE_SUBMISSION_MESSAGE;
    return refusal?.errorMessage ?? fallback;
};
