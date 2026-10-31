import {
    InformationRequestAcceptedFactDto,
    InformationRequestPartyDto,
    InformationRequestReviewDto,
    InformationRequestReviewOutcome,
    InformationRequestReviewState,
    InformationRequestSubmissionPackageDto,
} from "../../../models/models.tsx";
import {PromotableAnswer, promotableAnswers} from "./outcomeLabels.ts";

export interface PromotionCandidate {
    answer: PromotableAnswer;
    blockedReason: string | null;
    promotedPurposes: string[];
}

export const NO_SINGLE_SUBJECT = "Promotion needs the request to name a single subject.";
export const SUBJECT_NAMED_LATER = "The subject was named after this answer was submitted, so it cannot be promoted.";
export const REVIEW_NOT_ACCEPTED = "Its review has not accepted it yet.";
export const SUBJECT_RESTRICTED = "Processing for this subject is restricted, so no fact can be promoted.";
export const ANSWER_CORRECTED = "This answer was corrected, so it cannot be promoted.";

const ACCEPTED_STATES = new Set([InformationRequestReviewState.SATISFIED, InformationRequestReviewState.SATISFIED_WITH_EXCEPTION]);
const PASSING_OUTCOMES = new Set([
    InformationRequestReviewOutcome.SATISFIED,
    InformationRequestReviewOutcome.SATISFIED_WITH_EXCEPTION,
    InformationRequestReviewOutcome.WAIVED,
]);

export const namedSubjects = (parties: InformationRequestPartyDto[]): InformationRequestPartyDto[] =>
    parties.filter(party => party.active && Boolean(party.subjectIdentityRefId));

const latestReviewOf = (reviews: InformationRequestReviewDto[], packageId: string): InformationRequestReviewDto | undefined =>
    reviews
        .filter(candidate => candidate.review.packageId === packageId)
        .sort((first, second) => second.review.reviewNumber - first.review.reviewNumber)[0];

const reviewAccepts = (reviews: InformationRequestReviewDto[] | null, answer: PromotableAnswer): boolean => {
    if (reviews === null) return true;
    const latest = latestReviewOf(reviews, answer.packageId);
    if (!latest || !ACCEPTED_STATES.has(latest.review.state)) return false;
    const outcome = latest.items.find(item => item.submissionItemId === answer.item.id)?.finalOutcome;
    return !outcome || PASSING_OUTCOMES.has(outcome);
};

const blockOf = (
    answer: PromotableAnswer,
    submission: InformationRequestSubmissionPackageDto | undefined,
    subjects: InformationRequestPartyDto[],
    reviews: InformationRequestReviewDto[] | null,
    correctedItemIds: ReadonlySet<string>,
): string | null => {
    if (new Set(subjects.map(party => party.subjectIdentityRefId)).size !== 1) return NO_SINGLE_SUBJECT;
    if (subjects.some(party => party.processingRestricted)) return SUBJECT_RESTRICTED;
    const namedAt = Math.min(...subjects.map(party => party.assignedAt));
    if (submission && namedAt > submission.submittedAt) return SUBJECT_NAMED_LATER;
    if (correctedItemIds.has(answer.item.id)) return ANSWER_CORRECTED;
    if (submission?.reviewRequired && !reviewAccepts(reviews, answer)) return REVIEW_NOT_ACCEPTED;
    return null;
};

export const promotionCandidates = (
    packages: InformationRequestSubmissionPackageDto[],
    facts: InformationRequestAcceptedFactDto[],
    parties: InformationRequestPartyDto[],
    reviews: InformationRequestReviewDto[] | null,
    correctedItemIds: ReadonlySet<string> = new Set(),
): PromotionCandidate[] => {
    const subjects = namedSubjects(parties);
    return promotableAnswers(packages).map(answer => ({
        answer,
        blockedReason: blockOf(answer, packages.find(candidate => candidate.id === answer.packageId), subjects, reviews, correctedItemIds),
        promotedPurposes: facts
            .filter(fact => fact.sourceSubmissionItemId === answer.item.id && !fact.revoked && !fact.supersededByFactId)
            .map(fact => fact.purposeKey),
    }));
};
