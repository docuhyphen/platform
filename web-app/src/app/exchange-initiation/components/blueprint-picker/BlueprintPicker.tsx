import React, {useEffect, useMemo, useState} from 'react';
import {
    Badge,
    Button,
    Checkbox,
    Field,
    Menu,
    MenuItem,
    MenuList,
    MenuPopover,
    MenuTrigger,
    Popover,
    PopoverSurface,
    PopoverTrigger,
    SearchBox,
    Spinner,
    Tab,
    TabList,
    Tag,
    TagGroup,
    Text,
    Tooltip,
} from '@fluentui/react-components';
import {BlueprintDefinitionSummaryDto} from '../../../models/models.tsx';
import {listBlueprints} from '../../../../services/blueprintService.ts';
import {AddIcon, CheckmarkIcon, FilterIcon, SortDownIcon, SortUpIcon} from "../../../components/IconBundles.tsx";
import {useExchangeInitiationStyles} from '../../ExchangeInitiationStyles.tsx';
import ExchangeListPagination from '../../../exchanges/components/exchange-list/exchange-list-pagination/ExchangeListPagination.tsx';

interface BlueprintPickerProps
{
    onSelect: (blueprint: BlueprintDefinitionSummaryDto) => void;
    onCancel: () => void;
}

type PickerTab = 'PERSONAL' | 'ORG' | 'APP';
type SortOrder = 'default' | 'nameAsc' | 'nameDesc';

const PAGE_SIZE = 6;

const tabLabel: Record<PickerTab, string> = {
    PERSONAL: 'My Blueprints',
    ORG: 'Organization',
    APP: 'Platform',
};

const BlueprintPicker: React.FC<BlueprintPickerProps> = ({onSelect, onCancel}) =>
{
    const styles = useExchangeInitiationStyles();

    const [activeTab, setActiveTab] = useState<PickerTab>('PERSONAL');
    const [blueprints, setBlueprints] = useState<BlueprintDefinitionSummaryDto[]>([]);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<string | null>(null);

    const [searchQuery, setSearchQuery] = useState('');
    const [selectedTags, setSelectedTags] = useState<Set<string>>(new Set());
    const [sortOrder, setSortOrder] = useState<SortOrder>('default');
    const [currentPage, setCurrentPage] = useState(0);
    const [filterSearch, setFilterSearch] = useState('');

    useEffect(() =>
    {
        setLoading(true);
        setError(null);
        listBlueprints({scope: activeTab})
            .then(all =>
            {
                const visible = all.filter(bp =>
                    bp.isActive && (bp.scope === 'PERSONAL' || bp.isPublished),
                );
                setBlueprints(visible);
            })
            .catch(() => setError('Failed to load blueprints'))
            .finally(() => setLoading(false));
    }, [activeTab]);

    const availableTags = useMemo(() =>
    {
        const tags = new Set<string>();
        blueprints.forEach(bp => bp.generalTags.forEach(t => tags.add(t)));
        return [...tags].sort();
    }, [blueprints]);

    const filteredTagOptions = useMemo(() =>
    {
        const q = filterSearch.trim().toLowerCase();
        return q ? availableTags.filter(t => t.toLowerCase().includes(q)) : availableTags;
    }, [availableTags, filterSearch]);

    const filteredBlueprints = useMemo(() =>
    {
        const q = searchQuery.trim().toLowerCase();
        let result = blueprints
            .filter(bp => !q || bp.name.toLowerCase().includes(q) || (bp.summary ?? '').toLowerCase().includes(q))
            .filter(bp => selectedTags.size === 0 || bp.generalTags.some(t => selectedTags.has(t)));
        if (sortOrder === 'nameAsc') result = [...result].sort((a, b) => a.name.localeCompare(b.name));
        else if (sortOrder === 'nameDesc') result = [...result].sort((a, b) => b.name.localeCompare(a.name));
        return result;
    }, [blueprints, searchQuery, selectedTags, sortOrder]);

    const totalPages = Math.ceil(filteredBlueprints.length / PAGE_SIZE);
    const visibleBlueprints = filteredBlueprints.slice(currentPage * PAGE_SIZE, (currentPage + 1) * PAGE_SIZE);

    const toggleTag = (tag: string) =>
    {
        setSelectedTags(prev =>
        {
            const next = new Set(prev);
            if (next.has(tag)) next.delete(tag); else next.add(tag);
            return next;
        });
        setCurrentPage(0);
    };

    const resetControls = () =>
    {
        setBlueprints([]);
        setSearchQuery('');
        setSelectedTags(new Set());
        setSortOrder('default');
        setCurrentPage(0);
        setFilterSearch('');
    };

    return (
        <div className={styles.blueprintPickerContainer}>
            <div className={styles.blueprintPickerStickyHeader}>
                <TabList
                    selectedValue={activeTab}
                    onTabSelect={(_, data) =>
                    {
                        setActiveTab(data.value as PickerTab);
                        resetControls();
                    }}
                >
                    <Tab value="PERSONAL">{tabLabel.PERSONAL}</Tab>
                    <Tab value="ORG">{tabLabel.ORG}</Tab>
                    <Tab value="APP">{tabLabel.APP}</Tab>
                </TabList>

            <div className={styles.blueprintPickerControls}>
                <div className={styles.blueprintPickerSearchRow}>
                    <Field style={{flex: 1}}>
                        <SearchBox
                            id="blueprint-picker-search"
                            placeholder="Search blueprints"
                            maxLength={100}
                            value={searchQuery}
                            onChange={(_, data) =>
                            {
                                setSearchQuery(data.value);
                                setCurrentPage(0);
                            }}
                        />
                    </Field>

                    {availableTags.length > 0 && (
                        <Popover
                            positioning="below-end"
                            onOpenChange={(_, {open}) => { if (!open) setFilterSearch(''); }}
                        >
                            <PopoverTrigger disableButtonEnhancement>
                                <Tooltip content="Filter by tag" relationship="description">
                                    <Button
                                        id="blueprint-picker-filter-btn"
                                        icon={<FilterIcon/>}
                                        appearance={selectedTags.size > 0 ? 'primary' : 'subtle'}
                                        shape="circular"
                                    />
                                </Tooltip>
                            </PopoverTrigger>
                            <PopoverSurface className={styles.blueprintPickerFilterPopover}>
                                <SearchBox
                                    placeholder="Search tags"
                                    size="small"
                                    value={filterSearch}
                                    onChange={(_, d) => setFilterSearch(d.value)}
                                />
                                <div className={styles.blueprintPickerFilterPopoverList}>
                                    {filteredTagOptions.map(tag => (
                                        <Checkbox
                                            key={tag}
                                            label={tag}
                                            checked={selectedTags.has(tag)}
                                            onChange={() => toggleTag(tag)}
                                        />
                                    ))}
                                    {filteredTagOptions.length === 0 && (
                                        <Text size={200} style={{padding: '4px 8px', color: 'var(--colorNeutralForeground3)'}}>
                                            No tags found
                                        </Text>
                                    )}
                                </div>
                            </PopoverSurface>
                        </Popover>
                    )}

                    <Menu>
                        <MenuTrigger>
                            <Tooltip
                                content={sortOrder === 'nameAsc' ? 'Name (A-Z)' : sortOrder === 'nameDesc' ? 'Name (Z-A)' : 'Recently updated'}
                                relationship="description"
                            >
                                <Button
                                    id="blueprint-picker-sort-btn"
                                    icon={sortOrder === 'nameDesc' ? <SortDownIcon/> : <SortUpIcon/>}
                                    appearance={sortOrder !== 'default' ? 'primary' : 'subtle'}
                                    shape="circular"
                                />
                            </Tooltip>
                        </MenuTrigger>
                        <MenuPopover>
                            <MenuList>
                                <MenuItem
                                    icon={sortOrder === 'default' ? <CheckmarkIcon/> : undefined}
                                    onClick={() => { setSortOrder('default'); setCurrentPage(0); }}
                                >
                                    Recently updated
                                </MenuItem>
                                <MenuItem
                                    icon={sortOrder === 'nameAsc' ? <CheckmarkIcon/> : undefined}
                                    onClick={() => { setSortOrder('nameAsc'); setCurrentPage(0); }}
                                >
                                    Name (A-Z)
                                </MenuItem>
                                <MenuItem
                                    icon={sortOrder === 'nameDesc' ? <CheckmarkIcon/> : undefined}
                                    onClick={() => { setSortOrder('nameDesc'); setCurrentPage(0); }}
                                >
                                    Name (Z-A)
                                </MenuItem>
                            </MenuList>
                        </MenuPopover>
                    </Menu>
                </div>

                {selectedTags.size > 0 && (
                    <div className={styles.blueprintPickerActiveTagsRow}>
                        <TagGroup
                            onDismiss={(_ev, {value}) =>
                            {
                                setSelectedTags(prev =>
                                {
                                    const next = new Set(prev);
                                    next.delete(value);
                                    return next;
                                });
                                setCurrentPage(0);
                            }}
                        >
                            {[...selectedTags].map(tag => (
                                <Tag key={tag} value={tag} size="small" dismissible>
                                    {tag}
                                </Tag>
                            ))}
                        </TagGroup>
                        <Button
                            size="small"
                            appearance="subtle"
                            onClick={() => { setSelectedTags(new Set()); setCurrentPage(0); }}
                        >
                            Clear all
                        </Button>
                    </div>
                )}
            </div>
            </div>{/* blueprintPickerStickyHeader */}

            {loading && (
                <div className={styles.blueprintPickerSpinnerWrapper}>
                    <Spinner size="small" label="Loading blueprints..."/>
                </div>
            )}

            {!loading && error && (
                <Text className={styles.blueprintPickerErrorText}>{error}</Text>
            )}

            {!loading && !error && blueprints.length === 0 && (
                <Text className={styles.blueprintPickerEmptyText}>
                    No blueprints available in this category.
                </Text>
            )}

            {!loading && !error && blueprints.length > 0 && filteredBlueprints.length === 0 && (
                <Text className={styles.blueprintPickerEmptyText}>
                    No blueprints match your search.
                </Text>
            )}

            {!loading && !error && visibleBlueprints.length > 0 && (
                <div className={styles.blueprintList}>
                    {visibleBlueprints.map(bp => (
                        <div
                            key={bp.id}
                            className={styles.blueprintCard}
                        >
                            <div className={styles.blueprintCardHeader}>
                                <Text weight="semibold" size={400}>{bp.name}</Text>
                                <Button
                                    id={`blueprint-use-btn-${bp.id}`}
                                    appearance="primary"
                                    shape="circular"
                                    size="small"
                                    icon={<AddIcon/>}
                                    onClick={() => onSelect(bp)}
                                >
                                    Use Blueprint
                                </Button>
                            </div>
                            {bp.summary && (
                                <Text size={200} className={styles.blueprintCardSummary}>
                                    {bp.summary}
                                </Text>
                            )}
                            {bp.generalTags.length > 0 && (
                                <div className={styles.blueprintTagRow}>
                                    {bp.generalTags.map(tag => (
                                        <Badge key={tag} appearance="tint" size="small">{tag}</Badge>
                                    ))}
                                </div>
                            )}
                        </div>
                    ))}
                </div>
            )}

            {!loading && !error && totalPages > 1 && (
                <div className={styles.blueprintPickerPaginationRow}>
                    <ExchangeListPagination
                        currentPage={currentPage}
                        totalPages={totalPages}
                        onPageChange={setCurrentPage}
                    />
                </div>
            )}
        </div>
    );
};

export default BlueprintPicker;
