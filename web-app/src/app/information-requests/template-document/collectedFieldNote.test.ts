import {describe, expect, it} from "vitest";
import {FieldDataClassification, FieldValueType, SchemaFieldBindingDto} from "../../models/models.tsx";
import {collectedFieldNote} from "./collectedFieldNote.ts";

const binding = (overrides: Partial<SchemaFieldBindingDto>): SchemaFieldBindingDto => ({
    id: "binding-1",
    fieldContractId: "contract-1",
    fieldDefinitionId: "field-1",
    namespace: "process",
    fieldKey: "recorded-note",
    label: "Recorded note",
    valueType: FieldValueType.SHORT_TEXT,
    displayOrder: 1,
    isRequired: false,
    isReadOnly: false,
    visibility: FieldDataClassification.PUBLIC,
    constraints: {},
    options: [],
    ...overrides,
});

describe("collectedFieldNote", () => {
    it("says nothing about a Public Field anyone can answer", () => {
        expect(collectedFieldNote(binding({}))).toBeUndefined();
        expect(collectedFieldNote(undefined)).toBeUndefined();
    });

    it("explains that a Field that is not Public is answered only from inside the owner", () => {
        [FieldDataClassification.INTERNAL, FieldDataClassification.CONFIDENTIAL, FieldDataClassification.RESTRICTED]
            .forEach(visibility => expect(collectedFieldNote(binding({visibility}))).toContain("not marked Public"));
    });

    it("explains that a read-only Field cannot be answered by anyone", () => {
        expect(collectedFieldNote(binding({isReadOnly: true, visibility: FieldDataClassification.INTERNAL})))
            .toBe("Its Request Schema marks this Field read-only, so nobody could answer it.");
    });
});
