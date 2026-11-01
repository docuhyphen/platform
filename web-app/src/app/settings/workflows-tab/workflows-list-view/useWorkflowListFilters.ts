import {useEffect, useState} from "react";
import {WorkflowDefinitionSummaryDto} from "../../../models/models.tsx";
import {WorkflowSortOrder} from "./workflow-list-controls/WorkflowListControls.tsx";
import {availableTagsOf, filterAndSortWorkflows, WORKFLOW_LIST_PAGE_SIZE} from "./workflowListFilters.ts";

export const useWorkflowListFilters = (definitions: WorkflowDefinitionSummaryDto[]) =>
{
    const [searchQuery, setSearchQueryState] = useState("");
    const [selectedTags, setSelectedTags] = useState<Set<string>>(new Set());
    const [sortOrder, setSortOrderState] = useState<WorkflowSortOrder>("newest");
    const [currentPage, setCurrentPage] = useState(0);

    const availableTags = availableTagsOf(definitions);
    const visibleDefinitions = filterAndSortWorkflows(definitions, searchQuery, selectedTags, sortOrder);
    const totalPages = Math.ceil(visibleDefinitions.length / WORKFLOW_LIST_PAGE_SIZE);
    const pagedDefinitions = visibleDefinitions.slice(
        currentPage * WORKFLOW_LIST_PAGE_SIZE,
        (currentPage + 1) * WORKFLOW_LIST_PAGE_SIZE,
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
        setSearchQueryState("");
        setSelectedTags(new Set());
        setSortOrderState("newest");
        setCurrentPage(0);
    };

    const setSearchQuery = (value: string) =>
    {
        setSearchQueryState(value);
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

    const clearTags = () =>
    {
        setSelectedTags(new Set());
        setCurrentPage(0);
    };

    const setSortOrder = (value: WorkflowSortOrder) =>
    {
        setSortOrderState(value);
        setCurrentPage(0);
    };

    return {
        searchQuery,
        selectedTags,
        sortOrder,
        currentPage,
        availableTags,
        visibleDefinitions,
        pagedDefinitions,
        totalPages,
        setSearchQuery,
        toggleTag,
        clearTags,
        setSortOrder,
        setCurrentPage,
        resetFilters,
    };
};
