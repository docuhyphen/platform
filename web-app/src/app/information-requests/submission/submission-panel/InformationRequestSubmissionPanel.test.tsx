/** @vitest-environment jsdom */
import {cleanup, fireEvent, render, screen, waitFor, within} from "@testing-library/react";
import {afterEach, beforeAll, beforeEach, describe, expect, it, vi} from "vitest";
import * as transport from "../../../../services/informationRequestSubmissionService.ts";
import {
    InformationRequestAttestationDecision,
    InformationRequestAttestationOrdering,
    InformationRequestAttestationState,
    InformationRequestAttestationStatusDto,
    InformationRequestAuthenticationStrength,
    InformationRequestContributorRole,
    InformationRequestExternalSignatureReferencePolicy,
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
    attestations: [],
    ...overrides,
});

const preview = (overrides: Partial<InformationRequestSubmissionPreviewDto> = {}): InformationRequestSubmissionPreviewDto => ({
    informationRequestId: "request-a",
    submissionMode: InformationRequestSubmissionMode.WHOLE_PACKAGE,
    submissionStageOrdering: InformationRequestSubmissionStageOrdering.ANY_ORDER,
    submissionETag: "\"submission:whole:abc\"",
    ready: false,
    problems: [{
        requirementId: "assertion-a",
        requirementKey: "recorded-assertion",
        occurrencePath: "root",
        code: InformationRequestSubmissionProblemCode.ATTESTATION_MISSING,
    }],
    undisclosedProblemCount: 2,
    attestations: [assertion()],
    stages: [],
    canSubmit: false,
    packages: [],
    ...overrides,
});

const renderPanel = (onChanged = vi.fn()) =>
    render(<InformationRequestSubmissionPanel requestId={"request-a"}
                                              requestState={InformationRequestState.ISSUED}
                                              responseETag={"\"responses:1\""}
                                              requirementLabels={{"assertion-a": "Confirm the recorded items are accurate"}}
                                              reviewItems={[
                                                  {requirementId: "count-a", label: "How many records were kept?", answer: "12"},
                                                  {requirementId: "files-a", label: "Attach the record register", answer: "Not applicable: No register this period"},
                                              ]}
                                              onChanged={onChanged}/>);

describe("InformationRequestSubmissionPanel", () =>
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

    beforeEach(() => vi.clearAllMocks());
    afterEach(cleanup);

    it("names what blocks submission, counts what the caller cannot see, and keeps Submit disabled", async () =>
    {
        vi.mocked(transport.getInformationRequestSubmissionPreview).mockResolvedValue(preview());

        renderPanel();

        expect(await screen.findByText("Waiting for a confirmation")).toBeTruthy();
        expect(screen.getByText("2 more items handled by other parties are not complete yet.")).toBeTruthy();
        expect(screen.queryByText("recorded assertion")).toBeNull();
        const target = document.createElement("div");
        target.id = "information-request-response-requirement-root-recorded-assertion";
        target.tabIndex = -1;
        target.scrollIntoView = vi.fn();
        document.body.appendChild(target);
        fireEvent.click(screen.getByRole("button", {name: "Go to Confirm the recorded items are accurate"}));
        expect(target.scrollIntoView).toHaveBeenCalled();
        expect(document.activeElement).toBe(target);
        target.remove();
        expect((screen.getByRole("button", {name: "Submit"}) as HTMLButtonElement).disabled).toBe(true);
    });

    it("confirms an assertion against the reviewed submission ETag", async () =>
    {
        vi.mocked(transport.getInformationRequestSubmissionPreview).mockResolvedValue(preview());
        vi.mocked(transport.recordInformationRequestAttestation).mockResolvedValue({outcome: "SAVED", responseETag: "", data: {
            attestation: {} as never,
            submissionETag: "\"submission:whole:abc\"",
        }});
        const onChanged = vi.fn();

        renderPanel(onChanged);
        fireEvent.click(await screen.findByRole("button", {name: "Confirm"}));

        await waitFor(() => expect(transport.recordInformationRequestAttestation).toHaveBeenCalledWith(
            "request-a",
            "assertion-a",
            {decision: InformationRequestAttestationDecision.ASSENTED, externalSignatureReference: undefined},
            expect.objectContaining({expectedETag: "\"submission:whole:abc\""}),
        ));
        expect(onChanged).toHaveBeenCalled();
    });

    it("submits a ready scope and reports a completed request, or explains a stale review", async () =>
    {
        vi.mocked(transport.getInformationRequestSubmissionPreview).mockResolvedValue(
            preview({ready: true, problems: [], undisclosedProblemCount: 0, attestations: [], canSubmit: true}),
        );
        vi.mocked(transport.submitInformationRequestPackage)
            .mockResolvedValueOnce({outcome: "SAVED", responseETag: "\"responses:2\"", data: {
                requestState: InformationRequestState.CLOSED,
                requestETag: "\"request:3\"",
                responseETag: "\"responses:2\"",
                submission: {packageNumber: 1} as never,
            }})
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
});
