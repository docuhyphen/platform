import React, {useEffect, useState} from 'react';
import {
    Button,
    Dialog,
    DialogActions,
    DialogBody,
    DialogContent,
    DialogSurface,
    DialogTitle,
    DialogTrigger,
    Spinner,
    Tab,
    TabList,
    Text,
} from '@fluentui/react-components';
import {AddIcon, DocumentAddIcon} from '../../components/IconBundles.tsx';
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

type ActiveTab = 'PERSONAL' | 'ORG' | 'APP';

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
                <div className={styles.header}>
                    <TabList
                        selectedValue={activeTab}
                        onTabSelect={(_, d) =>
                        {
                            setActiveTab(d.value as ActiveTab);
                            setEntries([]);
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

                {loading && (
                    <Spinner
                        id="doc-loading-spinner"
                        size="medium"
                        label="Loading documents..."
                    />
                )}
                {!loading && error && (
                    <Text
                        id="doc-error-text"
                        style={{color: 'var(--colorPaletteRedForeground1)'}}
                    >
                        {error}
                    </Text>
                )}
                {!loading && !error && entries.length === 0 && (
                    <Text
                        id="doc-empty-text"
                        style={{color: 'var(--colorNeutralForeground3)'}}
                    >
                        {emptyMessage[activeTab]}
                    </Text>
                )}
                {!loading && !error && entries.length > 0 && (
                    <div className={styles.cardGrid}>
                        {entries.map(entry => (
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
                            />
                        ))}
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
