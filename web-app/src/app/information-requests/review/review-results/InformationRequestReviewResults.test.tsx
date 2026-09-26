/** @vitest-environment jsdom */
import {cleanup, fireEvent, render, screen, waitFor} from "@testing-library/react";
import {afterEach, beforeAll, beforeEach, describe, expect, it, vi} from "vitest";
import * as transport from "../../../../services/informationRequestReviewService.ts";
import {
    InformationRequestCorrectionState,
    InformationRequestFindingCorrectionScope,
    InformationRequestFindingSeverity,
    InformationRequestRespondentReviewDto,
    InformationRequestReviewKind,
    InformationRequestReviewState,
    InformationRequestReviewVisibility,
    InformationRequestState,
} from "../../../models/models.tsx";
import InformationRequestReviewResults from "./InformationRequestReviewResults.tsx";

vi.mock("../../../../services/informationRequestReviewService.ts", () => ({
    getInformationRequestReviewResults: vi.fn(),
    appealInformationRequestReview: vi.fn(),
}));

const returned = (canAppeal: boolean): InformationRequestRespondentReviewDto => ({
    review: {
        id: "review-a",
        informationRequestId: "request-a",
        packageId: "package-a",
        packageNumber: 1,
        reviewNumber: 1,
        kind: InformationRequestReviewKind.INITIAL,
        state: InformationRequestReviewState.CHANGES_REQUESTED,
        openedAt: "2026-09-26T08:00:00Z",
        settledAt: "2026-09-26T09:00:00Z",
        reviewETag: "\"review:4\"",
    },
    findings: [{
        id: "finding-a",
        submissionItemId: "item-a",
        requirementId: "requirement-a",
        reasonCode: "record.incomplete",
        narrative: "The record is missing a page",
        severity: InformationRequestFindingSeverity.MAJOR,
        visibility: InformationRequestReviewVisibility.RESPONDENT_VISIBLE,
        correctionScope: InformationRequestFindingCorrectionScope.RESPONSE,
        recordedAt: "2026-09-26T08:30:00Z",
        recordedByCaller: false,
    }],
    comments: [],
    correction: {
        id: "correction-a",
        reviewId: "review-a",
        packageId: "package-a",
        state: InformationRequestCorrectionState.OPEN,
        openedAt: "2026-09-26T09:00:00Z",
        requirementIds: ["requirement-a"],
        evidenceVersionIds: [],
        undisclosedItemCount: 1,
    },
    remediations: [],
    canAppeal,
    canComment: false,
});

describe("InformationRequestReviewResults", () =>
{
    beforeAll(() =>
    {
        vi.stubGlobal("ResizeObserver", class
        {
            observe() {}
            unobserve() {}
            disconnect() {}
        });
    });

    beforeEach(() => vi.clearAllMocks());

    afterEach(cleanup);

    it("shows what a settled review returned for correction without naming items the respondent cannot view", async () =>
    {
        vi.mocked(transport.getInformationRequestReviewResults).mockResolvedValue([returned(false)]);

        render(<InformationRequestReviewResults requestId={"request-a"}
                                                accessLinkToken={"bootstrap"}
                                                refreshKey={"\"responses:2\""}
                                                requirementLabels={{"requirement-a": "Provide the supporting record"}}
                                                onChanged={vi.fn()}/>);

        expect(await screen.findByText("Changes requested")).toBeTruthy();
        expect(transport.getInformationRequestReviewResults).toHaveBeenCalledWith("request-a", "bootstrap");
        expect(screen.getByText("The record is missing a page")).toBeTruthy();
        expect(screen.getByText("Correct and resubmit these items:")).toBeTruthy();
        expect(screen.getAllByText("Provide the supporting record").length).toBeGreaterThan(0);
        expect(screen.getByText("1 other returned items concern parts handled by other parties.")).toBeTruthy();
        expect(screen.queryByRole("button", {name: "Appeal"})).toBeNull();
    });

    it("appeals the exact review under its revision and reports a stale review", async () =>
    {
        const onChanged = vi.fn();
        vi.mocked(transport.getInformationRequestReviewResults).mockResolvedValue([returned(true)]);
        vi.mocked(transport.appealInformationRequestReview)
            .mockResolvedValueOnce({outcome: "STALE"})
            .mockResolvedValueOnce({
                outcome: "SAVED",
                responseETag: "\"responses:3\"",
                data: {
                    requestState: InformationRequestState.ISSUED,
                    responseETag: "\"responses:3\"",
                    review: {...returned(true).review, id: "review-b", kind: InformationRequestReviewKind.APPEAL, state: InformationRequestReviewState.PENDING},
                },
            });

        render(<InformationRequestReviewResults requestId={"request-a"}
                                                refreshKey={"\"responses:2\""}
                                                requirementLabels={{}}
                                                onChanged={onChanged}/>);

        fireEvent.click(await screen.findByRole("button", {name: "Appeal"}));
        fireEvent.change(screen.getByLabelText("Why the decision should change"), {target: {value: "The page was attached"}});
        fireEvent.click(screen.getByRole("button", {name: "Send appeal"}));
        expect(await screen.findByText("This review changed. Reload the request before appealing.")).toBeTruthy();

        fireEvent.click(await screen.findByRole("button", {name: "Appeal"}));
        fireEvent.change(screen.getByLabelText("Why the decision should change"), {target: {value: "The page was attached"}});
        fireEvent.click(screen.getByRole("button", {name: "Send appeal"}));

        await waitFor(() => expect(onChanged).toHaveBeenCalled());
        expect(transport.appealInformationRequestReview).toHaveBeenLastCalledWith("request-a", "review-a", {reason: "The page was attached"}, {
            expectedETag: "\"review:4\"",
            idempotencyKey: expect.any(String),
            accessLinkToken: undefined,
        });
        expect(await screen.findByText("Your appeal was recorded. A new review will decide it.")).toBeTruthy();
    });
});
