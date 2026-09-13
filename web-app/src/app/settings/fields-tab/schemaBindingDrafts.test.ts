import {describe, expect, it} from "vitest";
import {
    FieldContractDto,
    FieldDataClassification,
    FieldDefinitionDto,
    FieldLifecycleStatus,
    FieldScopeKind,
    FieldValueType,
    SchemaFieldBindingDto,
} from "../../models/models.tsx";
import {addableDefinitions, draftForDefinition, toBindingDrafts} from "./schemaBindingDrafts.ts";

const contract = (id: string, definitionId: string, version: number): FieldContractDto => ({
    id,
    fieldDefinitionId: definitionId,
    contractVersion: version,
    valueType: FieldValueType.SHORT_TEXT,
    typeContractVersion: 1,
    label: "Reference code",
    constraints: {},
    options: [],
    dataClassification: FieldDataClassification.INTERNAL,
    isSearchable: false,
    isFilterable: false,
    isSortable: false,
    isReportable: false,
    createdAt: "2026-08-01T00:00:00Z",
});

const definition = (id: string, fieldKey: string, latest?: FieldContractDto): FieldDefinitionDto => ({
    id,
    scopeKind: FieldScopeKind.ORGANIZATION,
    namespace: "process",
    fieldKey,
    status: FieldLifecycleStatus.PUBLISHED,
    contractCount: latest ? latest.contractVersion : 0,
    latestContract: latest,
    createdAt: "2026-08-01T00:00:00Z",
});

const binding = (contractId: string, definitionId: string): SchemaFieldBindingDto => ({
    id: `binding-${contractId}`,
    fieldContractId: contractId,
    fieldDefinitionId: definitionId,
    namespace: "process",
    fieldKey: "reference-code",
    label: "Reference code",
    valueType: FieldValueType.SHORT_TEXT,
    displayOrder: 0,
    isRequired: false,
    isReadOnly: false,
    visibility: FieldDataClassification.INTERNAL,
    constraints: {},
    options: [],
});

const referenceV1 = contract("contract-reference-1", "field-reference", 1);
const referenceV2 = contract("contract-reference-2", "field-reference", 2);
const reviewV1 = contract("contract-review-1", "field-review", 1);

const referenceField = definition("field-reference", "reference-code", referenceV2);
const reviewField = definition("field-review", "review-note", reviewV1);

describe("schema binding drafts", () =>
{
    it("does not offer a field that is already bound through an older contract version", () =>
    {
        const drafts = toBindingDrafts([binding(referenceV1.id, referenceField.id)]);

        const addable = addableDefinitions([referenceField, reviewField], drafts);

        expect(addable.map(d => d.id)).toEqual([reviewField.id]);
    });

    it("does not offer a field that is already bound through its latest contract version", () =>
    {
        const drafts = toBindingDrafts([binding(referenceV2.id, referenceField.id)]);

        const addable = addableDefinitions([referenceField, reviewField], drafts);

        expect(addable.map(d => d.id)).toEqual([reviewField.id]);
    });

    it("omits a field that has no contract to bind", () =>
    {
        const addable = addableDefinitions([definition("field-draft", "draft-only")], []);

        expect(addable).toEqual([]);
    });

    it("carries the stable field of an existing binding into its draft", () =>
    {
        const drafts = toBindingDrafts([binding(referenceV1.id, referenceField.id)]);

        expect(drafts).toEqual([{
            fieldDefinitionId: referenceField.id,
            fieldContractId: referenceV1.id,
            label: "Reference code",
            keyLabel: "process:reference-code",
            isRequired: false,
            isReadOnly: false,
        }]);
    });

    it("builds a draft from a definition's latest contract", () =>
    {
        expect(draftForDefinition(referenceField)).toEqual({
            fieldDefinitionId: referenceField.id,
            fieldContractId: referenceV2.id,
            label: "Reference code",
            keyLabel: "process:reference-code",
            isRequired: false,
            isReadOnly: false,
        });
    });

    it("builds no draft from a definition without a contract", () =>
    {
        expect(draftForDefinition(definition("field-draft", "draft-only"))).toBeNull();
    });
});
