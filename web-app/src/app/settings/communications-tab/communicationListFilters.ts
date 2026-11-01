import {CommunicationSummaryDto} from '../../models/models';
import {
    CommunicationPublicationFilter,
    CommunicationSortOrder,
    CommunicationStatusFilter,
} from './communications-toolbar/CommunicationsToolbarTypes';
import {CommunicationListTab} from './communicationListTypes';

export const COMMUNICATIONS_PAGE_SIZE = 12;

export const communicationTabLabels: Record<CommunicationListTab, string> = {
    PERSONAL: 'My Communications',
    ORG: 'Organization',
    PLATFORM: 'Platform',
};

export const communicationEmptyMessages: Record<CommunicationListTab, string> = {
    PERSONAL: 'No personal communications yet. Create one to get started.',
    ORG: 'No organization communications yet.',
    PLATFORM: 'No platform communications yet.',
};

export interface CommunicationListFilterValues
{
    searchQuery: string;
    selectedTags: ReadonlySet<string>;
    statusFilter: CommunicationStatusFilter;
    publicationFilter: CommunicationPublicationFilter;
    sortOrder: CommunicationSortOrder;
}

export const collectCommunicationTags = (communications: CommunicationSummaryDto[]): string[] =>
    [...new Set(communications.flatMap(communication => communication.generalTags))]
        .sort((left, right) => left.localeCompare(right));

const matchesCommunicationFilters = (
    communication: CommunicationSummaryDto,
    query: string,
    filters: CommunicationListFilterValues,
): boolean =>
{
    const matchesSearch = query.length === 0 || [
        communication.name,
        communication.summary ?? '',
        communication.subject,
        ...communication.generalTags,
    ].some(value => value.toLowerCase().includes(query));
    const matchesTags = filters.selectedTags.size === 0 ||
        [...filters.selectedTags].every(tag => communication.generalTags.includes(tag));
    const matchesStatus = filters.statusFilter === 'ALL' ||
        (filters.statusFilter === 'ACTIVE' && communication.isActive) ||
        (filters.statusFilter === 'INACTIVE' && !communication.isActive);
    const matchesPublication = filters.publicationFilter === 'ALL' ||
        (filters.publicationFilter === 'PUBLISHED' && communication.isPublished) ||
        (filters.publicationFilter === 'DRAFT' && !communication.isPublished);
    return matchesSearch && matchesTags && matchesStatus && matchesPublication;
};

const compareCommunications = (
    left: CommunicationSummaryDto,
    right: CommunicationSummaryDto,
    sortOrder: CommunicationSortOrder,
): number =>
{
    if (sortOrder === 'nameAsc') return left.name.localeCompare(right.name);
    if (sortOrder === 'nameDesc') return right.name.localeCompare(left.name);
    if (sortOrder === 'subjectAsc') return left.subject.localeCompare(right.subject);
    return new Date(right.updatedAt).getTime() - new Date(left.updatedAt).getTime();
};

export const filterAndSortCommunications = (
    communications: CommunicationSummaryDto[],
    filters: CommunicationListFilterValues,
): CommunicationSummaryDto[] =>
{
    const query = filters.searchQuery.trim().toLowerCase();
    return communications
        .filter(communication => matchesCommunicationFilters(communication, query, filters))
        .sort((left, right) => compareCommunications(left, right, filters.sortOrder));
};
