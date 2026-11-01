import {useEffect, useState} from 'react';
import {ViewMode} from '../../models/models';
import {FieldsListControls, FieldsSortOrder} from './fieldsListTypes';
import {toggleSetItem} from './fieldsListFilters';

export const useFieldsListControls = (): FieldsListControls =>
{
    const [search, setSearch] = useState('');
    const [filterNamespaces, setFilterNamespaces] = useState<Set<string>>(new Set());
    const [filterSearch, setFilterSearch] = useState('');
    const [sortOrder, setSortOrder] = useState<FieldsSortOrder>('default');
    const [currentPage, setCurrentPage] = useState(0);
    const [viewMode, setViewMode] = useState<ViewMode>('cards');

    const changeSearch = (value: string) =>
    {
        setSearch(value);
        setCurrentPage(0);
    };

    const toggleNamespace = (namespace: string) =>
    {
        setFilterNamespaces(prev => toggleSetItem(prev, namespace));
        setCurrentPage(0);
    };

    const changeSortOrder = (order: FieldsSortOrder) =>
    {
        setSortOrder(order);
        setCurrentPage(0);
    };

    const reset = () =>
    {
        setSearch('');
        setFilterNamespaces(new Set());
        setFilterSearch('');
        setSortOrder('default');
        setCurrentPage(0);
    };

    return {
        search,
        filterNamespaces,
        filterSearch,
        sortOrder,
        currentPage,
        viewMode,
        changeSearch,
        toggleNamespace,
        setFilterSearch,
        changeSortOrder,
        setCurrentPage,
        setViewMode,
        reset,
    };
};

export const usePageClamp = (currentPage: number, totalPages: number, setCurrentPage: (page: number) => void) =>
{
    useEffect(() =>
    {
        if (currentPage > 0 && currentPage >= Math.max(totalPages, 1))
        {
            setCurrentPage(Math.max(totalPages - 1, 0));
        }
    }, [currentPage, totalPages]);
};
