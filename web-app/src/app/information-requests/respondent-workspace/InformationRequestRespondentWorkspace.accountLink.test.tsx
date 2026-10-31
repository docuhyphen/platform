/** @vitest-environment jsdom */
import {act, cleanup, fireEvent, render, screen, waitFor} from "@testing-library/react";
import {Link, MemoryRouter, Route, Routes} from "react-router-dom";
import {afterEach, beforeAll, beforeEach, describe, expect, it, vi} from "vitest";
import apiClient from "../../../services/apiClient.ts";
import {
    InformationRequestExecutionStandingKind,
    InformationRequestOwnerType,
    InformationRequestPartyDto,
    InformationRequestResponseWorkspaceDto,
    InformationRequestShareRoleKey,
    InformationRequestState,
    InformationRequestTemplateStatus,
} from "../../models/models.tsx";
import InformationRequestRespondentWorkspace from "./InformationRequestRespondentWorkspace.tsx";

const auth = vi.hoisted(() => ({email: "participant@process.test" as string | null}));

vi.mock("../../../services/apiClient.ts", () => ({default: {get: vi.fn(), post: vi.fn()}}));
vi.mock("../../../context/AuthContext.tsx", () => ({
    useAuth: () => ({
        appUser: auth.email ? {id: `account-${auth.email}`, email: auth.email} : null,
        refreshTokens: () => Promise.resolve(),
    }),
}));

const workspacePath = "/no-auth/information-requests/request-a/response-workspace";
const partiesPath = "/no-auth/information-requests/request-a/parties";
const previewPath = "/no-auth/information-requests/request-a/submission-preview";
const linkPath = "/information-requests/request-a/participant-account-links";
const challengePath = "/no-auth/information-request-access-links/challenges";
const sessionPath = "/no-auth/information-request-access-links/sessions";
const SESSION_HEADER = "X-Request-Session-Token";

const workspace: InformationRequestResponseWorkspaceDto = {
    request: {
        id: "request-a",
        exchangeId: "exchange-a",
        templateVersionId: "version-a",
        ownerType: InformationRequestOwnerType.ORGANIZATION,
        state: InformationRequestState.IN_PROGRESS,
        gatesExchangeClosure: true,
        aggregateRevision: 3,
        createdAt: Date.parse("2026-10-12T08:00:00Z"),
        updatedAt: Date.parse("2026-10-12T08:00:00Z"),
        requestETag: "\"request-3\"",
        executionStanding: {kind: InformationRequestExecutionStandingKind.ACTIVE},
        conditionEvaluations: [],
    },
    title: "Periodic records request",
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
        createdAt: Date.parse("2026-10-01T08:00:00Z"),
    },
    responseETag: "\"responses-1\"",
    occurrences: [],
    responses: [],
    supportingEvidenceLinks: [],
    evidenceUploadAvailable: true,
    evidenceMalwareScanning: false,
    requirementsAwaitingStepUp: 0,
    executionStanding: {kind: InformationRequestExecutionStandingKind.ACTIVE},
};

const party: InformationRequestPartyDto = {
    id: "party-a",
    informationRequestId: "request-a",
    roleKey: InformationRequestShareRoleKey.CONTRIBUTOR,
    active: true,
    principalKind: "PARTICIPANT",
    principalId: "participant-a",
    partyRevision: 2,
    partyETag: "\"party-a-2\"",
    assignedAt: Date.parse("2026-10-12T08:00:00Z"),
};

const upgrade = {
    participantAccountLinkId: "link-a",
    participantId: "participant-a",
    appUserId: "account-a",
    linkedAt: Date.parse("2026-10-12T09:00:00Z"),
    grantedShareId: "share-a",
    grantedRoleName: "CONTRIBUTOR",
    respondentKey: "account-respondent",
};

const participantDraftKey = "information-request-unsaved:request-a:respondent-a";
const accountDraftKey = "information-request-unsaved:request-a:account-respondent";
const unsentAnswers = JSON.stringify({
    edits: {"binding-a|root": {"contract-a": "Typed before linking"}},
    answers: {},
    bases: {"binding-a|root": 1},
});

const sessionEnded = (reasonCode: string, errorMessage: string) =>
    ({isAxiosError: true, response: {status: 409, data: {reasonCode, errorMessage}}});
const revoked = sessionEnded("INFORMATION_REQUEST_ACCESS_SESSION_REVOKED", "This request access session has been revoked");
const expired = sessionEnded("INFORMATION_REQUEST_ACCESS_SESSION_EXPIRED", "This request access session has expired");
const lostConnection = {isAxiosError: true, message: "Network Error"};

interface Deferred {
    promise: Promise<never>;
    reject: (reason: unknown) => void;
}

interface Hold {
    promise: Promise<void>;
    release: () => void;
}

interface CommandConfig {
    headers: Record<string, string>;
}

type Delivery = "deliver" | "lose-answer" | "drop-request";

const deferred = (): Deferred => {
    let reject: (reason: unknown) => void = () => undefined;
    const promise = new Promise<never>((_resolve, fail) => {
        reject = fail;
    });
    return {promise, reject};
};

const hold = (): Hold => {
    let release: () => void = () => undefined;
    const promise = new Promise<void>(resolve => {
        release = resolve;
    });
    return {promise, release};
};

const server = {liveCredential: "", endedReason: revoked as unknown, receipts: [] as string[]};
const transport = {deliveries: [] as Delivery[], hold: null as Hold | null, previews: [] as Deferred[]};

const receiveLink = (command: string, credential: string) => {
    if (server.receipts.includes(command)) return;
    if (!credential || credential !== server.liveCredential) throw server.endedReason;
    server.receipts.push(command);
    server.liveCredential = "";
    server.endedReason = revoked;
};

const linkCommands = () => vi.mocked(apiClient.post).mock.calls.filter(([path]) => path === linkPath);
const sentHeaders = (index: number): Record<string, string> => (linkCommands()[index][2] as CommandConfig).headers;
const partyReads = () => vi.mocked(apiClient.get).mock.calls.filter(([path]) => path === partiesPath);
const linkButton = () => screen.queryByRole("button", {name: "Link to my account"});

const element = (id: string): HTMLElement => {
    const found = document.getElementById(id);
    if (!found) throw new Error(`Missing element ${id}`);
    return found;
};

const respondentPage = () => (
    <MemoryRouter initialEntries={["/respond?s=request-a"]}>
        <Routes>
            <Route path={"/respond"}
                   element={<InformationRequestRespondentWorkspace accessMode={"no-auth"}/>}/>
            <Route path={"/information-requests/:requestId/respond"}
                   element={<p id={"signed-in-workspace-double"}>Signed-in workspace</p>}/>
        </Routes>
    </MemoryRouter>
);

const respondentPageWithExit = () => (
    <MemoryRouter initialEntries={["/respond?s=request-a"]}>
        <Link id={"leave-respondent-page"}
              to={"/elsewhere"}>
            Leave
        </Link>
        <Routes>
            <Route path={"/respond"}
                   element={<InformationRequestRespondentWorkspace accessMode={"no-auth"}/>}/>
            <Route path={"/elsewhere"}
                   element={<p id={"elsewhere-double"}>Elsewhere</p>}/>
            <Route path={"/information-requests/:requestId/respond"}
                   element={<p id={"signed-in-workspace-double"}>Signed-in workspace</p>}/>
        </Routes>
    </MemoryRouter>
);

const openVerifiedRequest = async () => {
    const view = render(respondentPage());
    await screen.findByRole("heading", {name: "Periodic records request"});
    await waitFor(() => expect(transport.previews).toHaveLength(1));
    return view;
};

const sendLinkAndLoseAnswer = async () => {
    fireEvent.click(screen.getByRole("button", {name: "Link to my account"}));
    expect(await screen.findByText("Network Error")).toBeTruthy();
};

const refuseSummaryRead = async (refusal: unknown) => {
    await act(async () => transport.previews[0].reject(refusal));
    await waitFor(() => expect(document.getElementById("information-request-contact-proof")).toBeTruthy());
};

describe("InformationRequestRespondentWorkspace account linking", () => {
    beforeAll(() => {
        vi.stubGlobal("ResizeObserver", class {
            observe() {
            }

            unobserve() {
            }

            disconnect() {
            }
        });
        Element.prototype.scrollIntoView = vi.fn();
    });

    beforeEach(() => {
        vi.resetAllMocks();
        window.sessionStorage.clear();
        window.sessionStorage.setItem("information-request-access:request-a", "bootstrap-a");
        window.sessionStorage.setItem("information-request-session:request-a", "session-secret-a");
        window.sessionStorage.setItem("information-request-session-id:request-a", "session-a");
        auth.email = "participant@process.test";
        Object.assign(server, {liveCredential: "session-secret-a", endedReason: revoked, receipts: []});
        Object.assign(transport, {deliveries: [], hold: null, previews: []});
        vi.mocked(apiClient.get).mockImplementation(async (path, config) => {
            if (path === previewPath) {
                const read = deferred();
                transport.previews.push(read);
                return read.promise;
            }
            if (path !== workspacePath && path !== partiesPath) return new Promise<never>(() => undefined);
            const credential = (config as CommandConfig).headers[SESSION_HEADER];
            if (!credential || credential !== server.liveCredential) throw server.endedReason;
            return {data: path === workspacePath ? workspace : [party]};
        });
        vi.mocked(apiClient.post).mockImplementation(async (path, body, config) => {
            if (path === challengePath) return {data: undefined};
            if (path === sessionPath) {
                server.liveCredential = "session-secret-b";
                return {data: {sessionId: "session-b", sessionToken: "session-secret-b"}};
            }
            if (path !== linkPath) throw new Error(`Unexpected command ${path}`);
            const delivery = transport.deliveries.shift() ?? "deliver";
            if (delivery === "drop-request") throw lostConnection;
            receiveLink(JSON.stringify([path, body, config]), (config as CommandConfig).headers[SESSION_HEADER]);
            if (transport.hold) await transport.hold.promise;
            if (delivery === "lose-answer") throw lostConnection;
            return {data: upgrade};
        });
    });

    afterEach(cleanup);

    it("keeps a lost account link recoverable after the summary read reports the session the link ended", async () => {
        transport.deliveries.push("lose-answer");
        await openVerifiedRequest();
        await sendLinkAndLoseAnswer();

        await refuseSummaryRead(revoked);

        expect(screen.queryByRole("heading", {name: "Periodic records request"})).toBeNull();
        expect(document.getElementById("information-request-respondent-workspace-shell")).toBeNull();
        expect(window.sessionStorage.getItem("information-request-session:request-a")).toBe("");
        expect(element("information-request-respondent-error-body").textContent)
            .toBe("This request access session has been revoked");
        const finish = screen.getByRole("button", {name: "Link to my account"});
        expect(screen.getByText(/Your verified session ended while this request was being linked to participant@process\.test\./))
            .toBeTruthy();
        fireEvent.click(finish);

        expect(await screen.findByText("Signed-in workspace")).toBeTruthy();
        const [sent, resent] = linkCommands();
        expect(linkCommands()).toHaveLength(2);
        expect(resent).toEqual(sent);
        expect(sentHeaders(0)[SESSION_HEADER]).toBe("session-secret-a");
        expect(partyReads()).toHaveLength(1);
        expect(window.sessionStorage.getItem("information-request-access:request-a")).toBeNull();
    });

    it("moves the participant's unsent answers to the linked account so the signed-in workspace shows them", async () => {
        window.sessionStorage.setItem(participantDraftKey, unsentAnswers);
        await openVerifiedRequest();

        fireEvent.click(screen.getByRole("button", {name: "Link to my account"}));

        expect(await screen.findByText("Signed-in workspace")).toBeTruthy();
        expect(JSON.parse(window.sessionStorage.getItem(accountDraftKey) ?? "null")).toEqual(JSON.parse(unsentAnswers));
        expect(window.sessionStorage.getItem(participantDraftKey)).toBeNull();
    });

    it("moves the unsent answers when a link interrupted by its session's end is finished", async () => {
        window.sessionStorage.setItem(participantDraftKey, unsentAnswers);
        transport.deliveries.push("lose-answer");
        await openVerifiedRequest();
        await sendLinkAndLoseAnswer();
        await refuseSummaryRead(revoked);

        fireEvent.click(screen.getByRole("button", {name: "Link to my account"}));

        expect(await screen.findByText("Signed-in workspace")).toBeTruthy();
        expect(JSON.parse(window.sessionStorage.getItem(accountDraftKey) ?? "null")).toEqual(JSON.parse(unsentAnswers));
        expect(window.sessionStorage.getItem(participantDraftKey)).toBeNull();
    });

    it("keeps the unsent answers with the participant when the link is not made", async () => {
        window.sessionStorage.setItem(participantDraftKey, unsentAnswers);
        transport.deliveries.push("drop-request");
        await openVerifiedRequest();

        await sendLinkAndLoseAnswer();

        expect(JSON.parse(window.sessionStorage.getItem(participantDraftKey) ?? "null")).toEqual(JSON.parse(unsentAnswers));
        expect(window.sessionStorage.getItem(accountDraftKey)).toBeNull();
    });

    it("keeps the attempt when the summary read ends the session while the link is still being sent", async () => {
        transport.deliveries.push("lose-answer");
        const answer = hold();
        transport.hold = answer;
        await openVerifiedRequest();
        fireEvent.click(screen.getByRole("button", {name: "Link to my account"}));
        await waitFor(() => expect(server.receipts).toHaveLength(1));

        await refuseSummaryRead(revoked);

        expect((screen.getByRole("button", {name: "Link to my account"}) as HTMLButtonElement).disabled).toBe(true);
        await act(async () => answer.release());
        expect(await screen.findByText("Network Error")).toBeTruthy();
        fireEvent.click(screen.getByRole("button", {name: "Link to my account"}));

        expect(await screen.findByText("Signed-in workspace")).toBeTruthy();
        const [sent, resent] = linkCommands();
        expect(linkCommands()).toHaveLength(2);
        expect(resent).toEqual(sent);
    });

    it("opens the signed-in workspace when the link's answer arrives", async () => {
        await openVerifiedRequest();

        fireEvent.click(screen.getByRole("button", {name: "Link to my account"}));

        expect(await screen.findByText("Signed-in workspace")).toBeTruthy();
        expect(linkCommands()).toEqual([[
            linkPath,
            {sessionId: "session-a"},
            {
                headers: {
                    "If-Match": "\"party-a-2\"",
                    "Idempotency-Key": expect.any(String),
                    [SESSION_HEADER]: "session-secret-a",
                },
            },
        ]]);
        expect(window.sessionStorage.getItem("information-request-access:request-a")).toBeNull();
        expect(window.sessionStorage.getItem("information-request-session:request-a")).toBeNull();
    });

    it("resends the same attempt after a lost answer when no other read reports the ended session", async () => {
        transport.deliveries.push("lose-answer");
        await openVerifiedRequest();
        await sendLinkAndLoseAnswer();

        expect(screen.getByRole("heading", {name: "Periodic records request"})).toBeTruthy();
        fireEvent.click(screen.getByRole("button", {name: "Link to my account"}));

        expect(await screen.findByText("Signed-in workspace")).toBeTruthy();
        const [sent, resent] = linkCommands();
        expect(linkCommands()).toHaveLength(2);
        expect(resent).toEqual(sent);
        expect(partyReads()).toHaveLength(1);
    });

    it("returns to verification without the attempt when resending shows the link was never made", async () => {
        transport.deliveries.push("drop-request");
        await openVerifiedRequest();
        await sendLinkAndLoseAnswer();
        Object.assign(server, {liveCredential: "", endedReason: expired});
        await refuseSummaryRead(expired);

        fireEvent.click(screen.getByRole("button", {name: "Link to my account"}));

        await waitFor(() => expect(linkButton()).toBeNull());
        expect(document.getElementById("information-request-contact-proof")).toBeTruthy();
        expect(element("information-request-respondent-error-body").textContent).toBe("This request access session has expired");
        expect(linkCommands()).toHaveLength(2);
        expect(sentHeaders(1)).toEqual(sentHeaders(0));
        expect(window.sessionStorage.getItem("information-request-session:request-a")).toBe("");
        expect(screen.queryByText("Signed-in workspace")).toBeNull();
    });

    it("starts a new attempt under a newly verified session instead of resending the ended one", async () => {
        transport.deliveries.push("drop-request");
        await openVerifiedRequest();
        await sendLinkAndLoseAnswer();
        Object.assign(server, {liveCredential: "", endedReason: expired});
        await refuseSummaryRead(expired);

        fireEvent.click(element("information-request-send-code-btn"));
        expect(await screen.findByText("A verification code has been sent.")).toBeTruthy();
        fireEvent.change(element("information-request-access-code-input"), {target: {value: "123456"}});
        fireEvent.click(element("information-request-verify-code-btn"));
        await screen.findByRole("heading", {name: "Periodic records request"});
        fireEvent.click(screen.getByRole("button", {name: "Link to my account"}));

        expect(await screen.findByText("Signed-in workspace")).toBeTruthy();
        expect(linkCommands()).toHaveLength(2);
        expect(linkCommands()[1][1]).toEqual({sessionId: "session-b"});
        expect(sentHeaders(1)[SESSION_HEADER]).toBe("session-secret-b");
        expect(sentHeaders(1)["Idempotency-Key"]).not.toBe(sentHeaders(0)["Idempotency-Key"]);
        expect(partyReads()).toHaveLength(2);
    });

    it("leaves the route and the stored session alone when the link's answer arrives after the page has closed", async () => {
        const answer = hold();
        transport.hold = answer;
        render(respondentPageWithExit());
        await screen.findByRole("heading", {name: "Periodic records request"});
        fireEvent.click(screen.getByRole("button", {name: "Link to my account"}));
        await waitFor(() => expect(server.receipts).toHaveLength(1));
        fireEvent.click(screen.getByRole("link", {name: "Leave"}));
        expect(await screen.findByText("Elsewhere")).toBeTruthy();

        await act(async () => {
            answer.release();
            await new Promise(resolve => setTimeout(resolve, 0));
        });

        expect(screen.getByText("Elsewhere")).toBeTruthy();
        expect(screen.queryByText("Signed-in workspace")).toBeNull();
        expect(window.sessionStorage.getItem("information-request-access:request-a")).toBe("bootstrap-a");
        expect(window.sessionStorage.getItem("information-request-session:request-a")).toBe("session-secret-a");
        expect(window.sessionStorage.getItem("information-request-session-id:request-a")).toBe("session-a");
    });

    it("offers the kept attempt only to the account that sent it", async () => {
        transport.deliveries.push("lose-answer");
        const view = await openVerifiedRequest();
        await sendLinkAndLoseAnswer();
        await refuseSummaryRead(revoked);

        auth.email = "another@process.test";
        view.rerender(respondentPage());
        expect(linkButton()).toBeNull();
        expect(screen.queryByText(/another@process\.test/)).toBeNull();

        auth.email = null;
        view.rerender(respondentPage());
        expect(screen.getByRole("button", {name: "I have signed in"})).toBeTruthy();
        expect(linkButton()).toBeNull();

        auth.email = "participant@process.test";
        view.rerender(respondentPage());
        fireEvent.click(screen.getByRole("button", {name: "Link to my account"}));

        expect(await screen.findByText("Signed-in workspace")).toBeTruthy();
        const [sent, resent] = linkCommands();
        expect(linkCommands()).toHaveLength(2);
        expect(resent).toEqual(sent);
    });
});
