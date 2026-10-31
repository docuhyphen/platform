import {
    InformationRequestReviewAssignmentDto,
    InformationRequestReviewAssignmentState,
    InformationRequestReviewDecisionKind,
    InformationRequestReviewDto,
    InformationRequestReviewStageStandingDto,
    InformationRequestReviewStageState,
    InformationRequestReviewWorksheetDto,
} from "../../models/models.tsx";

export interface WorkableAssignment {
    worksheet: InformationRequestReviewWorksheetDto;
    assignment: InformationRequestReviewAssignmentDto;
    stage: InformationRequestReviewStageStandingDto;
}

export interface ReviewWorkScope {
    worksheet: InformationRequestReviewWorksheetDto;
    stageKey: string;
    itemIds: string[];
}

const decidedThroughAnother = (review: InformationRequestReviewDto, {assignment, stage}: WorkableAssignment): boolean =>
    review.decisions.some(decision => decision.decidedByCaller
        && decision.kind === InformationRequestReviewDecisionKind.REVIEWER
        && decision.stageKey === stage.stageKey
        && decision.assignmentId !== assignment.id);

const excludedAsEarlierReviewer = (review: InformationRequestReviewDto, {stage}: WorkableAssignment): boolean =>
    stage.excludesPriorReviewers && review.decisions.some(decision => decision.decidedByCaller
        && decision.kind !== InformationRequestReviewDecisionKind.CARRIED
        && (review.stages.find(candidate => candidate.stageKey === decision.stageKey)?.position ?? stage.position) < stage.position);

const open = (review: InformationRequestReviewDto, workable: WorkableAssignment): boolean =>
    workable.assignment.callerIsReviewer
    && workable.assignment.state === InformationRequestReviewAssignmentState.ACTIVE
    && !workable.assignment.decidedAt
    && workable.stage.state === InformationRequestReviewStageState.OPEN
    && !decidedThroughAnother(review, workable)
    && !excludedAsEarlierReviewer(review, workable);

export const workableAssignments = (review: InformationRequestReviewDto): WorkableAssignment[] =>
    review.worksheets
        .flatMap(worksheet => {
            const assignment = review.assignments.find(candidate => candidate.id === worksheet.assignmentId);
            const stage = review.stages.find(candidate => candidate.stageKey === assignment?.stageKey);
            return assignment && stage ? [{worksheet, assignment, stage}] : [];
        })
        .filter(workable => open(review, workable))
        .sort((left, right) => left.stage.position - right.stage.position);

export const workScopeOf = (review: InformationRequestReviewDto, {
    worksheet,
    stage
}: WorkableAssignment): ReviewWorkScope => {
    const settledOtherwise = new Set(review.decisions
        .filter(decision => decision.stageKey === stage.stageKey && decision.kind !== InformationRequestReviewDecisionKind.REVIEWER)
        .map(decision => decision.submissionItemId));
    return {
        worksheet,
        stageKey: stage.stageKey,
        itemIds: stage.items.map(item => item.submissionItemId).filter(itemId => !settledOtherwise.has(itemId)),
    };
};
