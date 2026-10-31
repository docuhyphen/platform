// @vitest-environment jsdom
import {act, renderHook, waitFor} from "@testing-library/react";
import {createElement, type ReactNode, StrictMode} from "react";
import {MemoryRouter, Route, Routes, useNavigate} from "react-router-dom";
import {beforeEach, describe, expect, it, vi} from "vitest";
import apiClient from "../../../services/apiClient.ts";
import {RECIPIENT_SIGN_IN_REQUIRED_GUIDANCE} from "./respondentAccessRefusal.ts";
import {useInformationRequestRespondentWorkspace} from "./useInformationRequestRespondentWorkspace.ts";

vi.mock("../../../services/apiClient.ts", () => ({default: {get: vi.fn(), post: vi.fn()}}));

const wrapper = ({children}: { children: ReactNode }) => createElement(MemoryRouter, {
    initialEntries: ["/respond?s=request-a&t=bootstrap"], children,
});

const signedInWrapper = ({children}: { children: ReactNode }) => createElement(MemoryRouter, {
    initialEntries: ["/information-requests/request-a/respond"],
    children: createElement(Routes, {
        children: createElement(Route, {path: "/information-requests/:requestId/respond", element: children}),
    }),
});

const linkWrapper = ({children}: { children: ReactNode }) => createElement(MemoryRouter, {
    initialEntries: ["/respond?s=request-a"], children,
});

const workspaceOf = (requestId: string) => ({request: {id: requestId}, templateVersion: {sections: []}});

const refusal = (reasonCode: string, errorMessage: string, status = 403) => ({
    isAxiosError: true, response: {status, data: {reasonCode, errorMessage}},
});

const storeVerifiedLink = (requestId: string) => {
    window.sessionStorage.setItem(`information-request-access:${requestId}`, "bootstrap-a");
    window.sessionStorage.setItem(`information-request-session:${requestId}`, "session-secret-a");
    window.sessionStorage.setItem(`information-request-session-id:${requestId}`, "session-a");
};

const deferred = <T, >() => {
    let resolve: (value: T) => void = () => undefined;
    let reject: (reason: unknown) => void = () => undefined;
    const promise = new Promise<T>((onResolve, onReject) => {
        resolve = onResolve;
        reject = onReject;
    });
    return {promise, resolve, reject};
};

const signedInRoute = () => renderHook(() => ({
    state: useInformationRequestRespondentWorkspace("authenticated"),
    navigate: useNavigate(),
}), {wrapper: signedInWrapper});

const pageAt = (entry: string, strict = false) => renderHook(() => useInformationRequestRespondentWorkspace("no-auth"), {
    wrapper: ({children}: { children: ReactNode }) => {
        const routed = createElement(MemoryRouter, {initialEntries: [entry], children});
        return strict ? createElement(StrictMode, null, routed) : routed;
    },
});

const verifiedAs = (name: string) => ({data: {sessionId: `session-${name}`, sessionToken: `session-secret-${name}`}});

const storedSession = (requestId: string) => ({
    accessLinkToken: window.sessionStorage.getItem(`information-request-access:${requestId}`),
    sessionToken: window.sessionStorage.getItem(`information-request-session:${requestId}`),
    sessionId: window.sessionStorage.getItem(`information-request-session-id:${requestId}`),
});

describe("respondent work that finishes after its page has closed", () => {
    const ended = refusal("INFORMATION_REQUEST_ACCESS_SESSION_EXPIRED", "Earlier session ended", 409);

    beforeEach(() => {
        vi.resetAllMocks();
        window.sessionStorage.clear();
        vi.mocked(apiClient.get).mockResolvedValue({data: workspaceOf("request-a")});
    });

    const startVerification = async (page: ReturnType<typeof pageAt>, link: string): Promise<{
        settled: Promise<void>
    }> => {
        await waitFor(() => expect(page.result.current.accessLinkToken).toBe(link));
        act(() => page.result.current.setCode("123456"));
        let settled: Promise<void> = Promise.resolve();
        act(() => {
            settled = page.result.current.verifyCode();
        });
        return {settled};
    };

    const verify = async (page: ReturnType<typeof pageAt>, link: string) => {
        await waitFor(() => expect(page.result.current.accessLinkToken).toBe(link));
        act(() => page.result.current.setCode("654321"));
        await act(async () => page.result.current.verifyCode());
        await waitFor(() => expect(page.result.current.workspace?.request.id).toBe("request-a"));
    };

    it.each([
        ["another access link", "bootstrap-b", false],
        ["the same access link", "bootstrap-a", false],
        ["another access link under StrictMode", "bootstrap-b", true],
    ])("keeps the next page's session for %s when the closed page's verification finishes", async (_case, nextLink, strict) => {
        const closed = deferred<ReturnType<typeof verifiedAs>>();
        vi.mocked(apiClient.post).mockReturnValueOnce(closed.promise).mockResolvedValueOnce(verifiedAs("b"));
        const first = pageAt("/respond?s=request-a&t=bootstrap-a", strict);
        const closedVerification = await startVerification(first, "bootstrap-a");
        await waitFor(() => expect(apiClient.post).toHaveBeenCalledTimes(1));
        first.unmount();
        const next = pageAt(`/respond?s=request-a&t=${nextLink}`, strict);
        await verify(next, nextLink);

        await act(async () => {
            closed.resolve(verifiedAs("a"));
            await closedVerification.settled;
        });

        expect(next.result.current.accessVerified).toBe(true);
        expect(next.result.current.sessionId).toBe("session-b");
        expect(storedSession("request-a")).toEqual({
            accessLinkToken: nextLink,
            sessionToken: "session-secret-b",
            sessionId: "session-b",
        });
    });

    it("keeps the next page's session when the closed page's background read reports its session ended", async () => {
        storeVerifiedLink("request-a");
        const closedRead = deferred<{ data: ReturnType<typeof workspaceOf> }>();
        vi.mocked(apiClient.get).mockReturnValueOnce(closedRead.promise);
        vi.mocked(apiClient.post).mockResolvedValueOnce(verifiedAs("b"));
        const first = pageAt("/respond?s=request-a");
        await waitFor(() => expect(apiClient.get).toHaveBeenCalledTimes(1));
        first.unmount();
        const next = pageAt("/respond?s=request-a&t=bootstrap-b");
        await verify(next, "bootstrap-b");

        await act(async () => closedRead.reject(ended));

        expect(next.result.current.accessVerified).toBe(true);
        expect(next.result.current.sessionId).toBe("session-b");
        expect(next.result.current.error).toBeNull();
        expect(storedSession("request-a")).toEqual({
            accessLinkToken: "bootstrap-b",
            sessionToken: "session-secret-b",
            sessionId: "session-b",
        });
    });

    it("leaves a stored session for the next page to check when the closed page's background read reports it ended", async () => {
        storeVerifiedLink("request-a");
        const closedRead = deferred<{ data: ReturnType<typeof workspaceOf> }>();
        vi.mocked(apiClient.get).mockReturnValueOnce(closedRead.promise).mockRejectedValueOnce(ended);
        const first = pageAt("/respond?s=request-a");
        await waitFor(() => expect(apiClient.get).toHaveBeenCalledTimes(1));
        first.unmount();

        await act(async () => closedRead.reject(ended));

        expect(storedSession("request-a")).toEqual({
            accessLinkToken: "bootstrap-a",
            sessionToken: "session-secret-a",
            sessionId: "session-a",
        });
        const next = pageAt("/respond?s=request-a");
        await waitFor(() => expect(next.result.current.error).toBe("Earlier session ended"));
        expect(next.result.current.accessVerified).toBe(false);
        expect(storedSession("request-a").sessionToken).toBe("");
    });

    it("verifies, loads, and returns to verification once its session ends under StrictMode", async () => {
        vi.mocked(apiClient.post).mockResolvedValueOnce(verifiedAs("a"));
        vi.mocked(apiClient.get).mockResolvedValueOnce({data: workspaceOf("request-a")}).mockRejectedValueOnce(ended);
        const page = pageAt("/respond?s=request-a&t=bootstrap-a", true);
        await waitFor(() => expect(page.result.current.accessLinkToken).toBe("bootstrap-a"));
        act(() => page.result.current.setCode("123456"));
        await act(async () => page.result.current.verifyCode());
        await waitFor(() => expect(page.result.current.workspace?.request.id).toBe("request-a"));
        expect(page.result.current.sessionId).toBe("session-a");
        expect(storedSession("request-a").sessionToken).toBe("session-secret-a");

        await act(async () => page.result.current.loadWorkspace());

        expect(page.result.current.accessVerified).toBe(false);
        expect(page.result.current.workspace).toBeNull();
        expect(page.result.current.error).toBe("Earlier session ended");
        expect(storedSession("request-a")).toEqual({accessLinkToken: "bootstrap-a", sessionToken: "", sessionId: null});
    });
});

describe("contact proof once a code was used", () => {
    beforeEach(() => {
        vi.resetAllMocks();
        window.sessionStorage.clear();
    });

    it("starts again from sending a new code once the session the code opened ends", async () => {
        vi.mocked(apiClient.post).mockResolvedValueOnce({data: {}}).mockResolvedValueOnce(verifiedAs("a"));
        vi.mocked(apiClient.get)
            .mockResolvedValueOnce({data: workspaceOf("request-a")})
            .mockRejectedValueOnce(refusal("INFORMATION_REQUEST_ACCESS_SESSION_EXPIRED", "Session expired", 409));
        const page = pageAt("/respond?s=request-a&t=bootstrap-a");
        await waitFor(() => expect(page.result.current.accessLinkToken).toBe("bootstrap-a"));
        await act(async () => page.result.current.issueChallenge());
        act(() => page.result.current.setCode("123456"));
        expect(page.result.current.challengeSent).toBe(true);

        await act(async () => page.result.current.verifyCode());
        await waitFor(() => expect(page.result.current.workspace?.request.id).toBe("request-a"));
        await act(async () => page.result.current.loadWorkspace());

        expect(page.result.current.accessVerified).toBe(false);
        expect(page.result.current.challengeSent).toBe(false);
        expect(page.result.current.code).toBe("");
    });

    it("sends nothing when asked to verify before a code is entered", async () => {
        const page = pageAt("/respond?s=request-a&t=bootstrap-a");
        await waitFor(() => expect(page.result.current.accessLinkToken).toBe("bootstrap-a"));

        await act(async () => page.result.current.verifyCode());

        expect(apiClient.post).not.toHaveBeenCalled();
    });
});

describe("late work from an earlier access link or session of the same request", () => {
    const ended = {reasonCode: "INFORMATION_REQUEST_ACCESS_SESSION_EXPIRED", errorMessage: "Earlier session ended"};

    beforeEach(() => {
        vi.resetAllMocks();
        window.sessionStorage.clear();
        vi.mocked(apiClient.get).mockResolvedValue({data: workspaceOf("request-a")});
    });

    it("keeps a renewed session when work sent through the session it replaced reports that session ended", async () => {
        storeVerifiedLink("request-a");
        vi.mocked(apiClient.post).mockResolvedValue({data: {sessionId: "session-b", sessionToken: "session-secret-b"}});
        const {result} = renderHook(() => useInformationRequestRespondentWorkspace("no-auth"), {wrapper: linkWrapper});
        await waitFor(() => expect(result.current.workspace?.request.id).toBe("request-a"));
        const reportedThroughEarlierSession = result.current.endAccessSession;
        act(() => reportedThroughEarlierSession(ended));
        expect(result.current.accessVerified).toBe(false);
        act(() => result.current.setCode("654321"));
        await act(async () => result.current.verifyCode());
        await waitFor(() => expect(result.current.workspace?.request.id).toBe("request-a"));

        act(() => reportedThroughEarlierSession(ended));

        expect(result.current.accessVerified).toBe(true);
        expect(result.current.sessionId).toBe("session-b");
        expect(result.current.workspace?.request.id).toBe("request-a");
        expect(result.current.error).toBeNull();
        expect(window.sessionStorage.getItem("information-request-session:request-a")).toBe("session-secret-b");
        expect(window.sessionStorage.getItem("information-request-session-id:request-a")).toBe("session-b");
    });

    it("keeps the session verified through a replacement link when the replaced link's verification finishes later", async () => {
        const earlier = deferred<{ data: { sessionId: string; sessionToken: string } }>();
        vi.mocked(apiClient.post)
            .mockReturnValueOnce(earlier.promise)
            .mockResolvedValueOnce({data: {sessionId: "session-b", sessionToken: "session-secret-b"}});
        const {result} = renderHook(() => ({
            state: useInformationRequestRespondentWorkspace("no-auth"),
            navigate: useNavigate(),
        }), {wrapper});
        await waitFor(() => expect(result.current.state.accessLinkToken).toBe("bootstrap"));
        act(() => result.current.state.setCode("123456"));
        let replacedVerification: Promise<void> = Promise.resolve();
        act(() => {
            replacedVerification = result.current.state.verifyCode();
        });
        await waitFor(() => expect(apiClient.post).toHaveBeenCalledTimes(1));
        act(() => result.current.navigate("/respond?s=request-a&t=bootstrap-b"));
        await waitFor(() => expect(result.current.state.accessLinkToken).toBe("bootstrap-b"));
        act(() => result.current.state.setCode("654321"));
        await act(async () => result.current.state.verifyCode());
        await waitFor(() => expect(result.current.state.workspace?.request.id).toBe("request-a"));

        await act(async () => {
            earlier.resolve({data: {sessionId: "session-a", sessionToken: "session-secret-a"}});
            await replacedVerification;
        });

        expect(result.current.state.accessVerified).toBe(true);
        expect(result.current.state.sessionId).toBe("session-b");
        expect(window.sessionStorage.getItem("information-request-access:request-a")).toBe("bootstrap-b");
        expect(window.sessionStorage.getItem("information-request-session:request-a")).toBe("session-secret-b");
        expect(window.sessionStorage.getItem("information-request-session-id:request-a")).toBe("session-b");
    });

    it("keeps the session verified through a replacement link when work sent through the replaced link reports its session ended", async () => {
        storeVerifiedLink("request-a");
        vi.mocked(apiClient.post).mockResolvedValue({data: {sessionId: "session-b", sessionToken: "session-secret-b"}});
        const {result} = renderHook(() => ({
            state: useInformationRequestRespondentWorkspace("no-auth"),
            navigate: useNavigate(),
        }), {wrapper: linkWrapper});
        await waitFor(() => expect(result.current.state.workspace?.request.id).toBe("request-a"));
        const reportedThroughReplacedLink = result.current.state.endAccessSession;
        act(() => result.current.navigate("/respond?s=request-a&t=bootstrap-b"));
        await waitFor(() => expect(result.current.state.accessLinkToken).toBe("bootstrap-b"));
        act(() => result.current.state.setCode("654321"));
        await act(async () => result.current.state.verifyCode());
        await waitFor(() => expect(result.current.state.workspace?.request.id).toBe("request-a"));

        act(() => reportedThroughReplacedLink(ended));

        expect(result.current.state.accessVerified).toBe(true);
        expect(result.current.state.sessionId).toBe("session-b");
        expect(result.current.state.workspace?.request.id).toBe("request-a");
        expect(result.current.state.error).toBeNull();
        expect(window.sessionStorage.getItem("information-request-session:request-a")).toBe("session-secret-b");
    });
});

describe("respondent session verification", () =>
{
    beforeEach(() =>
    {
        vi.resetAllMocks();
        window.sessionStorage.clear();
        vi.mocked(apiClient.get).mockResolvedValue({data: workspaceOf("request-a")});
        vi.mocked(apiClient.post).mockResolvedValue({data: {sessionToken: "independent-secret"}});
    });

    it("requires proof despite an old verified flag and stores the returned credential before loading", async () =>
    {
        window.sessionStorage.setItem("information-request-access-verified:request-a", "true");
        const {result} = renderHook(() => useInformationRequestRespondentWorkspace("no-auth"), {wrapper});
        await waitFor(() => expect(result.current.accessLinkToken).toBe("bootstrap"));
        expect(result.current.accessVerified).toBe(false);
        expect(apiClient.get).not.toHaveBeenCalled();
        act(() => result.current.setCode("123456"));
        await act(async () => result.current.verifyCode());
        await waitFor(() => expect(result.current.workspace).not.toBeNull());
        expect(window.sessionStorage.getItem("information-request-session:request-a")).toBe("independent-secret");
        expect(apiClient.get).toHaveBeenCalledWith(expect.any(String), {
            headers: {"X-Request-Access-Token": "bootstrap", "X-Request-Session-Token": "independent-secret"},
        });
    });

    it("keeps the verified session's id so the participant can later link an account", async () => {
        vi.mocked(apiClient.post).mockResolvedValue({
            data: {
                sessionId: "session-a",
                sessionToken: "independent-secret"
            }
        });
        const {result} = renderHook(() => useInformationRequestRespondentWorkspace("no-auth"), {wrapper});
        await waitFor(() => expect(result.current.accessLinkToken).toBe("bootstrap"));
        expect(result.current.sessionId).toBe("");

        act(() => result.current.setCode("123456"));
        await act(async () => result.current.verifyCode());

        expect(result.current.sessionId).toBe("session-a");
        expect(window.sessionStorage.getItem("information-request-session-id:request-a")).toBe("session-a");
    });

    it("returns to verification and clears content when the saved session has expired", async () =>
    {
        window.sessionStorage.setItem("information-request-access:request-a", "bootstrap");
        window.sessionStorage.setItem("information-request-session-id:request-a", "expired-session");
        window.sessionStorage.setItem("information-request-session:request-a", "expired-secret");
        vi.mocked(apiClient.get).mockRejectedValue({
            isAxiosError: true, response: {
                status: 409,
                data: {reasonCode: "INFORMATION_REQUEST_ACCESS_SESSION_EXPIRED", errorMessage: "Session expired"},
            }
        });
        const {result} = renderHook(() => useInformationRequestRespondentWorkspace("no-auth"), {wrapper});
        await waitFor(() => expect(result.current.error).toBe("Session expired"));
        expect(result.current.accessVerified).toBe(false);
        expect(result.current.workspace).toBeNull();
        expect(window.sessionStorage.getItem("information-request-session:request-a")).toBe("");
        expect(window.sessionStorage.getItem("information-request-session-id:request-a")).toBeNull();
        expect(result.current.sessionId).toBe("");
    });

    it("ends a session another command found expired and returns to verification without reloading", async () => {
        window.sessionStorage.setItem("information-request-access:request-a", "bootstrap");
        window.sessionStorage.setItem("information-request-session-id:request-a", "session-a");
        window.sessionStorage.setItem("information-request-session:request-a", "session-secret-a");
        const {result} = renderHook(() => useInformationRequestRespondentWorkspace("no-auth"), {wrapper});
        await waitFor(() => expect(result.current.workspace?.request.id).toBe("request-a"));

        act(() => result.current.endAccessSession(refusal("INFORMATION_REQUEST_ACCESS_SESSION_EXPIRED", "Session expired").response.data));

        expect(result.current.accessVerified).toBe(false);
        expect(result.current.workspace).toBeNull();
        expect(result.current.error).toBe("Session expired");
        expect(result.current.sessionId).toBe("");
        expect(window.sessionStorage.getItem("information-request-session:request-a")).toBe("");
        expect(apiClient.get).toHaveBeenCalledTimes(1);
    });
});

describe("respondent read refusals", () => {
    beforeEach(() => {
        vi.resetAllMocks();
        window.sessionStorage.clear();
    });

    it("withholds a signed-in workspace once a reread of the same request is refused and shows it again after a later read", async () => {
        vi.mocked(apiClient.get)
            .mockResolvedValueOnce({data: workspaceOf("request-a")})
            .mockRejectedValueOnce(refusal("INFORMATION_REQUEST_FORBIDDEN", "Request access was removed"))
            .mockResolvedValueOnce({data: workspaceOf("request-a")});
        const {result} = signedInRoute();
        await waitFor(() => expect(result.current.state.workspace?.request.id).toBe("request-a"));

        await act(async () => result.current.state.loadWorkspace());

        expect(result.current.state.workspace).toBeNull();
        expect(result.current.state.requirements).toEqual([]);
        expect(result.current.state.error).toBe("Request access was removed");
        expect(result.current.state.busy).toBe(false);

        await act(async () => result.current.state.loadWorkspace());

        expect(result.current.state.workspace?.request.id).toBe("request-a");
        expect(result.current.state.error).toBeNull();
    });

    it("withholds a verified access-link workspace refused for another reason and keeps its session for the next read", async () => {
        storeVerifiedLink("request-a");
        vi.mocked(apiClient.get)
            .mockResolvedValueOnce({data: workspaceOf("request-a")})
            .mockRejectedValueOnce(refusal("INFORMATION_REQUEST_RECIPIENT_SIGN_IN_REQUIRED", "Sign in to respond", 409));
        const {result} = renderHook(() => useInformationRequestRespondentWorkspace("no-auth"), {wrapper: linkWrapper});
        await waitFor(() => expect(result.current.workspace?.request.id).toBe("request-a"));

        await act(async () => result.current.loadWorkspace());

        expect(result.current.workspace).toBeNull();
        expect(result.current.error).toBe(RECIPIENT_SIGN_IN_REQUIRED_GUIDANCE);
        expect(result.current.accessVerified).toBe(true);
        expect(result.current.sessionId).toBe("session-a");
        expect(window.sessionStorage.getItem("information-request-session:request-a")).toBe("session-secret-a");
    });

    it("still returns an access link to verification when a reread finds its session ended", async () => {
        storeVerifiedLink("request-a");
        vi.mocked(apiClient.get)
            .mockResolvedValueOnce({data: workspaceOf("request-a")})
            .mockRejectedValueOnce(refusal("INFORMATION_REQUEST_ACCESS_SESSION_EXPIRED", "Session expired", 401));
        const {result} = renderHook(() => useInformationRequestRespondentWorkspace("no-auth"), {wrapper: linkWrapper});
        await waitFor(() => expect(result.current.workspace?.request.id).toBe("request-a"));

        await act(async () => result.current.loadWorkspace());

        expect(result.current.workspace).toBeNull();
        expect(result.current.accessVerified).toBe(false);
        expect(result.current.error).toBe("Session expired");
    });

    it("shows the latest workspace after a reread of the same request succeeds", async () => {
        vi.mocked(apiClient.get)
            .mockResolvedValueOnce({data: workspaceOf("request-a")})
            .mockResolvedValueOnce({data: {...workspaceOf("request-a"), title: "Latest response"}});
        const {result} = signedInRoute();
        await waitFor(() => expect(result.current.state.workspace?.request.id).toBe("request-a"));

        await act(async () => result.current.state.loadWorkspace());

        expect(result.current.state.workspace).toEqual({...workspaceOf("request-a"), title: "Latest response"});
        expect(result.current.state.error).toBeNull();
    });
});

describe("respondent route scope", () => {
    beforeEach(() => {
        vi.resetAllMocks();
        window.sessionStorage.clear();
    });

    it("hides the previous request as soon as the route names another and shows the next one's refusal alone", async () => {
        const next = deferred<{ data: ReturnType<typeof workspaceOf> }>();
        vi.mocked(apiClient.get).mockResolvedValueOnce({data: workspaceOf("request-a")}).mockReturnValueOnce(next.promise);
        const {result} = signedInRoute();
        await waitFor(() => expect(result.current.state.workspace?.request.id).toBe("request-a"));

        act(() => result.current.navigate("/information-requests/request-b/respond"));

        expect(result.current.state.requestId).toBe("request-b");
        expect(result.current.state.workspace).toBeNull();
        await act(async () => next.reject(refusal("AUTHORIZATION_DENIED", "Access denied")));
        expect(result.current.state.error).toBe("Access denied");
        expect(result.current.state.workspace).toBeNull();
        expect(result.current.state.busy).toBe(false);
    });

    it("keeps the current request when an earlier request's load finishes last", async () => {
        const earlier = deferred<{ data: ReturnType<typeof workspaceOf> }>();
        vi.mocked(apiClient.get).mockReturnValueOnce(earlier.promise).mockResolvedValueOnce({data: workspaceOf("request-b")});
        const {result} = signedInRoute();
        await waitFor(() => expect(apiClient.get).toHaveBeenCalledTimes(1));

        act(() => result.current.navigate("/information-requests/request-b/respond"));
        await waitFor(() => expect(result.current.state.workspace?.request.id).toBe("request-b"));
        await act(async () => earlier.resolve({data: workspaceOf("request-a")}));

        expect(result.current.state.requestId).toBe("request-b");
        expect(result.current.state.workspace?.request.id).toBe("request-b");
        expect(result.current.state.busy).toBe(false);
    });

    it("keeps the current request when an earlier request's refusal arrives last", async () => {
        const earlier = deferred<{ data: ReturnType<typeof workspaceOf> }>();
        vi.mocked(apiClient.get).mockReturnValueOnce(earlier.promise).mockResolvedValueOnce({data: workspaceOf("request-b")});
        const {result} = signedInRoute();
        await waitFor(() => expect(apiClient.get).toHaveBeenCalledTimes(1));

        act(() => result.current.navigate("/information-requests/request-b/respond"));
        await waitFor(() => expect(result.current.state.workspace?.request.id).toBe("request-b"));
        await act(async () => earlier.reject(refusal("AUTHORIZATION_DENIED", "Access denied")));

        expect(result.current.state.error).toBeNull();
        expect(result.current.state.workspace?.request.id).toBe("request-b");
    });

    it("keeps loading the current request when a refresh of the previous request starts late", async () => {
        const next = deferred<{ data: ReturnType<typeof workspaceOf> }>();
        vi.mocked(apiClient.get)
            .mockResolvedValueOnce({data: workspaceOf("request-a")})
            .mockReturnValueOnce(next.promise)
            .mockResolvedValue({data: workspaceOf("request-a")});
        const {result} = signedInRoute();
        await waitFor(() => expect(result.current.state.workspace?.request.id).toBe("request-a"));
        const refreshPrevious = result.current.state.loadWorkspace;

        act(() => result.current.navigate("/information-requests/request-b/respond"));
        await waitFor(() => expect(apiClient.get).toHaveBeenCalledTimes(2));
        await act(async () => refreshPrevious());
        await act(async () => next.resolve({data: workspaceOf("request-b")}));

        expect(result.current.state.workspace?.request.id).toBe("request-b");
        expect(result.current.state.busy).toBe(false);
        expect(apiClient.get).toHaveBeenCalledTimes(2);
    });

    it("never shows a workspace the server returned for another request than the route names", async () => {
        vi.mocked(apiClient.get).mockResolvedValue({data: workspaceOf("request-other")});
        const {result} = signedInRoute();

        await waitFor(() => expect(apiClient.get).toHaveBeenCalledTimes(1));
        await waitFor(() => expect(result.current.state.busy).toBe(false));

        expect(result.current.state.workspace).toBeNull();
    });

    it("keeps loading the current request when the previous request's session is reported ended late", async () => {
        ["a", "b"].forEach(name => {
            window.sessionStorage.setItem(`information-request-access:request-${name}`, `bootstrap-${name}`);
            window.sessionStorage.setItem(`information-request-session:request-${name}`, `session-secret-${name}`);
            window.sessionStorage.setItem(`information-request-session-id:request-${name}`, `session-${name}`);
        });
        const next = deferred<{ data: ReturnType<typeof workspaceOf> }>();
        vi.mocked(apiClient.get).mockResolvedValueOnce({data: workspaceOf("request-a")}).mockReturnValueOnce(next.promise);
        const {result} = renderHook(() => ({
            state: useInformationRequestRespondentWorkspace("no-auth"),
            navigate: useNavigate(),
        }), {wrapper: linkWrapper});
        await waitFor(() => expect(result.current.state.workspace?.request.id).toBe("request-a"));
        const endPrevious = result.current.state.endAccessSession;

        act(() => result.current.navigate("/respond?s=request-b"));
        await waitFor(() => expect(apiClient.get).toHaveBeenCalledTimes(2));
        act(() => endPrevious({
            reasonCode: "INFORMATION_REQUEST_ACCESS_SESSION_EXPIRED",
            errorMessage: "Session expired"
        }));
        await act(async () => next.resolve({data: workspaceOf("request-b")}));

        expect(result.current.state.workspace?.request.id).toBe("request-b");
        expect(result.current.state.accessVerified).toBe(true);
        expect(result.current.state.busy).toBe(false);
        expect(result.current.state.error).toBeNull();
        expect(window.sessionStorage.getItem("information-request-session:request-a")).toBe("");
        expect(window.sessionStorage.getItem("information-request-session:request-b")).toBe("session-secret-b");
    });

    it("switches to another request's access link without showing or loading under the previous link", async () => {
        window.sessionStorage.setItem("information-request-access:request-a", "bootstrap-a");
        window.sessionStorage.setItem("information-request-session:request-a", "session-secret-a");
        window.sessionStorage.setItem("information-request-session-id:request-a", "session-a");
        vi.mocked(apiClient.get).mockImplementation(async path =>
            ({data: workspaceOf(String(path).includes("request-b") ? "request-b" : "request-a")}));
        vi.mocked(apiClient.post).mockResolvedValue({data: {sessionId: "session-b", sessionToken: "session-secret-b"}});
        const {result} = renderHook(() => ({
            state: useInformationRequestRespondentWorkspace("no-auth"),
            navigate: useNavigate(),
        }), {wrapper: linkWrapper});
        await waitFor(() => expect(result.current.state.workspace?.request.id).toBe("request-a"));

        act(() => result.current.navigate("/respond?s=request-b&t=bootstrap-b"));

        expect(result.current.state.workspace).toBeNull();
        await waitFor(() => expect(result.current.state.accessLinkToken).toBe("bootstrap-b"));
        expect(result.current.state.accessVerified).toBe(false);
        expect(result.current.state.sessionId).toBe("");
        expect(result.current.state.workspace).toBeNull();
        expect(vi.mocked(apiClient.get).mock.calls.filter(([path]) => String(path).includes("request-b"))).toEqual([]);

        act(() => result.current.state.setCode("123456"));
        await act(async () => result.current.state.verifyCode());
        await waitFor(() => expect(result.current.state.workspace?.request.id).toBe("request-b"));
        expect(apiClient.post).toHaveBeenCalledWith(expect.any(String), {otp: "123456"}, {
            headers: {"X-Request-Access-Token": "bootstrap-b"},
        });
        expect(vi.mocked(apiClient.get).mock.calls.filter(([path]) => String(path).includes("request-b"))).toEqual([[
            "/no-auth/information-requests/request-b/response-workspace",
            {headers: {"X-Request-Access-Token": "bootstrap-b", "X-Request-Session-Token": "session-secret-b"}},
        ]]);
        expect(window.sessionStorage.getItem("information-request-session:request-a")).toBe("session-secret-a");
    });
});

describe("an access-link page opened without the token its link carried", () => {
    beforeEach(() => {
        vi.resetAllMocks();
        window.sessionStorage.clear();
        vi.mocked(apiClient.get).mockResolvedValue({data: workspaceOf("request-a")});
    });

    it("says the link is incomplete once no token is found for it in the tab or the address", async () => {
        const page = pageAt("/respond?s=request-a");

        await waitFor(() => expect(page.result.current.linkIncomplete).toBe(true));
        expect(apiClient.get).not.toHaveBeenCalled();
    });

    it("finds the link complete when the address or the tab holds its token", async () => {
        const fromAddress = pageAt("/respond?s=request-a&t=bootstrap-a");
        await waitFor(() => expect(fromAddress.result.current.accessLinkToken).toBe("bootstrap-a"));
        expect(fromAddress.result.current.linkIncomplete).toBe(false);
        fromAddress.unmount();

        const fromTab = pageAt("/respond?s=request-a");
        await waitFor(() => expect(fromTab.result.current.accessLinkToken).toBe("bootstrap-a"));
        expect(fromTab.result.current.linkIncomplete).toBe(false);
    });
});
