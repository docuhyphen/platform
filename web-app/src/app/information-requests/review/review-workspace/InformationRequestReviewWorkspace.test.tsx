/** @vitest-environment jsdom */
import {act, cleanup, fireEvent, render, screen, waitFor, within} from "@testing-library/react";
import {MemoryRouter, Route, Routes, useNavigate} from "react-router-dom";
import {afterEach, beforeAll, beforeEach, describe, expect, it, vi} from "vitest";
import * as transport from "../../../../services/informationRequestReviewService.ts";
import * as submissions from "../../../../services/informationRequestSubmissionService.ts";
import {usePlanFeature} from "../../../../hooks/subscription/usePlanFeature.ts";
import {
    InformationRequestCorrectionState,
    InformationRequestFindingCorrectionScope,
    InformationRequestFindingSeverity,
    InformationRequestRequirementType,
    InformationRequestResponseDisposition,
    InformationRequestRetestResult,
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
import {requestStepUp} from "../../../../services/stepUpBroker.ts";
import {getInformationRequestResponseWorkspace} from "../../../../services/informationRequestRuntimeService.ts";
import InformationRequestReviewWorkspace from "./InformationRequestReviewWorkspace.tsx";
import {formatInformationRequestTime} from "../../shared/informationRequestFormatting.ts";

const shownTime = (value: number): string => formatInformationRequestTime(value).replace(/\s+/g, " ");

vi.mock("../../../../services/informationRequestReviewService.ts", () => ({
    getInformationRequestReview: vi.fn(),
    saveInformationRequestReviewWorksheet: vi.fn(),
    recordInformationRequestReviewDecisions: vi.fn(),
    recordInformationRequestReviewFinding: vi.fn(),
}));
vi.mock("../../../../hooks/subscription/usePlanFeature.ts", () => ({usePlanFeature: vi.fn()}));
vi.mock("../../../../services/stepUpBroker.ts", () => ({requestStepUp: vi.fn()}));
vi.mock("../../../../services/informationRequestRuntimeService.ts", () => ({
    getInformationRequestResponseWorkspace: vi.fn().mockRejectedValue({errorMessage: "Not shown"}),
}));
vi.mock("../../../../services/informationRequestAuthoringService.ts", () => ({
    getInformationRequestParties: vi.fn().mockResolvedValue({parties: [], partiesETag: ""}),
}));
vi.mock("../../../../services/informationRequestSubmissionService.ts", () => ({
    createInformationRequestSupplement: vi.fn(),
}));

const summary = (state: InformationRequestReviewState) => ({
    id: "review-a",
    informationRequestId: "request-a",
    packageId: "package-a",
    packageNumber: 1,
    reviewNumber: 1,
    kind: InformationRequestReviewKind.INITIAL,
    state,
    openedAt: Date.parse("2026-09-26T08:00:00Z"),
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
            waiverPermitted: false,
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
            waiverPermitted: false,
            disposition: InformationRequestResponseDisposition.PROVIDED,
            evidence: [{
                artifactId: "artifact-a",
                evidenceVersionId: "evidence-a",
                versionNumber: 1,
                conformance: "CONFORMING"
            }],
        },
    ],
    assignments: [{
        id: "assignment-a",
        stageKey: "review",
        reviewerPartyId: "party-a",
        state: InformationRequestReviewAssignmentState.ACTIVE,
        assignedAt: Date.parse("2026-09-26T08:05:00Z"),
        callerIsReviewer: true,
        delegationCandidates: [],
    }],
    decisions: [],
    findings: [],
    comments: [],
    remediations: [],
    worksheets: [{assignmentId: "assignment-a", draftETag: "\"draft:1\"", draftRevision: 1, entries: []}],
    canManage: false,
    earlierFindings: [],
    earlierRemediations: [],
    canReconsider: false,
    canComment: true,
    itemsAwaitingStepUp: 0,
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
        <OtherReviewLink/>
        <Routes>
            <Route path={"/information-requests/:requestId/reviews/:reviewId"}
                   element={<InformationRequestReviewWorkspace/>}/>
        </Routes>
    </MemoryRouter>,
);

const OtherReviewLink = () => {
    const navigate = useNavigate();
    return (
        <button id={"open-other-review"}
                onClick={() => navigate("/information-requests/request-b/reviews/review-b")}>
            Open the other review
        </button>
    );
};

const otherReview = (): InformationRequestReviewDto => ({
    ...review(),
    review: {...summary(InformationRequestReviewState.IN_REVIEW), id: "review-b", informationRequestId: "request-b"},
});

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
            observe() {
            }

            unobserve() {
            }

            disconnect() {
            }
        });
    });

    beforeEach(() =>
    {
        vi.clearAllMocks();
        vi.mocked(usePlanFeature).mockReturnValue(availability(true));
    });

    afterEach(cleanup);

    it("asks a reviewer to verify again when protected items wait for it and reloads the review once verified", async () => {
        vi.mocked(transport.getInformationRequestReview).mockResolvedValue({...review(), itemsAwaitingStepUp: 1});
        vi.mocked(requestStepUp).mockResolvedValue(true);
        renderWorkspace();

        expect(await screen.findByText("1 protected item is hidden until you verify again. Verifying lasts 5 minutes."))
            .toBeTruthy();
        fireEvent.click(screen.getByRole("button", {name: "Verify again"}));

        await waitFor(() => expect(transport.getInformationRequestReview).toHaveBeenCalledTimes(2));
        expect(requestStepUp).toHaveBeenCalledWith({action: "review protected items"});
    });

    it("says when verifying again did not finish and keeps the review as it was", async () => {
        vi.mocked(transport.getInformationRequestReview).mockResolvedValue({...review(), itemsAwaitingStepUp: 2});
        vi.mocked(requestStepUp).mockRejectedValue(new Error("Step-up cancelled by user"));
        renderWorkspace();

        fireEvent.click(await screen.findByRole("button", {name: "Verify again"}));

        expect(await screen.findByText("Verification did not finish.")).toBeTruthy();
        expect(transport.getInformationRequestReview).toHaveBeenCalledTimes(1);
    });

    it("lets a reviewer ask for more information and says who issues the supplemental request", async () => {
        vi.mocked(transport.getInformationRequestReview).mockResolvedValue({
            ...review(),
            canRequestSupplement: true,
            requestETag: "\"request:7\"",
        });
        vi.mocked(submissions.createInformationRequestSupplement).mockResolvedValue({
            outcome: "SAVED",
            data: {successor: {id: "request-b"}},
        } as Awaited<ReturnType<typeof submissions.createInformationRequestSupplement>>);
        renderWorkspace();

        fireEvent.click(await screen.findByRole("button", {name: "Request more information"}));
        fireEvent.change(screen.getByLabelText("Reason"), {target: {value: "document-missing"}});
        fireEvent.click(screen.getByRole("button", {name: "Create supplement"}));

        await waitFor(() => expect(submissions.createInformationRequestSupplement).toHaveBeenCalledTimes(1));
        const [requestId, reasonCode, options] = vi.mocked(submissions.createInformationRequestSupplement).mock.calls[0];
        expect([requestId, reasonCode, options.expectedETag]).toEqual(["request-a", "document-missing", "\"request:7\""]);
        expect(await screen.findByText(
            "A supplemental request was created as a draft for the same parties. Whoever manages this request issues it.",
        )).toBeTruthy();
    });

    it("offers no supplemental request when the review does not allow one", async () => {
        vi.mocked(transport.getInformationRequestReview).mockResolvedValue(review());
        renderWorkspace();

        await card("item-a");
        expect(screen.queryByRole("button", {name: "Request more information"})).toBeNull();
    });

    it("offers no verification when no protected item waits for it", async () => {
        vi.mocked(transport.getInformationRequestReview).mockResolvedValue(review());
        renderWorkspace();

        await card("item-a");
        expect(screen.queryByRole("button", {name: "Verify again"})).toBeNull();
    });

    it("shows the reason a reconsideration or appeal was opened when the review states it", async () => {
        vi.mocked(transport.getInformationRequestReview).mockResolvedValue({
            ...review(),
            review: {
                ...summary(InformationRequestReviewState.PENDING),
                kind: InformationRequestReviewKind.APPEAL,
                openingReason: "The supporting record covers the whole period",
            },
        });
        renderWorkspace();

        expect(await screen.findByText("Reason given: The supporting record covers the whole period")).toBeTruthy();
    });

    it("saves the reviewer's outcomes under the worksheet revision and records them", async () =>
    {
        vi.mocked(transport.getInformationRequestReview)
            .mockResolvedValueOnce(review())
            .mockResolvedValue(review(InformationRequestReviewState.SATISFIED));
        vi.mocked(transport.saveInformationRequestReviewWorksheet).mockResolvedValue({
            outcome: "SAVED",
            responseETag: "\"draft:2\"",
            data: {assignmentId: "assignment-a", draftETag: "\"draft:2\"", draftRevision: 2, entries: []},
        });
        vi.mocked(transport.recordInformationRequestReviewDecisions).mockResolvedValue({
            outcome: "SAVED",
            responseETag: "\"responses:3\"",
            data: {
                requestState: InformationRequestState.CLOSED,
                responseETag: "\"responses:3\"",
                review: summary(InformationRequestReviewState.SATISFIED)
            },
        });

        renderWorkspace();
        expect(within(await card("item-a")).getByText("Synthetic summary")).toBeTruthy();
        expect(screen.getByText(`Opened ${shownTime(Date.parse("2026-09-26T08:00:00Z"))}.`, {exact: false})).toBeTruthy();
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
            data: {
                requestState: InformationRequestState.ISSUED,
                responseETag: "\"responses:2\"",
                review: summary(InformationRequestReviewState.IN_REVIEW),
                findingId: "finding-a"
            },
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
            assignmentId: "assignment-a",
        }, expect.any(String)));

        fireEvent.click(within(await card("item-b")).getByLabelText("Changes required"));
        fireEvent.click(screen.getByRole("button", {name: "Save worksheet"}));
        expect(await screen.findByText(/This review changed while you were working/)).toBeTruthy();
        expect(transport.recordInformationRequestReviewDecisions).not.toHaveBeenCalled();
        await waitFor(() => expect(transport.getInformationRequestReview).toHaveBeenCalledTimes(3));
        expect((within(await card("item-b")).getByLabelText("Changes required") as HTMLInputElement).checked).toBe(true);
    });

    it("names an item by the prompt its review states where a finding is added and where a correction returns it", async () => {
        const base = review();
        vi.mocked(transport.getInformationRequestReview).mockResolvedValue({
            ...base,
            items: base.items.map(item => item.submissionItemId === "item-b" ? {
                ...item,
                prompt: "Attach the supporting record"
            } : item),
            correction: {
                id: "correction-a",
                state: InformationRequestCorrectionState.OPEN,
                requirementIds: ["requirement-b"],
                evidence: [],
                undisclosedItemCount: 0,
            },
        } as unknown as InformationRequestReviewDto);

        renderWorkspace();

        await waitFor(() => expect(document.getElementById("information-request-review-correction-requirement-requirement-b-label")?.textContent)
            .toBe("Attach the supporting record"));
        fireEvent.click(within(await card("item-b")).getByRole("button", {name: "Add finding"}));
        expect(screen.getByText("Finding for Attach the supporting record")).toBeTruthy();
        expect(getInformationRequestResponseWorkspace).not.toHaveBeenCalled();
    });

    it("records a retest of a finding from the review this one follows, and of no older review", async () => {
        const earlier = (id: string, reviewId: string, requirementId: string, reasonCode: string) => ({
            id,
            reviewId,
            submissionItemId: "item-old",
            requirementId,
            reasonCode,
            narrative: "The record is missing a page",
            severity: InformationRequestFindingSeverity.MAJOR,
            visibility: InformationRequestReviewVisibility.RESPONDENT_VISIBLE,
            correctionScope: InformationRequestFindingCorrectionScope.RESPONSE,
            recordedAt: Date.parse("2026-09-20T08:00:00Z"),
            recordedByCaller: false,
        });
        vi.mocked(transport.getInformationRequestReview).mockResolvedValue({
            ...review(),
            review: {...summary(InformationRequestReviewState.IN_REVIEW), reviewNumber: 2, priorReviewId: "review-0"},
            earlierFindings: [
                earlier("finding-old", "review-0", "requirement-b", "record.incomplete"),
                earlier("finding-older", "review-older", "requirement-b", "record.unreadable"),
                earlier("finding-other", "review-0", "requirement-a", "record.missing"),
            ],
        });
        vi.mocked(transport.recordInformationRequestReviewFinding).mockResolvedValue({
            outcome: "SAVED",
            responseETag: "\"review:3\"",
            data: {
                requestState: InformationRequestState.ISSUED,
                responseETag: "\"responses:2\"",
                review: summary(InformationRequestReviewState.IN_REVIEW),
                findingId: "finding-new"
            },
        });

        renderWorkspace();
        fireEvent.click(within(await card("item-b")).getByRole("button", {name: "Add finding"}));
        const retest = screen.getByLabelText("Retests an earlier finding") as HTMLSelectElement;
        expect(Array.from(retest.options).map(option => option.textContent)).toEqual([
            "Not a retest",
            "record.incomplete: The record is missing a page",
        ]);
        fireEvent.change(retest, {target: {value: "finding-old"}});
        fireEvent.change(screen.getByLabelText("Retest result"), {target: {value: InformationRequestRetestResult.RESOLVED}});
        fireEvent.change(screen.getByLabelText("Reason code"), {target: {value: "record.complete"}});
        fireEvent.change(screen.getByLabelText("What needs attention"), {target: {value: "The missing page is now attached"}});
        fireEvent.click(screen.getByRole("button", {name: "Record finding"}));

        await waitFor(() => expect(transport.recordInformationRequestReviewFinding).toHaveBeenCalledWith(
            "request-a",
            "review-a",
            expect.objectContaining({
                retestsFindingId: "finding-old",
                retestResult: InformationRequestRetestResult.RESOLVED
            }),
            expect.any(String),
        ));
    });

    it("retries a finding that could not be recorded under the same Idempotency-Key", async () => {
        vi.mocked(transport.getInformationRequestReview).mockResolvedValue(review());
        vi.mocked(transport.recordInformationRequestReviewFinding)
            .mockRejectedValueOnce({errorMessage: "The connection was lost"})
            .mockResolvedValueOnce({
                outcome: "SAVED",
                responseETag: "\"review:3\"",
                data: {
                    requestState: InformationRequestState.ISSUED,
                    responseETag: "\"responses:2\"",
                    review: summary(InformationRequestReviewState.IN_REVIEW),
                    findingId: "finding-a"
                },
            });

        renderWorkspace();
        fireEvent.click(within(await card("item-b")).getByRole("button", {name: "Add finding"}));
        fireEvent.change(screen.getByLabelText("Reason code"), {target: {value: "record.incomplete"}});
        fireEvent.change(screen.getByLabelText("What needs attention"), {target: {value: "The record is missing a page"}});
        fireEvent.click(screen.getByRole("button", {name: "Record finding"}));
        expect(await screen.findByText("The connection was lost")).toBeTruthy();
        fireEvent.click(screen.getByRole("button", {name: "Record finding"}));

        await waitFor(() => expect(transport.recordInformationRequestReviewFinding).toHaveBeenCalledTimes(2));
        const [first, retried] = vi.mocked(transport.recordInformationRequestReviewFinding).mock.calls;
        expect(retried[2]).toEqual(first[2]);
        expect(retried[3]).toBe(first[3]);
    });

    it("shows none of the previous review or its unsaved outcomes after moving to another review", async () => {
        let showOther: (loaded: InformationRequestReviewDto) => void = () => undefined;
        vi.mocked(transport.getInformationRequestReview).mockImplementation((_, reviewId) => reviewId === "review-a"
            ? Promise.resolve(review())
            : new Promise(resolve => {
                showOther = resolve;
            }));
        renderWorkspace();
        fireEvent.click(within(await card("item-a")).getByLabelText("Satisfied"));
        fireEvent.click(within(await card("item-a")).getByRole("button", {name: "Add finding"}));

        fireEvent.click(screen.getByRole("button", {name: "Open the other review"}));

        await waitFor(() => expect(screen.queryByText("Synthetic summary")).toBeNull());
        expect(screen.queryByRole("dialog")).toBeNull();
        expect(screen.getByText("Loading review")).toBeTruthy();
        await act(async () => showOther(otherReview()));
        const other = await card("item-a");
        expect((within(other).getByLabelText("Satisfied") as HTMLInputElement).checked).toBe(false);
        expect(screen.queryByRole("dialog")).toBeNull();
        expect(transport.getInformationRequestReview).toHaveBeenLastCalledWith("request-b", "review-b");
    });

    it("offers to try again when the review could not be loaded and keeps unsaved outcomes once it loads", async () => {
        vi.mocked(transport.getInformationRequestReview)
            .mockResolvedValueOnce(review())
            .mockRejectedValueOnce({errorMessage: "The review could not be reached"})
            .mockResolvedValue(review());
        vi.mocked(transport.recordInformationRequestReviewFinding).mockResolvedValue({
            outcome: "SAVED",
            responseETag: "\"review:3\"",
            data: {
                requestState: InformationRequestState.ISSUED,
                responseETag: "\"responses:2\"",
                review: summary(InformationRequestReviewState.IN_REVIEW),
                findingId: "finding-a"
            },
        });
        renderWorkspace();
        fireEvent.click(within(await card("item-a")).getByLabelText("Satisfied"));
        fireEvent.click(within(await card("item-b")).getByRole("button", {name: "Add finding"}));
        fireEvent.change(screen.getByLabelText("Reason code"), {target: {value: "record.incomplete"}});
        fireEvent.change(screen.getByLabelText("What needs attention"), {target: {value: "The record is missing a page"}});
        fireEvent.click(screen.getByRole("button", {name: "Record finding"}));

        expect(await screen.findByText("The review could not be reached")).toBeTruthy();
        expect(screen.queryByText("Synthetic summary")).toBeNull();
        fireEvent.click(screen.getByRole("button", {name: "Try again"}));

        const item = await card("item-a");
        expect((within(item).getByLabelText("Satisfied") as HTMLInputElement).checked).toBe(true);
        expect(screen.queryByText("The review could not be reached")).toBeNull();
    });

    it("opens assigned review work whatever the reviewer's own plan, since the owner funds the request", async () =>
    {
        vi.mocked(usePlanFeature).mockReturnValue(availability(false));

        renderWorkspace();

        await waitFor(() => expect(transport.getInformationRequestReview).toHaveBeenCalledWith("request-a", "review-a"));
        expect(screen.queryByText("Information Requests are not included in your plan.")).toBeNull();
    });
});
