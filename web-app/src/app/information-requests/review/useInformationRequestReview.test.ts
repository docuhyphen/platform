/** @vitest-environment jsdom */
import {act, cleanup, renderHook, waitFor} from "@testing-library/react";
import {afterEach, beforeEach, describe, expect, it, vi} from "vitest";
import * as transport from "../../../services/informationRequestReviewService.ts";
import {RuntimeCommandResult} from "../../../services/informationRequestRuntimeService.ts";
import {
    InformationRequestFindingCorrectionScope,
    InformationRequestFindingSeverity,
    InformationRequestRequirementType,
    InformationRequestResponseDisposition,
    InformationRequestReviewAggregation,
    InformationRequestReviewAssignmentState,
    InformationRequestReviewCommandResultDto,
    InformationRequestReviewDecisionKind,
    InformationRequestReviewDto,
    InformationRequestReviewItemDto,
    InformationRequestReviewItemStanding,
    InformationRequestReviewKind,
    InformationRequestReviewDelegationCandidateDto,
    InformationRequestReviewOutcome,
    InformationRequestReviewStageOrdering,
    InformationRequestReviewStageState,
    InformationRequestReviewState,
    InformationRequestReviewTieResolution,
    InformationRequestReviewVisibility,
    InformationRequestReviewWorksheetDto,
    InformationRequestState,
} from "../../models/models.tsx";
import {useInformationRequestReview} from "./useInformationRequestReview.ts";

vi.mock("../../../services/informationRequestReviewService.ts", () => ({
    getInformationRequestReview: vi.fn(),
    saveInformationRequestReviewWorksheet: vi.fn(),
    recordInformationRequestReviewDecisions: vi.fn(),
    recordInformationRequestReviewFinding: vi.fn(),
}));

const reviewFor = (
    requestId: string,
    reviewId: string,
    entries: InformationRequestReviewWorksheetDto["entries"] = [],
    state = InformationRequestReviewState.IN_REVIEW,
): InformationRequestReviewDto => ({
    review: {
        id: reviewId,
        informationRequestId: requestId,
        packageId: `package-of-${reviewId}`,
        packageNumber: 1,
        reviewNumber: 1,
        kind: InformationRequestReviewKind.INITIAL,
        state,
        openedAt: Date.parse("2026-09-26T08:00:00Z"),
        reviewETag: `"${reviewId}:2"`,
    },
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
        items: [`${reviewId}-item-1`, `${reviewId}-item-2`]
            .map(submissionItemId => ({submissionItemId, standing: InformationRequestReviewItemStanding.PENDING})),
    }],
    items: [`${reviewId}-item-1`, `${reviewId}-item-2`].map((submissionItemId, index): InformationRequestReviewItemDto => ({
        submissionItemId,
        requirementId: `requirement-${index}`,
        requirementKey: `recorded-item-${index}`,
        requirementType: InformationRequestRequirementType.FIELD,
        occurrencePath: "root",
        reviewed: true,
        contentVisible: true,
        waiverPermitted: false,
        disposition: InformationRequestResponseDisposition.PROVIDED,
        fieldValue: "Synthetic value",
        evidence: [],
    })),
    assignments: [{
        id: `assignment-of-${reviewId}`,
        stageKey: "review",
        reviewerPartyId: "party-a",
        state: InformationRequestReviewAssignmentState.ACTIVE,
        assignedAt: Date.parse("2026-09-26T08:05:00Z"),
        callerIsReviewer: true,
        delegationCandidates: [] as InformationRequestReviewDelegationCandidateDto[],
    }],
    decisions: [],
    findings: [],
    comments: [],
    remediations: [],
    worksheets: [{
        assignmentId: `assignment-of-${reviewId}`,
        draftETag: `"${reviewId}-draft:1"`,
        draftRevision: 1,
        entries
    }],
    canManage: false,
    earlierFindings: [],
    earlierRemediations: [],
    canReconsider: false,
    canComment: true,
    itemsAwaitingStepUp: 0,
});

const satisfiedEntries = (reviewId: string) => [`${reviewId}-item-1`, `${reviewId}-item-2`]
    .map(submissionItemId => ({submissionItemId, outcome: InformationRequestReviewOutcome.SATISFIED}));

const recorded = (requestId: string, reviewId: string): RuntimeCommandResult<InformationRequestReviewCommandResultDto> => ({
    outcome: "SAVED",
    responseETag: "\"responses:3\"",
    data: {
        requestState: InformationRequestState.CLOSED,
        responseETag: "\"responses:3\"",
        review: {...reviewFor(requestId, reviewId).review, state: InformationRequestReviewState.SATISFIED},
    },
});

const worksheetAt = (
    reviewId: string,
    revision: number,
    entries: InformationRequestReviewWorksheetDto["entries"],
): InformationRequestReviewWorksheetDto =>
    ({
        assignmentId: `assignment-of-${reviewId}`,
        draftETag: `"${reviewId}-draft:${revision}"`,
        draftRevision: revision,
        entries
    });

const saved = (worksheet: InformationRequestReviewWorksheetDto): RuntimeCommandResult<InformationRequestReviewWorksheetDto> =>
    ({outcome: "SAVED", responseETag: worksheet.draftETag, data: worksheet});

const savedWorksheet = (reviewId: string, revision: number): RuntimeCommandResult<InformationRequestReviewWorksheetDto> =>
    saved(worksheetAt(reviewId, revision, satisfiedEntries(reviewId)));

const narrated = (narrative: string) =>
    [{submissionItemId: "review-a-item-1", outcome: InformationRequestReviewOutcome.SATISFIED, narrative}];

const deferred = <T>() => {
    let resolve!: (value: T) => void;
    let reject!: (reason: unknown) => void;
    const promise = new Promise<T>((onResolve, onReject) => {
        resolve = onResolve;
        reject = onReject;
    });
    return {promise, resolve, reject};
};

const stageOf = (
    stageKey: string,
    position: number,
    state: InformationRequestReviewStageState,
    itemIds: string[],
    excludesPriorReviewers = false,
) => ({
    ...reviewFor("request-a", "review-a").stages[0],
    stageKey,
    title: `Check ${position}`,
    position,
    excludesPriorReviewers,
    state,
    items: itemIds.map(submissionItemId => ({
        submissionItemId,
        standing: InformationRequestReviewItemStanding.PENDING
    })),
});

const assignmentIn = (id: string, stageKey: string, decidedAt?: number) => ({
    id,
    stageKey,
    reviewerPartyId: "party-a",
    state: InformationRequestReviewAssignmentState.ACTIVE,
    assignedAt: Date.parse("2026-09-26T08:05:00Z"),
    decidedAt,
    callerIsReviewer: true,
    delegationCandidates: [] as InformationRequestReviewDelegationCandidateDto[],
});

const sheetOf = (assignmentId: string, revision: number, entries: InformationRequestReviewWorksheetDto["entries"] = []) =>
    ({assignmentId, draftETag: `"${assignmentId}:${revision}"`, draftRevision: revision, entries});

const decidedThrough = (assignmentId: string, stageKey: string) => ({
    id: `decision-of-${assignmentId}`,
    stageKey,
    submissionItemId: "review-a-item-1",
    kind: InformationRequestReviewDecisionKind.REVIEWER,
    assignmentId,
    outcome: InformationRequestReviewOutcome.SATISFIED,
    narrative: "Checked against the request",
    decidedAt: Date.parse("2026-09-26T09:00:00Z"),
    decidedByCaller: true,
});

const sequentialStages = (): InformationRequestReviewDto => ({
    ...reviewFor("request-a", "review-a"),
    stages: [
        stageOf("first-check", 1, InformationRequestReviewStageState.SETTLED, ["review-a-item-1"]),
        stageOf("further-check", 2, InformationRequestReviewStageState.OPEN, ["review-a-item-1"]),
    ],
    assignments: [
        assignmentIn("assignment-first", "first-check", Date.parse("2026-09-26T09:00:00Z")),
        assignmentIn("assignment-further", "further-check"),
    ],
    decisions: [decidedThrough("assignment-first", "first-check")],
    worksheets: [
        sheetOf("assignment-first", 2, [{
            submissionItemId: "review-a-item-1",
            outcome: InformationRequestReviewOutcome.SATISFIED,
            narrative: "Checked against the request",
        }]),
        sheetOf("assignment-further", 1),
    ],
});

const parallelStages = (recordsDecided = false): InformationRequestReviewDto => ({
    ...reviewFor("request-a", "review-a"),
    reviewStageOrdering: InformationRequestReviewStageOrdering.PARALLEL,
    stages: [
        stageOf("content-check", 1, InformationRequestReviewStageState.OPEN, ["review-a-item-1"]),
        stageOf("record-check", 2, recordsDecided ? InformationRequestReviewStageState.SETTLED : InformationRequestReviewStageState.OPEN, ["review-a-item-2"]),
    ],
    assignments: [
        assignmentIn("assignment-records", "record-check", recordsDecided ? Date.parse("2026-09-26T09:00:00Z") : undefined),
        assignmentIn("assignment-content", "content-check"),
    ],
    worksheets: [sheetOf("assignment-records", recordsDecided ? 2 : 1), sheetOf("assignment-content", 1)],
});

const renderReview = (requestId: string, reviewId: string) =>
    renderHook(({routedRequest, routedReview}) => useInformationRequestReview(routedRequest, routedReview), {
        initialProps: {routedRequest: requestId, routedReview: reviewId},
    });

describe("useInformationRequestReview", () => {
    beforeEach(() => {
        vi.resetAllMocks();
    });

    afterEach(cleanup);

    it("loads each routed review in turn", async () => {
        vi.mocked(transport.getInformationRequestReview).mockImplementation(async (requestId, reviewId) => reviewFor(requestId, reviewId));
        const hook = renderReview("request-a", "review-a");
        await waitFor(() => expect(hook.result.current.review?.review.id).toBe("review-a"));

        hook.rerender({routedRequest: "request-b", routedReview: "review-b"});

        await waitFor(() => expect(hook.result.current.review?.review.id).toBe("review-b"));
        expect(hook.result.current.scope?.worksheet.assignmentId).toBe("assignment-of-review-b");
    });

    it("shows nothing of the previous review while the next one loads or after its read is refused", async () => {
        const next = deferred<InformationRequestReviewDto>();
        vi.mocked(transport.getInformationRequestReview)
            .mockResolvedValueOnce(reviewFor("request-a", "review-a"))
            .mockReturnValueOnce(next.promise);
        const hook = renderReview("request-a", "review-a");
        await waitFor(() => expect(hook.result.current.review?.review.id).toBe("review-a"));
        act(() => hook.result.current.updateDraft("review-a-item-1", {outcome: InformationRequestReviewOutcome.SATISFIED}));

        hook.rerender({routedRequest: "request-b", routedReview: "review-b"});

        expect(hook.result.current.review).toBeNull();
        expect(hook.result.current.scope).toBeNull();
        expect(hook.result.current.drafts).toEqual({});
        expect(hook.result.current.changed).toEqual([]);
        await act(async () => next.reject({errorMessage: "Request B access refused"}));
        expect(hook.result.current.message?.text).toBe("Request B access refused");
        expect(hook.result.current.review).toBeNull();
        expect(hook.result.current.scope).toBeNull();
    });

    it("ignores a slower read of an earlier review once the later review has loaded", async () => {
        const earlier = deferred<InformationRequestReviewDto>();
        vi.mocked(transport.getInformationRequestReview)
            .mockReturnValueOnce(earlier.promise)
            .mockResolvedValueOnce(reviewFor("request-b", "review-b"));
        const hook = renderReview("request-a", "review-a");

        hook.rerender({routedRequest: "request-b", routedReview: "review-b"});
        await waitFor(() => expect(hook.result.current.review?.review.id).toBe("review-b"));
        await act(async () => earlier.resolve(reviewFor("request-a", "review-a", satisfiedEntries("review-a"))));

        expect(hook.result.current.review?.review.id).toBe("review-b");
        expect(hook.result.current.drafts).toEqual({});
    });

    it("sends no worksheet command until the routed review has loaded", async () => {
        const next = deferred<InformationRequestReviewDto>();
        vi.mocked(transport.getInformationRequestReview)
            .mockResolvedValueOnce(reviewFor("request-a", "review-a", satisfiedEntries("review-a")))
            .mockReturnValueOnce(next.promise);
        const hook = renderReview("request-a", "review-a");
        await waitFor(() => expect(hook.result.current.scope).not.toBeNull());
        act(() => hook.result.current.updateDraft("review-a-item-1", {outcome: InformationRequestReviewOutcome.WAIVED}));

        hook.rerender({routedRequest: "request-b", routedReview: "review-b"});
        await act(async () => {
            await hook.result.current.saveWorksheet();
            await hook.result.current.recordDecisions();
            await hook.result.current.recordFinding({
                submissionItemId: "review-a-item-1",
                reasonCode: "record.incomplete",
                narrative: "A page is missing",
                severity: InformationRequestFindingSeverity.MAJOR,
                visibility: InformationRequestReviewVisibility.RESPONDENT_VISIBLE,
                correctionScope: InformationRequestFindingCorrectionScope.RESPONSE,
            });
        });

        expect(transport.saveInformationRequestReviewWorksheet).not.toHaveBeenCalled();
        expect(transport.recordInformationRequestReviewDecisions).not.toHaveBeenCalled();
        expect(transport.recordInformationRequestReviewFinding).not.toHaveBeenCalled();
        await act(async () => next.resolve(reviewFor("request-b", "review-b")));
        expect(hook.result.current.drafts).toEqual({});
        expect(hook.result.current.changed).toEqual([]);
    });

    it("keeps unsaved worksheet edits when the same review reloads", async () => {
        vi.mocked(transport.getInformationRequestReview).mockImplementation(async (requestId, reviewId) => reviewFor(requestId, reviewId));
        const hook = renderReview("request-a", "review-a");
        await waitFor(() => expect(hook.result.current.scope).not.toBeNull());
        act(() => hook.result.current.updateDraft("review-a-item-1", {outcome: InformationRequestReviewOutcome.SATISFIED}));

        await act(async () => hook.result.current.load());

        expect(transport.getInformationRequestReview).toHaveBeenCalledTimes(2);
        expect(hook.result.current.drafts["review-a-item-1"]?.outcome).toBe(InformationRequestReviewOutcome.SATISFIED);
        expect(hook.result.current.changed).toEqual(["review-a-item-1"]);
    });

    it("lets a worksheet save that finishes after the route moved on change nothing on the later review", async () => {
        const save = deferred<RuntimeCommandResult<InformationRequestReviewWorksheetDto>>();
        vi.mocked(transport.getInformationRequestReview).mockImplementation(async (requestId, reviewId) => reviewFor(requestId, reviewId));
        vi.mocked(transport.saveInformationRequestReviewWorksheet).mockReturnValueOnce(save.promise);
        const hook = renderReview("request-a", "review-a");
        await waitFor(() => expect(hook.result.current.scope).not.toBeNull());
        act(() => hook.result.current.updateDraft("review-a-item-1", {outcome: InformationRequestReviewOutcome.SATISFIED}));
        let pending: Promise<unknown> = Promise.resolve();
        act(() => {
            pending = hook.result.current.saveWorksheet();
        });

        hook.rerender({routedRequest: "request-b", routedReview: "review-b"});
        await waitFor(() => expect(hook.result.current.review?.review.id).toBe("review-b"));
        await act(async () => {
            save.resolve(savedWorksheet("review-a", 2));
            await pending;
        });

        expect(hook.result.current.review?.worksheets).toEqual(reviewFor("request-b", "review-b").worksheets);
        expect(hook.result.current.message).toBeNull();
        expect(hook.result.current.busy).toBe(false);
    });

    it("records decisions under the key of an attempt whose response was lost and recovers the recorded result", async () => {
        vi.mocked(transport.getInformationRequestReview)
            .mockResolvedValueOnce(reviewFor("request-a", "review-a", satisfiedEntries("review-a")))
            .mockResolvedValue(reviewFor("request-a", "review-a", satisfiedEntries("review-a"), InformationRequestReviewState.SATISFIED));
        vi.mocked(transport.recordInformationRequestReviewDecisions)
            .mockRejectedValueOnce({errorMessage: "The response was lost"})
            .mockResolvedValueOnce(recorded("request-a", "review-a"));
        const hook = renderReview("request-a", "review-a");
        await waitFor(() => expect(hook.result.current.scope).not.toBeNull());

        await act(async () => hook.result.current.recordDecisions());
        expect(hook.result.current.message?.text).toBe("The response was lost");
        await act(async () => hook.result.current.recordDecisions());

        const [first, retried] = vi.mocked(transport.recordInformationRequestReviewDecisions).mock.calls;
        expect(first[3].idempotencyKey).toMatch(/\S/);
        expect(retried.slice(0, 3)).toEqual(["request-a", "review-a", "assignment-of-review-a"]);
        expect(retried[3]).toEqual(first[3]);
        expect(hook.result.current.message?.text).toBe("Decisions recorded. This review is satisfied.");
        expect(hook.result.current.review?.review.state).toBe(InformationRequestReviewState.SATISFIED);
        expect(transport.saveInformationRequestReviewWorksheet).not.toHaveBeenCalled();
    });

    it("keys decisions afresh once the worksheet they decide has changed", async () => {
        vi.mocked(transport.getInformationRequestReview).mockResolvedValue(reviewFor("request-a", "review-a", satisfiedEntries("review-a")));
        vi.mocked(transport.recordInformationRequestReviewDecisions).mockRejectedValue({errorMessage: "The response was lost"});
        vi.mocked(transport.saveInformationRequestReviewWorksheet).mockResolvedValue(savedWorksheet("review-a", 2));
        const hook = renderReview("request-a", "review-a");
        await waitFor(() => expect(hook.result.current.scope).not.toBeNull());

        await act(async () => hook.result.current.recordDecisions());
        act(() => hook.result.current.updateDraft("review-a-item-2", {narrative: "Checked against the register"}));
        await act(async () => hook.result.current.recordDecisions());

        const [first, changed] = vi.mocked(transport.recordInformationRequestReviewDecisions).mock.calls;
        expect(first[3].expectedETag).toBe("\"review-a-draft:1\"");
        expect(changed[3].expectedETag).toBe("\"review-a-draft:2\"");
        expect(changed[3].idempotencyKey).not.toBe(first[3].idempotencyKey);
    });

    it("keeps a saved worksheet when a read that began before the save answers after it", async () => {
        const earlierRead = deferred<InformationRequestReviewDto>();
        vi.mocked(transport.getInformationRequestReview)
            .mockResolvedValueOnce(reviewFor("request-a", "review-a"))
            .mockReturnValueOnce(earlierRead.promise);
        vi.mocked(transport.saveInformationRequestReviewWorksheet).mockResolvedValueOnce(saved(worksheetAt("review-a", 2, narrated("Saved narrative"))));
        const hook = renderReview("request-a", "review-a");
        await waitFor(() => expect(hook.result.current.scope).not.toBeNull());
        act(() => hook.result.current.updateDraft("review-a-item-1", {
            outcome: InformationRequestReviewOutcome.SATISFIED,
            narrative: "Saved narrative"
        }));
        let reading: Promise<void> = Promise.resolve();
        act(() => {
            reading = hook.result.current.load();
        });
        await act(async () => {
            await hook.result.current.saveWorksheet();
        });
        expect(hook.result.current.scope?.worksheet.draftETag).toBe("\"review-a-draft:2\"");

        await act(async () => {
            earlierRead.resolve({...reviewFor("request-a", "review-a"), itemsAwaitingStepUp: 1});
            await reading;
        });

        expect(hook.result.current.scope?.worksheet.draftETag).toBe("\"review-a-draft:2\"");
        expect(hook.result.current.drafts["review-a-item-1"]).toEqual({
            outcome: InformationRequestReviewOutcome.SATISFIED,
            narrative: "Saved narrative"
        });
        expect(hook.result.current.changed).toEqual([]);
        expect(hook.result.current.review?.itemsAwaitingStepUp).toBe(1);
    });

    it("keeps a newer worksheet read while a save was pending and shows its choices once that save answers", async () => {
        const save = deferred<RuntimeCommandResult<InformationRequestReviewWorksheetDto>>();
        vi.mocked(transport.getInformationRequestReview)
            .mockResolvedValueOnce(reviewFor("request-a", "review-a"))
            .mockResolvedValueOnce({
                ...reviewFor("request-a", "review-a"),
                worksheets: [worksheetAt("review-a", 3, narrated("Newer narrative"))]
            });
        vi.mocked(transport.saveInformationRequestReviewWorksheet).mockReturnValueOnce(save.promise);
        const hook = renderReview("request-a", "review-a");
        await waitFor(() => expect(hook.result.current.scope).not.toBeNull());
        act(() => hook.result.current.updateDraft("review-a-item-1", {
            outcome: InformationRequestReviewOutcome.SATISFIED,
            narrative: "Saved narrative"
        }));
        let saving: Promise<string | null> = Promise.resolve(null);
        act(() => {
            saving = hook.result.current.saveWorksheet();
        });
        await act(async () => hook.result.current.load());
        expect(hook.result.current.scope?.worksheet.draftETag).toBe("\"review-a-draft:3\"");
        expect(hook.result.current.drafts["review-a-item-1"]?.narrative).toBe("Saved narrative");

        let answered: string | null = "";
        await act(async () => {
            save.resolve(saved(worksheetAt("review-a", 2, narrated("Saved narrative"))));
            answered = await saving;
        });

        expect(answered).toBeNull();
        expect(hook.result.current.scope?.worksheet.draftETag).toBe("\"review-a-draft:3\"");
        expect(hook.result.current.drafts["review-a-item-1"]).toEqual({
            outcome: InformationRequestReviewOutcome.SATISFIED,
            narrative: "Newer narrative"
        });
        expect(hook.result.current.changed).toEqual([]);
        expect(hook.result.current.message).toEqual({
            intent: "warning",
            text: "This worksheet changed elsewhere while it was being saved. The latest saved choices are shown.",
        });
    });

    it("records no decisions over a worksheet that changed elsewhere while it was being saved", async () => {
        const save = deferred<RuntimeCommandResult<InformationRequestReviewWorksheetDto>>();
        vi.mocked(transport.getInformationRequestReview)
            .mockResolvedValueOnce(reviewFor("request-a", "review-a", satisfiedEntries("review-a")))
            .mockResolvedValueOnce({
                ...reviewFor("request-a", "review-a"),
                worksheets: [worksheetAt("review-a", 3, satisfiedEntries("review-a"))]
            });
        vi.mocked(transport.saveInformationRequestReviewWorksheet).mockReturnValueOnce(save.promise);
        const hook = renderReview("request-a", "review-a");
        await waitFor(() => expect(hook.result.current.scope).not.toBeNull());
        act(() => hook.result.current.updateDraft("review-a-item-2", {narrative: "Checked against the register"}));
        let recording: Promise<void> = Promise.resolve();
        act(() => {
            recording = hook.result.current.recordDecisions();
        });
        await act(async () => hook.result.current.load());

        await act(async () => {
            save.resolve(savedWorksheet("review-a", 2));
            await recording;
        });

        expect(transport.recordInformationRequestReviewDecisions).not.toHaveBeenCalled();
        expect(hook.result.current.message?.intent).toBe("warning");
        expect(hook.result.current.scope?.worksheet.draftETag).toBe("\"review-a-draft:3\"");
    });

    it("keeps an edit made while its save was pending as an unsaved change", async () => {
        const save = deferred<RuntimeCommandResult<InformationRequestReviewWorksheetDto>>();
        vi.mocked(transport.getInformationRequestReview).mockImplementation(async (requestId, reviewId) => reviewFor(requestId, reviewId));
        vi.mocked(transport.saveInformationRequestReviewWorksheet).mockReturnValueOnce(save.promise);
        const hook = renderReview("request-a", "review-a");
        await waitFor(() => expect(hook.result.current.scope).not.toBeNull());
        act(() => hook.result.current.updateDraft("review-a-item-1", {
            outcome: InformationRequestReviewOutcome.SATISFIED,
            narrative: "First note"
        }));
        let saving: Promise<string | null> = Promise.resolve(null);
        act(() => {
            saving = hook.result.current.saveWorksheet();
        });
        act(() => hook.result.current.updateDraft("review-a-item-1", {narrative: "Second note"}));

        await act(async () => {
            save.resolve(saved(worksheetAt("review-a", 2, narrated("First note"))));
            await saving;
        });

        expect(hook.result.current.scope?.worksheet.draftETag).toBe("\"review-a-draft:2\"");
        expect(hook.result.current.drafts["review-a-item-1"]).toEqual({
            outcome: InformationRequestReviewOutcome.SATISFIED,
            narrative: "Second note"
        });
        expect(hook.result.current.changed).toEqual(["review-a-item-1"]);
    });

    it("keeps a reason typed before an outcome and asks for the outcome instead of saving the reason away", async () => {
        vi.mocked(transport.getInformationRequestReview).mockImplementation(async (requestId, reviewId) => reviewFor(requestId, reviewId));
        const hook = renderReview("request-a", "review-a");
        await waitFor(() => expect(hook.result.current.scope).not.toBeNull());
        act(() => hook.result.current.updateDraft("review-a-item-1", {narrative: "Checked against page 2"}));

        let answer: string | null = "not saved yet";
        await act(async () => {
            answer = await hook.result.current.saveWorksheet();
        });

        expect(answer).toBeNull();
        expect(transport.saveInformationRequestReviewWorksheet).not.toHaveBeenCalled();
        expect(hook.result.current.drafts["review-a-item-1"]).toEqual({narrative: "Checked against page 2"});
        expect(hook.result.current.changed).toEqual(["review-a-item-1"]);
        expect(hook.result.current.message?.text).toBe("Choose an outcome for each item you gave a reason for, then save again.");
    });

    it("leaves a review withheld when a save answers after the review could no longer be read", async () => {
        const save = deferred<RuntimeCommandResult<InformationRequestReviewWorksheetDto>>();
        vi.mocked(transport.getInformationRequestReview)
            .mockResolvedValueOnce(reviewFor("request-a", "review-a"))
            .mockRejectedValueOnce({errorMessage: "Review access was removed"});
        vi.mocked(transport.saveInformationRequestReviewWorksheet).mockReturnValueOnce(save.promise);
        const hook = renderReview("request-a", "review-a");
        await waitFor(() => expect(hook.result.current.scope).not.toBeNull());
        act(() => hook.result.current.updateDraft("review-a-item-1", {outcome: InformationRequestReviewOutcome.SATISFIED}));
        let saving: Promise<string | null> = Promise.resolve(null);
        act(() => {
            saving = hook.result.current.saveWorksheet();
        });
        await act(async () => hook.result.current.load());

        await act(async () => {
            save.resolve(savedWorksheet("review-a", 2));
            await saving;
        });

        expect(hook.result.current.review).toBeNull();
        expect(hook.result.current.scope).toBeNull();
        expect(hook.result.current.message).toEqual({intent: "error", text: "Review access was removed"});
    });
});

describe("useInformationRequestReview with several assignments", () => {
    beforeEach(() => {
        vi.resetAllMocks();
    });

    afterEach(cleanup);

    it("works in the stage still open once the reviewer's first assignment has recorded its decisions", async () => {
        vi.mocked(transport.getInformationRequestReview).mockResolvedValue(sequentialStages());
        const hook = renderReview("request-a", "review-a");

        await waitFor(() => expect(hook.result.current.review).not.toBeNull());

        expect(hook.result.current.scope).toEqual({
            worksheet: sheetOf("assignment-further", 1),
            stageKey: "further-check",
            itemIds: ["review-a-item-1"],
        });
        expect(hook.result.current.drafts).toEqual({});
        expect(hook.result.current.choices.map(choice => choice.assignmentId)).toEqual(["assignment-further"]);
    });

    it("works in the same open assignment whatever order the worksheets arrive in", async () => {
        const review = sequentialStages();
        vi.mocked(transport.getInformationRequestReview).mockResolvedValue({
            ...review,
            worksheets: [...review.worksheets].reverse()
        });
        const hook = renderReview("request-a", "review-a");

        await waitFor(() => expect(hook.result.current.review).not.toBeNull());

        expect(hook.result.current.scope?.worksheet.assignmentId).toBe("assignment-further");
        expect(hook.result.current.drafts).toEqual({});
    });

    it("opens the first open stage, moves to a chosen assignment, and keeps each assignment's unsaved choices apart", async () => {
        vi.mocked(transport.getInformationRequestReview).mockResolvedValue(parallelStages());
        const hook = renderReview("request-a", "review-a");
        await waitFor(() => expect(hook.result.current.scope).not.toBeNull());

        expect(hook.result.current.scope?.worksheet.assignmentId).toBe("assignment-content");
        expect(hook.result.current.choices).toEqual([
            {assignmentId: "assignment-content", stageTitle: "Check 1", position: 1, unsavedCount: 0},
            {assignmentId: "assignment-records", stageTitle: "Check 2", position: 2, unsavedCount: 0},
        ]);
        act(() => hook.result.current.updateDraft("review-a-item-1", {outcome: InformationRequestReviewOutcome.SATISFIED}));

        act(() => hook.result.current.chooseAssignment("assignment-records"));
        expect(hook.result.current.scope).toEqual({
            worksheet: sheetOf("assignment-records", 1),
            stageKey: "record-check",
            itemIds: ["review-a-item-2"],
        });
        expect(hook.result.current.drafts).toEqual({});
        expect(hook.result.current.changed).toEqual([]);
        act(() => hook.result.current.updateDraft("review-a-item-2", {
            outcome: InformationRequestReviewOutcome.WAIVED,
            narrative: "Not kept"
        }));

        act(() => hook.result.current.chooseAssignment("assignment-content"));
        expect(hook.result.current.drafts).toEqual({
            "review-a-item-1": {
                outcome: InformationRequestReviewOutcome.SATISFIED,
                narrative: ""
            }
        });
        expect(hook.result.current.changed).toEqual(["review-a-item-1"]);
        expect(hook.result.current.choices.map(choice => choice.unsavedCount)).toEqual([1, 1]);
    });

    it("saves and records only the chosen assignment's choices under its own worksheet revision", async () => {
        vi.mocked(transport.getInformationRequestReview)
            .mockResolvedValueOnce(parallelStages())
            .mockResolvedValue(parallelStages(true));
        vi.mocked(transport.saveInformationRequestReviewWorksheet).mockResolvedValue(saved(sheetOf("assignment-records", 2, [
            {submissionItemId: "review-a-item-2", outcome: InformationRequestReviewOutcome.SATISFIED},
        ])));
        vi.mocked(transport.recordInformationRequestReviewDecisions).mockResolvedValue(recorded("request-a", "review-a"));
        const hook = renderReview("request-a", "review-a");
        await waitFor(() => expect(hook.result.current.scope).not.toBeNull());
        act(() => hook.result.current.updateDraft("review-a-item-1", {outcome: InformationRequestReviewOutcome.SATISFIED}));
        act(() => hook.result.current.chooseAssignment("assignment-records"));
        act(() => hook.result.current.updateDraft("review-a-item-2", {outcome: InformationRequestReviewOutcome.SATISFIED}));

        await act(async () => hook.result.current.recordDecisions());

        expect(transport.saveInformationRequestReviewWorksheet).toHaveBeenCalledWith("request-a", "review-a", "assignment-records", {
            entries: [{
                submissionItemId: "review-a-item-2",
                outcome: InformationRequestReviewOutcome.SATISFIED,
                narrative: undefined
            }],
        }, "\"assignment-records:1\"");
        expect(transport.recordInformationRequestReviewDecisions).toHaveBeenCalledWith("request-a", "review-a", "assignment-records", {
            expectedETag: "\"assignment-records:2\"",
            idempotencyKey: expect.any(String),
        });
        expect(hook.result.current.scope?.worksheet.assignmentId).toBe("assignment-content");
        expect(hook.result.current.drafts).toEqual({
            "review-a-item-1": {
                outcome: InformationRequestReviewOutcome.SATISFIED,
                narrative: ""
            }
        });
        expect(hook.result.current.changed).toEqual(["review-a-item-1"]);
    });

    it("offers no assignment in a stage the reviewer already decided, a stage not yet open, or a stage that excludes earlier reviewers", async () => {
        vi.mocked(transport.getInformationRequestReview).mockResolvedValue({
            ...reviewFor("request-a", "review-a"),
            stages: [
                stageOf("review", 1, InformationRequestReviewStageState.OPEN, ["review-a-item-1"]),
                stageOf("independent", 2, InformationRequestReviewStageState.OPEN, ["review-a-item-1"], true),
                stageOf("later", 3, InformationRequestReviewStageState.WAITING, ["review-a-item-2"]),
            ],
            assignments: [
                assignmentIn("assignment-direct", "review"),
                assignmentIn("assignment-group", "review", Date.parse("2026-09-26T09:00:00Z")),
                assignmentIn("assignment-independent", "independent"),
                assignmentIn("assignment-later", "later"),
            ],
            decisions: [decidedThrough("assignment-group", "review")],
            worksheets: [
                sheetOf("assignment-direct", 1),
                sheetOf("assignment-independent", 1),
                sheetOf("assignment-later", 1),
                sheetOf("assignment-group", 2),
            ],
        });
        const hook = renderReview("request-a", "review-a");

        await waitFor(() => expect(hook.result.current.review).not.toBeNull());

        expect(hook.result.current.scope).toBeNull();
        expect(hook.result.current.choices).toEqual([]);
    });
});
