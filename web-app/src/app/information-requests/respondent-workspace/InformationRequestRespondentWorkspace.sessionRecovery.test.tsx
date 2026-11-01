/** @vitest-environment jsdom */
import {cleanup, fireEvent, render, screen, waitFor} from "@testing-library/react";
import {MemoryRouter, Route, Routes} from "react-router-dom";
import {afterEach, beforeAll, beforeEach, describe, expect, it, vi} from "vitest";
import apiClient from "../../../services/apiClient.ts";
import {
    InformationRequestContributorRole,
    InformationRequestDto,
    InformationRequestEvidenceArtifactDto,
    InformationRequestEvidenceCollectionState,
    InformationRequestEvidenceConformance,
    InformationRequestEvidenceListDto,
    InformationRequestEvidenceRequirementState,
    InformationRequestEvidenceSourceKind,
    InformationRequestExecutionStandingKind,
    InformationRequestOwnerType,
    InformationRequestRequiredness,
    InformationRequestRequirementType,
    InformationRequestResponseDisposition,
    InformationRequestResponseMode,
    InformationRequestResponseWorkspaceDto,
    InformationRequestReviewPolicy,
    InformationRequestState,
    InformationRequestTemplateStatus,
} from "../../models/models.tsx";
import InformationRequestRespondentWorkspace from "./InformationRequestRespondentWorkspace.tsx";

vi.mock("../../../services/apiClient.ts", () => ({default: {get: vi.fn(), post: vi.fn()}}));
vi.mock("../../../utils/useMediaQuery.ts", () => ({
    BREAKPOINTS: {mobile: 768, tablet: 1024},
    useMediaQuery: () => false,
    useIsMobile: () => false,
    useIsTablet: () => false,
}));
vi.mock("../structured-response-workspace/InformationRequestStructuredResponseWorkspace.tsx", async () => {
    const {default: RequirementEvidencePanel} =
        await import("../requirement-evidence/requirement-evidence-panel/RequirementEvidencePanel.tsx");
    return {
        default: ({request}: { request: InformationRequestDto }) => (
            <RequirementEvidencePanel requestId={request.id}
                                      requirementId={"requirement-a"}
                                      prompt={"Provide the supporting record"}
                                      elementId={"root-supporting-record"}/>
        ),
    };
});
vi.mock("../reusable-answer/useReusableAnswers.ts", () => ({useReusableAnswers: (): null => null}));
vi.mock("../submission/follow-up-section/InformationRequestSubmissionSection.tsx", () => ({default: (): null => null}));
vi.mock("./account-link-offer/AccountLinkOffer.tsx", () => ({default: (): null => null}));

const panelId = "information-request-evidence-root-supporting-record-PANEL";
const workspacePath = "/no-auth/information-requests/request-a/response-workspace";
const evidencePath = "/no-auth/information-requests/request-a/requirements/requirement-a/evidence-artifacts";
const challengePath = "/no-auth/information-request-access-links/challenges";
const sessionPath = "/no-auth/information-request-access-links/sessions";
const EXPIRED_MESSAGE = "Your verified session has ended.";

const workspace: InformationRequestResponseWorkspaceDto = {
    request: {
        id: "request-a",
        exchangeId: "exchange-a",
        templateVersionId: "version-a",
        ownerType: InformationRequestOwnerType.ORGANIZATION,
        state: InformationRequestState.IN_PROGRESS,
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
                templateRequirementId: "requirement-template-a",
                requirementKey: "supporting-record",
                requirementType: InformationRequestRequirementType.DOCUMENT,
                prompt: "Provide the supporting record",
                responseMode: InformationRequestResponseMode.PROVIDE,
                requiredness: InformationRequestRequiredness.REQUIRED,
                contributorRole: InformationRequestContributorRole.CONTRIBUTOR,
                reviewPolicy: InformationRequestReviewPolicy.NOT_REQUIRED,
                permittedDispositions: [InformationRequestResponseDisposition.PROVIDED],
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
};

const stored: InformationRequestEvidenceArtifactDto = {
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
    versions: [{
        id: "version-a",
        versionNumber: 1,
        sourceKind: InformationRequestEvidenceSourceKind.DOCUMENT_VERSION,
        declaredFileName: "record.pdf",
        declaredMediaType: "application/pdf",
        contentLength: 6,
        createdByCaller: true,
        createdAt: Date.parse("2026-09-25T08:00:00Z"),
        conformance: InformationRequestEvidenceConformance.PENDING,
        findings: [],
    }],
};

const listed = (artifacts: InformationRequestEvidenceArtifactDto[]): InformationRequestEvidenceListDto => ({
    requirementId: "requirement-a",
    evidenceETag: `"requirement-a:${artifacts.length}"`,
    artifacts,
    evaluation: {
        state: InformationRequestEvidenceRequirementState.PENDING_ASSESSMENT,
        completesWork: false,
        satisfiedBySubstitute: false,
        findings: [],
    },
});

const expired = {
    isAxiosError: true,
    response: {
        status: 401,
        data: {reasonCode: "INFORMATION_REQUEST_ACCESS_SESSION_EXPIRED", errorMessage: EXPIRED_MESSAGE}
    },
};

interface CommandConfig {
    headers: Record<string, string>;
}

const transport = {sessionValid: true, expireOnUpload: true, uploaded: false};

const element = (id: string): HTMLElement => {
    const found = document.getElementById(id);
    if (!found) throw new Error(`Missing element ${id}`);
    return found;
};

const uploadCalls = () => vi.mocked(apiClient.post).mock.calls
    .filter(([path]) => path === evidencePath)
    .map(([, form, config]) => ({form: form as FormData, headers: (config as CommandConfig).headers}));

describe("InformationRequestRespondentWorkspace session recovery", () => {
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
        window.sessionStorage.setItem("information-request-access:request-a", "bootstrap-a");
        window.sessionStorage.setItem("information-request-session:request-a", "session-secret-a");
        window.sessionStorage.setItem("information-request-session-id:request-a", "session-a");
        Object.assign(transport, {sessionValid: true, expireOnUpload: true, uploaded: false});
        vi.mocked(apiClient.get).mockImplementation(async path => {
            if (!transport.sessionValid) throw expired;
            if (path === workspacePath) return {data: workspace};
            if (path === evidencePath) return {data: listed(transport.uploaded ? [stored] : [])};
            throw new Error(`Unexpected read ${path}`);
        });
        vi.mocked(apiClient.post).mockImplementation(async path => {
            if (path === evidencePath) {
                if (!transport.sessionValid || transport.expireOnUpload) {
                    transport.expireOnUpload = false;
                    transport.sessionValid = false;
                    throw expired;
                }
                transport.uploaded = true;
                return {data: {}};
            }
            if (path === challengePath) return {data: undefined};
            if (path === sessionPath) {
                transport.sessionValid = true;
                return {data: {sessionId: "session-b", sessionToken: "fresh-secret"}};
            }
            throw new Error(`Unexpected command ${path}`);
        });
    });

    afterEach(cleanup);

    it("returns an expired upload to contact verification, keeps the file, and sends it once after fresh proof", async () => {
        render(
            <MemoryRouter initialEntries={["/respond?s=request-a"]}>
                <Routes>
                    <Route path={"/respond"}
                           element={<InformationRequestRespondentWorkspace accessMode={"no-auth"}/>}/>
                </Routes>
            </MemoryRouter>,
        );
        expect(await screen.findByText("No files yet.")).toBeTruthy();
        const file = new File(["record"], "record.pdf", {type: "application/pdf"});

        fireEvent.change(element(`${panelId}-upload-button-input`), {target: {files: [file]}});

        await waitFor(() => expect(document.getElementById("information-request-contact-proof")).toBeTruthy());
        expect(document.getElementById(panelId)).toBeNull();
        expect(element("information-request-respondent-error-body").textContent).toBe(EXPIRED_MESSAGE);
        expect(window.sessionStorage.getItem("information-request-session:request-a")).toBe("");

        fireEvent.click(element("information-request-send-code-btn"));
        expect(await screen.findByText("A verification code has been sent.")).toBeTruthy();
        fireEvent.change(element("information-request-access-code-input"), {target: {value: "123456"}});
        fireEvent.click(element("information-request-verify-code-btn"));

        await waitFor(() => expect(document.getElementById(`${panelId}-kept-1-retry`)).toBeTruthy());
        expect(screen.getByText("record.pdf was not sent. It is kept here so you can try again.")).toBeTruthy();
        expect(uploadCalls()).toHaveLength(1);

        fireEvent.click(element(`${panelId}-kept-1-retry`));

        await waitFor(() => expect(document.getElementById(`${panelId}-artifact-evidence-1`)).toBeTruthy());
        expect(document.getElementById(`${panelId}-kept`)).toBeNull();
        const [refused, sent] = uploadCalls();
        expect(uploadCalls()).toHaveLength(2);
        expect((sent.form.get("file") as File).name).toBe("record.pdf");
        expect(refused.headers["X-Request-Session-Token"]).toBe("session-secret-a");
        expect(sent.headers["X-Request-Session-Token"]).toBe("fresh-secret");
        expect(sent.headers["Idempotency-Key"]).toBe(refused.headers["Idempotency-Key"]);
        expect(window.sessionStorage.getItem("information-request-session:request-a")).toBe("fresh-secret");
    });
});
