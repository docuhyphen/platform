import {describe, expect, it} from 'vitest';
import {
    FieldDataClassification,
    FieldValueDto,
    FieldValueType,
    SchemaFieldBindingDto,
} from '../../../models/models';
import {buildSparseFieldValuePayload} from './fieldValuePayload';

const binding = (overrides: Partial<SchemaFieldBindingDto>): SchemaFieldBindingDto => ({
    id: `binding-${overrides.fieldContractId}`,
    fieldContractId: 'contract',
    fieldDefinitionId: 'definition',
    namespace: 'process',
    fieldKey: 'note',
    label: 'Note',
    valueType: FieldValueType.SHORT_TEXT,
    displayOrder: 0,
    isRequired: false,
    isReadOnly: false,
    visibility: FieldDataClassification.INTERNAL,
    constraints: {},
    options: [],
    ...overrides,
});

const storedValue = (fieldContractId: string, value: unknown): FieldValueDto => ({
    fieldContractId,
    namespace: 'process',
    fieldKey: 'note',
    label: 'Note',
    valueType: FieldValueType.SHORT_TEXT,
    isEmpty: value === null || value === undefined || value === '',
    value,
});

describe('buildSparseFieldValuePayload', () =>
{
    it('omits a read-only binding even when its editor holds a value', () =>
    {
        const bindings = [
            binding({fieldContractId: 'editable'}),
            binding({fieldContractId: 'locked', isReadOnly: true}),
        ];

        const payload = buildSparseFieldValuePayload(
            bindings,
            [],
            {editable: 'typed', locked: 'system'},
        );

        expect(payload.map(entry => entry.fieldContractId)).toEqual(['editable']);
    });

    it('omits an untouched binding so an update stays sparse', () =>
    {
        const bindings = [
            binding({fieldContractId: 'changed'}),
            binding({fieldContractId: 'untouched'}),
        ];
        const values = [
            storedValue('changed', 'before'),
            storedValue('untouched', 'kept'),
        ];

        const payload = buildSparseFieldValuePayload(
            bindings,
            values,
            {changed: 'after', untouched: 'kept'},
        );

        expect(payload).toEqual([{fieldContractId: 'changed', value: 'after'}]);
    });

    it('omits a required binding the responder never filled in', () =>
    {
        const bindings = [
            binding({fieldContractId: 'filled'}),
            binding({fieldContractId: 'empty', isRequired: true}),
        ];

        const payload = buildSparseFieldValuePayload(
            bindings,
            [],
            {filled: 'typed', empty: undefined},
        );

        expect(payload.map(entry => entry.fieldContractId)).toEqual(['filled']);
    });

    it('includes a cleared binding so a stored value can be removed', () =>
    {
        const bindings = [binding({fieldContractId: 'cleared'})];
        const values = [storedValue('cleared', 'was set')];

        const payload = buildSparseFieldValuePayload(bindings, values, {cleared: ''});

        expect(payload).toEqual([{fieldContractId: 'cleared', value: null}]);
    });

    it('treats an unanswered boolean as unchanged rather than a false answer', () =>
    {
        const bindings = [
            binding({fieldContractId: 'flag', valueType: FieldValueType.BOOLEAN}),
        ];

        const payload = buildSparseFieldValuePayload(bindings, [], {flag: undefined});

        expect(payload).toEqual([]);
    });

    it('sends an explicit no on a field that was never answered', () =>
    {
        const bindings = [
            binding({fieldContractId: 'flag', valueType: FieldValueType.BOOLEAN}),
        ];

        const payload = buildSparseFieldValuePayload(bindings, [], {flag: false});

        expect(payload).toEqual([{fieldContractId: 'flag', value: false}]);
    });

    it('sends a number as exact digits so no precision is lost on the way out', () =>
    {
        const wide = '1234567890123456789012345678.0123456789';
        const bindings = [
            binding({fieldContractId: 'measure', valueType: FieldValueType.DECIMAL}),
        ];

        const payload = buildSparseFieldValuePayload(bindings, [], {measure: wide});

        expect(payload).toEqual([{fieldContractId: 'measure', value: wide}]);
    });

    it('leaves a number alone when only its stored padding differs', () =>
    {
        const bindings = [
            binding({fieldContractId: 'measure', valueType: FieldValueType.DECIMAL}),
        ];
        const values = [storedValue('measure', '1.50')];

        const payload = buildSparseFieldValuePayload(bindings, values, {measure: '1.50'});

        expect(payload).toEqual([]);
    });

    it('includes a boolean the responder answered', () =>
    {
        const bindings = [
            binding({fieldContractId: 'flag', valueType: FieldValueType.BOOLEAN}),
        ];

        const payload = buildSparseFieldValuePayload(bindings, [], {flag: true});

        expect(payload).toEqual([{fieldContractId: 'flag', value: true}]);
    });
});
