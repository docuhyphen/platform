import {
    InformationRequestAcceptedFactConfidence,
    InformationRequestAcceptedFactFreshness,
    InformationRequestAcceptedFactVisibility,
    InformationRequestBusinessDecisionDto,
    InformationRequestBusinessDecisionKind,
    InformationRequestCompletenessItemState,
    InformationRequestRequirementType,
    InformationRequestSubmissionItemDto,
    InformationRequestSubmissionPackageDto,
} from "../../../models/models.tsx";
import {ChoiceOption} from "../../shared/choice-select/choiceOptions.ts";

export interface PromotableAnswer
{
    packageId: string;
    packageNumber: number;
    item: InformationRequestSubmissionItemDto;
}

export const confidenceLabels: Record<InformationRequestAcceptedFactConfidence, string> = {
    [InformationRequestAcceptedFactConfidence.DECLARED]: "As submitted",
    [InformationRequestAcceptedFactConfidence.REVIEWED]: "Accepted by review",
};

export const freshnessLabels: Record<InformationRequestAcceptedFactFreshness, string> = {
    [InformationRequestAcceptedFactFreshness.CURRENT]: "Current",
    [InformationRequestAcceptedFactFreshness.EXPIRED]: "Expired",
    [InformationRequestAcceptedFactFreshness.OUTSIDE_VALID_PERIOD]: "Outside its valid period",
};

export const factVisibilityLabels: Record<InformationRequestAcceptedFactVisibility, string> = {
    [InformationRequestAcceptedFactVisibility.REQUESTING_SIDE]: "Requesting side only",
    [InformationRequestAcceptedFactVisibility.RESPONDING_PARTIES]: "Requesting side and responding parties",
};

export const decisionKindLabels: Record<InformationRequestBusinessDecisionKind, string> = {
    [InformationRequestBusinessDecisionKind.ORIGINAL]: "Original decision",
    [InformationRequestBusinessDecisionKind.RECONSIDERATION]: "Reconsideration",
    [InformationRequestBusinessDecisionKind.APPEAL]: "Appeal",
};

const holdsValue = (item: InformationRequestSubmissionItemDto): boolean =>
    item.requirementType === InformationRequestRequirementType.FIELD &&
    item.completenessState === InformationRequestCompletenessItemState.COMPLETE &&
    item.fieldValue !== undefined &&
    item.fieldValue !== null &&
    !item.fieldValueCleared;

const currentItems = (
    packages: InformationRequestSubmissionPackageDto[],
    wanted: (item: InformationRequestSubmissionItemDto) => boolean,
): PromotableAnswer[] =>
{
    const followed = new Set(packages.flatMap(submission => submission.previousPackageId ? [submission.previousPackageId] : []));
    return packages
        .filter(submission => !submission.withdrawn && !followed.has(submission.id))
        .flatMap(submission => submission.items
            .filter(wanted)
            .map(item => ({packageId: submission.id, packageNumber: submission.packageNumber, item})));
};

export const promotableAnswers = (packages: InformationRequestSubmissionPackageDto[]): PromotableAnswer[] =>
    currentItems(packages, holdsValue);

export const supportingEvidenceChoices = (
    packages: InformationRequestSubmissionPackageDto[],
    answer: PromotableAnswer,
    labelOf: (requirementId: string, requirementKey?: string) => string,
): ChoiceOption<string>[] =>
{
    const submission = packages.find(candidate => candidate.id === answer.packageId);
    if (!submission) return [];
    const supporting = new Set(submission.supportingEvidenceLinks
        .filter(link => link.supportedRequirementId === answer.item.requirementId)
        .map(link => link.supportingRequirementId));
    return submission.items
        .filter(item => supporting.has(item.requirementId))
        .flatMap(item => item.evidence
            .filter(evidence => evidence.conformance === "CONFORMING")
            .map(evidence => ({
                value: evidence.evidenceVersionId,
                label: `${labelOf(item.requirementId, item.requirementKey)}, version ${evidence.versionNumber}`,
            })));
};

export const correctableAnswers = (packages: InformationRequestSubmissionPackageDto[]): PromotableAnswer[] =>
    currentItems(packages, item => (item.fieldValue !== undefined && item.fieldValue !== null) || Boolean(item.narrative?.trim()));

export const latestDecisionIds = (decisions: InformationRequestBusinessDecisionDto[]): Set<string> =>
{
    const latest = new Map<string, InformationRequestBusinessDecisionDto>();
    decisions.forEach(decision =>
    {
        const current = latest.get(decision.owningProcessKey);
        if (!current || decision.decisionRevision > current.decisionRevision) latest.set(decision.owningProcessKey, decision);
    });
    return new Set([...latest.values()].map(decision => decision.id));
};
