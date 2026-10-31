/** @vitest-environment jsdom */
import {act, cleanup, fireEvent, render, screen} from "@testing-library/react";
import {afterEach, beforeAll, beforeEach, describe, expect, it, vi} from "vitest";
import {
    FieldDataClassification,
    FieldValueType,
    InformationRequestContributorRole,
    InformationRequestDto,
    InformationRequestExecutionStandingKind,
    InformationRequestOwnerType,
    InformationRequestRequiredness,
    InformationRequestRequirementType,
    InformationRequestResponseDisposition,
    InformationRequestResponseDto,
    InformationRequestResponseMode,
    InformationRequestReviewPolicy,
    InformationRequestState,
    InformationRequestTemplateRequirementDto,
    SchemaFieldBindingDto,
} from "../../models/models.tsx";
import InformationRequestStructuredResponseWorkspace from "./InformationRequestStructuredResponseWorkspace.tsx";

const onSaveResponses = vi.fn();

const request: InformationRequestDto = {
    id: "request-answer-choice",
    exchangeId: "exchange-1",
    templateVersionId: "template-version-1",
    ownerType: InformationRequestOwnerType.USER,
    state: InformationRequestState.IN_PROGRESS,
    gatesExchangeClosure: false,
    aggregateRevision: 1,
    createdAt: Date.parse("2026-10-20T00:00:00Z"),
    updatedAt: Date.parse("2026-10-20T00:00:00Z"),
    requestETag: "\"request:1\"",
    executionStanding: {kind: InformationRequestExecutionStandingKind.ACTIVE},
    conditionEvaluations: [],
};

const binding: SchemaFieldBindingDto = {
    id: "schema-binding-1",
    fieldContractId: "field-contract-1",
    fieldDefinitionId: "field-definition-1",
    namespace: "process",
    fieldKey: "record-summary",
    label: "Record summary",
    valueType: FieldValueType.SHORT_TEXT,
    displayOrder: 0,
    section: "Records",
    isRequired: true,
    isReadOnly: false,
    visibility: FieldDataClassification.INTERNAL,
    constraints: {},
    options: [],
};

const documentRequirement = (permittedDispositions: InformationRequestResponseDisposition[]): InformationRequestTemplateRequirementDto => ({
    id: "template-binding-1",
    templateRequirementId: "template-requirement-1",
    requirementKey: "supporting-record",
    requirementType: InformationRequestRequirementType.DOCUMENT,
    prompt: "Provide the supporting record",
    responseMode: InformationRequestResponseMode.PROVIDE,
    requiredness: InformationRequestRequiredness.REQUIRED,
    contributorRole: InformationRequestContributorRole.CONTRIBUTOR,
    reviewPolicy: InformationRequestReviewPolicy.NOT_REQUIRED,
    permittedDispositions,
    substituteRequirementKeys: [],
    supportingEvidenceRequirementKeys: [],
});

const fieldRequirement = (permittedDispositions: InformationRequestResponseDisposition[]): InformationRequestTemplateRequirementDto => ({
    ...documentRequirement(permittedDispositions),
    requirementKey: "record-summary",
    requirementType: InformationRequestRequirementType.FIELD,
    prompt: "Summarize the record",
    collectedFieldDefinitionId: "field-definition-1",
});

const saved = (
    disposition: InformationRequestResponseDisposition,
    narrative: string | null,
    value?: string,
): InformationRequestResponseDto => ({
    informationRequestRequirementId: "runtime-requirement-1",
    sourceTemplateRequirementId: "template-requirement-1",
    sourceTemplateBindingId: "template-binding-1",
    occurrencePath: "root",
    disposition,
    narrative,
    fieldValueSetETag: value === undefined ? undefined : "\"field-set:1\"",
    fieldValues: value === undefined ? [] : [{
        fieldContractId: "field-contract-1",
        schemaFieldBindingId: "schema-binding-1",
        namespace: "process",
        fieldKey: "record-summary",
        label: "Record summary",
        valueType: FieldValueType.SHORT_TEXT,
        isEmpty: value === "",
        value: value === "" ? null : value,
    }],
    responseRevision: 1,
    updatedAt: Date.parse("2026-10-20T00:00:00Z"),
});

const renderWorkspace = (requirement: InformationRequestTemplateRequirementDto, response: InformationRequestResponseDto) => render(
    <InformationRequestStructuredResponseWorkspace request={request}
                                                   respondentKey={"respondent-a"}
                                                   responseETag={"\"responses:1\""}
                                                   groups={[]}
                                                   occurrences={[]}
                                                   requirements={[requirement]}
                                                   bindings={[binding]}
                                                   responses={[response]}
                                                   onSaveResponses={onSaveResponses}
                                                   onAddOccurrence={vi.fn()}
                                                   onRemoveOccurrence={vi.fn()}
                                                   onReorderOccurrences={vi.fn()}
                                                   onRefresh={vi.fn()}/>,
);

const valueInput = () => document.querySelector<HTMLInputElement>("#information-request-response-field-root-record-summary");

const explanation = () => screen.queryByLabelText(/^Explain your answer/);

const note = () => screen.queryByLabelText("Note");

const autosave = () => act(async () => {
    await vi.advanceTimersByTimeAsync(2000);
});

const sentPatches = () => {
    expect(onSaveResponses).toHaveBeenCalledTimes(1);
    return onSaveResponses.mock.calls[0][1].patches;
};

const valueChange = (value: string) => ({
    etag: "\"field-set:1\"",
    values: [{fieldContractId: "field-contract-1", value}],
});

describe("structured response answer choice", () => {
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
        [InformationRequestResponseDisposition.NOT_APPLICABLE, "Not applicable"],
        [InformationRequestResponseDisposition.UNAVAILABLE, "Unavailable"],
    ])("shows a saved %s answer and keeps it when only its explanation changes", async (disposition, label) => {
        renderWorkspace(
            documentRequirement([
                InformationRequestResponseDisposition.PROVIDED,
                InformationRequestResponseDisposition.NOT_APPLICABLE,
                InformationRequestResponseDisposition.UNAVAILABLE,
            ]),
            saved(disposition, "Original explanation"),
        );

        expect((screen.getByRole("radio", {name: label}) as HTMLInputElement).checked).toBe(true);
        fireEvent.change(explanation() as HTMLElement, {target: {value: "Revised explanation"}});
        await autosave();

        expect(sentPatches()).toEqual([{
            requirementId: "runtime-requirement-1",
            disposition,
            narrative: "Revised explanation",
        }]);
    });

    it("saves the only answer a Template permits without offering a choice", async () => {
        renderWorkspace(
            documentRequirement([InformationRequestResponseDisposition.UNAVAILABLE]),
            saved(InformationRequestResponseDisposition.NOT_ANSWERED, null),
        );

        expect(screen.queryByRole("radio")).toBeNull();
        fireEvent.change(explanation() as HTMLElement, {target: {value: "The source record is unavailable"}});
        await autosave();

        expect(sentPatches()).toEqual([{
            requirementId: "runtime-requirement-1",
            disposition: InformationRequestResponseDisposition.UNAVAILABLE,
            narrative: "The source record is unavailable",
        }]);
    });

    it.each([
        InformationRequestResponseDisposition.PARTIALLY_PROVIDED,
        InformationRequestResponseDisposition.PROVIDED,
    ])("keeps a saved %s typed answer when its value changes", async disposition => {
        renderWorkspace(
            fieldRequirement([
                InformationRequestResponseDisposition.PROVIDED,
                InformationRequestResponseDisposition.PARTIALLY_PROVIDED,
            ]),
            saved(disposition, "The rest follows", "First portion"),
        );

        fireEvent.change(valueInput() as HTMLInputElement, {target: {value: "Expanded portion"}});
        await autosave();

        expect(sentPatches()).toEqual([{
            requirementId: "runtime-requirement-1",
            disposition,
            fieldValues: valueChange("Expanded portion"),
        }]);
    });

    it("sends a newly chosen value answer in place of a saved answer other than a value", async () => {
        renderWorkspace(
            fieldRequirement([
                InformationRequestResponseDisposition.PROVIDED,
                InformationRequestResponseDisposition.NOT_APPLICABLE,
            ]),
            saved(InformationRequestResponseDisposition.NOT_APPLICABLE, "No record this period", ""),
        );

        expect(valueInput()).toBeNull();
        fireEvent.click(screen.getByRole("radio", {name: "Provided"}));
        expect(explanation()).toBeNull();
        fireEvent.change(valueInput() as HTMLInputElement, {target: {value: "A record was kept"}});
        await autosave();

        expect(sentPatches()).toEqual([{
            requirementId: "runtime-requirement-1",
            disposition: InformationRequestResponseDisposition.PROVIDED,
            fieldValues: valueChange("A record was kept"),
        }]);
    });

    it("offers nothing to answer for an item the party only sees", () => {
        renderWorkspace(
            {...fieldRequirement([]), responseMode: InformationRequestResponseMode.VIEW_ONLY},
            saved(InformationRequestResponseDisposition.NOT_ANSWERED, null, ""),
        );

        expect(screen.getByText("Summarize the record")).toBeTruthy();
        expect(screen.queryByRole("radio")).toBeNull();
        expect(valueInput()).toBeNull();
        expect(explanation()).toBeNull();
        expect(note()).toBeNull();
    });

    it("offers an optional note on a provided value and sends it with the value", async () => {
        renderWorkspace(
            fieldRequirement([InformationRequestResponseDisposition.PROVIDED]),
            saved(InformationRequestResponseDisposition.NOT_ANSWERED, null, ""),
        );

        expect(explanation()).toBeNull();
        expect(screen.getByText("Optional. Shown with your answer.")).toBeTruthy();
        fireEvent.change(valueInput() as HTMLInputElement, {target: {value: "12"}});
        fireEvent.change(note() as HTMLElement, {target: {value: "Counted by hand"}});
        await autosave();

        expect(sentPatches()).toEqual([{
            requirementId: "runtime-requirement-1",
            disposition: InformationRequestResponseDisposition.PROVIDED,
            narrative: "Counted by hand",
            fieldValues: valueChange("12"),
        }]);
    });

    it("keeps an unanswered item unanswered when only its note is saved", async () => {
        renderWorkspace(
            documentRequirement([InformationRequestResponseDisposition.PROVIDED]),
            saved(InformationRequestResponseDisposition.NOT_ANSWERED, null),
        );

        fireEvent.change(note() as HTMLElement, {target: {value: "The file follows tomorrow"}});
        await autosave();

        expect(sentPatches()).toEqual([{
            requirementId: "runtime-requirement-1",
            narrative: "The file follows tomorrow",
        }]);
    });

    it("shows the reason given for an earlier answer as the note of a provided answer, to keep or clear", async () => {
        renderWorkspace(
            fieldRequirement([
                InformationRequestResponseDisposition.PROVIDED,
                InformationRequestResponseDisposition.NOT_APPLICABLE,
            ]),
            saved(InformationRequestResponseDisposition.NOT_APPLICABLE, "No record this period", ""),
        );

        fireEvent.click(screen.getByRole("radio", {name: "Provided"}));
        expect((note() as HTMLTextAreaElement).value).toBe("No record this period");
        fireEvent.change(valueInput() as HTMLInputElement, {target: {value: "A record was kept"}});
        fireEvent.change(note() as HTMLElement, {target: {value: ""}});
        await autosave();

        expect(sentPatches()).toEqual([{
            requirementId: "runtime-requirement-1",
            disposition: InformationRequestResponseDisposition.PROVIDED,
            clearNarrative: true,
            fieldValues: valueChange("A record was kept"),
        }]);
    });

    it("asks why an answer is only partly provided and sends the reason with the value", async () => {
        renderWorkspace(
            fieldRequirement([
                InformationRequestResponseDisposition.PROVIDED,
                InformationRequestResponseDisposition.PARTIALLY_PROVIDED,
            ]),
            saved(InformationRequestResponseDisposition.NOT_ANSWERED, null, ""),
        );

        expect(explanation()).toBeNull();
        fireEvent.click(screen.getByRole("radio", {name: "Partly provided"}));
        fireEvent.change(valueInput() as HTMLInputElement, {target: {value: "First portion"}});
        fireEvent.change(explanation() as HTMLElement, {target: {value: "The rest follows next week"}});
        await autosave();

        expect(sentPatches()).toEqual([{
            requirementId: "runtime-requirement-1",
            disposition: InformationRequestResponseDisposition.PARTIALLY_PROVIDED,
            narrative: "The rest follows next week",
            fieldValues: valueChange("First portion"),
        }]);
    });
});
