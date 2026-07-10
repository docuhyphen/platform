import {SchemaFieldBindingDto} from '../../../models/models';

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
