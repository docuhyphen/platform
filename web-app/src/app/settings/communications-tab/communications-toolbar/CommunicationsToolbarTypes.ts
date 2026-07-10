import {ViewMode} from "../../../models/models";

export type CommunicationSortOrder = "updatedDesc" | "nameAsc" | "nameDesc" | "subjectAsc";
export type CommunicationStatusFilter = "ALL" | "ACTIVE" | "INACTIVE";
export type CommunicationPublicationFilter = "ALL" | "PUBLISHED" | "DRAFT";

export interface CommunicationsToolbarProps
{
    searchQuery: string;
    onSearchChange: (value: string) => void;
    availableTags: string[];
    selectedTags: ReadonlySet<string>;
    onTagToggle: (tag: string) => void;
    statusFilter: CommunicationStatusFilter;
    onStatusFilterChange: (value: CommunicationStatusFilter) => void;
    publicationFilter: CommunicationPublicationFilter;
    onPublicationFilterChange: (value: CommunicationPublicationFilter) => void;
    showPublicationFilter: boolean;
    sortOrder: CommunicationSortOrder;
    onSortOrderChange: (value: CommunicationSortOrder) => void;
    viewMode: ViewMode;
    onViewModeChange: (value: ViewMode) => void;
}
