import {FieldOption, FieldValueType} from '../../../models/models';

/** Formats a canonical field value for read-only display. */
export const formatFieldValue = (
    valueType: FieldValueType,
    value: unknown,
    options: FieldOption[],
): string =>
{
    if (value === null || value === undefined || value === '') return '-';

    const labelFor = (code: string): string =>
        options.find(o => o.code === code)?.label ?? code;

    switch (valueType)
    {
        case FieldValueType.BOOLEAN:
            return value === true ? 'Yes' : 'No';
        case FieldValueType.SINGLE_SELECT:
            return labelFor(String(value));
        case FieldValueType.MULTI_SELECT:
            return Array.isArray(value) && value.length > 0
                ? value.map(v => labelFor(String(v))).join(', ')
                : '-';
        default:
            return String(value);
    }
};

/** Normalizes an editor value into the canonical JSON shape the API expects. */
export const toCanonicalValue = (valueType: FieldValueType, value: unknown): unknown =>
{
    switch (valueType)
    {
        case FieldValueType.BOOLEAN:
            return value === true;
        case FieldValueType.INTEGER:
        case FieldValueType.DECIMAL:
        {
            if (value === '' || value === null || value === undefined) return null;
            const parsed = Number(value);
            return Number.isNaN(parsed) ? null : parsed;
        }
        case FieldValueType.MULTI_SELECT:
            return Array.isArray(value) ? value : [];
        default:
            return value === '' || value === undefined ? null : value;
    }
};
