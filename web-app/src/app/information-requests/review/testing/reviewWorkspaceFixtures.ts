import {fireEvent, within} from "@testing-library/react";
import {
    InformationRequestPartyDto,
    InformationRequestRequirementType,
    InformationRequestResponseDisposition,
    InformationRequestReviewAggregation,
    InformationRequestReviewAssignmentState,
    InformationRequestReviewCommentRole,
    InformationRequestReviewDecisionKind,
    InformationRequestReviewDto,
    InformationRequestReviewItemStanding,
    InformationRequestReviewKind,
    InformationRequestReviewOutcome,
    InformationRequestReviewStageOrdering,
    InformationRequestReviewStageState,
    InformationRequestReviewState,
    InformationRequestReviewTieResolution,
    InformationRequestReviewVisibility,
    InformationRequestShareRoleKey,
} from "../../../models/models.tsx";

export const savedReviewCommand = {outcome: "SAVED" as const, responseETag: "\"responses:3\"", data: {} as never};

export const reviewWorkspaceReview = (state = InformationRequestReviewState.IN_REVIEW): InformationRequestReviewDto => ({
    review: {
        id: "review-a",
        informationRequestId: "request-a",
        packageId: "package-a",
        packageNumber: 1,
        reviewNumber: 1,
        kind: InformationRequestReviewKind.INITIAL,
        state,
        openedAt: Date.parse("2026-09-26T08:00:00Z"),
        reviewETag: "\"review:2\"",
    },
    reviewStageOrdering: InformationRequestReviewStageOrdering.SEQUENTIAL,
    stages: [{
        stageKey: "content",
        title: "Content review",
        position: 1,
        aggregation: InformationRequestReviewAggregation.ALL,
        minimumReviewerCount: 2,
        tieResolution: InformationRequestReviewTieResolution.MOST_SEVERE_OUTCOME,
        overridePermitted: true,
        excludesResponseParties: true,
        excludesPriorReviewers: true,
        state: InformationRequestReviewStageState.OPEN,
        items: [{submissionItemId: "item-b", standing: InformationRequestReviewItemStanding.PENDING}],
    }],
    items: [{
        submissionItemId: "item-b",
        requirementId: "requirement-b",
        requirementKey: "supporting-record",
        prompt: "Attach the supporting record",
        requirementType: InformationRequestRequirementType.DOCUMENT,
        occurrencePath: "root",
        reviewed: true,
        contentVisible: true,
        waiverPermitted: false,
        disposition: InformationRequestResponseDisposition.PROVIDED,
        evidence: [{
            artifactId: "artifact-a",
            evidenceVersionId: "evidence-a",
            versionNumber: 1,
            contentLength: 2048,
            conformance: "CONFORMING"
        }],
    }],
    assignments: [{
        id: "assignment-a",
        stageKey: "content",
        reviewerPartyId: "party-a",
        state: InformationRequestReviewAssignmentState.ACTIVE,
        assignedAt: Date.parse("2026-09-26T08:05:00Z"),
        callerIsReviewer: true,
        delegationCandidates: [{partyId: "party-b", label: "Second reviewer"}],
    }],
    decisions: [{
        id: "decision-a",
        stageKey: "content",
        submissionItemId: "item-b",
        kind: InformationRequestReviewDecisionKind.REVIEWER,
        assignmentId: "assignment-a",
        outcome: InformationRequestReviewOutcome.CHANGES_REQUIRED,
        decidedAt: Date.parse("2026-09-26T09:00:00Z"),
        decidedByCaller: true,
    }],
    findings: [],
    comments: [{
        id: "comment-a",
        submissionItemId: "item-b",
        requirementId: "requirement-b",
        authorRole: InformationRequestReviewCommentRole.RESPONDENT,
        visibility: InformationRequestReviewVisibility.RESPONDENT_VISIBLE,
        body: "Page 2 is attached",
        createdAt: Date.parse("2026-09-26T09:30:00Z"),
        authoredByCaller: false,
    }],
    remediations: [{findingId: "finding-z", remediatedByPackageId: "package-b", remediatedByItemId: "item-c"} as never],
    worksheets: [{assignmentId: "assignment-a", draftETag: "\"draft:1\"", draftRevision: 1, entries: []}],
    canManage: true,
    earlierFindings: [],
    earlierRemediations: [],
    canReconsider: state !== InformationRequestReviewState.IN_REVIEW,
    canComment: state === InformationRequestReviewState.IN_REVIEW || state === InformationRequestReviewState.CHANGES_REQUESTED,
    itemsAwaitingStepUp: 0,
});

export const reviewWorkspaceParty = (
    id: string,
    roleKey: InformationRequestShareRoleKey,
    label?: string,
): InformationRequestPartyDto => ({
    id,
    informationRequestId: "request-a",
    roleKey,
    active: true,
    assignedAt: Date.parse("2026-09-26T08:00:00Z"),
    partyRevision: 1,
    partyETag: "",
    label,
});

export const reviewWorkspaceParties = (): InformationRequestPartyDto[] => [
    reviewWorkspaceParty("party-a", InformationRequestShareRoleKey.REVIEWER, "First reviewer"),
    reviewWorkspaceParty("party-b", InformationRequestShareRoleKey.REVIEWER, "Second reviewer"),
    reviewWorkspaceParty("party-c", InformationRequestShareRoleKey.CONTRIBUTOR, "Respondent"),
];

export const chooseReviewOption = (container: HTMLElement, label: string, option: string) => {
    const select = within(container).getByLabelText(label) as HTMLSelectElement;
    fireEvent.change(select, {target: {value: Array.from(select.options).find(candidate => candidate.textContent === option)?.value ?? ""}});
};
