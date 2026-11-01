import {BlueprintDefinitionSummaryDto} from '../../models/models.tsx';
import {BlueprintListTab, BlueprintSortOrder} from './blueprintListTypes.ts';

export const BLUEPRINTS_PAGE_SIZE = 12;

export const blueprintTabLabels: Record<BlueprintListTab, string> = {
    PERSONAL: 'My Blueprints',
    ORG: 'Organization',
    APP: 'Platform',
};

export const blueprintEmptyMessages: Record<BlueprintListTab, string> = {
    PERSONAL: 'No personal blueprints yet. Create one to get started.',
    ORG: 'No organization blueprints yet.',
    APP: 'No platform blueprints yet.',
};

export const blueprintSortOrderLabels: Record<BlueprintSortOrder, string> = {
    default: 'Recently updated',
    nameAsc: 'Name (A-Z)',
    nameDesc: 'Name (Z-A)',
};

export const collectBlueprintTags = (blueprints: BlueprintDefinitionSummaryDto[]): string[] =>
{
    const tags = new Set<string>();
    blueprints.forEach(bp => bp.generalTags.forEach(t => tags.add(t)));
    return [...tags].sort();
};

export const filterTagOptions = (availableTags: string[], filterSearch: string): string[] =>
{
    const q = filterSearch.trim().toLowerCase();
    return q ? availableTags.filter(t => t.toLowerCase().includes(q)) : availableTags;
};

export const filterAndSortBlueprints = (
    blueprints: BlueprintDefinitionSummaryDto[],
    searchQuery: string,
    selectedTags: Set<string>,
    sortOrder: BlueprintSortOrder,
): BlueprintDefinitionSummaryDto[] =>
{
    const q = searchQuery.trim().toLowerCase();
    let result = blueprints
        .filter(bp => !q || bp.name.toLowerCase().includes(q) || (bp.summary ?? '').toLowerCase().includes(q))
        .filter(bp => selectedTags.size === 0 || bp.generalTags.some(t => selectedTags.has(t)));
    if (sortOrder === 'nameAsc') result = [...result].sort((a, b) => a.name.localeCompare(b.name));
    else if (sortOrder === 'nameDesc') result = [...result].sort((a, b) => b.name.localeCompare(a.name));
    return result;
};
