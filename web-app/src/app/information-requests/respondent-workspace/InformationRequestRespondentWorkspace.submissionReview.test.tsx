/** @vitest-environment jsdom */
import {submissionSnapshotItems} from "./testing/submissionSnapshotFixture.ts";
import {cleanup, fireEvent, waitFor} from "@testing-library/react";
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
    InformationRequestResponseDto,
    InformationRequestResponseMode,
    InformationRequestResponseWorkspaceDto,
    InformationRequestReviewPolicy,
    InformationRequestState,
    InformationRequestSubmissionMode,
    InformationRequestSubmissionPreviewDto,
    InformationRequestSubmissionStageOrdering,
    InformationRequestTemplateRequirementDto,
    InformationRequestTemplateStatus,
    SchemaFieldBindingDto,
} from "../../models/models.tsx";
import {openRespondentShell, respondentShells, seedVerifiedAccessLink} from "./testing/respondentShells.tsx";

vi.mock("../../../services/apiClient.ts", () => ({default: {get: vi.fn(), post: vi.fn(), patch: vi.fn()}}));
vi.mock("../reusable-answer/useReusableAnswers.ts", () => ({useReusableAnswers: (): null => null}));
vi.mock("./account-link-offer/AccountLinkOffer.tsx", () => ({default: (): null => null}));

const at = Date.parse("2026-10-20T08:00:00Z");

const binding = (contract: number, fieldKey: string, label: string): SchemaFieldBindingDto => ({
    id: `schema-binding-${contract}`,
    fieldContractId: `field-contract-${contract}`,
    fieldDefinitionId: `field-definition-${contract}`,
    namespace: "process",
    fieldKey,
    label,
    valueType: FieldValueType.SHORT_TEXT,
    displayOrder: contract,
    isRequired: false,
    isReadOnly: false,
    visibility: FieldDataClassification.INTERNAL,
    constraints: {},
    options: [],
});

const bindings = [binding(1, "record-count", "Record count"), binding(2, "record-owner", "Record owner")];

const requirement = (index: number, requirementKey: string, prompt: string, contract?: number): InformationRequestTemplateRequirementDto => ({
    id: `template-binding-${index}`,
    templateRequirementId: `template-requirement-${index}`,
    requirementKey,
    requirementType: contract ? InformationRequestRequirementType.FIELD : InformationRequestRequirementType.DOCUMENT,
    prompt,
    responseMode: InformationRequestResponseMode.PROVIDE,
    requiredness: InformationRequestRequiredness.OPTIONAL,
    contributorRole: InformationRequestContributorRole.CONTRIBUTOR,
    reviewPolicy: InformationRequestReviewPolicy.NOT_REQUIRED,
    collectedFieldDefinitionId: contract ? `field-definition-${contract}` : undefined,
    permittedDispositions: [
        InformationRequestResponseDisposition.PROVIDED,
        InformationRequestResponseDisposition.PARTIALLY_PROVIDED,
    ],
    substituteRequirementKeys: [],
    supportingEvidenceRequirementKeys: [],
});

const requirements = [
    requirement(1, "record-count", "How many records were kept?", 1),
    requirement(2, "supporting-record", "Provide the supporting record"),
    requirement(3, "record-owner", "Who keeps the records?", 2),
];

const answer = (
    index: number,
    disposition: InformationRequestResponseDisposition,
    narrative: string,
    value?: { collected: SchemaFieldBindingDto; text: string },
): InformationRequestResponseDto => ({
    informationRequestRequirementId: `runtime-${index}`,
    sourceTemplateRequirementId: `template-requirement-${index}`,
    sourceTemplateBindingId: `template-binding-${index}`,
    occurrencePath: "root",
    disposition,
    narrative,
    fieldValueSetETag: value ? `"field-set:${index}"` : undefined,
    fieldValues: value ? [{
        fieldContractId: value.collected.fieldContractId,
        schemaFieldBindingId: value.collected.id,
        namespace: value.collected.namespace,
        fieldKey: value.collected.fieldKey,
        label: value.collected.label,
        valueType: value.collected.valueType,
        isEmpty: false,
        value: value.text,
    }] : [],
    responseRevision: 1,
    updatedAt: at,
});

const workspace: InformationRequestResponseWorkspaceDto = {
    request: {
        id: "request-a",
        exchangeId: "exchange-a",
        templateVersionId: "version-a",
        ownerType: InformationRequestOwnerType.ORGANIZATION,
        state: InformationRequestState.IN_PROGRESS,
        gatesExchangeClosure: true,
        aggregateRevision: 1,
        createdAt: at,
        updatedAt: at,
        requestETag: "\"request-1\"",
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
        sections: [{id: "section-a", sectionKey: "records", title: "Records", requirements}],
        groups: [],
        conditionRules: [],
        requiredCapabilities: [],
        createdAt: at,
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
        assignedAt: Date.parse("2026-10-20T08:00:00Z"),
        bindings,
        fields: [],
    },
    responses: [
        answer(1, InformationRequestResponseDisposition.PARTIALLY_PROVIDED, "Two records remain unavailable",
            {collected: bindings[0], text: "Three records"}),
        {
            ...answer(2, InformationRequestResponseDisposition.PARTIALLY_PROVIDED, "One file follows next week"),
            evidenceFileCount: 1
        },
        answer(3, InformationRequestResponseDisposition.PROVIDED, "Named by the keeper",
            {collected: bindings[1], text: "Records office"}),
    ],
    supportingEvidenceLinks: [],
    evidenceUploadAvailable: true,
    evidenceMalwareScanning: false,
    requirementsAwaitingStepUp: 0,
    executionStanding: {kind: InformationRequestExecutionStandingKind.ACTIVE},
};

const preview: InformationRequestSubmissionPreviewDto = {
    informationRequestId: "request-a",
    submissionMode: InformationRequestSubmissionMode.WHOLE_PACKAGE,
    submissionStageOrdering: InformationRequestSubmissionStageOrdering.ANY_ORDER,
    scopeItems: submissionSnapshotItems(workspace),
    submissionETag: "\"submission:1\"",
    ready: true,
    problems: [],
    undisclosedProblemCount: 0,
    groupProblems: [],
    attestations: [],
    stages: [],
    canSubmit: true,
    correctionOpen: false,
    packages: [],
};

const element = (id: string): HTMLElement => {
    const found = document.getElementById(id);
    if (!found) throw new Error(`Missing element ${id}`);
    return found;
};

const shownAnswer = (index: number) => element(`information-request-submission-review-runtime-${index}-answer`).textContent;

describe("InformationRequestRespondentWorkspace review before submitting on both respondent shells", () => {
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
        vi.mocked(apiClient.get).mockImplementation(async path => {
            const read = String(path);
            if (read.endsWith("/response-workspace")) return {data: workspace};
            if (read.endsWith("/submission-preview")) return {data: preview};
            if (read.endsWith("/amendments") || read.endsWith("/carry-forwards") || read.endsWith("/review-results")) return {data: []};
            if (read.endsWith("/evidence-artifacts")) return {
                data: {
                    requirementId: "runtime-2",
                    evidenceETag: "\"evidence:0\"",
                    artifacts: []
                }
            };
            throw new Error(`Unexpected read ${read}`);
        });
    });

    afterEach(cleanup);

    it.each(respondentShells)("states each answer with its choice, reason and note before submitting, in the $mode shell", async shell => {
        openRespondentShell(shell);

        await waitFor(() => expect((element("information-request-submission-submit") as HTMLButtonElement).disabled).toBe(false));
        fireEvent.click(element("information-request-submission-submit"));

        await waitFor(() => expect(document.getElementById("information-request-submission-review")).not.toBeNull());
        expect(shownAnswer(1)).toBe("Partly provided: Three records. Reason: Two records remain unavailable");
        expect(shownAnswer(2)).toBe("Partly provided: 1 file attached. Reason: One file follows next week");
        expect(shownAnswer(3)).toBe("Records office. Note: Named by the keeper");
        expect(vi.mocked(apiClient.get).mock.calls.some(([path]) => path === `${shell.base}/submission-preview`)).toBe(true);
    });
});
