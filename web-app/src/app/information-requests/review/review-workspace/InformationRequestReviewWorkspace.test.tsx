/** @vitest-environment jsdom */
import {cleanup, fireEvent, render, screen, waitFor, within} from "@testing-library/react";
import {MemoryRouter, Route, Routes} from "react-router-dom";
import {afterEach, beforeAll, beforeEach, describe, expect, it, vi} from "vitest";
import * as transport from "../../../../services/informationRequestReviewService.ts";
import {usePlanFeature} from "../../../../hooks/subscription/usePlanFeature.ts";
import {
    InformationRequestFindingCorrectionScope,
    InformationRequestFindingSeverity,
    InformationRequestRequirementType,
    InformationRequestResponseDisposition,
    InformationRequestReviewAggregation,
    InformationRequestReviewAssignmentState,
    InformationRequestReviewDto,
    InformationRequestReviewItemStanding,
    InformationRequestReviewKind,
    InformationRequestReviewOutcome,
    InformationRequestReviewStageOrdering,
    InformationRequestReviewStageState,
    InformationRequestReviewState,
    InformationRequestReviewTieResolution,
    InformationRequestReviewVisibility,
    InformationRequestState,
} from "../../../models/models.tsx";
import InformationRequestReviewWorkspace from "./InformationRequestReviewWorkspace.tsx";
import {formatInformationRequestTime} from "../../shared/informationRequestFormatting.ts";

const shownTime = (value: string): string => formatInformationRequestTime(value).replace(/\s+/g, " ");

vi.mock("../../../../services/informationRequestReviewService.ts", () => ({
    getInformationRequestReview: vi.fn(),
    saveInformationRequestReviewWorksheet: vi.fn(),
    recordInformationRequestReviewDecisions: vi.fn(),
    recordInformationRequestReviewFinding: vi.fn(),
}));
vi.mock("../../../../hooks/subscription/usePlanFeature.ts", () => ({usePlanFeature: vi.fn()}));
vi.mock("../../../../services/informationRequestRuntimeService.ts", () => ({
    getInformationRequestResponseWorkspace: vi.fn().mockRejectedValue({errorMessage: "Not shown"}),
}));
vi.mock("../../../../services/informationRequestAuthoringService.ts", () => ({
    getInformationRequestParties: vi.fn().mockResolvedValue({parties: [], partiesETag: ""}),
}));

const summary = (state: InformationRequestReviewState) => ({
    id: "review-a",
    informationRequestId: "request-a",
    packageId: "package-a",
    packageNumber: 1,
    reviewNumber: 1,
    kind: InformationRequestReviewKind.INITIAL,
    state,
    openedAt: "2026-09-26T08:00:00Z",
    reviewETag: "\"review:2\"",
});

const review = (state = InformationRequestReviewState.IN_REVIEW): InformationRequestReviewDto => ({
    review: summary(state),
    reviewStageOrdering: InformationRequestReviewStageOrdering.SEQUENTIAL,
    stages: [{
        stageKey: "review",
        title: "Review",
        position: 1,
        aggregation: InformationRequestReviewAggregation.ANY,
        minimumReviewerCount: 1,
        tieResolution: InformationRequestReviewTieResolution.MOST_SEVERE_OUTCOME,
        overridePermitted: false,
        excludesResponseParties: false,
        excludesPriorReviewers: false,
        state: InformationRequestReviewStageState.OPEN,
        items: [
            {submissionItemId: "item-a", standing: InformationRequestReviewItemStanding.PENDING},
            {submissionItemId: "item-b", standing: InformationRequestReviewItemStanding.PENDING},
        ],
    }],
    items: [
        {
            submissionItemId: "item-a",
            requirementId: "requirement-a",
            requirementKey: "recorded-summary",
            requirementType: InformationRequestRequirementType.FIELD,
            occurrencePath: "root",
            reviewed: true,
            contentVisible: true,
            disposition: InformationRequestResponseDisposition.PROVIDED,
            fieldValue: "Synthetic summary",
            evidence: [],
        },
        {
            submissionItemId: "item-b",
            requirementId: "requirement-b",
            requirementKey: "supporting-record",
            requirementType: InformationRequestRequirementType.DOCUMENT,
            occurrencePath: "root",
            reviewed: true,
            contentVisible: true,
            disposition: InformationRequestResponseDisposition.PROVIDED,
            evidence: [{artifactId: "artifact-a", evidenceVersionId: "evidence-a", versionNumber: 1, conformance: "CONFORMING"}],
        },
    ],
    assignments: [{
        id: "assignment-a",
        stageKey: "review",
        reviewerPartyId: "party-a",
        state: InformationRequestReviewAssignmentState.ACTIVE,
        assignedAt: "2026-09-26T08:05:00Z",
        callerIsReviewer: true,
    }],
    decisions: [],
    findings: [],
    comments: [],
    remediations: [],
    worksheets: [{assignmentId: "assignment-a", draftETag: "\"draft:1\"", entries: []}],
    canManage: false,
});

const availability = (isAvailable: boolean) => ({
    isKnown: true,
    isIncluded: isAvailable,
    isEnforced: true,
    isDiscoverable: isAvailable,
    isAvailable,
    upgradePlanCode: null,
});

const renderWorkspace = () => render(
    <MemoryRouter initialEntries={["/information-requests/request-a/reviews/review-a"]}>
        <Routes>
            <Route path={"/information-requests/:requestId/reviews/:reviewId"}
                   element={<InformationRequestReviewWorkspace/>}/>
        </Routes>
    </MemoryRouter>,
);

const card = async (itemId: string): Promise<HTMLElement> =>
{
    await screen.findByText("recorded summary");
    return document.getElementById(`information-request-review-item-${itemId}`) as HTMLElement;
};

describe("InformationRequestReviewWorkspace", () =>
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

    beforeEach(() =>
    {
        vi.clearAllMocks();
        vi.mocked(usePlanFeature).mockReturnValue(availability(true));
    });

    afterEach(cleanup);

    it("saves the reviewer's outcomes under the worksheet revision and records them", async () =>
    {
        vi.mocked(transport.getInformationRequestReview)
            .mockResolvedValueOnce(review())
            .mockResolvedValue(review(InformationRequestReviewState.SATISFIED));
        vi.mocked(transport.saveInformationRequestReviewWorksheet).mockResolvedValue({
            outcome: "SAVED",
            responseETag: "\"draft:2\"",
            data: {assignmentId: "assignment-a", draftETag: "\"draft:2\"", entries: []},
        });
        vi.mocked(transport.recordInformationRequestReviewDecisions).mockResolvedValue({
            outcome: "SAVED",
            responseETag: "\"responses:3\"",
            data: {requestState: InformationRequestState.CLOSED, responseETag: "\"responses:3\"", review: summary(InformationRequestReviewState.SATISFIED)},
        });

        renderWorkspace();
        expect(within(await card("item-a")).getByText("Synthetic summary")).toBeTruthy();
        expect(screen.getByText(`Opened ${shownTime("2026-09-26T08:00:00Z")}.`, {exact: false})).toBeTruthy();
        fireEvent.click(within(await card("item-a")).getByLabelText("Satisfied"));
        fireEvent.click(within(await card("item-b")).getByLabelText("Satisfied"));
        fireEvent.click(screen.getByRole("button", {name: "Record decisions"}));

        await waitFor(() => expect(transport.recordInformationRequestReviewDecisions).toHaveBeenCalled());
        expect(transport.saveInformationRequestReviewWorksheet).toHaveBeenCalledWith("request-a", "review-a", "assignment-a", {
            entries: [
                {submissionItemId: "item-a", outcome: InformationRequestReviewOutcome.SATISFIED, narrative: undefined},
                {submissionItemId: "item-b", outcome: InformationRequestReviewOutcome.SATISFIED, narrative: undefined},
            ],
        }, "\"draft:1\"");
        expect(transport.recordInformationRequestReviewDecisions).toHaveBeenCalledWith("request-a", "review-a", "assignment-a", {
            expectedETag: "\"draft:2\"",
            idempotencyKey: expect.any(String),
        });
        expect(await screen.findByText("Decisions recorded. This review is satisfied.")).toBeTruthy();
    });

    it("records a finding for an item and reports a stale worksheet instead of saving over it", async () =>
    {
        vi.mocked(transport.getInformationRequestReview).mockResolvedValue(review());
        vi.mocked(transport.recordInformationRequestReviewFinding).mockResolvedValue({
            outcome: "SAVED",
            responseETag: "\"review:3\"",
            data: {requestState: InformationRequestState.ISSUED, responseETag: "\"responses:2\"", review: summary(InformationRequestReviewState.IN_REVIEW), findingId: "finding-a"},
        });
        vi.mocked(transport.saveInformationRequestReviewWorksheet).mockResolvedValue({outcome: "STALE"});

        renderWorkspace();
        fireEvent.click(within(await card("item-b")).getByRole("button", {name: "Add finding"}));
        fireEvent.change(screen.getByLabelText("Reason code"), {target: {value: "record.incomplete"}});
        fireEvent.change(screen.getByLabelText("What needs attention"), {target: {value: "The record is missing a page"}});
        fireEvent.click(screen.getByRole("button", {name: "Record finding"}));

        await waitFor(() => expect(transport.recordInformationRequestReviewFinding).toHaveBeenCalledWith("request-a", "review-a", {
            submissionItemId: "item-b",
            reasonCode: "record.incomplete",
            narrative: "The record is missing a page",
            severity: InformationRequestFindingSeverity.MAJOR,
            visibility: InformationRequestReviewVisibility.RESPONDENT_VISIBLE,
            correctionScope: InformationRequestFindingCorrectionScope.RESPONSE,
            evidenceVersionId: undefined,
        }, expect.any(String)));

        fireEvent.click(within(await card("item-b")).getByLabelText("Changes required"));
        fireEvent.click(screen.getByRole("button", {name: "Save worksheet"}));
        expect(await screen.findByText(/This review changed while you were working/)).toBeTruthy();
        expect(transport.recordInformationRequestReviewDecisions).not.toHaveBeenCalled();
    });

    it("opens assigned review work whatever the reviewer's own plan, since the owner funds the request", async () =>
    {
        vi.mocked(usePlanFeature).mockReturnValue(availability(false));

        renderWorkspace();

        await waitFor(() => expect(transport.getInformationRequestReview).toHaveBeenCalledWith("request-a", "review-a"));
        expect(screen.queryByText("Information Requests are not included in your plan.")).toBeNull();
    });
});
