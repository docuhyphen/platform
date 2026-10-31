import {vi} from "vitest";
import apiClient from "../../../../services/apiClient.ts";
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
    InformationRequestResponseEditLock,
    InformationRequestResponseMode,
    InformationRequestResponseWorkspaceDto,
    InformationRequestReviewPolicy,
    InformationRequestState,
    InformationRequestSubmissionMode,
    InformationRequestSubmissionPreviewDto,
    InformationRequestSubmissionResultDto,
    InformationRequestSubmissionStageOrdering,
    InformationRequestTemplateRequirementDto,
    InformationRequestTemplateSectionDto,
    InformationRequestTemplateStatus,
    SchemaFieldBindingDto,
} from "../../../models/models.tsx";

export interface PendingWorkServerState {
    revision: number;
    count: string;
    owner: string;
    staged: boolean;
    correctionOpen: boolean;
    closed: boolean;
    submittedStages: string[];
}

interface SentCommand {
    url: string;
    body: unknown;
    headers: Record<string, string>;
}

const at = Date.parse("2026-10-20T08:00:00Z");

const INITIAL: PendingWorkServerState = {
    revision: 1,
    count: "Three records",
    owner: "Records office",
    staged: false,
    correctionOpen: false,
    closed: false,
    submittedStages: [],
};

const STAGES = ["first", "second"];

export const pendingWorkServer: PendingWorkServerState = {...INITIAL};

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

const sections = (): InformationRequestTemplateSectionDto[] => pendingWorkServer.staged
    ? [
        {
            id: "section-a",
            sectionKey: "records",
            title: "Records",
            submissionStageKey: "first",
            requirements: [requirements[0]]
        },
        {
            id: "section-b",
            sectionKey: "later",
            title: "Later records",
            submissionStageKey: "second",
            requirements: requirements.slice(1)
        },
    ]
    : [{id: "section-a", sectionKey: "records", title: "Records", requirements}];

const stageOfRequirement = (index: number): string | null =>
    pendingWorkServer.staged ? (index === 1 ? STAGES[0] : STAGES[1]) : null;

const lockOf = (index: number): InformationRequestResponseEditLock | undefined => {
    const stage = stageOfRequirement(index);
    return stage !== null && pendingWorkServer.submittedStages.includes(stage) && !pendingWorkServer.correctionOpen
        ? InformationRequestResponseEditLock.SUBMITTED
        : undefined;
};

const answer = (
    index: number,
    disposition: InformationRequestResponseDisposition,
    narrative: string,
    value?: { collected: SchemaFieldBindingDto; text: string },
): InformationRequestResponseDto => ({
    ...(lockOf(index) ? {editLock: lockOf(index)} : {}),
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
    responseRevision: pendingWorkServer.revision,
    updatedAt: at,
});

export const workspaceNow = (): InformationRequestResponseWorkspaceDto => ({
    request: {
        id: "request-a",
        exchangeId: "exchange-a",
        templateVersionId: "version-a",
        ownerType: InformationRequestOwnerType.ORGANIZATION,
        state: pendingWorkServer.closed ? InformationRequestState.CLOSED : InformationRequestState.IN_PROGRESS,
        gatesExchangeClosure: true,
        aggregateRevision: pendingWorkServer.revision,
        createdAt: at,
        updatedAt: at,
        requestETag: `"request-${pendingWorkServer.revision}"`,
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
        submissionMode: pendingWorkServer.staged ? InformationRequestSubmissionMode.STAGED : InformationRequestSubmissionMode.WHOLE_PACKAGE,
        sections: sections(),
        groups: [],
        conditionRules: [],
        requiredCapabilities: [],
        createdAt: at,
    },
    responseETag: `"responses:${pendingWorkServer.revision}"`,
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
            {collected: bindings[0], text: pendingWorkServer.count}),
        answer(2, InformationRequestResponseDisposition.PARTIALLY_PROVIDED, "One file follows next week"),
        answer(3, InformationRequestResponseDisposition.PROVIDED, "Named by the keeper",
            {collected: bindings[1], text: pendingWorkServer.owner}),
    ],
    supportingEvidenceLinks: [],
    evidenceUploadAvailable: true,
    evidenceMalwareScanning: false,
    requirementsAwaitingStepUp: 0,
    executionStanding: {kind: InformationRequestExecutionStandingKind.ACTIVE},
});

const stageOf = (requested?: string): string | null => {
    if (!pendingWorkServer.staged) return null;
    return requested ?? STAGES.find(stage => !pendingWorkServer.submittedStages.includes(stage)) ?? STAGES[0];
};

export const previewNow = (requested?: string): InformationRequestSubmissionPreviewDto => ({
    informationRequestId: "request-a",
    stageKey: stageOf(requested),
    submissionMode: pendingWorkServer.staged ? InformationRequestSubmissionMode.STAGED : InformationRequestSubmissionMode.WHOLE_PACKAGE,
    submissionStageOrdering: InformationRequestSubmissionStageOrdering.ANY_ORDER,
    submissionETag: `"submission:${pendingWorkServer.revision}"`,
    scopeItems: workspaceNow().responses.flatMap(response => {
        const section = sections().find(candidate => candidate.requirements.some(requirement => requirement.id === response.sourceTemplateBindingId));
        const requirement = section?.requirements.find(candidate => candidate.id === response.sourceTemplateBindingId);
        if (!requirement || (stageOf(requested) && section?.submissionStageKey !== stageOf(requested))) return [];
        const value = response.fieldValues[0];
        return [{
            requirementId: response.informationRequestRequirementId,
            templateBindingId: requirement.id,
            requirementKey: requirement.requirementKey,
            requirementType: requirement.requirementType,
            prompt: requirement.prompt,
            occurrencePath: response.occurrencePath,
            disposition: response.disposition,
            narrative: response.narrative,
            fieldContractId: value?.fieldContractId,
            fieldValueType: value?.valueType,
            fieldValue: value?.value,
            fieldValueCleared: value?.isEmpty ?? false,
            evidenceFileCount: response.evidenceFileCount ?? 0
        }];
    }),
    ready: true,
    problems: [],
    undisclosedProblemCount: 0,
    groupProblems: [],
    attestations: [],
    stages: pendingWorkServer.staged
        ? STAGES.map(stage => ({
            stageKey: stage,
            submitted: pendingWorkServer.submittedStages.includes(stage),
            correctionOpen: pendingWorkServer.correctionOpen && stage === stageOf(requested),
        }))
        : [],
    canSubmit: true,
    correctionOpen: pendingWorkServer.correctionOpen,
    packages: [],
});

const submissionResult = (): InformationRequestSubmissionResultDto => ({
    requestState: InformationRequestState.CLOSED,
    requestETag: `"request-${pendingWorkServer.revision}"`,
    responseETag: `"responses:${pendingWorkServer.revision}"`,
    submission: {
        id: "package-1",
        informationRequestId: "request-a",
        packageNumber: 1,
        stageKey: pendingWorkServer.staged ? "first" : null,
        templateVersionId: "version-a",
        contentHash: "content-hash",
        manifestHash: "manifest-hash",
        reviewRequired: false,
        completesRequest: true,
        submittedAt: at,
        submittedByCaller: true,
        withdrawn: false,
        items: [],
        attestations: [],
        supportingEvidenceLinks: [],
        undisclosedItemCount: 0,
        current: true,
        withdrawable: false,
    },
});

const lockedTargets = (body: unknown): string[] =>
    ((body as { patches?: { requirementId: string }[] }).patches ?? [])
        .map(patch => patch.requirementId)
        .filter(requirementId => lockOf(Number(requirementId.replace("runtime-", ""))) !== undefined);

const applyPatches = (body: unknown) => {
    const patches = (body as {
        patches?: { fieldValues?: { values?: { fieldContractId: string; value: unknown }[] } }[]
    }).patches ?? [];
    for (const value of patches.flatMap(patch => patch.fieldValues?.values ?? [])) {
        if (value.fieldContractId === "field-contract-1") pendingWorkServer.count = String(value.value);
        if (value.fieldContractId === "field-contract-2") pendingWorkServer.owner = String(value.value);
    }
};

export const startPendingWorkServer = (changes: Partial<PendingWorkServerState> = {}) => {
    Object.assign(pendingWorkServer, INITIAL, changes);
    vi.mocked(apiClient.get).mockImplementation(async (path, config) => {
        const read = String(path);
        if (read.endsWith("/response-workspace")) return {data: workspaceNow()};
        if (read.endsWith("/submission-preview")) {
            return {data: previewNow((config as { params?: { stageKey?: string } } | undefined)?.params?.stageKey)};
        }
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
    vi.mocked(apiClient.patch).mockImplementation(async (_path, body) => {
        const locked = lockedTargets(body);
        if (locked.length > 0) {
            throw {
                errorMessage: "A Requirement this change addresses is locked by its submission",
                reasonCode: "INFORMATION_REQUEST_SUBMISSION_LOCKED",
                problems: locked.map(requirementId => ({
                    code: "INFORMATION_REQUEST_SUBMISSION_LOCKED",
                    message: "This Requirement was submitted and cannot change until its submission is withdrawn",
                    requirementId,
                    occurrencePath: "root",
                })),
                undisclosedProblemCount: 0,
            };
        }
        applyPatches(body);
        pendingWorkServer.revision += 1;
        return {data: [], headers: {etag: `"responses:${pendingWorkServer.revision}"`}};
    });
    vi.mocked(apiClient.post).mockImplementation(async path => {
        if (String(path).endsWith("/submissions")) {
            pendingWorkServer.closed = true;
            return {data: submissionResult(), headers: {etag: `"request-${pendingWorkServer.revision}"`}};
        }
        throw new Error(`Unexpected command ${path}`);
    });
};

const sentTo = (calls: unknown[][], ending: string): SentCommand[] => calls
    .filter(([url]) => String(url).endsWith(ending))
    .map(([url, body, config]) => ({
        url: String(url),
        body,
        headers: (config as { headers: Record<string, string> } | undefined)?.headers ?? {},
    }));

export const savesSent = (): SentCommand[] => sentTo(vi.mocked(apiClient.patch).mock.calls, "/responses");

export const submissionsSent = (): SentCommand[] => sentTo(vi.mocked(apiClient.post).mock.calls, "/submissions");

export const uploadsSent = (): SentCommand[] => sentTo(vi.mocked(apiClient.post).mock.calls, "/evidence-artifacts");
