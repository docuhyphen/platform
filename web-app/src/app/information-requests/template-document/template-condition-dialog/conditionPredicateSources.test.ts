import {describe, expect, it} from "vitest";
import {
    FieldDataClassification,
    FieldValueType,
    InformationRequestRequirementType,
    SchemaFieldBindingDto,
} from "../../../models/models.tsx";
import {newRequirement} from "../templateDraftDocument.ts";
import {conditionPredicateSources} from "./conditionPredicateSources.ts";

const collected: SchemaFieldBindingDto = {
    id: "binding-note",
    fieldContractId: "contract-note",
    fieldDefinitionId: "field-note",
    namespace: "process",
    fieldKey: "note",
    label: "Note",
    valueType: FieldValueType.SHORT_TEXT,
    displayOrder: 0,
    isRequired: false,
    isReadOnly: false,
    visibility: FieldDataClassification.INTERNAL,
    constraints: {},
    options: [],
};

const requirements = [
    {
        ...newRequirement(InformationRequestRequirementType.FIELD, "recorded-note", "Provide a note"),
        collectedFieldDefinitionId: "field-note",
    },
    newRequirement(InformationRequestRequirementType.DOCUMENT, "supporting-record", "Upload a record"),
    newRequirement(InformationRequestRequirementType.RESPONSE_ATTESTATION, "confirm-record", "Confirm the record"),
];

describe("conditionPredicateSources", () => {
    it("offers the answers to typed and document requirements and the values of collected Fields", () => {
        const values = conditionPredicateSources(requirements, [collected]).map(source => source.value);

        expect(values).toEqual([
            "requirement:recorded-note",
            "requirement:supporting-record",
            "field:field-note",
        ]);
    });

    it("never offers a confirmation as something a condition reads", () => {
        const values = conditionPredicateSources(requirements, [collected]).map(source => source.value);

        expect(values).not.toContain("requirement:confirm-record");
    });
});
