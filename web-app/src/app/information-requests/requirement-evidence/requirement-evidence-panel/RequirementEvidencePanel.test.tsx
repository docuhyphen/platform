/** @vitest-environment jsdom */
import {act, cleanup, fireEvent, render, screen, waitFor} from "@testing-library/react";
import {afterEach, beforeAll, beforeEach, describe, expect, it, vi} from "vitest";
import {
    InformationRequestEvidenceArtifactDto,
    InformationRequestEvidenceAttribute,
    InformationRequestEvidenceAttributeRequirement,
    InformationRequestEvidenceCollectionState,
    InformationRequestEvidenceConformance,
    InformationRequestEvidenceConformancePolicy,
    InformationRequestEvidenceEvaluationDto,
    InformationRequestEvidenceFindingCode,
    InformationRequestEvidenceListDto,
    InformationRequestEvidenceRequirementState,
    InformationRequestEvidenceSourceKind,
    InformationRequestEvidenceVersionDto,
    InformationRequestEvidenceWaiverPolicy,
    InformationRequestTemplateEvidencePolicyDto,
} from "../../../models/models.tsx";
import {duplicateIds} from "../../shared/testing/duplicateIds.ts";
import {unnamedControls} from "../../shared/testing/unnamedControls.ts";
import {RequirementEvidenceCommands} from "../requirementEvidenceCommands.ts";
import {RequirementEvidenceContext} from "../RequirementEvidenceContext.ts";
import RequirementEvidencePanel from "./RequirementEvidencePanel.tsx";
import {openingPreviewTab, previewTabDouble} from "../testing/previewTabDouble.ts";

const viewport = vi.hoisted(() => ({phone: false}));

vi.mock("../../../../utils/useMediaQuery.ts", () => ({
    BREAKPOINTS: {mobile: 768, tablet: 1024},
    useMediaQuery: () => viewport.phone,
    useIsMobile: () => viewport.phone,
    useIsTablet: () => viewport.phone,
}));

const panelId = "information-request-evidence-root-supporting-record-PANEL";
const rowId = `${panelId}-artifact-evidence-1`;

const commands = {
    list: vi.fn(),
    upload: vi.fn(),
    replace: vi.fn(),
    withdraw: vi.fn(),
    open: vi.fn(),
};

const version = (overrides: Partial<InformationRequestEvidenceVersionDto> = {}): InformationRequestEvidenceVersionDto => ({
    id: "version-a",
    versionNumber: 1,
    sourceKind: InformationRequestEvidenceSourceKind.DOCUMENT_VERSION,
    declaredFileName: "record.pdf",
    declaredMediaType: "application/pdf",
    contentLength: 2048,
    createdByCaller: true,
    createdAt: Date.parse("2026-09-25T08:00:00Z"),
    conformance: InformationRequestEvidenceConformance.PENDING,
    findings: [{code: InformationRequestEvidenceFindingCode.NOT_SCANNED, blocking: false}],
    ...overrides,
});

const artifact = (overrides: Partial<InformationRequestEvidenceArtifactDto> = {}): InformationRequestEvidenceArtifactDto => ({
    id: "artifact-a",
    requirementId: "requirement-a",
    artifactKey: "evidence-1",
    collectionState: InformationRequestEvidenceCollectionState.ACTIVE,
    artifactRevision: 1,
    etag: "\"artifact-a:1\"",
    createdByCaller: true,
    changeableByCaller: true,
    createdAt: Date.parse("2026-09-25T08:00:00Z"),
    updatedAt: Date.parse("2026-09-25T08:00:00Z"),
    versions: [version()],
    ...overrides,
});

const evaluation = (state: InformationRequestEvidenceRequirementState): InformationRequestEvidenceEvaluationDto => ({
    state,
    completesWork: false,
    satisfiedBySubstitute: false,
    findings: [],
});

const listed = (artifacts: InformationRequestEvidenceArtifactDto[]): InformationRequestEvidenceListDto => ({
    requirementId: "requirement-a",
    evidenceETag: "\"requirement-a:1\"",
    artifacts,
    evaluation: evaluation(InformationRequestEvidenceRequirementState.PENDING_ASSESSMENT),
});

const policy = (overrides: Partial<InformationRequestTemplateEvidencePolicyDto> = {}): InformationRequestTemplateEvidencePolicyDto => ({
    id: "policy-a",
    minimumFileCount: 1,
    issuerRequirement: InformationRequestEvidenceAttributeRequirement.REQUIRED,
    jurisdictionRequirement: InformationRequestEvidenceAttributeRequirement.NOT_CAPTURED,
    languageRequirement: InformationRequestEvidenceAttributeRequirement.OPTIONAL,
    issueDateRequirement: InformationRequestEvidenceAttributeRequirement.NOT_CAPTURED,
    expiryDateRequirement: InformationRequestEvidenceAttributeRequirement.NOT_CAPTURED,
    coveragePeriodRequirement: InformationRequestEvidenceAttributeRequirement.REQUIRED,
    certificationRequirement: InformationRequestEvidenceAttributeRequirement.NOT_CAPTURED,
    signatureRequirement: InformationRequestEvidenceAttributeRequirement.NOT_CAPTURED,
    coverageContinuityRequired: false,
    waiverPolicy: InformationRequestEvidenceWaiverPolicy.NOT_PERMITTED,
    conformancePolicy: InformationRequestEvidenceConformancePolicy.CONFORMANCE_REQUIRED,
    acceptedValues: [
        {attribute: InformationRequestEvidenceAttribute.ISSUER, acceptedValue: "Records office"},
        {attribute: InformationRequestEvidenceAttribute.ISSUER, acceptedValue: "Registry service"},
    ],
    ...overrides,
});

const noDetailsPolicy = (): InformationRequestTemplateEvidencePolicyDto => policy({
    issuerRequirement: InformationRequestEvidenceAttributeRequirement.NOT_CAPTURED,
    languageRequirement: InformationRequestEvidenceAttributeRequirement.NOT_CAPTURED,
    coveragePeriodRequirement: InformationRequestEvidenceAttributeRequirement.NOT_CAPTURED,
});

const renderPanel = (uploadAvailable = true, malwareScanning = true, evidencePolicy?: InformationRequestTemplateEvidencePolicyDto) =>
    render(
        <RequirementEvidenceContext.Provider
            value={{uploadAvailable, malwareScanning, commands: commands as RequirementEvidenceCommands}}>
            <RequirementEvidencePanel requestId="request-a"
                                      requirementId="requirement-a"
                                      prompt="Provide the supporting record"
                                      elementId="root-supporting-record"
                                      evidencePolicy={evidencePolicy}/>
        </RequirementEvidenceContext.Provider>,
    );

const panelFor = (requirementId: string) => (
    <RequirementEvidenceContext.Provider
        value={{uploadAvailable: true, malwareScanning: true, commands: commands as RequirementEvidenceCommands}}>
        <RequirementEvidencePanel requestId="request-a"
                                  requirementId={requirementId}
                                  prompt="Provide the supporting record"
                                  elementId="root-supporting-record"/>
    </RequirementEvidenceContext.Provider>
);

const CONFIDENTIALITY_REFUSAL = {
    reasonCode: "INFORMATION_REQUEST_CONFIDENTIALITY_DENIED",
    errorMessage: "Verification is no longer current",
};

interface PendingList {
    read: Promise<InformationRequestEvidenceListDto>;
    answer: (list: InformationRequestEvidenceListDto) => void;
}

const pendingList = (): PendingList => {
    let answer: (list: InformationRequestEvidenceListDto) => void = () => undefined;
    const read = new Promise<InformationRequestEvidenceListDto>(resolve => {
        answer = resolve;
    });
    return {read, answer};
};

const detailsDialog = `${panelId}-details-dialog`;

const enter = (field: string, value: string) =>
    fireEvent.change(document.getElementById(`${detailsDialog}-${field}`) as HTMLInputElement, {target: {value}});

const confirmButton = () => document.getElementById(`${detailsDialog}-confirm`) as HTMLButtonElement;

const chooseFile = (inputId: string, file: File) =>
    fireEvent.change(document.getElementById(inputId) as HTMLInputElement, {target: {files: [file]}});

const duplicatedIds = (prefix: string): string[] => {
    const ids = Array.from(document.querySelectorAll("[id]"))
        .map(element => element.id)
        .filter(id => id.startsWith(prefix));
    return ids.filter((id, index) => ids.indexOf(id) !== index);
};

describe("RequirementEvidencePanel", () =>
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
        Object.assign(URL, {createObjectURL: vi.fn(), revokeObjectURL: vi.fn()});
    });

    beforeEach(() =>
    {
        vi.resetAllMocks();
        commands.list.mockResolvedValue(listed([artifact()]));
        commands.upload.mockResolvedValue({outcome: "SAVED", result: {}});
        commands.withdraw.mockResolvedValue({outcome: "SAVED", result: {}});
        vi.mocked(URL.createObjectURL).mockReturnValue("blob:evidence-content");
    });

    afterEach(() => {
        cleanup();
        viewport.phone = false;
        vi.restoreAllMocks();
    });

    it("shows the Requirement's evidence state, each file's conformance and findings, and the actions it allows", async () =>
    {
        renderPanel();

        expect(await screen.findByText("record.pdf")).toBeTruthy();
        expect(document.getElementById(`${panelId}-state`)?.textContent).toBe("Awaiting checks");
        expect(screen.getByText("This file is waiting for a malware scan.")).toBeTruthy();
        const row = `${panelId}-artifact-evidence-1`;
        ["preview", "download", "replace", "withdraw"].forEach(action =>
            expect(document.getElementById(`${row}-${action}`)).toBeTruthy());
        expect(commands.list).toHaveBeenCalledWith("request-a", "requirement-a");
    });

    it("shows whether the document is required and its help text while its files are collected", async () => {
        render(
            <RequirementEvidenceContext.Provider
                value={{
                    uploadAvailable: true,
                    malwareScanning: true,
                    commands: commands as RequirementEvidenceCommands
                }}>
                <RequirementEvidencePanel requestId="request-a"
                                          requirementId="requirement-a"
                                          prompt="Provide the supporting record"
                                          required={true}
                                          helpText="Both pages, as one PDF"
                                          elementId="root-supporting-record"/>
            </RequirementEvidenceContext.Provider>,
        );

        expect(await screen.findByText("record.pdf")).toBeTruthy();
        expect(screen.getByText("Both pages, as one PDF")).toBeTruthy();
        expect(document.getElementById(`${panelId}-requiredness`)?.textContent).toBe("Required");
    });

    it("keeps every id of one Requirement's panel apart from a Requirement whose key adds a suffix to it", async () => {
        const keys = ["root-records", "root-records-artifacts", "root-records-upload-button-input", "root-records-empty"];
        commands.list.mockImplementation(async (_requestId: string, requirementId: string) => ({
            ...listed([artifact({requirementId})]),
            requirementId,
        }));

        render(
            <RequirementEvidenceContext.Provider
                value={{
                    uploadAvailable: true,
                    malwareScanning: true,
                    commands: commands as RequirementEvidenceCommands
                }}>
                {keys.map(key => (
                    <RequirementEvidencePanel key={key}
                                              requestId="request-a"
                                              requirementId={`requirement-${key}`}
                                              prompt={`Provide ${key}`}
                                              elementId={key}/>
                ))}
            </RequirementEvidenceContext.Provider>,
        );

        await waitFor(() => expect(commands.list).toHaveBeenCalledTimes(keys.length));
        expect(await screen.findAllByText("record.pdf")).toHaveLength(keys.length);
        expect(duplicatedIds("information-request-evidence-")).toEqual([]);
    });

    it("states every missing or unaccepted detail of a file, not only the first of each kind", async () => {
        commands.list.mockResolvedValue(listed([artifact({
            versions: [version({
                conformance: InformationRequestEvidenceConformance.DEFICIENT,
                findings: [
                    {code: InformationRequestEvidenceFindingCode.ATTRIBUTE_MISSING, blocking: true, detail: "ISSUER"},
                    {
                        code: InformationRequestEvidenceFindingCode.ATTRIBUTE_MISSING,
                        blocking: true,
                        detail: "EXPIRY_DATE"
                    },
                    {
                        code: InformationRequestEvidenceFindingCode.ATTRIBUTE_MISSING,
                        blocking: true,
                        detail: "EXPIRY_DATE"
                    },
                    {
                        code: InformationRequestEvidenceFindingCode.ATTRIBUTE_NOT_ACCEPTED,
                        blocking: true,
                        detail: "ISSUER"
                    },
                ],
            })],
        })]));
        renderPanel();

        expect(await screen.findByText("A required detail is missing: issuer.")).toBeTruthy();
        expect(screen.getAllByText("A required detail is missing: expiry date.")).toHaveLength(1);
        expect(screen.getByText("The issuer is not one this Requirement accepts.")).toBeTruthy();
        expect(duplicatedIds("information-request-evidence-")).toEqual([]);
    });

    it("uploads a chosen file under the occurrence evidence ETag and shows the refreshed files", async () =>
    {
        renderPanel();
        await screen.findByText("record.pdf");
        const file = new File(["second"], "second.pdf", {type: "application/pdf"});

        chooseFile(`${panelId}-upload-button-input`, file);

        await waitFor(() => expect(commands.list).toHaveBeenCalledTimes(2));
        expect(commands.upload).toHaveBeenCalledWith("request-a", "requirement-a", file, "\"requirement-a:1\"", expect.any(Function), expect.any(String), {});
        expect(document.getElementById(detailsDialog)).toBeNull();
    });

    it("asks for the details the evidence policy captures and uploads them with the file", async () => {
        renderPanel(true, true, policy());
        await screen.findByText("record.pdf");
        const file = new File(["second"], "second.pdf", {type: "application/pdf"});

        chooseFile(`${panelId}-upload-button-input`, file);

        expect(await waitFor(() => document.getElementById(detailsDialog))).toBeTruthy();
        expect(commands.upload).not.toHaveBeenCalled();
        ["issuer", "language", "coverage-starts-on", "coverage-ends-on"].forEach(field =>
            expect(document.getElementById(`${detailsDialog}-${field}`)).toBeTruthy());
        ["jurisdiction", "issued-on", "expires-on", "certification-reference", "signature-reference"].forEach(field =>
            expect(document.getElementById(`${detailsDialog}-${field}`)).toBeNull());
        expect(Array.from((document.getElementById(`${detailsDialog}-issuer`) as HTMLSelectElement).options).map(option => option.value))
            .toEqual(["", "Records office", "Registry service"]);
        expect(confirmButton().disabled).toBe(true);

        enter("issuer", "Registry service");
        enter("language", "en");
        enter("coverage-starts-on", "2026-01-01");
        enter("coverage-ends-on", "2026-06-30");
        fireEvent.click(confirmButton());

        await waitFor(() => expect(commands.upload).toHaveBeenCalledWith(
            "request-a",
            "requirement-a",
            file,
            "\"requirement-a:1\"",
            expect.any(Function),
            expect.any(String),
            {issuer: "Registry service", language: "en", coverageStartsOn: "2026-01-01", coverageEndsOn: "2026-06-30"},
        ));
        await waitFor(() => expect(document.getElementById(detailsDialog)).toBeNull());
    });

    it("holds the upload until required details are stated and the coverage period is whole", async () => {
        renderPanel(true, true, policy());
        await screen.findByText("record.pdf");
        chooseFile(`${panelId}-upload-button-input`, new File(["a"], "a.pdf"));
        await waitFor(() => expect(document.getElementById(detailsDialog)).toBeTruthy());

        enter("issuer", "Records office");
        enter("coverage-starts-on", "2026-03-01");
        expect(confirmButton().disabled).toBe(true);
        expect(screen.getByText("State both the start and the end of the coverage period.")).toBeTruthy();

        enter("coverage-ends-on", "2026-02-01");
        expect(confirmButton().disabled).toBe(true);
        expect(screen.getByText("The coverage period cannot end before it starts.")).toBeTruthy();

        enter("coverage-ends-on", "2026-03-31");
        expect(confirmButton().disabled).toBe(false);
    });

    it("uploads nothing when the details are cancelled", async () => {
        renderPanel(true, true, policy());
        await screen.findByText("record.pdf");
        chooseFile(`${panelId}-upload-button-input`, new File(["a"], "a.pdf"));
        await waitFor(() => expect(document.getElementById(detailsDialog)).toBeTruthy());

        fireEvent.click(document.getElementById(`${detailsDialog}-cancel`) as HTMLElement);

        await waitFor(() => expect(document.getElementById(detailsDialog)).toBeNull());
        expect(commands.upload).not.toHaveBeenCalled();
    });

    it("uploads straight away when the evidence policy captures no details", async () => {
        renderPanel(true, true, noDetailsPolicy());
        await screen.findByText("record.pdf");
        const file = new File(["a"], "a.pdf");

        chooseFile(`${panelId}-upload-button-input`, file);

        await waitFor(() => expect(commands.upload).toHaveBeenCalledWith(
            "request-a", "requirement-a", file, "\"requirement-a:1\"", expect.any(Function), expect.any(String), {},
        ));
        expect(document.getElementById(detailsDialog)).toBeNull();
    });

    it("starts a replacement from the details the latest version stated", async () => {
        commands.list.mockResolvedValue(listed([artifact({
            versions: [version({
                issuer: "Records office",
                language: "en",
                coverageStartsOn: "2026-01-01",
                coverageEndsOn: "2026-03-31"
            })],
        })]));
        commands.replace.mockResolvedValue({outcome: "SAVED", result: {}});
        renderPanel(true, true, policy());
        await screen.findByText("record.pdf");
        const file = new File(["corrected"], "corrected.pdf");

        chooseFile(`${panelId}-artifact-evidence-1-replace-input`, file);
        await waitFor(() => expect(document.getElementById(detailsDialog)).toBeTruthy());
        expect((document.getElementById(`${detailsDialog}-issuer`) as HTMLSelectElement).value).toBe("Records office");
        enter("coverage-ends-on", "2026-06-30");
        fireEvent.click(confirmButton());

        await waitFor(() => expect(commands.replace).toHaveBeenCalledWith(
            "request-a",
            "requirement-a",
            "artifact-a",
            file,
            "\"artifact-a:1\"",
            expect.any(Function),
            expect.any(String),
            {issuer: "Records office", language: "en", coverageStartsOn: "2026-01-01", coverageEndsOn: "2026-06-30"},
        ));
    });

    it("retries an interrupted upload with the details it was given", async () => {
        commands.upload
            .mockRejectedValueOnce({errorMessage: "The connection was lost"})
            .mockResolvedValueOnce({outcome: "SAVED", result: {}});
        renderPanel(true, true, policy());
        await screen.findByText("record.pdf");
        chooseFile(`${panelId}-upload-button-input`, new File(["kept"], "kept.pdf"));
        await waitFor(() => expect(document.getElementById(detailsDialog)).toBeTruthy());
        enter("issuer", "Records office");
        enter("coverage-starts-on", "2026-01-01");
        enter("coverage-ends-on", "2026-01-31");
        fireEvent.click(confirmButton());

        fireEvent.click(await screen.findByRole("button", {name: "Retry uploading kept.pdf"}));

        await waitFor(() => expect(commands.upload).toHaveBeenCalledTimes(2));
        expect(commands.upload.mock.calls[1][6]).toEqual({
            issuer: "Records office",
            coverageStartsOn: "2026-01-01",
            coverageEndsOn: "2026-01-31"
        });
        expect(commands.upload.mock.calls[1][5]).toBe(commands.upload.mock.calls[0][5]);
    });

    it("shows the details each file stated", async () => {
        commands.list.mockResolvedValue(listed([artifact({
            versions: [version({
                issuer: "Records office",
                coverageStartsOn: "2026-01-01",
                coverageEndsOn: "2026-03-31"
            })],
        })]));
        renderPanel();
        await screen.findByText("record.pdf");

        const details = document.getElementById(`${panelId}-artifact-evidence-1-details`)?.textContent ?? "";
        expect(details).toContain("Issuer: Records office");
        expect(details).toContain("Covers 2026-01-01 to 2026-03-31");
    });

    it("names the upload's progress for assistive technology while the file is sent", async () =>
    {
        commands.upload.mockImplementation((_request: string, _requirement: string, _file: File, _etag: string, onProgress: (percent: number) => void) =>
        {
            onProgress(40);
            return new Promise(() => undefined);
        });
        renderPanel();
        await screen.findByText("record.pdf");

        chooseFile(`${panelId}-upload-button-input`, new File(["second"], "second.pdf", {type: "application/pdf"}));

        const progress = await screen.findByRole("progressbar", {name: "Uploading, 40 percent sent"});
        expect(progress.getAttribute("aria-valuenow")).toBe("0.4");
    });

    it("explains a stale precondition and a refusal in plain language", async () =>
    {
        commands.upload.mockResolvedValueOnce({outcome: "STALE"});
        renderPanel();
        await screen.findByText("record.pdf");

        chooseFile(`${panelId}-upload-button-input`, new File(["a"], "a.pdf"));
        expect(await screen.findByText(/This evidence changed while you were working/)).toBeTruthy();

        commands.upload.mockRejectedValueOnce({reasonCode: "INFORMATION_REQUEST_EVIDENCE_DUPLICATE_CONTENT"});
        chooseFile(`${panelId}-upload-button-input`, new File(["a"], "a.pdf"));
        expect(await screen.findByText("This file has already been provided for this Requirement.")).toBeTruthy();
    });

    it("says its files are loading until the list arrives", async () => {
        const pending = pendingList();
        commands.list.mockReturnValue(pending.read);
        renderPanel();

        expect(screen.getByRole("progressbar", {name: "Loading files"})).toBeTruthy();
        await act(async () => pending.answer(listed([artifact()])));

        expect(await screen.findByText("record.pdf")).toBeTruthy();
        expect(screen.queryByRole("progressbar", {name: "Loading files"})).toBeNull();
    });

    it("announces a stale or refused change to assistive technology", async () => {
        commands.upload.mockRejectedValueOnce({reasonCode: "INFORMATION_REQUEST_EVIDENCE_DUPLICATE_CONTENT"});
        renderPanel();
        await screen.findByText("record.pdf");

        chooseFile(`${panelId}-upload-button-input`, new File(["a"], "a.pdf"));

        expect((await screen.findByRole("alert")).textContent).toBe("This file has already been provided for this Requirement.");
    });

    it("keeps a file whose upload was interrupted and retries it under the same Idempotency-Key", async () =>
    {
        commands.list.mockResolvedValue(listed([]));
        commands.upload
            .mockRejectedValueOnce({errorMessage: "The connection was lost"})
            .mockResolvedValueOnce({outcome: "SAVED", result: {}});
        renderPanel();
        await screen.findByText("No files yet.");
        const file = new File(["record"], "record.pdf", {type: "application/pdf"});

        chooseFile(`${panelId}-upload-button-input`, file);
        fireEvent.click(await screen.findByRole("button", {name: "Retry uploading record.pdf"}));

        await waitFor(() => expect(commands.upload).toHaveBeenCalledTimes(2));
        const [first, second] = commands.upload.mock.calls;
        expect(second[2]).toBe(file);
        expect(second[5]).toBe(first[5]);
        expect(second[6]).toEqual(first[6]);
        await waitFor(() => expect(screen.queryByRole("button", {name: "Retry uploading record.pdf"})).toBeNull());
    });

    it("keeps a file whose upload went stale and retries it with its details against the latest files under the same Idempotency-Key", async () =>
    {
        commands.list
            .mockResolvedValueOnce(listed([]))
            .mockResolvedValue({...listed([]), evidenceETag: "\"requirement-a:2\""});
        commands.upload
            .mockResolvedValueOnce({outcome: "STALE"})
            .mockResolvedValueOnce({outcome: "SAVED", result: {}});
        renderPanel(true, true, policy());
        await screen.findByText("No files yet.");
        chooseFile(`${panelId}-upload-button-input`, new File(["a"], "a.pdf"));
        await waitFor(() => expect(document.getElementById(detailsDialog)).toBeTruthy());
        enter("issuer", "Records office");
        enter("coverage-starts-on", "2026-01-01");
        enter("coverage-ends-on", "2026-01-31");
        fireEvent.click(confirmButton());

        await screen.findByText(/This evidence changed while you were working/);
        fireEvent.click(await screen.findByRole("button", {name: "Retry uploading a.pdf"}));

        await waitFor(() => expect(commands.upload).toHaveBeenCalledTimes(2));
        const [first, retried] = commands.upload.mock.calls;
        expect(first[3]).toBe("\"requirement-a:1\"");
        expect(retried[3]).toBe("\"requirement-a:2\"");
        expect(retried[5]).toBe(first[5]);
        expect(retried[6]).toEqual({
            issuer: "Records office",
            coverageStartsOn: "2026-01-01",
            coverageEndsOn: "2026-01-31"
        });
        await waitFor(() => expect(screen.queryByRole("button", {name: "Retry uploading a.pdf"})).toBeNull());
    });

    it("keeps an earlier file that was not sent when a later file is sent, and discards a kept file on request", async () => {
        commands.list.mockResolvedValue(listed([]));
        commands.upload
            .mockRejectedValueOnce({errorMessage: "The connection was lost"})
            .mockResolvedValueOnce({outcome: "SAVED", result: {}});
        renderPanel();
        await screen.findByText("No files yet.");

        chooseFile(`${panelId}-upload-button-input`, new File(["a"], "a.pdf"));
        await screen.findByRole("button", {name: "Retry uploading a.pdf"});
        chooseFile(`${panelId}-upload-button-input`, new File(["b"], "b.pdf"));
        await waitFor(() => expect(commands.upload).toHaveBeenCalledTimes(2));

        expect(screen.getByRole("button", {name: "Retry uploading a.pdf"})).toBeTruthy();
        expect(screen.queryByRole("button", {name: "Retry uploading b.pdf"})).toBeNull();
        fireEvent.click(screen.getByRole("button", {name: "Discard a.pdf"}));
        expect(screen.queryByRole("button", {name: "Retry uploading a.pdf"})).toBeNull();
    });

    it("withdraws a file with a reason under the file's own revision", async () =>
    {
        renderPanel();
        await screen.findByText("record.pdf");

        fireEvent.click(document.getElementById(`${panelId}-artifact-evidence-1-withdraw`) as HTMLElement);
        const reason = await waitFor(() => document.getElementById(`${panelId}-withdraw-dialog-reason`) as HTMLTextAreaElement);
        fireEvent.change(reason, {target: {value: "Recorded in error"}});
        fireEvent.click(document.getElementById(`${panelId}-withdraw-dialog-confirm`) as HTMLElement);

        await waitFor(() => expect(commands.withdraw).toHaveBeenCalledWith(
            "request-a",
            "requirement-a",
            "artifact-a",
            "Recorded in error",
            "\"artifact-a:1\"",
        ));
    });

    it("offers no upload, replacement, or withdrawal while its item cannot change, and still opens its files", async () => {
        render(
            <RequirementEvidenceContext.Provider
                value={{
                    uploadAvailable: true,
                    malwareScanning: true,
                    commands: commands as RequirementEvidenceCommands
                }}>
                <RequirementEvidencePanel requestId="request-a"
                                          requirementId="requirement-a"
                                          prompt="Provide the supporting record"
                                          elementId="root-supporting-record"
                                          locked={true}/>
            </RequirementEvidenceContext.Provider>,
        );
        await screen.findByText("record.pdf");

        expect(document.getElementById(`${panelId}-upload-button`)).toBeNull();
        expect(document.getElementById(`${rowId}-replace`)).toBeNull();
        expect(document.getElementById(`${rowId}-withdraw`)).toBeNull();
        expect(document.getElementById(`${rowId}-download`)).toBeTruthy();
        expect(document.getElementById(`${rowId}-preview`)).toBeTruthy();
    });

    it("tells the respondent when upload is unavailable and offers no upload or replacement", async () =>
    {
        renderPanel(false);
        await screen.findByText("record.pdf");

        expect(screen.getByText("Evidence upload is not available right now.")).toBeTruthy();
        expect(document.getElementById(`${panelId}-upload-button`)).toBeNull();
        expect(document.getElementById(`${panelId}-artifact-evidence-1-replace`)).toBeNull();
    });

    it("shows a file another party provided without offering to replace or withdraw it", async () => {
        commands.list.mockResolvedValue(listed([artifact({createdByCaller: false, changeableByCaller: false})]));
        renderPanel();
        await screen.findByText("record.pdf");

        expect(document.getElementById(`${panelId}-artifact-evidence-1-download`)).toBeTruthy();
        expect(document.getElementById(`${panelId}-artifact-evidence-1-replace`)).toBeNull();
        expect(document.getElementById(`${panelId}-artifact-evidence-1-withdraw`)).toBeNull();
        expect(document.getElementById(`${panelId}-artifact-evidence-1-provided-by-other`)?.textContent)
            .toBe("Provided by another party. Only that party can replace or withdraw it.");
    });

    it("shows a withdrawn file without its findings or change actions", async () =>
    {
        commands.list.mockResolvedValue(listed([artifact({collectionState: InformationRequestEvidenceCollectionState.WITHDRAWN})]));
        renderPanel();
        await screen.findByText("record.pdf");

        expect(document.getElementById(`${panelId}-artifact-evidence-1-meta`)?.textContent).toContain("Withdrawn");
        expect(document.getElementById(`${panelId}-artifact-evidence-1-withdraw`)).toBeNull();
        expect(screen.queryByText("This file is waiting for a malware scan.")).toBeNull();
    });

    it("says so when this deployment does not scan files for malware", async () =>
    {
        renderPanel(true, false);
        await screen.findByText("record.pdf");

        expect(document.getElementById(`${panelId}-scanning-note`)?.textContent).toBe("Files are not scanned for malware.");
    });

    it("mentions no scanning gap where files are scanned", async () =>
    {
        renderPanel(true, true);
        await screen.findByText("record.pdf");

        expect(document.getElementById(`${panelId}-scanning-note`)).toBeNull();
    });

    it("opens the preview tab at once, shows the latest version in it once read, and downloads it under its file name", async () => {
        const content = new Blob(["record"], {type: "application/pdf"});
        let deliver: (read: Blob) => void = () => undefined;
        commands.open
            .mockImplementationOnce(() => new Promise<Blob>(resolve => {
                deliver = resolve;
            }))
            .mockResolvedValue(content);
        const tab = previewTabDouble();
        const opened = openingPreviewTab(tab);
        const saved: { href: string; download: string }[] = [];
        vi.spyOn(HTMLAnchorElement.prototype, "click").mockImplementation(function (this: HTMLAnchorElement) {
            saved.push({href: this.href, download: this.download});
        });
        renderPanel();
        await screen.findByText("record.pdf");

        fireEvent.click(document.getElementById(`${rowId}-preview`) as HTMLElement);

        expect(opened).toHaveBeenCalledWith("", "_blank");
        expect(tab.opener).toBeNull();
        expect(tab.location.replace).not.toHaveBeenCalled();
        await act(async () => deliver(content));
        await waitFor(() => expect(tab.location.replace).toHaveBeenCalledWith("blob:evidence-content"));
        expect(commands.open).toHaveBeenCalledWith("request-a", "requirement-a", "artifact-a", "version-a", "preview");
        expect(URL.createObjectURL).toHaveBeenCalledWith(content);

        fireEvent.click(document.getElementById(`${rowId}-download`) as HTMLElement);

        await waitFor(() => expect(saved).toEqual([{href: "blob:evidence-content", download: "record.pdf"}]));
        expect(commands.open).toHaveBeenLastCalledWith("request-a", "requirement-a", "artifact-a", "version-a", "content");
        expect(opened).toHaveBeenCalledTimes(1);
    });

    it("says so when the browser blocks the preview tab, and reads nothing", async () => {
        openingPreviewTab(null);
        renderPanel();
        await screen.findByText("record.pdf");

        fireEvent.click(document.getElementById(`${rowId}-preview`) as HTMLElement);

        expect((await screen.findByRole("alert")).textContent)
            .toBe("Your browser blocked the preview tab. Allow pop-ups for this site, or select Download.");
        expect(commands.open).not.toHaveBeenCalled();
    });

    it("says why a preview failed and closes the tab it opened without showing anything in it", async () => {
        commands.open.mockRejectedValue({reasonCode: "INFORMATION_REQUEST_EVIDENCE_PREVIEW_UNAVAILABLE"});
        const tab = previewTabDouble();
        openingPreviewTab(tab);
        renderPanel();
        await screen.findByText("record.pdf");

        fireEvent.click(document.getElementById(`${rowId}-preview`) as HTMLElement);

        expect(await screen.findByText("This file cannot be previewed. Download it instead.")).toBeTruthy();
        expect(tab.close).toHaveBeenCalledTimes(1);
        expect(tab.location.replace).not.toHaveBeenCalled();
        expect(URL.createObjectURL).not.toHaveBeenCalled();
    });

    it("explains a stale replacement, keeps it, and retries it against the file's latest revision", async () => {
        commands.list
            .mockResolvedValueOnce(listed([artifact()]))
            .mockResolvedValue(listed([artifact({etag: "\"artifact-a:2\"", artifactRevision: 2})]));
        commands.replace
            .mockResolvedValueOnce({outcome: "STALE"})
            .mockResolvedValueOnce({outcome: "SAVED", result: {}});
        renderPanel();
        await screen.findByText("record.pdf");

        chooseFile(`${rowId}-replace-input`, new File(["corrected"], "corrected.pdf"));

        expect(await screen.findByText(/This evidence changed while you were working/)).toBeTruthy();
        fireEvent.click(await screen.findByRole("button", {name: "Retry uploading corrected.pdf"}));

        await waitFor(() => expect(commands.replace).toHaveBeenCalledTimes(2));
        const [first, retried] = commands.replace.mock.calls;
        expect(first[4]).toBe("\"artifact-a:1\"");
        expect(retried.slice(0, 3)).toEqual(["request-a", "requirement-a", "artifact-a"]);
        expect(retried[4]).toBe("\"artifact-a:2\"");
        expect(retried[6]).toBe(first[6]);
        expect(commands.upload).not.toHaveBeenCalled();
    });

    it("states why a locked file cannot be replaced", async () => {
        commands.replace.mockRejectedValueOnce({
            reasonCode: "INFORMATION_REQUEST_SUBMISSION_LOCKED",
            errorMessage: "This Information Request Requirement was submitted and cannot change until its submission is withdrawn",
        });
        renderPanel();
        await screen.findByText("record.pdf");

        chooseFile(`${rowId}-replace-input`, new File(["corrected"], "corrected.pdf"));

        expect(await screen.findByText("This Information Request Requirement was submitted and cannot change until its submission is withdrawn"))
            .toBeTruthy();
        expect(commands.replace).toHaveBeenCalledTimes(1);
    });

    it("keeps a replacement whose upload was interrupted and retries it against the same file under the same Idempotency-Key", async () => {
        commands.replace
            .mockRejectedValueOnce({errorMessage: "The connection was lost"})
            .mockResolvedValueOnce({outcome: "SAVED", result: {}});
        renderPanel();
        await screen.findByText("record.pdf");
        const file = new File(["corrected"], "corrected.pdf");

        chooseFile(`${rowId}-replace-input`, file);
        fireEvent.click(await screen.findByRole("button", {name: "Retry uploading corrected.pdf"}));

        await waitFor(() => expect(commands.replace).toHaveBeenCalledTimes(2));
        const [first, retried] = commands.replace.mock.calls;
        expect(retried.slice(0, 5)).toEqual(["request-a", "requirement-a", "artifact-a", file, "\"artifact-a:1\""]);
        expect(retried[6]).toBe(first[6]);
        expect(commands.upload).not.toHaveBeenCalled();
        await waitFor(() => expect(screen.queryByRole("button", {name: "Retry uploading corrected.pdf"})).toBeNull());
    });

    it("keeps every evidence control and its id on a phone-sized screen", async () => {
        viewport.phone = true;
        renderPanel(true, false, policy());
        await screen.findByText("record.pdf");

        [
            `${panelId}-state`,
            `${panelId}-upload-button`,
            `${panelId}-scanning-note`,
            `${rowId}-name`,
            `${rowId}-meta`,
            `${rowId}-preview`,
            `${rowId}-download`,
            `${rowId}-replace`,
            `${rowId}-withdraw`,
        ].forEach(id => expect(document.getElementById(id)).toBeTruthy());
        expect(unnamedControls(document.body)).toEqual([]);
        expect(duplicateIds(document.body)).toEqual([]);
    });

    it("renders nothing outside the evidence switch", () =>
    {
        const {container} = render(
            <RequirementEvidencePanel requestId="request-a"
                                      requirementId="requirement-a"
                                      prompt="Provide the supporting record"
                                      elementId="root-supporting-record"/>,
        );

        expect(container.innerHTML).toBe("");
        expect(commands.list).not.toHaveBeenCalled();
    });

    it("withholds the listed files and their actions once a reread after a change is refused", async () => {
        renderPanel();
        await screen.findByText("record.pdf");
        commands.list.mockRejectedValue(CONFIDENTIALITY_REFUSAL);

        chooseFile(`${panelId}-upload-button-input`, new File(["additional"], "additional.pdf", {type: "application/pdf"}));

        expect(await screen.findByText("Verification is no longer current")).toBeTruthy();
        expect(screen.queryByText("record.pdf")).toBeNull();
        expect(document.getElementById(`${rowId}-withdraw`)).toBeNull();
        expect((document.getElementById(`${panelId}-upload-button`) as HTMLButtonElement).disabled).toBe(true);
    });

    it("reads the evidence again on Try again and shows no earlier file until it answers", async () => {
        const retried = pendingList();
        commands.list
            .mockResolvedValueOnce(listed([artifact()]))
            .mockRejectedValueOnce(CONFIDENTIALITY_REFUSAL)
            .mockReturnValueOnce(retried.read);
        renderPanel();
        await screen.findByText("record.pdf");
        chooseFile(`${panelId}-upload-button-input`, new File(["additional"], "additional.pdf", {type: "application/pdf"}));
        await screen.findByText("Verification is no longer current");

        fireEvent.click(screen.getByRole("button", {name: "Try again"}));

        expect(screen.queryByText("Verification is no longer current")).toBeNull();
        expect(screen.queryByText("record.pdf")).toBeNull();
        await act(async () => retried.answer(listed([artifact({versions: [version({declaredFileName: "current.pdf"})]})])));
        expect(await screen.findByText("current.pdf")).toBeTruthy();
        expect(commands.list).toHaveBeenCalledTimes(3);
    });

    it("keeps a file that was not sent while the evidence is withheld and retries it once the evidence is read again", async () => {
        commands.upload
            .mockRejectedValueOnce({errorMessage: "The connection was lost"})
            .mockResolvedValueOnce({outcome: "SAVED", result: {}});
        commands.list
            .mockResolvedValueOnce(listed([artifact()]))
            .mockRejectedValueOnce(CONFIDENTIALITY_REFUSAL)
            .mockResolvedValue(listed([artifact()]));
        renderPanel();
        await screen.findByText("record.pdf");

        chooseFile(`${panelId}-upload-button-input`, new File(["additional"], "additional.pdf", {type: "application/pdf"}));
        await screen.findByText("Verification is no longer current");

        expect((screen.getByRole("button", {name: "Retry uploading additional.pdf"}) as HTMLButtonElement).disabled).toBe(true);
        expect((screen.getByRole("button", {name: "Discard additional.pdf"}) as HTMLButtonElement).disabled).toBe(false);
        fireEvent.click(screen.getByRole("button", {name: "Try again"}));
        await screen.findByText("record.pdf");
        fireEvent.click(screen.getByRole("button", {name: "Retry uploading additional.pdf"}));

        await waitFor(() => expect(commands.upload).toHaveBeenCalledTimes(2));
        expect(commands.upload.mock.calls[1][5]).toBe(commands.upload.mock.calls[0][5]);
    });

    it("shows no file of an earlier Requirement once it shows another Requirement", async () => {
        const earlier = pendingList();
        const next = pendingList();
        commands.list.mockImplementation((_, requirementId: string) =>
            requirementId === "requirement-a" ? earlier.read : next.read);
        const panel = render(panelFor("requirement-a"));

        panel.rerender(panelFor("requirement-b"));
        await act(async () => earlier.answer(listed([artifact()])));

        expect(screen.queryByText("record.pdf")).toBeNull();
        await act(async () => next.answer(listed([artifact({
            id: "artifact-b",
            versions: [version({declaredFileName: "other.pdf"})]
        })])));
        expect(await screen.findByText("other.pdf")).toBeTruthy();
        expect(screen.queryByText("record.pdf")).toBeNull();
    });
});
