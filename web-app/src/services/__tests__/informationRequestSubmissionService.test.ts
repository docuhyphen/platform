// @vitest-environment jsdom
import {AxiosError, AxiosHeaders} from "axios";
import {beforeEach, describe, expect, it, vi} from "vitest";
import apiClient from "../apiClient.ts";
import {
    createInformationRequestSupplement,
    getInformationRequestAmendments,
    getInformationRequestCarryForwards,
    getInformationRequestSubmissionPreview,
    recordInformationRequestAttestation,
    submitInformationRequestPackage,
    withdrawInformationRequestPackage,
} from "../informationRequestSubmissionService.ts";
import {storeInformationRequestSessionToken} from "../informationRequestRuntimeService.ts";
import {InformationRequestAttestationDecision} from "../../app/models/models.tsx";

vi.mock("../apiClient.ts", () => ({default: {get: vi.fn(), post: vi.fn()}}));

const lastCall = (calls: unknown[][]): unknown => calls[calls.length - 1]?.[0];

const refusal = (status: number, data: object) =>
{
    const error = new AxiosError("Refused", String(status));
    error.response = {status, data, statusText: "", headers: {}, config: {headers: new AxiosHeaders()}};
    return error;
};

describe("Information Request submission transport", () =>
{
    beforeEach(() =>
    {
        vi.clearAllMocks();
        window.sessionStorage.clear();
    });

    it("reads review-before-submit for one stage on both access surfaces", async () =>
    {
        vi.mocked(apiClient.get).mockResolvedValue({data: {submissionETag: "\"submission:stage-one:abc\""}});

        const preview = await getInformationRequestSubmissionPreview("request-a", "stage-one");
        expect(preview.submissionETag).toBe("\"submission:stage-one:abc\"");
        expect(apiClient.get).toHaveBeenLastCalledWith("/information-requests/request-a/submission-preview", {
            params: {stageKey: "stage-one"},
            headers: undefined,
        });

        storeInformationRequestSessionToken("request-a", "session-secret");
        await getInformationRequestSubmissionPreview("request-a", undefined, "bootstrap");
        expect(apiClient.get).toHaveBeenLastCalledWith("/no-auth/information-requests/request-a/submission-preview", {
            params: {},
            headers: {"X-Request-Access-Token": "bootstrap", "X-Request-Session-Token": "session-secret"},
        });
    });

    it("submits under the reviewed submission ETag and a fresh key, and reports an incomplete scope", async () =>
    {
        vi.mocked(apiClient.post).mockResolvedValueOnce({data: {submission: {id: "package-a"}}, headers: {etag: "\"responses:2\""}});

        const outcome = await submitInformationRequestPackage("request-a", {stageKey: "stage-one"}, {
            expectedETag: "\"submission:stage-one:abc\"",
            idempotencyKey: "submit-key",
        });

        expect(outcome).toEqual({outcome: "SAVED", responseETag: "\"responses:2\"", data: {submission: {id: "package-a"}}});
        expect(apiClient.post).toHaveBeenLastCalledWith(
            "/information-requests/request-a/submissions",
            {stageKey: "stage-one"},
            {headers: {"If-Match": "\"submission:stage-one:abc\"", "Idempotency-Key": "submit-key"}},
        );

        const problems = {reasonCode: "INFORMATION_REQUEST_SUBMISSION_INCOMPLETE", problems: [], undisclosedProblemCount: 1};
        vi.mocked(apiClient.post).mockRejectedValueOnce(refusal(422, problems));
        await expect(submitInformationRequestPackage("request-a", {}, {expectedETag: "\"x\"", idempotencyKey: "k"}))
            .rejects.toEqual(problems);
    });

    it("withdraws a package and records an assertion on the exact Requirement", async () =>
    {
        vi.mocked(apiClient.post).mockResolvedValue({data: {}, headers: {etag: "\"e\""}});

        await withdrawInformationRequestPackage("request-a", "package-a", {reasonCode: "correction"}, {
            expectedETag: "\"responses:2\"",
            idempotencyKey: "withdraw-key",
        });
        expect(apiClient.post).toHaveBeenLastCalledWith(
            "/information-requests/request-a/submissions/package-a/withdrawal",
            {reasonCode: "correction"},
            {headers: {"If-Match": "\"responses:2\"", "Idempotency-Key": "withdraw-key"}},
        );

        await recordInformationRequestAttestation("request-a", "requirement-a", {decision: InformationRequestAttestationDecision.ASSENTED}, {
            expectedETag: "\"submission:whole:abc\"",
            idempotencyKey: "assent-key",
            accessLinkToken: "bootstrap",
        });
        expect(lastCall(vi.mocked(apiClient.post).mock.calls))
            .toBe("/no-auth/information-requests/request-a/requirements/requirement-a/attestations");
    });

    it("reads amendments and carry-forward offers and requests a supplement as the signed-in caller", async () =>
    {
        vi.mocked(apiClient.get).mockResolvedValue({data: []});
        vi.mocked(apiClient.post).mockResolvedValue({data: {successor: {id: "request-b"}}, headers: {etag: "\"request-b:1\""}});

        await getInformationRequestAmendments("request-a");
        expect(apiClient.get).toHaveBeenLastCalledWith("/information-requests/request-a/amendments", {headers: undefined});
        await getInformationRequestCarryForwards("request-a", "bootstrap");
        expect(lastCall(vi.mocked(apiClient.get).mock.calls)).toBe("/no-auth/information-requests/request-a/carry-forwards");

        const created = await createInformationRequestSupplement("request-a", "additional-records", {
            expectedETag: "\"request-a:4\"",
            idempotencyKey: "supplement-key",
        });
        expect(created.outcome).toBe("SAVED");
        expect(apiClient.post).toHaveBeenLastCalledWith(
            "/information-requests/request-a/successors",
            {kind: "SUPPLEMENT", reasonCode: "additional-records"},
            {headers: {"If-Match": "\"request-a:4\"", "Idempotency-Key": "supplement-key"}},
        );
    });
});
