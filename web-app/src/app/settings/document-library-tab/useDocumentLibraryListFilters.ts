import {useEffect, useMemo, useState} from 'react';
import {DocumentLibraryEntrySummaryDto} from '../../models/models.tsx';
import {DocumentLibrarySortOrder} from './documentLibraryListTypes.ts';
import {
    collectDocumentLibraryTags,
    DOCUMENT_LIBRARY_PAGE_SIZE,
    filterAndSortDocumentLibraryEntries,
    filterDocumentLibraryTagOptions,
} from './documentLibraryListFilters.ts';

export interface DocumentLibraryListFilters
{
    searchQuery: string;
    setSearchQuery: (value: string) => void;
    selectedTags: Set<string>;
    toggleTag: (tag: string) => void;
    removeTag: (tag: string) => void;
    clearTags: () => void;
    sortOrder: DocumentLibrarySortOrder;
    setSortOrder: (order: DocumentLibrarySortOrder) => void;
    filterSearch: string;
    setFilterSearch: (value: string) => void;
    availableTags: string[];
    filteredTagOptions: string[];
    filteredEntries: DocumentLibraryEntrySummaryDto[];
    visibleEntries: DocumentLibraryEntrySummaryDto[];
    currentPage: number;
    setCurrentPage: (page: number) => void;
    totalPages: number;
    resetFilters: () => void;
}

export const useDocumentLibraryListFilters = (entries: DocumentLibraryEntrySummaryDto[]): DocumentLibraryListFilters =>
{
    const [searchQuery, setSearchQueryState] = useState('');
    const [selectedTags, setSelectedTags] = useState<Set<string>>(new Set());
    const [sortOrder, setSortOrderState] = useState<DocumentLibrarySortOrder>('default');
    const [currentPage, setCurrentPage] = useState(0);
    const [filterSearch, setFilterSearch] = useState('');

    const availableTags = useMemo(() => collectDocumentLibraryTags(entries), [entries]);
    const filteredTagOptions = useMemo(
        () => filterDocumentLibraryTagOptions(availableTags, filterSearch),
        [availableTags, filterSearch],
    );
    const filteredEntries = useMemo(
        () => filterAndSortDocumentLibraryEntries(entries, searchQuery, selectedTags, sortOrder),
        [entries, searchQuery, selectedTags, sortOrder],
    );

    const totalPages = Math.ceil(filteredEntries.length / DOCUMENT_LIBRARY_PAGE_SIZE);
    const visibleEntries = filteredEntries.slice(
        currentPage * DOCUMENT_LIBRARY_PAGE_SIZE,
        (currentPage + 1) * DOCUMENT_LIBRARY_PAGE_SIZE,
    );

    useEffect(() =>
    {
        if (currentPage > 0 && currentPage >= Math.max(totalPages, 1))
        {
            setCurrentPage(Math.max(totalPages - 1, 0));
        }
    }, [currentPage, totalPages]);

    const setSearchQuery = (value: string) =>
    {
        setSearchQueryState(value);
        setCurrentPage(0);
    };

    const setSortOrder = (order: DocumentLibrarySortOrder) =>
    {
        setSortOrderState(order);
        setCurrentPage(0);
    };

    const toggleTag = (tag: string) =>
    {
        setSelectedTags(prev =>
        {
            const next = new Set(prev);
            if (next.has(tag)) next.delete(tag); else next.add(tag);
            return next;
        });
        setCurrentPage(0);
    };

    const removeTag = (tag: string) =>
    {
        setSelectedTags(prev =>
        {
            const next = new Set(prev);
            next.delete(tag);
            return next;
        });
        setCurrentPage(0);
    };

    const clearTags = () =>
    {
        setSelectedTags(new Set());
        setCurrentPage(0);
    };

    const resetFilters = () =>
    {
        setSearchQueryState('');
        setSelectedTags(new Set());
        setSortOrderState('default');
        setCurrentPage(0);
        setFilterSearch('');
    };

    return {
        searchQuery,
        setSearchQuery,
        selectedTags,
        toggleTag,
        removeTag,
        clearTags,
        sortOrder,
        setSortOrder,
        filterSearch,
        setFilterSearch,
        availableTags,
        filteredTagOptions,
        filteredEntries,
        visibleEntries,
        currentPage,
        setCurrentPage,
        totalPages,
        resetFilters,
    };
};
