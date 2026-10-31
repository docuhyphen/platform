// @vitest-environment jsdom
import {beforeEach, describe, expect, it, vi} from "vitest";
import apiClient from "../apiClient.ts";
import {
    addInformationRequestGroupOccurrence,
    clearInformationRequestAccessCredentials,
    clearInformationRequestSession,
    getInformationRequestAccessToken,
    getInformationRequestManagementWorkspace,
    getInformationRequestNoAuthParties,
    getInformationRequestResponseWorkspace,
    getInformationRequestSessionId,
    getInformationRequestSessionToken,
    issueInformationRequestStepUpChallenge,
    linkInformationRequestParticipantAccount,
    patchInformationRequestResponses,
    removeInformationRequestGroupOccurrence,
    reorderInformationRequestGroupOccurrences,
    storeInformationRequestAccessToken,
    storeInformationRequestSession,
    storeInformationRequestSessionToken,
    verifyInformationRequestStepUp,
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

    it("reads a management workspace only on the signed-in surface, naming its purpose and no session", async () => {
        window.sessionStorage.setItem("information-request-session:request-a", "session-secret");
        await getInformationRequestManagementWorkspace("request-a");
        expect(apiClient.get).toHaveBeenLastCalledWith("/information-requests/request-a/response-workspace", {
            params: {purpose: "MANAGEMENT"},
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
        expect(apiClient.patch).toHaveBeenCalledWith(expect.any(String), {
            groupKey: "items",
            occurrenceIds: []
        }, config);
    });

    it("asks for and checks a step-up code with the access link and the verified session", async () => {
        storeInformationRequestSessionToken("request-a", "secret");
        vi.mocked(apiClient.post).mockResolvedValue({
            data: {
                sessionId: "session-a",
                verifiedUntil: Date.parse("2026-10-05T18:00:00Z")
            }, headers: {}
        });

        await issueInformationRequestStepUpChallenge("request-a", "bootstrap");
        const stepUp = await verifyInformationRequestStepUp("request-a", "bootstrap", "123456");

        const credentials = {headers: {"X-Request-Access-Token": "bootstrap", "X-Request-Session-Token": "secret"}};
        expect(apiClient.post).toHaveBeenNthCalledWith(
            1, "/no-auth/information-request-access-links/sessions/current/step-up-challenges", undefined, credentials,
        );
        expect(apiClient.post).toHaveBeenNthCalledWith(
            2, "/no-auth/information-request-access-links/sessions/current/step-ups", {otp: "123456"}, credentials,
        );
        expect(stepUp.verifiedUntil).toBe(Date.parse("2026-10-05T18:00:00Z"));
    });

    it("keeps the verified session's id with its credential and drops both together", () => {
        storeInformationRequestAccessToken("request-a", "first");
        storeInformationRequestSession("request-a", {sessionId: "session-a", sessionToken: "secret"});
        expect(getInformationRequestSessionId("request-a")).toBe("session-a");
        expect(getInformationRequestSessionToken("request-a")).toBe("secret");

        clearInformationRequestSession("request-a");
        expect(getInformationRequestSessionId("request-a")).toBe("");
        expect(getInformationRequestSessionToken("request-a")).toBe("");

        storeInformationRequestSession("request-a", {sessionId: "session-b", sessionToken: "secret-b"});
        storeInformationRequestAccessToken("request-a", "replacement");
        expect(getInformationRequestSessionId("request-a")).toBe("");

        storeInformationRequestSession("request-a", {sessionId: "session-c", sessionToken: "secret-c"});
        clearInformationRequestAccessCredentials("request-a");
        expect(getInformationRequestAccessToken("request-a")).toBe("");
        expect(getInformationRequestSessionToken("request-a")).toBe("");
        expect(getInformationRequestSessionId("request-a")).toBe("");
    });

    it("reads the respondent's parties and links the verified participant to the signed-in account", async () => {
        storeInformationRequestSession("request-a", {sessionId: "session-a", sessionToken: "secret"});
        vi.mocked(apiClient.get).mockResolvedValue({data: [{id: "party-a", partyETag: "\"party-a-2\""}], headers: {}});
        vi.mocked(apiClient.post).mockResolvedValue({
            data: {participantAccountLinkId: "link-a"},
            headers: {etag: "\"party-a-3\""}
        });

        const parties = await getInformationRequestNoAuthParties("request-a", "bootstrap");
        const linked = await linkInformationRequestParticipantAccount(
            "request-a", {sessionId: "session-a"}, "\"party-a-2\"", "link-key", "secret",
        );

        expect(parties).toEqual([{id: "party-a", partyETag: "\"party-a-2\""}]);
        expect(apiClient.get).toHaveBeenCalledWith("/no-auth/information-requests/request-a/parties", {
            headers: {"X-Request-Access-Token": "bootstrap", "X-Request-Session-Token": "secret"},
        });
        expect(linked).toEqual({participantAccountLinkId: "link-a"});
        expect(apiClient.post).toHaveBeenCalledWith(
            "/information-requests/request-a/participant-account-links",
            {sessionId: "session-a"},
            {
                headers: {
                    "If-Match": "\"party-a-2\"",
                    "Idempotency-Key": "link-key",
                    "X-Request-Session-Token": "secret"
                }
            },
        );
    });

    it("links with the credential the attempt was sent with, after the tab's session was cleared", async () => {
        storeInformationRequestSession("request-a", {sessionId: "session-a", sessionToken: "secret"});
        clearInformationRequestSession("request-a");

        await linkInformationRequestParticipantAccount("request-a", {sessionId: "session-a"}, "\"party-a-2\"", "link-key", "secret");

        expect(apiClient.post).toHaveBeenCalledWith(
            "/information-requests/request-a/participant-account-links",
            {sessionId: "session-a"},
            {
                headers: {
                    "If-Match": "\"party-a-2\"",
                    "Idempotency-Key": "link-key",
                    "X-Request-Session-Token": "secret"
                }
            },
        );
        expect(getInformationRequestSessionToken("request-a")).toBe("");
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
