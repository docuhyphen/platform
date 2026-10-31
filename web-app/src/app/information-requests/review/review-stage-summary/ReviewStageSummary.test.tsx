/** @vitest-environment jsdom */
import {cleanup, render} from "@testing-library/react";
import {afterEach, describe, expect, it} from "vitest";
import {
    InformationRequestReviewAggregation,
    InformationRequestReviewItemStanding,
    InformationRequestReviewStageStandingDto,
    InformationRequestReviewStageState,
    InformationRequestReviewTieResolution,
} from "../../../models/models.tsx";
import ReviewStageSummary from "./ReviewStageSummary.tsx";

const stage: InformationRequestReviewStageStandingDto = {
    stageKey: "content",
    title: "Content review",
    position: 1,
    aggregation: InformationRequestReviewAggregation.ALL,
    minimumReviewerCount: 2,
    tieResolution: InformationRequestReviewTieResolution.MOST_SEVERE_OUTCOME,
    overridePermitted: true,
    excludesResponseParties: true,
    excludesPriorReviewers: false,
    state: InformationRequestReviewStageState.OPEN,
    items: [{submissionItemId: "item-a", standing: InformationRequestReviewItemStanding.PENDING}],
};

describe("ReviewStageSummary", () => {
    afterEach(cleanup);

    it("gives each rule of a stage an id built from the stage key and the rule's position", () => {
        const {container} = render(<ReviewStageSummary stages={[stage]}/>);
        const rules = Array.from(container.querySelectorAll("#information-request-review-stage-content-rules > li"))
            .map(rule => [rule.id, rule.textContent]);

        expect(rules).toEqual([
            ["information-request-review-stage-content-rules-0", "Anyone who answered this request cannot review it."],
            ["information-request-review-stage-content-rules-1", "At least 2 reviewers decide each item."],
            ["information-request-review-stage-content-rules-2", "A manager can override an item's decision."],
        ]);
    });
});
