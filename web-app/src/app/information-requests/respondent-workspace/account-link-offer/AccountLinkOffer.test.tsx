/** @vitest-environment jsdom */
import {cleanup, fireEvent, render, screen, waitFor} from "@testing-library/react";
import {MemoryRouter, Route, Routes} from "react-router-dom";
import {afterEach, beforeAll, beforeEach, describe, expect, it, vi} from "vitest";
import * as runtime from "../../../../services/informationRequestRuntimeService.ts";
import {InformationRequestPartyDto, InformationRequestShareRoleKey} from "../../../models/models.tsx";
import {duplicateIds} from "../../shared/testing/duplicateIds.ts";
import {unnamedControls} from "../../shared/testing/unnamedControls.ts";
import AccountLinkOffer from "./AccountLinkOffer.tsx";
import {usePendingAccountLink} from "./usePendingAccountLink.ts";

const auth = vi.hoisted(() => ({
    appUser: null as { id: string; email: string } | null,
    refreshTokens: (() => Promise.resolve()) as () => Promise<void>,
}));

vi.mock("../../../../context/AuthContext.tsx", () => ({
    useAuth: () => ({appUser: auth.appUser, refreshTokens: auth.refreshTokens}),
}));
vi.mock("../../../../services/informationRequestRuntimeService.ts", () => ({
    getInformationRequestNoAuthParties: vi.fn(),
    getInformationRequestSessionToken: vi.fn(),
    linkInformationRequestParticipantAccount: vi.fn(),
    clearInformationRequestAccessCredentials: vi.fn(),
}));

const ownParty = (id: string, partyETag: string): InformationRequestPartyDto => ({
    id,
    informationRequestId: "request-a",
    roleKey: InformationRequestShareRoleKey.CONTRIBUTOR,
    active: true,
    principalId: "participant-a",
    principalKind: "PARTICIPANT",
    assignedAt: Date.parse("2026-09-20T08:00:00Z"),
    partyRevision: 2,
    partyETag,
});

const otherParty: InformationRequestPartyDto = {
    id: "party-x",
    informationRequestId: "request-a",
    roleKey: InformationRequestShareRoleKey.DECISION_MAKER,
    active: true,
    assignedAt: Date.parse("2026-09-20T08:00:00Z"),
    partyRevision: 1,
    partyETag: "\"party-x-1\"",
};

const LinkingOffer = ({onAccessSessionEnded}: { onAccessSessionEnded?: (refusal: unknown) => void }) => {
    const pending = usePendingAccountLink("request-a", "session-a");
    return (
        <AccountLinkOffer requestId={"request-a"}
                          accessLinkToken={"bootstrap"}
                          sessionId={"session-a"}
                          respondentKey={"respondent-a"}
                          pending={pending}
                          onAccessSessionEnded={onAccessSessionEnded}/>
    );
};

const renderOffer = () => render(
    <MemoryRouter initialEntries={["/nir?s=request-a"]}>
        <Routes>
            <Route path={"/nir"}
                   element={<LinkingOffer/>}/>
            <Route path={"/information-requests/:requestId/respond"}
                   element={<p>Signed-in workspace</p>}/>
        </Routes>
    </MemoryRouter>,
);

describe("AccountLinkOffer", () => {
    beforeAll(() => {
        vi.stubGlobal("ResizeObserver", class {
            observe() {
            }

            unobserve() {
            }

            disconnect() {
            }
        });
    });

    beforeEach(() => {
        vi.clearAllMocks();
        auth.appUser = {id: "user-a", email: "member@process.test"};
        auth.refreshTokens = vi.fn().mockResolvedValue(undefined);
        vi.mocked(runtime.getInformationRequestNoAuthParties).mockResolvedValue([otherParty, ownParty("party-a", "\"party-a-2\"")]);
        vi.mocked(runtime.getInformationRequestSessionToken).mockReturnValue("session-secret");
        vi.mocked(runtime.linkInformationRequestParticipantAccount).mockReset().mockResolvedValue({
            participantAccountLinkId: "link-a",
            participantId: "participant-a",
            appUserId: "user-a",
            linkedAt: Date.parse("2026-10-05T08:00:00Z"),
            grantedShareId: "share-a",
            grantedRoleName: "CONTRIBUTOR",
            respondentKey: "account-respondent",
        });
    });

    afterEach(cleanup);

    it("links a signed-in viewer's account under their own party's ETag and opens the signed-in workspace", async () => {
        renderOffer();

        expect(screen.getByRole("region", {name: "Use your account for this request"})).toBeTruthy();
        expect(screen.getByText(/member@process\.test/)).toBeTruthy();
        fireEvent.click(screen.getByRole("button", {name: "Link to my account"}));

        expect(await screen.findByText("Signed-in workspace")).toBeTruthy();
        expect(runtime.getInformationRequestNoAuthParties).toHaveBeenCalledWith("request-a", "bootstrap");
        expect(runtime.linkInformationRequestParticipantAccount).toHaveBeenCalledWith(
            "request-a",
            {sessionId: "session-a"},
            "\"party-a-2\"",
            expect.any(String),
            "session-secret",
        );
        expect(runtime.clearInformationRequestAccessCredentials).toHaveBeenCalledWith("request-a");
    });

    it("tries the participant's other party when the first is not the one the session belongs to", async () => {
        vi.mocked(runtime.getInformationRequestNoAuthParties).mockResolvedValue([
            ownParty("party-a", "\"party-a-2\""),
            ownParty("party-b", "\"party-b-4\""),
        ]);
        vi.mocked(runtime.linkInformationRequestParticipantAccount)
            .mockRejectedValueOnce({errorMessage: "Stale", reasonCode: "COMMAND_PRECONDITION_STALE"});
        renderOffer();

        fireEvent.click(screen.getByRole("button", {name: "Link to my account"}));

        expect(await screen.findByText("Signed-in workspace")).toBeTruthy();
        const calls = vi.mocked(runtime.linkInformationRequestParticipantAccount).mock.calls;
        expect(calls.map(call => call[2])).toEqual(["\"party-a-2\"", "\"party-b-4\""]);
        expect(calls[0][3]).toBe(calls[1][3]);
    });

    it("recovers a link the server recorded when its answer was lost, without reading the parties again", async () => {
        vi.mocked(runtime.getInformationRequestNoAuthParties)
            .mockResolvedValueOnce([otherParty, ownParty("party-a", "\"party-a-2\"")])
            .mockRejectedValue({
                errorMessage: "This request access session has been revoked",
                reasonCode: "INFORMATION_REQUEST_ACCESS_SESSION_REVOKED",
            });
        vi.mocked(runtime.linkInformationRequestParticipantAccount).mockRejectedValueOnce("Network Error");
        renderOffer();

        fireEvent.click(screen.getByRole("button", {name: "Link to my account"}));
        expect(await screen.findByText("Network Error")).toBeTruthy();
        fireEvent.click(screen.getByRole("button", {name: "Link to my account"}));

        expect(await screen.findByText("Signed-in workspace")).toBeTruthy();
        expect(runtime.getInformationRequestNoAuthParties).toHaveBeenCalledTimes(1);
        const calls = vi.mocked(runtime.linkInformationRequestParticipantAccount).mock.calls;
        expect(calls.map(call => call[2])).toEqual(["\"party-a-2\"", "\"party-a-2\""]);
        expect(calls[0][3]).toBe(calls[1][3]);
        expect(runtime.clearInformationRequestAccessCredentials).toHaveBeenCalledWith("request-a");
    });

    it("reads the parties again when the party it last tried has changed since", async () => {
        vi.mocked(runtime.getInformationRequestNoAuthParties)
            .mockResolvedValueOnce([ownParty("party-a", "\"party-a-2\"")])
            .mockResolvedValueOnce([ownParty("party-a", "\"party-a-3\"")]);
        vi.mocked(runtime.linkInformationRequestParticipantAccount)
            .mockRejectedValueOnce("Network Error")
            .mockRejectedValueOnce({errorMessage: "Stale", reasonCode: "COMMAND_PRECONDITION_STALE"});
        renderOffer();

        fireEvent.click(screen.getByRole("button", {name: "Link to my account"}));
        expect(await screen.findByText("Network Error")).toBeTruthy();
        fireEvent.click(screen.getByRole("button", {name: "Link to my account"}));

        expect(await screen.findByText("Signed-in workspace")).toBeTruthy();
        expect(runtime.getInformationRequestNoAuthParties).toHaveBeenCalledTimes(2);
        const calls = vi.mocked(runtime.linkInformationRequestParticipantAccount).mock.calls;
        expect(calls.map(call => call[2])).toEqual(["\"party-a-2\"", "\"party-a-2\"", "\"party-a-3\""]);
        expect(new Set(calls.map(call => call[3])).size).toBe(1);
    });

    it.each([
        ["INFORMATION_REQUEST_PARTICIPANT_ACCOUNT_EMAIL_MISMATCH", /email address is not the one this request was sent to/],
        ["INFORMATION_REQUEST_PARTICIPANT_ACCOUNT_ALREADY_LINKED", /already linked to a different account/],
        ["INFORMATION_REQUEST_PARTICIPANT_ACCOUNT_ACCESS_CONFLICT", /already has its own access to this request/],
        ["INFORMATION_REQUEST_TRUST_SUSPENDED", /trusted relationship .* is suspended/],
        ["INFORMATION_REQUEST_CONTACT_PROOF_REQUIRED", /Verify your contact address again/],
        ["INFORMATION_REQUEST_PARTY_SHARING_NOT_PERMITTED", /does not permit sharing it with this account/],
        ["INFORMATION_REQUEST_RECIPIENT_SIGN_IN_REQUIRED", /add you to the Exchange and assign your part/],
        ["SUBSCRIPTION_SUSPENDED", /Changes to this request are paused/],
    ])("explains the refusal %s and keeps the access link working", async (reasonCode, message) => {
        vi.mocked(runtime.linkInformationRequestParticipantAccount).mockRejectedValue({
            errorMessage: "Refused",
            reasonCode
        });
        renderOffer();

        fireEvent.click(screen.getByRole("button", {name: "Link to my account"}));

        expect(await screen.findByText(message)).toBeTruthy();
        expect(screen.queryByText("Signed-in workspace")).toBeNull();
        expect(runtime.clearInformationRequestAccessCredentials).not.toHaveBeenCalled();
    });

    it("hands an ended access session back to the page, and no other refusal", async () => {
        const ended = {
            errorMessage: "Your verified session has ended.",
            reasonCode: "INFORMATION_REQUEST_ACCESS_SESSION_EXPIRED"
        };
        vi.mocked(runtime.linkInformationRequestParticipantAccount)
            .mockRejectedValueOnce({errorMessage: "Refused", reasonCode: "INFORMATION_REQUEST_TRUST_SUSPENDED"})
            .mockRejectedValueOnce(ended);
        const onAccessSessionEnded = vi.fn();
        render(
            <MemoryRouter initialEntries={["/nir?s=request-a"]}>
                <LinkingOffer onAccessSessionEnded={onAccessSessionEnded}/>
            </MemoryRouter>,
        );

        fireEvent.click(screen.getByRole("button", {name: "Link to my account"}));
        expect(await screen.findByText(/trusted relationship .* is suspended/)).toBeTruthy();
        expect(onAccessSessionEnded).not.toHaveBeenCalled();
        fireEvent.click(screen.getByRole("button", {name: "Link to my account"}));

        await waitFor(() => expect(onAccessSessionEnded).toHaveBeenCalledWith(ended));
        expect(runtime.clearInformationRequestAccessCredentials).not.toHaveBeenCalled();
    });

    it("asks a viewer who is not signed in to sign in in a new tab and continues once they have", async () => {
        auth.appUser = null;
        auth.refreshTokens = vi.fn().mockRejectedValue(new Error("No session"));
        renderOffer();

        const signIn = screen.getByRole("link", {name: "Sign in"}) as HTMLAnchorElement;
        expect(signIn.getAttribute("href")).toBe("/sign-in");
        expect(signIn.getAttribute("target")).toBe("_blank");
        expect(screen.queryByRole("button", {name: "Link to my account"})).toBeNull();
        fireEvent.click(screen.getByRole("button", {name: "I have signed in"}));

        await waitFor(() => expect(auth.refreshTokens).toHaveBeenCalled());
        expect(await screen.findByText(/You are not signed in yet/)).toBeTruthy();
        expect(unnamedControls(document.body)).toEqual([]);
        expect(duplicateIds(document.body)).toEqual([]);
    });
});
