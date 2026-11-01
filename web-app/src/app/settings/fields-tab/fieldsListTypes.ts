import {FieldDefinitionDto, FieldValueType, SchemaDefinitionDto, ViewMode} from '../../models/models';

export type FieldsSortOrder = 'default' | 'nameAsc' | 'nameDesc';

export type FieldsListVariant = 'fields' | 'schemas';

export const FIELDS_PAGE_SIZE = 12;

export interface FieldsListControls
{
    search: string;
    filterNamespaces: Set<string>;
    filterSearch: string;
    sortOrder: FieldsSortOrder;
    currentPage: number;
    viewMode: ViewMode;
    changeSearch: (value: string) => void;
    toggleNamespace: (namespace: string) => void;
    setFilterSearch: (value: string) => void;
    changeSortOrder: (order: FieldsSortOrder) => void;
    setCurrentPage: (page: number) => void;
    setViewMode: (mode: ViewMode) => void;
    reset: () => void;
}

export interface FieldsListState<T>
{
    controls: FieldsListControls;
    loading: boolean;
    error: string | null;
    namespaces: string[];
    filteredNamespaces: string[];
    filtered: T[];
    visible: T[];
    totalPages: number;
    filtersActive: boolean;
    load: () => void;
    reset: () => void;
}

export interface FieldDefinitionsList extends FieldsListState<FieldDefinitionDto>
{
    filterTypes: Set<FieldValueType>;
    toggleType: (type: FieldValueType) => void;
    retire: (definition: FieldDefinitionDto) => Promise<void>;
}

export type SchemasList = FieldsListState<SchemaDefinitionDto>;
