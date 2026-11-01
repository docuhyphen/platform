/** @vitest-environment jsdom */
import {cleanup, fireEvent, render, screen} from "@testing-library/react";
import {MemoryRouter, Route, Routes} from "react-router-dom";
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
    SchemaAssignmentSource,
} from "../../models/models.tsx";
import InformationRequestRespondentWorkspace from "./InformationRequestRespondentWorkspace.tsx";

vi.mock("../../../services/apiClient.ts", () => ({default: {get: vi.fn(), post: vi.fn(), patch: vi.fn()}}));
vi.mock("../reusable-answer/useReusableAnswers.ts", () => ({useReusableAnswers: (): null => null}));
vi.mock("../submission/follow-up-section/InformationRequestSubmissionSection.tsx", () => ({
    default: ({onChanged}: { onChanged: () => void }) => (
        <button id={"respondent-refresh-double"}
                onClick={onChanged}>
            Refresh the request
        </button>
    ),
}));

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
        createdAt: Date.parse("2026-09-20T08:00:00Z"),
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
        assignmentSource: SchemaAssignmentSource.MANUAL,
        assignedAt: Date.parse("2026-09-27T08:00:00Z"),
        bindings: [{
            id: "binding-1",
            fieldContractId: "field-contract-1",
            fieldDefinitionId: "field-definition-1",
            namespace: "process",
            fieldKey: "record-count",
            label: "Record count",
            valueType: FieldValueType.SHORT_TEXT,
            displayOrder: 0,
            section: "Records",
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
        updatedAt: Date.parse("2026-09-27T08:00:00Z"),
    }],
    supportingEvidenceLinks: [],
    evidenceUploadAvailable: true,
    evidenceMalwareScanning: false,
    requirementsAwaitingStepUp: 0,
    executionStanding: {kind: InformationRequestExecutionStandingKind.ACTIVE},
};

const refused = {
    isAxiosError: true,
    response: {
        status: 403,
        data: {reasonCode: "INFORMATION_REQUEST_FORBIDDEN", errorMessage: "Request access was removed"}
    },
};

const answerField = (): HTMLInputElement | null =>
    document.querySelector<HTMLInputElement>("#information-request-response-field-root-record-count");

const renderSignedIn = () => render(
    <MemoryRouter initialEntries={["/information-requests/request-a/respond"]}>
        <Routes>
            <Route path={"/information-requests/:requestId/respond"}
                   element={<InformationRequestRespondentWorkspace accessMode={"authenticated"}/>}/>
        </Routes>
    </MemoryRouter>,
);

describe("InformationRequestRespondentWorkspace after a refused read", () => {
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
        vi.mocked(apiClient.patch).mockReturnValue(new Promise(() => undefined));
    });

    afterEach(cleanup);

    it("hides the response and its commands once a refresh is refused, keeps unsaved answers, and shows them after Try again", async () => {
        vi.mocked(apiClient.get)
            .mockResolvedValueOnce({data: workspace})
            .mockRejectedValueOnce(refused)
            .mockResolvedValueOnce({data: workspace});
        renderSignedIn();
        await screen.findByRole("heading", {name: "Periodic records request"});
        fireEvent.change(answerField() as HTMLInputElement, {target: {value: "12"}});

        fireEvent.click(screen.getByRole("button", {name: "Refresh the request"}));

        expect(await screen.findByText("Request access was removed")).toBeTruthy();
        expect(answerField()).toBeNull();
        expect(screen.queryByRole("button", {name: "Refresh the request"})).toBeNull();
        expect(screen.queryByRole("navigation", {name: "Sections"})).toBeNull();
        expect(screen.getByRole("heading", {name: "Information Request"})).toBeTruthy();
        expect(window.sessionStorage.getItem("information-request-unsaved:request-a:respondent-a")).toContain("12");

        fireEvent.click(screen.getByRole("button", {name: "Try again"}));

        await screen.findByRole("heading", {name: "Periodic records request"});
        expect(answerField()?.value).toBe("12");
        expect(screen.queryByText("Request access was removed")).toBeNull();
        expect(apiClient.get).toHaveBeenCalledTimes(3);
    });
});
