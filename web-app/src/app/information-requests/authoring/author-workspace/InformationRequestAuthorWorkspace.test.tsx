/** @vitest-environment jsdom */
import {act, cleanup, fireEvent, render, screen, waitFor, within} from "@testing-library/react";
import {MemoryRouter, Route, Routes, useNavigate} from "react-router-dom";
import {afterEach, beforeAll, beforeEach, describe, expect, it, vi} from "vitest";
import * as authoring from "../../../../services/informationRequestAuthoringService.ts";
import * as operations from "../../../../services/informationRequestOperationsService.ts";
import * as outcomeReads from "../../../../services/informationRequestOutcomeService.ts";
import * as runtime from "../../../../services/informationRequestRuntimeService.ts";
import * as reviewReads from "../../../../services/informationRequestReviewService.ts";
import * as submissions from "../../../../services/informationRequestSubmissionService.ts";
import {formatInformationRequestTime} from "../../shared/informationRequestFormatting.ts";
import {
    FieldValueType,
    InformationRequestAcceptedFactConfidence,
    InformationRequestAcceptedFactConflictState,
    InformationRequestAcceptedFactDto,
    InformationRequestAcceptedFactFreshness,
    InformationRequestAcceptedFactVisibility,
    InformationRequestAccessLinkStatus,
    InformationRequestAuthorActionsDto,
    InformationRequestBusinessDecisionDto,
    InformationRequestBusinessDecisionKind,
    InformationRequestClockDto,
    InformationRequestClockState,
    InformationRequestCompletenessItemState,
    InformationRequestContributorRole,
    InformationRequestExecutionStandingDto,
    InformationRequestExecutionStandingKind,
    InformationRequestOwnerType,
    InformationRequestPartyDto,
    InformationRequestRecipientPreviewDto,
    InformationRequestRecurrenceUnit,
    InformationRequestRequiredness,
    InformationRequestRequirementType,
    InformationRequestResponseDisposition,
    InformationRequestResponseMode,
    InformationRequestResponseWorkspaceDto,
    InformationRequestReviewPolicy,
    InformationRequestShareRoleKey,
    InformationRequestStandingReason,
    InformationRequestState,
    InformationRequestSubmissionPackageDto,
    InformationRequestSummaryDto,
    InformationRequestTemplateStatus,
} from "../../../models/models.tsx";
import InformationRequestAuthorWorkspace from "./InformationRequestAuthorWorkspace.tsx";
import {duplicateIds} from "../../shared/testing/duplicateIds.ts";
import {unnamedControls} from "../../shared/testing/unnamedControls.ts";
import {everyAuthorAction, noAuthorAction, retainedRecordAuthorActions} from "../testing/authorActions.ts";

vi.mock("../../../../services/informationRequestAuthoringService.ts", async () => ({
    ...(await vi.importActual<typeof authoring>("../../../../services/informationRequestAuthoringService.ts")),
    getInformationRequestParties: vi.fn(),
    getInformationRequestAccessLinks: vi.fn(),
    getInformationRequestDocumentPlaceholders: vi.fn(),
    assignInformationRequestParty: vi.fn(),
    reassignInformationRequestParty: vi.fn(),
    revokeInformationRequestParty: vi.fn(),
    issueInformationRequest: vi.fn(),
    cancelInformationRequest: vi.fn(),
    supersedeInformationRequest: vi.fn(),
    defineInformationRequestRecurrence: vi.fn(),
    changeInformationRequestCompletionGate: vi.fn(),
    issueInformationRequestAccessLink: vi.fn(),
    rotateInformationRequestAccessLink: vi.fn(),
    revokeInformationRequestAccessLink: vi.fn(),
    getExchangeInformationRequests: vi.fn(),
    getInformationRequestRecipientPreview: vi.fn(),
    getInformationRequestLineage: vi.fn().mockResolvedValue({informationRequestId: "request-a", successors: []}),
}));
vi.mock("../../../../services/informationRequestRuntimeService.ts", async () => ({
    ...(await vi.importActual<typeof runtime>("../../../../services/informationRequestRuntimeService.ts")),
    getInformationRequestResponseWorkspace: vi.fn(),
    getInformationRequestManagementWorkspace: vi.fn(),
}));
vi.mock("../../../../services/informationRequestOperationsService.ts", () => ({getInformationRequestClocks: vi.fn().mockResolvedValue([])}));
vi.mock("../../../../services/informationRequestSubmissionService.ts", async () => ({
    ...(await vi.importActual<typeof submissions>("../../../../services/informationRequestSubmissionService.ts")),
    withdrawInformationRequestPackage: vi.fn(),
}));
vi.mock("../../../../services/informationRequestReviewService.ts", () => ({listInformationRequestReviews: vi.fn().mockResolvedValue([])}));
vi.mock("../../../../services/informationRequestAdministrationService.ts", () => ({
    getInformationRequestClockPolicies: vi.fn().mockResolvedValue([]),
    recordInformationRequestPrivacyRequest: vi.fn(),
}));
vi.mock("../../../../services/informationRequestOutcomeService.ts", () => ({
    getInformationRequestSubmissionPackages: vi.fn().mockResolvedValue([]),
    getInformationRequestAcceptedFacts: vi.fn().mockResolvedValue([]),
    getInformationRequestBusinessDecisions: vi.fn().mockResolvedValue([]),
}));
vi.mock("../add-party-dialog/usePartyCandidates.ts", () => ({
    usePartyCandidates: () => ({
        groups: [{id: "group-a", name: "Records team"}],
        people: [{id: "user-b", name: "Second Member (second@process.test)"}],
        subjects: [],
    }),
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
        createdAt: Date.parse("2026-09-27T08:00:00Z"),
        updatedAt: Date.parse("2026-09-27T08:00:00Z"),
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
        createdAt: Date.parse("2026-09-20T08:00:00Z"),
    },
    responseETag: "\"responses-1\"",
    occurrences: [],
    responses: [],
    supportingEvidenceLinks: [],
    evidenceUploadAvailable: true,
    evidenceMalwareScanning: false,
    requirementsAwaitingStepUp: 0,
    executionStanding: {kind: InformationRequestExecutionStandingKind.ACTIVE},
    authorActions: everyAuthorAction,
});

const withAuthorActions = (
    projection: InformationRequestResponseWorkspaceDto,
    authorActions: InformationRequestAuthorActionsDto,
): InformationRequestResponseWorkspaceDto => ({...projection, authorActions});

const managedSummary = (id: string, title: string, state: InformationRequestState): InformationRequestSummaryDto =>
    ({
        id,
        exchangeId: "exchange-a",
        title,
        state,
        permissions: {canManage: true, canRespond: false, canReview: false}
    }) as InformationRequestSummaryDto;

const submittedPackage: InformationRequestSubmissionPackageDto = {
    id: "package-a",
    informationRequestId: "request-a",
    packageNumber: 1,
    templateVersionId: "version-a",
    contentHash: "content-a",
    manifestHash: "manifest-a",
    reviewRequired: false,
    completesRequest: true,
    submittedAt: Date.parse("2026-09-28T08:00:00Z"),
    submittedByCaller: false,
    withdrawn: false,
    items: [{
        id: "item-a",
        requirementId: "runtime-requirement-a",
        requirementKey: "record-count",
        requirementType: InformationRequestRequirementType.FIELD,
        occurrencePath: "",
        completenessState: InformationRequestCompletenessItemState.COMPLETE,
        disposition: InformationRequestResponseDisposition.PROVIDED,
        fieldValue: "12",
        fieldValueCleared: false,
        evidence: [],
    }],
    attestations: [],
    supportingEvidenceLinks: [],
    undisclosedItemCount: 0,
    current: true,
    withdrawable: false,
};

const promotedFact: InformationRequestAcceptedFactDto = {
    id: "fact-a",
    subjectIdentityRefId: "subject-a",
    purposeKey: "record-review",
    policyBasisKey: "retained-confirmation",
    evidenceVersionIds: [],
    fieldDefinitionId: "field-a",
    valueType: FieldValueType.SHORT_TEXT,
    value: "12",
    sourceInformationRequestId: "request-a",
    sourcePackageId: "package-a",
    sourceSubmissionItemId: "item-a",
    sourceRequirementId: "runtime-requirement-a",
    visibility: InformationRequestAcceptedFactVisibility.REQUESTING_SIDE,
    confidence: InformationRequestAcceptedFactConfidence.DECLARED,
    validFrom: Date.parse("2026-09-28T08:00:00Z"),
    conflictState: InformationRequestAcceptedFactConflictState.NONE,
    promotedAt: Date.parse("2026-09-28T09:00:00Z"),
    revoked: false,
    freshness: InformationRequestAcceptedFactFreshness.CURRENT,
};

const recordedDecision: InformationRequestBusinessDecisionDto = {
    id: "decision-a",
    informationRequestId: "request-a",
    owningProcessKey: "intake",
    outcomeCode: "approved",
    kind: InformationRequestBusinessDecisionKind.ORIGINAL,
    decisionRevision: 1,
    decidedAt: Date.parse("2026-09-28T10:00:00Z"),
    recordedAt: Date.parse("2026-09-28T10:05:00Z"),
    recordedByCaller: true,
};

const contributor: InformationRequestPartyDto = {
    id: "party-a",
    informationRequestId: "request-a",
    roleKey: InformationRequestShareRoleKey.CONTRIBUTOR,
    active: true,
    principalId: "participant-a",
    principalKind: "PARTICIPANT",
    assignedAt: Date.parse("2026-09-27T08:00:00Z"),
    partyRevision: 1,
    partyETag: "\"party-a-1\"",
    label: "member@process.test",
};

const preparer: InformationRequestPartyDto = {
    ...contributor,
    id: "party-b",
    roleKey: InformationRequestShareRoleKey.PREPARER,
    principalId: "participant-b",
    partyETag: "\"party-b-1\"",
    label: "preparer@process.test",
};

const decisionMaker: InformationRequestPartyDto = {
    ...contributor,
    id: "party-c",
    roleKey: InformationRequestShareRoleKey.DECISION_MAKER,
    principalId: "user-a",
    principalKind: "USER",
    partyETag: "\"party-c-1\"",
    label: "Request Owner",
};

const subjectOfRequest: InformationRequestPartyDto = {
    ...contributor,
    id: "party-s",
    roleKey: InformationRequestShareRoleKey.SUBJECT,
    principalId: null,
    principalKind: null,
    subjectIdentityRefId: "subject-a",
    assignedAt: Date.parse("2026-09-27T08:00:00Z"),
    partyETag: "\"party-s-1\"",
    label: "Reference 1001",
};

const runningClock: InformationRequestClockDto = {
    id: "clock-a",
    clockKey: "response",
    policyVersionId: "policy-version-a",
    policyVersionNumber: 1,
    clockType: "CALENDAR",
    urgency: "STANDARD",
    receivedAt: Date.parse("2026-09-27T08:00:00Z"),
    state: InformationRequestClockState.RUNNING,
    dueAt: Date.parse("2026-09-29T08:00:00Z"),
    dueCycle: 0,
    clockETag: "\"clock-a-1\"",
    events: [],
};

const pausedClock: InformationRequestClockDto = {
    ...runningClock,
    id: "clock-b",
    clockKey: "review",
    state: InformationRequestClockState.PAUSED,
    remainingSeconds: 3600,
    clockETag: "\"clock-b-1\"",
};

const STOPPED_OR_PAUSED: [string, InformationRequestExecutionStandingDto][] = [
    ["changes are paused", {
        kind: InformationRequestExecutionStandingKind.OPERATIONALLY_SUSPENDED,
        reason: InformationRequestStandingReason.SUBSCRIPTION_SUSPENDED,
    }],
    ["the request was stopped", {
        kind: InformationRequestExecutionStandingKind.EXECUTION_GRANT_REVOKED,
        reason: InformationRequestStandingReason.EXECUTION_GRANT_REVOKED,
    }],
];

const authorProjection = (): InformationRequestResponseWorkspaceDto =>
    ({...workspace(), templateVersion: {...workspace().templateVersion, sections: []}});

const withStanding = (
    projection: InformationRequestResponseWorkspaceDto,
    executionStanding: InformationRequestExecutionStandingDto,
): InformationRequestResponseWorkspaceDto => ({
    ...projection,
    request: {...projection.request, executionStanding},
    executionStanding
});

const previewFor = (party: InformationRequestPartyDto, prompt: string): InformationRequestRecipientPreviewDto => ({
    partyId: party.id,
    roleKey: party.roleKey,
    templateVersion: {
        ...workspace().templateVersion,
        sections: workspace().templateVersion.sections.map(section => ({
            ...section,
            requirements: section.requirements.map(requirement => ({
                ...requirement,
                id: `${party.id}-binding`,
                prompt
            })),
        })),
    },
});

const renderWorkspace = () => render(
    <MemoryRouter initialEntries={["/information-requests/request-a/manage"]}>
        <OtherRequestLink/>
        <Routes>
            <Route path={"/information-requests/:requestId/manage"}
                   element={<InformationRequestAuthorWorkspace/>}/>
            <Route path={"/exchanges"}
                   element={<p>Exchange opened</p>}/>
        </Routes>
    </MemoryRouter>,
);

const OtherRequestLink = () => {
    const navigate = useNavigate();
    return (
        <button id={"open-other-request"}
                onClick={() => navigate("/information-requests/request-b/manage")}>
            Open the other request
        </button>
    );
};

describe("InformationRequestAuthorWorkspace", () =>
{
    beforeAll(() =>
    {
        vi.stubGlobal("ResizeObserver", class
        {
            observe() {
            }

            unobserve() {
            }

            disconnect() {
            }
        });
    });

    beforeEach(() =>
    {
        vi.clearAllMocks();
        vi.mocked(runtime.getInformationRequestManagementWorkspace).mockResolvedValue(workspace());
        vi.mocked(authoring.getInformationRequestParties).mockResolvedValue({
            parties: [contributor],
            partiesETag: "\"parties-2\""
        });
        vi.mocked(authoring.getInformationRequestAccessLinks).mockResolvedValue([]);
        vi.mocked(authoring.getInformationRequestDocumentPlaceholders).mockResolvedValue([]);
        vi.mocked(authoring.getInformationRequestRecipientPreview)
            .mockResolvedValue(previewFor(contributor, "How many records were kept?"));
        vi.mocked(authoring.assignInformationRequestParty).mockResolvedValue({
            party: contributor,
            partiesETag: "\"parties-3\""
        });
        vi.mocked(authoring.getExchangeInformationRequests).mockResolvedValue({requests: [], canCreate: true});
        vi.mocked(operations.getInformationRequestClocks).mockResolvedValue([]);
    });

    afterEach(cleanup);

    it("refreshes every panel-local read with the Manage refresh action", async () => {
        vi.mocked(runtime.getInformationRequestManagementWorkspace).mockResolvedValue(workspace(InformationRequestState.IN_PROGRESS));
        renderWorkspace();
        const reads = [operations.getInformationRequestClocks, authoring.getInformationRequestLineage,
            outcomeReads.getInformationRequestSubmissionPackages, reviewReads.listInformationRequestReviews];
        await waitFor(() => reads.forEach(read => expect(read).toHaveBeenCalled()));
        const counts = reads.map(read => vi.mocked(read).mock.calls.length);

        fireEvent.click(screen.getByRole("button", {name: "Refresh"}));

        await waitFor(() => reads.forEach((read, index) => expect(vi.mocked(read).mock.calls.length).toBeGreaterThan(counts[index])));
    });

    it("shows the rebased deadline after issuing a draft clock", async () => {
        const reissued = {...runningClock, dueAt: runningClock.dueAt! + 86400000, clockETag: '"clock-a-2"'};
        vi.mocked(runtime.getInformationRequestManagementWorkspace)
            .mockResolvedValueOnce(workspace())
            .mockResolvedValue(workspace(InformationRequestState.ISSUED));
        vi.mocked(operations.getInformationRequestClocks).mockResolvedValueOnce([runningClock]).mockResolvedValue([reissued]);
        vi.mocked(authoring.issueInformationRequest).mockResolvedValue(workspace(InformationRequestState.ISSUED).request);
        renderWorkspace();
        await waitFor(() => expect(document.getElementById("information-request-clock-panel-clock-a-due")?.textContent)
            .toBe(`Due ${formatInformationRequestTime(runningClock.dueAt)}`));

        fireEvent.click(screen.getByRole("button", {name: "Issue"}));

        await waitFor(() => expect(document.getElementById("information-request-clock-panel-clock-a-due")?.textContent)
            .toBe(`Due ${formatInformationRequestTime(reissued.dueAt)}`));
    });

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

    it("asks for a Decision Maker only on a draft, though an issued request has none", async () => {
        vi.mocked(runtime.getInformationRequestManagementWorkspace).mockResolvedValue(workspace(InformationRequestState.ISSUED));
        renderWorkspace();

        expect(await screen.findByRole("button", {name: "Add party"})).toBeTruthy();
        expect(screen.queryByText("Name a Decision Maker before issuing this request.")).toBeNull();
        expect(screen.queryByRole("button", {name: "Make me the Decision Maker"})).toBeNull();
    });

    it("says that only the named Decision Maker can issue a draft when someone else holds the role", async () => {
        const colleague = {...decisionMaker, principalId: "user-b", label: "Colleague Owner"};
        vi.mocked(runtime.getInformationRequestManagementWorkspace)
            .mockResolvedValue(withAuthorActions(workspace(), {...everyAuthorAction, issue: false}));
        vi.mocked(authoring.getInformationRequestParties).mockResolvedValue({
            parties: [contributor, colleague],
            partiesETag: "\"parties-2\"",
        });
        renderWorkspace();

        expect(await screen.findByText("Only the Decision Maker, Colleague Owner, can issue this request.")).toBeTruthy();
        expect(screen.queryByRole("button", {name: "Issue"})).toBeNull();
        expect(screen.queryByText("Name a Decision Maker before issuing this request.")).toBeNull();
    });

    it("says why the author was not named the Decision Maker when the new request opens", async () => {
        render(
            <MemoryRouter initialEntries={[{
                pathname: "/information-requests/request-a/manage",
                state: {decisionMakerFailure: "Access denied"},
            }]}>
                <Routes>
                    <Route path={"/information-requests/:requestId/manage"}
                           element={<InformationRequestAuthorWorkspace/>}/>
                </Routes>
            </MemoryRouter>,
        );

        expect(await screen.findByText("You were not named the Decision Maker when this request was created. Access denied"))
            .toBeTruthy();
        expect(screen.getByRole("button", {name: "Make me the Decision Maker"})).toBeTruthy();
    });

    it("lists the documents its Blueprint expected as read-only reference", async () => {
        vi.mocked(authoring.getInformationRequestDocumentPlaceholders).mockResolvedValue([
            {
                id: "placeholder-a",
                title: "Signed record",
                required: true,
                restrictType: true,
                restrictedType: "PDF",
                libraryTitle: "Record template",
                libraryFileName: "record-template.pdf",
                libraryFileSizeBytes: 2048,
                displayOrder: 0,
            },
            {id: "placeholder-b", title: "Supporting statement", required: false, restrictType: false, displayOrder: 1},
        ]);
        renderWorkspace();

        const panel = await screen.findByRole("region", {name: "Expected documents"});
        expect(within(panel).getByText("Signed record")).toBeTruthy();
        expect(within(panel).getByText("Required")).toBeTruthy();
        expect(within(panel).getByText("PDF only")).toBeTruthy();
        expect(within(panel).getByText(/record-template\.pdf/)).toBeTruthy();
        expect(within(panel).getByText("Supporting statement")).toBeTruthy();
        expect(within(panel).getByText("Optional")).toBeTruthy();
        expect(within(panel).queryAllByRole("button")).toHaveLength(0);
        expect(authoring.getInformationRequestDocumentPlaceholders).toHaveBeenCalledWith("request-a");
    });

    it("shows no expected documents when the request has none or the caller may not read them", async () => {
        vi.mocked(authoring.getInformationRequestDocumentPlaceholders)
            .mockRejectedValue({errorMessage: "Access denied", reasonCode: "INFORMATION_REQUEST_FORBIDDEN"});
        renderWorkspace();

        expect(await screen.findByRole("heading", {name: "Periodic records request"})).toBeTruthy();
        expect(screen.queryByRole("region", {name: "Expected documents"})).toBeNull();
        expect(screen.queryByText("Access denied")).toBeNull();
    });

    it("states a refused read of the expected documents with Try again and lists them once a retry loads", async () => {
        vi.mocked(authoring.getInformationRequestDocumentPlaceholders)
            .mockRejectedValueOnce({errorMessage: "The expected documents could not be read."})
            .mockResolvedValue([{
                id: "placeholder-a",
                title: "Signed record",
                required: true,
                restrictType: false,
                displayOrder: 0
            }]);
        renderWorkspace();

        await waitFor(() => expect(document.getElementById("information-request-expected-documents-load-error")?.textContent)
            .toContain("The expected documents could not be read."));
        fireEvent.click(within(document.getElementById("information-request-expected-documents-load-error") as HTMLElement)
            .getByRole("button", {name: "Try again"}));
        const panel = await screen.findByRole("region", {name: "Expected documents"});
        expect(within(panel).getByText("Signed record")).toBeTruthy();
        expect(document.getElementById("information-request-expected-documents-load-error")).toBeNull();
    });

    it("offers no link action while the access links cannot be read, states it with Try again, and offers them once a retry loads", async () => {
        vi.mocked(authoring.getInformationRequestAccessLinks)
            .mockRejectedValueOnce({errorMessage: "The access links could not be read."})
            .mockResolvedValue([]);
        renderWorkspace();

        await waitFor(() => expect(document.getElementById("information-request-parties-links-load-error")?.textContent)
            .toContain("The access links could not be read."));
        expect(screen.getByText("member@process.test")).toBeTruthy();
        expect(screen.queryByRole("button", {name: "Create link for member@process.test"})).toBeNull();
        fireEvent.click(within(document.getElementById("information-request-parties-links-load-error") as HTMLElement)
            .getByRole("button", {name: "Try again"}));
        expect(await screen.findByRole("button", {name: "Create link for member@process.test"})).toBeTruthy();
        expect(document.getElementById("information-request-parties-links-load-error")).toBeNull();
    });

    it("says why a paused request takes no changes and offers none", async () =>
    {
        vi.mocked(runtime.getInformationRequestManagementWorkspace).mockResolvedValue(withStanding(workspace(InformationRequestState.IN_PROGRESS), {
            kind: InformationRequestExecutionStandingKind.OPERATIONALLY_SUSPENDED,
            reason: InformationRequestStandingReason.SUBSCRIPTION_SUSPENDED,
        }));
        renderWorkspace();

        expect(await screen.findByText(/Changes to this request are paused/)).toBeTruthy();
        expect(screen.getByText(/account is suspended/)).toBeTruthy();
        expect(screen.queryByRole("button", {name: "Add party"})).toBeNull();
        expect(screen.queryByRole("button", {name: "Supersede"})).toBeNull();
        expect(screen.queryByRole("button", {name: "Cancel request"})).toBeNull();
        expect(screen.queryByRole("switch", {name: "Must finish before the Exchange can end"})).toBeNull();
        expect(screen.getByText("This request must finish before its Exchange can end.")).toBeTruthy();
    });

    it("says why a read-only draft cannot be issued and still lets the owner cancel it or stop it holding back the Exchange", async () => {
        vi.mocked(runtime.getInformationRequestManagementWorkspace).mockResolvedValue(withStanding(workspace(), {
            kind: InformationRequestExecutionStandingKind.NEW_WORK_UNAVAILABLE,
            reason: InformationRequestStandingReason.TRIAL_ENDED,
        }));
        renderWorkspace();

        expect(await screen.findByText(/This draft cannot be issued or changed right now/)).toBeTruthy();
        expect(screen.queryByRole("button", {name: "Issue"})).toBeNull();
        expect(screen.queryByRole("button", {name: "Supersede"})).toBeNull();
        expect(screen.getByRole("button", {name: "Cancel request"})).toBeTruthy();
        expect(screen.getByRole("switch", {name: "Must finish before the Exchange can end"})).toBeTruthy();
        await waitFor(() => expect(authoring.getInformationRequestLineage).toHaveBeenCalledWith("request-a"));
        expect(document.getElementById("information-request-recurrence-form")).toBeNull();
    });

    it("lets the owner supersede an active draft with another request it manages on the Exchange", async () => {
        vi.mocked(authoring.getExchangeInformationRequests).mockResolvedValue({
            requests: [
                managedSummary("request-a", "Periodic records request", InformationRequestState.DRAFT),
                managedSummary("request-b", "Revised records request", InformationRequestState.DRAFT),
            ],
            canCreate: true,
        });
        vi.mocked(authoring.supersedeInformationRequest).mockResolvedValue(workspace(InformationRequestState.SUPERSEDED).request);
        renderWorkspace();

        fireEvent.click(await screen.findByRole("button", {name: "Supersede"}));
        const dialog = await screen.findByRole("dialog", {name: "Supersede this request"});
        expect(await within(dialog).findByRole("option", {name: "Revised records request"})).toBeTruthy();
        fireEvent.change(within(dialog).getByLabelText("Replacement request"), {target: {value: "request-b"}});
        fireEvent.change(within(dialog).getByLabelText("Reason"), {target: {value: "requirements-changed"}});
        fireEvent.click(within(dialog).getByRole("button", {name: "Supersede"}));

        await waitFor(() => expect(authoring.supersedeInformationRequest)
            .toHaveBeenCalledWith("request-a", "request-b", "requirements-changed", "\"request-3\"", expect.any(String)));
        expect(await screen.findByText("The request was superseded.")).toBeTruthy();
    });

    it("lets the owner schedule an active draft's recurrence on its page but start no follow-up, review or outcome before it is issued", async () => {
        vi.mocked(authoring.defineInformationRequestRecurrence).mockResolvedValue({} as never);
        renderWorkspace();

        const followUps = await screen.findByRole("region", {name: "Follow-up requests"});
        fireEvent.change(await within(followUps).findByLabelText("Repeat every"), {target: {value: "3"}});
        fireEvent.change(within(followUps).getByLabelText("First due"), {target: {value: "2026-11-02T09:00"}});
        fireEvent.click(within(followUps).getByRole("button", {name: "Schedule"}));

        await waitFor(() => expect(authoring.defineInformationRequestRecurrence).toHaveBeenCalledWith(
            "request-a",
            {
                intervalUnit: InformationRequestRecurrenceUnit.MONTH,
                intervalCount: 3,
                firstDueAt: new Date("2026-11-02T09:00").toISOString(),
            },
            "\"request-3\"",
            expect.any(String),
        ));
        expect(within(followUps).queryByRole("button", {name: "Request a supplement"})).toBeNull();
        expect(within(followUps).queryByRole("button", {name: "Create the next request"})).toBeNull();
        expect(screen.queryByRole("region", {name: "Outcomes"})).toBeNull();
    });

    it("offers no Supersede or recurrence scheduling on a draft the server lets the owner neither supersede nor schedule", async () => {
        vi.mocked(runtime.getInformationRequestManagementWorkspace)
            .mockResolvedValue(withAuthorActions(workspace(), {
                ...everyAuthorAction,
                supersede: false,
                scheduleFollowUps: false
            }));
        renderWorkspace();

        expect(await screen.findByRole("button", {name: "Issue"})).toBeTruthy();
        expect(screen.getByRole("button", {name: "Cancel request"})).toBeTruthy();
        await waitFor(() => expect(authoring.getInformationRequestLineage).toHaveBeenCalledWith("request-a"));
        expect(screen.queryByRole("button", {name: "Supersede"})).toBeNull();
        expect(document.getElementById("information-request-recurrence-form")).toBeNull();
    });

    it("still lets the owner supersede, cancel, or stop the Exchange waiting for a request that was stopped", async () => {
        vi.mocked(runtime.getInformationRequestManagementWorkspace).mockResolvedValue(withStanding(workspace(InformationRequestState.IN_PROGRESS), {
            kind: InformationRequestExecutionStandingKind.EXECUTION_GRANT_REVOKED,
            reason: InformationRequestStandingReason.EXECUTION_GRANT_REVOKED,
        }));
        vi.mocked(authoring.changeInformationRequestCompletionGate).mockResolvedValue({
            ...workspace(InformationRequestState.IN_PROGRESS).request,
            gatesExchangeClosure: false,
        });
        renderWorkspace();

        expect(await screen.findByText(/This request was stopped/)).toBeTruthy();
        expect(screen.getByRole("button", {name: "Supersede"})).toBeTruthy();
        expect(screen.getByRole("button", {name: "Cancel request"})).toBeTruthy();
        expect(screen.queryByRole("button", {name: "Add party"})).toBeNull();
        fireEvent.click(screen.getByRole("switch", {name: "Must finish before the Exchange can end"}));

        await waitFor(() => expect(authoring.changeInformationRequestCompletionGate)
            .toHaveBeenCalledWith("request-a", false, "\"request-3\"", expect.any(String)));
        expect(await screen.findByText("The Exchange no longer waits for this request.")).toBeTruthy();
    });

    it("keeps an issued request workable after a lapse and says so", async () =>
    {
        vi.mocked(runtime.getInformationRequestManagementWorkspace).mockResolvedValue({
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

    it.each(STOPPED_OR_PAUSED)("still lets the manager remove a party and revoke its access link while %s", async (_case, standing) => {
        vi.mocked(runtime.getInformationRequestManagementWorkspace)
            .mockResolvedValue(withStanding(workspace(InformationRequestState.IN_PROGRESS), standing));
        vi.mocked(authoring.getInformationRequestAccessLinks).mockResolvedValue([{
            shareLinkId: "link-a",
            status: InformationRequestAccessLinkStatus.ACTIVE,
            rotationCount: 0,
            partyId: "party-a",
        }]);
        vi.mocked(authoring.revokeInformationRequestAccessLink).mockResolvedValue({
            shareLinkId: "link-a",
            status: InformationRequestAccessLinkStatus.REVOKED,
            rotationCount: 0,
        });
        vi.mocked(authoring.revokeInformationRequestParty).mockResolvedValue({
            party: {...contributor, active: false},
            partiesETag: "\"parties-3\"",
        });
        renderWorkspace();

        fireEvent.click(await screen.findByRole("button", {name: "Revoke link for member@process.test"}));
        await waitFor(() => expect(authoring.revokeInformationRequestAccessLink)
            .toHaveBeenCalledWith("request-a", "link-a", "\"party-a-1\"", expect.any(String)));
        expect(await screen.findByText("The link was revoked.")).toBeTruthy();
        fireEvent.click(await screen.findByRole("button", {name: "Remove member@process.test"}));
        await waitFor(() => expect(authoring.revokeInformationRequestParty)
            .toHaveBeenCalledWith("request-a", "party-a", "\"parties-2\"", expect.any(String)));
        expect(screen.queryByRole("button", {name: "Add party"})).toBeNull();
        expect(screen.queryByRole("button", {name: "Reassign member@process.test"})).toBeNull();
        expect(screen.queryByRole("button", {name: "Resend link to member@process.test"})).toBeNull();
        expect(screen.queryByRole("button", {name: "Make me the Decision Maker"})).toBeNull();
    });

    it("lets the owner pause and extend the clocks of a stopped request but not start or resume one", async () => {
        vi.mocked(runtime.getInformationRequestManagementWorkspace)
            .mockResolvedValue(withStanding(workspace(InformationRequestState.IN_PROGRESS), STOPPED_OR_PAUSED[1][1]));
        vi.mocked(operations.getInformationRequestClocks).mockResolvedValue([runningClock, pausedClock]);
        renderWorkspace();

        expect(await screen.findByRole("button", {name: "Pause response clock"})).toBeTruthy();
        expect(screen.getByRole("button", {name: "Extend response clock"})).toBeTruthy();
        expect(screen.getByRole("button", {name: "Extend review clock"})).toBeTruthy();
        expect(screen.queryByRole("button", {name: "Resume review clock"})).toBeNull();
        expect(screen.queryByRole("button", {name: "Start clock"})).toBeNull();
    });

    it("offers no clock change while changes are paused", async () => {
        vi.mocked(runtime.getInformationRequestManagementWorkspace)
            .mockResolvedValue(withStanding(workspace(InformationRequestState.IN_PROGRESS), STOPPED_OR_PAUSED[0][1]));
        vi.mocked(operations.getInformationRequestClocks).mockResolvedValue([runningClock, pausedClock]);
        renderWorkspace();

        expect(await screen.findByText("Time left when paused: 1h 0m")).toBeTruthy();
        expect(screen.queryByRole("button", {name: "Pause response clock"})).toBeNull();
        expect(screen.queryByRole("button", {name: "Extend response clock"})).toBeNull();
        expect(screen.queryByRole("button", {name: "Resume review clock"})).toBeNull();
        expect(screen.queryByRole("button", {name: "Start clock"})).toBeNull();
    });

    it("offers every clock change on an active request", async () => {
        vi.mocked(runtime.getInformationRequestManagementWorkspace).mockResolvedValue(workspace(InformationRequestState.IN_PROGRESS));
        vi.mocked(operations.getInformationRequestClocks).mockResolvedValue([runningClock, pausedClock]);
        renderWorkspace();

        expect(await screen.findByRole("button", {name: "Pause response clock"})).toBeTruthy();
        expect(screen.getByRole("button", {name: "Extend response clock"})).toBeTruthy();
        expect(screen.getByRole("button", {name: "Resume review clock"})).toBeTruthy();
        expect(screen.getByRole("button", {name: "Reassign member@process.test"})).toBeTruthy();
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

    it("changes whether the Exchange waits for the request under the request ETag, only while it can change", async () => {
        vi.mocked(authoring.changeInformationRequestCompletionGate).mockResolvedValue({
            ...workspace().request,
            gatesExchangeClosure: false,
        });
        renderWorkspace();

        fireEvent.click(await screen.findByRole("switch", {name: "Must finish before the Exchange can end"}));

        await waitFor(() => expect(authoring.changeInformationRequestCompletionGate)
            .toHaveBeenCalledWith("request-a", false, "\"request-3\"", expect.any(String)));
        expect(await screen.findByText("The Exchange no longer waits for this request.")).toBeTruthy();
        cleanup();

        vi.mocked(runtime.getInformationRequestManagementWorkspace).mockResolvedValue(workspace(InformationRequestState.CLOSED));
        renderWorkspace();

        expect(await screen.findByText("This request must finish before its Exchange can end.")).toBeTruthy();
        expect(screen.queryByRole("switch", {name: "Must finish before the Exchange can end"})).toBeNull();
    });

    it("reassigns a party to a person with an account under the parties ETag", async () => {
        vi.mocked(authoring.reassignInformationRequestParty).mockResolvedValue({
            party: contributor,
            partiesETag: "\"parties-3\""
        });
        renderWorkspace();

        fireEvent.click(await screen.findByRole("button", {name: "Reassign member@process.test"}));
        const dialog = await screen.findByRole("dialog", {name: "Reassign party"});
        const person = within(dialog).getByLabelText("Person") as HTMLSelectElement;
        fireEvent.change(person, {target: {value: "user-b"}});
        fireEvent.click(within(dialog).getByRole("button", {name: "Reassign"}));

        await waitFor(() => expect(authoring.reassignInformationRequestParty).toHaveBeenCalledWith(
            "request-a",
            "party-a",
            {userId: "user-b"},
            "\"parties-2\"",
            expect.any(String),
        ));
        expect(await screen.findByText("The party was reassigned.")).toBeTruthy();
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

    it("opens no access link when the request can no longer be read after the link is created", async () => {
        vi.mocked(runtime.getInformationRequestManagementWorkspace)
            .mockResolvedValueOnce(workspace())
            .mockRejectedValueOnce({
                errorMessage: "Access to this request was removed",
                reasonCode: "INFORMATION_REQUEST_FORBIDDEN"
            });
        vi.mocked(authoring.issueInformationRequestAccessLink).mockResolvedValue({
            shareLinkId: "link-a",
            accessToken: "withheld-secret",
            status: InformationRequestAccessLinkStatus.ACTIVE,
            rotationCount: 0,
        });
        renderWorkspace();

        fireEvent.click(await screen.findByRole("button", {name: "Create link for member@process.test"}));

        expect(await screen.findByText("Access to this request was removed")).toBeTruthy();
        expect(authoring.issueInformationRequestAccessLink).toHaveBeenCalledTimes(1);
        expect(screen.queryByRole("dialog", {name: "Access link"})).toBeNull();
        expect(screen.queryByDisplayValue(/withheld-secret/)).toBeNull();
        expect(screen.queryByRole("heading", {name: "Periodic records request"})).toBeNull();
    });

    it("shows nothing of the previous request after moving to another, while it loads and once its read is refused", async () => {
        let refuse: (reason: unknown) => void = () => undefined;
        const otherRead = new Promise<InformationRequestResponseWorkspaceDto>((_, reject) => {
            refuse = reject;
        });
        vi.mocked(runtime.getInformationRequestManagementWorkspace)
            .mockImplementation(requestId => requestId === "request-a" ? Promise.resolve(workspace()) : otherRead);
        renderWorkspace();
        fireEvent.click(await screen.findByRole("button", {name: "Preview as recipient"}));
        expect(await screen.findByRole("dialog", {name: "Preview as recipient"})).toBeTruthy();

        fireEvent.click(screen.getByRole("button", {name: "Open the other request"}));

        await waitFor(() => expect(screen.queryByRole("heading", {name: "Periodic records request"})).toBeNull());
        expect(screen.queryByRole("dialog", {name: "Preview as recipient"})).toBeNull();
        expect(screen.queryByText("member@process.test")).toBeNull();
        expect(screen.getByText("Loading Information Request")).toBeTruthy();
        await act(async () => refuse({errorMessage: "Request B access refused"}));
        expect(await screen.findByText("Request B access refused")).toBeTruthy();
        expect(screen.queryByRole("heading", {name: "Periodic records request"})).toBeNull();
        expect(screen.queryByRole("button", {name: "Issue"})).toBeNull();
    });

    it("opens the next request without the previous request's dialogs", async () => {
        vi.mocked(runtime.getInformationRequestManagementWorkspace).mockImplementation(async requestId => requestId === "request-a"
            ? workspace()
            : {...workspace(), request: {...workspace().request, id: requestId}, title: "Second records request"});
        renderWorkspace();
        fireEvent.click(await screen.findByRole("button", {name: "Preview as recipient"}));
        expect(await screen.findByRole("dialog", {name: "Preview as recipient"})).toBeTruthy();

        fireEvent.click(screen.getByRole("button", {name: "Open the other request"}));

        expect(await screen.findByRole("heading", {name: "Second records request"})).toBeTruthy();
        expect(screen.queryByRole("dialog", {name: "Preview as recipient"})).toBeNull();
    });

    it("previews what the nominated party is asked when the author answers nothing on the request", async () => {
        vi.mocked(runtime.getInformationRequestManagementWorkspace).mockResolvedValue(authorProjection());
        vi.mocked(authoring.getInformationRequestParties).mockResolvedValue({
            parties: [contributor, decisionMaker],
            partiesETag: "\"parties-2\"",
        });
        vi.mocked(authoring.getInformationRequestRecipientPreview)
            .mockResolvedValue(previewFor(contributor, "How many records were kept?"));
        renderWorkspace();

        fireEvent.click(await screen.findByRole("button", {name: "Preview as recipient"}));
        const preview = await screen.findByRole("dialog", {name: "Preview as recipient"});

        expect(await within(preview).findByText("How many records were kept?")).toBeTruthy();
        expect(within(preview).getByText("Records")).toBeTruthy();
        expect(within(preview).getByText("Required")).toBeTruthy();
        expect(within(preview).getByText("Count every record kept this period.")).toBeTruthy();
        expect(within(preview).getByText("Answers accepted: Provided, Not applicable")).toBeTruthy();
        expect(within(preview).queryByText("record-count")).toBeNull();
        expect(authoring.getInformationRequestRecipientPreview).toHaveBeenCalledWith("request-a", "party-a");
        const choice = within(preview).getByLabelText("Preview for") as HTMLSelectElement;
        expect(Array.from(choice.options).map(option => option.textContent)).toEqual(["Contributor: member@process.test"]);
    });

    it("previews each answering party with that party's own questions", async () => {
        vi.mocked(authoring.getInformationRequestParties).mockResolvedValue({
            parties: [contributor, preparer, decisionMaker],
            partiesETag: "\"parties-2\"",
        });
        vi.mocked(authoring.getInformationRequestRecipientPreview).mockImplementation(async (_, partyId) =>
            partyId === "party-b" ? previewFor(preparer, "Prepare the record summary") : previewFor(contributor, "How many records were kept?"));
        renderWorkspace();

        fireEvent.click(await screen.findByRole("button", {name: "Preview as recipient"}));
        const preview = await screen.findByRole("dialog", {name: "Preview as recipient"});
        expect(await within(preview).findByText("How many records were kept?")).toBeTruthy();
        const choice = within(preview).getByLabelText("Preview for") as HTMLSelectElement;
        fireEvent.change(choice, {target: {value: "party-b"}});

        expect(await within(preview).findByText("Prepare the record summary")).toBeTruthy();
        expect(within(preview).queryByText("How many records were kept?")).toBeNull();
        expect(authoring.getInformationRequestRecipientPreview).toHaveBeenLastCalledWith("request-a", "party-b");
    });

    it("asks for an answering party before it can preview the request", async () =>
    {
        vi.mocked(runtime.getInformationRequestManagementWorkspace).mockResolvedValue(authorProjection());
        vi.mocked(authoring.getInformationRequestParties).mockResolvedValue({
            parties: [decisionMaker],
            partiesETag: "\"parties-2\""
        });
        renderWorkspace();

        fireEvent.click(await screen.findByRole("button", {name: "Preview as recipient"}));
        const preview = await screen.findByRole("dialog", {name: "Preview as recipient"});

        expect(within(preview).getByText("Add a party who answers this request to preview what they will be asked.")).toBeTruthy();
        expect(within(preview).queryByLabelText("Preview for")).toBeNull();
        expect(authoring.getInformationRequestRecipientPreview).not.toHaveBeenCalled();
    });

    it("keeps a refused supersession open with its replacement, reason and an inline error", async () => {
        vi.mocked(authoring.getExchangeInformationRequests).mockResolvedValue({
            requests: [
                managedSummary("request-a", "Periodic records request", InformationRequestState.DRAFT),
                managedSummary("request-b", "Revised records request", InformationRequestState.DRAFT),
            ],
            canCreate: true,
        });
        vi.mocked(authoring.supersedeInformationRequest).mockRejectedValue({errorMessage: "The replacement cannot take over yet"});
        renderWorkspace();
        fireEvent.click(await screen.findByRole("button", {name: "Supersede"}));
        const dialog = await screen.findByRole("dialog", {name: "Supersede this request"});
        expect(await within(dialog).findByRole("option", {name: "Revised records request"})).toBeTruthy();
        fireEvent.change(within(dialog).getByLabelText("Replacement request"), {target: {value: "request-b"}});
        fireEvent.change(within(dialog).getByLabelText("Reason"), {target: {value: "requirements-changed"}});
        fireEvent.click(within(dialog).getByRole("button", {name: "Supersede"}));

        await waitFor(() => expect(authoring.supersedeInformationRequest).toHaveBeenCalledOnce());
        const kept = screen.getByRole("dialog", {name: "Supersede this request"});
        expect((within(kept).getByLabelText("Replacement request") as HTMLSelectElement).value).toBe("request-b");
        expect((within(kept).getByLabelText("Reason") as HTMLSelectElement).value).toBe("requirements-changed");
        expect((await within(kept).findByRole("alert")).textContent).toContain("The replacement cannot take over yet");
    });

    it("keeps a refused reassignment open with the chosen person and an inline error", async () => {
        vi.mocked(authoring.reassignInformationRequestParty).mockRejectedValue({errorMessage: "This person cannot hold the role"});
        renderWorkspace();
        fireEvent.click(await screen.findByRole("button", {name: "Reassign member@process.test"}));
        const dialog = await screen.findByRole("dialog", {name: "Reassign party"});
        fireEvent.change(within(dialog).getByLabelText("Person"), {target: {value: "user-b"}});
        fireEvent.click(within(dialog).getByRole("button", {name: "Reassign"}));

        await waitFor(() => expect(authoring.reassignInformationRequestParty).toHaveBeenCalledOnce());
        const kept = screen.getByRole("dialog", {name: "Reassign party"});
        expect((within(kept).getByLabelText("Person") as HTMLSelectElement).value).toBe("user-b");
        expect((await within(kept).findByRole("alert")).textContent).toContain("This person cannot hold the role");
    });

    it.each(["cancel", "supersede"] as const)("reads the clocks again after the request is %sed", async command => {
        vi.mocked(runtime.getInformationRequestManagementWorkspace)
            .mockResolvedValueOnce(workspace(InformationRequestState.ISSUED))
            .mockResolvedValue(workspace(command === "cancel" ? InformationRequestState.CANCELLED : InformationRequestState.SUPERSEDED));
        vi.mocked(authoring.getExchangeInformationRequests).mockResolvedValue({
            requests: [
                managedSummary("request-a", "Periodic records request", InformationRequestState.ISSUED),
                managedSummary("request-b", "Revised records request", InformationRequestState.DRAFT),
            ],
            canCreate: true,
        });
        vi.mocked(authoring.cancelInformationRequest).mockResolvedValue(workspace(InformationRequestState.CANCELLED).request);
        vi.mocked(authoring.supersedeInformationRequest).mockResolvedValue(workspace(InformationRequestState.SUPERSEDED).request);
        renderWorkspace();
        await waitFor(() => expect(operations.getInformationRequestClocks).toHaveBeenCalled());
        const reads = vi.mocked(operations.getInformationRequestClocks).mock.calls.length;

        if (command === "cancel") {
            fireEvent.click(await screen.findByRole("button", {name: "Cancel request"}));
            const dialog = await screen.findByRole("dialog", {name: "Cancel this request"});
            fireEvent.change(within(dialog).getByLabelText("Reason"), {target: {value: "no-longer-needed"}});
            fireEvent.click(within(dialog).getByRole("button", {name: "Cancel request"}));
        } else {
            fireEvent.click(await screen.findByRole("button", {name: "Supersede"}));
            const dialog = await screen.findByRole("dialog", {name: "Supersede this request"});
            expect(await within(dialog).findByRole("option", {name: "Revised records request"})).toBeTruthy();
            fireEvent.change(within(dialog).getByLabelText("Replacement request"), {target: {value: "request-b"}});
            fireEvent.change(within(dialog).getByLabelText("Reason"), {target: {value: "requirements-changed"}});
            fireEvent.click(within(dialog).getByRole("button", {name: "Supersede"}));
        }

        await waitFor(() => expect(vi.mocked(operations.getInformationRequestClocks).mock.calls.length).toBeGreaterThan(reads));
    });

    it("lets a party manager withdraw a party's submission for recovery from the request's page", async () => {
        vi.mocked(runtime.getInformationRequestManagementWorkspace).mockResolvedValue(workspace(InformationRequestState.IN_PROGRESS));
        vi.mocked(outcomeReads.getInformationRequestSubmissionPackages).mockResolvedValue([{
            ...submittedPackage,
            withdrawable: true
        }]);
        vi.mocked(submissions.withdrawInformationRequestPackage)
            .mockResolvedValue({outcome: "SAVED", responseETag: "\"responses-2\"", data: {} as never});
        renderWorkspace();

        const panel = await screen.findByRole("region", {name: "Submissions"});
        fireEvent.click(within(panel).getByRole("button", {name: "Withdraw submission 1"}));
        const dialog = await screen.findByRole("dialog", {name: "Withdraw submission 1"});
        fireEvent.change(within(dialog).getByLabelText("Reason code"), {target: {value: "recovery"}});
        fireEvent.click(within(dialog).getByRole("button", {name: "Withdraw submission"}));

        await waitFor(() => expect(submissions.withdrawInformationRequestPackage).toHaveBeenCalledWith(
            "request-a",
            "package-a",
            {reasonCode: "recovery"},
            {expectedETag: "\"responses-1\"", idempotencyKey: expect.any(String)},
        ));
        expect(await screen.findByText(/The submission was withdrawn/)).toBeTruthy();
        await waitFor(() => expect(screen.queryByRole("dialog", {name: "Withdraw submission 1"})).toBeNull());
        expect(runtime.getInformationRequestManagementWorkspace).toHaveBeenCalledTimes(2);
    });

    it("offers no submission recovery to a caller who cannot manage the request's parties", async () => {
        vi.mocked(runtime.getInformationRequestManagementWorkspace)
            .mockResolvedValue(withAuthorActions(workspace(InformationRequestState.IN_PROGRESS), {
                ...everyAuthorAction,
                manageParties: false
            }));
        vi.mocked(outcomeReads.getInformationRequestSubmissionPackages).mockResolvedValue([{
            ...submittedPackage,
            withdrawable: true
        }]);
        renderWorkspace();

        expect(await screen.findByRole("heading", {name: "Outcomes"})).toBeTruthy();
        expect(screen.queryByRole("region", {name: "Submissions"})).toBeNull();
    });

    it("states a failed page read with Try again and shows the request once a retry loads", async () => {
        vi.mocked(runtime.getInformationRequestManagementWorkspace)
            .mockRejectedValueOnce({errorMessage: "The service is not answering"})
            .mockResolvedValue(workspace());
        renderWorkspace();

        expect(await screen.findByText("The service is not answering")).toBeTruthy();
        fireEvent.click(screen.getByRole("button", {name: "Try again"}));

        expect(await screen.findByRole("heading", {name: "Periodic records request"})).toBeTruthy();
        expect(screen.queryByText("The service is not answering")).toBeNull();
    });

    it("cancels with the reason code the author chooses", async () => {
        vi.mocked(authoring.cancelInformationRequest).mockResolvedValue(workspace(InformationRequestState.CANCELLED).request);
        renderWorkspace();

        fireEvent.click(await screen.findByRole("button", {name: "Cancel request"}));
        const dialog = await screen.findByRole("dialog", {name: "Cancel this request"});
        fireEvent.change(within(dialog).getByLabelText("Reason"), {target: {value: "no-longer-needed"}});
        fireEvent.click(within(dialog).getByRole("button", {name: "Cancel request"}));

        await waitFor(() => expect(authoring.cancelInformationRequest)
            .toHaveBeenCalledWith("request-a", "no-longer-needed", "\"request-3\"", expect.any(String)));
    });

    it("keeps a refused cancellation open with its reason and an inline error", async () => {
        vi.mocked(authoring.cancelInformationRequest).mockRejectedValue({errorMessage: "The request cannot be cancelled yet"});
        renderWorkspace();
        fireEvent.click(await screen.findByRole("button", {name: "Cancel request"}));
        const dialog = await screen.findByRole("dialog", {name: "Cancel this request"});
        fireEvent.change(within(dialog).getByLabelText("Reason"), {target: {value: "no-longer-needed"}});
        fireEvent.click(within(dialog).getByRole("button", {name: "Cancel request"}));

        await waitFor(() => expect(authoring.cancelInformationRequest).toHaveBeenCalledOnce());
        const kept = screen.getByRole("dialog", {name: "Cancel this request"});
        expect((within(kept).getByLabelText("Reason") as HTMLSelectElement).value).toBe("no-longer-needed");
        expect(within(kept).getByRole("alert").textContent).toContain("The request cannot be cancelled yet");
    });

    it("keeps a refused new party open with its email and inline error", async () => {
        vi.mocked(authoring.assignInformationRequestParty).mockRejectedValue({errorMessage: "Choose this person from trusted recipients"});
        renderWorkspace();
        fireEvent.click(await screen.findByRole("button", {name: "Add party"}));
        const dialog = await screen.findByRole("dialog", {name: "Add party"});
        fireEvent.change(within(dialog).getByLabelText("Email address"), {target: {value: "new-person@process.test"}});
        fireEvent.click(within(dialog).getByRole("button", {name: "Add"}));

        await waitFor(() => expect(authoring.assignInformationRequestParty).toHaveBeenCalledOnce());
        const kept = screen.getByRole("dialog", {name: "Add party"});
        expect((within(kept).getByLabelText("Email address") as HTMLInputElement).value).toBe("new-person@process.test");
        expect((await within(kept).findByRole("alert")).textContent).toContain("Choose this person from trusted recipients");
    });

    it.each([
        ["an active Exchange offers", everyAuthorAction, true],
        ["an ended Exchange keeps fact revocation but withholds", retainedRecordAuthorActions, false],
    ] as const)("a closed request of %s new outcomes, follow-ups and the recipient preview", async (_case, authorActions, offered) => {
        vi.mocked(runtime.getInformationRequestManagementWorkspace)
            .mockResolvedValue(withAuthorActions(workspace(InformationRequestState.CLOSED), authorActions));
        vi.mocked(outcomeReads.getInformationRequestSubmissionPackages).mockResolvedValue([submittedPackage]);
        vi.mocked(outcomeReads.getInformationRequestAcceptedFacts).mockResolvedValue([promotedFact]);
        vi.mocked(outcomeReads.getInformationRequestBusinessDecisions).mockResolvedValue([recordedDecision]);
        vi.mocked(authoring.getInformationRequestParties).mockResolvedValue({
            parties: [contributor, subjectOfRequest],
            partiesETag: "\"parties-2\"",
        });
        renderWorkspace();

        const outcomes = await screen.findByRole("region", {name: "Outcomes"});
        const facts = await within(outcomes).findByRole("list", {name: "Promoted facts"});
        expect(within(outcomes).getByRole("list", {name: "Business decisions"})).toBeTruthy();
        expect(within(facts).getByRole("button", {name: /^Revoke /})).toBeTruthy();
        expect(screen.getByText("This request no longer accepts changes. Its history is kept.")).toBeTruthy();
        await waitFor(() => expect(authoring.getInformationRequestLineage).toHaveBeenCalled());
        expect(within(outcomes).queryByRole("button", {name: "Promote How many records were kept?"}) !== null).toBe(offered);
        expect(within(outcomes).queryByRole("button", {name: "Record decision"}) !== null).toBe(offered);
        expect(within(outcomes).queryByRole("button", {name: "Reconsider intake"}) !== null).toBe(offered);
        expect(screen.queryByRole("button", {name: "Request a supplement"}) !== null).toBe(offered);
        expect(screen.queryByRole("button", {name: "Preview as recipient"}) !== null).toBe(offered);
    });

    it("keeps an open request of a deleted Exchange readable to its owner and offers no change the server refuses", async () => {
        vi.mocked(runtime.getInformationRequestManagementWorkspace)
            .mockResolvedValue(withAuthorActions(workspace(InformationRequestState.IN_PROGRESS), noAuthorAction));
        vi.mocked(authoring.getInformationRequestAccessLinks).mockResolvedValue([{
            shareLinkId: "link-a",
            status: InformationRequestAccessLinkStatus.ACTIVE,
            rotationCount: 0,
            partyId: "party-a",
        }]);
        vi.mocked(operations.getInformationRequestClocks).mockResolvedValue([runningClock, pausedClock]);
        vi.mocked(outcomeReads.getInformationRequestAcceptedFacts).mockResolvedValue([promotedFact]);
        renderWorkspace();

        const outcomes = await screen.findByRole("region", {name: "Outcomes"});
        const facts = await within(outcomes).findByRole("list", {name: "Promoted facts"});
        expect(await screen.findByText("Time left when paused: 1h 0m")).toBeTruthy();
        expect(screen.getByText("member@process.test")).toBeTruthy();
        expect(within(facts).queryByRole("button", {name: /^Revoke /})).toBeNull();
        expect(within(outcomes).queryByRole("button", {name: "Record decision"})).toBeNull();
        expect(screen.queryByRole("group", {name: "Request actions"})).toBeNull();
        expect(screen.queryByRole("switch", {name: "Must finish before the Exchange can end"})).toBeNull();
        expect(screen.queryByRole("button", {name: "Add party"})).toBeNull();
        expect(screen.queryByRole("button", {name: "Make me the Decision Maker"})).toBeNull();
        expect(screen.queryByRole("button", {name: "Reassign member@process.test"})).toBeNull();
        expect(screen.queryByRole("button", {name: "Remove member@process.test"})).toBeNull();
        expect(screen.queryByRole("button", {name: "Resend link to member@process.test"})).toBeNull();
        expect(screen.queryByRole("button", {name: "Revoke link for member@process.test"})).toBeNull();
        expect(screen.queryByRole("button", {name: "Pause response clock"})).toBeNull();
        expect(screen.queryByRole("button", {name: "Resume review clock"})).toBeNull();
        expect(screen.queryByRole("button", {name: "Start clock"})).toBeNull();
        expect(screen.queryByRole("button", {name: "Request a supplement"})).toBeNull();
        expect(screen.queryByRole("button", {name: "Preview as recipient"})).toBeNull();
    });

    it("names every control and region of an issued request's workspace", async () =>
    {
        vi.mocked(runtime.getInformationRequestManagementWorkspace).mockResolvedValue(workspace(InformationRequestState.ISSUED));
        renderWorkspace();
        const outcomes = await screen.findByRole("region", {name: "Outcomes"});
        await within(outcomes).findByRole("region", {name: "Accepted facts"});

        expect(unnamedControls(document.body)).toEqual([]);

        expect(duplicateIds(document.body)).toEqual([]);
    });

    it("names every control and region of a draft's workspace", async () => {
        renderWorkspace();
        const followUps = await screen.findByRole("region", {name: "Follow-up requests"});
        await within(followUps).findByRole("group", {name: "Schedule a recurrence"});

        expect(unnamedControls(document.body)).toEqual([]);

        expect(duplicateIds(document.body)).toEqual([]);
    });

    it("shows the request's accepted facts and business decisions once it is issued, and not on a draft", async () =>
    {
        renderWorkspace();
        expect(await screen.findByRole("button", {name: "Issue"})).toBeTruthy();
        expect(screen.queryByRole("region", {name: "Outcomes"})).toBeNull();
        cleanup();

        vi.mocked(runtime.getInformationRequestManagementWorkspace).mockResolvedValue(workspace(InformationRequestState.ISSUED));
        vi.mocked(authoring.getInformationRequestParties).mockResolvedValue({
            parties: [contributor, {
                ...contributor,
                id: "party-s",
                roleKey: InformationRequestShareRoleKey.SUBJECT,
                subjectIdentityRefId: "subject-a"
            }],
            partiesETag: "\"parties-2\"",
        });
        renderWorkspace();

        const outcomes = await screen.findByRole("region", {name: "Outcomes"});
        expect(await within(outcomes).findByRole("region", {name: "Accepted facts"})).toBeTruthy();
        expect(within(outcomes).getByRole("region", {name: "Business decisions"})).toBeTruthy();
        expect(await within(outcomes).findByRole("region", {name: "Corrections"})).toBeTruthy();
    });
});
