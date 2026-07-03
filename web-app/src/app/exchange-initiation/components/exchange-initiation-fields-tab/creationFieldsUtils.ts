import {BlueprintFieldDefaultConfig, FieldLifecycleStatus, SchemaDefinitionDto, SchemaFieldBindingDto} from '../../../models/models';
import {toCanonicalValue} from '../../../exchanges/components/exchange-fields-tab/fieldValueUtils';

/**
 * Schemas selectable at Exchange creation: those with a published version, not retired, and
 * targeting the EXCHANGE resource type.
 */
export const filterEligibleExchangeSchemas = (
    schemas: SchemaDefinitionDto[],
): SchemaDefinitionDto[] =>
    schemas.filter(schema => !!schema.latestPublishedVersion
        && schema.status !== FieldLifecycleStatus.RETIRED
        && schema.targetResourceType === 'EXCHANGE');

/**
 * Builds the canonical creation-time field values for the initiation request. Returns undefined
 * when no schema is chosen. Read-only bindings are excluded (the backend rejects values for them).
 */
export const buildCreationFieldValues = (
    schemaDefinitionId: string | undefined,
    bindings: SchemaFieldBindingDto[],
    valueMap: Record<string, unknown>,
): { fieldContractId: string; value: unknown }[] | undefined =>
{
    if (!schemaDefinitionId) return undefined;
    return bindings
        .filter(binding => !binding.isReadOnly)
        .map(binding => ({
            fieldContractId: binding.fieldContractId,
            value: toCanonicalValue(binding.valueType, valueMap[binding.fieldContractId]),
        }));
};

/**
 * Captures the current schema field values as blueprint defaults, keyed by the stable
 * fieldDefinitionId so they survive schema re-publishing. Empty values are omitted. Returns an empty
 * array when no schema is chosen.
 */
export const buildBlueprintFieldDefaults = (
    schemaDefinitionId: string | undefined,
    bindings: SchemaFieldBindingDto[],
    valueMap: Record<string, unknown>,
): BlueprintFieldDefaultConfig[] =>
{
    if (!schemaDefinitionId) return [];
    return bindings
        .map((binding, index) =>
        {
            const value = toCanonicalValue(binding.valueType, valueMap[binding.fieldContractId]);
            return {binding, value, index};
        })
        .filter(({value}) => value !== undefined && value !== null && value !== '')
        .map(({binding, value, index}) => ({
            fieldDefinitionId: binding.fieldDefinitionId,
            valueType: binding.valueType,
            value,
            displayOrder: index,
        }));
};
