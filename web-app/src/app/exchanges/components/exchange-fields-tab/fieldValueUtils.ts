import {FieldOption, FieldValueType} from '../../../models/models';

export const isFieldValueEmpty = (value: unknown): boolean =>
{
    if (value === null || value === undefined || value === '')
    {
        return true;
    }

    return Array.isArray(value) && value.length === 0;
};

const labelForOption = (code: string, options: FieldOption[]): string =>
    options.find(option => option.code === code)?.label ?? code;

const formatDate = (value: string): string =>
{
    const [year, month, day] = value.split('T')[0].split('-').map(Number);

    if (!year || !month || !day)
    {
        return value;
    }

    return new Intl.DateTimeFormat('en-GB', {
        day: 'numeric',
        month: 'short',
        year: 'numeric',
    }).format(new Date(year, month - 1, day));
};

const formatDateTime = (value: string): string =>
{
    const normalized = value.includes('T') ? value : value.replace(' ', 'T');
    const parsed = new Date(normalized);

    if (Number.isNaN(parsed.getTime()))
    {
        return value;
    }

    return new Intl.DateTimeFormat('en-GB', {
        day: 'numeric',
        month: 'short',
        year: 'numeric',
        hour: '2-digit',
        minute: '2-digit',
    }).format(parsed);
};

export const getFieldValueLabels = (
    valueType: FieldValueType,
    value: unknown,
    options: FieldOption[],
): string[] =>
{
    if (isFieldValueEmpty(value))
    {
        return [];
    }

    switch (valueType)
    {
        case FieldValueType.SINGLE_SELECT:
            return [labelForOption(String(value), options)];
        case FieldValueType.MULTI_SELECT:
            return Array.isArray(value)
                ? value.map(item => labelForOption(String(item), options))
                : [];
        default:
            return [String(value)];
    }
};

/** Formats a canonical field value for read-only display. */
export const formatFieldValue = (
    valueType: FieldValueType,
    value: unknown,
    options: FieldOption[],
): string =>
{
    if (isFieldValueEmpty(value)) return 'Not provided';

    switch (valueType)
    {
        case FieldValueType.BOOLEAN:
            return value === true ? 'Yes' : 'No';
        case FieldValueType.INTEGER:
        case FieldValueType.DECIMAL:
            return new Intl.NumberFormat('en-US').format(Number(value));
        case FieldValueType.DATE:
            return formatDate(String(value));
        case FieldValueType.DATE_TIME:
            return formatDateTime(String(value));
        case FieldValueType.SINGLE_SELECT:
            return labelForOption(String(value), options);
        case FieldValueType.MULTI_SELECT:
            return Array.isArray(value) && value.length > 0
                ? value.map(item => labelForOption(String(item), options)).join(', ')
                : 'Not provided';
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
