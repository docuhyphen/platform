export interface ExchangeListSortOption
{
    id: string;
    field: string;
    label: string;
}

export const EXCHANGE_LIST_SORT_OPTIONS: ExchangeListSortOption[] = [
    {id: "exchange-list-sort-created-date", field: "createdDate", label: "Date created"},
    {id: "exchange-list-sort-exchange-name", field: "name", label: "Exchange name"},
    {id: "exchange-list-sort-last-activity", field: "lastActivity", label: "Last activity"},
];

export const getSortDescription = (field: string, direction: string): string =>
{
    switch (field)
    {
        case "createdDate":
            return direction === "DESC" ? "Created Date (Newest First)" : "Created Date (Oldest First)";
        case "name":
            return direction === "DESC" ? "Exchange Name (Z-A)" : "Exchange Name (A-Z)";
        case "lastActivity":
            return direction === "DESC" ? "Last Activity (Recent First)" : "Last Activity (Oldest First)";
        default:
            return `${field} (${direction})`;
    }
};

export const getNextSortDirection = (field: string, sortBy: string, sortDirection: string): string =>
    sortBy === field && sortDirection === "DESC" ? "ASC" : "DESC";
