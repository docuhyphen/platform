/** @vitest-environment jsdom */
import {cleanup, fireEvent, render, screen, waitFor} from "@testing-library/react";
import {afterEach, beforeAll, beforeEach, describe, expect, it, vi} from "vitest";
import {
    InformationRequestEvidenceArtifactDto,
    InformationRequestEvidenceCollectionState,
    InformationRequestEvidenceConformance,
    InformationRequestEvidenceEvaluationDto,
    InformationRequestEvidenceFindingCode,
    InformationRequestEvidenceListDto,
    InformationRequestEvidenceRequirementState,
    InformationRequestEvidenceSourceKind,
    InformationRequestEvidenceVersionDto,
} from "../../../models/models.tsx";
import {RequirementEvidenceCommands} from "../requirementEvidenceCommands.ts";
import {RequirementEvidenceContext} from "../RequirementEvidenceContext.ts";
import RequirementEvidencePanel from "./RequirementEvidencePanel.tsx";

const panelId = "information-request-evidence-root-supporting-record";

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
    createdAt: "2026-09-25T08:00:00Z",
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
    createdAt: "2026-09-25T08:00:00Z",
    updatedAt: "2026-09-25T08:00:00Z",
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

const renderPanel = (uploadAvailable = true, malwareScanning = true) =>
    render(
        <RequirementEvidenceContext.Provider value={{uploadAvailable, malwareScanning, commands: commands as RequirementEvidenceCommands}}>
            <RequirementEvidencePanel requestId="request-a"
                                      requirementId="requirement-a"
                                      prompt="Provide the supporting record"
                                      elementId="root-supporting-record"/>
        </RequirementEvidenceContext.Provider>,
    );

const chooseFile = (inputId: string, file: File) =>
    fireEvent.change(document.getElementById(inputId) as HTMLInputElement, {target: {files: [file]}});

describe("RequirementEvidencePanel", () =>
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
        commands.list.mockResolvedValue(listed([artifact()]));
        commands.upload.mockResolvedValue({outcome: "SAVED", result: {}});
        commands.withdraw.mockResolvedValue({outcome: "SAVED", result: {}});
    });

    afterEach(cleanup);

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

    it("uploads a chosen file under the occurrence evidence ETag and shows the refreshed files", async () =>
    {
        renderPanel();
        await screen.findByText("record.pdf");
        const file = new File(["second"], "second.pdf", {type: "application/pdf"});

        chooseFile(`${panelId}-upload-button-input`, file);

        await waitFor(() => expect(commands.list).toHaveBeenCalledTimes(2));
        expect(commands.upload).toHaveBeenCalledWith("request-a", "requirement-a", file, "\"requirement-a:1\"", expect.any(Function));
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

    it("tells the respondent when upload is unavailable and offers no upload or replacement", async () =>
    {
        renderPanel(false);
        await screen.findByText("record.pdf");

        expect(screen.getByText("Evidence upload is not available right now.")).toBeTruthy();
        expect(document.getElementById(`${panelId}-upload-button`)).toBeNull();
        expect(document.getElementById(`${panelId}-artifact-evidence-1-replace`)).toBeNull();
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
});
