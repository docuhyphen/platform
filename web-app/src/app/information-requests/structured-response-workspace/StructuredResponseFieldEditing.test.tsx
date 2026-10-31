/** @vitest-environment jsdom */
import {act, cleanup, fireEvent, render, screen} from "@testing-library/react";
import {afterEach, beforeAll, beforeEach, describe, expect, it, vi} from "vitest";
import {
    FieldDataClassification,
    FieldValueType,
    InformationRequestContributorRole,
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
import {StructuredResponseWorkspaceProps} from "./StructuredResponseWorkspaceTypes.ts";

const at = Date.parse("2026-10-20T00:00:00Z");
const onSaveResponses = vi.fn<StructuredResponseWorkspaceProps["onSaveResponses"]>();

const binding = (valueType: FieldValueType, isReadOnly = false): SchemaFieldBindingDto => ({
    id: "schema-binding",
    fieldContractId: "field-contract",
    fieldDefinitionId: "field-definition",
    namespace: "process",
    fieldKey: "record-choice",
    label: "Record choice",
    valueType,
    displayOrder: 0,
    isRequired: false,
    isReadOnly,
    visibility: FieldDataClassification.INTERNAL,
    constraints: {},
    options: [{code: "FIRST", label: "First option"}, {code: "SECOND", label: "Second option"}],
});

const answered = (valueType: FieldValueType, value: unknown, occurrencePath = "root"): InformationRequestResponseDto => ({
    informationRequestRequirementId: `runtime-${occurrencePath}`,
    sourceTemplateRequirementId: "template-requirement",
    sourceTemplateBindingId: "template-binding",
    occurrencePath,
    disposition: InformationRequestResponseDisposition.PROVIDED,
    fieldValueSetETag: `"set:${occurrencePath}"`,
    fieldValues: [{
        fieldContractId: "field-contract",
        schemaFieldBindingId: "schema-binding",
        namespace: "process",
        fieldKey: "record-choice",
        label: "Record choice",
        valueType,
        isEmpty: false,
        value,
    }],
    responseRevision: 1,
    updatedAt: at,
});

const occurrence = (
    id: string,
    groupId: string,
    occurrencePath: string,
    occurrenceIndex: number,
    parentOccurrenceId?: string,
): InformationRequestGroupOccurrenceDto => ({
    id,
    informationRequestId: "request-field-editing",
    sourceTemplateGroupId: groupId,
    parentOccurrenceId,
    occurrenceIndex,
    occurrencePath,
    createdAt: at,
});

const props = (
    valueType: FieldValueType,
    responses: InformationRequestResponseDto[],
    anchor?: string,
    groups: InformationRequestTemplateGroupDto[] = [],
    occurrences: InformationRequestGroupOccurrenceDto[] = [],
    isReadOnly = false,
): StructuredResponseWorkspaceProps => ({
    request: {
        id: "request-field-editing",
        exchangeId: "exchange-1",
        templateVersionId: "template-version-1",
        ownerType: InformationRequestOwnerType.USER,
        state: InformationRequestState.IN_PROGRESS,
        gatesExchangeClosure: false,
        aggregateRevision: 1,
        createdAt: at,
        updatedAt: at,
        requestETag: "\"request:1\"",
        executionStanding: {kind: InformationRequestExecutionStandingKind.ACTIVE},
        conditionEvaluations: [],
    },
    respondentKey: "respondent-a",
    responseETag: "\"responses:1\"",
    groups,
    occurrences,
    requirements: [{
        id: "template-binding",
        templateRequirementId: "template-requirement",
        requirementKey: "record-choice",
        requirementType: InformationRequestRequirementType.FIELD,
        prompt: "Choose the record option",
        responseMode: InformationRequestResponseMode.PROVIDE,
        requiredness: InformationRequestRequiredness.OPTIONAL,
        contributorRole: InformationRequestContributorRole.CONTRIBUTOR,
        reviewPolicy: InformationRequestReviewPolicy.NOT_REQUIRED,
        occurrenceAnchorKey: anchor,
        collectedFieldDefinitionId: "field-definition",
        permittedDispositions: [InformationRequestResponseDisposition.PROVIDED],
        substituteRequirementKeys: [],
        supportingEvidenceRequirementKeys: [],
    }],
    bindings: [binding(valueType, isReadOnly)],
    responses,
    onSaveResponses,
    onAddOccurrence: vi.fn(),
    onRemoveOccurrence: vi.fn(),
    onReorderOccurrences: vi.fn(),
    onRefresh: vi.fn(),
});

const keyedRequirement = (index: number, requirementKey: string, anchor: string): InformationRequestTemplateRequirementDto => ({
    ...props(FieldValueType.SHORT_TEXT, []).requirements[0],
    id: `template-binding-${index}`,
    templateRequirementId: `template-requirement-${index}`,
    requirementKey,
    prompt: `Answer ${requirementKey}`,
    occurrenceAnchorKey: anchor,
});

const keyedAnswer = (index: number, occurrencePath: string): InformationRequestResponseDto => ({
    ...answered(FieldValueType.SHORT_TEXT, `${occurrencePath} ${index}`, occurrencePath),
    informationRequestRequirementId: `runtime-${index}-${occurrencePath}`,
    sourceTemplateRequirementId: `template-requirement-${index}`,
    sourceTemplateBindingId: `template-binding-${index}`,
});

const expectDistinctLabelledInputs = (expectedPrompts: string[]) => {
    const inputs = fieldInputs();
    expect(new Set(inputs.map(input => input.id)).size).toBe(expectedPrompts.length);
    expect(new Set(inputs.map(input => input.closest("[id^='information-request-response-requirement-']")?.id)).size)
        .toBe(expectedPrompts.length);
    expect(inputs.map(input => document.getElementById(input.getAttribute("aria-labelledby") as string)?.textContent))
        .toEqual(expectedPrompts);
};

const autosave = () => act(async () => {
    await vi.advanceTimersByTimeAsync(2000);
});

const clearControl = () => screen.queryByRole("button", {name: /clear/i});

const sentValues = () => {
    expect(onSaveResponses).toHaveBeenCalledTimes(1);
    return onSaveResponses.mock.calls[0][1].patches[0].fieldValues?.values;
};

const fieldInputs = () =>
    Array.from(document.querySelectorAll<HTMLElement>("[aria-labelledby^='information-request-response-prompt-']"));

const ENTRY_PREFIX = "information-request-occurrence-requirements-";

const occurrenceOf = (element: HTMLElement) => element.closest(`[id^='${ENTRY_PREFIX}']`)?.id.slice(ENTRY_PREFIX.length);

const expectEachInputLabelledInItsOwnEntry = (expectedEntries: string[]) => {
    const inputs = fieldInputs();
    expect(inputs.map(occurrenceOf)).toEqual(expectedEntries);
    expect(new Set(inputs.map(input => input.id)).size).toBe(expectedEntries.length);
    inputs.forEach(input => {
        const prompt = document.getElementById(input.getAttribute("aria-labelledby") as string) as HTMLElement;
        expect(prompt.textContent).toBe("Choose the record option");
        expect(occurrenceOf(prompt)).toBe(occurrenceOf(input));
    });
};

describe("structured response Field editing", () => {
    beforeAll(() => {
        vi.stubGlobal("ResizeObserver", class {
            observe() {
            }

            unobserve() {
            }

            disconnect() {
            }
        });
    });

    beforeEach(() => {
        vi.clearAllMocks();
        window.sessionStorage.clear();
        vi.useFakeTimers({toFake: ["setTimeout", "clearTimeout"]});
        onSaveResponses.mockResolvedValue({outcome: "SAVED", responseETag: "\"responses:2\"", responses: []});
    });

    afterEach(() => {
        cleanup();
        vi.useRealTimers();
    });

    it.each([
        [FieldValueType.BOOLEAN, true],
        [FieldValueType.SINGLE_SELECT, "FIRST"],
    ])("lets an optional saved %s answer be cleared", async (valueType, value) => {
        render(<InformationRequestStructuredResponseWorkspace {...props(valueType, [answered(valueType, value)])}/>);

        fireEvent.click(clearControl() as HTMLElement);
        await autosave();

        expect(sentValues()).toEqual([{fieldContractId: "field-contract", value: null}]);
    });

    it("keeps a saved text answer clearable", async () => {
        render(
            <InformationRequestStructuredResponseWorkspace {...props(FieldValueType.SHORT_TEXT, [answered(FieldValueType.SHORT_TEXT, "Recorded value")])}/>);

        fireEvent.change(fieldInputs()[0], {target: {value: ""}});
        await autosave();

        expect(sentValues()).toEqual([{fieldContractId: "field-contract", value: null}]);
    });

    it("offers no clear for a read-only Field", () => {
        render(<InformationRequestStructuredResponseWorkspace {...props(
            FieldValueType.SINGLE_SELECT,
            [answered(FieldValueType.SINGLE_SELECT, "FIRST")],
            undefined,
            [],
            [],
            true,
        )}/>);

        expect(clearControl()).toBeNull();
    });

    it("gives the same Field in each entry its own input, labelled by that entry's prompt", () => {
        const records = {id: "group-records", groupKey: "records", minOccurrences: 0, maxOccurrences: 3};
        render(<InformationRequestStructuredResponseWorkspace {...props(
            FieldValueType.SHORT_TEXT,
            [answered(FieldValueType.SHORT_TEXT, "First value", "records[0]"), answered(FieldValueType.SHORT_TEXT, "Second value", "records[1]")],
            "records",
            [records],
            [occurrence("occurrence-0", "group-records", "records[0]", 0), occurrence("occurrence-1", "group-records", "records[1]", 1)],
        )}/>);

        expectEachInputLabelledInItsOwnEntry(["occurrence-0", "occurrence-1"]);
    });

    it("gives the same Field in each nested entry its own input", () => {
        const records = {id: "group-records", groupKey: "records", minOccurrences: 0, maxOccurrences: 3};
        const entries = {
            id: "group-entries",
            groupKey: "entries",
            parentGroupKey: "records",
            minOccurrences: 0,
            maxOccurrences: 3
        };
        const paths = ["records[0]/entries[0]", "records[0]/entries[1]", "records[1]/entries[0]"];
        render(<InformationRequestStructuredResponseWorkspace {...props(
            FieldValueType.SHORT_TEXT,
            paths.map(path => answered(FieldValueType.SHORT_TEXT, path, path)),
            "entries",
            [records, entries],
            [
                occurrence("occurrence-0", "group-records", "records[0]", 0),
                occurrence("occurrence-0-0", "group-entries", paths[0], 0, "occurrence-0"),
                occurrence("occurrence-0-1", "group-entries", paths[1], 1, "occurrence-0"),
                occurrence("occurrence-1", "group-records", "records[1]", 1),
                occurrence("occurrence-1-0", "group-entries", paths[2], 0, "occurrence-1"),
            ],
        )}/>);

        expectEachInputLabelledInItsOwnEntry(["occurrence-0-0", "occurrence-0-1", "occurrence-1-0"]);
    });

    it("gives Requirement keys that differ only in their hyphens their own inputs in each entry", () => {
        const records = {id: "group-records", groupKey: "records", minOccurrences: 0, maxOccurrences: 3};
        const requirements = [keyedRequirement(1, "record--choice", "records"), keyedRequirement(2, "record-choice", "records")];
        const paths = ["records[0]", "records[1]"];
        render(<InformationRequestStructuredResponseWorkspace {...{
            ...props(FieldValueType.SHORT_TEXT, [], "records", [records], [
                occurrence("occurrence-0", "group-records", paths[0], 0),
                occurrence("occurrence-1", "group-records", paths[1], 1),
            ]),
            requirements,
            responses: paths.flatMap(path => [keyedAnswer(1, path), keyedAnswer(2, path)]),
        }}/>);

        expectDistinctLabelledInputs(["Answer record--choice", "Answer record-choice", "Answer record--choice", "Answer record-choice"]);
    });

    it("keeps a nested entry's Requirement apart from a key in its parent entry that reads the same", () => {
        const records = {id: "group-records", groupKey: "records", minOccurrences: 0, maxOccurrences: 3};
        const detail = {
            id: "group-detail",
            groupKey: "detail",
            parentGroupKey: "records",
            minOccurrences: 0,
            maxOccurrences: 3
        };
        render(<InformationRequestStructuredResponseWorkspace {...{
            ...props(FieldValueType.SHORT_TEXT, [], "records", [records, detail], [
                occurrence("occurrence-0", "group-records", "records[0]", 0),
                occurrence("occurrence-0-0", "group-detail", "records[0]/detail[0]", 0, "occurrence-0"),
            ]),
            requirements: [keyedRequirement(1, "detail-0-amount", "records"), keyedRequirement(2, "amount", "detail")],
            responses: [keyedAnswer(1, "records[0]"), keyedAnswer(2, "records[0]/detail[0]")],
        }}/>);

        expectDistinctLabelledInputs(["Answer detail-0-amount", "Answer amount"]);
    });
});
