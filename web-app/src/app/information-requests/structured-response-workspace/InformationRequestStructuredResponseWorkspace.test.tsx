/** @vitest-environment jsdom */
import {cleanup, fireEvent, render, screen, waitFor} from "@testing-library/react";
import {afterEach, beforeEach, describe, expect, it, vi} from "vitest";
import {
    FieldDataClassification,
    FieldValueType,
    InformationRequestConditionEvaluationState,
    InformationRequestContributorRole,
    InformationRequestDto,
    InformationRequestExecutionStandingKind,
    InformationRequestGroupOccurrenceDto,
    InformationRequestOwnerType,
    InformationRequestRequiredness,
    InformationRequestRequirementType,
    InformationRequestResponseDisposition,
    InformationRequestResponseDto,
    InformationRequestResponseMode,
    InformationRequestReviewPolicy,
    InformationRequestState,
    InformationRequestTemplateGroupDto,
    InformationRequestTemplateRequirementDto,
    SchemaFieldBindingDto,
} from "../../models/models.tsx";
import InformationRequestStructuredResponseWorkspace from "./InformationRequestStructuredResponseWorkspace.tsx";
import {duplicateIds} from "../shared/testing/duplicateIds.ts";
import {unnamedControls} from "../shared/testing/unnamedControls.ts";

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
    createdAt: Date.parse("2026-09-08T00:00:00Z"),
    updatedAt: Date.parse("2026-09-08T00:00:00Z"),
    requestETag: "\"request:3\"",
    executionStanding: {kind: InformationRequestExecutionStandingKind.ACTIVE},
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
        updatedAt: Date.parse("2026-09-08T00:00:00Z"),
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
    updatedAt: Date.parse("2026-09-08T00:00:00Z"),
});

const renderWorkspace = () => render(
    <InformationRequestStructuredResponseWorkspace request={request}
                                                   respondentKey={"respondent-a"}
                                                   responseETag={"\"responses:1\""}
                                                   groups={[group]}
                                                   occurrences={[
                                                       {
                                                           id: "occurrence-1",
                                                           informationRequestId: "request-1",
                                                           sourceTemplateGroupId: "group-1",
                                                           occurrenceIndex: 0,
                                                           occurrencePath: "reported-item[0]",
                                                           createdAt: Date.parse("2026-09-08T00:00:00Z"),
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
    createdAt: Date.parse("2026-09-08T00:00:00Z"),
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
    createdAt: Date.parse("2026-09-08T00:00:00Z"),
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
    const input = document.getElementById(requirementElementId)?.querySelector("input:not([type='radio'])") as HTMLInputElement | null;
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
                                                   respondentKey={"respondent-a"}
                                                   responseETag={"\"responses:1\""}
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
                                                   respondentKey={"respondent-a"}
                                                   responseETag={"\"responses:1\""}
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
                                                           occurrenceAnchorKey: undefined as string | undefined,
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
                                                           updatedAt: Date.parse("2026-09-08T00:00:00Z"),
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
        window.sessionStorage.clear();
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

    it("names every control and region of the response form", () =>
    {
        renderWorkspace();

        expect(unnamedControls(document.body)).toEqual([]);

        expect(duplicateIds(document.body)).toEqual([]);
    });

    it("names each answer and entry control after the item or entry it belongs to", () => {
        renderTwoOccurrences();

        expect(screen.getByRole("radiogroup", {name: "Your answer Provide the reported summary"})).toBeTruthy();
        fireEvent.click(screen.getByRole("radio", {name: "Not applicable"}));
        expect(screen.getByRole("textbox", {name: "Explain your answer Provide the reported summary"})).toBeTruthy();
        ["Move Reported item 1 down", "Move Reported item 2 up", "Remove Reported item 1", "Remove Reported item 2"]
            .forEach(name => expect(screen.getByRole("button", {name})).toBeTruthy());
        cleanup();

        render(
            <InformationRequestStructuredResponseWorkspace request={{...request, conditionEvaluations: []}}
                                                           respondentKey={"respondent-a"}
                                                           responseETag={"\"responses:1\""}
                                                           groups={[group, nestedGroup]}
                                                           occurrences={[
                                                               occurrence("parent-1", 0, "reported-item[0]"),
                                                               occurrence("parent-2", 1, "reported-item[1]"),
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

        expect(screen.getByRole("button", {name: "Add entry to Reported item 1"})).toBeTruthy();
        expect(screen.getByRole("button", {name: "Add entry to Reported item 2"})).toBeTruthy();
    });

    it.each([
        [InformationRequestState.CLOSED, InformationRequestExecutionStandingKind.ACTIVE],
        [InformationRequestState.CANCELLED, InformationRequestExecutionStandingKind.ACTIVE],
        [InformationRequestState.SUPERSEDED, InformationRequestExecutionStandingKind.ACTIVE],
        [InformationRequestState.EXPIRED, InformationRequestExecutionStandingKind.ACTIVE],
        [InformationRequestState.IN_PROGRESS, InformationRequestExecutionStandingKind.OPERATIONALLY_SUSPENDED],
        [InformationRequestState.IN_PROGRESS, InformationRequestExecutionStandingKind.EXECUTION_GRANT_REVOKED],
    ])("offers no change to answers or entries while the request is %s and %s", (state, kind) => {
        render(
            <InformationRequestStructuredResponseWorkspace request={{...request, state, executionStanding: {kind}}}
                                                           respondentKey={"respondent-a"}
                                                           responseETag={"\"responses:1\""}
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
                                                               ),
                                                           ]}
                                                           bindings={[editableBinding]}
                                                           responses={responses}
                                                           onSaveResponses={onSaveResponses}
                                                           onAddOccurrence={onAddOccurrence}
                                                           onRemoveOccurrence={onRemoveOccurrence}
                                                           onReorderOccurrences={onReorderOccurrences}
                                                           onRefresh={onRefresh}/>,
        );

        const disabled = (id: string) => (document.getElementById(id) as HTMLButtonElement | HTMLInputElement).disabled;
        expect(disabled("information-request-response-field-reported-item_0-reported-summary")).toBe(true);
        screen.getAllByRole("radio").forEach(radio => expect((radio as HTMLInputElement).disabled).toBe(true));
        [
            "information-request-add-occurrence-reported-item",
            "information-request-occurrence-down-occurrence-1",
            "information-request-occurrence-up-occurrence-2",
            "information-request-occurrence-remove-occurrence-1",
            "information-request-response-save",
        ].forEach(id => expect(disabled(id)).toBe(true));
        expect(screen.getByRole("status").textContent).toBe("Answers cannot be changed now.");
    });

    const entriesWorkspace = (groups: InformationRequestTemplateGroupDto[], occurrences: InformationRequestGroupOccurrenceDto[]) => (
        <InformationRequestStructuredResponseWorkspace request={{...request, conditionEvaluations: []}}
                                                       respondentKey={"respondent-a"}
                                                       responseETag={"\"responses:1\""}
                                                       groups={groups}
                                                       occurrences={occurrences}
                                                       requirements={[]}
                                                       bindings={[]}
                                                       responses={[]}
                                                       onSaveResponses={onSaveResponses}
                                                       onAddOccurrence={onAddOccurrence}
                                                       onRemoveOccurrence={onRemoveOccurrence}
                                                       onReorderOccurrences={onReorderOccurrences}
                                                       onRefresh={onRefresh}/>
    );

    const isDisabled = (id: string) => (document.getElementById(id) as HTMLButtonElement).disabled;

    it("shows beside an answer the document that supports it, and beside the document the answer it supports", () => {
        const answer = {
            ...requirement("requirement-answer", "reported-summary", "Provide the reported summary", "field-definition-1"),
            occurrenceAnchorKey: undefined as string | undefined,
        };
        const supportingDocument = {
            ...requirement("requirement-document", "supporting-record", "Attach the supporting record", "field-definition-unused"),
            requirementType: InformationRequestRequirementType.DOCUMENT,
            collectedFieldDefinitionId: undefined as string | undefined,
            occurrenceAnchorKey: undefined as string | undefined,
        };
        render(
            <InformationRequestStructuredResponseWorkspace request={{...request, conditionEvaluations: []}}
                                                           respondentKey={"respondent-a"}
                                                           responseETag={"\"responses:1\""}
                                                           groups={[]}
                                                           occurrences={[]}
                                                           requirements={[answer, supportingDocument]}
                                                           bindings={[editableBinding]}
                                                           responses={[
                                                               responseFor("runtime-answer", "requirement-answer", "root", "\"field-set:a\"", editableBinding),
                                                               {
                                                                   ...responseFor("runtime-document", "requirement-document", "root", "\"field-set:d\"", editableBinding),
                                                                   fieldValues: [],
                                                               },
                                                           ]}
                                                           supportingEvidenceLinks={[{
                                                               supportedRequirementId: "runtime-answer",
                                                               supportingRequirementId: "runtime-document",
                                                           }]}
                                                           onSaveResponses={onSaveResponses}
                                                           onAddOccurrence={onAddOccurrence}
                                                           onRemoveOccurrence={onRemoveOccurrence}
                                                           onReorderOccurrences={onReorderOccurrences}
                                                           onRefresh={onRefresh}/>,
        );

        expect(document.getElementById("information-request-supporting-evidence-root-reported-summary")?.textContent)
            .toBe("Supported by: Attach the supporting record");
        expect(document.getElementById("information-request-supporting-evidence-root-supporting-record")?.textContent)
            .toBe("Supports: Provide the reported summary");
    });

    it("says when nothing in the request is there for the respondent to answer", () => {
        render(entriesWorkspace([], []));

        expect(document.getElementById("information-request-response-nothing-to-answer")?.textContent)
            .toBe("There is nothing for you to answer here right now.");
        expect(screen.queryByRole("button", {name: "Save responses"})).toBeNull();
    });

    it("offers Add only below a section's maximum and Remove only above its minimum", () => {
        const bounded = {...group, minOccurrences: 1, maxOccurrences: 2};
        const view = render(entriesWorkspace([bounded], [
            occurrence("occurrence-1", 0, "reported-item[0]"),
            occurrence("occurrence-2", 1, "reported-item[1]"),
        ]));

        expect(isDisabled("information-request-add-occurrence-reported-item")).toBe(true);
        expect(isDisabled("information-request-occurrence-remove-occurrence-1")).toBe(false);

        view.rerender(entriesWorkspace([bounded], [occurrence("occurrence-1", 0, "reported-item[0]")]));

        expect(isDisabled("information-request-add-occurrence-reported-item")).toBe(false);
        expect(isDisabled("information-request-occurrence-remove-occurrence-1")).toBe(true);
    });

    it("offers a nested Add only below that part's maximum within each entry", () => {
        render(entriesWorkspace([group, {...nestedGroup, maxOccurrences: 1}], [
            occurrence("parent-1", 0, "reported-item[0]"),
            occurrence("parent-2", 1, "reported-item[1]"),
            nestedOccurrence("child-1", 0, "reported-item[0]/entry[0]", "parent-1"),
        ]));

        expect(isDisabled("information-request-add-child-occurrence-parent-1-entry")).toBe(true);
        expect(isDisabled("information-request-add-child-occurrence-parent-2-entry")).toBe(false);
    });

    const creatableWorkspace = (creatableGroupKeys: string[], occurrences: InformationRequestGroupOccurrenceDto[]) => (
        <InformationRequestStructuredResponseWorkspace request={{...request, conditionEvaluations: []}}
                                                       respondentKey={"respondent-a"}
                                                       responseETag={"\"responses:1\""}
                                                       groups={[group, nestedGroup, {
                                                           ...group,
                                                           id: "group-3",
                                                           groupKey: "other-item"
                                                       }]}
                                                       creatableGroupKeys={creatableGroupKeys}
                                                       occurrences={occurrences}
                                                       requirements={[]}
                                                       bindings={[]}
                                                       responses={[]}
                                                       onSaveResponses={onSaveResponses}
                                                       onAddOccurrence={onAddOccurrence}
                                                       onRemoveOccurrence={onRemoveOccurrence}
                                                       onReorderOccurrences={onReorderOccurrences}
                                                       onRefresh={onRefresh}/>
    );

    it("offers Add only for the parts the server lets this respondent add entries to", () => {
        const view = render(creatableWorkspace(["reported-item"], [occurrence("parent-1", 0, "reported-item[0]")]));

        expect(document.getElementById("information-request-add-occurrence-reported-item")).not.toBeNull();
        expect(document.getElementById("information-request-add-occurrence-other-item")).toBeNull();
        expect(document.getElementById("information-request-add-child-occurrence-parent-1-entry")).toBeNull();

        view.rerender(creatableWorkspace([], []));

        expect(document.getElementById("information-request-response-nothing-to-answer")?.textContent)
            .toBe("There is nothing for you to answer here right now.");
    });

    const entryTitles = () => Array.from(document.querySelectorAll("[id^='information-request-occurrence-title-']"))
        .map(title => title.textContent);

    it("shows each nested entry under its parent in sibling order and names every entry by its place", () => {
        render(entriesWorkspace([group, nestedGroup], [
            occurrence("parent-1", 0, "reported-item[0]"),
            nestedOccurrence("child-1", 0, "reported-item[0]/entry[0]", "parent-1"),
            nestedOccurrence("child-3", 0, "reported-item[1]/entry[0]", "parent-2"),
            occurrence("parent-2", 1, "reported-item[1]"),
            nestedOccurrence("child-2", 1, "reported-item[0]/entry[1]", "parent-1"),
        ]));

        expect(entryTitles()).toEqual([
            "Reported item 1",
            "Reported item 1, entry 1",
            "Reported item 1, entry 2",
            "Reported item 2",
            "Reported item 2, entry 1",
        ]);
        expect(document.getElementById("information-request-occurrence-reported-item_0")
            ?.contains(document.getElementById("information-request-occurrence-reported-item_0_entry_1"))).toBe(true);
        expect(document.getElementById("information-request-occurrence-reported-item_0")
            ?.contains(document.getElementById("information-request-occurrence-reported-item_1_entry_0"))).toBe(false);
    });

    it("names entries by their place after a move or a removal, not by the order they were added", () => {
        const view = render(entriesWorkspace([group], [
            occurrence("parent-1", 1, "reported-item[0]"),
            occurrence("parent-2", 0, "reported-item[1]"),
        ]));

        expect(entryTitles()).toEqual(["Reported item 1", "Reported item 2"]);
        expect(document.getElementById("information-request-occurrence-title-parent-2")?.textContent).toBe("Reported item 1");
        expect(screen.getByRole("button", {name: "Move Reported item 1 down"}).id).toBe("information-request-occurrence-down-parent-2");

        view.rerender(entriesWorkspace([group], [occurrence("parent-2", 0, "reported-item[1]")]));

        expect(entryTitles()).toEqual(["Reported item 1"]);
    });

    it("names an entry by its place when a refused save names an answer or a clear in it", async () => {
        Element.prototype.scrollIntoView = vi.fn();
        onSaveResponses.mockReset();
        onSaveResponses
            .mockRejectedValueOnce({
                errorMessage: "Some answers are not valid",
                reasonCode: "INFORMATION_REQUEST_STRUCTURED_RESPONSE_INVALID",
                problems: [{
                    code: "VALUE_OUT_OF_RANGE",
                    message: "Out of range",
                    requirementId: "runtime-moved",
                    occurrencePath: "reported-item[0]"
                }],
                undisclosedProblemCount: 0,
            })
            .mockRejectedValueOnce({
                errorMessage: "Hidden response data requires explicit clearing confirmation",
                reasonCode: "INFORMATION_REQUEST_HIDDEN_RESPONSE_CLEAR_CONFIRMATION_REQUIRED",
                hiddenResponseClears: [{
                    requirementId: "hidden-moved",
                    sourceTemplateBindingId: "requirement-1",
                    occurrencePath: "reported-item[0]"
                }],
            });
        render(
            <InformationRequestStructuredResponseWorkspace request={{...request, conditionEvaluations: []}}
                                                           respondentKey={"respondent-a"}
                                                           responseETag={"\"responses:1\""}
                                                           groups={[group]}
                                                           occurrences={[
                                                               occurrence("parent-1", 1, "reported-item[0]"),
                                                               occurrence("parent-2", 0, "reported-item[1]"),
                                                           ]}
                                                           requirements={[requirement(
                                                               "requirement-1",
                                                               "reported-summary",
                                                               "Provide the reported summary",
                                                               "field-definition-1",
                                                           )]}
                                                           bindings={[editableBinding]}
                                                           responses={[responseFor(
                                                               "runtime-moved",
                                                               "requirement-1",
                                                               "reported-item[0]",
                                                               "\"field-set:moved\"",
                                                               editableBinding,
                                                           )]}
                                                           onSaveResponses={onSaveResponses}
                                                           onAddOccurrence={onAddOccurrence}
                                                           onRemoveOccurrence={onRemoveOccurrence}
                                                           onReorderOccurrences={onReorderOccurrences}
                                                           onRefresh={onRefresh}/>,
        );

        changeRequirementValue("information-request-response-requirement-reported-item_0-reported-summary", "Moved entry");
        fireEvent.click(screen.getByRole("button", {name: "Save responses"}));
        expect(await screen.findByRole("button", {name: "Go to Provide the reported summary (Reported item 2)"})).toBeTruthy();

        fireEvent.click(screen.getByRole("button", {name: "Save responses"}));
        expect(await screen.findByLabelText("Provide the reported summary (Reported item 2)")).toBe(
            document.getElementById("information-request-confirm-clear-requirement-hidden-moved"),
        );
    });

    it("asks before removing an entry and the entries nested in it, and removes it only once confirmed", async () => {
        onRemoveOccurrence.mockResolvedValue({outcome: "SAVED", responseETag: "\"responses:2\"", occurrences: []});
        render(entriesWorkspace([group], [
            occurrence("occurrence-1", 0, "reported-item[0]"),
            occurrence("occurrence-2", 1, "reported-item[1]"),
        ]));

        fireEvent.click(screen.getByRole("button", {name: "Remove Reported item 1"}));
        const dialog = await screen.findByRole("dialog", {name: "Remove Reported item 1?"});
        expect(dialog.textContent).toContain("every entry nested in it");
        fireEvent.click(screen.getByRole("button", {name: "Cancel"}));
        await waitFor(() => expect(screen.queryByRole("dialog")).toBeNull());
        expect(onRemoveOccurrence).not.toHaveBeenCalled();

        fireEvent.click(screen.getByRole("button", {name: "Remove Reported item 1"}));
        fireEvent.click(await screen.findByRole("button", {name: "Remove"}));

        await waitFor(() => expect(onRemoveOccurrence).toHaveBeenCalledWith("request-1", "occurrence-1", "\"responses:1\""));
        expect(onRemoveOccurrence).toHaveBeenCalledTimes(1);
    });

    it("saves active Field Requirements sparsely and exposes occurrence commands", async () =>
    {
        renderWorkspace();

        expect(screen.getByText("\"Provide the hidden summary\" is not asked because of an earlier answer.")).toBeTruthy();
        expect(screen.queryByText(/collect-hidden-detail/)).toBeNull();
        expect(screen.getByText("In progress")).toBeTruthy();
        expect(screen.getByText("Reported item 1")).toBeTruthy();
        expect(screen.queryByText("Provide the hidden summary")).toBeNull();

        fireEvent.change(document.querySelector("#information-request-response-field-reported-item_0-reported-summary")!, {
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
                respondentKey: "respondent-a",
            },
            "\"responses:1\"",
        ));

        fireEvent.click(screen.getByRole("button", {name: "Add reported item"}));

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
        expect(document.getElementById("information-request-response-prompt-reported-item_0-reported-summary"))
            .toBeTruthy();
        expect(document.getElementById("information-request-response-prompt-reported-item_1-reported-summary"))
            .toBeNull();

        fireEvent.change(document.querySelector("#information-request-response-field-reported-item_0-reported-summary")!, {
            target: {value: "Updated summary"},
        });
        fireEvent.click(screen.getByRole("button", {name: "Save responses"}));

        await waitFor(() => expect(onSaveResponses).toHaveBeenCalledTimes(1));
        expect(onSaveResponses.mock.calls[0][1].patches).toHaveLength(1);
    });

    it("says a conditional Requirement is not asked in each repeated entry where its rule is not true", () => {
        renderTwoOccurrences();

        const notice = (entry: string) => document.getElementById(`information-request-inactive-condition-${entry}-reported-summary`);
        expect(notice("reported-item_1")?.textContent).toBe("\"Provide the reported summary\" is not asked because of an earlier answer.");
        expect(document.getElementById("information-request-occurrence-reported-item_1")?.contains(notice("reported-item_1"))).toBe(true);
        expect(notice("reported-item_0")).toBeNull();
    });

    it("says an item waits for the answers it depends on when its condition cannot be decided yet", () => {
        render(
            <InformationRequestStructuredResponseWorkspace request={{
                ...request,
                conditionEvaluations: [{
                    ruleKey: "collect-hidden-detail",
                    expressionVersion: 1,
                    state: InformationRequestConditionEvaluationState.UNKNOWN,
                    occurrencePath: "reported-item[0]",
                }],
            }}
                                                           respondentKey={"respondent-a"}
                                                           responseETag={"\"responses:1\""}
                                                           groups={[group]}
                                                           occurrences={[occurrence("occurrence-1", 0, "reported-item[0]")]}
                                                           requirements={[requirement(
                                                               "requirement-2",
                                                               "hidden-summary",
                                                               "Provide the hidden summary",
                                                               "field-definition-2",
                                                               "collect-hidden-detail",
                                                           )]}
                                                           bindings={[hiddenBinding]}
                                                           responses={[]}
                                                           onSaveResponses={onSaveResponses}
                                                           onAddOccurrence={onAddOccurrence}
                                                           onRemoveOccurrence={onRemoveOccurrence}
                                                           onReorderOccurrences={onReorderOccurrences}
                                                           onRefresh={onRefresh}/>,
        );

        expect(document.getElementById("information-request-inactive-condition-reported-item_0-hidden-summary")?.textContent)
            .toBe("\"Provide the hidden summary\" is asked once the answers it depends on are given.");
    });

    it("sends parent occurrence identity for nested add and reorder commands", async () =>
    {
        render(
            <InformationRequestStructuredResponseWorkspace request={request}
                                                           respondentKey={"respondent-a"}
                                                           responseETag={"\"responses:1\""}
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
                                                           respondentKey={"respondent-a"}
                                                           responseETag={"\"responses:1\""}
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
            "information-request-response-requirement-reported-item_0_entry_0-entry-summary",
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
                respondentKey: "respondent-a",
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
                                                           respondentKey={"respondent-a"}
                                                           responseETag={"\"responses:1\""}
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
            "information-request-response-requirement-reported-item_0-reported-summary",
            "Parent summary",
        );
        changeRequirementValue(
            "information-request-response-requirement-reported-item_0_entry_0-entry-summary",
            "First branch child",
        );
        changeRequirementValue(
            "information-request-response-requirement-reported-item_1_entry_0-entry-summary",
            "Second branch child",
        );
        changeRequirementValue(
            "information-request-response-requirement-reported-item_0_entry_0_detail_0-detail-summary",
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

        fireEvent.change(document.querySelector("#information-request-response-field-root-root-summary")!, {
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
                respondentKey: "respondent-a",
            },
            "\"responses:1\"",
        ));
    });

    it("shows stale conflict feedback without retrying the failed save", async () =>
    {
        onSaveResponses.mockResolvedValueOnce({outcome: "STALE"});
        renderWorkspace();

        fireEvent.change(document.querySelector("#information-request-response-field-reported-item_0-reported-summary")!, {
            target: {value: "Updated summary"},
        });
        fireEvent.click(screen.getByRole("button", {name: "Save responses"}));

        expect(await screen.findByText("These answers changed somewhere else. Your changes are kept: review them against the latest answers, then choose Save responses."))
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

        fireEvent.change(document.querySelector("#information-request-response-field-reported-item_0-reported-summary")!, {
            target: {value: "Updated summary"},
        });
        fireEvent.click(screen.getByRole("button", {name: "Save responses"}));

        expect(await screen.findByText("Access denied to save responses")).toBeTruthy();
        expect((screen.getByRole("button", {name: "Save responses"}) as HTMLButtonElement).disabled).toBe(false);
        expect((document.querySelector("#information-request-response-field-reported-item_0-reported-summary") as HTMLInputElement).value)
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
                respondentKey: "respondent-a",
            },
            "\"responses:1\"",
        ]);
    });

    it("asks to confirm the clears a refused save names and saves again with them", async () => {
        onSaveResponses.mockReset();
        onSaveResponses
            .mockRejectedValueOnce({
                errorMessage: "Hidden response data requires explicit clearing confirmation",
                reasonCode: "INFORMATION_REQUEST_HIDDEN_RESPONSE_CLEAR_CONFIRMATION_REQUIRED",
                hiddenResponseClears: [
                    {
                        requirementId: "hidden-runtime",
                        sourceTemplateBindingId: "requirement-2",
                        occurrencePath: "reported-item[0]"
                    },
                ],
            })
            .mockResolvedValueOnce({outcome: "SAVED", responseETag: "\"responses:2\"", responses: []});
        renderWorkspace();

        fireEvent.change(document.querySelector("#information-request-response-field-reported-item_0-reported-summary")!, {
            target: {value: "Updated summary"},
        });
        fireEvent.click(screen.getByRole("button", {name: "Save responses"}));

        expect(await screen.findByText("Saving this change clears answers that no longer apply. Confirm each one, then save again."))
            .toBeTruthy();
        const confirmation = document.getElementById("information-request-confirm-clear-requirement-hidden-runtime") as HTMLInputElement;
        expect(screen.getByLabelText(/Provide the hidden summary/)).toBe(confirmation);
        fireEvent.click(screen.getByRole("button", {name: "Save responses"}));
        expect(onSaveResponses).toHaveBeenCalledTimes(1);

        fireEvent.click(confirmation);
        fireEvent.click(screen.getByRole("button", {name: "Save responses"}));

        await waitFor(() => expect(onSaveResponses).toHaveBeenCalledTimes(2));
        expect(onSaveResponses.mock.calls[1][1].confirmedHiddenResponseClearRequirementIds).toEqual(["hidden-runtime"]);
        await waitFor(() => expect(document.getElementById("information-request-confirm-clear-requirement-hidden-runtime")).toBeNull());
    });

    it("asks again for a clear it confirmed once when a later save would clear that answer again", async () => {
        onSaveResponses.mockReset();
        const refusal = {
            errorMessage: "Hidden response data requires explicit clearing confirmation",
            reasonCode: "INFORMATION_REQUEST_HIDDEN_RESPONSE_CLEAR_CONFIRMATION_REQUIRED",
            hiddenResponseClears: [
                {
                    requirementId: "hidden-runtime",
                    sourceTemplateBindingId: "requirement-2",
                    occurrencePath: "reported-item[0]"
                },
            ],
        };
        onSaveResponses
            .mockRejectedValueOnce(refusal)
            .mockResolvedValueOnce({outcome: "SAVED", responseETag: "\"responses:2\"", responses: []})
            .mockRejectedValueOnce(refusal);
        renderWorkspace();
        const summary = () => document.querySelector("#information-request-response-field-reported-item_0-reported-summary")!;

        fireEvent.change(summary(), {target: {value: "Updated summary"}});
        fireEvent.click(screen.getByRole("button", {name: "Save responses"}));
        fireEvent.click(await screen.findByLabelText(/Provide the hidden summary/));
        fireEvent.click(screen.getByRole("button", {name: "Save responses"}));
        await waitFor(() => expect(onSaveResponses).toHaveBeenCalledTimes(2));
        await waitFor(() => expect(document.getElementById("information-request-confirm-clear-requirement-hidden-runtime")).toBeNull());

        fireEvent.change(summary(), {target: {value: "Summary changed again"}});
        fireEvent.click(screen.getByRole("button", {name: "Save responses"}));
        const confirmation = await screen.findByLabelText(/Provide the hidden summary/) as HTMLInputElement;

        expect(confirmation.checked).toBe(false);
        fireEvent.click(screen.getByRole("button", {name: "Save responses"}));
        expect(onSaveResponses).toHaveBeenCalledTimes(3);
    });

    it("keeps the clears an earlier refusal named when a later refusal names more", async () => {
        onSaveResponses.mockReset();
        onSaveResponses
            .mockRejectedValueOnce({
                errorMessage: "Hidden response data requires explicit clearing confirmation",
                reasonCode: "INFORMATION_REQUEST_HIDDEN_RESPONSE_CLEAR_CONFIRMATION_REQUIRED",
                hiddenResponseClears: [
                    {
                        requirementId: "hidden-runtime",
                        sourceTemplateBindingId: "requirement-2",
                        occurrencePath: "reported-item[0]"
                    },
                ],
            })
            .mockRejectedValueOnce({
                errorMessage: "Hidden response data requires explicit clearing confirmation",
                reasonCode: "INFORMATION_REQUEST_HIDDEN_RESPONSE_CLEAR_CONFIRMATION_REQUIRED",
                hiddenResponseClears: [
                    {
                        requirementId: "dependent-runtime",
                        sourceTemplateBindingId: "dependent-binding",
                        occurrencePath: "root"
                    },
                ],
            })
            .mockResolvedValueOnce({outcome: "SAVED", responseETag: "\"responses:2\"", responses: []});
        renderWorkspace();

        fireEvent.change(document.querySelector("#information-request-response-field-reported-item_0-reported-summary")!, {
            target: {value: "Updated summary"},
        });
        fireEvent.click(screen.getByRole("button", {name: "Save responses"}));
        fireEvent.click(await screen.findByLabelText(/Provide the hidden summary/));
        fireEvent.click(screen.getByRole("button", {name: "Save responses"}));

        await waitFor(() => expect(document.getElementById("information-request-confirm-clear-requirement-dependent-runtime")).toBeTruthy());
        const earlier = document.getElementById("information-request-confirm-clear-requirement-hidden-runtime") as HTMLInputElement | null;
        expect(earlier?.checked).toBe(true);
        fireEvent.click(document.getElementById("information-request-confirm-clear-requirement-dependent-runtime")!);
        fireEvent.click(screen.getByRole("button", {name: "Save responses"}));

        await waitFor(() => expect(onSaveResponses).toHaveBeenCalledTimes(3));
        expect(onSaveResponses.mock.calls[2][1].confirmedHiddenResponseClearRequirementIds)
            .toEqual(["hidden-runtime", "dependent-runtime"]);
    });

    it("says when a save would clear an answer the respondent cannot clear and offers no confirmation", async () => {
        onSaveResponses.mockReset();
        onSaveResponses.mockRejectedValueOnce({
            errorMessage: "This change would clear an answer you cannot clear",
            reasonCode: "INFORMATION_REQUEST_HIDDEN_RESPONSE_CLEAR_NOT_PERMITTED",
        });
        renderWorkspace();

        fireEvent.change(document.querySelector("#information-request-response-field-reported-item_0-reported-summary")!, {
            target: {value: "Updated summary"},
        });
        fireEvent.click(screen.getByRole("button", {name: "Save responses"}));

        expect(await screen.findByText("This change would clear an answer you cannot clear")).toBeTruthy();
        expect(document.getElementById("information-request-hidden-clear-confirmations")).toBeNull();
    });

    it("recovers when a group command is refused", async () =>
    {
        onAddOccurrence.mockRejectedValueOnce({errorMessage: "Access denied to add occurrence"});
        renderWorkspace();

        fireEvent.click(screen.getByRole("button", {name: "Add reported item"}));

        expect(await screen.findByText("Access denied to add occurrence")).toBeTruthy();
        expect((screen.getByRole("button", {name: "Add reported item"}) as HTMLButtonElement).disabled).toBe(false);
    });

    it("asks to confirm clearing a hidden answer only once a save refusal names it", async () => {
        onSaveResponses.mockReset();
        onSaveResponses.mockRejectedValueOnce({
            errorMessage: "Hidden response data requires explicit clearing confirmation",
            reasonCode: "INFORMATION_REQUEST_HIDDEN_RESPONSE_CLEAR_CONFIRMATION_REQUIRED",
            hiddenResponseClears: [
                {
                    requirementId: "hidden-runtime",
                    sourceTemplateBindingId: "requirement-2",
                    occurrencePath: "reported-item[0]"
                },
            ],
        });
        render(
            <InformationRequestStructuredResponseWorkspace request={request}
                                                           respondentKey={"respondent-a"}
                                                           responseETag={"\"responses:1\""}
                                                           groups={[group]}
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

        expect(document.getElementById("information-request-hidden-clear-confirmations")).toBeNull();
        fireEvent.change(document.querySelector("#information-request-response-field-reported-item_0-reported-summary")!, {
            target: {value: "Updated summary"},
        });
        fireEvent.click(screen.getByRole("button", {name: "Save responses"}));

        await waitFor(() => expect(onSaveResponses).toHaveBeenCalledTimes(1));
        expect(onSaveResponses.mock.calls[0][1].confirmedHiddenResponseClearRequirementIds).toEqual([]);
        expect(await screen.findByLabelText(/Provide the hidden summary/)).toBe(
            document.getElementById("information-request-confirm-clear-requirement-hidden-runtime"),
        );
    });
});
