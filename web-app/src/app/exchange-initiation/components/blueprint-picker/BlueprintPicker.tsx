import React from 'react';
import {Field, SearchBox, Tab, TabList} from '@fluentui/react-components';
import {BlueprintDefinitionSummaryDto} from '../../../models/models.tsx';
import {useExchangeInitiationStyles} from '../../ExchangeInitiationStyles.tsx';
import {PickerTab, tabLabel} from './blueprintPickerTypes.ts';
import {useBlueprintPicker} from './useBlueprintPicker.ts';
import BlueprintPickerTagFilter from './blueprint-picker-tag-filter/BlueprintPickerTagFilter.tsx';
import BlueprintPickerSortMenu from './blueprint-picker-sort-menu/BlueprintPickerSortMenu.tsx';
import BlueprintPickerActiveTags from './blueprint-picker-active-tags/BlueprintPickerActiveTags.tsx';
import BlueprintPickerResults from './blueprint-picker-results/BlueprintPickerResults.tsx';

interface BlueprintPickerProps
{
    onSelect: (blueprint: BlueprintDefinitionSummaryDto) => void;
    onCancel: () => void;
}

const BlueprintPicker: React.FC<BlueprintPickerProps> = ({onSelect}) =>
{
    const styles = useExchangeInitiationStyles();
    const {
        hasOrganization,
        activeTab,
        selectTab,
        blueprints,
        loading,
        error,
        searchQuery,
        changeSearchQuery,
        availableTags,
        selectedTags,
        toggleTag,
        dismissTag,
        clearTags,
        sortOrder,
        changeSortOrder,
        filteredBlueprints,
        visibleBlueprints,
        totalPages,
        currentPage,
        setCurrentPage,
    } = useBlueprintPicker();

    return (
        <div id={"blueprint-picker-container"}
             className={styles.blueprintPickerContainer}>
            <div id={"blueprint-picker-sticky-header"}
                 className={styles.blueprintPickerStickyHeader}>
                <TabList
                    id={"blueprint-picker-tab-list"}
                    selectedValue={activeTab}
                    onTabSelect={(_, data) => selectTab(data.value as PickerTab)}
                >
                    <Tab id={"blueprint-picker-personal-tab"}
                         value="PERSONAL">{tabLabel.PERSONAL}</Tab>
                    {hasOrganization && <Tab id={"blueprint-picker-org-tab"}
                                             value="ORG">{tabLabel.ORG}</Tab>}
                    <Tab id={"blueprint-picker-app-tab"}
                         value="APP">{tabLabel.APP}</Tab>
                </TabList>

                <div id={"blueprint-picker-controls"}
                     className={styles.blueprintPickerControls}>
                    <div id={"blueprint-picker-search-row"}
                         className={styles.blueprintPickerSearchRow}>
                        <Field id={"blueprint-picker-search-field"}
                               className={styles.blueprintPickerSearchField}>
                            <SearchBox
                                id="blueprint-picker-search"
                                placeholder="Search blueprints"
                                maxLength={100}
                                value={searchQuery}
                                onChange={(_, data) => changeSearchQuery(data.value)}
                            />
                        </Field>

                        {availableTags.length > 0 && (
                            <BlueprintPickerTagFilter
                                tags={availableTags}
                                selectedTags={selectedTags}
                                onToggleTag={toggleTag}
                            />
                        )}

                        <BlueprintPickerSortMenu
                            sortOrder={sortOrder}
                            onSortOrderChange={changeSortOrder}
                        />
                    </div>

                    {selectedTags.size > 0 && (
                        <BlueprintPickerActiveTags
                            selectedTags={selectedTags}
                            onDismissTag={dismissTag}
                            onClearAll={clearTags}
                        />
                    )}
                </div>
            </div>

            <BlueprintPickerResults
                loading={loading}
                error={error}
                hasBlueprints={blueprints.length > 0}
                hasMatches={filteredBlueprints.length > 0}
                visibleBlueprints={visibleBlueprints}
                totalPages={totalPages}
                currentPage={currentPage}
                onPageChange={setCurrentPage}
                onSelect={onSelect}
            />
        </div>
    );
};

export default BlueprintPicker;
