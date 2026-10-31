/** @vitest-environment jsdom */
import {act, cleanup, renderHook, waitFor} from "@testing-library/react";
import {afterEach, beforeEach, describe, expect, it, vi} from "vitest";
import * as authoring from "../../../../services/informationRequestAuthoringService.ts";
import * as runtime from "../../../../services/informationRequestRuntimeService.ts";
import {
    InformationRequestAccessLinkIssuedDto,
    InformationRequestAccessLinkStatus,
    InformationRequestDto,
    InformationRequestExecutionStandingKind,
    InformationRequestOwnerType,
    InformationRequestPartyDto,
    InformationRequestResponseWorkspaceDto,
    InformationRequestShareRoleKey,
    InformationRequestState,
    InformationRequestTemplateStatus,
} from "../../../models/models.tsx";
import {useAuthorWorkspace} from "./useAuthorWorkspace.ts";

vi.mock("../../../../services/informationRequestAuthoringService.ts", async () => ({
    ...(await vi.importActual<typeof authoring>("../../../../services/informationRequestAuthoringService.ts")),
    getInformationRequestParties: vi.fn(),
    getInformationRequestAccessLinks: vi.fn(),
    getInformationRequestDocumentPlaceholders: vi.fn(),
    cancelInformationRequest: vi.fn(),
    supersedeInformationRequest: vi.fn(),
    issueInformationRequestAccessLink: vi.fn(),
    rotateInformationRequestAccessLink: vi.fn(),
}));
vi.mock("../../../../services/informationRequestRuntimeService.ts", async () => ({
    ...(await vi.importActual<typeof runtime>("../../../../services/informationRequestRuntimeService.ts")),
    getInformationRequestResponseWorkspace: vi.fn(),
    getInformationRequestManagementWorkspace: vi.fn(),
}));
vi.mock("../../../../context/AuthContext.tsx", () => ({useAuth: () => ({appUser: {id: "user-a"}})}));

const workspaceFor = (requestId: string): InformationRequestResponseWorkspaceDto => ({
    request: {
        id: requestId,
        exchangeId: `exchange-of-${requestId}`,
        templateVersionId: "version-a",
        ownerType: InformationRequestOwnerType.ORGANIZATION,
        ownerOrganizationId: "org-a",
        state: InformationRequestState.ISSUED,
        gatesExchangeClosure: true,
        aggregateRevision: 3,
        createdAt: Date.parse("2026-09-27T08:00:00Z"),
        updatedAt: Date.parse("2026-09-27T08:00:00Z"),
        requestETag: `"${requestId}-3"`,
        executionStanding: {kind: InformationRequestExecutionStandingKind.ACTIVE},
        conditionEvaluations: [],
    },
    title: `Records for ${requestId}`,
    respondentKey: "respondent-a",
    templateVersion: {
        id: "version-a",
        templateDefinitionId: "definition-a",
        versionNumber: 1,
        status: InformationRequestTemplateStatus.PUBLISHED,
        sections: [],
        groups: [],
        conditionRules: [],
        requiredCapabilities: [],
        createdAt: Date.parse("2026-09-20T08:00:00Z"),
    },
    responseETag: `"${requestId}-responses"`,
    occurrences: [],
    responses: [],
    supportingEvidenceLinks: [],
    evidenceUploadAvailable: true,
    evidenceMalwareScanning: false,
    requirementsAwaitingStepUp: 0,
    executionStanding: {kind: InformationRequestExecutionStandingKind.ACTIVE},
});

const partyOf = (requestId: string): InformationRequestPartyDto => ({
    id: `party-of-${requestId}`,
    informationRequestId: requestId,
    roleKey: InformationRequestShareRoleKey.CONTRIBUTOR,
    active: true,
    principalId: "participant-a",
    principalKind: "PARTICIPANT",
    assignedAt: Date.parse("2026-09-27T08:00:00Z"),
    partyRevision: 1,
    partyETag: `"party-of-${requestId}-1"`,
    label: `member of ${requestId}`,
});

const issuedLink = (accessToken: string, rotationCount = 0): InformationRequestAccessLinkIssuedDto =>
    ({shareLinkId: "link-a", accessToken, status: InformationRequestAccessLinkStatus.ACTIVE, rotationCount});

const deferred = <T>() => {
    let resolve!: (value: T) => void;
    let reject!: (reason: unknown) => void;
    const promise = new Promise<T>((onResolve, onReject) => {
        resolve = onResolve;
        reject = onReject;
    });
    return {promise, resolve, reject};
};

const renderAuthor = (requestId: string) =>
    renderHook(({routed}) => useAuthorWorkspace(routed), {initialProps: {routed: requestId}});

describe("useAuthorWorkspace", () => {
    it("keeps a newly issued link and the current workspace when the post-command read fails transiently", async () => {
        vi.mocked(runtime.getInformationRequestManagementWorkspace)
            .mockResolvedValueOnce(workspaceFor("request-a"))
            .mockRejectedValueOnce("Bad gateway");
        vi.mocked(authoring.issueInformationRequestAccessLink).mockResolvedValueOnce(issuedLink("token-a"));
        const hook = renderAuthor("request-a");
        await waitFor(() => expect(hook.result.current.workspace?.request.id).toBe("request-a"));

        await act(async () => hook.result.current.issueLink(partyOf("request-a")));

        expect(hook.result.current.workspace?.request.id).toBe("request-a");
        expect(hook.result.current.shownLink?.url).toContain("t=token-a");
        expect(hook.result.current.loadError).toBe("Bad gateway");
    });

    beforeEach(() => {
        vi.resetAllMocks();
        vi.mocked(authoring.getInformationRequestParties).mockImplementation(async requestId =>
            ({parties: [partyOf(requestId)], partiesETag: `"${requestId}-parties"`}));
        vi.mocked(authoring.getInformationRequestAccessLinks).mockResolvedValue([]);
        vi.mocked(authoring.getInformationRequestDocumentPlaceholders).mockResolvedValue([]);
    });

    afterEach(cleanup);

    it("reads the workspace for management, so opening Manage records no respondent first view", async () => {
        vi.mocked(runtime.getInformationRequestResponseWorkspace).mockImplementation(async requestId => workspaceFor(requestId));
        vi.mocked(runtime.getInformationRequestManagementWorkspace).mockImplementation(async requestId => workspaceFor(requestId));
        const hook = renderAuthor("request-a");

        await waitFor(() => expect(hook.result.current.workspace?.request.id).toBe("request-a"));
        expect(runtime.getInformationRequestManagementWorkspace).toHaveBeenCalledWith("request-a");
        expect(runtime.getInformationRequestResponseWorkspace).not.toHaveBeenCalled();
    });

    it("loads each routed request in turn", async () => {
        vi.mocked(runtime.getInformationRequestManagementWorkspace).mockImplementation(async requestId => workspaceFor(requestId));
        const hook = renderAuthor("request-a");
        await waitFor(() => expect(hook.result.current.workspace?.request.id).toBe("request-a"));

        hook.rerender({routed: "request-b"});

        await waitFor(() => expect(hook.result.current.workspace?.request.id).toBe("request-b"));
        expect(hook.result.current.parties.map(party => party.informationRequestId)).toEqual(["request-b"]);
    });

    it("shows nothing of the previous request while the next one loads or after its read is refused", async () => {
        const next = deferred<InformationRequestResponseWorkspaceDto>();
        vi.mocked(runtime.getInformationRequestManagementWorkspace)
            .mockResolvedValueOnce(workspaceFor("request-a"))
            .mockReturnValueOnce(next.promise);
        const hook = renderAuthor("request-a");
        await waitFor(() => expect(hook.result.current.workspace?.request.id).toBe("request-a"));

        hook.rerender({routed: "request-b"});

        expect(hook.result.current.workspace).toBeNull();
        expect(hook.result.current.parties).toEqual([]);
        await act(async () => next.reject({errorMessage: "Request B access refused"}));
        expect(hook.result.current.loadError).toBe("Request B access refused");
        expect(hook.result.current.workspace).toBeNull();
        expect(hook.result.current.parties).toEqual([]);
    });

    it("ignores a slower read of an earlier route once the later route has loaded", async () => {
        const earlier = deferred<InformationRequestResponseWorkspaceDto>();
        vi.mocked(runtime.getInformationRequestManagementWorkspace)
            .mockReturnValueOnce(earlier.promise)
            .mockResolvedValueOnce(workspaceFor("request-b"));
        const hook = renderAuthor("request-a");

        hook.rerender({routed: "request-b"});
        await waitFor(() => expect(hook.result.current.workspace?.request.id).toBe("request-b"));
        await act(async () => earlier.resolve(workspaceFor("request-a")));

        expect(hook.result.current.workspace?.request.id).toBe("request-b");
        expect(hook.result.current.parties.map(party => party.informationRequestId)).toEqual(["request-b"]);
    });

    it("sends no command until the routed request has loaded, then targets it under its own ETag", async () => {
        const next = deferred<InformationRequestResponseWorkspaceDto>();
        vi.mocked(runtime.getInformationRequestManagementWorkspace)
            .mockResolvedValueOnce(workspaceFor("request-a"))
            .mockReturnValueOnce(next.promise)
            .mockResolvedValue(workspaceFor("request-b"));
        vi.mocked(authoring.cancelInformationRequest).mockResolvedValue(workspaceFor("request-b").request);
        const hook = renderAuthor("request-a");
        await waitFor(() => expect(hook.result.current.workspace?.request.id).toBe("request-a"));
        hook.rerender({routed: "request-b"});

        await act(async () => {
            await hook.result.current.cancel("author_cancelled");
        });
        expect(authoring.cancelInformationRequest).not.toHaveBeenCalled();

        await act(async () => next.resolve(workspaceFor("request-b")));
        await act(async () => {
            await hook.result.current.cancel("author_cancelled");
        });
        expect(authoring.cancelInformationRequest).toHaveBeenCalledTimes(1);
        expect(authoring.cancelInformationRequest).toHaveBeenCalledWith("request-b", "author_cancelled", "\"request-b-3\"", expect.any(String));
    });

    it("withholds the request when a later read of the same request is refused", async () => {
        vi.mocked(runtime.getInformationRequestManagementWorkspace)
            .mockResolvedValueOnce(workspaceFor("request-a"))
            .mockRejectedValueOnce({
                errorMessage: "Access to this request was removed",
                reasonCode: "INFORMATION_REQUEST_FORBIDDEN"
            });
        const hook = renderAuthor("request-a");
        await waitFor(() => expect(hook.result.current.workspace?.request.id).toBe("request-a"));

        await act(async () => hook.result.current.reload());

        expect(hook.result.current.workspace).toBeNull();
        expect(hook.result.current.loadError).toBe("Access to this request was removed");
    });

    it("keeps an earlier request's command result and reload away from a later route", async () => {
        const cancelled = deferred<InformationRequestDto>();
        vi.mocked(runtime.getInformationRequestManagementWorkspace).mockImplementation(async requestId => workspaceFor(requestId));
        vi.mocked(authoring.cancelInformationRequest).mockReturnValueOnce(cancelled.promise);
        const hook = renderAuthor("request-a");
        await waitFor(() => expect(hook.result.current.workspace?.request.id).toBe("request-a"));

        let pending: Promise<unknown> = Promise.resolve();
        act(() => {
            pending = hook.result.current.cancel("author_cancelled");
        });
        await waitFor(() => expect(hook.result.current.busy).toBe(true));
        hook.rerender({routed: "request-b"});
        await waitFor(() => expect(hook.result.current.workspace?.request.id).toBe("request-b"));
        const readsBefore = vi.mocked(runtime.getInformationRequestManagementWorkspace).mock.calls.length;
        await act(async () => {
            cancelled.resolve(workspaceFor("request-a").request);
            await pending;
        });

        expect(hook.result.current.workspace?.request.id).toBe("request-b");
        expect(hook.result.current.notice).toBeNull();
        expect(hook.result.current.busy).toBe(false);
        expect(vi.mocked(runtime.getInformationRequestManagementWorkspace).mock.calls.slice(readsBefore)
            .every(([requestId]) => requestId === "request-b")).toBe(true);
    });

    it("does not show an access link issued for an earlier request under a later route", async () => {
        const issued = deferred<InformationRequestAccessLinkIssuedDto>();
        vi.mocked(runtime.getInformationRequestManagementWorkspace).mockImplementation(async requestId => workspaceFor(requestId));
        vi.mocked(authoring.issueInformationRequestAccessLink).mockReturnValueOnce(issued.promise);
        const hook = renderAuthor("request-a");
        await waitFor(() => expect(hook.result.current.workspace?.request.id).toBe("request-a"));

        let pending: Promise<unknown> = Promise.resolve();
        act(() => {
            pending = hook.result.current.issueLink(partyOf("request-a"));
        });
        hook.rerender({routed: "request-b"});
        await waitFor(() => expect(hook.result.current.workspace?.request.id).toBe("request-b"));
        await act(async () => {
            issued.resolve({
                shareLinkId: "link-a",
                accessToken: "secret-a",
                status: InformationRequestAccessLinkStatus.ACTIVE,
                rotationCount: 0
            });
            await pending;
        });

        expect(hook.result.current.shownLink).toBeNull();
    });

    it("shows an access link it issued through later reads of the request until it is dismissed", async () => {
        vi.mocked(runtime.getInformationRequestManagementWorkspace).mockImplementation(async requestId => workspaceFor(requestId));
        vi.mocked(authoring.issueInformationRequestAccessLink).mockResolvedValueOnce(issuedLink("token-a"));
        const hook = renderAuthor("request-a");
        await waitFor(() => expect(hook.result.current.workspace?.request.id).toBe("request-a"));

        await act(async () => {
            await hook.result.current.issueLink(partyOf("request-a"));
        });
        expect(hook.result.current.shownLink).toEqual({
            url: expect.stringContaining("t=token-a"),
            partyLabel: "member of request-a"
        });
        await act(async () => hook.result.current.reload());
        expect(hook.result.current.shownLink?.url).toContain("t=token-a");

        act(() => hook.result.current.dismissShownLink());
        expect(hook.result.current.shownLink).toBeNull();
        expect(hook.result.current.workspace?.request.id).toBe("request-a");
    });

    it("withholds a shown access link once a later read of the same request is refused, and does not bring it back", async () => {
        vi.mocked(runtime.getInformationRequestManagementWorkspace)
            .mockResolvedValueOnce(workspaceFor("request-a"))
            .mockResolvedValueOnce(workspaceFor("request-a"))
            .mockRejectedValueOnce({
                errorMessage: "Access to this request was removed",
                reasonCode: "INFORMATION_REQUEST_FORBIDDEN"
            })
            .mockResolvedValue(workspaceFor("request-a"));
        vi.mocked(authoring.issueInformationRequestAccessLink).mockResolvedValueOnce(issuedLink("token-a"));
        const hook = renderAuthor("request-a");
        await waitFor(() => expect(hook.result.current.workspace?.request.id).toBe("request-a"));
        await act(async () => {
            await hook.result.current.issueLink(partyOf("request-a"));
        });
        expect(hook.result.current.shownLink?.url).toContain("t=token-a");

        await act(async () => hook.result.current.reload());

        expect(hook.result.current.workspace).toBeNull();
        expect(hook.result.current.loadError).toBe("Access to this request was removed");
        expect(hook.result.current.shownLink).toBeNull();
        await act(async () => hook.result.current.reload());
        expect(hook.result.current.workspace?.request.id).toBe("request-a");
        expect(hook.result.current.shownLink).toBeNull();
    });

    it("does not show an access link issued by a command whose refresh is refused", async () => {
        vi.mocked(runtime.getInformationRequestManagementWorkspace)
            .mockResolvedValueOnce(workspaceFor("request-a"))
            .mockRejectedValueOnce({
                errorMessage: "Access to this request was removed",
                reasonCode: "INFORMATION_REQUEST_FORBIDDEN"
            });
        vi.mocked(authoring.issueInformationRequestAccessLink).mockResolvedValueOnce(issuedLink("token-a"));
        const hook = renderAuthor("request-a");
        await waitFor(() => expect(hook.result.current.workspace?.request.id).toBe("request-a"));

        await act(async () => {
            await hook.result.current.issueLink(partyOf("request-a"));
        });

        expect(authoring.issueInformationRequestAccessLink).toHaveBeenCalledTimes(1);
        expect(hook.result.current.workspace).toBeNull();
        expect(hook.result.current.loadError).toBe("Access to this request was removed");
        expect(hook.result.current.shownLink).toBeNull();
    });

    it("does not show a replacement access link whose command refresh is refused", async () => {
        const link = {
            shareLinkId: "link-a",
            status: InformationRequestAccessLinkStatus.ACTIVE,
            rotationCount: 0,
            partyId: "party-of-request-a"
        };
        vi.mocked(authoring.getInformationRequestAccessLinks).mockResolvedValue([link]);
        vi.mocked(runtime.getInformationRequestManagementWorkspace)
            .mockResolvedValueOnce(workspaceFor("request-a"))
            .mockRejectedValueOnce({
                errorMessage: "Access to this request was removed",
                reasonCode: "INFORMATION_REQUEST_FORBIDDEN"
            });
        vi.mocked(authoring.rotateInformationRequestAccessLink).mockResolvedValueOnce(issuedLink("token-b", 1));
        const hook = renderAuthor("request-a");
        await waitFor(() => expect(hook.result.current.links).toEqual([link]));

        await act(async () => {
            await hook.result.current.resendLink(partyOf("request-a"), link);
        });

        expect(authoring.rotateInformationRequestAccessLink).toHaveBeenCalledTimes(1);
        expect(hook.result.current.workspace).toBeNull();
        expect(hook.result.current.shownLink).toBeNull();
    });

    it("retries an unanswered supersession under its key, and gives one with another reason a key of its own", async () => {
        vi.mocked(runtime.getInformationRequestManagementWorkspace).mockImplementation(async requestId => workspaceFor(requestId));
        vi.mocked(authoring.supersedeInformationRequest).mockRejectedValue("Network Error");
        const hook = renderAuthor("request-a");
        await waitFor(() => expect(hook.result.current.workspace?.request.id).toBe("request-a"));

        await act(async () => {
            await hook.result.current.supersede("replacement-a", "requirements-changed");
        });
        await act(async () => {
            await hook.result.current.supersede("replacement-a", "requirements-changed");
        });
        await act(async () => {
            await hook.result.current.supersede("replacement-a", "request-corrected");
        });

        const keys = vi.mocked(authoring.supersedeInformationRequest).mock.calls.map(call => call[4]);
        expect(keys).toHaveLength(3);
        expect(keys[1]).toBe(keys[0]);
        expect(keys[2]).not.toBe(keys[0]);
        expect(authoring.supersedeInformationRequest).toHaveBeenLastCalledWith(
            "request-a", "replacement-a", "request-corrected", "\"request-a-3\"", keys[2]);
    });

    it("rereads the links and drops the spent key once an unanswered link resend proves to have rotated the link", async () => {
        const link = {
            shareLinkId: "link-a",
            status: InformationRequestAccessLinkStatus.ACTIVE,
            rotationCount: 0,
            partyId: "party-of-request-a"
        };
        const rotated = {...link, rotationCount: 1};
        const explanation = "This access link was already rotated for this request; the new secret cannot be recovered";
        vi.mocked(runtime.getInformationRequestManagementWorkspace).mockImplementation(async requestId => workspaceFor(requestId));
        vi.mocked(authoring.getInformationRequestAccessLinks).mockResolvedValueOnce([link]).mockResolvedValue([rotated]);
        vi.mocked(authoring.rotateInformationRequestAccessLink)
            .mockRejectedValueOnce("Network Error")
            .mockRejectedValueOnce({
                reasonCode: "INFORMATION_REQUEST_ACCESS_LINK_ALREADY_ROTATED",
                errorMessage: explanation
            })
            .mockResolvedValueOnce(issuedLink("token-c", 2));
        const hook = renderAuthor("request-a");
        await waitFor(() => expect(hook.result.current.links).toEqual([link]));

        await act(async () => {
            await hook.result.current.resendLink(partyOf("request-a"), link);
        });
        await act(async () => {
            await hook.result.current.resendLink(partyOf("request-a"), link);
        });

        expect(authoring.getInformationRequestAccessLinks).toHaveBeenCalledTimes(2);
        expect(authoring.getInformationRequestParties).toHaveBeenCalledTimes(2);
        expect(hook.result.current.links).toEqual([rotated]);
        expect(hook.result.current.error).toBe(explanation);
        expect(hook.result.current.shownLink).toBeNull();

        await act(async () => {
            await hook.result.current.resendLink(partyOf("request-a"), link);
        });

        const keys = vi.mocked(authoring.rotateInformationRequestAccessLink).mock.calls.map(call => call[3]);
        expect(keys[1]).toBe(keys[0]);
        expect(keys[2]).not.toBe(keys[0]);
        expect(hook.result.current.shownLink?.url).toContain("t=token-c");
    });

    it("rereads the links and drops the spent key once an unanswered link creation proves to have issued the link", async () => {
        const issued = {
            shareLinkId: "link-a",
            status: InformationRequestAccessLinkStatus.ACTIVE,
            rotationCount: 0,
            partyId: "party-of-request-a"
        };
        const explanation = "This access link was already issued for this request; the original token cannot be recovered";
        vi.mocked(runtime.getInformationRequestManagementWorkspace).mockImplementation(async requestId => workspaceFor(requestId));
        vi.mocked(authoring.getInformationRequestAccessLinks).mockResolvedValueOnce([]).mockResolvedValue([issued]);
        vi.mocked(authoring.issueInformationRequestAccessLink)
            .mockRejectedValueOnce("Network Error")
            .mockRejectedValueOnce({
                reasonCode: "INFORMATION_REQUEST_ACCESS_LINK_ALREADY_ISSUED",
                errorMessage: explanation
            })
            .mockResolvedValueOnce(issuedLink("token-b"));
        const hook = renderAuthor("request-a");
        await waitFor(() => expect(hook.result.current.workspace?.request.id).toBe("request-a"));

        await act(async () => {
            await hook.result.current.issueLink(partyOf("request-a"));
        });
        await act(async () => {
            await hook.result.current.issueLink(partyOf("request-a"));
        });

        expect(authoring.getInformationRequestAccessLinks).toHaveBeenCalledTimes(2);
        expect(authoring.getInformationRequestParties).toHaveBeenCalledTimes(2);
        expect(hook.result.current.links).toEqual([issued]);
        expect(hook.result.current.error).toBe(explanation);
        expect(hook.result.current.shownLink).toBeNull();

        await act(async () => {
            await hook.result.current.issueLink(partyOf("request-a"));
        });

        const keys = vi.mocked(authoring.issueInformationRequestAccessLink).mock.calls.map(call => call[3]);
        expect(keys[1]).toBe(keys[0]);
        expect(keys[2]).not.toBe(keys[0]);
        expect(hook.result.current.shownLink?.url).toContain("t=token-b");
    });
});
