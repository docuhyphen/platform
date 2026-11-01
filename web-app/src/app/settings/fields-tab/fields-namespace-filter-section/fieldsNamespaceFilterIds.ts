import {FieldsListVariant} from '../fieldsListTypes';

export interface FieldsNamespaceFilterIds
{
    section: string;
    title: string;
    search: string;
    list: string;
    checkboxPrefix: string;
    empty: string;
}

export const FIELDS_NAMESPACE_FILTER_IDS: Record<FieldsListVariant, FieldsNamespaceFilterIds> = {
    fields: {
        section: 'fields-tab-div-5',
        title: 'fields-tab-text-1',
        search: 'fields-tab-search-box-1',
        list: 'fields-tab-div-6',
        checkboxPrefix: 'fields-tab-checkbox-1',
        empty: 'fields-tab-text-2',
    },
    schemas: {
        section: 'fields-tab-div-11',
        title: 'fields-tab-text-4',
        search: 'fields-tab-search-box-2',
        list: 'fields-tab-div-12',
        checkboxPrefix: 'fields-tab-checkbox-3',
        empty: 'fields-tab-text-5',
    },
};
