import {describe, expect, it} from 'vitest';
import {
    buildBlueprintFieldDefaults,
    buildCreationFieldValues,
    filterEligibleExchangeSchemas
} from './creationFieldsUtils';
import {
    FieldDataClassification,
    FieldLifecycleStatus,
    FieldScopeKind,
    FieldValueType,
    SchemaDefinitionDto,
    SchemaFieldBindingDto
} from '../../../models/models';

const schema = (over: Partial<SchemaDefinitionDto>): SchemaDefinitionDto => ({
    id: 'id',
    scopeKind: FieldScopeKind.ORGANIZATION,
    namespace: 'acme',
    schemaKey: 'onboarding',
    displayName: 'Client Onboarding',
    targetResourceType: 'EXCHANGE',
    status: FieldLifecycleStatus.PUBLISHED,
    latestPublishedVersion: {
        id: 'v1',
        schemaDefinitionId: 'id',
        versionNumber: 1,
        status: FieldLifecycleStatus.PUBLISHED,
        bindings: [],
        createdAt: '2026-01-01',
    },
    createdAt: '2026-01-01',
    ...over,
});

const binding = (over: Partial<SchemaFieldBindingDto>): SchemaFieldBindingDto => ({
    id: 'b',
    fieldContractId: 'c',
    fieldDefinitionId: 'd',
    namespace: 'acme',
    fieldKey: 'category',
    label: 'Category',
    valueType: FieldValueType.SHORT_TEXT,
    displayOrder: 0,
    isRequired: false,
    isReadOnly: false,
    visibility: FieldDataClassification.INTERNAL,
    constraints: {} as SchemaFieldBindingDto['constraints'],
    options: [],
    ...over,
});

describe('filterEligibleExchangeSchemas', () =>
{
    it('keeps published, non-retired, EXCHANGE-targeted schemas', () =>
    {
        const result = filterEligibleExchangeSchemas([schema({id: 'a'})]);
        expect(result.map(s => s.id)).toEqual(['a']);
    });

    it('drops schemas without a published version', () =>
    {
        const result = filterEligibleExchangeSchemas([schema({id: 'a', latestPublishedVersion: undefined})]);
        expect(result).toHaveLength(0);
    });

    it('drops retired schemas', () =>
    {
        const result = filterEligibleExchangeSchemas([schema({id: 'a', status: FieldLifecycleStatus.RETIRED})]);
        expect(result).toHaveLength(0);
    });

    it('drops schemas that do not target EXCHANGE', () =>
    {
        const result = filterEligibleExchangeSchemas([schema({id: 'a', targetResourceType: 'BLUEPRINT'})]);
        expect(result).toHaveLength(0);
    });
});

describe('buildCreationFieldValues', () =>
{
    it('returns undefined when no schema is selected', () =>
    {
        expect(buildCreationFieldValues(undefined, [binding({})], {c: 'x'})).toBeUndefined();
    });

    it('builds canonical entries and excludes read-only bindings', () =>
    {
        const bindings = [
            binding({fieldContractId: 'c1', valueType: FieldValueType.SHORT_TEXT}),
            binding({fieldContractId: 'c2', valueType: FieldValueType.BOOLEAN, isReadOnly: true}),
        ];
        const result = buildCreationFieldValues('schema-1', bindings, {c1: 'Onboarding', c2: true});
        expect(result).toEqual([{fieldContractId: 'c1', value: 'Onboarding'}]);
    });

    it('canonicalizes typed values', () =>
    {
        const bindings = [
            binding({fieldContractId: 'num', valueType: FieldValueType.INTEGER}),
            binding({fieldContractId: 'flag', valueType: FieldValueType.BOOLEAN}),
        ];
        const result = buildCreationFieldValues('schema-1', bindings, {num: '42', flag: true});
        expect(result).toEqual([
            {fieldContractId: 'num', value: '42'},
            {fieldContractId: 'flag', value: true},
        ]);
    });

    it('omits a binding the initiator left blank so its configured default survives', () =>
    {
        const bindings = [
            binding({fieldContractId: 'filled', valueType: FieldValueType.SHORT_TEXT}),
            binding({fieldContractId: 'blank', valueType: FieldValueType.SHORT_TEXT}),
        ];
        const result = buildCreationFieldValues('schema-1', bindings, {filled: 'Provided', blank: ''});
        expect(result).toEqual([{fieldContractId: 'filled', value: 'Provided'}]);
    });

    it('omits an unanswered boolean but keeps an explicit no', () =>
    {
        const bindings = [
            binding({fieldContractId: 'unanswered', valueType: FieldValueType.BOOLEAN}),
            binding({fieldContractId: 'answered', valueType: FieldValueType.BOOLEAN}),
        ];
        const result = buildCreationFieldValues('schema-1', bindings, {answered: false});
        expect(result).toEqual([{fieldContractId: 'answered', value: false}]);
    });
});

describe('buildBlueprintFieldDefaults', () =>
{
    it('returns empty array when no schema is selected', () =>
    {
        expect(buildBlueprintFieldDefaults(undefined, [binding({})], {c: 'x'})).toEqual([]);
    });

    it('keys defaults by fieldDefinitionId, carries valueType, and omits empty values', () =>
    {
        const bindings = [
            binding({fieldContractId: 'c1', fieldDefinitionId: 'd1', valueType: FieldValueType.SHORT_TEXT}),
            binding({fieldContractId: 'c2', fieldDefinitionId: 'd2', valueType: FieldValueType.SHORT_TEXT}),
        ];
        const result = buildBlueprintFieldDefaults('schema-1', bindings, {c1: 'Onboarding', c2: ''});
        expect(result).toEqual([
            {fieldDefinitionId: 'd1', valueType: FieldValueType.SHORT_TEXT, value: 'Onboarding', displayOrder: 0},
        ]);
    });

    it('canonicalizes typed values before storing them as defaults', () =>
    {
        const bindings = [
            binding({fieldContractId: 'num', fieldDefinitionId: 'dn', valueType: FieldValueType.INTEGER}),
        ];
        const result = buildBlueprintFieldDefaults('schema-1', bindings, {num: '42'});
        expect(result).toEqual([
            {fieldDefinitionId: 'dn', valueType: FieldValueType.INTEGER, value: '42', displayOrder: 0},
        ]);
    });

    it('does not turn a yes or no field the initiator never touched into a No default', () =>
    {
        const bindings = [
            binding({fieldContractId: 'flag', fieldDefinitionId: 'df', valueType: FieldValueType.BOOLEAN}),
        ];

        expect(buildBlueprintFieldDefaults('schema-1', bindings, {})).toEqual([]);
    });

    it('keeps an explicit No the initiator chose as a default', () =>
    {
        const bindings = [
            binding({fieldContractId: 'flag', fieldDefinitionId: 'df', valueType: FieldValueType.BOOLEAN}),
        ];

        expect(buildBlueprintFieldDefaults('schema-1', bindings, {flag: false})).toEqual([
            {fieldDefinitionId: 'df', valueType: FieldValueType.BOOLEAN, value: false, displayOrder: 0},
        ]);
    });
});
