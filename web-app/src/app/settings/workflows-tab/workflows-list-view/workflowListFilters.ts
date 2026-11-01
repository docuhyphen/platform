import {WorkflowDefinitionSummaryDto} from "../../../models/models.tsx";
import {formatTriggerName} from "../workflowUtils.ts";
import {WorkflowSortOrder} from "./workflow-list-controls/WorkflowListControls.tsx";
import {WorkflowListTab} from "./workflowListTypes.ts";

export const WORKFLOW_LIST_PAGE_SIZE = 12;

export const WORKFLOW_TAB_LABEL: Record<WorkflowListTab, string> = {
    PERSONAL: 'My Workflows',
    ORG: 'Organization',
    APP: 'Platform',
};

export const availableTagsOf = (definitions: WorkflowDefinitionSummaryDto[]): string[] =>
    [...new Set(definitions.flatMap(definition => definition.generalTags))]
        .sort((left, right) => left.localeCompare(right));

const matchesSearch = (definition: WorkflowDefinitionSummaryDto, normalizedSearch: string): boolean =>
    normalizedSearch.length === 0 || [
        definition.name,
        definition.summary ?? "",
        formatTriggerName(definition.triggerEvent),
        ...definition.generalTags,
    ].some(value => value.toLowerCase().includes(normalizedSearch));

const matchesTags = (definition: WorkflowDefinitionSummaryDto, selectedTags: ReadonlySet<string>): boolean =>
    selectedTags.size === 0 || [...selectedTags].every(tag => definition.generalTags.includes(tag));

const compareDefinitions = (sortOrder: WorkflowSortOrder) =>
    (left: WorkflowDefinitionSummaryDto, right: WorkflowDefinitionSummaryDto): number =>
    {
        if (sortOrder === "nameAsc") return left.name.localeCompare(right.name);
        if (sortOrder === "nameDesc") return right.name.localeCompare(left.name);
        return new Date(right.createdAt).getTime() - new Date(left.createdAt).getTime();
    };

export const filterAndSortWorkflows = (
    definitions: WorkflowDefinitionSummaryDto[],
    searchQuery: string,
    selectedTags: ReadonlySet<string>,
    sortOrder: WorkflowSortOrder,
): WorkflowDefinitionSummaryDto[] =>
{
    const normalizedSearch = searchQuery.trim().toLowerCase();
    return definitions
        .filter(definition => matchesSearch(definition, normalizedSearch) && matchesTags(definition, selectedTags))
        .sort(compareDefinitions(sortOrder));
};
