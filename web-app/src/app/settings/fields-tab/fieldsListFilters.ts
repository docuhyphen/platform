import {FieldDefinitionDto, FieldValueType, SchemaDefinitionDto} from '../../models/models';
import {FIELDS_PAGE_SIZE, FieldsSortOrder} from './fieldsListTypes';

export const toggleSetItem = <T,>(set: Set<T>, item: T): Set<T> =>
{
    const next = new Set(set);
    if (next.has(item)) next.delete(item); else next.add(item);
    return next;
};

export const namespacesOf = (items: {namespace: string}[]): string[] =>
    [...new Set(items.map(item => item.namespace))].sort();

export const matchNamespaces = (namespaces: string[], search: string): string[] =>
{
    const q = search.trim().toLowerCase();
    return q ? namespaces.filter(n => n.toLowerCase().includes(q)) : namespaces;
};

const sortByName = <T,>(items: T[], nameOf: (item: T) => string, sortOrder: FieldsSortOrder): T[] =>
{
    if (sortOrder === 'nameAsc') return [...items].sort((a, b) => nameOf(a).localeCompare(nameOf(b)));
    if (sortOrder === 'nameDesc') return [...items].sort((a, b) => nameOf(b).localeCompare(nameOf(a)));
    return items;
};

export const definitionNameOf = (definition: FieldDefinitionDto): string =>
    definition.latestContract?.label ?? definition.fieldKey;

export const filterDefinitions = (
    definitions: FieldDefinitionDto[],
    search: string,
    namespaces: Set<string>,
    types: Set<FieldValueType>,
    sortOrder: FieldsSortOrder,
): FieldDefinitionDto[] =>
{
    const q = search.trim().toLowerCase();
    const result = definitions
        .filter(d => namespaces.size === 0 || namespaces.has(d.namespace))
        .filter(d => types.size === 0 || (d.latestContract && types.has(d.latestContract.valueType)))
        .filter(d =>
            !q ||
            (d.latestContract?.label ?? '').toLowerCase().includes(q) ||
            d.fieldKey.toLowerCase().includes(q) ||
            d.namespace.toLowerCase().includes(q),
        );
    return sortByName(result, definitionNameOf, sortOrder);
};

export const filterSchemas = (
    schemas: SchemaDefinitionDto[],
    search: string,
    namespaces: Set<string>,
    sortOrder: FieldsSortOrder,
): SchemaDefinitionDto[] =>
{
    const q = search.trim().toLowerCase();
    const result = schemas
        .filter(s => namespaces.size === 0 || namespaces.has(s.namespace))
        .filter(s =>
            !q ||
            s.displayName.toLowerCase().includes(q) ||
            (s.description ?? '').toLowerCase().includes(q) ||
            s.namespace.toLowerCase().includes(q) ||
            s.schemaKey.toLowerCase().includes(q),
        );
    return sortByName(result, schema => schema.displayName, sortOrder);
};

export const totalPagesOf = (count: number): number =>
    Math.ceil(count / FIELDS_PAGE_SIZE);

export const pageOf = <T,>(items: T[], page: number): T[] =>
    items.slice(page * FIELDS_PAGE_SIZE, (page + 1) * FIELDS_PAGE_SIZE);
