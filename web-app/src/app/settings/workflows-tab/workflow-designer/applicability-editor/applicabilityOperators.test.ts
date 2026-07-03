import {describe, expect, it} from "vitest";
import {FieldOperator, FieldValueType, SchemaFieldBindingDto} from "../../../../models/models.tsx";
import {
    defaultConditionFor,
    operatorIsMultiValue,
    operatorNeedsValue,
    operatorsForType,
} from "./applicabilityOperators.ts";

const binding = (valueType: FieldValueType): SchemaFieldBindingDto => ({
    id: "b1",
    fieldContractId: "c1",
    fieldDefinitionId: "def-1",
    namespace: "org",
    fieldKey: "category",
    label: "Category",
    valueType,
    displayOrder: 0,
    isRequired: false,
    isReadOnly: false,
    visibility: "INTERNAL" as never,
    constraints: {} as never,
    options: [],
});

describe("applicabilityOperators", () =>
{
    it("returns text operators including CONTAINS and STARTS_WITH", () =>
    {
        const ops = operatorsForType(FieldValueType.SHORT_TEXT);
        expect(ops).toContain(FieldOperator.CONTAINS);
        expect(ops).toContain(FieldOperator.STARTS_WITH);
        expect(ops).not.toContain(FieldOperator.GREATER_THAN);
    });

    it("returns number comparison operators", () =>
    {
        const ops = operatorsForType(FieldValueType.INTEGER);
        expect(ops).toContain(FieldOperator.GREATER_THAN);
        expect(ops).toContain(FieldOperator.LESS_THAN_OR_EQUAL);
        expect(ops).not.toContain(FieldOperator.CONTAINS);
    });

    it("single select supports EQUALS and IN but not CONTAINS", () =>
    {
        const ops = operatorsForType(FieldValueType.SINGLE_SELECT);
        expect(ops).toContain(FieldOperator.EQUALS);
        expect(ops).toContain(FieldOperator.IN);
        expect(ops).not.toContain(FieldOperator.CONTAINS);
    });

    it("multi select supports CONTAINS and IN but not EQUALS", () =>
    {
        const ops = operatorsForType(FieldValueType.MULTI_SELECT);
        expect(ops).toContain(FieldOperator.CONTAINS);
        expect(ops).toContain(FieldOperator.IN);
        expect(ops).not.toContain(FieldOperator.EQUALS);
    });

    it("operatorNeedsValue is false only for emptiness operators", () =>
    {
        expect(operatorNeedsValue(FieldOperator.IS_EMPTY)).toBe(false);
        expect(operatorNeedsValue(FieldOperator.IS_NOT_EMPTY)).toBe(false);
        expect(operatorNeedsValue(FieldOperator.EQUALS)).toBe(true);
    });

    it("operatorIsMultiValue is true for IN and NOT_IN", () =>
    {
        expect(operatorIsMultiValue(FieldOperator.IN)).toBe(true);
        expect(operatorIsMultiValue(FieldOperator.NOT_IN)).toBe(true);
        expect(operatorIsMultiValue(FieldOperator.EQUALS)).toBe(false);
    });

    it("defaultConditionFor uses the field's first supported operator and no value", () =>
    {
        const condition = defaultConditionFor(binding(FieldValueType.SINGLE_SELECT));
        expect(condition.fieldDefinitionId).toBe("def-1");
        expect(condition.valueType).toBe(FieldValueType.SINGLE_SELECT);
        expect(condition.operator).toBe(FieldOperator.EQUALS);
        expect(condition.value).toBeUndefined();
    });
});
