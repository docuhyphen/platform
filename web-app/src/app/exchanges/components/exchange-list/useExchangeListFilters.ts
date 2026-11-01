import {useState} from 'react';

const DEFAULT_SORT_BY = 'createdDate';
const DEFAULT_SORT_DIRECTION = 'DESC';

export const useExchangeListFilters = (markLoading: () => void) =>
{
    const [searchQuery, setSearchQuery] = useState<string>('');
    const [selectedInitiator, setSelectedInitiator] = useState<boolean | null>(null);
    const [currentPage, setCurrentPage] = useState(0);
    const [pageSize] = useState(30);
    const [totalPages, setTotalPages] = useState(0);
    const [sortBy, setSortBy] = useState(DEFAULT_SORT_BY);
    const [sortDirection, setSortDirection] = useState(DEFAULT_SORT_DIRECTION);

    const resetFilters = () =>
    {
        setCurrentPage(0);
        setSearchQuery('');
        setSelectedInitiator(null);
        setSortBy(DEFAULT_SORT_BY);
        setSortDirection(DEFAULT_SORT_DIRECTION);
    };

    const handlePageChange = (page: number) =>
    {
        markLoading();
        setCurrentPage(page);
    };

    const handleSearchQueryChange = (query: string) =>
    {
        markLoading();
        setSearchQuery(query);
    };

    const handleInitiatorFilterChange = (initiatedBy: boolean | null) =>
    {
        markLoading();
        setSelectedInitiator(initiatedBy);
    };

    const handleSortChange = (field: string, direction: string) =>
    {
        markLoading();
        setSortBy(field);
        setSortDirection(direction);
    };

    const handleClearFilters = () =>
    {
        markLoading();
        resetFilters();
    };

    return {
        searchQuery,
        selectedInitiator,
        currentPage,
        setCurrentPage,
        pageSize,
        totalPages,
        setTotalPages,
        sortBy,
        sortDirection,
        hasActiveFilters: !!searchQuery || selectedInitiator !== null,
        resetFilters,
        handlePageChange,
        handleSearchQueryChange,
        handleInitiatorFilterChange,
        handleSortChange,
        handleClearFilters,
    };
};
