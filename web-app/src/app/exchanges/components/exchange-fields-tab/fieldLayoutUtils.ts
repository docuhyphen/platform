import {FieldValueType, SchemaFieldBindingDto} from '../../../models/models';

export interface FieldSectionGroup
{
    key: string;
    title: string;
    bindings: SchemaFieldBindingDto[];
}

export const DEFAULT_FIELD_SECTION_TITLE = '';

const normalizeSectionTitle = (section?: string): string =>
{
    const trimmed = section?.trim();
    return trimmed && trimmed.length > 0 ? trimmed : DEFAULT_FIELD_SECTION_TITLE;
};

export const toFieldElementId = (value: string): string =>
    value.toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-+|-+$/g, '');

export const getFieldTypeLabel = (valueType: FieldValueType): string =>
{
    switch (valueType)
    {
        case FieldValueType.SHORT_TEXT:
            return 'Short text';
        case FieldValueType.LONG_TEXT:
            return 'Long text';
        case FieldValueType.BOOLEAN:
            return 'Yes or no';
        case FieldValueType.INTEGER:
            return 'Number';
        case FieldValueType.DECIMAL:
            return 'Decimal';
        case FieldValueType.DATE:
            return 'Date';
        case FieldValueType.DATE_TIME:
            return 'Date and time';
        case FieldValueType.SINGLE_SELECT:
            return 'Selection';
        case FieldValueType.MULTI_SELECT:
            return 'Multi selection';
        default:
            return 'Value';
    }
};

export const shouldFieldSpanWide = (valueType: FieldValueType, value: unknown): boolean =>
{
    if (valueType === FieldValueType.LONG_TEXT || valueType === FieldValueType.MULTI_SELECT)
    {
        return true;
    }

    if (typeof value === 'string')
    {
        return value.trim().length > 42;
    }

    return Array.isArray(value) && value.length > 2;
};

export const groupBindingsBySection = (bindings: SchemaFieldBindingDto[]): FieldSectionGroup[] =>
{
    const sortedBindings = [...bindings].sort((left, right) =>
    {
        if (left.displayOrder !== right.displayOrder)
        {
            return left.displayOrder - right.displayOrder;
        }

        return left.label.localeCompare(right.label);
    });

    const groups = new Map<string, FieldSectionGroup>();

    sortedBindings.forEach(binding =>
    {
        const title = normalizeSectionTitle(binding.section);
        const key = toFieldElementId(title);

        if (!groups.has(key))
        {
            groups.set(key, {
                key,
                title,
                bindings: [],
            });
        }

        groups.get(key)?.bindings.push(binding);
    });

    return Array.from(groups.values());
};
