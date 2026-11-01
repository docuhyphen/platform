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

    it.each(respondentShells)("blocks submitting while a changed answer is still saving, in the $mode shell", async shell => {
        vi.mocked(apiClient.patch).mockImplementation(() => new Promise(() => {
        }));
        vi.mocked(apiClient.post).mockImplementation(() => new Promise(() => {
        }));
        openRespondentShell(shell);
        await waitFor(() => expect((element("information-request-submission-submit") as HTMLButtonElement).disabled).toBe(false));
        fireEvent.change(element("information-request-response-field-root-record-owner"), {target: {value: "Updated keeper"}});
        fireEvent.click(element("information-request-response-save"));
        await waitFor(() => expect(apiClient.patch).toHaveBeenCalledTimes(1));
        expect(element("information-request-response-save-status").textContent).toContain("Saving");
        fireEvent.click(element("information-request-submission-submit"));
        const confirm = document.getElementById("information-request-submission-review-confirm") as HTMLButtonElement | null;
        if (confirm && !confirm.disabled) fireEvent.click(confirm);
        expect(vi.mocked(apiClient.post).mock.calls.filter(([url]) => String(url).endsWith("/submissions"))).toHaveLength(0);
    });

    it.each(respondentShells)("blocks submitting after the changed answer failed to save, in the $mode shell", async shell => {
        vi.mocked(apiClient.patch).mockRejectedValue(new Error("Audit save refused"));
        vi.mocked(apiClient.post).mockImplementation(() => new Promise(() => {
        }));
        openRespondentShell(shell);
        await waitFor(() => expect((element("information-request-submission-submit") as HTMLButtonElement).disabled).toBe(false));
        fireEvent.change(element("information-request-response-field-root-record-owner"), {target: {value: "Updated keeper"}});
        fireEvent.click(element("information-request-response-save"));
        await waitFor(() => expect(document.getElementById("information-request-response-save-error")).not.toBeNull());
        expect(window.sessionStorage.getItem("information-request-unsaved:request-a:respondent-a")).toContain("Updated keeper");
        fireEvent.click(element("information-request-submission-submit"));
        const confirm = document.getElementById("information-request-submission-review-confirm") as HTMLButtonElement | null;
        if (confirm && !confirm.disabled) fireEvent.click(confirm);
        expect(vi.mocked(apiClient.post).mock.calls.filter(([url]) => String(url).endsWith("/submissions"))).toHaveLength(0);
    });

    it.each(respondentShells)("submits the reviewed server answers when there are no pending edits, in the $mode shell", async shell => {
        vi.mocked(apiClient.post).mockImplementation(() => new Promise(() => {
        }));
        openRespondentShell(shell);
        await waitFor(() => expect((element("information-request-submission-submit") as HTMLButtonElement).disabled).toBe(false));
        fireEvent.click(element("information-request-submission-submit"));
        fireEvent.click(element("information-request-submission-review-confirm"));
        expect(vi.mocked(apiClient.post).mock.calls.filter(([url]) => String(url).endsWith("/submissions"))).toHaveLength(1);
        expect(apiClient.patch).not.toHaveBeenCalled();
    });

    for (const choiceType of [FieldValueType.SINGLE_SELECT, FieldValueType.MULTI_SELECT]) {
        it.each(respondentShells)("states the " + choiceType + " answer by its configured label in the $mode shell", async shell => {
            const choiceWorkspace = structuredClone(workspace);
            const schema = choiceWorkspace.schemaAssignment;
            if (!schema) throw new Error("The choice fixture needs its schema");
            schema.bindings[1].valueType = choiceType;
            schema.bindings[1].options = [{code: "records-office-code", label: "Records office"}];
            const response = choiceWorkspace.responses[2];
            response.narrative = "";
            response.fieldValues[0].valueType = choiceType;
            response.fieldValues[0].value = choiceType === FieldValueType.MULTI_SELECT ? ["records-office-code"] : "records-office-code";
            const read = vi.mocked(apiClient.get).getMockImplementation();
            if (!read) throw new Error("The fixture needs its read handler");
            vi.mocked(apiClient.get).mockImplementation((url, config) =>
                String(url).endsWith("/response-workspace") ? Promise.resolve({data: choiceWorkspace}) :
                    String(url).endsWith("/submission-preview") ? Promise.resolve({
                        data: {
                            ...preview,
                            scopeItems: submissionSnapshotItems(choiceWorkspace)
                        }
                    }) : read(url, config));
            openRespondentShell(shell);
            await waitFor(() => expect((element("information-request-submission-submit") as HTMLButtonElement).disabled).toBe(false));
            expect(element("information-request-response-field-root-record-owner").textContent).toContain("Records office");
            fireEvent.click(element("information-request-submission-submit"));
            await waitFor(() => expect(document.getElementById("information-request-submission-review")).not.toBeNull());
            expect(shownAnswer(3)).toBe("Records office");
        });
    }

    it.each(respondentShells)("reviews only the selected submission stage, in the $mode shell", async shell => {
        const stagedWorkspace = structuredClone(workspace);
        stagedWorkspace.templateVersion.submissionMode = InformationRequestSubmissionMode.STAGED;
        stagedWorkspace.templateVersion.sections = [
            {
                ...stagedWorkspace.templateVersion.sections[0],
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
        ];
        vi.mocked(apiClient.get).mockImplementation(async path => {
            const read = String(path);
            if (read.endsWith("/response-workspace")) return {data: stagedWorkspace};
            if (read.endsWith("/submission-preview")) return {
                data: {
                    ...preview, submissionMode: InformationRequestSubmissionMode.STAGED, stageKey: "first",
                    scopeItems: submissionSnapshotItems(stagedWorkspace, "first"),
                    stages: [{stageKey: "first", correctionOpen: false}, {stageKey: "second", correctionOpen: false}],
                }
            };
            if (read.endsWith("/amendments") || read.endsWith("/carry-forwards") || read.endsWith("/review-results")) return {data: []};
            if (read.endsWith("/evidence-artifacts")) return {
                data: {
                    requirementId: "runtime-2",
                    evidenceETag: "evidence:0",
                    artifacts: []
                }
            };
            throw new Error("Unexpected read " + read);
        });
        openRespondentShell(shell);
        await waitFor(() => expect((element("information-request-submission-submit") as HTMLButtonElement).disabled).toBe(false));
        fireEvent.click(element("information-request-submission-submit"));
        await waitFor(() => expect(document.getElementById("information-request-submission-review")).not.toBeNull());
        expect(document.getElementById("information-request-submission-review-runtime-1")).not.toBeNull();
        expect(document.getElementById("information-request-submission-review-runtime-2")).toBeNull();
        expect(document.getElementById("information-request-submission-review-runtime-3")).toBeNull();
    });

    it.each(respondentShells)("gives distinct valid Requirement keys distinct input and prompt identities, in the $mode shell", async shell => {
        const identityWorkspace = structuredClone(workspace);
        identityWorkspace.templateVersion.sections[0].requirements[0].requirementKey = "record--owner";
        identityWorkspace.templateVersion.sections[0].requirements[2].requirementKey = "record-owner";
        const read = vi.mocked(apiClient.get).getMockImplementation();
        if (!read) throw new Error("The fixture needs its read handler");
        vi.mocked(apiClient.get).mockImplementation((url, config) =>
            String(url).endsWith("/response-workspace") ? Promise.resolve({data: identityWorkspace}) : read(url, config));
        openRespondentShell(shell);
        await waitFor(() => expect((element("information-request-submission-submit") as HTMLButtonElement).disabled).toBe(false));
        const inputs = Array.from(document.querySelectorAll<HTMLInputElement>("input[id^='information-request-response-field-']"));
        expect(inputs.map(input => input.value).sort()).toEqual(["Records office", "Three records"]);
        expect(new Set(inputs.map(input => input.id)).size).toBe(2);
        expect(new Set(inputs.map(input => input.getAttribute("aria-labelledby"))).size).toBe(2);
        expect(inputs.map(input => document.getElementById(input.getAttribute("aria-labelledby") ?? "")?.textContent))
            .toEqual(["How many records were kept?", "Who keeps the records?"]);
    });
});
