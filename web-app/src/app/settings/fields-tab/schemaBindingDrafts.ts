import {
    FieldDataClassification,
    FieldDefinitionDto,
    FieldOption,
    FieldValueType,
    SchemaFieldBindingDto,
} from '../../models/models';
import {BindingRequest} from '../../../services/fieldsService';

export interface BindingDraft
{
    fieldDefinitionId: string;
    fieldContractId: string;
    label: string;
    keyLabel: string;
    valueType: FieldValueType;
    options: FieldOption[];
    isRequired: boolean;
    isReadOnly: boolean;
    section?: string;
    defaultValueJson?: string;
    visibility: FieldDataClassification;
}

export const toBindingDrafts = (bindings: SchemaFieldBindingDto[]): BindingDraft[] =>
    bindings.map(binding => ({
        fieldDefinitionId: binding.fieldDefinitionId,
        fieldContractId: binding.fieldContractId,
        label: binding.label,
        keyLabel: `${binding.namespace}:${binding.fieldKey}`,
        valueType: binding.valueType,
        options: binding.options,
        isRequired: binding.isRequired,
        isReadOnly: binding.isReadOnly,
        section: binding.section,
        defaultValueJson: binding.defaultValueJson,
        visibility: binding.visibility,
    }));

export const draftForDefinition = (definition: FieldDefinitionDto): BindingDraft | null =>
{
    if (!definition.latestContract) return null;
    return {
        fieldDefinitionId: definition.id,
        fieldContractId: definition.latestContract.id,
        label: definition.latestContract.label,
        keyLabel: `${definition.namespace}:${definition.fieldKey}`,
        valueType: definition.latestContract.valueType,
        options: definition.latestContract.options,
        isRequired: false,
        isReadOnly: false,
        visibility: definition.latestContract.dataClassification,
    };
};

export const addableDefinitions = (
    definitions: FieldDefinitionDto[],
    bindings: BindingDraft[],
): FieldDefinitionDto[] =>
{
    const used = new Set(bindings.map(binding => binding.fieldDefinitionId));
    return definitions.filter(definition => definition.latestContract && !used.has(definition.id));
};

export const toBindingRequests = (bindings: BindingDraft[]): BindingRequest[] =>
    bindings.map((binding, index) => ({
        fieldContractId: binding.fieldContractId,
        displayOrder: index,
        section: binding.section,
        isRequired: binding.isRequired,
        isReadOnly: binding.isReadOnly,
        defaultValueJson: binding.defaultValueJson,
        visibility: binding.visibility,
    }));
