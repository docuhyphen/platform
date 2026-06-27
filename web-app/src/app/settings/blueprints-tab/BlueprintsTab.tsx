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
import {MoreVerticalRegular} from '@fluentui/react-icons';
import {
    ActivateIcon, AddIcon,
    BlueprintAddIcon,
    CheckmarkIcon,
    CopyIcon,
    DeactivateIcon,
    DeleteIcon,
    EditIcon,
    FilterIcon,
    PublishIcon,
    SortDownIcon,
    SortUpIcon,
    UnpublishIcon,
} from '../../components/IconBundles.tsx';
import {useTemplatesTabStyles} from './BlueprintsTabStyles.tsx';
import {AppUserRole, BlueprintDefinitionSummaryDto, BlueprintScope} from '../../models/models.tsx';
import {
    cloneBlueprint,
    deleteBlueprint,
    listBlueprints,
    patchBlueprintPublished,
    patchBlueprintStatus,
} from '../../../services/blueprintService.ts';
import BlueprintEditorDialog from './BlueprintEditorDialog.tsx';
import {useAuth} from '../../../context/AuthContext.tsx';
import {useGlobalStyles} from "../../../GlobalStyles.tsx";
import ExchangeListPagination from '../../exchanges/components/exchange-list/exchange-list-pagination/ExchangeListPagination.tsx';

type ActiveTab = 'PERSONAL' | 'ORG' | 'APP';

const tabLabels: Record<ActiveTab, string> = {
    PERSONAL: 'My Blueprints',
    ORG: 'Organization',
    APP: 'Platform',
};

const emptyMessage: Record<ActiveTab, string> = {
    PERSONAL: 'No personal blueprints yet. Create one to get started.',
    ORG: 'No organization blueprints yet.',
    APP: 'No platform blueprints yet.',
};

const createLabel: Record<ActiveTab, string> = {
    PERSONAL: 'Create ',
    ORG: 'Create',
    APP: 'Create',
};

const PAGE_SIZE = 12;

type SortOrder = 'default' | 'nameAsc' | 'nameDesc';

const BlueprintsTab = () =>
{
    const globalStyles = useGlobalStyles();
    const styles = useTemplatesTabStyles();
    const {appUser, appUserPersonOrganization} = useAuth();

    const roleValue = `${appUser?.role ?? ''}`;
    const canManageOrganization =
        appUserPersonOrganization?.isActive &&
        (roleValue === AppUserRole.ORG_ADMIN || roleValue === 'APP_ADMIN');
    const isAppAdmin = roleValue === 'APP_ADMIN';

    const [activeTab, setActiveTab] = useState<ActiveTab>('PERSONAL');
    const [blueprints, setBlueprints] = useState<BlueprintDefinitionSummaryDto[]>([]);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [editorOpen, setEditorOpen] = useState(false);
    const [editingBlueprint, setEditingBlueprint] = useState<BlueprintDefinitionSummaryDto | undefined>();
    const [searchQuery, setSearchQuery] = useState('');
    const [selectedTags, setSelectedTags] = useState<Set<string>>(new Set());
    const [sortOrder, setSortOrder] = useState<SortOrder>('default');
    const [currentPage, setCurrentPage] = useState(0);
    const [filterSearch, setFilterSearch] = useState('');

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

    const loadBlueprints = () =>
    {
        setLoading(true);
        setError(null);
        listBlueprints({scope: activeTab})
            .then(setBlueprints)
            .catch(() => setError('Failed to load blueprints'))
            .finally(() => setLoading(false));
    };

    useEffect(() => { loadBlueprints(); }, [activeTab]);

    const handlePublish = async (bp: BlueprintDefinitionSummaryDto) =>
    {
        await patchBlueprintPublished(bp.id, {isPublished: !bp.isPublished});
        loadBlueprints();
    };

    const handleActivate = async (bp: BlueprintDefinitionSummaryDto) =>
    {
        await patchBlueprintStatus(bp.id, {isActive: !bp.isActive});
        loadBlueprints();
    };

    const handleDuplicate = async (bp: BlueprintDefinitionSummaryDto) =>
    {
        await cloneBlueprint(bp.id, {newName: `${bp.name} (copy)`});
        loadBlueprints();
    };

    const handleCloneToPersonal = async (bp: BlueprintDefinitionSummaryDto) =>
    {
        await cloneBlueprint(bp.id, {newName: `${bp.name} (copy)`, targetScope: 'PERSONAL'});
        loadBlueprints();
    };

    const handleCloneToOrg = async (bp: BlueprintDefinitionSummaryDto) =>
    {
        await cloneBlueprint(bp.id, {newName: `${bp.name} (copy)`, targetScope: 'ORG'});
        loadBlueprints();
    };

    const handleDelete = async (bp: BlueprintDefinitionSummaryDto) =>
    {
        await deleteBlueprint(bp.id);
        loadBlueprints();
    };

    const openEdit = (bp: BlueprintDefinitionSummaryDto) =>
    {
        setEditingBlueprint(bp);
        setEditorOpen(true);
    };

    const openCreate = () =>
    {
        setEditingBlueprint(undefined);
        setEditorOpen(true);
    };

    const canCreate =
        activeTab === 'PERSONAL' ||
        (activeTab === 'ORG' && canManageOrganization) ||
        (activeTab === 'APP' && isAppAdmin);

    const canManageItem = (bp: BlueprintDefinitionSummaryDto) =>
        bp.scope === 'PERSONAL' ||
        (bp.scope === 'ORG' && !!canManageOrganization) ||
        (bp.scope === 'APP' && isAppAdmin);

    return (
        <>
            <div className={styles.outerContainer}>
                <div className={styles.stickyBlock}>
                    <div className={styles.headerRow}>
                        <TabList
                            selectedValue={activeTab}
                            onTabSelect={(_, d) =>
                            {
                                setActiveTab(d.value as ActiveTab);
                                setBlueprints([]);
                                setSearchQuery('');
                                setSelectedTags(new Set());
                                setSortOrder('default');
                                setCurrentPage(0);
                                setFilterSearch('');
                            }}
                        >
                            <Tab value="PERSONAL">{tabLabels.PERSONAL}</Tab>
                            <Tab value="ORG">{tabLabels.ORG}</Tab>
                            <Tab value="APP">{tabLabels.APP}</Tab>
                        </TabList>

                        {canCreate && (
                            <Button
                                id={"button-create-blueprint"}
                                className={globalStyles.buttonWithLoading}
                                icon={<AddIcon/>}
                                appearance="subtle"
                                shape={"circular"}
                                onClick={openCreate}
                            >
                                Create Blueprint
                            </Button>
                        )}
                    </div>

                    <div className={styles.searchRow}>
                        <Field style={{flex: 1}}>
                            <SearchBox
                                id="blueprint-search-input"
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
                            <Popover positioning="below-end" onOpenChange={(_, {open}) => { if (!open) setFilterSearch(''); }}>
                                <PopoverTrigger disableButtonEnhancement>
                                    <Tooltip content="Filter by tag" relationship="description">
                                        <Button
                                            id="blueprint-filter-btn"
                                            icon={<FilterIcon/>}
                                            appearance={selectedTags.size > 0 ? 'primary' : 'subtle'}
                                            shape="circular"
                                        />
                                    </Tooltip>
                                </PopoverTrigger>
                                <PopoverSurface className={styles.filterPopover}>
                                    <SearchBox
                                        placeholder="Search tags"
                                        size="small"
                                        value={filterSearch}
                                        onChange={(_, d) => setFilterSearch(d.value)}
                                    />
                                    <div className={styles.filterPopoverList}>
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
                                        id="blueprint-sort-btn"
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
                        <div className={styles.activeTagsRow}>
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

                {loading && <Spinner size="small" label="Loading blueprints…"/>}
                {!loading && error && (
                    <Text className={styles.errorText}>{error}</Text>
                )}
                {!loading && !error && blueprints.length === 0 && (
                    <Text className={styles.emptyText}>{emptyMessage[activeTab]}</Text>
                )}
                {!loading && !error && blueprints.length > 0 && filteredBlueprints.length === 0 && (
                    <Text className={styles.emptyText}>No blueprints match your search.</Text>
                )}
                {!loading && !error && visibleBlueprints.length > 0 && (
                    <div className={styles.cardGrid}>
                        {visibleBlueprints.map(bp => (
                            <div
                                key={bp.id}
                                className={styles.blueprintCard}
                            >
                                <div className={styles.blueprintCardContent}>
                                    <Text weight="semibold" size={400}>{bp.name}</Text>
                                    {bp.summary && (
                                        <Text size={200} className={styles.summaryText}>
                                            {bp.summary}
                                        </Text>
                                    )}
                                    <div className={styles.badgeRow}>
                                        {activeTab !== 'PERSONAL' && (
                                            <Badge
                                                appearance="tint"
                                                color={bp.isPublished ? 'success' : 'warning'}
                                                size="small"
                                            >
                                                {bp.isPublished ? 'Published' : 'Draft'}
                                            </Badge>
                                        )}
                                        <Badge
                                            appearance="tint"
                                            color={bp.isActive ? 'success' : 'warning'}
                                            size="small"
                                        >
                                            {bp.isActive ? 'Active' : 'Inactive'}
                                        </Badge>
                                        {bp.generalTags.map(tag => (
                                            <Badge key={tag} appearance="tint" size="small">{tag}</Badge>
                                        ))}
                                    </div>
                                </div>
                                {activeTab === 'APP' && !canManageItem(bp) && (
                                    <Menu>
                                        <MenuTrigger disableButtonEnhancement>
                                            <Button
                                                id={`button-blueprint-more-${bp.id}`}
                                                size="small"
                                                appearance="subtle"
                                                shape="circular"
                                                icon={<MoreVerticalRegular/>}
                                                aria-label="More actions"
                                            />
                                        </MenuTrigger>
                                        <MenuPopover>
                                            <MenuList>
                                                <MenuItem icon={<CopyIcon/>} onClick={() => handleCloneToPersonal(bp)}>
                                                    Clone to My Collection
                                                </MenuItem>
                                                {canManageOrganization && (
                                                    <MenuItem icon={<CopyIcon/>} onClick={() => handleCloneToOrg(bp)}>
                                                        Clone to Organization
                                                    </MenuItem>
                                                )}
                                            </MenuList>
                                        </MenuPopover>
                                    </Menu>
                                )}
                                {canManageItem(bp) && (
                                    <Menu>
                                        <MenuTrigger disableButtonEnhancement>
                                            <Button
                                                id={`button-blueprint-more-${bp.id}`}
                                                size="small"
                                                appearance="subtle"
                                                shape={"circular"}
                                                icon={<MoreVerticalRegular/>}
                                                aria-label="More actions"
                                            />
                                        </MenuTrigger>
                                        <MenuPopover>
                                            <MenuList>
                                                <MenuItem icon={<EditIcon/>} onClick={() => openEdit(bp)}>Edit</MenuItem>
                                                {activeTab !== 'PERSONAL' && (
                                                    <MenuItem
                                                        icon={bp.isPublished ? <UnpublishIcon/> : <PublishIcon/>}
                                                        onClick={() => handlePublish(bp)}
                                                    >
                                                        {bp.isPublished ? 'Unpublish' : 'Publish'}
                                                    </MenuItem>
                                                )}
                                                <MenuItem
                                                    icon={bp.isActive ? <DeactivateIcon/> : <ActivateIcon/>}
                                                    onClick={() => handleActivate(bp)}
                                                >
                                                    {bp.isActive ? 'Deactivate' : 'Activate'}
                                                </MenuItem>
                                                {activeTab === 'APP' ? (
                                                    <>
                                                        <MenuItem icon={<CopyIcon/>} onClick={() => handleCloneToPersonal(bp)}>
                                                            Clone to My Collection
                                                        </MenuItem>
                                                        {canManageOrganization && (
                                                            <MenuItem icon={<CopyIcon/>} onClick={() => handleCloneToOrg(bp)}>
                                                                Clone to Organization
                                                            </MenuItem>
                                                        )}
                                                    </>
                                                ) : (
                                                    <MenuItem icon={<CopyIcon/>} onClick={() => handleDuplicate(bp)}>Duplicate</MenuItem>
                                                )}
                                                <MenuItem icon={<DeleteIcon/>} onClick={() => handleDelete(bp)}>Delete</MenuItem>
                                            </MenuList>
                                        </MenuPopover>
                                    </Menu>
                                )}
                            </div>
                        ))}
                    </div>
                )}
                {!loading && !error && totalPages > 1 && (
                    <div className={styles.paginationRow}>
                        <ExchangeListPagination
                            currentPage={currentPage}
                            totalPages={totalPages}
                            onPageChange={setCurrentPage}
                        />
                    </div>
                )}
            </div>

            <BlueprintEditorDialog
                open={editorOpen}
                onClose={() => setEditorOpen(false)}
                onSaved={() => { setEditorOpen(false); loadBlueprints(); }}
                blueprint={editingBlueprint}
                scope={activeTab as BlueprintScope}
            />
        </>
    );
};

export default BlueprintsTab;
