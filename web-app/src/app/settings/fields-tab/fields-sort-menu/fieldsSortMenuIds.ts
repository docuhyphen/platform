import {FieldsListVariant, FieldsSortOrder} from '../fieldsListTypes';

export interface FieldsSortMenuIds
{
    button: string;
    popover: string;
    list: string;
    defaultItem: string;
    nameAscItem: string;
    nameDescItem: string;
}

export const FIELDS_SORT_MENU_IDS: Record<FieldsListVariant, FieldsSortMenuIds> = {
    fields: {
        button: 'field-def-sort-btn',
        popover: 'fields-tab-menu-popover-1',
        list: 'fields-tab-menu-list-1',
        defaultItem: 'field-def-sort-default',
        nameAscItem: 'field-def-sort-name-asc',
        nameDescItem: 'field-def-sort-name-desc',
    },
    schemas: {
        button: 'schema-sort-btn',
        popover: 'fields-tab-menu-popover-2',
        list: 'fields-tab-menu-list-2',
        defaultItem: 'schema-sort-default',
        nameAscItem: 'schema-sort-name-asc',
        nameDescItem: 'schema-sort-name-desc',
    },
};

export const FIELDS_SORT_LABELS: Record<FieldsSortOrder, string> = {
    default: 'Default order',
    nameAsc: 'Name (A-Z)',
    nameDesc: 'Name (Z-A)',
};
