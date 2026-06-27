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
    BlueprintAddIcon,
    CopyIcon,
    DeactivateIcon,
    DeleteIcon,
    EditIcon,
    PublishIcon,
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
                <div className={styles.headerRow}>
                    <TabList
                        selectedValue={activeTab}
                        onTabSelect={(_, d) =>
                        {
                            setActiveTab(d.value as ActiveTab);
                            setBlueprints([]);
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

                {loading && <Spinner size="small" label="Loading blueprints…"/>}
                {!loading && error && (
                    <Text className={styles.errorText}>{error}</Text>
                )}
                {!loading && !error && blueprints.length === 0 && (
                    <Text className={styles.emptyText}>{emptyMessage[activeTab]}</Text>
                )}
                {!loading && !error && blueprints.length > 0 && (
                    <div className={styles.cardGrid}>
                        {blueprints.map(bp => (
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
                                                <MenuItem icon={<CopyIcon/>} onClick={() => handleDuplicate(bp)}>Duplicate</MenuItem>
                                                <MenuItem icon={<DeleteIcon/>} onClick={() => handleDelete(bp)}>Delete</MenuItem>
                                            </MenuList>
                                        </MenuPopover>
                                    </Menu>
                                )}
                            </div>
                        ))}
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
