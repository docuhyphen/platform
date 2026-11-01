import {FieldDataClassification, FieldOption, FieldValueType} from '../../../models/models';
import {CreateFieldDefinitionRequest, FieldContractRequest} from '../../../../services/fieldsService';
import {typeSupportsOptions} from '../fieldLabels';

export interface FieldForm
{
    namespace: string;
    fieldKey: string;
    valueType: FieldValueType;
    label: string;
    helpText: string;
    classification: FieldDataClassification;
    options: FieldOption[];
}

export const emptyFieldForm = (): FieldForm => ({
    namespace: '',
    fieldKey: '',
    valueType: FieldValueType.SHORT_TEXT,
    label: '',
    helpText: '',
    classification: FieldDataClassification.INTERNAL,
    options: [],
});

const namedOptions = (form: FieldForm): FieldOption[] =>
    form.options.filter(o => o.code.trim());

export const fieldFormValidationError = (form: FieldForm): string | null =>
{
    if (!form.namespace.trim() || !form.fieldKey.trim() || !form.label.trim())
    {
        return 'Namespace, key, and label are required';
    }
    if (typeSupportsOptions(form.valueType) && namedOptions(form).length === 0)
    {
        return 'Add at least one option for a selection field';
    }
    return null;
};

export const toCreateFieldRequest = (form: FieldForm): Omit<CreateFieldDefinitionRequest, 'scopeKind'> =>
{
    const contract: FieldContractRequest = {
        valueType: form.valueType,
        label: form.label.trim(),
        helpText: form.helpText.trim() || undefined,
        dataClassification: form.classification,
        options: typeSupportsOptions(form.valueType) ? namedOptions(form) : [],
    };
    return {
        namespace: form.namespace.trim(),
        fieldKey: form.fieldKey.trim(),
        contract,
    };
};

export const createFieldFailureMessage = (failure: unknown): string =>
    typeof failure === 'object' && failure !== null && 'error' in failure
        ? String((failure as {error: string}).error)
        : 'Failed to create field';
