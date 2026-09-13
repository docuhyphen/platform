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

/** The canonical value a binding currently holds on the server, or the empty value when unanswered. */
const storedCanonicalValue = (
    binding: SchemaFieldBindingDto,
    values: FieldValueDto[],
): unknown =>
{
    const stored = values.find(value => value.fieldContractId === binding.fieldContractId);
    return toCanonicalValue(binding.valueType, stored && !stored.isEmpty ? stored.value : undefined);
};

/**
 * The field value entries to send for an assigned schema.
 *
 * Updates are sparse: an entry is sent only for a binding whose canonical value actually changed, so
 * a binding the responder never touched keeps whatever it already held and a required binding they
 * have not reached yet does not block saving the fields they did fill in. Clearing a field is a real
 * change and is sent, which is how a stored value gets removed.
 *
 * Read-only bindings are never sent. Their values come from the schema's configured defaults, and
 * the server refuses a caller-supplied value for them.
 */
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
