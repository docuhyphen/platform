// @vitest-environment jsdom
import {AxiosError, AxiosHeaders} from "axios";
import {beforeEach, describe, expect, it, vi} from "vitest";
import apiClient from "../apiClient.ts";
import {
    appealInformationRequestReview,
    assignInformationRequestReviewer,
    changeInformationRequestReviewAssignment,
    getInformationRequestReview,
    getInformationRequestReviewQueue,
    getInformationRequestReviewResults,
    recordInformationRequestReviewDecisions,
    recordInformationRequestReviewFinding,
    saveInformationRequestReviewWorksheet,
} from "../informationRequestReviewService.ts";
import {storeInformationRequestSessionToken} from "../informationRequestRuntimeService.ts";
import {
    InformationRequestFindingCorrectionScope,
    InformationRequestFindingSeverity,
    InformationRequestReviewOutcome,
    InformationRequestReviewVisibility,
} from "../../app/models/models.tsx";

vi.mock("../apiClient.ts", () => ({default: {get: vi.fn(), post: vi.fn(), patch: vi.fn()}}));

const refusal = (status: number, data: object) =>
{
    const error = new AxiosError("Refused", String(status));
    error.response = {status, data, statusText: "", headers: {}, config: {headers: new AxiosHeaders()}};
    return error;
};

describe("Information Request review transport", () =>
{
    beforeEach(() =>
    {
        vi.clearAllMocks();
        window.sessionStorage.clear();
    });

    it("reads a review, the caller's queue, and respondent results on both access surfaces", async () =>
    {
        vi.mocked(apiClient.get).mockResolvedValue({data: []});

        await getInformationRequestReview("request-a", "review-a");
        expect(apiClient.get).toHaveBeenLastCalledWith("/information-requests/request-a/reviews/review-a");
        await getInformationRequestReviewQueue();
        expect(apiClient.get).toHaveBeenLastCalledWith("/information-request-reviews");
        await getInformationRequestReviewResults("request-a");
        expect(apiClient.get).toHaveBeenLastCalledWith("/information-requests/request-a/review-results", {headers: undefined});

        storeInformationRequestSessionToken("request-a", "session-secret");
        await getInformationRequestReviewResults("request-a", "bootstrap");
        expect(apiClient.get).toHaveBeenLastCalledWith("/no-auth/information-requests/request-a/review-results", {
            headers: {"X-Request-Access-Token": "bootstrap", "X-Request-Session-Token": "session-secret"},
        });
    });

    it("saves a worksheet under its draft revision and reports a stale draft instead of retrying", async () =>
    {
        vi.mocked(apiClient.patch).mockResolvedValueOnce({data: {assignmentId: "assignment-a", draftETag: "\"draft:2\"", entries: []}, headers: {etag: "\"draft:2\""}});

        const saved = await saveInformationRequestReviewWorksheet(
            "request-a",
            "review-a",
            "assignment-a",
            {entries: [{submissionItemId: "item-a", outcome: InformationRequestReviewOutcome.SATISFIED}]},
            "\"draft:1\"",
        );

        expect(saved).toEqual({outcome: "SAVED", responseETag: "\"draft:2\"", data: {assignmentId: "assignment-a", draftETag: "\"draft:2\"", entries: []}});
        expect(apiClient.patch).toHaveBeenLastCalledWith(
            "/information-requests/request-a/reviews/review-a/assignments/assignment-a/worksheet",
            {entries: [{submissionItemId: "item-a", outcome: InformationRequestReviewOutcome.SATISFIED}]},
            {headers: {"If-Match": "\"draft:1\""}},
        );

        vi.mocked(apiClient.patch).mockRejectedValueOnce(refusal(412, {reasonCode: "COMMAND_PRECONDITION_STALE"}));
        expect(await saveInformationRequestReviewWorksheet("request-a", "review-a", "assignment-a", {entries: []}, "\"draft:1\""))
            .toEqual({outcome: "STALE"});
    });

    it("records the worksheet with its draft revision and a fresh key, and states a refusal", async () =>
    {
        vi.mocked(apiClient.post).mockResolvedValueOnce({data: {review: {id: "review-a"}}, headers: {etag: "\"review:4\""}});

        await recordInformationRequestReviewDecisions("request-a", "review-a", "assignment-a", {
            expectedETag: "\"draft:2\"",
            idempotencyKey: "record-key",
        });

        expect(apiClient.post).toHaveBeenLastCalledWith(
            "/information-requests/request-a/reviews/review-a/assignments/assignment-a/decisions",
            undefined,
            {headers: {"If-Match": "\"draft:2\"", "Idempotency-Key": "record-key"}},
        );

        const settled = {reasonCode: "INFORMATION_REQUEST_REVIEW_SETTLED", errorMessage: "This review has settled"};
        vi.mocked(apiClient.post).mockRejectedValueOnce(refusal(409, settled));
        await expect(recordInformationRequestReviewDecisions("request-a", "review-a", "assignment-a", {expectedETag: "\"x\"", idempotencyKey: "k"}))
            .rejects.toEqual(settled);
    });

    it("records a finding with only its key and appeals from the link surface with the review revision", async () =>
    {
        vi.mocked(apiClient.post).mockResolvedValue({data: {findingId: "finding-a"}, headers: {etag: "\"review:5\""}});

        await recordInformationRequestReviewFinding("request-a", "review-a", {
            submissionItemId: "item-a",
            reasonCode: "record.incomplete",
            narrative: "The record is incomplete",
            severity: InformationRequestFindingSeverity.MAJOR,
            visibility: InformationRequestReviewVisibility.RESPONDENT_VISIBLE,
            correctionScope: InformationRequestFindingCorrectionScope.RESPONSE,
        }, "finding-key");
        expect(apiClient.post).toHaveBeenLastCalledWith(
            "/information-requests/request-a/reviews/review-a/findings",
            expect.objectContaining({reasonCode: "record.incomplete"}),
            {headers: {"Idempotency-Key": "finding-key"}},
        );

        storeInformationRequestSessionToken("request-a", "session-secret");
        await appealInformationRequestReview("request-a", "review-a", {reason: "The file was complete"}, {
            expectedETag: "\"review:5\"",
            idempotencyKey: "appeal-key",
            accessLinkToken: "bootstrap",
        });
        expect(apiClient.post).toHaveBeenLastCalledWith(
            "/no-auth/information-requests/request-a/reviews/review-a/appeals",
            {reason: "The file was complete"},
            {
                headers: {
                    "If-Match": "\"review:5\"",
                    "Idempotency-Key": "appeal-key",
                    "X-Request-Access-Token": "bootstrap",
                    "X-Request-Session-Token": "session-secret",
                },
            },
        );
    });

    it("assigns a reviewer party to a stage and changes an assignment under the review revision", async () =>
    {
        vi.mocked(apiClient.post).mockResolvedValue({data: {assignmentId: "assignment-b"}, headers: {etag: "\"review:6\""}});
        const options = {expectedETag: "\"review:5\"", idempotencyKey: "assign-key"};

        await assignInformationRequestReviewer("request-a", "review-a", {stageKey: "content", reviewerPartyId: "party-r"}, options);
        expect(apiClient.post).toHaveBeenLastCalledWith(
            "/information-requests/request-a/reviews/review-a/assignments",
            {stageKey: "content", reviewerPartyId: "party-r"},
            {headers: {"If-Match": "\"review:5\"", "Idempotency-Key": "assign-key"}},
        );
        for (const change of ["recusal", "delegation", "revocation"] as const)
        {
            await changeInformationRequestReviewAssignment("request-a", "review-a", "assignment-a", change, {reasonCode: "CONFLICT"}, options);
            expect(apiClient.post).toHaveBeenLastCalledWith(
                `/information-requests/request-a/reviews/review-a/assignments/assignment-a/${change}`,
                {reasonCode: "CONFLICT"},
                {headers: {"If-Match": "\"review:5\"", "Idempotency-Key": "assign-key"}},
            );
        }
        vi.mocked(apiClient.post).mockRejectedValueOnce(refusal(412, {reasonCode: "COMMAND_PRECONDITION_STALE"}));
        expect(await changeInformationRequestReviewAssignment("request-a", "review-a", "assignment-a", "revocation", {}, options))
            .toEqual({outcome: "STALE"});
    });
});
