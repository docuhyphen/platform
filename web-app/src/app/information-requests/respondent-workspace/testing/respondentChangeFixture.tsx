import {act, fireEvent, render, waitFor} from "@testing-library/react";
import {MemoryRouter, Route, Routes} from "react-router-dom";
import {expect, vi} from "vitest";
import apiClient from "../../../../services/apiClient.ts";
import {
    FieldDataClassification,
    FieldValueType,
    InformationRequestAcceptedFactConfidence,
    InformationRequestAcceptedFactFreshness,
    InformationRequestAcceptedFactOfferDto,
    InformationRequestAmendmentChangeKind,
    InformationRequestAmendmentDto,
    InformationRequestCarryForwardDecision,
    InformationRequestCarryForwardDto,
    InformationRequestContributorRole,
    InformationRequestEvidenceListDto,
    InformationRequestExecutionStandingKind,
    InformationRequestFindingCorrectionScope,
    InformationRequestFindingSeverity,
    InformationRequestOwnerType,
    InformationRequestRequiredness,
    InformationRequestRequirementType,
    InformationRequestRespondentReviewDto,
    InformationRequestResponseDisposition,
    InformationRequestResponseMode,
    InformationRequestResponseWorkspaceDto,
    InformationRequestReviewKind,
    InformationRequestReviewPolicy,
    InformationRequestReviewState,
    InformationRequestReviewVisibility,
    InformationRequestState,
    InformationRequestSubmissionMode,
    InformationRequestSubmissionPreviewDto,
    InformationRequestSubmissionResultDto,
    InformationRequestSubmissionStageOrdering,
    InformationRequestTemplateStatus,
} from "../../../models/models.tsx";
import InformationRequestRespondentWorkspace from "../InformationRequestRespondentWorkspace.tsx";

export const FIRST = "respondent-first";
export const SECOND = "respondent-second";
export const requestPath = "/information-requests/request-a";
export const evidenceSuffix = "/requirements/runtime-2/evidence-artifacts";
export const panelId = "information-request-evidence-root-supporting-record-PANEL";
export const reviewDialogId = "information-request-submission-review";
export const heldSuffixes = ["/submission-preview", "/carry-forwards", "/accepted-fact-offers", "/amendments", "/review-results"];

interface Projection {
    carried: string;
    offered: string;
    amended: string;
    finding: string;
    canSubmit: boolean;
}

export const projections: Record<string, Projection> = {
    [FIRST]: {
        carried: "Carried answer of the first respondent",
        offered: "Accepted answer of the first respondent",
        amended: "first-respondent-item",
        finding: "Finding for the first respondent",
        canSubmit: true,
    },
    [SECOND]: {
        carried: "Carried answer of the second respondent",
        offered: "Accepted answer of the second respondent",
        amended: "second-respondent-item",
        finding: "Finding for the second respondent",
        canSubmit: false,
    },
};

export const amendedText = (respondent: string): string => `${projections[respondent].amended.replace(/-/g, " ")}: Changed`;

export const workspaceFor = (respondentKey: string): InformationRequestResponseWorkspaceDto => ({
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
            requirements: [
                {
                    id: "requirement-1",
                    templateRequirementId: "requirement-1",
                    requirementKey: "record-count",
                    requirementType: InformationRequestRequirementType.FIELD,
                    prompt: "How many records were kept?",
                    responseMode: InformationRequestResponseMode.PROVIDE,
                    requiredness: InformationRequestRequiredness.OPTIONAL,
                    contributorRole: InformationRequestContributorRole.CONTRIBUTOR,
                    reviewPolicy: InformationRequestReviewPolicy.NOT_REQUIRED,
                    collectedFieldDefinitionId: "field-definition-1",
                    permittedDispositions: [InformationRequestResponseDisposition.PROVIDED],
                    substituteRequirementKeys: [],
                    supportingEvidenceRequirementKeys: [],
                },
                {
                    id: "requirement-2",
                    templateRequirementId: "requirement-2",
                    requirementKey: "supporting-record",
                    requirementType: InformationRequestRequirementType.DOCUMENT,
                    prompt: "Provide the supporting record",
                    responseMode: InformationRequestResponseMode.PROVIDE,
                    requiredness: InformationRequestRequiredness.OPTIONAL,
                    contributorRole: InformationRequestContributorRole.CONTRIBUTOR,
                    reviewPolicy: InformationRequestReviewPolicy.NOT_REQUIRED,
                    permittedDispositions: [InformationRequestResponseDisposition.PROVIDED],
                    substituteRequirementKeys: [],
                    supportingEvidenceRequirementKeys: [],
                },
            ],
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
            isRequired: false,
            isReadOnly: false,
            visibility: FieldDataClassification.INTERNAL,
            constraints: {},
            options: [],
        }],
        fields: [],
    },
    responses: [
        {
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
        },
        {
            informationRequestRequirementId: "runtime-2",
            sourceTemplateRequirementId: "requirement-2",
            sourceTemplateBindingId: "requirement-2",
            occurrencePath: "root",
            disposition: InformationRequestResponseDisposition.NOT_ANSWERED,
            fieldValues: [],
            responseRevision: 1,
            updatedAt: Date.parse("2026-10-13T08:00:00Z"),
        },
    ],
    supportingEvidenceLinks: [],
    evidenceUploadAvailable: true,
    evidenceMalwareScanning: false,
    requirementsAwaitingStepUp: 0,
    executionStanding: {kind: InformationRequestExecutionStandingKind.ACTIVE},
});

export const previewFor = (respondent: string): InformationRequestSubmissionPreviewDto => ({
    informationRequestId: "request-a",
    submissionMode: InformationRequestSubmissionMode.WHOLE_PACKAGE,
    submissionStageOrdering: InformationRequestSubmissionStageOrdering.ANY_ORDER,
    scopeItems: [],
    submissionETag: "\"submission:1\"",
    ready: true,
    problems: [],
    undisclosedProblemCount: 0,
    groupProblems: [],
    attestations: [],
    stages: [],
    canSubmit: projections[respondent].canSubmit,
    correctionOpen: false,
    packages: [],
});

export const carriedFor = (respondent: string): InformationRequestCarryForwardDto => ({
    requirementId: "runtime-1",
    decision: InformationRequestCarryForwardDecision.OFFERED,
    sourcePackageId: `package-${respondent}`,
    priorDisposition: InformationRequestResponseDisposition.PROVIDED,
    priorNarrative: projections[respondent].carried,
});

export const offerFor = (respondent: string): InformationRequestAcceptedFactOfferDto => ({
    requirementId: "runtime-1",
    requirementKey: "record-count",
    reconfirmationRequired: true,
    fact: {
        id: `fact-${respondent}`,
        purposeKey: "profile.reuse",
        policyBasisKey: "policy.reuse",
        valueType: FieldValueType.SHORT_TEXT,
        value: projections[respondent].offered,
        confidence: InformationRequestAcceptedFactConfidence.DECLARED,
        validFrom: Date.parse("2026-10-01T08:00:00Z"),
        freshness: InformationRequestAcceptedFactFreshness.CURRENT,
    },
});

export const amendmentFor = (respondent: string): InformationRequestAmendmentDto => ({
    id: `amendment-${respondent}`,
    amendmentNumber: 1,
    fromTemplateVersionId: "version-0",
    toTemplateVersionId: "version-a",
    amendedAt: Date.parse("2026-10-12T08:00:00Z"),
    amendedByCaller: false,
    changes: [{
        requirementKey: projections[respondent].amended,
        changeKind: InformationRequestAmendmentChangeKind.MEANING_CHANGED,
        reconfirmationRequired: false,
    }],
    undisclosedChangeCount: 0,
    notices: [],
});

export const reviewFor = (respondent: string): InformationRequestRespondentReviewDto => ({
    review: {
        id: `review-${respondent}`,
        informationRequestId: "request-a",
        packageId: `package-${respondent}`,
        packageNumber: 1,
        reviewNumber: 1,
        kind: InformationRequestReviewKind.INITIAL,
        state: InformationRequestReviewState.CHANGES_REQUESTED,
        openedAt: Date.parse("2026-10-12T08:00:00Z"),
        settledAt: Date.parse("2026-10-12T09:00:00Z"),
        reviewETag: `"review:${respondent}"`,
    },
    findings: [{
        id: `finding-${respondent}`,
        submissionItemId: `item-${respondent}`,
        requirementId: "runtime-1",
        reasonCode: "record.incomplete",
        narrative: projections[respondent].finding,
        severity: InformationRequestFindingSeverity.MAJOR,
        visibility: InformationRequestReviewVisibility.RESPONDENT_VISIBLE,
        correctionScope: InformationRequestFindingCorrectionScope.RESPONSE,
        recordedAt: Date.parse("2026-10-12T08:30:00Z"),
        recordedByCaller: false,
        reviewId: `review-${respondent}`,
    }],
    comments: [],
    remediations: [],
    canAppeal: false,
    canComment: false,
});

export const submitted: InformationRequestSubmissionResultDto = {
    requestState: InformationRequestState.IN_PROGRESS,
    requestETag: "\"request-4\"",
    responseETag: "\"responses:1\"",
    submission: {
        id: `package-${FIRST}`,
        informationRequestId: "request-a",
        packageNumber: 1,
        templateVersionId: "version-a",
        contentHash: "content-hash",
        manifestHash: "manifest-hash",
        reviewRequired: false,
        completesRequest: false,
        submittedAt: Date.parse("2026-10-13T09:00:00Z"),
        submittedByCaller: true,
        withdrawn: false,
        items: [],
        attestations: [],
        supportingEvidenceLinks: [],
        undisclosedItemCount: 0,
        current: true,
        withdrawable: false,
    },
};

export const noFiles: InformationRequestEvidenceListDto = {
    requirementId: "runtime-2",
    evidenceETag: "\"evidence:0\"",
    artifacts: [],
};

export const readFor = (respondent: string, path: string): { data: unknown } => {
    if (path === `${requestPath}/response-workspace`) return {data: workspaceFor(respondent)};
    if (path === `${requestPath}/submission-preview`) return {data: previewFor(respondent)};
    if (path === `${requestPath}/carry-forwards`) return {data: [carriedFor(respondent)]};
    if (path === `${requestPath}/accepted-fact-offers`) return {data: [offerFor(respondent)]};
    if (path === `${requestPath}/amendments`) return {data: [amendmentFor(respondent)]};
    if (path === `${requestPath}/review-results`) return {data: [reviewFor(respondent)]};
    if (path === `${requestPath}${evidenceSuffix}`) return {data: noFiles};
    throw new Error(`Unexpected read ${path}`);
};

export const server = {
    caller: FIRST,
    callerAtNextSave: SECOND as string | null,
    holdReadsOf: null as string | null,
    held: [] as Array<() => void>,
    submissionAnswers: [] as Array<() => void>,
};

export const respondentChanged = {
    isAxiosError: true,
    response: {
        status: 409,
        data: {
            reasonCode: "INFORMATION_REQUEST_RESPONDENT_CHANGED",
            errorMessage: "These answers were entered by another respondent and were not saved",
        },
    },
};
export const dropped = {isAxiosError: true, message: "Network Error"};

export const reads = (suffix: string) => vi.mocked(apiClient.get).mock.calls.filter(([path]) => String(path).endsWith(suffix));

export const element = (id: string): HTMLElement => {
    const found = document.getElementById(id);
    if (!found) throw new Error(`Missing element ${id}`);
    return found;
};

export const answerField = () => document.querySelector<HTMLInputElement>("#information-request-response-field-root-record-count");

export const submitButton = () => document.getElementById("information-request-submission-submit") as HTMLButtonElement | null;

export const keptText = (fileName: string): string => `${fileName} was not sent. It is kept here so you can try again.`;

export const openRespondentPage = () => render(
    <MemoryRouter initialEntries={["/information-requests/request-a/respond"]}>
        <Routes>
            <Route path={"/information-requests/:requestId/respond"}
                   element={<InformationRequestRespondentWorkspace accessMode={"authenticated"}/>}/>
        </Routes>
    </MemoryRouter>,
);

export const saveAnswer = async (answer: string) => {
    const workspaceReads = reads("/response-workspace").length;
    fireEvent.change(answerField() as HTMLInputElement, {target: {value: answer}});
    fireEvent.click(element("information-request-response-save"));
    await waitFor(() => expect(reads("/response-workspace")).toHaveLength(workspaceReads + 1));
};

export const changeRespondent = async () => {
    await saveAnswer("Unsaved answer of the first respondent");
    await waitFor(() => expect(answerField()?.value).toBe(""));
};

export const deliverHeldReads = () => act(async () => {
    server.held.splice(0).forEach(deliver => deliver());
});
