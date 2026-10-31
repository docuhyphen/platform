/** @vitest-environment jsdom */
import {act, cleanup, fireEvent, render, screen, waitFor} from "@testing-library/react";
import {afterEach, beforeAll, beforeEach, describe, expect, it, vi} from "vitest";
import {
    InformationRequestEvidenceArtifactDto,
    InformationRequestEvidenceCollectionState,
    InformationRequestEvidenceListDto,
    InformationRequestEvidenceSourceKind,
    InformationRequestEvidenceVersionDto,
} from "../../../models/models.tsx";
import {InformationRequestEvidenceContentUse} from "../../../../services/informationRequestEvidenceService.ts";
import {RequirementEvidenceCommands} from "../requirementEvidenceCommands.ts";
import {RequirementEvidenceContext} from "../RequirementEvidenceContext.ts";
import RequirementEvidencePanel from "./RequirementEvidencePanel.tsx";
import {openingPreviewTab, PreviewTabDouble, previewTabDouble} from "../testing/previewTabDouble.ts";

const panelId = "information-request-evidence-root-supporting-record-PANEL";
const rowId = `${panelId}-artifact-record`;
const objectUrl = "blob:supporting-record";
const REFUSAL = {
    reasonCode: "INFORMATION_REQUEST_CONFIDENTIALITY_DENIED",
    errorMessage: "Verification is no longer current"
};
const uses: InformationRequestEvidenceContentUse[] = ["content", "preview"];

const commands = {list: vi.fn(), upload: vi.fn(), replace: vi.fn(), withdraw: vi.fn(), open: vi.fn()};

const version = (overrides: Partial<InformationRequestEvidenceVersionDto> = {}): InformationRequestEvidenceVersionDto => ({
    id: "version-a",
    versionNumber: 1,
    sourceKind: InformationRequestEvidenceSourceKind.DOCUMENT_VERSION,
    declaredFileName: "record.pdf",
    declaredMediaType: "application/pdf",
    contentLength: 2048,
    createdByCaller: true,
    createdAt: Date.parse("2026-10-14T08:00:00Z"),
    findings: [],
    ...overrides,
});

const recordFile = (overrides: Partial<InformationRequestEvidenceArtifactDto> = {}): InformationRequestEvidenceArtifactDto => ({
    id: "artifact-a",
    requirementId: "requirement-a",
    artifactKey: "record",
    collectionState: InformationRequestEvidenceCollectionState.ACTIVE,
    artifactRevision: 1,
    etag: "\"artifact-a:1\"",
    createdByCaller: true,
    changeableByCaller: true,
    createdAt: Date.parse("2026-10-14T08:00:00Z"),
    updatedAt: Date.parse("2026-10-14T08:00:00Z"),
    versions: [version()],
    ...overrides,
});

const additionalFile = recordFile({
    id: "artifact-c",
    artifactKey: "additional",
    versions: [version({id: "version-c", declaredFileName: "additional.pdf"})],
});

const listed = (artifacts: InformationRequestEvidenceArtifactDto[]): InformationRequestEvidenceListDto => ({
    requirementId: "requirement-a",
    evidenceETag: "\"requirement-a:1\"",
    artifacts,
});

type Read = InformationRequestEvidenceListDto | typeof REFUSAL;

const reads: Read[] = [];
const pendingOpenings: Array<(content: Blob) => void> = [];
const content = new Blob(["%PDF-1.4 supporting record"], {type: "application/pdf"});

const renderPanel = () => render(
    <RequirementEvidenceContext.Provider
        value={{uploadAvailable: true, malwareScanning: false, commands: commands as RequirementEvidenceCommands}}>
        <RequirementEvidencePanel requestId="request-a"
                                  requirementId="requirement-a"
                                  prompt="Provide the supporting record"
                                  elementId="root-supporting-record"/>
    </RequirementEvidenceContext.Provider>,
);

const select = async (use: InformationRequestEvidenceContentUse) => {
    const asked = commands.open.mock.calls.length;
    fireEvent.click(document.getElementById(`${rowId}-${use === "preview" ? "preview" : "download"}`) as HTMLElement);
    await waitFor(() => expect(commands.open).toHaveBeenCalledTimes(asked + 1));
};

const chooseFile = (inputId: string, file: File) =>
    fireEvent.change(document.getElementById(inputId) as HTMLInputElement, {target: {files: [file]}});

const uploadAnother = () => chooseFile(`${panelId}-upload-button-input`, new File(["additional"], "additional.pdf"));

const deliverOpenings = () => act(async () => {
    pendingOpenings.splice(0).forEach(deliver => deliver(content));
});

describe("RequirementEvidencePanel when a file opening answers late", () => {
    let windowOpened: ReturnType<typeof vi.spyOn>;
    let tab: PreviewTabDouble;
    let downloads: { href: string; download: string }[];

    beforeAll(() => {
        vi.stubGlobal("ResizeObserver", class {
            observe() {
            }

            unobserve() {
            }

            disconnect() {
            }
        });
        Object.assign(URL, {createObjectURL: vi.fn(), revokeObjectURL: vi.fn()});
    });

    beforeEach(() => {
        vi.resetAllMocks();
        reads.splice(0, reads.length, listed([recordFile()]));
        pendingOpenings.splice(0);
        downloads = [];
        commands.list.mockImplementation(async () => {
            const read = reads.length > 1 ? reads.shift() as Read : reads[0];
            if ("reasonCode" in read) throw read;
            return read;
        });
        commands.upload.mockResolvedValue({outcome: "SAVED", result: {}});
        commands.replace.mockResolvedValue({outcome: "SAVED", result: {}});
        commands.open.mockImplementation(() => new Promise<Blob>(resolve => pendingOpenings.push(resolve)));
        vi.mocked(URL.createObjectURL).mockReturnValue(objectUrl);
        tab = previewTabDouble();
        windowOpened = openingPreviewTab(tab);
        vi.spyOn(HTMLAnchorElement.prototype, "click").mockImplementation(function (this: HTMLAnchorElement) {
            downloads.push({href: this.href, download: this.download});
        });
    });

    afterEach(() => {
        cleanup();
        vi.restoreAllMocks();
    });

    const expectNothingOpened = () => {
        expect(URL.createObjectURL).not.toHaveBeenCalled();
        expect(tab.location.replace).not.toHaveBeenCalled();
        expect(tab.close).toHaveBeenCalledTimes(windowOpened.mock.calls.length);
        expect(downloads).toEqual([]);
    };

    const expectOpened = (use: InformationRequestEvidenceContentUse) => {
        expect(URL.createObjectURL).toHaveBeenCalledTimes(1);
        expect(URL.createObjectURL).toHaveBeenCalledWith(content);
        if (use === "preview") expect(tab.location.replace).toHaveBeenCalledWith(objectUrl);
        else expect(downloads).toEqual([{href: objectUrl, download: "record.pdf"}]);
    };

    it.each(uses)("opens nothing of a late %s once a refused reread hides its file", async use => {
        reads.push(REFUSAL);
        renderPanel();
        await screen.findByText("record.pdf");
        await select(use);

        uploadAnother();
        await screen.findByText(REFUSAL.errorMessage);
        expect(screen.queryByText("record.pdf")).toBeNull();
        await deliverOpenings();

        expectNothingOpened();
    });

    it.each(uses)("opens a late %s once Try again shows its file again", async use => {
        reads.push(REFUSAL, listed([recordFile()]));
        renderPanel();
        await screen.findByText("record.pdf");
        await select(use);
        uploadAnother();
        await screen.findByText(REFUSAL.errorMessage);

        fireEvent.click(screen.getByRole("button", {name: "Try again"}));
        await screen.findByText("record.pdf");
        await deliverOpenings();

        expectOpened(use);
    });

    it.each(uses)("still opens a late %s when the reread after an upload still shows its file", async use => {
        reads.push(listed([recordFile(), additionalFile]));
        renderPanel();
        await screen.findByText("record.pdf");
        await select(use);

        uploadAnother();
        await screen.findByText("additional.pdf");
        await deliverOpenings();

        expectOpened(use);
    });

    it("opens nothing of a late download once the reread shows a newer version of its file", async () => {
        reads.push(listed([recordFile({
            artifactRevision: 2,
            versions: [version(), version({
                id: "version-b",
                versionNumber: 2,
                declaredFileName: "record-corrected.pdf"
            })],
        })]));
        renderPanel();
        await screen.findByText("record.pdf");
        await select("content");

        chooseFile(`${rowId}-replace-input`, new File(["corrected"], "record-corrected.pdf"));
        await screen.findByText("record-corrected.pdf");
        await deliverOpenings();

        expectNothingOpened();
    });

    it("opens nothing of a late download once the reread shows another file in its place", async () => {
        reads.push(listed([recordFile({
            id: "artifact-b",
            etag: "\"artifact-b:1\"",
            versions: [version({id: "version-b", contentLength: 4096})],
        })]));
        renderPanel();
        await screen.findByText("record.pdf");
        await select("content");

        uploadAnother();
        await waitFor(() => expect(document.getElementById(`${rowId}-meta`)?.textContent).toBe("Version 1, 4 KiB"));
        await deliverOpenings();

        expectNothingOpened();
    });

    it.each(uses)("opens nothing of a late %s once its panel is closed", async use => {
        const panel = renderPanel();
        await screen.findByText("record.pdf");
        await select(use);

        panel.unmount();
        await deliverOpenings();

        expectNothingOpened();
    });
});
