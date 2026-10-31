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
import ExchangeListPagination
    from '../../../exchanges/components/exchange-list/exchange-list-pagination/ExchangeListPagination.tsx';
import {useAuth} from '../../../../context/AuthContext.tsx';
import BlueprintInformationRequestBadge
    from '../blueprint-information-request-badge/BlueprintInformationRequestBadge.tsx';

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

const BlueprintPicker: React.FC<BlueprintPickerProps> = ({onSelect}) =>
{
    const styles = useExchangeInitiationStyles();
    const {appUserPersonOrganization} = useAuth();
    const hasOrganization = !!appUserPersonOrganization?.isActive;

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
        if (!hasOrganization && activeTab === 'ORG')
        {
            setActiveTab('PERSONAL');
            resetControls();
        }
    }, [activeTab, hasOrganization]);

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
        <div id={"blueprint-picker-container"}
             className={styles.blueprintPickerContainer}>
            <div id={"blueprint-picker-sticky-header"}
                 className={styles.blueprintPickerStickyHeader}>
                <TabList
                    id={"blueprint-picker-tab-list"}
                    selectedValue={activeTab}
                    onTabSelect={(_, data) =>
                    {
                        setActiveTab(data.value as PickerTab);
                        resetControls();
                    }}
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
                                            onChange={() => toggleTag(tag)}
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
                        <MenuPopover id={"blueprint-picker-search-row-menu-popover"}>
                            <MenuList id={"blueprint-picker-search-row-menu-list"}>
                                <MenuItem
                                    id={"blueprint-picker-recently-updated-menu-item"}
                                    icon={sortOrder === 'default' ? <CheckmarkIcon/> : undefined}
                                    onClick={() => { setSortOrder('default'); setCurrentPage(0); }}
                                >
                                    Recently updated
                                </MenuItem>
                                <MenuItem
                                    id={"blueprint-picker-name-a-z-menu-item"}
                                    icon={sortOrder === 'nameAsc' ? <CheckmarkIcon/> : undefined}
                                    onClick={() => { setSortOrder('nameAsc'); setCurrentPage(0); }}
                                >
                                    Name (A-Z)
                                </MenuItem>
                                <MenuItem
                                    id={"blueprint-picker-name-z-a-menu-item"}
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
                    <div id={"blueprint-picker-active-tags-row"}
                         className={styles.blueprintPickerActiveTagsRow}>
                        <TagGroup
                            id={"blueprint-picker-map-tag-group"}
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
                                <Tag id={`blueprint-picker-tag-${tag}`}
                                     key={tag}
                                     value={tag}
                                     size="small"
                                     dismissible>
                                    {tag}
                                </Tag>
                            ))}
                        </TagGroup>
                        <Button
                            id="blueprint-picker-clear-tags-btn"
                            size="small"
                            appearance="subtle"
                            shape="circular"
                            onClick={() => { setSelectedTags(new Set()); setCurrentPage(0); }}
                        >
                            Clear all
                        </Button>
                    </div>
                )}
            </div>
            </div>

            {loading && (
                <div id={"blueprint-picker-spinner-wrapper"}
                     className={styles.blueprintPickerSpinnerWrapper}>
                    <Spinner id={"blueprint-picker-loading-blueprints-spinner"}
                             size="small"
                             label="Loading blueprints..."/>
                </div>
            )}

            {!loading && error && (
                <Text id={"blueprint-picker-error-text"}
                      className={styles.blueprintPickerErrorText}>{error}</Text>
            )}

            {!loading && !error && blueprints.length === 0 && (
                <Text id={"blueprint-picker-empty-text"}
                      className={styles.blueprintPickerEmptyText}>
                    No blueprints available in this category.
                </Text>
            )}

            {!loading && !error && blueprints.length > 0 && filteredBlueprints.length === 0 && (
                <Text id={"blueprint-picker-empty-text-2"}
                      className={styles.blueprintPickerEmptyText}>
                    No blueprints match your search.
                </Text>
            )}

            {!loading && !error && visibleBlueprints.length > 0 && (
                <div id={"blueprint-picker-blueprint-list"}
                     className={styles.blueprintList}>
                    {visibleBlueprints.map(bp => (
                        <div
                            id={`blueprint-picker-blueprint-card-${bp.id}`}
                            key={bp.id}
                            className={styles.blueprintCard}
                        >
                            <div id={`blueprint-picker-blueprint-card-header-${bp.id}`}
                                 className={styles.blueprintCardHeader}>
                                <Text id={`blueprint-picker-name-text-${bp.id}`}
                                      weight="semibold"
                                      size={400}>{bp.name}</Text>
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
                                <Text id={`blueprint-picker-blueprint-card-summary-${bp.id}`}
                                      size={200}
                                      className={styles.blueprintCardSummary}>
                                    {bp.summary}
                                </Text>
                            )}
                            <BlueprintInformationRequestBadge blueprint={bp}/>
                            {bp.generalTags.length > 0 && (
                                <div id={`blueprint-picker-blueprint-tag-row-${bp.id}`}
                                     className={styles.blueprintTagRow}>
                                    {bp.generalTags.map(tag => (
                                        <Badge id={`blueprint-picker-tag-badge-${bp.id}-${tag}`}
                                               key={tag}
                                               appearance="tint"
                                               size="small">{tag}</Badge>
                                    ))}
                                </div>
                            )}
                        </div>
                    ))}
                </div>
            )}

            {!loading && !error && totalPages > 1 && (
                <div id={"blueprint-picker-pagination-row"}
                     className={styles.blueprintPickerPaginationRow}>
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
