import {
    BlueprintFieldDefaultConfig,
    FieldLifecycleStatus,
    SchemaDefinitionDto,
    SchemaFieldBindingDto
} from '../../../models/models';
import {isFieldValueEmpty, toCanonicalValue,} from '../../../exchanges/components/exchange-fields-tab/fieldValueUtils';

export const filterEligibleExchangeSchemas = (
    schemas: SchemaDefinitionDto[],
): SchemaDefinitionDto[] =>
    schemas.filter(schema => !!schema.latestPublishedVersion
        && schema.status !== FieldLifecycleStatus.RETIRED
        && schema.targetResourceType === 'EXCHANGE');

export const buildCreationFieldValues = (
    schemaDefinitionId: string | undefined,
    bindings: SchemaFieldBindingDto[],
    valueMap: Record<string, unknown>,
): { fieldContractId: string; value: unknown }[] | undefined =>
{
    if (!schemaDefinitionId) return undefined;
    return bindings
        .filter(binding => !binding.isReadOnly)
        .filter(binding => !isFieldValueEmpty(valueMap[binding.fieldContractId]))
        .map(binding => ({
            fieldContractId: binding.fieldContractId,
            value: toCanonicalValue(binding.valueType, valueMap[binding.fieldContractId]),
        }));
};

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
