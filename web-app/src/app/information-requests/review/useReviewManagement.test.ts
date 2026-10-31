/** @vitest-environment jsdom */
import {act, cleanup, renderHook, waitFor} from "@testing-library/react";
import {afterEach, beforeEach, describe, expect, it, vi} from "vitest";
import * as authoring from "../../../services/informationRequestAuthoringService.ts";
import * as transport from "../../../services/informationRequestReviewService.ts";
import {RuntimeCommandResult} from "../../../services/informationRequestRuntimeService.ts";
import {
    InformationRequestPartyListingDto,
    InformationRequestReviewAggregation,
    InformationRequestReviewCommandResultDto,
    InformationRequestReviewDto,
    InformationRequestReviewKind,
    InformationRequestReviewOutcome,
    InformationRequestReviewStageOrdering,
    InformationRequestReviewStageState,
    InformationRequestReviewState,
    InformationRequestReviewTieResolution,
    InformationRequestShareRoleKey,
    InformationRequestState,
} from "../../models/models.tsx";
import {useReviewManagement} from "./useReviewManagement.ts";

vi.mock("../../../services/informationRequestReviewService.ts", () => ({
    assignInformationRequestReviewer: vi.fn(),
    changeInformationRequestReviewAssignment: vi.fn(),
    overrideInformationRequestReviewItem: vi.fn(),
    reconsiderInformationRequestReview: vi.fn(),
    recordInformationRequestReviewComment: vi.fn(),
}));
vi.mock("../../../services/informationRequestAuthoringService.ts", () => ({getInformationRequestParties: vi.fn()}));

const reviewFor = (requestId: string, reviewId: string): InformationRequestReviewDto => ({
    review: {
        id: reviewId,
        informationRequestId: requestId,
        packageId: `package-of-${reviewId}`,
        packageNumber: 1,
        reviewNumber: 1,
        kind: InformationRequestReviewKind.INITIAL,
        state: InformationRequestReviewState.CHANGES_REQUESTED,
        openedAt: Date.parse("2026-09-26T08:00:00Z"),
        reviewETag: `"${reviewId}:2"`,
    },
    reviewStageOrdering: InformationRequestReviewStageOrdering.SEQUENTIAL,
    stages: [{
        stageKey: "content",
        title: "Content review",
        position: 1,
        aggregation: InformationRequestReviewAggregation.ANY,
        minimumReviewerCount: 1,
        tieResolution: InformationRequestReviewTieResolution.MOST_SEVERE_OUTCOME,
        overridePermitted: true,
        excludesResponseParties: false,
        excludesPriorReviewers: false,
        state: InformationRequestReviewStageState.OPEN,
        items: [],
    }],
    items: [],
    assignments: [],
    decisions: [],
    findings: [],
    comments: [],
    remediations: [],
    worksheets: [],
    canManage: true,
    earlierFindings: [],
    earlierRemediations: [],
    canReconsider: true,
    canComment: true,
    itemsAwaitingStepUp: 0,
});

const partiesOf = (requestId: string): InformationRequestPartyListingDto => ({
    partiesETag: `"${requestId}-parties"`,
    parties: [{
        id: `reviewer-of-${requestId}`,
        informationRequestId: requestId,
        roleKey: InformationRequestShareRoleKey.REVIEWER,
        active: true,
        assignedAt: Date.parse("2026-09-26T08:00:00Z"),
        partyRevision: 1,
        partyETag: `"reviewer-of-${requestId}-1"`,
        label: `Reviewer of ${requestId}`,
    }],
});

const saved = (requestId: string, reviewId: string): RuntimeCommandResult<InformationRequestReviewCommandResultDto> => ({
    outcome: "SAVED",
    responseETag: "\"responses:3\"",
    data: {
        requestState: InformationRequestState.ISSUED,
        responseETag: "\"responses:3\"",
        review: reviewFor(requestId, reviewId).review
    },
});

const deferred = <T>() => {
    let resolve!: (value: T) => void;
    const promise = new Promise<T>(onResolve => {
        resolve = onResolve;
    });
    return {promise, resolve};
};

const lost = {errorMessage: "The response was lost"};

describe("useReviewManagement", () => {
    const reload = vi.fn(async () => undefined);

    beforeEach(() => {
        vi.resetAllMocks();
        reload.mockImplementation(async () => undefined);
        vi.mocked(authoring.getInformationRequestParties).mockImplementation(async requestId => partiesOf(requestId));
    });

    afterEach(cleanup);

    it("sends no command without a loaded review of the routed request", async () => {
        const unloaded = renderHook(() => useReviewManagement("request-b", null, reload));
        const foreign = renderHook(() => useReviewManagement("request-b", reviewFor("request-a", "review-a"), reload));

        await act(async () => {
            for (const management of [unloaded.result.current, foreign.result.current]) {
                await management.assign({stageKey: "content", reviewerPartyId: "reviewer-of-request-b"});
                await management.reconsider("New information arrived");
                await management.override({
                    stageKey: "content",
                    submissionItemId: "item-a",
                    outcome: InformationRequestReviewOutcome.SATISFIED,
                    narrative: "Confirmed",
                });
            }
        });

        expect(transport.assignInformationRequestReviewer).not.toHaveBeenCalled();
        expect(transport.reconsiderInformationRequestReview).not.toHaveBeenCalled();
        expect(transport.overrideInformationRequestReviewItem).not.toHaveBeenCalled();
    });

    it("offers no reviewer party of an earlier request once the route moved on", async () => {
        const later = deferred<InformationRequestPartyListingDto>();
        vi.mocked(authoring.getInformationRequestParties)
            .mockResolvedValueOnce(partiesOf("request-a"))
            .mockReturnValueOnce(later.promise);
        const hook = renderHook(({requestId, review}) => useReviewManagement(requestId, review, reload), {
            initialProps: {requestId: "request-a", review: reviewFor("request-a", "review-a")},
        });
        await waitFor(() => expect(hook.result.current.parties.map(party => party.id)).toEqual(["reviewer-of-request-a"]));

        hook.rerender({requestId: "request-b", review: reviewFor("request-b", "review-b")});

        expect(hook.result.current.parties).toEqual([]);
        await act(async () => later.resolve(partiesOf("request-b")));
        expect(hook.result.current.parties.map(party => party.id)).toEqual(["reviewer-of-request-b"]);
    });

    it("retries each management command under the key of an attempt whose response was lost", async () => {
        vi.mocked(transport.assignInformationRequestReviewer).mockRejectedValueOnce(lost).mockResolvedValueOnce(saved("request-a", "review-a"));
        vi.mocked(transport.changeInformationRequestReviewAssignment).mockRejectedValueOnce(lost).mockResolvedValueOnce(saved("request-a", "review-a"));
        vi.mocked(transport.overrideInformationRequestReviewItem).mockRejectedValueOnce(lost).mockResolvedValueOnce(saved("request-a", "review-a"));
        vi.mocked(transport.reconsiderInformationRequestReview).mockRejectedValueOnce(lost).mockResolvedValueOnce(saved("request-a", "review-a"));
        const hook = renderHook(() => useReviewManagement("request-a", reviewFor("request-a", "review-a"), reload));
        const twice = async (command: () => Promise<boolean>) => {
            await act(async () => command());
            await act(async () => command());
        };

        await twice(() => hook.result.current.assign({stageKey: "content", reviewerPartyId: "reviewer-of-request-a"}));
        await twice(() => hook.result.current.changeAssignment("assignment-a", "recusal", {reasonCode: "CONFLICT"}));
        await twice(() => hook.result.current.override({
            stageKey: "content",
            submissionItemId: "item-a",
            outcome: InformationRequestReviewOutcome.SATISFIED,
            narrative: "Confirmed",
        }));
        await twice(() => hook.result.current.reconsider("New information arrived"));

        for (const command of [
            transport.assignInformationRequestReviewer,
            transport.changeInformationRequestReviewAssignment,
            transport.overrideInformationRequestReviewItem,
            transport.reconsiderInformationRequestReview,
        ]) {
            const [first, retried] = vi.mocked(command).mock.calls;
            const options = (call: unknown[]) => call[call.length - 1] as {
                expectedETag: string;
                idempotencyKey: string
            };
            expect(options(first).idempotencyKey).toMatch(/\S/);
            expect(options(retried)).toEqual(options(first));
            expect(retried.slice(0, 2)).toEqual(["request-a", "review-a"]);
        }
        expect(hook.result.current.message?.text).toBe("A reconsideration was opened.");
    });

    it("keys the same management command afresh once an attempt was answered", async () => {
        vi.mocked(transport.reconsiderInformationRequestReview).mockResolvedValue(saved("request-a", "review-a"));
        const hook = renderHook(() => useReviewManagement("request-a", reviewFor("request-a", "review-a"), reload));

        await act(async () => hook.result.current.reconsider("New information arrived"));
        await act(async () => hook.result.current.reconsider("New information arrived"));

        const [first, next] = vi.mocked(transport.reconsiderInformationRequestReview).mock.calls;
        expect(next[3].idempotencyKey).not.toBe(first[3].idempotencyKey);
    });
});
