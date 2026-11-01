import {FieldValueDto, SchemaFieldBindingDto} from '../../../models/models';
import {toCanonicalValue} from './fieldValueUtils';

export interface FieldValueEntryPayload
{
    fieldContractId: string;
    value: unknown;
}

const sameCanonicalValue = (left: unknown, right: unknown): boolean =>
{
    if (Array.isArray(left) && Array.isArray(right))
    {
        return left.length === right.length && left.every((item, index) => item === right[index]);
    }

    return left === right;
};

const storedCanonicalValue = (
    binding: SchemaFieldBindingDto,
    values: FieldValueDto[],
): unknown =>
{
    const stored = values.find(value => value.fieldContractId === binding.fieldContractId);
    return toCanonicalValue(binding.valueType, stored && !stored.isEmpty ? stored.value : undefined);
};

export const buildSparseFieldValuePayload = (
    bindings: SchemaFieldBindingDto[],
    values: FieldValueDto[],
    editorState: Record<string, unknown>,
): FieldValueEntryPayload[] =>
    bindings
        .filter(binding => !binding.isReadOnly)
        .map(binding => ({
            binding,
            value: toCanonicalValue(binding.valueType, editorState[binding.fieldContractId]),
        }))
        .filter(({binding, value}) => !sameCanonicalValue(value, storedCanonicalValue(binding, values)))
        .map(({binding, value}) => ({fieldContractId: binding.fieldContractId, value}));
