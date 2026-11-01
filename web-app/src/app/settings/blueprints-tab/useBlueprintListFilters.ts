import {useEffect, useMemo, useState} from 'react';
import {BlueprintDefinitionSummaryDto} from '../../models/models.tsx';
import {BlueprintSortOrder} from './blueprintListTypes.ts';
import {
    BLUEPRINTS_PAGE_SIZE,
    collectBlueprintTags,
    filterAndSortBlueprints,
    filterTagOptions,
} from './blueprintListFilters.ts';

export interface BlueprintListFilters
{
    searchQuery: string;
    setSearchQuery: (value: string) => void;
    filterSearch: string;
    setFilterSearch: (value: string) => void;
    availableTags: string[];
    filteredTagOptions: string[];
    selectedTags: Set<string>;
    toggleTag: (tag: string) => void;
    removeTag: (tag: string) => void;
    clearTags: () => void;
    sortOrder: BlueprintSortOrder;
    setSortOrder: (order: BlueprintSortOrder) => void;
    filteredBlueprints: BlueprintDefinitionSummaryDto[];
    visibleBlueprints: BlueprintDefinitionSummaryDto[];
    currentPage: number;
    setCurrentPage: (page: number) => void;
    totalPages: number;
    reset: () => void;
}

export const useBlueprintListFilters = (blueprints: BlueprintDefinitionSummaryDto[]): BlueprintListFilters =>
{
    const [searchQuery, setSearchQueryState] = useState('');
    const [selectedTags, setSelectedTags] = useState<Set<string>>(new Set());
    const [sortOrder, setSortOrderState] = useState<BlueprintSortOrder>('default');
    const [currentPage, setCurrentPage] = useState(0);
    const [filterSearch, setFilterSearch] = useState('');

    const availableTags = useMemo(() => collectBlueprintTags(blueprints), [blueprints]);
    const filteredTagOptions = useMemo(
        () => filterTagOptions(availableTags, filterSearch),
        [availableTags, filterSearch],
    );
    const filteredBlueprints = useMemo(
        () => filterAndSortBlueprints(blueprints, searchQuery, selectedTags, sortOrder),
        [blueprints, searchQuery, selectedTags, sortOrder],
    );
    const totalPages = Math.ceil(filteredBlueprints.length / BLUEPRINTS_PAGE_SIZE);
    const visibleBlueprints = filteredBlueprints.slice(
        currentPage * BLUEPRINTS_PAGE_SIZE,
        (currentPage + 1) * BLUEPRINTS_PAGE_SIZE,
    );

    useEffect(() =>
    {
        if (currentPage > 0 && currentPage >= Math.max(totalPages, 1))
        {
            setCurrentPage(Math.max(totalPages - 1, 0));
        }
    }, [currentPage, totalPages]);

    const changeSelectedTags = (change: (prev: Set<string>) => Set<string>) =>
    {
        setSelectedTags(change);
        setCurrentPage(0);
    };

    const toggleTag = (tag: string) =>
        changeSelectedTags(prev =>
        {
            const next = new Set(prev);
            if (next.has(tag)) next.delete(tag); else next.add(tag);
            return next;
        });

    const removeTag = (tag: string) =>
        changeSelectedTags(prev =>
        {
            const next = new Set(prev);
            next.delete(tag);
            return next;
        });

    const reset = () =>
    {
        setSearchQueryState('');
        setSelectedTags(new Set());
        setSortOrderState('default');
        setCurrentPage(0);
        setFilterSearch('');
    };

    return {
        searchQuery,
        setSearchQuery: value =>
        {
            setSearchQueryState(value);
            setCurrentPage(0);
        },
        filterSearch,
        setFilterSearch,
        availableTags,
        filteredTagOptions,
        selectedTags,
        toggleTag,
        removeTag,
        clearTags: () => changeSelectedTags(() => new Set()),
        sortOrder,
        setSortOrder: order =>
        {
            setSortOrderState(order);
            setCurrentPage(0);
        },
        filteredBlueprints,
        visibleBlueprints,
        currentPage,
        setCurrentPage,
        totalPages,
        reset,
    };
};
