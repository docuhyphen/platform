import React, {useEffect, useMemo, useState} from 'react';
import {
    Badge,
    Button,
    Menu,
    MenuItem,
    MenuList,
    MenuPopover,
    MenuTrigger,
    Spinner,
    Tab,
    TabList,
    Text,
} from '@fluentui/react-components';
import {MoreVerticalRegular} from '@fluentui/react-icons';
import {
    ActivateIcon, AddIcon,
    CopyIcon,
    DeactivateIcon,
    DeleteIcon,
    EditIcon,
    PublishIcon,
    UnpublishIcon,
} from '../../components/IconBundles';
import {CommunicationSummaryDto, CommunicationScope, ViewMode} from '../../models/models';
import {Capability} from '../../models/models';
import {
    cloneCommunication,
    deleteCommunication,
    listCommunications,
    patchCommunicationPublished,
    patchCommunicationStatus,
} from '../../../services/communicationService';
import CommunicationEditorDialog from './CommunicationEditorDialog';
import {useAuth} from '../../../context/AuthContext';
import {useCommunicationsTabStyles} from './CommunicationsTabStyles';
import TagList from '../../components/TagList.tsx';
import {updateAppUserSettings} from '../../../services/appUserApi';
import CommunicationsPagination from './communications-pagination/CommunicationsPagination';
import CommunicationsToolbar, {
    CommunicationPublicationFilter,
    CommunicationSortOrder,
    CommunicationStatusFilter,
} from './communications-toolbar/CommunicationsToolbar';

type ActiveTab = 'PERSONAL' | 'ORG' | 'PLATFORM';
const PAGE_SIZE = 12;

const tabLabels: Record<ActiveTab, string> = {
    PERSONAL: 'My Communications',
    ORG: 'Organization',
    PLATFORM: 'Platform',
};

const emptyMessage: Record<ActiveTab, string> = {
    PERSONAL: 'No personal communications yet. Create one to get started.',
    ORG: 'No organization communications yet.',
    PLATFORM: 'No platform communications yet.',
};

const CommunicationsTab = () =>
{
    const styles = useCommunicationsTabStyles();
    const {appUser, setAppUser, token, appUserPersonOrganization, hasCapability} = useAuth();

    const hasOrg = !!appUserPersonOrganization?.isActive;
    const canManageOrganization =
        appUserPersonOrganization?.isActive &&
        hasCapability(Capability.ORG_POLICY_MANAGE);
    const [viewMode, setViewMode] = useState<ViewMode>(appUser?.settings?.communicationsView ?? 'cards');

    const [activeTab, setActiveTab] = useState<ActiveTab>('PERSONAL');
    const [communications, setCommunications] = useState<CommunicationSummaryDto[]>([]);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [editorOpen, setEditorOpen] = useState(false);
    const [editingCommunication, setEditingCommunication] = useState<CommunicationSummaryDto | undefined>();
    const [searchQuery, setSearchQuery] = useState('');
    const [selectedTags, setSelectedTags] = useState<Set<string>>(new Set());
    const [statusFilter, setStatusFilter] = useState<CommunicationStatusFilter>('ALL');
    const [publicationFilter, setPublicationFilter] = useState<CommunicationPublicationFilter>('ALL');
    const [sortOrder, setSortOrder] = useState<CommunicationSortOrder>('updatedDesc');
    const [currentPage, setCurrentPage] = useState(0);

    const loadCommunications = () =>
    {
        setLoading(true);
        setError(null);
        listCommunications({scope: activeTab})
            .then(setCommunications)
            .catch(() => setError('Failed to load communications'))
            .finally(() => setLoading(false));
    };

    useEffect(() => { loadCommunications(); }, [activeTab]);

    const availableTags = useMemo(() =>
        [...new Set(communications.flatMap(communication => communication.generalTags))]
            .sort((left, right) => left.localeCompare(right)),
    [communications]);

    const visibleCommunications = useMemo(() =>
    {
        const query = searchQuery.trim().toLowerCase();
        return communications
            .filter(communication =>
            {
                const matchesSearch = query.length === 0 || [
                    communication.name,
                    communication.summary ?? '',
                    communication.subject,
                    ...communication.generalTags,
                ].some(value => value.toLowerCase().includes(query));
                const matchesTags = selectedTags.size === 0 ||
                    [...selectedTags].every(tag => communication.generalTags.includes(tag));
                const matchesStatus = statusFilter === 'ALL' ||
                    (statusFilter === 'ACTIVE' && communication.isActive) ||
                    (statusFilter === 'INACTIVE' && !communication.isActive);
                const matchesPublication = publicationFilter === 'ALL' ||
                    (publicationFilter === 'PUBLISHED' && communication.isPublished) ||
                    (publicationFilter === 'DRAFT' && !communication.isPublished);
                return matchesSearch && matchesTags && matchesStatus && matchesPublication;
            })
            .sort((left, right) =>
            {
                if (sortOrder === 'nameAsc') return left.name.localeCompare(right.name);
                if (sortOrder === 'nameDesc') return right.name.localeCompare(left.name);
                if (sortOrder === 'subjectAsc') return left.subject.localeCompare(right.subject);
                return new Date(right.updatedAt).getTime() - new Date(left.updatedAt).getTime();
            });
    }, [communications, publicationFilter, searchQuery, selectedTags, sortOrder, statusFilter]);

    const totalPages = Math.ceil(visibleCommunications.length / PAGE_SIZE);
    const pagedCommunications = visibleCommunications.slice(
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

    const handleViewModeChange = async (mode: ViewMode) => {
        setViewMode(mode);
        if (!appUser?.settings) return;
        const updated = {...appUser.settings, communicationsView: mode};
        try { await updateAppUserSettings(updated, token); if (appUser) setAppUser({...appUser, settings: updated}); }
        catch { /* non-critical */ }
    };

    const handlePublish = async (t: CommunicationSummaryDto) =>
    {
        await patchCommunicationPublished(t.id, {isPublished: !t.isPublished});
        loadCommunications();
    };

    const handleActivate = async (t: CommunicationSummaryDto) =>
    {
        await patchCommunicationStatus(t.id, {isActive: !t.isActive});
        loadCommunications();
    };

    const handleDuplicate = async (t: CommunicationSummaryDto) =>
    {
        await cloneCommunication(t.id);
        loadCommunications();
    };

    const handleDelete = async (t: CommunicationSummaryDto) =>
    {
        await deleteCommunication(t.id);
        loadCommunications();
    };

    const openEdit = (t: CommunicationSummaryDto) =>
    {
        setEditingCommunication(t);
        setEditorOpen(true);
    };

    const openCreate = () =>
    {
        setEditingCommunication(undefined);
        setEditorOpen(true);
    };

    const canCreate =
        activeTab === 'PERSONAL' ||
        (activeTab === 'ORG' && !!canManageOrganization);

    const canManageItem = (t: CommunicationSummaryDto) =>
        t.scope === 'PERSONAL' ||
        (t.scope === 'ORG' && !!canManageOrganization);

    return (
        <>
            <div className={styles.outerContainer}>
                <div className={styles.headerRow}>
                    <TabList
                        id={"communications-tabs"}
                        selectedValue={activeTab}
                        onTabSelect={(_, d) =>
                        {
                            setActiveTab(d.value as ActiveTab);
                            setCommunications([]);
                            setSearchQuery('');
                            setSelectedTags(new Set());
                            setStatusFilter('ALL');
                            setPublicationFilter('ALL');
                            setSortOrder('updatedDesc');
                            setCurrentPage(0);
                        }}
                    >
                        <Tab
                            id={"communications-tab-personal"}
                            value="PERSONAL"
                        >
                            {tabLabels.PERSONAL}
                        </Tab>
                        {hasOrg && (
                            <Tab
                                id={"communications-tab-organization"}
                                value="ORG"
                            >
                                {tabLabels.ORG}
                            </Tab>
                        )}
                        <Tab
                            id={"communications-tab-platform"}
                            value="PLATFORM"
                        >
                            {tabLabels.PLATFORM}
                        </Tab>
                    </TabList>

                    {canCreate && (
                        <Button
                            id={"button-create-communication"}
                            appearance="subtle"
                            shape={"circular"}
                            icon={<AddIcon/>}
                            onClick={openCreate}
                        >
                            Create Communication
                        </Button>
                    )}
                </div>

                <CommunicationsToolbar
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
                        setSelectedTags(previous =>
                        {
                            const next = new Set(previous);
                            if (next.has(tag)) next.delete(tag);
                            else next.add(tag);
                            return next;
                        });
                        setCurrentPage(0);
                    }}
                    statusFilter={statusFilter}
                    onStatusFilterChange={(value) =>
                    {
                        setStatusFilter(value);
                        setCurrentPage(0);
                    }}
                    publicationFilter={publicationFilter}
                    onPublicationFilterChange={(value) =>
                    {
                        setPublicationFilter(value);
                        setCurrentPage(0);
                    }}
                    showPublicationFilter={activeTab !== 'PERSONAL'}
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
                {loading && (
                    <Spinner
                        size="small"
                        label={"Loading communications..."}
                    />
                )}
                {!loading && error && (
                    <Text className={styles.errorText}>{error}</Text>
                )}
                {!loading && !error && communications.length === 0 && (
                    <Text className={styles.emptyText}>{emptyMessage[activeTab]}</Text>
                )}
                {!loading && !error && communications.length > 0 && visibleCommunications.length === 0 && (
                    <Text className={styles.emptyText}>No communications match your search and filters.</Text>
                )}
                {!loading && !error && pagedCommunications.length > 0 && viewMode === 'cards' && (
                    <div className={styles.cardGrid}>
                        {pagedCommunications.map(t => (
                            <div
                                key={t.id}
                                className={styles.commCard}
                            >
                                <div className={styles.commCardContent}>
                                    <Text weight="semibold" size={400}>{t.name}</Text>
                                    {t.summary && (
                                        <Text size={200} className={styles.summaryText}>
                                            {t.summary}
                                        </Text>
                                    )}
                                    <Text
                                        size={200}
                                        className={styles.subjectText}
                                    >
                                        {t.subject}
                                    </Text>
                                    <div className={styles.badgeRow}>
                                        {activeTab !== 'PERSONAL' && (
                                            <Badge
                                                appearance="tint"
                                                color={t.isPublished ? 'success' : 'warning'}
                                                size="small"
                                            >
                                                {t.isPublished ? 'Published' : 'Draft'}
                                            </Badge>
                                        )}
                                        <Badge
                                            appearance="tint"
                                            color={t.isActive ? 'success' : 'warning'}
                                            size="small"
                                        >
                                            {t.isActive ? 'Active' : 'Inactive'}
                                        </Badge>
                                        <TagList tags={t.generalTags}/>
                                    </div>
                                </div>
                                {canManageItem(t) && (
                                    <Menu>
                                        <MenuTrigger disableButtonEnhancement>
                                            <Button
                                                id={`button-communication-more-${t.id}`}
                                                size="small"
                                                appearance="subtle"
                                                shape={"circular"}
                                                icon={<MoreVerticalRegular/>}
                                                aria-label="More actions"
                                            />
                                        </MenuTrigger>
                                        <MenuPopover>
                                            <MenuList>
                                                <MenuItem icon={<EditIcon/>} onClick={() => openEdit(t)}>Edit</MenuItem>
                                                {activeTab !== 'PERSONAL' && (
                                                    <MenuItem
                                                        icon={t.isPublished ? <UnpublishIcon/> : <PublishIcon/>}
                                                        onClick={() => handlePublish(t)}
                                                    >
                                                        {t.isPublished ? 'Unpublish' : 'Publish'}
                                                    </MenuItem>
                                                )}
                                                <MenuItem
                                                    icon={t.isActive ? <DeactivateIcon/> : <ActivateIcon/>}
                                                    onClick={() => handleActivate(t)}
                                                >
                                                    {t.isActive ? 'Deactivate' : 'Activate'}
                                                </MenuItem>
                                                <MenuItem icon={<CopyIcon/>} onClick={() => handleDuplicate(t)}>Duplicate</MenuItem>
                                                <MenuItem icon={<DeleteIcon/>} onClick={() => handleDelete(t)}>Delete</MenuItem>
                                            </MenuList>
                                        </MenuPopover>
                                    </Menu>
                                )}
                            </div>
                        ))}
                    </div>
                )}
                {!loading && !error && pagedCommunications.length > 0 && viewMode === 'table' && (
                    <table className={styles.table}>
                        <thead>
                            <tr>
                                <th className={styles.th}>Name</th>
                                <th className={styles.th}>Subject</th>
                                <th className={styles.th}>Tags</th>
                                {activeTab !== 'PERSONAL' && <th className={styles.th}>Published</th>}
                                <th className={styles.th}>Active</th>
                                <th className={styles.th}/>
                            </tr>
                        </thead>
                        <tbody>
                            {pagedCommunications.map(t => (
                                <tr key={t.id} className={styles.tr}>
                                    <td className={styles.td}>
                                        <Text weight="semibold">{t.name}</Text>
                                        {t.summary && <Text size={200} className={styles.summaryText} block>{t.summary}</Text>}
                                    </td>
                                    <td className={styles.td}><Text size={200}>{t.subject}</Text></td>
                                    <td className={styles.td}>
                                        <TagList tags={t.generalTags}/>
                                    </td>
                                    {activeTab !== 'PERSONAL' && (
                                        <td className={styles.td}><Text size={200}>{t.isPublished ? 'Published' : 'Draft'}</Text></td>
                                    )}
                                    <td className={styles.td}>
                                        <Badge appearance="tint" color={t.isActive ? 'success' : 'warning'} size="small">
                                            {t.isActive ? 'Active' : 'Inactive'}
                                        </Badge>
                                    </td>
                                    <td className={styles.td}>
                                        {canManageItem(t) && (
                                            <Menu>
                                                <MenuTrigger disableButtonEnhancement>
                                                    <Button
                                                        id={`button-communication-table-more-${t.id}`}
                                                        size="small"
                                                        appearance="subtle"
                                                        shape={"circular"}
                                                        icon={<MoreVerticalRegular/>}
                                                        aria-label="More actions"
                                                    />
                                                </MenuTrigger>
                                                <MenuPopover>
                                                    <MenuList>
                                                        <MenuItem icon={<EditIcon/>} onClick={() => openEdit(t)}>Edit</MenuItem>
                                                        {activeTab !== 'PERSONAL' && (
                                                            <MenuItem icon={t.isPublished ? <UnpublishIcon/> : <PublishIcon/>} onClick={() => handlePublish(t)}>
                                                                {t.isPublished ? 'Unpublish' : 'Publish'}
                                                            </MenuItem>
                                                        )}
                                                        <MenuItem icon={t.isActive ? <DeactivateIcon/> : <ActivateIcon/>} onClick={() => handleActivate(t)}>
                                                            {t.isActive ? 'Deactivate' : 'Activate'}
                                                        </MenuItem>
                                                        <MenuItem icon={<CopyIcon/>} onClick={() => handleDuplicate(t)}>Duplicate</MenuItem>
                                                        <MenuItem icon={<DeleteIcon/>} onClick={() => handleDelete(t)}>Delete</MenuItem>
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
                {!loading && !error && visibleCommunications.length > 0 && (
                    <CommunicationsPagination
                        currentPage={currentPage}
                        totalPages={Math.max(totalPages, 1)}
                        totalItems={visibleCommunications.length}
                        pageSize={PAGE_SIZE}
                        onPageChange={setCurrentPage}
                    />
                )}
            </div>

            <CommunicationEditorDialog
                open={editorOpen}
                onClose={() => setEditorOpen(false)}
                onSaved={() => { setEditorOpen(false); loadCommunications(); }}
                communication={editingCommunication}
                scope={activeTab as CommunicationScope}
            />
        </>
    );
};

export default CommunicationsTab;
