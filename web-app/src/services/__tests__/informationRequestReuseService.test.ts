// @vitest-environment jsdom
import {beforeEach, describe, expect, it, vi} from "vitest";
import apiClient from "../apiClient.ts";
import {
    getInformationRequestAcceptedFactOffers,
    recertifyInformationRequestAcceptedFact,
} from "../informationRequestReuseService.ts";
import {storeInformationRequestSessionToken} from "../informationRequestRuntimeService.ts";

vi.mock("../apiClient.ts", () => ({default: {get: vi.fn(), post: vi.fn()}}));

describe("Information Request answer reuse transport", () =>
{
    beforeEach(() =>
    {
        vi.clearAllMocks();
        window.sessionStorage.clear();
        vi.mocked(apiClient.get).mockResolvedValue({data: [{requirementId: "requirement-a"}]});
        vi.mocked(apiClient.post).mockResolvedValue({data: {id: "recertification-a"}, headers: {etag: "\"responses-5\""}});
    });

    it("reads offers on the signed-in and access link surfaces", async () =>
    {
        expect(await getInformationRequestAcceptedFactOffers("request-a")).toEqual([{requirementId: "requirement-a"}]);
        expect(apiClient.get).toHaveBeenLastCalledWith("/information-requests/request-a/accepted-fact-offers", {headers: undefined});

        storeInformationRequestSessionToken("request-a", "session-a");
        await getInformationRequestAcceptedFactOffers("request-a", "link-a");
        expect(apiClient.get).toHaveBeenLastCalledWith("/no-auth/information-requests/request-a/accepted-fact-offers", {
            headers: {"X-Request-Access-Token": "link-a", "X-Request-Session-Token": "session-a"},
        });
    });

    it("recertifies with explicit assent, the response precondition, and an Idempotency-Key on both surfaces", async () =>
    {
        const body = {requirementId: "requirement-a", assented: true};
        const signedIn = await recertifyInformationRequestAcceptedFact("request-a", "fact-a", body, {
            expectedETag: "\"responses-4\"",
            idempotencyKey: "key-a",
        });
        expect(signedIn).toEqual({outcome: "SAVED", responseETag: "\"responses-5\"", data: {id: "recertification-a"}});
        expect(apiClient.post).toHaveBeenLastCalledWith(
            "/information-requests/request-a/accepted-fact-offers/fact-a/recertifications",
            body,
            {headers: {"If-Match": "\"responses-4\"", "Idempotency-Key": "key-a"}},
        );

        storeInformationRequestSessionToken("request-a", "session-a");
        await recertifyInformationRequestAcceptedFact("request-a", "fact-a", body, {
            expectedETag: "\"responses-4\"",
            idempotencyKey: "key-b",
            accessLinkToken: "link-a",
        });
        expect(apiClient.post).toHaveBeenLastCalledWith(
            "/no-auth/information-requests/request-a/accepted-fact-offers/fact-a/recertifications",
            body,
            {
                headers: {
                    "If-Match": "\"responses-4\"",
                    "Idempotency-Key": "key-b",
                    "X-Request-Access-Token": "link-a",
                    "X-Request-Session-Token": "session-a",
                },
            },
        );
    });

    it("reports a stale response precondition as STALE", async () =>
    {
        vi.mocked(apiClient.post).mockRejectedValue({
            isAxiosError: true,
            response: {status: 412, data: {reasonCode: "COMMAND_PRECONDITION_STALE"}},
        });
        expect(await recertifyInformationRequestAcceptedFact(
            "request-a",
            "fact-a",
            {requirementId: "requirement-a", assented: true},
            {expectedETag: "\"old\"", idempotencyKey: "key-c"},
        )).toEqual({outcome: "STALE"});
    });
});
