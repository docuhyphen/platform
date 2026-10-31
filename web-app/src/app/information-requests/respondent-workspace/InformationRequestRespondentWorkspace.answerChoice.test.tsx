/** @vitest-environment jsdom */
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
    InformationRequestResponsePatchRequest,
    InformationRequestResponseWorkspaceDto,
    InformationRequestReviewPolicy,
    InformationRequestState,
    InformationRequestTemplateRequirementDto,
    InformationRequestTemplateStatus,
    SchemaFieldBindingDto,
} from "../../models/models.tsx";
import {
    openRespondentShell,
    RespondentShell,
    respondentShells,
    seedVerifiedAccessLink,
} from "./testing/respondentShells.tsx";

vi.mock("../../../services/apiClient.ts", () => ({default: {get: vi.fn(), post: vi.fn(), patch: vi.fn()}}));
vi.mock("../reusable-answer/useReusableAnswers.ts", () => ({useReusableAnswers: () => null}));
vi.mock("../submission/follow-up-section/InformationRequestSubmissionSection.tsx", () => ({default: () => null}));
vi.mock("./account-link-offer/AccountLinkOffer.tsx", () => ({default: () => null}));

interface SentCommand {
    body: { patches: InformationRequestResponsePatchRequest[] };
    headers: Record<string, string>;
}

const DOCUMENT_REASON = "information-request-response-narrative-root-supporting-record";
const KEPT_ELSEWHERE = "The record is held by another office";

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

const bindings = [
    binding(1, "record-summary", "Record summary"),
    binding(2, "record-count", "Record count"),
    binding(3, "record-owner", "Record owner"),
];

const requirement = (
    index: number,
    requirementKey: string,
    permittedDispositions: InformationRequestResponseDisposition[],
    contract?: number,
): InformationRequestTemplateRequirementDto => ({
    id: `template-binding-${index}`,
    templateRequirementId: `template-requirement-${index}`,
    requirementKey,
    requirementType: contract ? InformationRequestRequirementType.FIELD : InformationRequestRequirementType.DOCUMENT,
    prompt: `Answer ${requirementKey.replace(/-/g, " ")}`,
    responseMode: InformationRequestResponseMode.PROVIDE,
    requiredness: InformationRequestRequiredness.OPTIONAL,
    contributorRole: InformationRequestContributorRole.CONTRIBUTOR,
    reviewPolicy: InformationRequestReviewPolicy.NOT_REQUIRED,
    collectedFieldDefinitionId: contract ? `field-definition-${contract}` : undefined,
    permittedDispositions,
    substituteRequirementKeys: [],
    supportingEvidenceRequirementKeys: [],
});

const requirements = [
    requirement(1, "supporting-record", [
        InformationRequestResponseDisposition.PROVIDED,
        InformationRequestResponseDisposition.NOT_APPLICABLE,
        InformationRequestResponseDisposition.UNAVAILABLE,
    ]),
    requirement(2, "record-summary", [
        InformationRequestResponseDisposition.PROVIDED,
        InformationRequestResponseDisposition.PARTIALLY_PROVIDED,
    ], 1),
    requirement(3, "record-count", [InformationRequestResponseDisposition.PROVIDED], 2),
    requirement(4, "record-owner", [
        InformationRequestResponseDisposition.PROVIDED,
        InformationRequestResponseDisposition.NOT_APPLICABLE,
    ], 3),
];

const documentAnswer = (
    disposition: InformationRequestResponseDisposition,
    narrative: string | null,
): InformationRequestResponseDto => ({
    informationRequestRequirementId: "runtime-1",
    sourceTemplateRequirementId: "template-requirement-1",
    sourceTemplateBindingId: "template-binding-1",
    occurrencePath: "root",
    disposition,
    narrative,
    fieldValues: [],
    responseRevision: 1,
    updatedAt: Date.parse("2026-10-20T08:00:00Z"),
});

const fieldAnswer = (
    index: number,
    collected: SchemaFieldBindingDto,
    disposition: InformationRequestResponseDisposition,
    narrative: string | null,
    value: string | null,
): InformationRequestResponseDto => ({
    ...documentAnswer(disposition, narrative),
    informationRequestRequirementId: `runtime-${index}`,
    sourceTemplateRequirementId: `template-requirement-${index}`,
    sourceTemplateBindingId: `template-binding-${index}`,
    fieldValueSetETag: `"field-set:${index}"`,
    fieldValues: [{
        fieldContractId: collected.fieldContractId,
        schemaFieldBindingId: collected.id,
        namespace: collected.namespace,
        fieldKey: collected.fieldKey,
        label: collected.label,
        valueType: collected.valueType,
        isEmpty: value === null,
        value,
    }],
});

const savedAnswers = (
    document = documentAnswer(InformationRequestResponseDisposition.UNAVAILABLE, "The record is held elsewhere"),
) => [
    document,
    fieldAnswer(2, bindings[0], InformationRequestResponseDisposition.PARTIALLY_PROVIDED, "The rest follows", "First portion"),
    fieldAnswer(3, bindings[1], InformationRequestResponseDisposition.NOT_ANSWERED, null, null),
    fieldAnswer(4, bindings[2], InformationRequestResponseDisposition.NOT_APPLICABLE, "No owner is named", null),
];

const workspaceAt = (revision: number, responses: InformationRequestResponseDto[]): InformationRequestResponseWorkspaceDto => ({
    request: {
        id: "request-a",
        exchangeId: "exchange-a",
        templateVersionId: "version-a",
        ownerType: InformationRequestOwnerType.ORGANIZATION,
        state: InformationRequestState.IN_PROGRESS,
        gatesExchangeClosure: true,
        aggregateRevision: revision,
        createdAt: Date.parse("2026-10-20T08:00:00Z"),
        updatedAt: Date.parse("2026-10-20T08:00:00Z"),
        requestETag: `"request-${revision}"`,
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
        createdAt: Date.parse("2026-10-01T08:00:00Z"),
    },
    responseETag: `"responses:${revision}"`,
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
    responses,
    supportingEvidenceLinks: [],
    evidenceUploadAvailable: true,
    evidenceMalwareScanning: false,
    requirementsAwaitingStepUp: 0,
    executionStanding: {kind: InformationRequestExecutionStandingKind.ACTIVE},
});

const server = {reads: [] as InformationRequestResponseWorkspaceDto[]};

const element = (id: string): HTMLElement => {
    const found = document.getElementById(id);
    if (!found) throw new Error(`Missing element ${id}`);
    return found;
};

const sent = (shell: RespondentShell): SentCommand[] => vi.mocked(apiClient.patch).mock.calls
    .filter(([path]) => path === `${shell.base}/responses`)
    .map(([, body, config]) => ({
        body: body as SentCommand["body"],
        headers: (config as { headers: Record<string, string> }).headers,
    }));

const explainDocument = async (reason: string) => {
    const field = await waitFor(() => element(DOCUMENT_REASON));
    fireEvent.change(field, {target: {value: reason}});
};

describe("InformationRequestRespondentWorkspace answer choices on both respondent shells", () => {
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
        server.reads = [];
        vi.mocked(apiClient.get).mockImplementation(async path => {
            if (String(path).endsWith("/response-workspace")) {
                return {data: server.reads.length > 1 ? server.reads.shift() : server.reads[0]};
            }
            throw new Error(`Unexpected read ${path}`);
        });
        vi.mocked(apiClient.patch).mockResolvedValue({data: [], headers: {etag: "\"responses:2\""}});
    });

    afterEach(cleanup);

    it.each(respondentShells)("sends each saved answer's own choice with only the changes made, in the $mode shell", async shell => {
        server.reads = [workspaceAt(1, savedAnswers())];
        openRespondentShell(shell);

        await explainDocument(KEPT_ELSEWHERE);
        fireEvent.change(element("information-request-response-field-root-record-summary"), {target: {value: "First and second portions"}});
        fireEvent.change(element("information-request-response-field-root-record-count"), {target: {value: "12"}});
        fireEvent.change(element("information-request-response-narrative-root-record-count"), {target: {value: "Counted by hand"}});
        fireEvent.click(element("information-request-response-save"));

        await waitFor(() => expect(sent(shell)).toHaveLength(1));
        const [command] = sent(shell);
        expect(command.body.patches).toEqual([
            {
                requirementId: "runtime-1",
                disposition: InformationRequestResponseDisposition.UNAVAILABLE,
                narrative: KEPT_ELSEWHERE,
            },
            {
                requirementId: "runtime-2",
                disposition: InformationRequestResponseDisposition.PARTIALLY_PROVIDED,
                fieldValues: {
                    etag: "\"field-set:2\"",
                    values: [{fieldContractId: "field-contract-1", value: "First and second portions"}],
                },
            },
            {
                requirementId: "runtime-3",
                disposition: InformationRequestResponseDisposition.PROVIDED,
                narrative: "Counted by hand",
                fieldValues: {
                    etag: "\"field-set:3\"",
                    values: [{fieldContractId: "field-contract-2", value: "12"}],
                },
            },
        ]);
        expect(command.headers["If-Match"]).toBe("\"responses:1\"");
        expect(command.headers["X-Request-Access-Token"]).toBe(shell.accessToken);
    });

    it.each(respondentShells)("saves an earlier answer entered again after another device changed it, in the $mode shell", async shell => {
        server.reads = [
            workspaceAt(1, savedAnswers()),
            workspaceAt(3, savedAnswers(documentAnswer(InformationRequestResponseDisposition.NOT_APPLICABLE, "Changed on another device"))),
        ];
        openRespondentShell(shell);
        await explainDocument(KEPT_ELSEWHERE);
        fireEvent.click(element("information-request-response-save"));
        await waitFor(() => expect((element(DOCUMENT_REASON) as HTMLTextAreaElement).value).toBe("Changed on another device"));

        fireEvent.click(element("information-request-response-disposition-root-supporting-record-UNAVAILABLE"));
        await explainDocument(KEPT_ELSEWHERE);
        expect(element("information-request-response-save-status").textContent).toBe("Unsaved changes. Saving shortly.");

        await waitFor(() => expect(sent(shell)).toHaveLength(2), {timeout: 5000});
        const [first, second] = sent(shell);
        expect(second.body.patches).toEqual(first.body.patches);
        expect(second.headers["If-Match"]).toBe("\"responses:3\"");
    }, 15000);
});
