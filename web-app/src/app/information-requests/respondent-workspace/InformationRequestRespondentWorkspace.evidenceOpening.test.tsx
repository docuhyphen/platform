/** @vitest-environment jsdom */
import {act, cleanup, fireEvent, screen, waitFor} from "@testing-library/react";
import {afterEach, beforeAll, beforeEach, describe, expect, it, vi} from "vitest";
import apiClient from "../../../services/apiClient.ts";
import {InformationRequestEvidenceContentUse} from "../../../services/informationRequestEvidenceService.ts";
import {
    openingPreviewTab,
    PreviewTabDouble,
    previewTabDouble
} from "../requirement-evidence/testing/previewTabDouble.ts";
import {
    InformationRequestEvidenceArtifactDto,
    InformationRequestEvidenceCollectionState,
    InformationRequestEvidenceConformance,
    InformationRequestEvidenceSourceKind,
    InformationRequestResponseWorkspaceDto,
} from "../../models/models.tsx";
import {
    changeRespondent,
    element,
    evidenceSuffix,
    FIRST,
    noFiles,
    openRespondentPage,
    panelId,
    readFor,
    requestPath,
    respondentChanged,
    saveAnswer,
    SECOND,
    server,
} from "./testing/respondentChangeFixture.tsx";

vi.mock("../../../services/apiClient.ts", () => ({default: {get: vi.fn(), post: vi.fn(), patch: vi.fn()}}));

const fileName = "first-respondent-record.pdf";
const objectUrl = "blob:first-respondent-record";
const rowId = `${panelId}-artifact-first-respondent-record`;
const versionPath = `${requestPath}${evidenceSuffix}/first-respondent-artifact/versions/first-respondent-version/`;
const content = new Blob(["%PDF-1.4 record of the first respondent"], {type: "application/pdf"});

const firstRespondentFile: InformationRequestEvidenceArtifactDto = {
    id: "first-respondent-artifact",
    requirementId: "runtime-2",
    artifactKey: "first-respondent-record",
    collectionState: InformationRequestEvidenceCollectionState.ACTIVE,
    artifactRevision: 1,
    etag: "\"first-respondent-artifact:1\"",
    createdByCaller: true,
    changeableByCaller: true,
    createdAt: Date.parse("2026-10-14T08:00:00Z"),
    updatedAt: Date.parse("2026-10-14T08:00:00Z"),
    versions: [{
        id: "first-respondent-version",
        versionNumber: 1,
        sourceKind: InformationRequestEvidenceSourceKind.DOCUMENT_VERSION,
        declaredFileName: fileName,
        declaredMediaType: "application/pdf",
        contentLength: content.size,
        createdByCaller: true,
        createdAt: Date.parse("2026-10-14T08:00:00Z"),
        conformance: InformationRequestEvidenceConformance.CONFORMING,
        findings: [],
    }],
};

const withoutDocumentRequirement = (workspace: InformationRequestResponseWorkspaceDto): InformationRequestResponseWorkspaceDto => ({
    ...workspace,
    templateVersion: {
        ...workspace.templateVersion,
        sections: workspace.templateVersion.sections.map(section => ({
            ...section,
            requirements: section.requirements.filter(requirement => requirement.id !== "requirement-2"),
        })),
    },
    responses: workspace.responses.filter(response => response.informationRequestRequirementId !== "runtime-2"),
});

const opening = {
    askedBy: [] as string[],
    answers: [] as Array<() => void>,
};

const buttonOf = (use: InformationRequestEvidenceContentUse): string => `${rowId}-${use === "preview" ? "preview" : "download"}`;

const startOpening = async (use: InformationRequestEvidenceContentUse) => {
    fireEvent.click(element(buttonOf(use)));
    await waitFor(() => expect(opening.answers).toHaveLength(1));
};

const deliverOpening = () => act(async () => {
    opening.answers.splice(0).forEach(answer => answer());
});

describe("InformationRequestRespondentWorkspace when a file opening answers late", () => {
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
        Element.prototype.scrollIntoView = vi.fn();
        Object.assign(URL, {createObjectURL: vi.fn(), revokeObjectURL: vi.fn()});
    });

    beforeEach(() => {
        vi.resetAllMocks();
        window.sessionStorage.clear();
        Object.assign(server, {
            caller: FIRST,
            callerAtNextSave: SECOND,
            holdReadsOf: null,
            held: [],
            submissionAnswers: []
        });
        Object.assign(opening, {askedBy: [], answers: []});
        downloads = [];
        vi.mocked(URL.createObjectURL).mockReturnValue(objectUrl);
        tab = previewTabDouble();
        windowOpened = openingPreviewTab(tab);
        vi.spyOn(HTMLAnchorElement.prototype, "click").mockImplementation(function (this: HTMLAnchorElement) {
            downloads.push({href: this.href, download: this.download});
        });
        vi.mocked(apiClient.get).mockImplementation(async path => {
            const caller = server.caller;
            const url = String(path);
            if (url.startsWith(versionPath)) {
                opening.askedBy.push(caller);
                return new Promise(resolve => opening.answers.push(() => resolve({data: content})));
            }
            if (url === `${requestPath}${evidenceSuffix}`) {
                return {data: {...noFiles, artifacts: caller === FIRST ? [firstRespondentFile] : []}};
            }
            const read = readFor(caller, url);
            if (caller === SECOND && url === `${requestPath}/response-workspace`) {
                return {data: withoutDocumentRequirement(read.data as InformationRequestResponseWorkspaceDto)};
            }
            return read;
        });
        vi.mocked(apiClient.patch).mockImplementation(async (path, body) => {
            if (path !== `${requestPath}/responses`) throw new Error(`Unexpected command ${path}`);
            if (server.callerAtNextSave) {
                server.caller = server.callerAtNextSave;
                server.callerAtNextSave = null;
            }
            if ((body as { respondentKey?: string }).respondentKey !== server.caller) throw respondentChanged;
            return {data: [], headers: {etag: "\"responses:1\""}};
        });
        vi.mocked(apiClient.post).mockImplementation(async path => {
            throw new Error(`Unexpected command ${path}`);
        });
    });

    afterEach(() => {
        cleanup();
        vi.restoreAllMocks();
    });

    it.each(["preview", "content"] as const)("opens nothing of the earlier respondent's %s once the workspace names another", async use => {
        openRespondentPage();
        await screen.findByText(fileName);
        await startOpening(use);

        await changeRespondent();
        await waitFor(() => expect(document.getElementById(panelId)).toBeNull());
        await deliverOpening();

        expect(opening.askedBy).toEqual([FIRST]);
        expect(URL.createObjectURL).not.toHaveBeenCalled();
        expect(tab.location.replace).not.toHaveBeenCalled();
        expect(tab.close).toHaveBeenCalledTimes(use === "preview" ? 1 : 0);
        expect(downloads).toEqual([]);
        expect(screen.queryByText(fileName)).toBeNull();
    });

    it.each(["preview", "content"] as const)("still opens a late %s while its respondent is shown", async use => {
        openRespondentPage();
        await screen.findByText(fileName);
        await startOpening(use);

        await deliverOpening();

        expect(URL.createObjectURL).toHaveBeenCalledWith(content);
        if (use === "preview") {
            expect(tab.location.replace).toHaveBeenCalledWith(objectUrl);
            expect(downloads).toEqual([]);
        } else {
            expect(downloads).toEqual([{href: objectUrl, download: fileName}]);
            expect(windowOpened).not.toHaveBeenCalled();
        }
    });

    it.each(["preview", "content"] as const)("still opens a late %s after a refresh names the same respondent", async use => {
        server.callerAtNextSave = null;
        openRespondentPage();
        await screen.findByText(fileName);
        await startOpening(use);

        await saveAnswer("Saved answer of the first respondent");
        expect(await screen.findByText("All changes saved.")).toBeTruthy();
        await deliverOpening();

        expect(document.getElementById(panelId)).not.toBeNull();
        expect(URL.createObjectURL).toHaveBeenCalledWith(content);
        if (use === "preview") expect(tab.location.replace).toHaveBeenCalledWith(objectUrl);
        else expect(downloads).toEqual([{href: objectUrl, download: fileName}]);
    });
});
