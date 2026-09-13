import {FieldDefinitionDto, SchemaFieldBindingDto} from '../../models/models';

/**
 * One field placed into the schema draft being edited. The stable field is carried alongside the
 * contract because a schema version describes each field once, however many contract versions that
 * field has.
 */
export interface BindingDraft
{
    fieldDefinitionId: string;
    fieldContractId: string;
    label: string;
    keyLabel: string;
    isRequired: boolean;
    isReadOnly: boolean;
}

/** The draft rows for a schema version's existing bindings. */
export const toBindingDrafts = (bindings: SchemaFieldBindingDto[]): BindingDraft[] =>
    bindings.map(binding => ({
        fieldDefinitionId: binding.fieldDefinitionId,
        fieldContractId: binding.fieldContractId,
        label: binding.label,
        keyLabel: `${binding.namespace}:${binding.fieldKey}`,
        isRequired: binding.isRequired,
        isReadOnly: binding.isReadOnly,
    }));

/** A draft row for a field, or null when the field has no contract to bind. */
export const draftForDefinition = (definition: FieldDefinitionDto): BindingDraft | null =>
{
    if (!definition.latestContract) return null;
    return {
        fieldDefinitionId: definition.id,
        fieldContractId: definition.latestContract.id,
        label: definition.latestContract.label,
        keyLabel: `${definition.namespace}:${definition.fieldKey}`,
        isRequired: false,
        isReadOnly: false,
    };
};

/**
 * The fields that may still be added to the draft. A field already bound through any contract
 * version is excluded, so adding a field twice cannot produce two versions of the same field.
 */
export const addableDefinitions = (
    definitions: FieldDefinitionDto[],
    bindings: BindingDraft[],
): FieldDefinitionDto[] =>
{
    const used = new Set(bindings.map(binding => binding.fieldDefinitionId));
    return definitions.filter(definition => definition.latestContract && !used.has(definition.id));
};
