/** @vitest-environment jsdom */
import {cleanup, fireEvent, waitFor} from "@testing-library/react";
import {afterEach, beforeAll, beforeEach, describe, expect, it, vi} from "vitest";
import apiClient from "../../../services/apiClient.ts";
import {
    InformationRequestEvidenceCollectionState,
    InformationRequestEvidenceSourceKind,
    InformationRequestResponseDisposition,
    InformationRequestResponseWorkspaceDto,
} from "../../models/models.tsx";
import {
    pendingWorkServer,
    previewNow,
    savesSent,
    startPendingWorkServer,
    workspaceNow
} from "./testing/pendingWorkServer.ts";
import {openRespondentShell, respondentShells, seedVerifiedAccessLink} from "./testing/respondentShells.tsx";

vi.mock("../../../services/apiClient.ts", () => ({default: {get: vi.fn(), post: vi.fn(), patch: vi.fn()}}));
vi.mock("../reusable-answer/useReusableAnswers.ts", () => ({useReusableAnswers: (): null => null}));
vi.mock("./account-link-offer/AccountLinkOffer.tsx", () => ({default: (): null => null}));

const COUNT_FIELD = "information-request-response-field-root-record-count";
const OWNER_FIELD = "information-request-response-field-root-record-owner";
const KEEP_OWNER = "information-request-response-keep-root-record-owner";
const UPLOAD_INPUT = "information-request-evidence-root-supporting-record-PANEL-upload-button-input";

const element = <T extends HTMLElement>(id: string): T => {
    const found = document.getElementById(id);
    if (!found) throw new Error(`Missing element ${id}`);
    return found as T;
};

const submitButton = () => element<HTMLButtonElement>("information-request-submission-submit");

const saveResponses = () => fireEvent.click(element("information-request-response-save"));

const patchedRequirements = (index: number) =>
    ((savesSent()[index].body as { patches: { requirementId: string }[] }).patches).map(patch => patch.requirementId);

const serveWorkspace = (change: (workspace: InformationRequestResponseWorkspaceDto) => void) => {
    const previousGet = vi.mocked(apiClient.get).getMockImplementation();
    vi.mocked(apiClient.get).mockImplementation(async (path, config) => {
        if (String(path).endsWith("/response-workspace")) {
            const workspace = workspaceNow();
            change(workspace);
            return {data: workspace};
        }
        if (previousGet) return previousGet(path, config);
        throw new Error(`Unexpected read ${String(path)}`);
    });
};

const amendedOwnerAnswer = (workspace: InformationRequestResponseWorkspaceDto) => {
    const response = workspace.responses.find(item => item.informationRequestRequirementId === "runtime-3");
    const requirement = workspace.templateVersion.sections.flatMap(section => section.requirements)
        .find(item => item.id === "template-binding-3");
    if (!response || !requirement) throw new Error("The amended answer is missing");
    response.reconfirmationRequired = pendingWorkServer.revision === 1;
    requirement.permittedDispositions = [InformationRequestResponseDisposition.PROVIDED];
};

describe("InformationRequestRespondentWorkspace answer standing on both respondent shells", () => {
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
    });

    beforeEach(() => {
        vi.resetAllMocks();
        window.sessionStorage.clear();
        seedVerifiedAccessLink();
    });

    afterEach(cleanup);

    it.each(respondentShells)("keeps a submitted stage's answer read-only and says why while the open stage stays editable, in the $mode shell", async shell => {
        startPendingWorkServer({staged: true, submittedStages: ["first"]});
        openRespondentShell(shell);

        await waitFor(() => expect(element<HTMLInputElement>(OWNER_FIELD).disabled).toBe(false));
        expect(element<HTMLInputElement>(COUNT_FIELD).disabled).toBe(true);
        expect(element("information-request-response-lock-root-record-count").textContent)
            .toBe("Submitted. Withdraw the submission to change this answer.");
        expect(document.getElementById("information-request-response-lock-root-record-owner")).toBeNull();
    });

    it.each(respondentShells)("saves the open stage's change after a kept change to a newly submitted answer is refused, in the $mode shell", async shell => {
        startPendingWorkServer({staged: true});
        openRespondentShell(shell);
        await waitFor(() => expect(element<HTMLInputElement>(COUNT_FIELD).disabled).toBe(false));

        fireEvent.change(element(COUNT_FIELD), {target: {value: "Five records"}});
        fireEvent.change(element(OWNER_FIELD), {target: {value: "Archive office"}});
        pendingWorkServer.submittedStages = ["first"];
        saveResponses();

        await waitFor(() => expect(element<HTMLInputElement>(COUNT_FIELD).disabled).toBe(true));
        await waitFor(() => expect(element<HTMLInputElement>(COUNT_FIELD).value).toBe("Three records"));
        expect(element<HTMLInputElement>(OWNER_FIELD).value).toBe("Archive office");
        saveResponses();

        await waitFor(() => expect(pendingWorkServer.owner).toBe("Archive office"));
        expect(patchedRequirements(0)).toEqual(["runtime-1", "runtime-3"]);
        expect(patchedRequirements(savesSent().length - 1)).toEqual(["runtime-3"]);
        expect(pendingWorkServer.count).toBe("Three records");
    });

    it.each(respondentShells)("refreshes submission readiness after the last evidence upload, in the $mode shell", async shell => {
        startPendingWorkServer();
        let uploaded = false;
        const previousGet = vi.mocked(apiClient.get).getMockImplementation();
        vi.mocked(apiClient.get).mockImplementation(async (path, config) => {
            if (String(path).endsWith("/submission-preview")) return {
                data: {
                    ...previewNow(),
                    ready: uploaded,
                    canSubmit: uploaded
                }
            };
            if (previousGet) return previousGet(path, config);
            throw new Error(`Unexpected read ${String(path)}`);
        });
        const previousPost = vi.mocked(apiClient.post).getMockImplementation();
        vi.mocked(apiClient.post).mockImplementation(async (path, body, config) => {
            if (!String(path).endsWith("/evidence-artifacts")) {
                if (previousPost) return previousPost(path, body, config);
                throw new Error(`Unexpected command ${String(path)}`);
            }
            uploaded = true;
            return {
                data: {
                    artifact: {
                        id: "artifact-a",
                        requirementId: "runtime-2",
                        artifactKey: "record",
                        collectionState: InformationRequestEvidenceCollectionState.ACTIVE,
                        artifactRevision: 1,
                        etag: "\"artifact:1\"",
                        createdAt: 0,
                        updatedAt: 0,
                        versions: [],
                    },
                    version: {
                        id: "version-a",
                        versionNumber: 1,
                        sourceKind: InformationRequestEvidenceSourceKind.DOCUMENT_VERSION,
                        createdAt: 0,
                        findings: [],
                    },
                    artifactETag: "\"artifact:1\"",
                    evidenceETag: "\"evidence:1\"",
                },
            };
        });
        openRespondentShell(shell);
        const input = await waitFor(() => element<HTMLInputElement>(UPLOAD_INPUT));
        await waitFor(() => expect(submitButton().disabled).toBe(true));

        fireEvent.change(input, {target: {files: [new File(["record"], "record.pdf", {type: "application/pdf"})]}});

        await waitFor(() => expect(uploaded).toBe(true));
        await waitFor(() => expect(submitButton().disabled).toBe(false));
    });

    it.each(respondentShells)("keeps an amended answer as it is with one choice of answer, sending the current answer, in the $mode shell", async shell => {
        startPendingWorkServer();
        serveWorkspace(amendedOwnerAnswer);
        openRespondentShell(shell);
        await waitFor(() => expect(element<HTMLInputElement>(OWNER_FIELD).value).toBe("Records office"));

        saveResponses();
        expect(savesSent()).toHaveLength(0);
        fireEvent.click(element(KEEP_OWNER));
        saveResponses();

        await waitFor(() => expect(savesSent()).toHaveLength(1));
        expect(savesSent()[0].body).toEqual(expect.objectContaining({
            patches: [{requirementId: "runtime-3", disposition: InformationRequestResponseDisposition.PROVIDED}],
        }));
        expect(Object.values(savesSent()[0].headers)).toContain("\"responses:1\"");
        expect(pendingWorkServer.owner).toBe("Records office");
        await waitFor(() => expect(document.getElementById(KEEP_OWNER)).toBeNull());
    });

    it.each(respondentShells)("refuses to keep an amended answer that changed somewhere else, in the $mode shell", async shell => {
        startPendingWorkServer();
        serveWorkspace(amendedOwnerAnswer);
        vi.mocked(apiClient.patch).mockRejectedValue({
            isAxiosError: true,
            message: "Precondition failed",
            response: {
                status: 412,
                data: {reasonCode: "COMMAND_PRECONDITION_STALE", errorMessage: "The answers changed"}
            },
        });
        openRespondentShell(shell);
        await waitFor(() => expect(element<HTMLInputElement>(OWNER_FIELD).value).toBe("Records office"));

        fireEvent.click(element(KEEP_OWNER));
        saveResponses();

        await waitFor(() => expect(element("information-request-response-save-status").textContent)
            .toContain("These answers changed somewhere else"));
        expect(pendingWorkServer.revision).toBe(1);
    });
});
