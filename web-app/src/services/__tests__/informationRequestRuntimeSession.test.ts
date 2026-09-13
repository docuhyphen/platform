// @vitest-environment jsdom
import {beforeEach, describe, expect, it, vi} from "vitest";
import apiClient from "../apiClient.ts";
import {
    addInformationRequestGroupOccurrence,
    getInformationRequestResponseWorkspace,
    getInformationRequestSessionToken,
    patchInformationRequestResponses,
    removeInformationRequestGroupOccurrence,
    reorderInformationRequestGroupOccurrences,
    storeInformationRequestAccessToken,
    storeInformationRequestSessionToken,
} from "../informationRequestRuntimeService.ts";

vi.mock("../apiClient.ts", () => ({default: {get: vi.fn(), patch: vi.fn(), post: vi.fn(), delete: vi.fn()}}));

describe("Information Request session transport", () =>
{
    beforeEach(() =>
    {
        vi.clearAllMocks();
        window.sessionStorage.clear();
        vi.mocked(apiClient.get).mockResolvedValue({data: {}, headers: {}});
        vi.mocked(apiClient.patch).mockResolvedValue({data: [], headers: {etag: "revision"}});
        vi.mocked(apiClient.post).mockResolvedValue({data: [], headers: {etag: "revision"}});
        vi.mocked(apiClient.delete).mockResolvedValue({data: [], headers: {etag: "revision"}});
    });

    it("sends the independently verified credential on workspace reads and response writes", async () =>
    {
        window.sessionStorage.setItem("information-request-session:request-a", "session-secret");
        await getInformationRequestResponseWorkspace("request-a", "bootstrap");
        expect(apiClient.get).toHaveBeenCalledWith("/no-auth/information-requests/request-a/response-workspace", {
            headers: {"X-Request-Access-Token": "bootstrap", "X-Request-Session-Token": "session-secret"},
        });
        await patchInformationRequestResponses("request-a", {patches: []}, {
            expectedETag: "revision", idempotencyKey: "command", accessLinkToken: "bootstrap",
        });
        expect(apiClient.patch).toHaveBeenCalledWith(expect.any(String), {patches: []}, {
            headers: expect.objectContaining({"X-Request-Session-Token": "session-secret"}),
        });
    });

    it("never attaches a no-auth session to authenticated requests or another request", async () =>
    {
        window.sessionStorage.setItem("information-request-session:request-a", "session-secret");
        await getInformationRequestResponseWorkspace("request-a");
        expect(apiClient.get).toHaveBeenLastCalledWith("/information-requests/request-a/response-workspace", undefined);
        await getInformationRequestResponseWorkspace("request-b", "other-bootstrap");
        expect(apiClient.get).toHaveBeenLastCalledWith(expect.any(String), {
            headers: expect.not.objectContaining({"X-Request-Session-Token": "session-secret"}),
        });
    });

    it("sends the same verified credential for add remove and reorder", async () =>
    {
        storeInformationRequestSessionToken("request-a", "secret");
        const options = {expectedETag: "revision", idempotencyKey: "command", accessLinkToken: "bootstrap"};
        await addInformationRequestGroupOccurrence("request-a", {groupKey: "items"}, options);
        await removeInformationRequestGroupOccurrence("request-a", "occurrence", options);
        await reorderInformationRequestGroupOccurrences("request-a", {groupKey: "items", occurrenceIds: []}, options);
        const config = {headers: expect.objectContaining({"X-Request-Session-Token": "secret"})};
        expect(apiClient.post).toHaveBeenCalledWith(expect.any(String), {groupKey: "items"}, config);
        expect(apiClient.delete).toHaveBeenCalledWith(expect.any(String), config);
        expect(apiClient.patch).toHaveBeenCalledWith(expect.any(String), {groupKey: "items", occurrenceIds: []}, config);
    });

    it("clears stale session credentials when a different link is opened", () =>
    {
        storeInformationRequestAccessToken("request-a", "first");
        storeInformationRequestSessionToken("request-a", "secret");
        storeInformationRequestAccessToken("request-a", "first");
        expect(getInformationRequestSessionToken("request-a")).toBe("secret");
        storeInformationRequestAccessToken("request-a", "replacement");
        expect(getInformationRequestSessionToken("request-a")).toBe("");
    });
});
