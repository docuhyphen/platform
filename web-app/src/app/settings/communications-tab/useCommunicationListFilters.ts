import {useEffect, useMemo, useState} from 'react';
import {CommunicationSummaryDto} from '../../models/models';
import {
    CommunicationPublicationFilter,
    CommunicationSortOrder,
    CommunicationStatusFilter,
} from './communications-toolbar/CommunicationsToolbarTypes';
import {
    COMMUNICATIONS_PAGE_SIZE,
    collectCommunicationTags,
    filterAndSortCommunications,
} from './communicationListFilters';

export const useCommunicationListFilters = (communications: CommunicationSummaryDto[]) =>
{
    const [searchQuery, setSearchQuery] = useState('');
    const [selectedTags, setSelectedTags] = useState<Set<string>>(new Set());
    const [statusFilter, setStatusFilter] = useState<CommunicationStatusFilter>('ALL');
    const [publicationFilter, setPublicationFilter] = useState<CommunicationPublicationFilter>('ALL');
    const [sortOrder, setSortOrder] = useState<CommunicationSortOrder>('updatedDesc');
    const [currentPage, setCurrentPage] = useState(0);

    const availableTags = useMemo(() => collectCommunicationTags(communications), [communications]);

    const visibleCommunications = useMemo(() =>
        filterAndSortCommunications(communications, {
            searchQuery,
            selectedTags,
            statusFilter,
            publicationFilter,
            sortOrder,
        }),
    [communications, publicationFilter, searchQuery, selectedTags, sortOrder, statusFilter]);

    const totalPages = Math.ceil(visibleCommunications.length / COMMUNICATIONS_PAGE_SIZE);
    const pagedCommunications = visibleCommunications.slice(
        currentPage * COMMUNICATIONS_PAGE_SIZE,
        (currentPage + 1) * COMMUNICATIONS_PAGE_SIZE,
    );

    useEffect(() =>
    {
        if (currentPage > 0 && currentPage >= Math.max(totalPages, 1))
        {
            setCurrentPage(Math.max(totalPages - 1, 0));
        }
    }, [currentPage, totalPages]);

    const resetFilters = () =>
    {
        setSearchQuery('');
        setSelectedTags(new Set());
        setStatusFilter('ALL');
        setPublicationFilter('ALL');
        setSortOrder('updatedDesc');
        setCurrentPage(0);
    };

    const changeSearch = (value: string) =>
    {
        setSearchQuery(value);
        setCurrentPage(0);
    };

    const toggleTag = (tag: string) =>
    {
        setSelectedTags(previous =>
        {
            const next = new Set(previous);
            if (next.has(tag)) next.delete(tag);
            else next.add(tag);
            return next;
        });
        setCurrentPage(0);
    };

    const changeStatusFilter = (value: CommunicationStatusFilter) =>
    {
        setStatusFilter(value);
        setCurrentPage(0);
    };

    const changePublicationFilter = (value: CommunicationPublicationFilter) =>
    {
        setPublicationFilter(value);
        setCurrentPage(0);
    };

    const changeSortOrder = (value: CommunicationSortOrder) =>
    {
        setSortOrder(value);
        setCurrentPage(0);
    };

    return {
        searchQuery,
        selectedTags,
        statusFilter,
        publicationFilter,
        sortOrder,
        currentPage,
        setCurrentPage,
        availableTags,
        visibleCommunications,
        pagedCommunications,
        totalPages,
        resetFilters,
        changeSearch,
        toggleTag,
        changeStatusFilter,
        changePublicationFilter,
        changeSortOrder,
    };
};
