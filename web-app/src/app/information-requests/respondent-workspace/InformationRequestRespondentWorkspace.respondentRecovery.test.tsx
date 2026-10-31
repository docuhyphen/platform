/** @vitest-environment jsdom */
import {act, cleanup, fireEvent, render, screen, waitFor} from "@testing-library/react";
import {MemoryRouter, Route, Routes, useNavigate} from "react-router-dom";
import {afterEach, beforeAll, beforeEach, describe, expect, it, vi} from "vitest";
import apiClient from "../../../services/apiClient.ts";
import {
    FieldDataClassification,
    FieldValueType,
    InformationRequestContributorRole,
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

vi.mock("../../../services/apiClient.ts", () => ({default: {get: vi.fn(), post: vi.fn(), patch: vi.fn()}}));
vi.mock("../reusable-answer/useReusableAnswers.ts", () => ({useReusableAnswers: () => null}));
vi.mock("../submission/follow-up-section/InformationRequestSubmissionSection.tsx", () => ({default: () => null}));
vi.mock("./account-link-offer/AccountLinkOffer.tsx", () => ({default: () => null}));

const workspacePath = "/no-auth/information-requests/request-a/response-workspace";
const responsesPath = "/no-auth/information-requests/request-a/responses";
const challengePath = "/no-auth/information-request-access-links/challenges";
const sessionPath = "/no-auth/information-request-access-links/sessions";
const LINK_HEADER = "X-Request-Access-Token";
const SESSION_HEADER = "X-Request-Session-Token";

type Delivery = "drop-request" | "end-session" | "deliver";

interface CommandConfig {
    headers: Record<string, string>;
}

const server = {
    liveCredentials: new Set<string>(),
    respondents: {"link-a": "respondent-a", "link-b": "respondent-b"} as Record<string, string>,
    deliveries: [] as Delivery[],
    issued: 0,
};

const ended = {
    isAxiosError: true,
    response: {
        status: 401,
        data: {
            reasonCode: "INFORMATION_REQUEST_ACCESS_SESSION_EXPIRED",
            errorMessage: "Your verified session has ended."
        }
    },
};
const dropped = {isAxiosError: true, message: "Network Error"};

const workspaceFor = (respondentKey: string): InformationRequestResponseWorkspaceDto => ({
    request: {
        id: "request-a",
        exchangeId: "exchange-a",
        templateVersionId: "version-a",
        ownerType: InformationRequestOwnerType.ORGANIZATION,
        state: InformationRequestState.IN_PROGRESS,
        gatesExchangeClosure: true,
        aggregateRevision: 3,
        createdAt: Date.parse("2026-10-13T08:00:00Z"),
        updatedAt: Date.parse("2026-10-13T08:00:00Z"),
        requestETag: "\"request-3\"",
        executionStanding: {kind: InformationRequestExecutionStandingKind.ACTIVE},
        conditionEvaluations: [],
    },
    title: "Periodic records request",
    respondentKey,
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
                id: "requirement-1",
                templateRequirementId: "requirement-1",
                requirementKey: "record-count",
                requirementType: InformationRequestRequirementType.FIELD,
                prompt: "How many records were kept?",
                responseMode: InformationRequestResponseMode.PROVIDE,
                requiredness: InformationRequestRequiredness.REQUIRED,
                contributorRole: InformationRequestContributorRole.CONTRIBUTOR,
                reviewPolicy: InformationRequestReviewPolicy.NOT_REQUIRED,
                collectedFieldDefinitionId: "field-definition-1",
                permittedDispositions: [InformationRequestResponseDisposition.PROVIDED],
                substituteRequirementKeys: [],
                supportingEvidenceRequirementKeys: [],
            }],
        }],
        groups: [],
        conditionRules: [],
        requiredCapabilities: [],
        createdAt: Date.parse("2026-10-01T08:00:00Z"),
    },
    responseETag: "\"responses:1\"",
    occurrences: [],
    schemaAssignment: {
        id: "assignment-a",
        resourceType: "INFORMATION_REQUEST",
        resourceId: "request-a",
        schemaVersionId: "schema-version-a",
        schemaDefinitionId: "schema-a",
        schemaKey: "records",
        displayName: "Records",
        versionNumber: 1,
        assignmentSource: "MANUAL" as never,
        assignedAt: Date.parse("2026-10-13T08:00:00Z"),
        bindings: [{
            id: "binding-1",
            fieldContractId: "field-contract-1",
            fieldDefinitionId: "field-definition-1",
            namespace: "process",
            fieldKey: "record-count",
            label: "Record count",
            valueType: FieldValueType.SHORT_TEXT,
            displayOrder: 0,
            isRequired: true,
            isReadOnly: false,
            visibility: FieldDataClassification.INTERNAL,
            constraints: {},
            options: [],
        }],
        fields: [],
    },
    responses: [{
        informationRequestRequirementId: "runtime-1",
        sourceTemplateRequirementId: "requirement-1",
        sourceTemplateBindingId: "requirement-1",
        occurrencePath: "root",
        disposition: InformationRequestResponseDisposition.NOT_ANSWERED,
        fieldValueSetETag: "\"field-set:1\"",
        fieldValues: [{
            fieldContractId: "field-contract-1",
            schemaFieldBindingId: "binding-1",
            namespace: "process",
            fieldKey: "record-count",
            label: "Record count",
            valueType: FieldValueType.SHORT_TEXT,
            isEmpty: true,
            value: null,
        }],
        responseRevision: 1,
        updatedAt: Date.parse("2026-10-13T08:00:00Z"),
    }],
    supportingEvidenceLinks: [],
    evidenceUploadAvailable: true,
    evidenceMalwareScanning: false,
    requirementsAwaitingStepUp: 0,
    executionStanding: {kind: InformationRequestExecutionStandingKind.ACTIVE},
});

const element = (id: string): HTMLElement => {
    const found = document.getElementById(id);
    if (!found) throw new Error(`Missing element ${id}`);
    return found;
};

const answerField = () => document.querySelector<HTMLInputElement>("#information-request-response-field-root-record-count");

const saves = () => vi.mocked(apiClient.patch).mock.calls
    .filter(([path]) => path === responsesPath)
    .map(([, body, config]) => ({
        body: body as { respondentKey?: string },
        headers: (config as CommandConfig).headers
    }));

const OpenLink = ({token}: { token: string }) => {
    const navigate = useNavigate();
    return (
        <button id={"open-another-link"}
                onClick={() => navigate(`/respond?s=request-a&t=${token}`)}>
            Open another link
        </button>
    );
};

const respondentPage = () => render(
    <MemoryRouter initialEntries={["/respond?s=request-a"]}>
        <Routes>
            <Route path={"/respond"}
                   element={(
                       <>
                           <OpenLink token={"link-b"}/>
                           <InformationRequestRespondentWorkspace accessMode={"no-auth"}/>
                       </>
                   )}/>
        </Routes>
    </MemoryRouter>,
);

const verifyAgain = async () => {
    await waitFor(() => expect(document.getElementById("information-request-contact-proof")).toBeTruthy());
    fireEvent.click(element("information-request-send-code-btn"));
    expect(await screen.findByText("A verification code has been sent.")).toBeTruthy();
    fireEvent.change(element("information-request-access-code-input"), {target: {value: "123456"}});
    fireEvent.click(element("information-request-verify-code-btn"));
    await waitFor(() => expect(answerField()).not.toBeNull());
};

const elapse = (milliseconds: number) => act(async () => {
    await new Promise(resolve => setTimeout(resolve, milliseconds));
});

describe("InformationRequestRespondentWorkspace respondent recovery", () => {
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
        window.sessionStorage.setItem("information-request-access:request-a", "link-a");
        window.sessionStorage.setItem("information-request-session:request-a", "credential-0");
        window.sessionStorage.setItem("information-request-session-id:request-a", "session-0");
        Object.assign(server, {liveCredentials: new Set(["credential-0"]), deliveries: [], issued: 0});
        vi.mocked(apiClient.get).mockImplementation(async (path, config) => {
            if (path !== workspacePath) throw new Error(`Unexpected read ${path}`);
            const headers = (config as CommandConfig).headers;
            if (!server.liveCredentials.has(headers[SESSION_HEADER])) throw ended;
            return {data: workspaceFor(server.respondents[headers[LINK_HEADER]])};
        });
        vi.mocked(apiClient.patch).mockImplementation(async (path, _body, config) => {
            if (path !== responsesPath) throw new Error(`Unexpected command ${path}`);
            const delivery = server.deliveries.shift() ?? "deliver";
            if (delivery === "drop-request") throw dropped;
            const headers = (config as CommandConfig).headers;
            if (delivery === "end-session") server.liveCredentials.delete(headers[SESSION_HEADER]);
            if (!server.liveCredentials.has(headers[SESSION_HEADER])) throw ended;
            return {data: [], headers: {etag: "\"responses:2\""}};
        });
        vi.mocked(apiClient.post).mockImplementation(async path => {
            if (path === challengePath) return {data: undefined};
            if (path !== sessionPath) throw new Error(`Unexpected command ${path}`);
            const credential = `credential-${++server.issued}`;
            server.liveCredentials.add(credential);
            return {data: {sessionId: `session-${server.issued}`, sessionToken: credential}};
        });
    });

    afterEach(cleanup);

    it("sends an interrupted save again under its first key after the respondent verifies a new session", async () => {
        server.deliveries.push("drop-request", "end-session");
        respondentPage();
        await screen.findByRole("heading", {name: "Periodic records request"});
        fireEvent.change(answerField() as HTMLInputElement, {target: {value: "12"}});
        await waitFor(() => expect(saves()).toHaveLength(1), {timeout: 4000});
        expect(await screen.findByText("Network Error")).toBeTruthy();

        fireEvent.click(screen.getByRole("button", {name: "Save responses"}));
        await verifyAgain();

        expect(answerField()?.value).toBe("12");
        await waitFor(() => expect(saves()).toHaveLength(3), {timeout: 4000});
        const [dropped, refused, resent] = saves();
        expect(refused.headers["Idempotency-Key"]).toBe(dropped.headers["Idempotency-Key"]);
        expect(resent.headers["Idempotency-Key"]).toBe(dropped.headers["Idempotency-Key"]);
        expect(resent.headers[SESSION_HEADER]).toBe("credential-1");
        expect(resent.body.respondentKey).toBe("respondent-a");
    }, 15000);

    it("shows and sends none of the earlier recipient's unsent answers after another recipient's link is opened in the tab", async () => {
        respondentPage();
        await screen.findByRole("heading", {name: "Periodic records request"});
        fireEvent.change(answerField() as HTMLInputElement, {target: {value: "12"}});

        fireEvent.click(element("open-another-link"));
        await verifyAgain();

        expect(answerField()?.value).toBe("");
        await elapse(2500);
        expect(saves()).toHaveLength(0);
        expect(window.sessionStorage.getItem("information-request-unsaved:request-a:respondent-a")).toContain("12");
    }, 15000);
});
