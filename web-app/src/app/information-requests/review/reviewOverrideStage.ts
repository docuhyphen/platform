import {
    InformationRequestReviewDto,
    InformationRequestReviewItemStanding,
    InformationRequestReviewStageStandingDto,
    InformationRequestReviewStageState,
    InformationRequestReviewState,
} from "../../models/models.tsx";

const OPEN_REVIEW_STATES = new Set<InformationRequestReviewState>([
    InformationRequestReviewState.PENDING,
    InformationRequestReviewState.IN_REVIEW,
]);

export const reviewIsOpen = (review: InformationRequestReviewDto): boolean => OPEN_REVIEW_STATES.has(review.review.state);

export const overrideStageFor = (
    review: InformationRequestReviewDto,
    submissionItemId: string,
): InformationRequestReviewStageStandingDto | undefined =>
    review.canManage && reviewIsOpen(review)
        ? review.stages.find(stage =>
            stage.overridePermitted &&
            stage.state === InformationRequestReviewStageState.OPEN &&
            stage.items.some(standing =>
                standing.submissionItemId === submissionItemId &&
                standing.standing !== InformationRequestReviewItemStanding.DECIDED))
        : undefined;
