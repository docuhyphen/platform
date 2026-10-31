/** @vitest-environment jsdom */
import {cleanup, fireEvent, render, screen, waitFor, within} from "@testing-library/react";
import {afterEach, beforeAll, beforeEach, describe, expect, it, vi} from "vitest";
import * as transport from "../../../../services/informationRequestSubmissionService.ts";
import {
    FieldValueType,
    InformationRequestAttestationDecision,
    InformationRequestAttestationOrdering,
    InformationRequestAttestationState,
    InformationRequestAttestationStatusDto,
    InformationRequestAuthenticationStrength,
    InformationRequestContributorRole,
    InformationRequestExternalSignatureReferencePolicy,
    InformationRequestRequirementType,
    InformationRequestResponseDisposition,
    InformationRequestState,
    InformationRequestSubmissionMode,
    InformationRequestSubmissionPreviewDto,
    InformationRequestSubmissionProblemCode,
    InformationRequestSubmissionStageOrdering,
} from "../../../models/models.tsx";
import InformationRequestSubmissionPanel from "./InformationRequestSubmissionPanel.tsx";

vi.mock("../../../../services/informationRequestSubmissionService.ts", () => ({
    getInformationRequestSubmissionPreview: vi.fn(),
    submitInformationRequestPackage: vi.fn(),
    recordInformationRequestAttestation: vi.fn(),
    withdrawInformationRequestPackage: vi.fn(),
}));

const assertion = (overrides: Partial<InformationRequestAttestationStatusDto> = {}): InformationRequestAttestationStatusDto => ({
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
    ...overrides,
});

const preview = (overrides: Partial<InformationRequestSubmissionPreviewDto> = {}): InformationRequestSubmissionPreviewDto => ({
    informationRequestId: "request-a",
    submissionMode: InformationRequestSubmissionMode.WHOLE_PACKAGE,
    submissionStageOrdering: InformationRequestSubmissionStageOrdering.ANY_ORDER,
    submissionETag: "\"submission:whole:abc\"",
    scopeItems: [
        {
            requirementId: "count-a",
            templateBindingId: "count-a",
            requirementKey: "count",
            requirementType: InformationRequestRequirementType.FIELD,
            prompt: "How many records were kept?",
            occurrencePath: "root",
            disposition: InformationRequestResponseDisposition.PROVIDED,
            fieldValueType: FieldValueType.SHORT_TEXT,
            fieldValue: "12",
            fieldValueCleared: false,
            evidenceFileCount: 0
        },
        {
            requirementId: "files-a",
            templateBindingId: "files-a",
            requirementKey: "files",
            requirementType: InformationRequestRequirementType.DOCUMENT,
            prompt: "Attach the record register",
            occurrencePath: "root",
            disposition: InformationRequestResponseDisposition.NOT_APPLICABLE,
            narrative: "No register this period",
            fieldValueCleared: false,
            evidenceFileCount: 0
        },
    ],
    ready: false,
    problems: [{
        requirementId: "assertion-a",
        requirementKey: "recorded-assertion",
        occurrencePath: "root",
        code: InformationRequestSubmissionProblemCode.ATTESTATION_MISSING,
    }],
    undisclosedProblemCount: 2,
    groupProblems: [],
    attestations: [assertion()],
    stages: [],
    canSubmit: false,
    correctionOpen: false,
    packages: [],
    ...overrides,
});

const renderPanel = (onChanged = vi.fn()) =>
    render(<InformationRequestSubmissionPanel requestId={"request-a"}
                                              requestState={InformationRequestState.ISSUED}
                                              responseETag={"\"responses:1\""}
                                              requirementLabels={{"assertion-a": "Confirm the recorded items are accurate"}}
                                              onChanged={onChanged}/>);

describe("InformationRequestSubmissionPanel", () =>
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

    beforeEach(() => vi.resetAllMocks());
    afterEach(cleanup);

    it.each([InformationRequestState.CANCELLED, InformationRequestState.SUPERSEDED, InformationRequestState.EXPIRED])("keeps a %s request read only", async state => {
        vi.mocked(transport.getInformationRequestSubmissionPreview).mockResolvedValue(preview());
        render(<InformationRequestSubmissionPanel requestId={"request-a"}
                                                  requestState={state}
                                                  responseETag={"responses-1"}
                                                  onChanged={vi.fn()}/>);
        await screen.findByText("Review and submit");
        expect(screen.getByRole("region", {name: "Review and submit"})).toBeTruthy();
        expect(screen.queryByText("Before you submit")).toBeNull();
        expect(screen.queryByRole("button", {name: /^Confirm(?:\s|$)/})).toBeNull();
        expect(screen.queryByRole("button", {name: "Submit"})).toBeNull();
    });

    it("holds confirmation while answers are unsaved", async () => {
        vi.mocked(transport.getInformationRequestSubmissionPreview).mockResolvedValue(preview());
        render(<InformationRequestSubmissionPanel requestId={"request-a"}
                                                  requestState={InformationRequestState.ISSUED}
                                                  responseETag={"responses-1"}
                                                  pendingWork={{
                                                      blocker: null,
                                                      unsaved: true,
                                                      evidenceRevision: 0,
                                                      flush: async () => true
                                                  }}
                                                  onChanged={vi.fn()}/>);
        expect(await screen.findByRole("button", {name: /^Confirm(?:\s|$)/})).toHaveProperty("disabled", true);
    });

    it("caps signature references and explains the confirmation policy", async () => {
        vi.mocked(transport.getInformationRequestSubmissionPreview).mockResolvedValue(preview({
            attestations: [assertion({
                minimumAuthenticationStrength: InformationRequestAuthenticationStrength.MULTI_FACTOR,
                externalSignatureReference: InformationRequestExternalSignatureReferencePolicy.OPTIONAL,
                validityHours: 24,
            })]
        }));
        renderPanel();
        const input = await screen.findByLabelText("Signature reference");
        expect(input.getAttribute("maxlength")).toBe("512");
        expect(screen.getByText(/Requires multi-factor verification/)).toBeTruthy();
        expect(screen.getByText(/Valid for 24 hours/)).toBeTruthy();
    });

    it("explains and withholds a confirmation an access-link session cannot give", async () => {
        vi.mocked(transport.getInformationRequestSubmissionPreview).mockResolvedValue(preview({attestations: [assertion({minimumAuthenticationStrength: InformationRequestAuthenticationStrength.MULTI_FACTOR})]}));
        render(<InformationRequestSubmissionPanel requestId={"request-a"}
                                                  requestState={InformationRequestState.ISSUED}
                                                  responseETag={"responses-1"}
                                                  accessLinkToken={"bootstrap"}
                                                  onChanged={vi.fn()}/>);
        expect(await screen.findByRole("button", {name: /^Confirm(?:\s|$)/})).toHaveProperty("disabled", true);
        expect(screen.getByText(/Sign in to give this confirmation/)).toBeTruthy();
    });
    it("focuses the actual confirmation card for a missing-confirmation blocker", async () => {
        vi.mocked(transport.getInformationRequestSubmissionPreview).mockResolvedValue(preview());
        renderPanel();
        await screen.findByText("Waiting for a confirmation");
        const target = document.getElementById("information-request-attestation-assertion-a");
        if (!target) throw new Error("Missing confirmation card");
        target.scrollIntoView = vi.fn();
        fireEvent.click(screen.getByRole("button", {name: "Go to Confirm the recorded items are accurate"}));
        expect(target.scrollIntoView).toHaveBeenCalled();
        expect(document.activeElement).toBe(target);
    });
    it("announces a refused first read, offers Try again, and drops the message once the summary loads", async () => {
        vi.mocked(transport.getInformationRequestSubmissionPreview)
            .mockRejectedValueOnce({
                reasonCode: "INFORMATION_REQUEST_FORBIDDEN",
                errorMessage: "This summary is not available."
            })
            .mockResolvedValueOnce(preview());

        renderPanel();

        expect(await screen.findByText("This summary is not available.")).toBeTruthy();
        expect(screen.queryByRole("button", {name: "Submit"})).toBeNull();
        fireEvent.click(screen.getByRole("button", {name: "Try again"}));
        expect(await screen.findByText("Waiting for a confirmation")).toBeTruthy();
        expect(screen.queryByText("This summary is not available.")).toBeNull();
    });

    it("shows the chosen part at once and keeps Submit disabled until that part has loaded", async () => {
        const staged = (stageKey: string) => preview({
            submissionMode: InformationRequestSubmissionMode.STAGED,
            stageKey,
            submissionETag: `"submission:${stageKey}"`,
            ready: true,
            problems: [],
            undisclosedProblemCount: 0,
            groupProblems: [],
            attestations: [],
            canSubmit: true,
            stages: [
                {stageKey: "record-stage", submitted: false, correctionOpen: false},
                {stageKey: "closing-stage", submitted: false, correctionOpen: false},
            ],
        });
        let answerClosing!: (value: InformationRequestSubmissionPreviewDto) => void;
        vi.mocked(transport.getInformationRequestSubmissionPreview)
            .mockResolvedValueOnce(staged("record-stage"))
            .mockReturnValueOnce(new Promise(resolve => {
                answerClosing = resolve;
            }));

        renderPanel();
        await waitFor(() => expect((screen.getByRole("button", {name: "Submit"}) as HTMLButtonElement).disabled).toBe(false));
        fireEvent.click(screen.getByRole("combobox"));
        fireEvent.click(await screen.findByText("closing stage"));

        await waitFor(() => expect((screen.getByRole("combobox") as HTMLButtonElement).textContent).toContain("closing stage"));
        expect((screen.getByRole("button", {name: "Submit"}) as HTMLButtonElement).disabled).toBe(true);
        answerClosing(staged("closing-stage"));
        await waitFor(() => expect((screen.getByRole("button", {name: "Submit"}) as HTMLButtonElement).disabled).toBe(false));
    });

    it("names what blocks submission, counts what the caller cannot see, and keeps Submit disabled", async () =>
    {
        vi.mocked(transport.getInformationRequestSubmissionPreview).mockResolvedValue(preview());

        renderPanel();

        expect(await screen.findByText("Waiting for a confirmation")).toBeTruthy();
        expect(screen.getByText("2 additional items or submission rules are not complete yet.")).toBeTruthy();
        expect(screen.queryByText("recorded assertion")).toBeNull();
        const target = document.getElementById("information-request-attestation-assertion-a");
        if (!target) throw new Error("Missing confirmation card");
        target.scrollIntoView = vi.fn();
        fireEvent.click(screen.getByRole("button", {name: "Go to Confirm the recorded items are accurate"}));
        expect(target.scrollIntoView).toHaveBeenCalled();
        expect(document.activeElement).toBe(target);
        expect((screen.getByRole("button", {name: "Submit"}) as HTMLButtonElement).disabled).toBe(true);
    });

    it("confirms an assertion against the reviewed submission ETag", async () =>
    {
        vi.mocked(transport.getInformationRequestSubmissionPreview).mockResolvedValue(preview());
        vi.mocked(transport.recordInformationRequestAttestation).mockResolvedValue({
            outcome: "SAVED", responseETag: "", data: {
                attestation: {} as never,
                submissionETag: "\"submission:whole:abc\"",

            }
        });
        const onChanged = vi.fn();

        renderPanel(onChanged);
        fireEvent.click(await screen.findByRole("button", {name: /^Confirm(?:\s|$)/}));

        await waitFor(() => expect(transport.recordInformationRequestAttestation).toHaveBeenCalledWith(
            "request-a",
            "assertion-a",
            {decision: InformationRequestAttestationDecision.ASSENTED, externalSignatureReference: undefined},
            expect.objectContaining({expectedETag: "\"submission:whole:abc\""}),
        ));
        expect(onChanged).toHaveBeenCalled();
    });

    it("asks the page to check access again when a command finds the access session ended, and only then", async () => {
        vi.mocked(transport.getInformationRequestSubmissionPreview).mockResolvedValue(preview());
        vi.mocked(transport.recordInformationRequestAttestation)
            .mockRejectedValueOnce({reasonCode: "INFORMATION_REQUEST_FORBIDDEN", errorMessage: "Access denied"})
            .mockRejectedValueOnce({
                reasonCode: "INFORMATION_REQUEST_ACCESS_SESSION_EXPIRED",
                errorMessage: "Your verified session has ended.",
            });
        const onChanged = vi.fn();

        renderPanel(onChanged);
        fireEvent.click(await screen.findByRole("button", {name: /^Confirm(?:\s|$)/}));
        expect(await screen.findByText("Access denied")).toBeTruthy();
        expect(onChanged).not.toHaveBeenCalled();

        fireEvent.click(await screen.findByRole("button", {name: /^Confirm(?:\s|$)/}));

        expect(await screen.findByText("Your verified session has ended.")).toBeTruthy();
        expect(onChanged).toHaveBeenCalledTimes(1);
    });

    it("offers a part returned for correction to be submitted again", async () => {
        vi.mocked(transport.getInformationRequestSubmissionPreview).mockResolvedValue(preview({
            submissionMode: InformationRequestSubmissionMode.STAGED,
            stageKey: "record-stage",
            ready: true,
            problems: [],
            undisclosedProblemCount: 0,
            groupProblems: [],
            canSubmit: true,
            correctionOpen: true,
            stages: [
                {
                    stageKey: "record-stage",
                    submittedPackageId: "package-a",
                    submittedPackageNumber: 1,
                    submitted: true,
                    correctionOpen: true
                },
                {stageKey: "closing-stage", submitted: false, correctionOpen: false},
            ],
        }));

        renderPanel();

        expect(await screen.findByText("This part was returned for correction. Correct the items listed under Review results, then submit it again."))
            .toBeTruthy();
        expect((screen.getByRole("button", {name: "Submit again"}) as HTMLButtonElement).disabled).toBe(false);
        fireEvent.click(screen.getByRole("combobox"));
        expect(await screen.findByText("record stage (returned for correction)")).toBeTruthy();
        expect(screen.getByText("closing stage")).toBeTruthy();
    });

    it("submits a ready scope and reports a completed request, or explains a stale review", async () =>
    {
        vi.mocked(transport.getInformationRequestSubmissionPreview).mockResolvedValue(
            preview({ready: true, problems: [], undisclosedProblemCount: 0, attestations: [], canSubmit: true}),
        );
        vi.mocked(transport.submitInformationRequestPackage)
            .mockResolvedValueOnce({
                outcome: "SAVED", responseETag: "\"responses:2\"", data: {
                    requestState: InformationRequestState.CLOSED,
                    requestETag: "\"request:3\"",
                    responseETag: "\"responses:2\"",
                    submission: {packageNumber: 1} as never,
                }
            })
            .mockResolvedValueOnce({outcome: "STALE"});

        renderPanel();
        fireEvent.click(await screen.findByRole("button", {name: "Submit"}));
        const review = await screen.findByRole("dialog", {name: "Review before submitting"});
        expect(within(review).getByText("How many records were kept?")).toBeTruthy();
        expect(within(review).getByText("Not applicable: No register this period")).toBeTruthy();
        expect(transport.submitInformationRequestPackage).not.toHaveBeenCalled();
        fireEvent.click(within(review).getByRole("button", {name: "Submit"}));
        expect(await screen.findByText(/Submission 1 was recorded\. This request is now complete\./)).toBeTruthy();

        fireEvent.click(screen.getByRole("button", {name: "Submit"}));
        fireEvent.click(within(await screen.findByRole("dialog", {name: "Review before submitting"})).getByRole("button", {name: "Submit"}));
        expect(await screen.findByText(/The information changed after you reviewed it/)).toBeTruthy();
    });
    it("explains when a reviewed part is already submitted", async () => {
        vi.mocked(transport.getInformationRequestSubmissionPreview).mockResolvedValue(preview({
            submissionMode: InformationRequestSubmissionMode.STAGED, stageKey: "records", ready: true,
            stages: [{stageKey: "records", submitted: true, correctionOpen: false}],
        }));
        renderPanel();
        expect(await screen.findByText("This part is already submitted. It must be returned or withdrawn before you can change it.")).toBeTruthy();
    });
    it("explains when an earlier part must be submitted first", async () => {
        vi.mocked(transport.getInformationRequestSubmissionPreview).mockResolvedValue(preview({
            submissionMode: InformationRequestSubmissionMode.STAGED, stageKey: "details", ready: true,
            submissionStageOrdering: InformationRequestSubmissionStageOrdering.SEQUENTIAL,
            stages: [{stageKey: "records", submitted: false, correctionOpen: false}, {
                stageKey: "details",
                submitted: false,
                correctionOpen: false
            }],
        }));
        renderPanel();
        expect(await screen.findByText("Submit the earlier parts before submitting this part.")).toBeTruthy();
    });
});
