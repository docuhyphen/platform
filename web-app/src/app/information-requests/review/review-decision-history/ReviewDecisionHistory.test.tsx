/** @vitest-environment jsdom */
import {cleanup, render} from "@testing-library/react";
import {afterEach, describe, expect, it} from "vitest";
import {
    InformationRequestReviewAggregation,
    InformationRequestReviewDecisionDto,
    InformationRequestReviewDecisionKind,
    InformationRequestReviewDto,
    InformationRequestReviewKind,
    InformationRequestReviewOutcome,
    InformationRequestReviewStageOrdering,
    InformationRequestReviewStageState,
    InformationRequestReviewState,
    InformationRequestReviewTieResolution,
} from "../../../models/models.tsx";
import {formatInformationRequestTime} from "../../shared/informationRequestFormatting.ts";
import ReviewDecisionHistory from "./ReviewDecisionHistory.tsx";

const firstDecidedAt = Date.parse("2026-09-26T09:00:00Z");
const secondDecidedAt = Date.parse("2026-09-26T10:00:00Z");
const thirdDecidedAt = Date.parse("2026-09-27T08:15:00Z");

const firstDecision: InformationRequestReviewDecisionDto = {
    id: "decision-a",
    stageKey: "content",
    submissionItemId: "item-a",
    kind: InformationRequestReviewDecisionKind.REVIEWER,
    outcome: InformationRequestReviewOutcome.CHANGES_REQUIRED,
    narrative: "The second page is missing",
    decidedAt: firstDecidedAt,
    decidedByCaller: true,
};

const secondDecision: InformationRequestReviewDecisionDto = {
    id: "decision-b",
    stageKey: "content",
    submissionItemId: "item-b",
    kind: InformationRequestReviewDecisionKind.REVIEWER,
    outcome: InformationRequestReviewOutcome.SATISFIED,
    decidedAt: secondDecidedAt,
    decidedByCaller: false,
};

const thirdDecision: InformationRequestReviewDecisionDto = {
    id: "decision-c",
    stageKey: "content",
    submissionItemId: "item-a",
    kind: InformationRequestReviewDecisionKind.OVERRIDE,
    outcome: InformationRequestReviewOutcome.SATISFIED,
    decidedAt: thirdDecidedAt,
    decidedByCaller: false,
};

const reviewWith = (decisions: InformationRequestReviewDecisionDto[]): InformationRequestReviewDto => ({
    review: {
        id: "review-a",
        informationRequestId: "request-a",
        packageId: "package-a",
        packageNumber: 1,
        reviewNumber: 1,
        kind: InformationRequestReviewKind.INITIAL,
        state: InformationRequestReviewState.IN_REVIEW,
        openedAt: Date.parse("2026-09-26T08:00:00Z"),
        reviewETag: "\"review:2\"",
    },
    reviewStageOrdering: InformationRequestReviewStageOrdering.SEQUENTIAL,
    stages: [{
        stageKey: "content",
        title: "Content review",
        position: 1,
        aggregation: InformationRequestReviewAggregation.ALL,
        minimumReviewerCount: 1,
        tieResolution: InformationRequestReviewTieResolution.MOST_SEVERE_OUTCOME,
        overridePermitted: false,
        excludesResponseParties: false,
        excludesPriorReviewers: false,
        state: InformationRequestReviewStageState.OPEN,
        items: [],
    }],
    items: [],
    assignments: [],
    decisions,
    findings: [],
    comments: [],
    remediations: [],
    worksheets: [],
    canManage: false,
    earlierFindings: [],
    earlierRemediations: [],
    canReconsider: false,
    canComment: true,
    itemsAwaitingStepUp: 0,
});

const labels: Record<string, string> = {"item-a": "Supporting record", "item-b": "Additional record"};

const renderHistory = (decisions: InformationRequestReviewDecisionDto[]) =>
    render(<ReviewDecisionHistory review={reviewWith(decisions)}
                                  labelOf={itemId => labels[itemId]}/>);

const listedIds = (container: HTMLElement): string[] =>
    Array.from(container.querySelectorAll("#information-request-review-history-list > li")).map(item => item.id);

describe("ReviewDecisionHistory", () => {
    afterEach(cleanup);

    it("gives every line of a decision an id built from that decision", () => {
        const {container} = renderHistory([firstDecision, secondDecision]);
        const text = (id: string) => container.querySelector(`#${id}`)?.textContent;

        expect(text("information-request-review-history-decision-a-outcome")).toBe("Supporting record: Changes required by you");
        expect(text("information-request-review-history-decision-a-decided")).toContain("Content review");
        expect(text("information-request-review-history-decision-a-narrative")).toBe(" The second page is missing");
        expect(text("information-request-review-history-decision-b-outcome")).toBe("Additional record: Satisfied by a reviewer");
        expect(text("information-request-review-history-decision-b-decided")).toContain("Content review");
        expect(container.querySelector("#information-request-review-history-decision-b-narrative")).toBeNull();
    });

    it("lists decisions whose times the server sends as epoch milliseconds from the earliest to the latest", () => {
        const {container} = renderHistory([thirdDecision, secondDecision, firstDecision]);
        const text = (id: string) => container.querySelector(`#${id}`)?.textContent;

        expect(listedIds(container)).toEqual([
            "information-request-review-history-decision-a",
            "information-request-review-history-decision-b",
            "information-request-review-history-decision-c",
        ]);
        expect(text("information-request-review-history-decision-a-decided"))
            .toBe(` (Content review, ${formatInformationRequestTime(firstDecidedAt)})`);
        expect(text("information-request-review-history-decision-c-decided"))
            .toBe(` (Content review, ${formatInformationRequestTime(thirdDecidedAt)})`);
        expect(text("information-request-review-history-decision-c-outcome")).toBe("Supporting record: Satisfied by an override");
    });
});
