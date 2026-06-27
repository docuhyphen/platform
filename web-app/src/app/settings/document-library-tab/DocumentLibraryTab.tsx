import React, {useEffect, useMemo, useState} from 'react';
import {
    Button,
    Checkbox,
    Dialog,
    DialogActions,
    DialogBody,
    DialogContent,
    DialogSurface,
    DialogTitle,
    DialogTrigger,
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
import {
    AddIcon,
    CheckmarkIcon,
    DocumentAddIcon,
    FilterIcon,
    SortDownIcon,
    SortUpIcon,
} from '../../components/IconBundles.tsx';
import {useDocumentsTabStyles} from './DocumentLibraryTabStyles.tsx';
import {AppUserRole, DocumentLibraryEntrySummaryDto, DocumentLibraryScope} from '../../models/models.tsx';
import {
    cloneDocumentLibraryEntry,
    deleteDocumentLibraryEntry,
    downloadDocumentLibraryFile,
    listDocumentLibraryEntries,
    patchDocumentLibraryEntryPublished,
    patchDocumentLibraryEntryStatus,
} from '../../../services/documentLibraryService.ts';
import {useAuth} from '../../../context/AuthContext.tsx';
import DocumentLibraryEntryCard from './DocumentLibraryEntryCard.tsx';
import DocumentLibraryEditorDialog from './DocumentLibraryEditorDialog.tsx';
import DocumentLibraryUploadDialog from './DocumentLibraryUploadDialog.tsx';
import ExchangeListPagination from '../../exchanges/components/exchange-list/exchange-list-pagination/ExchangeListPagination.tsx';

const PAGE_SIZE = 12;

type ActiveTab = 'PERSONAL' | 'ORG' | 'APP';
type SortOrder = 'default' | 'nameAsc' | 'nameDesc';

const tabLabels: Record<ActiveTab, string> = {
    PERSONAL: 'My Documents',
    ORG: 'Organization',
    APP: 'Platform',
};

const emptyMessage: Record<ActiveTab, string> = {
    PERSONAL: 'No personal documents yet. Create one to get started.',
    ORG: 'No organization documents yet.',
    APP: 'No platform documents yet.',
};

const DocumentLibraryTab = () =>
{
    const styles = useDocumentsTabStyles();
    const {appUser, appUserPersonOrganization} = useAuth();

    const roleValue = `${appUser?.role ?? ''}`;
    const canManageOrganization =
        appUserPersonOrganization?.isActive &&
        (roleValue === AppUserRole.ORG_ADMIN || roleValue === 'APP_ADMIN');
    const isAppAdmin = roleValue === 'APP_ADMIN';

    const [activeTab, setActiveTab] = useState<ActiveTab>('PERSONAL');
    const [entries, setEntries] = useState<DocumentLibraryEntrySummaryDto[]>([]);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [searchQuery, setSearchQuery] = useState('');
    const [selectedTags, setSelectedTags] = useState<Set<string>>(new Set());
    const [sortOrder, setSortOrder] = useState<SortOrder>('default');
    const [currentPage, setCurrentPage] = useState(0);
    const [filterSearch, setFilterSearch] = useState('');

    const availableTags = useMemo(() =>
    {
        const tags = new Set<string>();
        entries.forEach(e => e.generalTags.forEach(t => tags.add(t)));
        return [...tags].sort();
    }, [entries]);

    const filteredTagOptions = useMemo(() =>
    {
        const q = filterSearch.trim().toLowerCase();
        return q ? availableTags.filter(t => t.toLowerCase().includes(q)) : availableTags;
    }, [availableTags, filterSearch]);

    const filteredEntries = useMemo(() =>
    {
        const q = searchQuery.trim().toLowerCase();
        let result = entries
            .filter(e => !q || e.title.toLowerCase().includes(q) || (e.description ?? '').toLowerCase().includes(q))
            .filter(e => selectedTags.size === 0 || e.generalTags.some(t => selectedTags.has(t)));
        if (sortOrder === 'nameAsc') result = [...result].sort((a, b) => a.title.localeCompare(b.title));
        else if (sortOrder === 'nameDesc') result = [...result].sort((a, b) => b.title.localeCompare(a.title));
        return result;
    }, [entries, searchQuery, selectedTags, sortOrder]);

    const totalPages = Math.ceil(filteredEntries.length / PAGE_SIZE);
    const visibleEntries = filteredEntries.slice(currentPage * PAGE_SIZE, (currentPage + 1) * PAGE_SIZE);

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

    const [editorOpen, setEditorOpen] = useState(false);
    const [editingEntry, setEditingEntry] = useState<DocumentLibraryEntrySummaryDto | undefined>();

    const [uploadOpen, setUploadOpen] = useState(false);
    const [uploadingEntry, setUploadingEntry] = useState<DocumentLibraryEntrySummaryDto | undefined>();

    const [confirmDeleteId, setConfirmDeleteId] = useState<string | null>(null);

    const loadEntries = () =>
    {
        setLoading(true);
        setError(null);
        listDocumentLibraryEntries({scope: activeTab})
            .then(setEntries)
            .catch(() => setError('Failed to load documents'))
            .finally(() => setLoading(false));
    };

    useEffect(() => { loadEntries(); }, [activeTab]);

    const handlePublish = async (entry: DocumentLibraryEntrySummaryDto) =>
    {
        await patchDocumentLibraryEntryPublished(entry.id, {isPublished: !entry.isPublished});
        loadEntries();
    };

    const handleActivate = async (entry: DocumentLibraryEntrySummaryDto) =>
    {
        await patchDocumentLibraryEntryStatus(entry.id, {isActive: !entry.isActive});
        loadEntries();
    };

    const handleClone = async (entry: DocumentLibraryEntrySummaryDto) =>
    {
        await cloneDocumentLibraryEntry(entry.id, {newName: `${entry.title} (copy)`});
        loadEntries();
    };

    const handleCloneToPersonal = async (entry: DocumentLibraryEntrySummaryDto) =>
    {
        await cloneDocumentLibraryEntry(entry.id, {newName: `${entry.title} (copy)`, targetScope: 'PERSONAL'});
        loadEntries();
    };

    const handleCloneToOrg = async (entry: DocumentLibraryEntrySummaryDto) =>
    {
        await cloneDocumentLibraryEntry(entry.id, {newName: `${entry.title} (copy)`, targetScope: 'ORG'});
        loadEntries();
    };

    const handleDelete = async (id: string) =>
    {
        await deleteDocumentLibraryEntry(id);
        loadEntries();
    };

    const handleDownload = (entry: DocumentLibraryEntrySummaryDto) =>
    {
        downloadDocumentLibraryFile(entry.id, entry.fileName ?? entry.title);
    };

    const openEdit = (entry: DocumentLibraryEntrySummaryDto) =>
    {
        setEditingEntry(entry);
        setEditorOpen(true);
    };

    const openCreate = () =>
    {
        setEditingEntry(undefined);
        setEditorOpen(true);
    };

    const openUpload = (entry: DocumentLibraryEntrySummaryDto) =>
    {
        setUploadingEntry(entry);
        setUploadOpen(true);
    };

    const canCreate =
        activeTab === 'PERSONAL' ||
        (activeTab === 'ORG' && !!canManageOrganization) ||
        (activeTab === 'APP' && isAppAdmin);

    const canManageItem = (entry: DocumentLibraryEntrySummaryDto) =>
        entry.scope === 'PERSONAL' ||
        (entry.scope === 'ORG' && !!canManageOrganization) ||
        (entry.scope === 'APP' && isAppAdmin);

    return (
        <>
            <div
                id="documents-tab-container"
                className={styles.container}
            >
                <div className={styles.stickyBlock}>
                    <div className={styles.header}>
                        <TabList
                            selectedValue={activeTab}
                            onTabSelect={(_, d) =>
                            {
                                setActiveTab(d.value as ActiveTab);
                                setEntries([]);
                                setSearchQuery('');
                                setSelectedTags(new Set());
                                setSortOrder('default');
                                setCurrentPage(0);
                                setFilterSearch('');
                            }}
                        >
                            <Tab
                                id="doc-tab-personal"
                                value="PERSONAL"
                            >
                                {tabLabels.PERSONAL}
                            </Tab>
                            <Tab
                                id="doc-tab-org"
                                value="ORG"
                            >
                                {tabLabels.ORG}
                            </Tab>
                            <Tab
                                id="doc-tab-app"
                                value="APP"
                            >
                                {tabLabels.APP}
                            </Tab>
                        </TabList>

                        {canCreate && (
                            <Button
                                id="doc-create-btn"
                                icon={<AddIcon/>}
                                appearance="subtle"
                                shape="circular"
                                onClick={openCreate}
                            >
                                Create Document
                            </Button>
                        )}
                    </div>

                    <div className={styles.searchRow}>
                        <Field style={{flex: 1}}>
                            <SearchBox
                                id="doc-search-input"
                                placeholder="Search documents"
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
                                            id="doc-filter-btn"
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
                                        id="doc-sort-btn"
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

                {loading && (
                    <Spinner
                        id="doc-loading-spinner"
                        size="small"
                        label="Loading documents..."
                    />
                )}
                {!loading && error && (
                    <Text
                        id="doc-error-text"
                        className={styles.errorText}
                    >
                        {error}
                    </Text>
                )}
                {!loading && !error && entries.length === 0 && (
                    <Text
                        id="doc-empty-text"
                        className={styles.emptyText}
                    >
                        {emptyMessage[activeTab]}
                    </Text>
                )}
                {!loading && !error && entries.length > 0 && filteredEntries.length === 0 && (
                    <Text
                        id="doc-no-results-text"
                        className={styles.emptyText}
                    >
                        No documents match your search.
                    </Text>
                )}
                {!loading && !error && visibleEntries.length > 0 && (
                    <div className={styles.cardGrid}>
                        {visibleEntries.map(entry => (
                            <DocumentLibraryEntryCard
                                key={entry.id}
                                entry={entry}
                                canManage={canManageItem(entry)}
                                showPublishToggle={activeTab !== 'PERSONAL'}
                                onEdit={() => openEdit(entry)}
                                onUpload={() => openUpload(entry)}
                                onDownload={() => handleDownload(entry)}
                                onPublish={() => handlePublish(entry)}
                                onActivate={() => handleActivate(entry)}
                                onClone={() => handleClone(entry)}
                                onDelete={() => setConfirmDeleteId(entry.id)}
                                onCloneToPersonal={activeTab === 'APP' ? () => handleCloneToPersonal(entry) : undefined}
                                onCloneToOrg={activeTab === 'APP' && canManageOrganization ? () => handleCloneToOrg(entry) : undefined}
                            />
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

            <DocumentLibraryEditorDialog
                open={editorOpen}
                onClose={() => setEditorOpen(false)}
                onSaved={() => { setEditorOpen(false); loadEntries(); }}
                entry={editingEntry}
                scope={activeTab as DocumentLibraryScope}
            />

            {uploadingEntry && (
                <DocumentLibraryUploadDialog
                    open={uploadOpen}
                    entryId={uploadingEntry.id}
                    entryTitle={uploadingEntry.title}
                    onClose={() => setUploadOpen(false)}
                    onUploaded={() => { setUploadOpen(false); loadEntries(); }}
                />
            )}

            <Dialog
                open={confirmDeleteId !== null}
                modalType="alert"
            >
                <DialogSurface>
                    <DialogBody>
                        <DialogTitle>Delete document?</DialogTitle>
                        <DialogContent>
                            This document will be permanently removed from the library. Any blueprints that
                            reference it will lose the file association but will not be deleted.
                        </DialogContent>
                        <DialogActions>
                            <DialogTrigger disableButtonEnhancement>
                                <Button
                                    id="doc-library-delete-cancel-btn"
                                    shape="circular"
                                    appearance="secondary"
                                    onClick={() => setConfirmDeleteId(null)}
                                >
                                    Cancel
                                </Button>
                            </DialogTrigger>
                            <Button
                                id="doc-library-delete-confirm-btn"
                                shape="circular"
                                appearance="primary"
                                onClick={() => { handleDelete(confirmDeleteId!); setConfirmDeleteId(null); }}
                            >
                                Delete
                            </Button>
                        </DialogActions>
                    </DialogBody>
                </DialogSurface>
            </Dialog>
        </>
    );
};

export default DocumentLibraryTab;
