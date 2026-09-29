import {
    InformationRequestPrivacyRequestDto,
    InformationRequestPrivacyRequestKind,
    InformationRequestPrivacyRequestState,
    InformationRequestPrivacyTargetOutcome,
    InformationRequestSubjectDto,
} from "../../../models/models.tsx";
import {subjectKindLabels} from "../../shared/informationRequestLabels.ts";
import {humanizedKey} from "../../submission/submissionLabels.ts";
import {shortId} from "../operationsLabels.ts";

export const privacyKindLabels: Record<InformationRequestPrivacyRequestKind, string> = {
    [InformationRequestPrivacyRequestKind.ACCESS]: "Access",
    [InformationRequestPrivacyRequestKind.EXPORT]: "Export",
    [InformationRequestPrivacyRequestKind.CORRECTION]: "Correction",
    [InformationRequestPrivacyRequestKind.RESTRICTION]: "Restriction",
    [InformationRequestPrivacyRequestKind.DELETION]: "Deletion",
};

export const privacyStateLabels: Record<InformationRequestPrivacyRequestState, string> = {
    [InformationRequestPrivacyRequestState.RECORDED]: "Recorded",
    [InformationRequestPrivacyRequestState.COMPLETED]: "Completed",
    [InformationRequestPrivacyRequestState.REFUSED]: "Refused",
};

const outcomeVerbs: Record<InformationRequestPrivacyTargetOutcome, string> = {
    [InformationRequestPrivacyTargetOutcome.EXPORTED]: "exported",
    [InformationRequestPrivacyTargetOutcome.CORRECTED]: "corrected",
    [InformationRequestPrivacyTargetOutcome.RESTRICTED]: "restricted",
    [InformationRequestPrivacyTargetOutcome.DISPOSAL_CLAIMED]: "claimed for disposal",
    [InformationRequestPrivacyTargetOutcome.REFUSED]: "refused",
};

export const subjectLabel = (subject: InformationRequestSubjectDto): string =>
{
    const references = subject.references.map(reference => `${humanizedKey(reference.identifierType)} ${reference.identifierValue}`);
    return references.length > 0
        ? `${subjectKindLabels[subject.subjectKind]}, ${references.join(", ")}`
        : `${subjectKindLabels[subject.subjectKind]} ${shortId(subject.id)}`;
};

export const targetSummary = (request: InformationRequestPrivacyRequestDto): string =>
    Object.values(InformationRequestPrivacyTargetOutcome)
        .map(outcome => ({outcome, count: request.targets.filter(target => target.outcome === outcome).length}))
        .filter(entry => entry.count > 0)
        .map(entry => `${entry.count} ${entry.count === 1 ? "request" : "requests"} ${outcomeVerbs[entry.outcome]}`)
        .join(", ");

export const recordedSentence = (request: InformationRequestPrivacyRequestDto): string =>
{
    if (request.state === InformationRequestPrivacyRequestState.REFUSED)
    {
        const reason = request.refusalDetail ?? humanizedKey((request.refusalCode ?? "no reason given").toLowerCase());
        return `The privacy request was refused: ${reason}.`;
    }
    return request.state === InformationRequestPrivacyRequestState.COMPLETED
        ? "The privacy request was recorded and completed."
        : "The privacy request was recorded.";
};
