import {DocumentLibraryEntrySummaryDto} from '../../models/models.tsx';
import {DocumentLibraryListTab, DocumentLibrarySortOrder} from './documentLibraryListTypes.ts';

export const DOCUMENT_LIBRARY_PAGE_SIZE = 12;

export const documentLibraryTabLabels: Record<DocumentLibraryListTab, string> = {
    PERSONAL: 'My Documents',
    ORG: 'Organization',
    APP: 'Platform',
};

export const documentLibraryEmptyMessages: Record<DocumentLibraryListTab, string> = {
    PERSONAL: 'No personal documents yet. Create one to get started.',
    ORG: 'No organization documents yet.',
    APP: 'No platform documents yet.',
};

export const documentLibrarySortOrders: DocumentLibrarySortOrder[] = ['default', 'nameAsc', 'nameDesc'];

export const documentLibrarySortLabels: Record<DocumentLibrarySortOrder, string> = {
    default: 'Recently updated',
    nameAsc: 'Name (A-Z)',
    nameDesc: 'Name (Z-A)',
};

export const collectDocumentLibraryTags = (entries: DocumentLibraryEntrySummaryDto[]): string[] =>
{
    const tags = new Set<string>();
    entries.forEach(e => e.generalTags.forEach(t => tags.add(t)));
    return [...tags].sort();
};

export const filterDocumentLibraryTagOptions = (availableTags: string[], filterSearch: string): string[] =>
{
    const q = filterSearch.trim().toLowerCase();
    return q ? availableTags.filter(t => t.toLowerCase().includes(q)) : availableTags;
};

export const filterAndSortDocumentLibraryEntries = (
    entries: DocumentLibraryEntrySummaryDto[],
    searchQuery: string,
    selectedTags: Set<string>,
    sortOrder: DocumentLibrarySortOrder,
): DocumentLibraryEntrySummaryDto[] =>
{
    const q = searchQuery.trim().toLowerCase();
    const result = entries
        .filter(e => !q || e.title.toLowerCase().includes(q) || (e.description ?? '').toLowerCase().includes(q))
        .filter(e => selectedTags.size === 0 || e.generalTags.some(t => selectedTags.has(t)));
    if (sortOrder === 'nameAsc') return [...result].sort((a, b) => a.title.localeCompare(b.title));
    if (sortOrder === 'nameDesc') return [...result].sort((a, b) => b.title.localeCompare(a.title));
    return result;
};
