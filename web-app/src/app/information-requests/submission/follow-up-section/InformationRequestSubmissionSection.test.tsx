/** @vitest-environment jsdom */
import {cleanup, fireEvent, render, screen, waitFor} from "@testing-library/react";
import {afterEach, beforeAll, beforeEach, describe, expect, it, vi} from "vitest";
import * as transport from "../../../../services/informationRequestSubmissionService.ts";
import {
    InformationRequestAttestationOrdering,
    InformationRequestAttestationState,
    InformationRequestAttestationStatusDto,
    InformationRequestAuthenticationStrength,
    InformationRequestContributorRole,
    InformationRequestExecutionStandingDto,
    InformationRequestExecutionStandingKind,
    InformationRequestExternalSignatureReferencePolicy,
    InformationRequestOwnerType,
    InformationRequestRequiredness,
    InformationRequestRequirementType,
    InformationRequestResponseDisposition,
    InformationRequestResponseDto,
    InformationRequestResponseMode,
    InformationRequestResponseWorkspaceDto,
    InformationRequestReviewPolicy,
    InformationRequestState,
    InformationRequestSubmissionMode,
    InformationRequestSubmissionPreviewDto,
    InformationRequestSubmissionStageOrdering,
    InformationRequestTemplateRequirementDto,
    InformationRequestTemplateStatus,
} from "../../../models/models.tsx";
import InformationRequestSubmissionSection from "./InformationRequestSubmissionSection.tsx";

vi.mock("../../../../services/informationRequestSubmissionService.ts", () => ({
    getInformationRequestSubmissionPreview: vi.fn(),
    getInformationRequestAmendments: vi.fn(),
    getInformationRequestCarryForwards: vi.fn(),
    createInformationRequestSupplement: vi.fn(),
}));
vi.mock("../../../../services/informationRequestReviewService.ts", () => ({
    getInformationRequestReviewResults: vi.fn().mockResolvedValue([]),
    appealInformationRequestReview: vi.fn(),
}));

const workspace = (
    state: InformationRequestState,
    standing: InformationRequestExecutionStandingDto = active,
    canRequestSupplement = true,
): InformationRequestResponseWorkspaceDto => ({
    request: {
        id: "request-a",
        exchangeId: "exchange-a",
        templateVersionId: "version-a",
        ownerType: InformationRequestOwnerType.ORGANIZATION,
        state,
        gatesExchangeClosure: true,
        aggregateRevision: 4,
        createdAt: Date.parse("2026-09-25T08:00:00Z"),
        updatedAt: Date.parse("2026-09-25T08:00:00Z"),
        requestETag: "\"request-a:4\"",
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
        createdAt: Date.parse("2026-09-25T08:00:00Z"),
    },
    responseETag: "\"responses:1\"",
    occurrences: [],
    responses: [],
    supportingEvidenceLinks: [],
    evidenceUploadAvailable: true,
    evidenceMalwareScanning: false,
    requirementsAwaitingStepUp: 0,
    executionStanding: standing,
    canRequestSupplement,
});

const active: InformationRequestExecutionStandingDto = {kind: InformationRequestExecutionStandingKind.ACTIVE};

const created = {outcome: "SAVED" as const, responseETag: "", data: {} as never};

const askForSupplement = (reason: string) => {
    fireEvent.change(screen.getByLabelText("Reason"), {target: {value: reason}});
    fireEvent.click(screen.getByRole("button", {name: "Create supplement"}));
};

const supplementKeys = (): string[] =>
    vi.mocked(transport.createInformationRequestSupplement).mock.calls.map(([, , precondition]) => precondition.idempotencyKey);

describe("InformationRequestSubmissionSection", () =>
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
        vi.mocked(transport.createInformationRequestSupplement).mockReset();
        vi.mocked(transport.getInformationRequestSubmissionPreview).mockRejectedValue("not loaded");
        vi.mocked(transport.getInformationRequestAmendments).mockResolvedValue([]);
        vi.mocked(transport.getInformationRequestCarryForwards).mockResolvedValue([]);
    });
    afterEach(cleanup);

    it("lets a signed-in caller request a supplement of an active request under the request ETag", async () =>
    {
        vi.mocked(transport.createInformationRequestSupplement).mockResolvedValue({
            outcome: "SAVED",
            responseETag: "",
            data: {} as never
        });

        render(<InformationRequestSubmissionSection workspace={workspace(InformationRequestState.CLOSED)}
                                                    requirements={[]}
                                                    onChanged={vi.fn()}/>);
        fireEvent.click(screen.getByRole("button", {name: "Request more information"}));
        expect(screen.queryByRole("textbox")).toBeNull();
        expect((screen.getByRole("button", {name: "Create supplement"}) as HTMLButtonElement).disabled).toBe(true);
        fireEvent.change(screen.getByLabelText("Reason"), {target: {value: "document-missing"}});
        fireEvent.click(screen.getByRole("button", {name: "Create supplement"}));

        await waitFor(() => expect(transport.createInformationRequestSupplement).toHaveBeenCalledWith(
            "request-a",
            "document-missing",
            expect.objectContaining({expectedETag: "\"request-a:4\""}),
        ));
        expect(await screen.findByText(/A supplemental request was created as a draft/)).toBeTruthy();
    });

    it("offers no supplement to a signed-in caller the server does not allow to ask for one", () => {
        render(<InformationRequestSubmissionSection workspace={workspace(InformationRequestState.CLOSED, active, false)}
                                                    requirements={[]}
                                                    onChanged={vi.fn()}/>);

        expect(screen.queryByRole("button", {name: "Request more information"})).toBeNull();
    });

    it("offers no supplement on an access link, before issuance, or once the owner can start no new work", () =>
    {
        render(<InformationRequestSubmissionSection workspace={workspace(InformationRequestState.CLOSED)}
                                                    requirements={[]}
                                                    accessLinkToken={"bootstrap"}
                                                    onChanged={vi.fn()}/>);
        expect(screen.queryByRole("button", {name: "Request more information"})).toBeNull();
        cleanup();

        render(<InformationRequestSubmissionSection workspace={workspace(InformationRequestState.DRAFT)}
                                                    requirements={[]}
                                                    onChanged={vi.fn()}/>);
        expect(screen.queryByRole("button", {name: "Request more information"})).toBeNull();
        cleanup();

        render(<InformationRequestSubmissionSection workspace={workspace(
            InformationRequestState.CLOSED,
            {kind: InformationRequestExecutionStandingKind.CONTINUING_AFTER_LAPSE},
        )}
                                                    requirements={[]}
                                                    onChanged={vi.fn()}/>);
        expect(screen.queryByRole("button", {name: "Request more information"})).toBeNull();
    });

    it("asks again for a supplement whose answer was lost under the same Idempotency-Key", async () => {
        vi.mocked(transport.createInformationRequestSupplement)
            .mockRejectedValueOnce({errorMessage: "The connection was lost"})
            .mockResolvedValueOnce(created);
        render(<InformationRequestSubmissionSection workspace={workspace(InformationRequestState.CLOSED)}
                                                    requirements={[]}
                                                    onChanged={vi.fn()}/>);
        fireEvent.click(screen.getByRole("button", {name: "Request more information"}));
        askForSupplement("more-information-needed");
        expect(await screen.findByText("The connection was lost")).toBeTruthy();

        await waitFor(() => expect(screen.getByRole("button", {name: "Create supplement"}).hasAttribute("disabled")).toBe(false));
        fireEvent.click(screen.getByRole("button", {name: "Create supplement"}));

        expect(await screen.findByText(/A supplemental request was created as a draft/)).toBeTruthy();
        const [first, retried] = vi.mocked(transport.createInformationRequestSupplement).mock.calls;
        expect(retried.slice(0, 2)).toEqual(["request-a", "more-information-needed"]);
        expect(retried[2]).toEqual(first[2]);
        expect(retried[2].idempotencyKey).toMatch(/\S/);
    });

    it("keys a supplement afresh once its reason changes after an unanswered attempt", async () => {
        vi.mocked(transport.createInformationRequestSupplement)
            .mockRejectedValueOnce({errorMessage: "The connection was lost"})
            .mockResolvedValueOnce(created);
        render(<InformationRequestSubmissionSection workspace={workspace(InformationRequestState.CLOSED)}
                                                    requirements={[]}
                                                    onChanged={vi.fn()}/>);
        fireEvent.click(screen.getByRole("button", {name: "Request more information"}));
        askForSupplement("more-information-needed");
        expect(await screen.findByText("The connection was lost")).toBeTruthy();

        await waitFor(() => expect(screen.getByRole("button", {name: "Create supplement"}).hasAttribute("disabled")).toBe(false));
        askForSupplement("answer-unclear");

        await waitFor(() => expect(transport.createInformationRequestSupplement).toHaveBeenCalledTimes(2));
        const [first, changed] = supplementKeys();
        expect(changed).not.toBe(first);
    });

    it("keys the next supplement afresh once the earlier one was created", async () => {
        vi.mocked(transport.createInformationRequestSupplement).mockResolvedValue(created);
        render(<InformationRequestSubmissionSection workspace={workspace(InformationRequestState.CLOSED)}
                                                    requirements={[]}
                                                    onChanged={vi.fn()}/>);
        fireEvent.click(screen.getByRole("button", {name: "Request more information"}));
        askForSupplement("more-information-needed");
        expect(await screen.findByText(/A supplemental request was created as a draft/)).toBeTruthy();

        fireEvent.click(screen.getByRole("button", {name: "Request more information"}));
        askForSupplement("more-information-needed");

        await waitFor(() => expect(transport.createInformationRequestSupplement).toHaveBeenCalledTimes(2));
        const [first, next] = supplementKeys();
        expect(next).not.toBe(first);
    });
    it("keeps the supplement reason after a stale reply and refreshes the request", async () => {
        vi.mocked(transport.createInformationRequestSupplement).mockResolvedValue({outcome: "STALE"});
        const changed = vi.fn();
        render(<InformationRequestSubmissionSection workspace={workspace(InformationRequestState.CLOSED)}
                                                    requirements={[]}
                                                    onChanged={changed}/>);
        fireEvent.click(screen.getByRole("button", {name: "Request more information"}));
        askForSupplement("answer-unclear");
        await waitFor(() => expect(changed).toHaveBeenCalled());
        expect((screen.getByLabelText("Reason") as HTMLSelectElement).value).toBe("answer-unclear");
        expect(screen.getByRole("dialog").textContent).toContain("This request changed");
    });
    it("links to the created supplemental draft", async () => {
        vi.mocked(transport.createInformationRequestSupplement).mockResolvedValue({
            ...created,
            data: {successor: {id: "supplement-a"}} as never
        });
        render(<InformationRequestSubmissionSection workspace={workspace(InformationRequestState.CLOSED)}
                                                    requirements={[]}
                                                    onChanged={vi.fn()}/>);
        fireEvent.click(screen.getByRole("button", {name: "Request more information"}));
        askForSupplement("answer-unclear");
        expect((await screen.findByRole("link", {name: "Open supplemental draft"})).getAttribute("href"))
            .toBe("/information-requests/supplement-a/manage");
    });
});

const confirmation: InformationRequestTemplateRequirementDto = {
    id: "binding-confirm",
    templateRequirementId: "requirement-confirm",
    requirementKey: "recorded-assertion",
    requirementType: InformationRequestRequirementType.RESPONSE_ATTESTATION,
    prompt: "Confirm the recorded items are accurate",
    responseMode: InformationRequestResponseMode.PROVIDE,
    requiredness: InformationRequestRequiredness.REQUIRED,
    contributorRole: InformationRequestContributorRole.ATTESTOR,
    reviewPolicy: InformationRequestReviewPolicy.NOT_REQUIRED,
    permittedDispositions: [InformationRequestResponseDisposition.PROVIDED, InformationRequestResponseDisposition.NOT_APPLICABLE],
    substituteRequirementKeys: [],
    supportingEvidenceRequirementKeys: [],
};

const confirmationResponse: InformationRequestResponseDto = {
    informationRequestRequirementId: "assertion-a",
    sourceTemplateRequirementId: "requirement-confirm",
    sourceTemplateBindingId: "binding-confirm",
    occurrencePath: "root",
    disposition: InformationRequestResponseDisposition.NOT_ANSWERED,
    fieldValues: [],
    responseRevision: 1,
    updatedAt: 0,
};

const confirmationStatus: InformationRequestAttestationStatusDto = {
    requirementId: "assertion-a",
    requirementKey: "recorded-assertion",
    prompt: "Confirm the recorded items are accurate",
    state: InformationRequestAttestationState.PENDING,
    requiredRoles: [InformationRequestContributorRole.ATTESTOR],
    missingRoles: [InformationRequestContributorRole.ATTESTOR],
    ordering: InformationRequestAttestationOrdering.ANY_ORDER,
    assentCount: 0,
    requiredAssentCount: 1,
    minimumAuthenticationStrength: InformationRequestAuthenticationStrength.VERIFIED_CONTACT,
    externalSignatureReference: InformationRequestExternalSignatureReferencePolicy.NOT_ACCEPTED,
    callerCanAttest: true,
    waitingForRoles: [],
    attestations: [],
};

const previewWithConfirmation: InformationRequestSubmissionPreviewDto = {
    informationRequestId: "request-a",
    submissionMode: InformationRequestSubmissionMode.WHOLE_PACKAGE,
    submissionStageOrdering: InformationRequestSubmissionStageOrdering.ANY_ORDER,
    submissionETag: "\"submission:whole:abc\"",
    scopeItems: [],
    ready: false,
    problems: [],
    undisclosedProblemCount: 0,
    groupProblems: [],
    attestations: [confirmationStatus],
    stages: [],
    canSubmit: false,
    correctionOpen: false,
    packages: [],
};

describe("InformationRequestSubmissionSection confirmation exceptions", () => {
    beforeEach(() => {
        vi.mocked(transport.getInformationRequestSubmissionPreview).mockResolvedValue(previewWithConfirmation);
        vi.mocked(transport.getInformationRequestAmendments).mockResolvedValue([]);
        vi.mocked(transport.getInformationRequestCarryForwards).mockResolvedValue([]);
    });
    afterEach(cleanup);

    it("offers the exceptions the Template permits on a confirmation the caller may give", async () => {
        render(<InformationRequestSubmissionSection
            workspace={{...workspace(InformationRequestState.ISSUED), responses: [confirmationResponse]}}
            requirements={[confirmation]}
            onChanged={vi.fn()}/>);

        expect(await screen.findByRole("button", {name: "Record an exception for Confirm the recorded items are accurate"})).toBeTruthy();
    });
});
