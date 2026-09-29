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
import {groupLabel, occurrenceLabel, pruneSavedEdits} from "./responseAnswerState.ts";
import {buildResponsePatches, responseKey, ROOT_OCCURRENCE_PATH} from "./structuredResponseWorkspaceState.ts";

const root: InformationRequestGroupOccurrenceDto = {
    id: ROOT_OCCURRENCE_PATH,
    informationRequestId: "request-1",
    sourceTemplateGroupId: ROOT_OCCURRENCE_PATH,
    occurrenceIndex: 0,
    occurrencePath: ROOT_OCCURRENCE_PATH,
    createdAt: "2026-09-11T00:00:00Z",
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
    updatedAt: "2026-09-11T00:00:00Z",
    ...overrides,
});

const key = responseKey("template-binding-a", ROOT_OCCURRENCE_PATH);

const patchesFor = (
    answers: Parameters<typeof buildResponsePatches>[7],
    edits: Parameters<typeof buildResponsePatches>[6] = {},
    stored: InformationRequestResponseDto = response(),
    target: InformationRequestTemplateRequirementDto = requirement(),
) => buildResponsePatches([root], [], [target], [binding], [stored], new Map(), edits, answers);

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

    it("answers a document requirement by disposition alone", () =>
    {
        const document = requirement({requirementType: InformationRequestRequirementType.DOCUMENT, collectedFieldDefinitionId: undefined});

        expect(patchesFor({[key]: {disposition: InformationRequestResponseDisposition.NOT_APPLICABLE}}, {}, response({fieldValues: []}), document))
            .toEqual([{requirementId: "runtime-requirement-a", disposition: InformationRequestResponseDisposition.NOT_APPLICABLE}]);
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
