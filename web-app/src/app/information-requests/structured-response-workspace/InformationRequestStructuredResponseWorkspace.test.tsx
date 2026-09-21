/** @vitest-environment jsdom */
import {cleanup, fireEvent, render, screen, waitFor} from "@testing-library/react";
import {afterEach, beforeEach, describe, expect, it, vi} from "vitest";
import {
    FieldDataClassification,
    FieldValueType,
    InformationRequestConditionHiddenDataPolicy,
    InformationRequestConditionEvaluationState,
    InformationRequestContributorRole,
    InformationRequestDto,
    InformationRequestGroupOccurrenceDto,
    InformationRequestOwnerType,
    InformationRequestRequiredness,
    InformationRequestRequirementType,
    InformationRequestResponseDisposition,
    InformationRequestResponseDto,
    InformationRequestResponseMode,
    InformationRequestReviewPolicy,
    InformationRequestState,
    InformationRequestTemplateConditionRuleDto,
    InformationRequestTemplateGroupDto,
    InformationRequestTemplateRequirementDto,
    SchemaFieldBindingDto,
} from "../../models/models.tsx";
import InformationRequestStructuredResponseWorkspace from "./InformationRequestStructuredResponseWorkspace.tsx";

const onSaveResponses = vi.fn();
const onAddOccurrence = vi.fn();
const onRemoveOccurrence = vi.fn();
const onReorderOccurrences = vi.fn();
const onRefresh = vi.fn();

const request: InformationRequestDto = {
    id: "request-1",
    exchangeId: "exchange-1",
    templateVersionId: "template-version-1",
    ownerType: InformationRequestOwnerType.USER,
    state: InformationRequestState.IN_PROGRESS,
    gatesExchangeClosure: true,
    aggregateRevision: 3,
    createdAt: "2026-09-08T00:00:00Z",
    updatedAt: "2026-09-08T00:00:00Z",
    requestETag: "\"request:3\"",
    conditionEvaluations: [
        {
            ruleKey: "collect-detail",
            expressionVersion: 1,
            state: InformationRequestConditionEvaluationState.TRUE,
            occurrencePath: "reported-item[0]",
        },
        {
            ruleKey: "collect-hidden-detail",
            expressionVersion: 1,
            state: InformationRequestConditionEvaluationState.FALSE,
            occurrencePath: "reported-item[0]",
        },
    ],
};

const group: InformationRequestTemplateGroupDto = {
    id: "group-1",
    groupKey: "reported-item",
    minOccurrences: 0,
    maxOccurrences: 3,
};

const nestedGroup: InformationRequestTemplateGroupDto = {
    id: "group-2",
    groupKey: "entry",
    parentGroupKey: "reported-item",
    minOccurrences: 0,
    maxOccurrences: 4,
};

const clearWithConfirmationRule: InformationRequestTemplateConditionRuleDto = {
    id: "condition-rule-1",
    ruleKey: "collect-hidden-detail",
    expressionVersion: 1,
    hiddenDataPolicy: InformationRequestConditionHiddenDataPolicy.CLEAR_WITH_CONFIRMATION,
    predicates: [],
};

const editableBinding: SchemaFieldBindingDto = {
    id: "binding-1",
    fieldContractId: "field-contract-1",
    fieldDefinitionId: "field-definition-1",
    namespace: "process",
    fieldKey: "reported-summary",
    label: "Reported summary",
    valueType: FieldValueType.SHORT_TEXT,
    displayOrder: 0,
    section: "Reported item",
    isRequired: true,
    isReadOnly: false,
    visibility: FieldDataClassification.INTERNAL,
    constraints: {},
    options: [],
};

const hiddenBinding: SchemaFieldBindingDto = {
    ...editableBinding,
    id: "binding-2",
    fieldContractId: "field-contract-2",
    fieldDefinitionId: "field-definition-2",
    fieldKey: "hidden-summary",
    label: "Hidden summary",
};

const requirement = (
    id: string,
    requirementKey: string,
    prompt: string,
    fieldDefinitionId: string,
    conditionalRuleKey?: string,
    occurrenceAnchorKey = "reported-item",
): InformationRequestTemplateRequirementDto => ({
    id,
    templateRequirementId: id,
    requirementKey,
    requirementType: InformationRequestRequirementType.FIELD,
    prompt,
    responseMode: InformationRequestResponseMode.PROVIDE,
    requiredness: conditionalRuleKey
        ? InformationRequestRequiredness.CONDITIONAL
        : InformationRequestRequiredness.REQUIRED,
    contributorRole: InformationRequestContributorRole.CONTRIBUTOR,
    reviewPolicy: InformationRequestReviewPolicy.NOT_REQUIRED,
    conditionalRuleKey,
    occurrenceAnchorKey,
    collectedFieldDefinitionId: fieldDefinitionId,
    permittedDispositions: [
        InformationRequestResponseDisposition.PROVIDED,
        InformationRequestResponseDisposition.NOT_APPLICABLE,
    ],
    substituteRequirementKeys: [],
    supportingEvidenceRequirementKeys: [],
});

const responses: InformationRequestResponseDto[] = [
    {
        informationRequestRequirementId: "requirement-1",
        sourceTemplateRequirementId: "requirement-1",
        sourceTemplateBindingId: "requirement-1",
        occurrencePath: "reported-item[0]",
        disposition: InformationRequestResponseDisposition.NOT_ANSWERED,
        fieldValueSetId: "value-set-1",
        fieldValueSetETag: "\"field-set:1\"",
        fieldValues: [
            {
                fieldContractId: "field-contract-1",
                schemaFieldBindingId: "binding-1",
                namespace: "process",
                fieldKey: "reported-summary",
                label: "Reported summary",
                valueType: FieldValueType.SHORT_TEXT,
                isEmpty: true,
                value: null,
            },
        ],
        responseRevision: 1,
        updatedAt: "2026-09-08T00:00:00Z",
    },
];

const responseFor = (
    runtimeRequirementId: string,
    sourceTemplateBindingId: string,
    occurrencePath: string,
    valueSetETag: string,
    binding: SchemaFieldBindingDto,
    value: unknown = null,
): InformationRequestResponseDto => ({
    informationRequestRequirementId: runtimeRequirementId,
    sourceTemplateRequirementId: sourceTemplateBindingId,
    sourceTemplateBindingId,
    occurrencePath,
    disposition: InformationRequestResponseDisposition.NOT_ANSWERED,
    fieldValueSetId: `value-set-${runtimeRequirementId}`,
    fieldValueSetETag: valueSetETag,
    fieldValues: [
        {
            fieldContractId: binding.fieldContractId,
            schemaFieldBindingId: binding.id,
            namespace: binding.namespace,
            fieldKey: binding.fieldKey,
            label: binding.label,
            valueType: binding.valueType,
            isEmpty: value === null || value === undefined || value === "",
            value,
        },
    ],
    responseRevision: 1,
    updatedAt: "2026-09-08T00:00:00Z",
});

const renderWorkspace = (enabled = true) => render(
    <InformationRequestStructuredResponseWorkspace request={request}
                                                   responseETag={"\"responses:1\""}
                                                   enabled={enabled}
                                                   groups={[group]}
                                                   occurrences={[
                                                       {
                                                           id: "occurrence-1",
                                                           informationRequestId: "request-1",
                                                           sourceTemplateGroupId: "group-1",
                                                           occurrenceIndex: 0,
                                                           occurrencePath: "reported-item[0]",
                                                           createdAt: "2026-09-08T00:00:00Z",
                                                       },
                                                   ]}
                                                   requirements={[
                                                       requirement(
                                                           "requirement-1",
                                                           "reported-summary",
                                                           "Provide the reported summary",
                                                           "field-definition-1",
                                                           "collect-detail",
                                                       ),
                                                       requirement(
                                                           "requirement-2",
                                                           "hidden-summary",
                                                           "Provide the hidden summary",
                                                           "field-definition-2",
                                                           "collect-hidden-detail",
                                                       ),
                                                   ]}
                                                   bindings={[editableBinding, hiddenBinding]}
                                                   responses={responses}
                                                   onSaveResponses={onSaveResponses}
                                                   onAddOccurrence={onAddOccurrence}
                                                   onRemoveOccurrence={onRemoveOccurrence}
                                                   onReorderOccurrences={onReorderOccurrences}
                                                   onRefresh={onRefresh}/>
);

const occurrence = (id: string, index: number, path: string): InformationRequestGroupOccurrenceDto => ({
    id,
    informationRequestId: "request-1",
    sourceTemplateGroupId: "group-1",
    occurrenceIndex: index,
    occurrencePath: path,
    createdAt: "2026-09-08T00:00:00Z",
});

const nestedOccurrence = (
    id: string,
    index: number,
    path: string,
    parentOccurrenceId: string,
): InformationRequestGroupOccurrenceDto => ({
    id,
    informationRequestId: "request-1",
    sourceTemplateGroupId: "group-2",
    parentOccurrenceId,
    occurrenceIndex: index,
    occurrencePath: path,
    createdAt: "2026-09-08T00:00:00Z",
});

const nestedFieldBinding: SchemaFieldBindingDto = {
    ...editableBinding,
    id: "binding-entry",
    fieldContractId: "field-contract-entry",
    fieldDefinitionId: "field-definition-entry",
    fieldKey: "entry-summary",
    label: "Entry summary",
};

const deepGroup: InformationRequestTemplateGroupDto = {
    id: "group-3",
    groupKey: "detail",
    parentGroupKey: "entry",
    minOccurrences: 0,
    maxOccurrences: 2,
};

const deepFieldBinding: SchemaFieldBindingDto = {
    ...editableBinding,
    id: "binding-detail",
    fieldContractId: "field-contract-detail",
    fieldDefinitionId: "field-definition-detail",
    fieldKey: "detail-summary",
    label: "Detail summary",
};

const changeRequirementValue = (requirementElementId: string, value: string) =>
{
    const input = document.getElementById(requirementElementId)?.querySelector("input") as HTMLInputElement | null;
    if (!input) throw new Error(`Missing input for ${requirementElementId}`);
    fireEvent.change(input, {target: {value}});
};

const renderTwoOccurrences = () => render(
    <InformationRequestStructuredResponseWorkspace request={{
                                                       ...request,
                                                       conditionEvaluations: [
                                                           {
                                                               ruleKey: "collect-detail",
                                                               expressionVersion: 1,
                                                               state: InformationRequestConditionEvaluationState.TRUE,
                                                               occurrencePath: "reported-item[0]",
                                                           },
                                                           {
                                                               ruleKey: "collect-detail",
                                                               expressionVersion: 1,
                                                               state: InformationRequestConditionEvaluationState.FALSE,
                                                               occurrencePath: "reported-item[1]",
                                                           },
                                                       ],
                                                   }}
                                                   responseETag={"\"responses:1\""}
                                                   enabled={true}
                                                   groups={[group]}
                                                   occurrences={[
                                                       occurrence("occurrence-1", 0, "reported-item[0]"),
                                                       occurrence("occurrence-2", 1, "reported-item[1]"),
                                                   ]}
                                                   requirements={[
                                                       requirement(
                                                           "requirement-1",
                                                           "reported-summary",
                                                           "Provide the reported summary",
                                                           "field-definition-1",
                                                           "collect-detail",
                                                       ),
                                                   ]}
                                                   bindings={[editableBinding]}
                                                   responses={responses}
                                                   onSaveResponses={onSaveResponses}
                                                   onAddOccurrence={onAddOccurrence}
                                                   onRemoveOccurrence={onRemoveOccurrence}
                                                   onReorderOccurrences={onReorderOccurrences}
                                                   onRefresh={onRefresh}/>
);

const renderRootRequirement = () => render(
    <InformationRequestStructuredResponseWorkspace request={request}
                                                   responseETag={"\"responses:1\""}
                                                   enabled={true}
                                                   groups={[]}
                                                   occurrences={[]}
                                                   requirements={[
                                                       {
                                                           ...requirement(
                                                               "requirement-root",
                                                               "root-summary",
                                                               "Provide the root summary",
                                                               "field-definition-1",
                                                           ),
                                                           occurrenceAnchorKey: undefined,
                                                       },
                                                   ]}
                                                   bindings={[editableBinding]}
                                                   responses={[
                                                       {
                                                           informationRequestRequirementId: "requirement-root",
                                                           sourceTemplateRequirementId: "requirement-root",
                                                           sourceTemplateBindingId: "requirement-root",
                                                           occurrencePath: "root",
                                                           disposition: InformationRequestResponseDisposition.NOT_ANSWERED,
                                                           fieldValueSetETag: "\"field-set:root\"",
                                                           fieldValues: [
                                                               {
                                                                   fieldContractId: "field-contract-1",
                                                                   schemaFieldBindingId: "binding-1",
                                                                   namespace: "process",
                                                                   fieldKey: "reported-summary",
                                                                   label: "Reported summary",
                                                                   valueType: FieldValueType.SHORT_TEXT,
                                                                   isEmpty: true,
                                                                   value: null,
                                                               },
                                                           ],
                                                           responseRevision: 1,
                                                           updatedAt: "2026-09-08T00:00:00Z",
                                                       },
                                                   ]}
                                                   onSaveResponses={onSaveResponses}
                                                   onAddOccurrence={onAddOccurrence}
                                                   onRemoveOccurrence={onRemoveOccurrence}
                                                   onReorderOccurrences={onReorderOccurrences}
                                                   onRefresh={onRefresh}/>
);

describe("InformationRequestStructuredResponseWorkspace", () =>
{
    beforeEach(() =>
    {
        vi.clearAllMocks();
        onSaveResponses.mockResolvedValue({
            outcome: "SAVED",
            responseETag: "\"responses:2\"",
            responses: [],
        });
        onAddOccurrence.mockResolvedValue({
            outcome: "SAVED",
            responseETag: "\"responses:2\"",
            occurrences: [],
        });
        onReorderOccurrences.mockResolvedValue({
            outcome: "SAVED",
            responseETag: "\"responses:2\"",
            occurrences: [],
        });
    });

    afterEach(cleanup);

    it("hides the workspace while the feature switch is off", () =>
    {
        renderWorkspace(false);

        expect(document.getElementById("information-request-response-workspace-disabled")).toBeTruthy();
        expect(screen.queryByText("Provide the reported summary")).toBeNull();
    });

    it("saves active Field Requirements sparsely and exposes occurrence commands", async () =>
    {
        renderWorkspace();

        expect(screen.getByText("collect-hidden-detail is inactive")).toBeTruthy();
        expect(screen.queryByText("Provide the hidden summary")).toBeNull();

        fireEvent.change(document.querySelector("#exchange-field-field-contract-1")!, {
            target: {value: "Updated summary"},
        });
        fireEvent.click(screen.getByRole("button", {name: "Save responses"}));

        await waitFor(() => expect(onSaveResponses).toHaveBeenCalledWith(
            "request-1",
            {
                patches: [
                    {
                        requirementId: "requirement-1",
                        disposition: InformationRequestResponseDisposition.PROVIDED,
                        fieldValues: {
                            etag: "\"field-set:1\"",
                            values: [
                                {
                                    fieldContractId: "field-contract-1",
                                    value: "Updated summary",
                                },
                            ],
                        },
                    },
                ],
                confirmedHiddenResponseClearRequirementIds: [],
            },
            "\"responses:1\"",
        ));

        fireEvent.click(screen.getByRole("button", {name: "Add reported-item"}));

        await waitFor(() => expect(onAddOccurrence).toHaveBeenCalledWith(
            "request-1",
            {groupKey: "reported-item"},
            "\"responses:2\"",
        ));
    });

    it("shows a conditional Requirement only in the occurrences whose own rule is true", async () =>
    {
        renderTwoOccurrences();

        expect(document.querySelectorAll("#information-request-response-occurrences [id^='information-request-response-prompt-']"))
            .toHaveLength(1);
        expect(document.getElementById("information-request-response-prompt-reported-item-0-reported-summary"))
            .toBeTruthy();
        expect(document.getElementById("information-request-response-prompt-reported-item-1-reported-summary"))
            .toBeNull();

        fireEvent.change(document.querySelector("#exchange-field-field-contract-1")!, {
            target: {value: "Updated summary"},
        });
        fireEvent.click(screen.getByRole("button", {name: "Save responses"}));

        await waitFor(() => expect(onSaveResponses).toHaveBeenCalledTimes(1));
        expect(onSaveResponses.mock.calls[0][1].patches).toHaveLength(1);
    });

    it("sends parent occurrence identity for nested add and reorder commands", async () =>
    {
        render(
            <InformationRequestStructuredResponseWorkspace request={request}
                                                           responseETag={"\"responses:1\""}
                                                           enabled={true}
                                                           groups={[group, nestedGroup]}
                                                           occurrences={[
                                                               occurrence("parent-1", 0, "reported-item[0]"),
                                                               occurrence("parent-2", 1, "reported-item[1]"),
                                                               nestedOccurrence(
                                                                   "child-1",
                                                                   0,
                                                                   "reported-item[0]/entry[0]",
                                                                   "parent-1",
                                                               ),
                                                               nestedOccurrence(
                                                                   "child-2",
                                                                   1,
                                                                   "reported-item[0]/entry[1]",
                                                                   "parent-1",
                                                               ),
                                                               nestedOccurrence(
                                                                   "child-3",
                                                                   0,
                                                                   "reported-item[1]/entry[0]",
                                                                   "parent-2",
                                                               ),
                                                           ]}
                                                           requirements={[]}
                                                           bindings={[]}
                                                           responses={[]}
                                                           onSaveResponses={onSaveResponses}
                                                           onAddOccurrence={onAddOccurrence}
                                                           onRemoveOccurrence={onRemoveOccurrence}
                                                           onReorderOccurrences={onReorderOccurrences}
                                                           onRefresh={onRefresh}/>,
        );

        fireEvent.click(document.getElementById("information-request-add-child-occurrence-parent-1-entry")!);

        await waitFor(() => expect(onAddOccurrence).toHaveBeenCalledWith(
            "request-1",
            {groupKey: "entry", parentOccurrenceId: "parent-1"},
            "\"responses:1\"",
        ));

        fireEvent.click(document.getElementById("information-request-occurrence-down-child-1")!);

        await waitFor(() => expect(onReorderOccurrences).toHaveBeenCalledWith(
            "request-1",
            {
                groupKey: "entry",
                parentOccurrenceId: "parent-1",
                occurrenceIds: ["child-2", "child-1"],
            },
            "\"responses:2\"",
        ));
    });

    it("saves a child-only nested Field edit through the Save handler", async () =>
    {
        render(
            <InformationRequestStructuredResponseWorkspace request={{
                                                               ...request,
                                                               conditionEvaluations: [],
                                                           }}
                                                           responseETag={"\"responses:1\""}
                                                           enabled={true}
                                                           groups={[group, nestedGroup]}
                                                           occurrences={[
                                                               occurrence("parent-1", 0, "reported-item[0]"),
                                                               nestedOccurrence(
                                                                   "child-1",
                                                                   0,
                                                                   "reported-item[0]/entry[0]",
                                                                   "parent-1",
                                                               ),
                                                           ]}
                                                           requirements={[
                                                               requirement(
                                                                   "requirement-entry",
                                                                   "entry-summary",
                                                                   "Provide the entry summary",
                                                                   "field-definition-entry",
                                                                   undefined,
                                                                   "entry",
                                                               ),
                                                           ]}
                                                           bindings={[nestedFieldBinding]}
                                                           responses={[
                                                               responseFor(
                                                                   "runtime-entry-1",
                                                                   "requirement-entry",
                                                                   "reported-item[0]/entry[0]",
                                                                   "\"field-set:entry-1\"",
                                                                   nestedFieldBinding,
                                                               ),
                                                           ]}
                                                           onSaveResponses={onSaveResponses}
                                                           onAddOccurrence={onAddOccurrence}
                                                           onRemoveOccurrence={onRemoveOccurrence}
                                                           onReorderOccurrences={onReorderOccurrences}
                                                           onRefresh={onRefresh}/>,
        );

        changeRequirementValue(
            "information-request-response-requirement-reported-item-0-entry-0-entry-summary",
            "Child entry",
        );
        fireEvent.click(screen.getByRole("button", {name: "Save responses"}));

        await waitFor(() => expect(onSaveResponses).toHaveBeenCalledWith(
            "request-1",
            {
                patches: [
                    {
                        requirementId: "runtime-entry-1",
                        disposition: InformationRequestResponseDisposition.PROVIDED,
                        fieldValues: {
                            etag: "\"field-set:entry-1\"",
                            values: [
                                {
                                    fieldContractId: "field-contract-entry",
                                    value: "Child entry",
                                },
                            ],
                        },
                    },
                ],
                confirmedHiddenResponseClearRequirementIds: [],
            },
            "\"responses:1\"",
        ));
    });

    it("saves parent, sibling child, and deeper nested Field edits with runtime Requirement identity", async () =>
    {
        render(
            <InformationRequestStructuredResponseWorkspace request={{
                                                               ...request,
                                                               conditionEvaluations: [],
                                                           }}
                                                           responseETag={"\"responses:1\""}
                                                           enabled={true}
                                                           groups={[group, nestedGroup, deepGroup]}
                                                           occurrences={[
                                                               occurrence("parent-1", 0, "reported-item[0]"),
                                                               occurrence("parent-2", 1, "reported-item[1]"),
                                                               nestedOccurrence(
                                                                   "child-1",
                                                                   0,
                                                                   "reported-item[0]/entry[0]",
                                                                   "parent-1",
                                                               ),
                                                               nestedOccurrence(
                                                                   "child-3",
                                                                   0,
                                                                   "reported-item[1]/entry[0]",
                                                                   "parent-2",
                                                               ),
                                                               {
                                                                   ...nestedOccurrence(
                                                                       "detail-1",
                                                                       0,
                                                                       "reported-item[0]/entry[0]/detail[0]",
                                                                       "child-1",
                                                                   ),
                                                                   sourceTemplateGroupId: "group-3",
                                                               },
                                                           ]}
                                                           requirements={[
                                                               requirement(
                                                                   "requirement-parent",
                                                                   "reported-summary",
                                                                   "Provide the reported summary",
                                                                   "field-definition-1",
                                                               ),
                                                               requirement(
                                                                   "requirement-entry",
                                                                   "entry-summary",
                                                                   "Provide the entry summary",
                                                                   "field-definition-entry",
                                                                   undefined,
                                                                   "entry",
                                                               ),
                                                               requirement(
                                                                   "requirement-detail",
                                                                   "detail-summary",
                                                                   "Provide the detail summary",
                                                                   "field-definition-detail",
                                                                   undefined,
                                                                   "detail",
                                                               ),
                                                           ]}
                                                           bindings={[
                                                               editableBinding,
                                                               nestedFieldBinding,
                                                               deepFieldBinding,
                                                           ]}
                                                           responses={[
                                                               responseFor(
                                                                   "runtime-parent-1",
                                                                   "requirement-parent",
                                                                   "reported-item[0]",
                                                                   "\"field-set:parent-1\"",
                                                                   editableBinding,
                                                               ),
                                                               responseFor(
                                                                   "runtime-entry-1",
                                                                   "requirement-entry",
                                                                   "reported-item[0]/entry[0]",
                                                                   "\"field-set:entry-1\"",
                                                                   nestedFieldBinding,
                                                               ),
                                                               responseFor(
                                                                   "runtime-entry-3",
                                                                   "requirement-entry",
                                                                   "reported-item[1]/entry[0]",
                                                                   "\"field-set:entry-3\"",
                                                                   nestedFieldBinding,
                                                               ),
                                                               responseFor(
                                                                   "runtime-detail-1",
                                                                   "requirement-detail",
                                                                   "reported-item[0]/entry[0]/detail[0]",
                                                                   "\"field-set:detail-1\"",
                                                                   deepFieldBinding,
                                                               ),
                                                           ]}
                                                           onSaveResponses={onSaveResponses}
                                                           onAddOccurrence={onAddOccurrence}
                                                           onRemoveOccurrence={onRemoveOccurrence}
                                                           onReorderOccurrences={onReorderOccurrences}
                                                           onRefresh={onRefresh}/>,
        );

        changeRequirementValue(
            "information-request-response-requirement-reported-item-0-reported-summary",
            "Parent summary",
        );
        changeRequirementValue(
            "information-request-response-requirement-reported-item-0-entry-0-entry-summary",
            "First branch child",
        );
        changeRequirementValue(
            "information-request-response-requirement-reported-item-1-entry-0-entry-summary",
            "Second branch child",
        );
        changeRequirementValue(
            "information-request-response-requirement-reported-item-0-entry-0-detail-0-detail-summary",
            "Deep detail",
        );
        fireEvent.click(screen.getByRole("button", {name: "Save responses"}));

        await waitFor(() => expect(onSaveResponses).toHaveBeenCalledTimes(1));
        expect(onSaveResponses.mock.calls[0][1].patches).toEqual([
            {
                requirementId: "runtime-parent-1",
                disposition: InformationRequestResponseDisposition.PROVIDED,
                fieldValues: {
                    etag: "\"field-set:parent-1\"",
                    values: [
                        {
                            fieldContractId: "field-contract-1",
                            value: "Parent summary",
                        },
                    ],
                },
            },
            {
                requirementId: "runtime-entry-1",
                disposition: InformationRequestResponseDisposition.PROVIDED,
                fieldValues: {
                    etag: "\"field-set:entry-1\"",
                    values: [
                        {
                            fieldContractId: "field-contract-entry",
                            value: "First branch child",
                        },
                    ],
                },
            },
            {
                requirementId: "runtime-entry-3",
                disposition: InformationRequestResponseDisposition.PROVIDED,
                fieldValues: {
                    etag: "\"field-set:entry-3\"",
                    values: [
                        {
                            fieldContractId: "field-contract-entry",
                            value: "Second branch child",
                        },
                    ],
                },
            },
            {
                requirementId: "runtime-detail-1",
                disposition: InformationRequestResponseDisposition.PROVIDED,
                fieldValues: {
                    etag: "\"field-set:detail-1\"",
                    values: [
                        {
                            fieldContractId: "field-contract-detail",
                            value: "Deep detail",
                        },
                    ],
                },
            },
        ]);
    });

    it("renders and saves a root Field Requirement without occurrence controls", async () =>
    {
        renderRootRequirement();

        expect(screen.getByText("Provide the root summary")).toBeTruthy();
        expect(screen.queryByRole("button", {name: "Move occurrence up"})).toBeNull();

        fireEvent.change(document.querySelector("#exchange-field-field-contract-1")!, {
            target: {value: "Root summary"},
        });
        fireEvent.click(screen.getByRole("button", {name: "Save responses"}));

        await waitFor(() => expect(onSaveResponses).toHaveBeenCalledWith(
            "request-1",
            {
                patches: [
                    {
                        requirementId: "requirement-root",
                        disposition: InformationRequestResponseDisposition.PROVIDED,
                        fieldValues: {
                            etag: "\"field-set:root\"",
                            values: [
                                {
                                    fieldContractId: "field-contract-1",
                                    value: "Root summary",
                                },
                            ],
                        },
                    },
                ],
                confirmedHiddenResponseClearRequirementIds: [],
            },
            "\"responses:1\"",
        ));
    });

    it("shows stale conflict feedback without retrying the failed save", async () =>
    {
        onSaveResponses.mockResolvedValueOnce({outcome: "STALE"});
        renderWorkspace();

        fireEvent.change(document.querySelector("#exchange-field-field-contract-1")!, {
            target: {value: "Updated summary"},
        });
        fireEvent.click(screen.getByRole("button", {name: "Save responses"}));

        expect(await screen.findByText("These responses changed after this workspace loaded, so your save was not applied."))
            .toBeTruthy();
        expect(onRefresh).toHaveBeenCalledTimes(1);
        expect(onSaveResponses).toHaveBeenCalledTimes(1);
    });

    it("recovers from a refused save and preserves the edit for retry", async () =>
    {
        onSaveResponses
            .mockRejectedValueOnce({errorMessage: "Access denied to save responses"})
            .mockResolvedValueOnce({
                outcome: "SAVED",
                responseETag: "\"responses:2\"",
                responses: [],
            });
        renderWorkspace();

        fireEvent.change(document.querySelector("#exchange-field-field-contract-1")!, {
            target: {value: "Updated summary"},
        });
        fireEvent.click(screen.getByRole("button", {name: "Save responses"}));

        expect(await screen.findByText("Access denied to save responses")).toBeTruthy();
        expect((screen.getByRole("button", {name: "Save responses"}) as HTMLButtonElement).disabled).toBe(false);
        expect((document.querySelector("#exchange-field-field-contract-1") as HTMLInputElement).value)
            .toBe("Updated summary");

        fireEvent.click(screen.getByRole("button", {name: "Save responses"}));

        await waitFor(() => expect(onSaveResponses).toHaveBeenCalledTimes(2));
        expect(onSaveResponses.mock.calls[1]).toEqual([
            "request-1",
            {
                patches: [
                    {
                        requirementId: "requirement-1",
                        disposition: InformationRequestResponseDisposition.PROVIDED,
                        fieldValues: {
                            etag: "\"field-set:1\"",
                            values: [
                                {
                                    fieldContractId: "field-contract-1",
                                    value: "Updated summary",
                                },
                            ],
                        },
                    },
                ],
                confirmedHiddenResponseClearRequirementIds: [],
            },
            "\"responses:1\"",
        ]);
    });

    it("recovers when a group command is refused", async () =>
    {
        onAddOccurrence.mockRejectedValueOnce({errorMessage: "Access denied to add occurrence"});
        renderWorkspace();

        fireEvent.click(screen.getByRole("button", {name: "Add reported-item"}));

        expect(await screen.findByText("Access denied to add occurrence")).toBeTruthy();
        expect((screen.getByRole("button", {name: "Add reported-item"}) as HTMLButtonElement).disabled).toBe(false);
    });

    it("requires explicit confirmation before saving a clear for inactive hidden responses", async () =>
    {
        render(
            <InformationRequestStructuredResponseWorkspace request={request}
                                                           responseETag={"\"responses:1\""}
                                                           enabled={true}
                                                           groups={[group]}
                                                           conditionRules={[clearWithConfirmationRule]}
                                                           occurrences={[occurrence(
                                                               "occurrence-1",
                                                               0,
                                                               "reported-item[0]",
                                                           )]}
                                                           requirements={[
                                                               requirement(
                                                                   "requirement-1",
                                                                   "reported-summary",
                                                                   "Provide the reported summary",
                                                                   "field-definition-1",
                                                                   "collect-detail",
                                                               ),
                                                               requirement(
                                                                   "requirement-2",
                                                                   "hidden-summary",
                                                                   "Provide the hidden summary",
                                                                   "field-definition-2",
                                                                   "collect-hidden-detail",
                                                               ),
                                                           ]}
                                                           bindings={[editableBinding, hiddenBinding]}
                                                           responses={[
                                                               responses[0],
                                                               responseFor(
                                                                   "hidden-runtime",
                                                                   "requirement-2",
                                                                   "reported-item[0]",
                                                                   "\"field-set:hidden\"",
                                                                   hiddenBinding,
                                                                   "Hidden saved value",
                                                               ),
                                                           ]}
                                                           onSaveResponses={onSaveResponses}
                                                           onAddOccurrence={onAddOccurrence}
                                                           onRemoveOccurrence={onRemoveOccurrence}
                                                           onReorderOccurrences={onReorderOccurrences}
                                                           onRefresh={onRefresh}/>,
        );

        fireEvent.change(document.querySelector("#exchange-field-field-contract-1")!, {
            target: {value: "Updated summary"},
        });
        fireEvent.click(screen.getByRole("button", {name: "Save responses"}));

        expect(await screen.findByText("Confirm clearing hidden response data before saving.")).toBeTruthy();
        expect(onSaveResponses).not.toHaveBeenCalled();

        fireEvent.click(document.getElementById("information-request-confirm-clear-requirement-hidden-runtime")!);
        fireEvent.click(screen.getByRole("button", {name: "Save responses"}));

        await waitFor(() => expect(onSaveResponses).toHaveBeenCalledWith(
            "request-1",
            {
                patches: [
                    {
                        requirementId: "requirement-1",
                        disposition: InformationRequestResponseDisposition.PROVIDED,
                        fieldValues: {
                            etag: "\"field-set:1\"",
                            values: [
                                {
                                    fieldContractId: "field-contract-1",
                                    value: "Updated summary",
                                },
                            ],
                        },
                    },
                ],
                confirmedHiddenResponseClearRequirementIds: ["hidden-runtime"],
            },
            "\"responses:1\"",
        ));
    });

    it("cancels hidden response clear confirmation when the respondent unchecks it", async () =>
    {
        render(
            <InformationRequestStructuredResponseWorkspace request={request}
                                                           responseETag={"\"responses:1\""}
                                                           enabled={true}
                                                           groups={[group]}
                                                           conditionRules={[clearWithConfirmationRule]}
                                                           occurrences={[occurrence(
                                                               "occurrence-1",
                                                               0,
                                                               "reported-item[0]",
                                                           )]}
                                                           requirements={[
                                                               requirement(
                                                                   "requirement-1",
                                                                   "reported-summary",
                                                                   "Provide the reported summary",
                                                                   "field-definition-1",
                                                                   "collect-detail",
                                                               ),
                                                               requirement(
                                                                   "requirement-2",
                                                                   "hidden-summary",
                                                                   "Provide the hidden summary",
                                                                   "field-definition-2",
                                                                   "collect-hidden-detail",
                                                               ),
                                                           ]}
                                                           bindings={[editableBinding, hiddenBinding]}
                                                           responses={[
                                                               responses[0],
                                                               responseFor(
                                                                   "hidden-runtime",
                                                                   "requirement-2",
                                                                   "reported-item[0]",
                                                                   "\"field-set:hidden\"",
                                                                   hiddenBinding,
                                                                   "Hidden saved value",
                                                               ),
                                                           ]}
                                                           onSaveResponses={onSaveResponses}
                                                           onAddOccurrence={onAddOccurrence}
                                                           onRemoveOccurrence={onRemoveOccurrence}
                                                           onReorderOccurrences={onReorderOccurrences}
                                                           onRefresh={onRefresh}/>,
        );

        fireEvent.change(document.querySelector("#exchange-field-field-contract-1")!, {
            target: {value: "Updated summary"},
        });
        const checkbox = document.getElementById(
            "information-request-confirm-clear-requirement-hidden-runtime",
        )!;
        fireEvent.click(checkbox);
        fireEvent.click(checkbox);
        fireEvent.click(screen.getByRole("button", {name: "Save responses"}));

        expect(await screen.findByText("Confirm clearing hidden response data before saving.")).toBeTruthy();
        expect(onSaveResponses).not.toHaveBeenCalled();
    });
});
