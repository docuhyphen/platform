import {describe, expect, it} from "vitest";
import {
    FieldDataClassification,
    FieldValueType,
    InformationRequestContributorRole,
    InformationRequestGroupOccurrenceDto,
    InformationRequestRequiredness,
    InformationRequestRequirementType,
    InformationRequestResponseDisposition,
    InformationRequestResponseDto,
    InformationRequestResponseMode,
    InformationRequestReviewPolicy,
    InformationRequestTemplateRequirementDto,
    SchemaFieldBindingDto,
} from "../../models/models.tsx";
import {
    asksForExplanation,
    currentDisposition,
    groupLabel,
    occurrenceLabel,
    pruneSavedEdits,
} from "./responseAnswerState.ts";
import {buildResponsePatches, responseKey, ROOT_OCCURRENCE_PATH} from "./structuredResponseWorkspaceState.ts";

const root: InformationRequestGroupOccurrenceDto = {
    id: ROOT_OCCURRENCE_PATH,
    informationRequestId: "request-1",
    sourceTemplateGroupId: ROOT_OCCURRENCE_PATH,
    occurrenceIndex: 0,
    occurrencePath: ROOT_OCCURRENCE_PATH,
    createdAt: Date.parse("2026-09-11T00:00:00Z"),
};

const binding: SchemaFieldBindingDto = {
    id: "binding-a",
    fieldContractId: "field-contract-a",
    fieldDefinitionId: "field-definition-a",
    namespace: "process",
    fieldKey: "count",
    label: "Count",
    valueType: FieldValueType.SHORT_TEXT,
    displayOrder: 0,
    section: "Data",
    isRequired: true,
    isReadOnly: false,
    visibility: FieldDataClassification.PUBLIC,
    constraints: {},
    options: [],
};

const requirement = (overrides: Partial<InformationRequestTemplateRequirementDto> = {}): InformationRequestTemplateRequirementDto => ({
    id: "template-binding-a",
    templateRequirementId: "template-requirement-a",
    requirementKey: "record-count",
    requirementType: InformationRequestRequirementType.FIELD,
    prompt: "How many records were kept?",
    responseMode: InformationRequestResponseMode.PROVIDE,
    requiredness: InformationRequestRequiredness.REQUIRED,
    contributorRole: InformationRequestContributorRole.CONTRIBUTOR,
    reviewPolicy: InformationRequestReviewPolicy.NOT_REQUIRED,
    collectedFieldDefinitionId: "field-definition-a",
    permittedDispositions: [InformationRequestResponseDisposition.PROVIDED, InformationRequestResponseDisposition.NOT_APPLICABLE],
    substituteRequirementKeys: [],
    supportingEvidenceRequirementKeys: [],
    ...overrides,
});

const response = (overrides: Partial<InformationRequestResponseDto> = {}): InformationRequestResponseDto => ({
    informationRequestRequirementId: "runtime-requirement-a",
    sourceTemplateBindingId: "template-binding-a",
    sourceTemplateRequirementId: "template-requirement-a",
    occurrencePath: ROOT_OCCURRENCE_PATH,
    disposition: InformationRequestResponseDisposition.NOT_ANSWERED,
    fieldValueSetETag: "\"field-set:1\"",
    fieldValues: [{
        fieldContractId: "field-contract-a",
        schemaFieldBindingId: "binding-a",
        namespace: "process",
        fieldKey: "count",
        label: "Count",
        valueType: FieldValueType.SHORT_TEXT,
        isEmpty: true,
        value: null,
    }],
    responseRevision: 1,
    updatedAt: Date.parse("2026-09-11T00:00:00Z"),
    ...overrides,
});

const key = responseKey("template-binding-a", ROOT_OCCURRENCE_PATH);

const patchesFor = (
    answers: Parameters<typeof buildResponsePatches>[7],
    edits: Parameters<typeof buildResponsePatches>[6] = {},
    stored: InformationRequestResponseDto = response(),
    target: InformationRequestTemplateRequirementDto = requirement(),
) => buildResponsePatches([root], [], [target], [binding], [stored], new Map(), edits, answers);

const valued = (value: string): InformationRequestResponseDto["fieldValues"] =>
    response().fieldValues.map(stored => ({...stored, isEmpty: false, value}));

const changedValue = (value: string) => ({
    etag: "\"field-set:1\"",
    values: [{fieldContractId: "field-contract-a", value}],
});

describe("responseAnswerState", () =>
{
    it("sends a chosen answer other than a value with its explanation and no Field values", () =>
    {
        expect(patchesFor({[key]: {disposition: InformationRequestResponseDisposition.NOT_APPLICABLE, narrative: " Not kept this period "}}))
            .toEqual([{
                requirementId: "runtime-requirement-a",
                disposition: InformationRequestResponseDisposition.NOT_APPLICABLE,
                narrative: "Not kept this period",
            }]);
    });

    it("sends a value with a note, and clears a stored note that was emptied", () =>
    {
        expect(patchesFor({[key]: {narrative: "Counted by hand"}}, {[key]: {"field-contract-a": "12"}})).toEqual([{
            requirementId: "runtime-requirement-a",
            disposition: InformationRequestResponseDisposition.PROVIDED,
            narrative: "Counted by hand",
            fieldValues: {etag: "\"field-set:1\"", values: [{fieldContractId: "field-contract-a", value: "12"}]},
        }]);
        expect(patchesFor({[key]: {narrative: ""}}, {}, response({narrative: "Earlier note", disposition: InformationRequestResponseDisposition.PROVIDED})))
            .toEqual([{requirementId: "runtime-requirement-a", disposition: InformationRequestResponseDisposition.PROVIDED, clearNarrative: true}]);
        expect(patchesFor({[key]: {narrative: "Earlier note"}}, {}, response({narrative: "Earlier note"}))).toEqual([]);
    });

    it("answers a document requirement by an explained disposition once its explanation is given", () =>
    {
        const document = requirement({requirementType: InformationRequestRequirementType.DOCUMENT, collectedFieldDefinitionId: undefined});
        const notApplicable = InformationRequestResponseDisposition.NOT_APPLICABLE;

        expect(patchesFor({[key]: {disposition: notApplicable}}, {}, response({fieldValues: []}), document)).toEqual([]);
        expect(patchesFor({
            [key]: {
                disposition: notApplicable,
                narrative: "No file this period"
            }
        }, {}, response({fieldValues: []}), document))
            .toEqual([{
                requirementId: "runtime-requirement-a",
                disposition: notApplicable,
                narrative: "No file this period"
            }]);
    });

    it.each([
        InformationRequestResponseDisposition.NOT_APPLICABLE,
        InformationRequestResponseDisposition.UNAVAILABLE,
    ])("keeps a saved %s answer when only its explanation changes", saved => {
        const document = requirement({
            requirementType: InformationRequestRequirementType.DOCUMENT,
            collectedFieldDefinitionId: undefined,
            permittedDispositions: [
                InformationRequestResponseDisposition.PROVIDED,
                InformationRequestResponseDisposition.NOT_APPLICABLE,
                InformationRequestResponseDisposition.UNAVAILABLE,
            ],
        });
        const stored = response({disposition: saved, narrative: "Original explanation", fieldValues: []});

        expect(patchesFor({[key]: {narrative: "Revised explanation"}}, {}, stored, document))
            .toEqual([{requirementId: "runtime-requirement-a", disposition: saved, narrative: "Revised explanation"}]);
        expect(patchesFor({[key]: {narrative: "Original explanation"}}, {}, stored, document)).toEqual([]);
    });

    it.each([
        InformationRequestResponseDisposition.PARTIALLY_PROVIDED,
        InformationRequestResponseDisposition.PROVIDED,
    ])("keeps a saved %s answer when its value changes", saved => {
        const partial = requirement({
            permittedDispositions: [
                InformationRequestResponseDisposition.PROVIDED,
                InformationRequestResponseDisposition.PARTIALLY_PROVIDED,
            ],
        });
        const stored = response({
            disposition: saved,
            narrative: "The rest follows",
            fieldValues: valued("First portion")
        });

        expect(patchesFor({}, {[key]: {"field-contract-a": "Both portions"}}, stored, partial)).toEqual([{
            requirementId: "runtime-requirement-a",
            disposition: saved,
            fieldValues: changedValue("Both portions"),
        }]);
    });

    it("answers with the only choice a Template permits once its explanation is entered", () => {
        const exceptionOnly = requirement({permittedDispositions: [InformationRequestResponseDisposition.UNAVAILABLE]});

        expect(patchesFor({[key]: {narrative: "The record was not kept"}}, {}, response(), exceptionOnly)).toEqual([{
            requirementId: "runtime-requirement-a",
            disposition: InformationRequestResponseDisposition.UNAVAILABLE,
            narrative: "The record was not kept",
        }]);
    });

    it("keeps an unanswered item unanswered when only its note changes, and answers it with a value", () => {
        expect(patchesFor({[key]: {narrative: "Counted later"}}))
            .toEqual([{requirementId: "runtime-requirement-a", narrative: "Counted later"}]);
        expect(patchesFor({[key]: {narrative: "Counted later"}}, {[key]: {"field-contract-a": "12"}})).toEqual([{
            requirementId: "runtime-requirement-a",
            disposition: InformationRequestResponseDisposition.PROVIDED,
            narrative: "Counted later",
            fieldValues: changedValue("12"),
        }]);
    });

    it("sends no value that a saved answer other than a value does not show", () => {
        const stored = response({
            disposition: InformationRequestResponseDisposition.NOT_APPLICABLE,
            narrative: "Not kept"
        });

        expect(patchesFor({}, {[key]: {"field-contract-a": "12"}}, stored)).toEqual([]);
    });

    it("sends a newly chosen answer in place of the saved one", () => {
        const stored = response({
            disposition: InformationRequestResponseDisposition.NOT_APPLICABLE,
            narrative: "Not kept"
        });

        expect(patchesFor({[key]: {disposition: InformationRequestResponseDisposition.PROVIDED}}, {[key]: {"field-contract-a": "12"}}, stored))
            .toEqual([{
                requirementId: "runtime-requirement-a",
                disposition: InformationRequestResponseDisposition.PROVIDED,
                fieldValues: changedValue("12"),
            }]);
    });

    it("sends the answer shown in place of a saved one the Template no longer permits", () => {
        const withdrawn = response({
            disposition: InformationRequestResponseDisposition.UNAVAILABLE,
            narrative: "Earlier reason"
        });
        const exceptionsOnly = requirement({
            permittedDispositions: [
                InformationRequestResponseDisposition.NOT_APPLICABLE,
                InformationRequestResponseDisposition.WAIVED,
            ],
        });

        expect(patchesFor({}, {[key]: {"field-contract-a": "12"}}, withdrawn)).toEqual([{
            requirementId: "runtime-requirement-a",
            disposition: InformationRequestResponseDisposition.PROVIDED,
            fieldValues: changedValue("12"),
        }]);
        expect(patchesFor({[key]: {narrative: "Revised reason"}}, {}, withdrawn, exceptionsOnly)).toEqual([{
            requirementId: "runtime-requirement-a",
            disposition: InformationRequestResponseDisposition.NOT_APPLICABLE,
            narrative: "Revised reason",
        }]);
    });

    it("shows the chosen answer, else the saved permitted one, else Provided, else the first permitted, else none", () => {
        const exceptions = requirement({
            permittedDispositions: [
                InformationRequestResponseDisposition.UNAVAILABLE,
                InformationRequestResponseDisposition.WAIVED,
            ],
        });
        const waived = response({disposition: InformationRequestResponseDisposition.WAIVED});
        const chosen = {disposition: InformationRequestResponseDisposition.UNAVAILABLE};

        expect(currentDisposition(exceptions, waived, chosen)).toBe(InformationRequestResponseDisposition.UNAVAILABLE);
        expect(currentDisposition(exceptions, waived, {narrative: "Reason"})).toBe(InformationRequestResponseDisposition.WAIVED);
        expect(currentDisposition(requirement(), waived, undefined)).toBe(InformationRequestResponseDisposition.PROVIDED);
        expect(currentDisposition(exceptions, response(), undefined)).toBe(InformationRequestResponseDisposition.UNAVAILABLE);
        expect(currentDisposition(requirement({permittedDispositions: []}), waived, undefined))
            .toBe(InformationRequestResponseDisposition.NOT_ANSWERED);
    });

    it("asks for the reason exactly where the answer needs one", () => {
        const explained = Object.values(InformationRequestResponseDisposition).filter(asksForExplanation);

        expect(explained).toEqual([
            InformationRequestResponseDisposition.PARTIALLY_PROVIDED,
            InformationRequestResponseDisposition.NOT_APPLICABLE,
            InformationRequestResponseDisposition.UNAVAILABLE,
            InformationRequestResponseDisposition.EXCEPTION_REQUESTED,
            InformationRequestResponseDisposition.SATISFIED_BY_REFERENCE,
            InformationRequestResponseDisposition.WAIVED,
        ]);
    });

    it("forgets only the edits that were saved unchanged", () =>
    {
        const sent = {first: {a: "1"}, second: {b: "2"}};
        const current = {...sent, second: {b: "3"}, third: {c: "4"}};

        expect(pruneSavedEdits(current, sent)).toEqual({second: {b: "3"}, third: {c: "4"}});
        expect(pruneSavedEdits(sent, sent)).toEqual({});
    });

    it("names groups and occurrences in words rather than keys", () =>
    {
        expect(groupLabel("reported-item")).toBe("Reported item");
        expect(occurrenceLabel("reported-item[0]/entry[1]")).toBe("Reported item 1, entry 2");
        expect(occurrenceLabel(ROOT_OCCURRENCE_PATH)).toBe("");
    });
});
