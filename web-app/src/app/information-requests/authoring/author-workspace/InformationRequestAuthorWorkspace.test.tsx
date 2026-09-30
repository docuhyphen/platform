/** @vitest-environment jsdom */
import {cleanup, fireEvent, render, screen, waitFor, within} from "@testing-library/react";
import {MemoryRouter, Route, Routes} from "react-router-dom";
import {afterEach, beforeAll, beforeEach, describe, expect, it, vi} from "vitest";
import * as authoring from "../../../../services/informationRequestAuthoringService.ts";
import * as runtime from "../../../../services/informationRequestRuntimeService.ts";
import {
    InformationRequestAccessLinkStatus,
    InformationRequestExecutionStandingKind,
    InformationRequestStandingReason,
    InformationRequestContributorRole,
    InformationRequestOwnerType,
    InformationRequestPartyDto,
    InformationRequestRequiredness,
    InformationRequestRequirementType,
    InformationRequestResponseDisposition,
    InformationRequestResponseMode,
    InformationRequestResponseWorkspaceDto,
    InformationRequestReviewPolicy,
    InformationRequestShareRoleKey,
    InformationRequestState,
    InformationRequestTemplateStatus,
} from "../../../models/models.tsx";
import InformationRequestAuthorWorkspace from "./InformationRequestAuthorWorkspace.tsx";
import {unnamedControls} from "../../shared/testing/unnamedControls.ts";

vi.mock("../../../../services/informationRequestAuthoringService.ts", async () => ({
    ...(await vi.importActual<typeof authoring>("../../../../services/informationRequestAuthoringService.ts")),
    getInformationRequestParties: vi.fn(),
    getInformationRequestAccessLinks: vi.fn(),
    assignInformationRequestParty: vi.fn(),
    revokeInformationRequestParty: vi.fn(),
    issueInformationRequest: vi.fn(),
    cancelInformationRequest: vi.fn(),
    issueInformationRequestAccessLink: vi.fn(),
    rotateInformationRequestAccessLink: vi.fn(),
    revokeInformationRequestAccessLink: vi.fn(),
    getExchangeInformationRequests: vi.fn(),
    getInformationRequestLineage: vi.fn().mockResolvedValue({informationRequestId: "request-a", successors: []}),
}));
vi.mock("../../../../services/informationRequestRuntimeService.ts", async () => ({
    ...(await vi.importActual<typeof runtime>("../../../../services/informationRequestRuntimeService.ts")),
    getInformationRequestResponseWorkspace: vi.fn(),
}));
vi.mock("../../../../services/informationRequestOperationsService.ts", () => ({getInformationRequestClocks: vi.fn().mockResolvedValue([])}));
vi.mock("../../../../services/informationRequestAdministrationService.ts", () => ({
    getInformationRequestClockPolicies: vi.fn().mockResolvedValue([]),
    recordInformationRequestPrivacyRequest: vi.fn(),
}));
vi.mock("../../../../services/informationRequestOutcomeService.ts", () => ({
    getInformationRequestSubmissionPackages: vi.fn().mockResolvedValue([]),
    getInformationRequestAcceptedFacts: vi.fn().mockResolvedValue([]),
    getInformationRequestBusinessDecisions: vi.fn().mockResolvedValue([]),
}));
vi.mock("../../../../context/AuthContext.tsx", () => ({
    useAuth: () => ({
        appUser: {id: "user-a"},
        currentSession: {userId: "user-a", activeOrganizationId: "org-a"},
        hasCapability: (capability: string) => capability === "INFORMATION_REQUEST_PRIVACY_MANAGE",
    }),
}));

const workspace = (state = InformationRequestState.DRAFT): InformationRequestResponseWorkspaceDto => ({
    request: {
        id: "request-a",
        exchangeId: "exchange-a",
        templateVersionId: "version-a",
        ownerType: InformationRequestOwnerType.ORGANIZATION,
        ownerOrganizationId: "org-a",
        state,
        gatesExchangeClosure: true,
        aggregateRevision: 3,
        createdAt: "2026-09-27T08:00:00Z",
        updatedAt: "2026-09-27T08:00:00Z",
        requestETag: "\"request-3\"",
        conditionEvaluations: [],
    },
    title: "Periodic records request",
    templateVersion: {
        id: "version-a",
        templateDefinitionId: "definition-a",
        versionNumber: 1,
        status: InformationRequestTemplateStatus.PUBLISHED,
        sections: [{
            id: "section-a",
            sectionKey: "records",
            title: "Records",
            requirements: [{
                id: "binding-a",
                templateRequirementId: "requirement-a",
                requirementKey: "record-count",
                requirementType: InformationRequestRequirementType.FIELD,
                prompt: "How many records were kept?",
                helpText: "Count every record kept this period.",
                responseMode: InformationRequestResponseMode.PROVIDE,
                requiredness: InformationRequestRequiredness.REQUIRED,
                contributorRole: InformationRequestContributorRole.CONTRIBUTOR,
                reviewPolicy: InformationRequestReviewPolicy.NOT_REQUIRED,
                permittedDispositions: [InformationRequestResponseDisposition.PROVIDED, InformationRequestResponseDisposition.NOT_APPLICABLE],
                substituteRequirementKeys: [],
                supportingEvidenceRequirementKeys: [],
            }],
        }],
        groups: [],
        conditionRules: [],
        requiredCapabilities: [],
        createdAt: "2026-09-20T08:00:00Z",
    },
    responseETag: "\"responses-1\"",
    occurrences: [],
    responses: [],
    supportingEvidenceLinks: [],
    evidenceUploadAvailable: true,
    evidenceMalwareScanning: false,
    executionStanding: {kind: InformationRequestExecutionStandingKind.ACTIVE},
});

const contributor: InformationRequestPartyDto = {
    id: "party-a",
    informationRequestId: "request-a",
    roleKey: InformationRequestShareRoleKey.CONTRIBUTOR,
    active: true,
    principalId: "participant-a",
    principalKind: "PARTICIPANT",
    assignedAt: "2026-09-27T08:00:00Z",
    partyRevision: 1,
    partyETag: "\"party-a-1\"",
    label: "member@process.test",
};

const renderWorkspace = () => render(
    <MemoryRouter initialEntries={["/information-requests/request-a/manage"]}>
        <Routes>
            <Route path={"/information-requests/:requestId/manage"}
                   element={<InformationRequestAuthorWorkspace/>}/>
            <Route path={"/exchanges"}
                   element={<p>Exchange opened</p>}/>
        </Routes>
    </MemoryRouter>,
);

describe("InformationRequestAuthorWorkspace", () =>
{
    beforeAll(() =>
    {
        vi.stubGlobal("ResizeObserver", class
        {
            observe() {}
            unobserve() {}
            disconnect() {}
        });
    });

    beforeEach(() =>
    {
        vi.clearAllMocks();
        vi.mocked(runtime.getInformationRequestResponseWorkspace).mockResolvedValue(workspace());
        vi.mocked(authoring.getInformationRequestParties).mockResolvedValue({parties: [contributor], partiesETag: "\"parties-2\""});
        vi.mocked(authoring.getInformationRequestAccessLinks).mockResolvedValue([]);
        vi.mocked(authoring.assignInformationRequestParty).mockResolvedValue({party: contributor, partiesETag: "\"parties-3\""});
        vi.mocked(authoring.getExchangeInformationRequests).mockResolvedValue({requests: [], canCreate: true});
    });

    afterEach(cleanup);

    it("shows the request with its parties by name and asks for a Decision Maker until one is named", async () =>
    {
        renderWorkspace();

        expect(await screen.findByRole("heading", {name: "Periodic records request"})).toBeTruthy();
        expect(screen.getByText("Draft")).toBeTruthy();
        expect(screen.getByText("member@process.test")).toBeTruthy();
        expect(screen.getByText("Name a Decision Maker before issuing this request.")).toBeTruthy();
        fireEvent.click(screen.getByRole("button", {name: "Make me the Decision Maker"}));

        await waitFor(() => expect(authoring.assignInformationRequestParty).toHaveBeenCalledWith(
            "request-a",
            {roleKey: InformationRequestShareRoleKey.DECISION_MAKER, userId: "user-a"},
            "\"parties-2\"",
            expect.any(String),
        ));
    });

    it("says why a paused request takes no changes and offers none", async () =>
    {
        vi.mocked(runtime.getInformationRequestResponseWorkspace).mockResolvedValue({
            ...workspace(InformationRequestState.IN_PROGRESS),
            executionStanding: {
                kind: InformationRequestExecutionStandingKind.OPERATIONALLY_SUSPENDED,
                reason: InformationRequestStandingReason.SUBSCRIPTION_SUSPENDED,
            },
        });
        renderWorkspace();

        expect(await screen.findByText(/Changes to this request are paused/)).toBeTruthy();
        expect(screen.getByText(/account is suspended/)).toBeTruthy();
        expect(screen.queryByRole("button", {name: "Add party"})).toBeNull();
    });

    it("keeps an issued request workable after a lapse and says so", async () =>
    {
        vi.mocked(runtime.getInformationRequestResponseWorkspace).mockResolvedValue({
            ...workspace(InformationRequestState.IN_PROGRESS),
            executionStanding: {
                kind: InformationRequestExecutionStandingKind.CONTINUING_AFTER_LAPSE,
                reason: InformationRequestStandingReason.TRIAL_ENDED,
            },
        });
        renderWorkspace();

        expect(await screen.findByText(/continues as it was issued/)).toBeTruthy();
        expect(screen.getByRole("button", {name: "Add party"})).toBeTruthy();
    });

    it("issues under the request ETag and states a refusal", async () =>
    {
        vi.mocked(authoring.issueInformationRequest)
            .mockResolvedValueOnce(workspace(InformationRequestState.ISSUED).request)
            .mockRejectedValueOnce({errorMessage: "This request needs a Decision Maker"});
        renderWorkspace();

        fireEvent.click(await screen.findByRole("button", {name: "Issue"}));
        await waitFor(() => expect(authoring.issueInformationRequest).toHaveBeenCalledWith("request-a", "\"request-3\"", expect.any(String)));
        expect(await screen.findByText("The request was issued.")).toBeTruthy();

        fireEvent.click(screen.getByRole("button", {name: "Issue"}));
        expect(await screen.findByText("This request needs a Decision Maker")).toBeTruthy();
    });

    it("reloads a stale party change and asks the author to try again", async () =>
    {
        vi.mocked(authoring.assignInformationRequestParty).mockRejectedValueOnce({
            errorMessage: "Stale",
            reasonCode: "COMMAND_PRECONDITION_STALE",
        });
        renderWorkspace();

        fireEvent.click(await screen.findByRole("button", {name: "Make me the Decision Maker"}));

        expect(await screen.findByText("This request changed while you were working. The latest details are shown; try again.")).toBeTruthy();
        expect(vi.mocked(authoring.getInformationRequestParties).mock.calls.length).toBeGreaterThan(1);
    });

    it("shows an access link once to copy, resends it by rotation, and revokes it", async () =>
    {
        vi.mocked(authoring.issueInformationRequestAccessLink).mockResolvedValue({
            shareLinkId: "link-a",
            accessToken: "first-secret",
            status: InformationRequestAccessLinkStatus.ACTIVE,
            rotationCount: 0,
        });
        vi.mocked(authoring.rotateInformationRequestAccessLink).mockResolvedValue({
            shareLinkId: "link-a",
            accessToken: "second-secret",
            status: InformationRequestAccessLinkStatus.ACTIVE,
            rotationCount: 1,
        });
        vi.mocked(authoring.revokeInformationRequestAccessLink).mockResolvedValue({
            shareLinkId: "link-a",
            status: InformationRequestAccessLinkStatus.REVOKED,
            rotationCount: 1,
        });
        renderWorkspace();

        fireEvent.click(await screen.findByRole("button", {name: "Create link for member@process.test"}));
        const shown = await screen.findByRole("dialog", {name: "Access link"});
        expect((within(shown).getByLabelText("Link") as HTMLInputElement).value).toContain("t=first-secret");
        expect(authoring.issueInformationRequestAccessLink).toHaveBeenCalledWith("request-a", {partyId: "party-a"}, "\"party-a-1\"", expect.any(String));
        fireEvent.click(within(shown).getByRole("button", {name: "Close"}));

        vi.mocked(authoring.getInformationRequestAccessLinks).mockResolvedValue([{
            shareLinkId: "link-a",
            status: InformationRequestAccessLinkStatus.ACTIVE,
            rotationCount: 0,
            partyId: "party-a",
        }]);
        fireEvent.click(screen.getByRole("button", {name: "Refresh"}));
        fireEvent.click(await screen.findByRole("button", {name: "Resend link to member@process.test"}));
        const resent = await screen.findByRole("dialog", {name: "Access link"});
        expect((within(resent).getByLabelText("Link") as HTMLInputElement).value).toContain("t=second-secret");
        fireEvent.click(within(resent).getByRole("button", {name: "Close"}));

        fireEvent.click(screen.getByRole("button", {name: "Revoke link for member@process.test"}));
        await waitFor(() => expect(authoring.revokeInformationRequestAccessLink)
            .toHaveBeenCalledWith("request-a", "link-a", "\"party-a-1\"", expect.any(String)));
    });

    it("previews the request as recipients see it", async () =>
    {
        renderWorkspace();

        fireEvent.click(await screen.findByRole("button", {name: "Preview as recipient"}));
        const preview = await screen.findByRole("dialog", {name: "Preview as recipient"});

        expect(within(preview).getByText("Records")).toBeTruthy();
        expect(within(preview).getByText("How many records were kept?")).toBeTruthy();
        expect(within(preview).getByText("Required")).toBeTruthy();
        expect(within(preview).getByText("Count every record kept this period.")).toBeTruthy();
        expect(within(preview).getByText("Answers accepted: Provided, Not applicable")).toBeTruthy();
        expect(within(preview).queryByText("record-count")).toBeNull();
    });

    it("cancels with the reason the author states", async () =>
    {
        vi.mocked(authoring.cancelInformationRequest).mockResolvedValue(workspace(InformationRequestState.CANCELLED).request);
        renderWorkspace();

        fireEvent.click(await screen.findByRole("button", {name: "Cancel request"}));
        const dialog = await screen.findByRole("dialog", {name: "Cancel this request"});
        fireEvent.change(within(dialog).getByLabelText("Reason"), {target: {value: "NO_LONGER_NEEDED"}});
        fireEvent.click(within(dialog).getByRole("button", {name: "Cancel request"}));

        await waitFor(() => expect(authoring.cancelInformationRequest)
            .toHaveBeenCalledWith("request-a", "NO_LONGER_NEEDED", "\"request-3\"", expect.any(String)));
    });

    it("names every control and region of an issued request's workspace", async () =>
    {
        vi.mocked(runtime.getInformationRequestResponseWorkspace).mockResolvedValue(workspace(InformationRequestState.ISSUED));
        renderWorkspace();
        const outcomes = await screen.findByRole("region", {name: "Outcomes"});
        await within(outcomes).findByRole("region", {name: "Accepted facts"});

        expect(unnamedControls(document.body)).toEqual([]);
    });

    it("shows the request's accepted facts and business decisions once it is issued, and not on a draft", async () =>
    {
        renderWorkspace();
        expect(await screen.findByRole("button", {name: "Issue"})).toBeTruthy();
        expect(screen.queryByRole("region", {name: "Outcomes"})).toBeNull();
        cleanup();

        vi.mocked(runtime.getInformationRequestResponseWorkspace).mockResolvedValue(workspace(InformationRequestState.ISSUED));
        vi.mocked(authoring.getInformationRequestParties).mockResolvedValue({
            parties: [contributor, {...contributor, id: "party-s", roleKey: InformationRequestShareRoleKey.SUBJECT, subjectIdentityRefId: "subject-a"}],
            partiesETag: "\"parties-2\"",
        });
        renderWorkspace();

        const outcomes = await screen.findByRole("region", {name: "Outcomes"});
        expect(await within(outcomes).findByRole("region", {name: "Accepted facts"})).toBeTruthy();
        expect(within(outcomes).getByRole("region", {name: "Business decisions"})).toBeTruthy();
        expect(await within(outcomes).findByRole("region", {name: "Corrections"})).toBeTruthy();
    });
});
