import {FieldValueDto, SchemaFieldBindingDto} from '../../../models/models';

export const storedFieldValues = (
    bindings: SchemaFieldBindingDto[],
    values: FieldValueDto[],
): Record<string, unknown> =>
{
    const stored: Record<string, unknown> = {};
    bindings.forEach(binding =>
    {
        const held = values.find(value => value.fieldContractId === binding.fieldContractId);
        stored[binding.fieldContractId] = held && !held.isEmpty ? held.value : undefined;
    });
    return stored;
};

export const mergeFieldValueEdits = (
    stored: Record<string, unknown>,
    edits: Record<string, unknown>,
): Record<string, unknown> => ({...stored, ...edits});
