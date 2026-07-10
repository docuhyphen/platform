import {useCallback, useEffect, useState} from "react";
import TagList from "../../../components/TagList.tsx";
import {updateAppUserSettings} from "../../../../services/appUserApi";
import {useAuth} from "../../../../context/AuthContext";
import {
    Badge,
    Button,
    Dialog,
    DialogActions,
    DialogBody,
    DialogContent,
    DialogSurface,
    DialogTitle,
    Input,
    Label,
    Menu,
    MenuItem,
    MenuList,
    MenuPopover,
    MenuTrigger,
    MessageBar,
    MessageBarBody,
    Spinner,
    Tab,
    TabList,
    Text,
} from "@fluentui/react-components";
import {MoreVerticalRegular} from "@fluentui/react-icons";
import {ViewMode, WorkflowDefinitionSummaryDto} from "../../../models/models.tsx";
import {
    cloneWorkflowDefinition,
    listWorkflowDefinitions,
    patchWorkflowDefinitionPublished,
    patchWorkflowDefinitionStatus,
} from "../../../../services/workflowService.ts";
import {useWorkflowsListViewStyles} from "./WorkflowsListViewStyles.tsx";
import {ActivateIcon, AddIcon, CopyIcon, DeactivateIcon, DeleteIcon, EditIcon, PublishIcon, UnpublishIcon} from "../../../components/IconBundles.tsx";
import {formatTriggerName} from "../workflowUtils.ts";
import WorkflowDeleteDialog from "../WorkflowDeleteDialog.tsx";
import WorkflowListControls, {
    WorkflowSortOrder,
} from "./workflow-list-controls/WorkflowListControls.tsx";
import WorkflowsPagination from "../workflows-pagination/WorkflowsPagination.tsx";

export type WorkflowListTab = 'PERSONAL' | 'ORG' | 'APP';
const PAGE_SIZE = 12;

const TAB_LABEL: Record<WorkflowListTab, string> = {
    PERSONAL: 'My Workflows',
    ORG: 'Organization',
    APP: 'Platform',
};

interface Props
{
    activeTab: WorkflowListTab;
    onActiveTabChange: (tab: WorkflowListTab) => void;
    onEdit: (definition: WorkflowDefinitionSummaryDto) => void;
    onNew: (scope: 'PERSONAL' | 'ORG') => void;
}

const statusColor = (isActive: boolean): "success" | "warning" => (isActive ? "success" : "warning");


interface WorkflowCardProps
{
    def: WorkflowDefinitionSummaryDto;
    isPersonal: boolean;
    onEdit: () => void;
    onToggleActive: () => void;
    onTogglePublished: () => void;
    onClone: () => void;
    onDelete: () => void;
}

const WorkflowCard = ({def, isPersonal, onEdit, onToggleActive, onTogglePublished, onClone, onDelete}: WorkflowCardProps) =>
{
    const styles = useWorkflowsListViewStyles();
    return (
        <div className={styles.row}>
            {/* Row 1: name + trigger */}
            <div className={styles.topRow}>
                <Text weight="semibold">{def.name}</Text>
                <Text size={200} className={styles.triggerText}>{formatTriggerName(def.triggerEvent)}</Text>
            </div>

            {/* Row 2: description + badges + actions */}
            <div className={styles.middleRow}>
                <Text size={200} className={styles.description}>{def.summary ?? ''}</Text>
                <div className={styles.middleActions}>
                    {!isPersonal && (
                        <Badge
                            color={def.isPublished ? "brand" : "subtle"}
                            appearance={def.isPublished ? "filled" : "outline"}
                            size="small"
                        >
                            {def.isPublished ? "Published" : "Draft"}
                        </Badge>
                    )}
                    <Badge color={statusColor(def.isActive)} appearance="tint" size="small">
                        {def.isActive ? "Active" : "Inactive"}
                    </Badge>
                    <Menu>
                        <MenuTrigger disableButtonEnhancement>
                            <Button
                            id={`workflow-card-more-actions-btn-${def.id}`}
                            size="small"
                            appearance="subtle"
                            shape={"circular"}
                            icon={<MoreVerticalRegular/>}
                            aria-label="More actions"
                        />
                        </MenuTrigger>
                        <MenuPopover>
                            <MenuList>
                                <MenuItem icon={<EditIcon/>} onClick={onEdit}>Edit</MenuItem>
                                {!isPersonal && (
                                    <MenuItem
                                        icon={def.isPublished ? <UnpublishIcon/> : <PublishIcon/>}
                                        onClick={onTogglePublished}
                                    >
                                        {def.isPublished ? "Unpublish" : "Publish"}
                                    </MenuItem>
                                )}
                                <MenuItem
                                    icon={def.isActive ? <DeactivateIcon/> : <ActivateIcon/>}
                                    onClick={onToggleActive}
                                >
                                    {def.isActive ? "Deactivate" : "Activate"}
                                </MenuItem>
                                <MenuItem icon={<CopyIcon/>} onClick={onClone}>Duplicate</MenuItem>
                                <MenuItem icon={<DeleteIcon/>} onClick={onDelete}>Delete</MenuItem>
                            </MenuList>
                        </MenuPopover>
                    </Menu>
                </div>
            </div>

            {/* Row 3: tags */}
            <TagList tags={def.generalTags}/>
        </div>
    );
};


const WorkflowsListView = ({activeTab, onActiveTabChange, onEdit, onNew}: Props) =>
{
    const styles = useWorkflowsListViewStyles();
    const {appUser, setAppUser, token, appUserPersonOrganization} = useAuth();
    const hasOrg = !!appUserPersonOrganization?.isActive;
    const [viewMode, setViewMode] = useState<ViewMode>(appUser?.settings?.workflowsView ?? 'cards');
    const [definitions, setDefinitions] = useState<WorkflowDefinitionSummaryDto[]>([]);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [deletingDef, setDeletingDef] = useState<WorkflowDefinitionSummaryDto | null>(null);
    const [cloningDef, setCloningDef] = useState<WorkflowDefinitionSummaryDto | null>(null);
    const [cloneNameInput, setCloneNameInput] = useState("");
    const [cloning, setCloning] = useState(false);
    const [searchQuery, setSearchQuery] = useState("");
    const [selectedTags, setSelectedTags] = useState<Set<string>>(new Set());
    const [sortOrder, setSortOrder] = useState<WorkflowSortOrder>("newest");
    const [currentPage, setCurrentPage] = useState(0);

    const handleViewModeChange = async (mode: ViewMode) => {
        setViewMode(mode);
        if (!appUser?.settings) return;
        const updated = {...appUser.settings, workflowsView: mode};
        try { await updateAppUserSettings(updated, token); if (appUser) setAppUser({...appUser, settings: updated}); }
        catch { /* non-critical */ }
    };

    const load = useCallback(async (tab: WorkflowListTab) =>
    {
        setLoading(true);
        setError(null);
        try
        {
            setDefinitions(await listWorkflowDefinitions({scope: tab}));
        }
        catch (e: unknown)
        {
            setError(typeof e === "string" ? e : "Failed to load workflows");
        }
        finally
        {
            setLoading(false);
        }
    }, []);

    useEffect(() =>
    {
        load(activeTab);
    }, [load, activeTab]);

    const selectTab = (tab: WorkflowListTab) =>
    {
        onActiveTabChange(tab);
        setSearchQuery("");
        setSelectedTags(new Set());
        setSortOrder("newest");
        setCurrentPage(0);
    };

    const toggleTag = (tag: string) =>
    {
        setSelectedTags(previous =>
        {
            const next = new Set(previous);
            if (next.has(tag)) next.delete(tag);
            else next.add(tag);
            return next;
        });
    };

    const availableTags = [...new Set(definitions.flatMap(definition => definition.generalTags))]
        .sort((left, right) => left.localeCompare(right));
    const normalizedSearch = searchQuery.trim().toLowerCase();
    const visibleDefinitions = definitions
        .filter(definition =>
        {
            const matchesSearch = normalizedSearch.length === 0 || [
                definition.name,
                definition.summary ?? "",
                formatTriggerName(definition.triggerEvent),
                ...definition.generalTags,
            ].some(value => value.toLowerCase().includes(normalizedSearch));
            const matchesTags = selectedTags.size === 0 ||
                [...selectedTags].every(tag => definition.generalTags.includes(tag));
            return matchesSearch && matchesTags;
        })
        .sort((left, right) =>
        {
            if (sortOrder === "nameAsc") return left.name.localeCompare(right.name);
            if (sortOrder === "nameDesc") return right.name.localeCompare(left.name);
            return new Date(right.createdAt).getTime() - new Date(left.createdAt).getTime();
        });
    const totalPages = Math.ceil(visibleDefinitions.length / PAGE_SIZE);
    const pagedDefinitions = visibleDefinitions.slice(
        currentPage * PAGE_SIZE,
        (currentPage + 1) * PAGE_SIZE,
    );

    useEffect(() =>
    {
        if (currentPage > 0 && currentPage >= Math.max(totalPages, 1))
        {
            setCurrentPage(Math.max(totalPages - 1, 0));
        }
    }, [currentPage, totalPages]);

    const toggleActive = async (def: WorkflowDefinitionSummaryDto) =>
    {
        try
        {
            await patchWorkflowDefinitionStatus(def.id, {isActive: !def.isActive});
            await load(activeTab);
        }
        catch { /* ignore */ }
    };

    const togglePublished = async (def: WorkflowDefinitionSummaryDto) =>
    {
        try
        {
            await patchWorkflowDefinitionPublished(def.id, {isPublished: !def.isPublished});
            await load(activeTab);
        }
        catch { /* ignore */ }
    };

    const openCloneDialog = (def: WorkflowDefinitionSummaryDto) =>
    {
        setCloningDef(def);
        setCloneNameInput(`${def.name} (copy)`);
    };

    const confirmClone = async () =>
    {
        if (!cloningDef) return;
        setCloning(true);
        try
        {
            await cloneWorkflowDefinition(cloningDef.id, {newName: cloneNameInput.trim() || undefined});
            setCloningDef(null);
            selectTab('PERSONAL');
        }
        catch { /* ignore */ }
        finally
        {
            setCloning(false);
        }
    };

    const renderPlatformCard = (def: WorkflowDefinitionSummaryDto) => (
        <div key={def.id} className={styles.templateCard}>
            <div className={styles.templateCardInfo}>
                <Text weight="semibold">{def.name}</Text>
                {def.summary && <Text size={200} block>{def.summary}</Text>}
                <Text
                    size={200}
                    block
                    className={styles.platformCardTriggerText}
                >
                    Runs when: {formatTriggerName(def.triggerEvent)}
                </Text>
                <TagList tags={def.generalTags}/>
            </div>
            <Button
                id={`workflows-list-add-platform-btn-${def.id}`}
                size="small"
                appearance="outline"
                shape={"circular"}
                onClick={() => openCloneDialog(def)}
            >
                Add to my workflows
            </Button>
        </div>
    );

    return (
        <>
            <div className={styles.container}>
                <div className={styles.outerWrapper}>
                    <TabList
                        selectedValue={activeTab}
                        onTabSelect={(_, d) => selectTab(d.value as WorkflowListTab)}
                    >
                        <Tab value="PERSONAL">{TAB_LABEL.PERSONAL}</Tab>
                        {hasOrg && <Tab value="ORG">{TAB_LABEL.ORG}</Tab>}
                        <Tab value="APP">{TAB_LABEL.APP}</Tab>
                    </TabList>

                    {activeTab !== 'APP' && (
                        <Button
                            id="workflows-list-create-btn"
                            appearance="subtle"
                            icon={<AddIcon/>}
                            onClick={() => onNew(activeTab)}
                            shape="circular"
                        >
                            Create Workflow
                        </Button>
                    )}
                </div>

                <WorkflowListControls
                    searchQuery={searchQuery}
                    onSearchChange={(value) =>
                    {
                        setSearchQuery(value);
                        setCurrentPage(0);
                    }}
                    availableTags={availableTags}
                    selectedTags={selectedTags}
                    onTagToggle={(tag) =>
                    {
                        toggleTag(tag);
                        setCurrentPage(0);
                    }}
                    onClearTags={() =>
                    {
                        setSelectedTags(new Set());
                        setCurrentPage(0);
                    }}
                    sortOrder={sortOrder}
                    onSortOrderChange={(value) =>
                    {
                        setSortOrder(value);
                        setCurrentPage(0);
                    }}
                    viewMode={viewMode}
                    onViewModeChange={handleViewModeChange}
                />

                <div className={styles.scrollableContent}>
                {error && (
                    <MessageBar intent="error" className={styles.errorBar}>
                        <MessageBarBody>{error}</MessageBarBody>
                    </MessageBar>
                )}

                {loading && <Spinner size="small" label="Loading workflows..."/>}

                {!loading && !error && definitions.length === 0 && (
                    <div className={styles.emptyState}>
                        <Text>
                            {activeTab === 'PERSONAL' && "No personal workflows yet. Create one to get started."}
                            {activeTab === 'ORG' && "No organisation workflows yet. Create one to get started."}
                            {activeTab === 'APP' && "No platform templates available."}
                        </Text>
                    </div>
                )}

                {!loading && !error && definitions.length > 0 && visibleDefinitions.length === 0 && (
                    <div className={styles.emptyState}>
                        <Text>No workflows match your search and filters.</Text>
                    </div>
                )}

                {!loading && !error && pagedDefinitions.length > 0 && viewMode === 'cards' && (
                    <div className={styles.cardGrid}>
                        {pagedDefinitions.map(def =>
                            activeTab === 'APP'
                                ? renderPlatformCard(def)
                                : (
                                    <WorkflowCard
                                        key={def.id}
                                        def={def}
                                        isPersonal={activeTab === 'PERSONAL'}
                                        onEdit={() => onEdit(def)}
                                        onToggleActive={() => toggleActive(def)}
                                        onTogglePublished={() => togglePublished(def)}
                                        onClone={() => openCloneDialog(def)}
                                        onDelete={() => setDeletingDef(def)}
                                    />
                                ),
                        )}
                    </div>
                )}

                {!loading && !error && pagedDefinitions.length > 0 && viewMode === 'table' && (
                    <table className={styles.table}>
                        <thead>
                            <tr>
                                <th className={styles.th}>Name</th>
                                <th className={styles.th}>Trigger</th>
                                <th className={styles.th}>Tags</th>
                                {activeTab !== 'PERSONAL' && <th className={styles.th}>Published</th>}
                                <th className={styles.th}>Active</th>
                                <th className={styles.th}/>
                            </tr>
                        </thead>
                        <tbody>
                            {pagedDefinitions.map(def => (
                                <tr key={def.id} className={styles.tr}>
                                    <td className={styles.td}>
                                        <Text weight="semibold">{def.name}</Text>
                                        {def.summary && <Text size={200} block>{def.summary}</Text>}
                                    </td>
                                    <td className={styles.td}><Text size={200}>{formatTriggerName(def.triggerEvent)}</Text></td>
                                    <td className={styles.td}>
                                        <TagList tags={def.generalTags}/>
                                    </td>
                                    {activeTab !== 'PERSONAL' && (
                                        <td className={styles.td}><Text size={200}>{def.isPublished ? 'Published' : 'Draft'}</Text></td>
                                    )}
                                    <td className={styles.td}>
                                        <Badge appearance="tint" color={def.isActive ? 'success' : 'warning'} size="small">
                                            {def.isActive ? 'Active' : 'Inactive'}
                                        </Badge>
                                    </td>
                                    <td className={styles.td}>
                                        {activeTab === 'APP' ? (
                                            <Button
                                                id={`workflows-table-add-platform-btn-${def.id}`}
                                                size="small"
                                                appearance="outline"
                                                shape="circular"
                                                onClick={() => openCloneDialog(def)}
                                            >
                                                Add to my workflows
                                            </Button>
                                        ) : (
                                            <Menu>
                                            <MenuTrigger disableButtonEnhancement>
                                                <Button size="small" appearance="subtle" shape="circular" icon={<MoreVerticalRegular/>} aria-label="More actions"/>
                                            </MenuTrigger>
                                            <MenuPopover>
                                                <MenuList>
                                                    <MenuItem icon={<EditIcon/>} onClick={() => onEdit(def)}>Edit</MenuItem>
                                                    {activeTab !== 'PERSONAL' && (
                                                        <MenuItem icon={def.isPublished ? <UnpublishIcon/> : <PublishIcon/>} onClick={() => togglePublished(def)}>
                                                            {def.isPublished ? 'Unpublish' : 'Publish'}
                                                        </MenuItem>
                                                    )}
                                                    <MenuItem icon={def.isActive ? <DeactivateIcon/> : <ActivateIcon/>} onClick={() => toggleActive(def)}>
                                                        {def.isActive ? 'Deactivate' : 'Activate'}
                                                    </MenuItem>
                                                    <MenuItem icon={<CopyIcon/>} onClick={() => openCloneDialog(def)}>Duplicate</MenuItem>
                                                    <MenuItem icon={<DeleteIcon/>} onClick={() => setDeletingDef(def)}>Delete</MenuItem>
                                                </MenuList>
                                            </MenuPopover>
                                            </Menu>
                                        )}
                                    </td>
                                </tr>
                            ))}
                        </tbody>
                    </table>
                )}
                </div>
                {!loading && !error && visibleDefinitions.length > 0 && (
                    <WorkflowsPagination
                        currentPage={currentPage}
                        totalPages={Math.max(totalPages, 1)}
                        firstItem={currentPage * PAGE_SIZE + 1}
                        lastItem={Math.min((currentPage + 1) * PAGE_SIZE, visibleDefinitions.length)}
                        totalItems={visibleDefinitions.length}
                        itemLabel={activeTab === "APP" ? "templates" : "workflows"}
                        onPageChange={setCurrentPage}
                    />
                )}
            </div>

            {deletingDef && (
                <WorkflowDeleteDialog
                    isOpen={true}
                    definition={deletingDef}
                    onDismiss={() => setDeletingDef(null)}
                    onDeleted={() =>
                    {
                        setDeletingDef(null);
                        load(activeTab);
                    }}
                />
            )}

            <Dialog open={!!cloningDef} onOpenChange={(_, d) => { if (!d.open) setCloningDef(null); }}>
                <DialogSurface>
                    <DialogBody>
                        <DialogTitle>Clone workflow</DialogTitle>
                        <DialogContent>
                            <Label
                                htmlFor="clone-name-input"
                                className={styles.cloneNameLabel}
                            >
                                Name
                            </Label>
                            <Input
                                id="clone-name-input"
                                value={cloneNameInput}
                                onChange={(_, d) => setCloneNameInput(d.value)}
                                onKeyDown={e => { if (e.key === "Enter") confirmClone(); }}
                                className={styles.cloneNameInput}
                                autoFocus
                            />
                        </DialogContent>
                        <DialogActions>
                            <Button
                                id="workflows-list-clone-cancel-btn"
                                appearance="secondary"
                                shape="circular"
                                onClick={() => setCloningDef(null)}
                            >
                                Cancel
                            </Button>
                            <Button
                                id="workflows-list-clone-confirm-btn"
                                appearance="primary"
                                shape="circular"
                                onClick={confirmClone}
                                disabled={cloning || !cloneNameInput.trim()}
                            >
                                {cloning ? "Cloning..." : "Clone"}
                            </Button>
                        </DialogActions>
                    </DialogBody>
                </DialogSurface>
            </Dialog>
        </>
    );
};

export default WorkflowsListView;
