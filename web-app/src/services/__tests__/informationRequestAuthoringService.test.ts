// @vitest-environment jsdom
import {AxiosError, AxiosHeaders} from "axios";
import {beforeEach, describe, expect, it, vi} from "vitest";
import apiClient from "../apiClient.ts";
import {
    assignInformationRequestParty,
    assignInformationRequestSubject,
    cancelInformationRequest,
    createInformationRequest,
    createNextInformationRequestOccurrence,
    defineInformationRequestRecurrence,
    getExchangeInformationRequests,
    getInformationRequestAccessLinks,
    getInformationRequestLineage,
    getInformationRequestParties,
    getInformationRequestSubjects,
    informationRequestAccessLinkUrl,
    issueInformationRequest,
    issueInformationRequestAccessLink,
    reassignInformationRequestParty,
    requestInformationRequestSupplement,
    revokeInformationRequestAccessLink,
    revokeInformationRequestParty,
    rotateInformationRequestAccessLink,
    supersedeInformationRequest,
} from "../informationRequestAuthoringService.ts";
import {
    InformationRequestLineageKind,
    InformationRequestRecurrenceUnit,
    InformationRequestShareRoleKey,
    InformationRequestSubjectKind,
} from "../../app/models/models.tsx";

vi.mock("../apiClient.ts", () => ({default: {get: vi.fn(), post: vi.fn()}}));

const refusal = (status: number, data: object) =>
{
    const error = new AxiosError("Refused", String(status));
    error.response = {status, data, statusText: "", headers: {}, config: {headers: new AxiosHeaders()}};
    return error;
};

describe("Information Request authoring transport", () =>
{
    beforeEach(() => vi.clearAllMocks());

    it("reads an Exchange's requests, a request's parties with their ETag, links, and the owner's subjects", async () =>
    {
        vi.mocked(apiClient.get)
            .mockResolvedValueOnce({data: {requests: [], canCreate: true}})
            .mockResolvedValueOnce({data: [{id: "party-a"}], headers: {etag: "\"parties-2\""}})
            .mockResolvedValueOnce({data: []})
            .mockResolvedValueOnce({data: []});

        expect(await getExchangeInformationRequests("exchange-a")).toEqual({requests: [], canCreate: true});
        expect(apiClient.get).toHaveBeenLastCalledWith("/exchanges/exchange-a/information-requests");
        expect(await getInformationRequestParties("request-a")).toEqual({parties: [{id: "party-a"}], partiesETag: "\"parties-2\""});
        expect(apiClient.get).toHaveBeenLastCalledWith("/information-requests/request-a/parties");
        await getInformationRequestAccessLinks("request-a");
        expect(apiClient.get).toHaveBeenLastCalledWith("/information-requests/request-a/access-links");
        await getInformationRequestSubjects();
        expect(apiClient.get).toHaveBeenLastCalledWith("/information-request-subjects");
    });

    it("creates a request under its Idempotency-Key and changes parties under the parties ETag", async () =>
    {
        vi.mocked(apiClient.post).mockResolvedValue({data: {id: "party-a"}, headers: {etag: "\"parties-3\""}});

        await createInformationRequest({exchangeId: "exchange-a", templateVersionId: "version-a"}, "create-a");
        expect(apiClient.post).toHaveBeenLastCalledWith(
            "/information-requests",
            {exchangeId: "exchange-a", templateVersionId: "version-a"},
            {headers: {"Idempotency-Key": "create-a"}},
        );
        const assigned = await assignInformationRequestParty(
            "request-a",
            {roleKey: InformationRequestShareRoleKey.CONTRIBUTOR, email: "member@process.test"},
            "\"parties-2\"",
            "assign-a",
        );
        expect(assigned).toEqual({party: {id: "party-a"}, partiesETag: "\"parties-3\""});
        expect(apiClient.post).toHaveBeenLastCalledWith(
            "/information-requests/request-a/parties",
            {roleKey: InformationRequestShareRoleKey.CONTRIBUTOR, email: "member@process.test"},
            {headers: {"Idempotency-Key": "assign-a", "If-Match": "\"parties-2\""}},
        );
        await reassignInformationRequestParty("request-a", "party-a", {userId: "user-b"}, "\"parties-3\"", "reassign-a");
        expect(apiClient.post).toHaveBeenLastCalledWith(
            "/information-requests/request-a/parties/party-a/reassignment",
            {userId: "user-b"},
            {headers: {"Idempotency-Key": "reassign-a", "If-Match": "\"parties-3\""}},
        );
        await revokeInformationRequestParty("request-a", "party-a", "\"parties-4\"", "revoke-a");
        expect(apiClient.post).toHaveBeenLastCalledWith(
            "/information-requests/request-a/parties/party-a/revocation",
            {},
            {headers: {"Idempotency-Key": "revoke-a", "If-Match": "\"parties-4\""}},
        );
        await assignInformationRequestSubject("request-a", {subjectKind: InformationRequestSubjectKind.RECORD}, "\"parties-5\"", "subject-a");
        expect(apiClient.post).toHaveBeenLastCalledWith(
            "/information-requests/request-a/subjects",
            {subjectKind: InformationRequestSubjectKind.RECORD},
            {headers: {"Idempotency-Key": "subject-a", "If-Match": "\"parties-5\""}},
        );
    });

    it("issues, rotates, and revokes a party's access link under that party's ETag and builds the respondent link", async () =>
    {
        vi.mocked(apiClient.post).mockResolvedValue({data: {shareLinkId: "link-a", accessToken: "secret"}, headers: {}});

        await issueInformationRequestAccessLink("request-a", {partyId: "party-a"}, "\"party-1\"", "issue-a");
        expect(apiClient.post).toHaveBeenLastCalledWith(
            "/information-requests/request-a/access-links",
            {partyId: "party-a"},
            {headers: {"Idempotency-Key": "issue-a", "If-Match": "\"party-1\""}},
        );
        await rotateInformationRequestAccessLink("request-a", "link-a", "\"party-1\"", "rotate-a");
        expect(apiClient.post).toHaveBeenLastCalledWith(
            "/information-requests/request-a/access-links/link-a/rotation",
            {},
            {headers: {"Idempotency-Key": "rotate-a", "If-Match": "\"party-1\""}},
        );
        await revokeInformationRequestAccessLink("request-a", "link-a", "\"party-1\"", "revoke-a");
        expect(apiClient.post).toHaveBeenLastCalledWith(
            "/information-requests/request-a/access-links/link-a/revocation",
            {},
            {headers: {"Idempotency-Key": "revoke-a", "If-Match": "\"party-1\""}},
        );
        expect(informationRequestAccessLinkUrl("request-a", "secret value", "https://app.process.test"))
            .toBe("https://app.process.test/nir?s=request-a&t=secret+value");
    });

    it("reads the lineage, schedules a recurrence under the request ETag, and creates follow-up requests", async () =>
    {
        vi.mocked(apiClient.get).mockResolvedValue({data: {informationRequestId: "request-a", successors: []}});
        vi.mocked(apiClient.post).mockResolvedValue({data: {}, headers: {}});
        const recurrence = {intervalUnit: InformationRequestRecurrenceUnit.MONTH, intervalCount: 1, firstDueAt: "2026-10-01T08:00:00.000Z"};

        await getInformationRequestLineage("request-a");
        expect(apiClient.get).toHaveBeenLastCalledWith("/information-requests/request-a/successors");
        await defineInformationRequestRecurrence("request-a", recurrence, "\"request-3\"", "recur-a");
        expect(apiClient.post).toHaveBeenLastCalledWith(
            "/information-requests/request-a/recurrences",
            recurrence,
            {headers: {"Idempotency-Key": "recur-a", "If-Match": "\"request-3\""}},
        );
        await createNextInformationRequestOccurrence("request-a", "recurrence-a", "next-a");
        expect(apiClient.post).toHaveBeenLastCalledWith(
            "/information-requests/request-a/recurrences/recurrence-a/occurrences",
            {},
            {headers: {"Idempotency-Key": "next-a"}},
        );
        await requestInformationRequestSupplement("request-a", "MISSING_ITEM", "\"request-4\"", "supplement-a");
        expect(apiClient.post).toHaveBeenLastCalledWith(
            "/information-requests/request-a/successors",
            {kind: InformationRequestLineageKind.SUPPLEMENT, reasonCode: "MISSING_ITEM"},
            {headers: {"Idempotency-Key": "supplement-a", "If-Match": "\"request-4\""}},
        );
    });

    it("issues, cancels, and supersedes under the request ETag and states a refusal", async () =>
    {
        vi.mocked(apiClient.post)
            .mockResolvedValueOnce({data: {id: "request-a"}, headers: {}})
            .mockResolvedValueOnce({data: {id: "request-a"}, headers: {}})
            .mockResolvedValueOnce({data: {id: "request-a"}, headers: {}})
            .mockRejectedValueOnce(refusal(409, {errorMessage: "This request needs a Decision Maker", reasonCode: "INFORMATION_REQUEST_STATE_INVALID"}));

        await issueInformationRequest("request-a", "\"request-1\"", "issue-a");
        expect(apiClient.post).toHaveBeenLastCalledWith(
            "/information-requests/request-a/issuance",
            {},
            {headers: {"Idempotency-Key": "issue-a", "If-Match": "\"request-1\""}},
        );
        await cancelInformationRequest("request-a", "NO_LONGER_NEEDED", "\"request-2\"", "cancel-a");
        expect(apiClient.post).toHaveBeenLastCalledWith(
            "/information-requests/request-a/cancellation",
            {reasonCode: "NO_LONGER_NEEDED"},
            {headers: {"Idempotency-Key": "cancel-a", "If-Match": "\"request-2\""}},
        );
        await supersedeInformationRequest("request-a", "request-b", "REPLACED", "\"request-3\"", "supersede-a");
        expect(apiClient.post).toHaveBeenLastCalledWith(
            "/information-requests/request-a/supersession",
            {supersededByRequestId: "request-b", reasonCode: "REPLACED"},
            {headers: {"Idempotency-Key": "supersede-a", "If-Match": "\"request-3\""}},
        );
        await expect(issueInformationRequest("request-a", "\"request-4\"", "issue-b"))
            .rejects.toEqual({errorMessage: "This request needs a Decision Maker", reasonCode: "INFORMATION_REQUEST_STATE_INVALID"});
    });
});
