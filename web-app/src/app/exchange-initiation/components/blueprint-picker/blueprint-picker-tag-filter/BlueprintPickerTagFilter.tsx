import React, {useMemo, useState} from 'react';
import {
    Button,
    Checkbox,
    Popover,
    PopoverSurface,
    PopoverTrigger,
    SearchBox,
    Text,
    Tooltip,
} from '@fluentui/react-components';
import {FilterIcon} from "../../../../components/IconBundles.tsx";
import {useExchangeInitiationStyles} from '../../../ExchangeInitiationStyles.tsx';

interface BlueprintPickerTagFilterProps
{
    tags: string[];
    selectedTags: Set<string>;
    onToggleTag: (tag: string) => void;
}

const BlueprintPickerTagFilter: React.FC<BlueprintPickerTagFilterProps> = ({tags, selectedTags, onToggleTag}) =>
{
    const styles = useExchangeInitiationStyles();
    const [filterSearch, setFilterSearch] = useState('');

    const filteredTagOptions = useMemo(() =>
    {
        const q = filterSearch.trim().toLowerCase();
        return q ? tags.filter(t => t.toLowerCase().includes(q)) : tags;
    }, [tags, filterSearch]);

    return (
        <Popover
            positioning="below-end"
            onOpenChange={(_, {open}) => { if (!open) setFilterSearch(''); }}
        >
            <PopoverTrigger disableButtonEnhancement>
                <Tooltip content="Filter by tag"
                         relationship="description">
                    <Button
                        id="blueprint-picker-filter-btn"
                        icon={<FilterIcon/>}
                        appearance={selectedTags.size > 0 ? 'primary' : 'subtle'}
                        shape="circular"
                    />
                </Tooltip>
            </PopoverTrigger>
            <PopoverSurface id={"blueprint-picker-filter-popover"}
                            className={styles.blueprintPickerFilterPopover}>
                <SearchBox
                    id={"blueprint-picker-filter-popover-search-box"}
                    placeholder="Search tags"
                    size="small"
                    value={filterSearch}
                    onChange={(_, d) => setFilterSearch(d.value)}
                />
                <div id={"blueprint-picker-filter-popover-list"}
                     className={styles.blueprintPickerFilterPopoverList}>
                    {filteredTagOptions.map(tag => (
                        <Checkbox
                            id={`blueprint-picker-checkbox-${tag}`}
                            key={tag}
                            label={tag}
                            checked={selectedTags.has(tag)}
                            onChange={() => onToggleTag(tag)}
                        />
                    ))}
                    {filteredTagOptions.length === 0 && (
                        <Text id={"blueprint-picker-filter-empty-text"}
                              size={200}
                              className={styles.blueprintPickerFilterEmptyText}>
                            No tags found
                        </Text>
                    )}
                </div>
            </PopoverSurface>
        </Popover>
    );
};

export default BlueprintPickerTagFilter;
