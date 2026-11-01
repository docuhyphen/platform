import React from 'react';
import {Field, SearchBox} from "@fluentui/react-components";
import {useExchangeListSearchControlsStyles} from "./ExchangeListSearchControlsStyles.tsx";
import ExchangeListFilterMenu from "./exchange-list-filter-menu/ExchangeListFilterMenu.tsx";
import ExchangeListSortMenu from "./exchange-list-sort-menu/ExchangeListSortMenu.tsx";

interface ExchangeListSearchControlsProps
{
    searchQuery: string;
    onSearchQueryChange: (query: string) => void;
    onInitiatorFilterChange: (initiatedBy: boolean | null) => void;
    onSortChange: (field: string, direction: string) => void;
    selectedInitiator: boolean | null;
    sortBy: string;
    sortDirection: string;
    onClearFilters: () => void;
    hasActiveFilters: boolean;
}

const ExchangeListSearchControls: React.FC<ExchangeListSearchControlsProps> = (
    {
        searchQuery,
        onSearchQueryChange,
        onInitiatorFilterChange,
        onSortChange,
        selectedInitiator,
        sortBy,
        sortDirection
    }) =>
{
    const styles = useExchangeListSearchControlsStyles();

    return (
        <section id={"exchange-list-search-controls-section-1"}
                 className={styles.container}>
            <Field id={"exchange-list-search-controls-field-1"}
                   className={styles.searchField}>
                <SearchBox
                    id="exchange-list-search-input"
                    placeholder="Search Exchanges"
                    maxLength={50}
                    value={searchQuery}
                    onChange={(_, data) => onSearchQueryChange(data.value)}
                />
            </Field>
            <ExchangeListFilterMenu
                selectedInitiator={selectedInitiator}
                onInitiatorFilterChange={onInitiatorFilterChange}/>
            <ExchangeListSortMenu
                sortBy={sortBy}
                sortDirection={sortDirection}
                onSortChange={onSortChange}/>
        </section>
    );
};

export default ExchangeListSearchControls;
