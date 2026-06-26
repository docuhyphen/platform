import React, {useEffect, useState} from 'react';
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
import {AppUserRole, CommunicationSummaryDto, CommunicationScope} from '../../models/models';
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

type ActiveTab = 'PERSONAL' | 'ORG' | 'PLATFORM';

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
    const {appUser, appUserPersonOrganization} = useAuth();

    const roleValue = `${appUser?.role ?? ''}`;
    const canManageOrganization =
        appUserPersonOrganization?.isActive &&
        (roleValue === AppUserRole.ORG_ADMIN || roleValue === 'APP_ADMIN');
    const isAppAdmin = roleValue === 'APP_ADMIN';

    const [activeTab, setActiveTab] = useState<ActiveTab>('PERSONAL');
    const [communications, setCommunications] = useState<CommunicationSummaryDto[]>([]);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [editorOpen, setEditorOpen] = useState(false);
    const [editingCommunication, setEditingCommunication] = useState<CommunicationSummaryDto | undefined>();

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
        (activeTab === 'ORG' && !!canManageOrganization) ||
        (activeTab === 'PLATFORM' && isAppAdmin);

    const canManageItem = (t: CommunicationSummaryDto) =>
        t.scope === 'PERSONAL' ||
        (t.scope === 'ORG' && !!canManageOrganization) ||
        (t.scope === 'PLATFORM' && isAppAdmin);

    return (
        <>
            <div style={{display: 'flex', flexDirection: 'column', gap: '16px', width: '100%'}}>
                <div style={{display: 'flex', alignItems: 'center', justifyContent: 'space-between'}}>
                    <TabList
                        selectedValue={activeTab}
                        onTabSelect={(_, d) =>
                        {
                            setActiveTab(d.value as ActiveTab);
                            setCommunications([]);
                        }}
                    >
                        <Tab value="PERSONAL">{tabLabels.PERSONAL}</Tab>
                        <Tab value="ORG">{tabLabels.ORG}</Tab>
                        <Tab value="PLATFORM">{tabLabels.PLATFORM}</Tab>
                    </TabList>

                    {canCreate && (
                        <Button
                            appearance="subtle"
                            shape="circular"
                            icon={<AddIcon/>}
                            onClick={openCreate}
                        >
                            Create Communication
                        </Button>
                    )}
                </div>

                {loading && <Spinner size="medium" label="Loading communications…"/>}
                {!loading && error && (
                    <Text style={{color: 'var(--colorPaletteRedForeground1)'}}>{error}</Text>
                )}
                {!loading && !error && communications.length === 0 && (
                    <Text style={{color: 'var(--colorNeutralForeground3)'}}>{emptyMessage[activeTab]}</Text>
                )}
                {!loading && !error && communications.length > 0 && (
                    <div className={styles.cardGrid}>
                        {communications.map(t => (
                            <div
                                key={t.id}
                                style={{
                                    border: '1px solid var(--colorNeutralStroke1)',
                                    borderRadius: '8px',
                                    padding: '12px 16px',
                                    display: 'flex',
                                    justifyContent: 'space-between',
                                    alignItems: 'flex-start',
                                    gap: '8px',
                                }}
                            >
                                <div style={{display: 'flex', flexDirection: 'column', gap: '4px', flex: 1, minWidth: 0}}>
                                    <Text weight="semibold" size={400}>{t.name}</Text>
                                    {t.summary && (
                                        <Text size={200} style={{color: 'var(--colorNeutralForeground2)'}}>
                                            {t.summary}
                                        </Text>
                                    )}
                                    <Text
                                        size={200}
                                        style={{
                                            color: 'var(--colorNeutralForeground3)',
                                            fontStyle: 'italic',
                                            overflow: 'hidden',
                                            textOverflow: 'ellipsis',
                                            whiteSpace: 'nowrap',
                                        }}
                                    >
                                        {t.subject}
                                    </Text>
                                    <div style={{display: 'flex', gap: '6px', flexWrap: 'wrap', alignItems: 'center'}}>
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
                                        {t.generalTags.map(tag => (
                                            <Badge key={tag} appearance="tint" size="small">{tag}</Badge>
                                        ))}
                                    </div>
                                </div>
                                {canManageItem(t) && (
                                    <Menu>
                                        <MenuTrigger disableButtonEnhancement>
                                            <Button
                                                size="small"
                                                appearance="subtle"
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
