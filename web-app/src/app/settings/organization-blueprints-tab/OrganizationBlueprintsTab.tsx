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
    Text,
} from '@fluentui/react-components';
import {MoreVerticalRegular} from '@fluentui/react-icons';
import {ActivateIcon, BlueprintAddIcon, CopyIcon, DeactivateIcon, DeleteIcon, EditIcon, PublishIcon, UnpublishIcon} from '../../components/IconBundles.tsx';
import {BlueprintDefinitionSummaryDto} from '../../models/models.tsx';
import {
    cloneBlueprint,
    deleteBlueprint,
    listBlueprints,
    patchBlueprintPublished,
    patchBlueprintStatus,
} from '../../../services/blueprintService.ts';
import BlueprintEditorDialog from '../blueprints-tab/BlueprintEditorDialog.tsx';
import {useTemplatesTabStyles} from '../blueprints-tab/BlueprintsTabStyles.tsx';

const OrganizationBlueprintsTab = () =>
{
    const styles = useTemplatesTabStyles();
    const [blueprints, setBlueprints] = useState<BlueprintDefinitionSummaryDto[]>([]);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [editorOpen, setEditorOpen] = useState(false);
    const [editingBlueprint, setEditingBlueprint] = useState<BlueprintDefinitionSummaryDto | undefined>();

    const loadBlueprints = () =>
    {
        setLoading(true);
        setError(null);
        listBlueprints({scope: 'ORG'})
            .then(setBlueprints)
            .catch(() => setError('Failed to load organization blueprints'))
            .finally(() => setLoading(false));
    };

    useEffect(() => { loadBlueprints(); }, []);

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

    return (
        <>
            <div className={styles.tabContainer}>
                <div className={styles.header}>
                    <Text size={500} weight="semibold">Organization Blueprints</Text>
                    <Button icon={<BlueprintAddIcon/>}
                            appearance="secondary"
                            shape="circular"
                            onClick={openCreate}>
                        Create
                    </Button>
                </div>

                {loading && <Spinner size="medium" label="Loading blueprints…"/>}
                {!loading && error && (
                    <Text style={{color: 'var(--colorPaletteRedForeground1)'}}>{error}</Text>
                )}
                {!loading && !error && blueprints.length === 0 && (
                    <Text style={{color: 'var(--colorNeutralForeground3)'}}>
                        No organization blueprints yet. Create one to share with your org.
                    </Text>
                )}
                {!loading && !error && blueprints.map(bp => (
                    <div
                        key={bp.id}
                        style={{
                            width: '100%',
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
                            <Text weight="semibold" size={400}>{bp.name}</Text>
                            {bp.summary && (
                                <Text size={200} style={{color: 'var(--colorNeutralForeground2)'}}>
                                    {bp.summary}
                                </Text>
                            )}
                            <div style={{display: 'flex', gap: '6px', flexWrap: 'wrap', alignItems: 'center'}}>
                                <Badge
                                    appearance="tint"
                                    color={bp.isPublished ? 'success' : 'warning'}
                                    size="small"
                                >
                                    {bp.isPublished ? 'Published' : 'Draft'}
                                </Badge>
                                <Badge
                                    appearance="tint"
                                    color={bp.isActive ? 'success' : 'severe'}
                                    size="small"
                                >
                                    {bp.isActive ? 'Active' : 'Inactive'}
                                </Badge>
                                {bp.generalTags.map(tag => (
                                    <Badge key={tag} appearance="tint" size="small">{tag}</Badge>
                                ))}
                            </div>
                        </div>
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
                                    <MenuItem icon={<EditIcon/>} onClick={() => openEdit(bp)}>Edit</MenuItem>
                                    <MenuItem
                                        icon={bp.isPublished ? <UnpublishIcon/> : <PublishIcon/>}
                                        onClick={() => handlePublish(bp)}>
                                        {bp.isPublished ? 'Unpublish' : 'Publish'}
                                    </MenuItem>
                                    <MenuItem
                                        icon={bp.isActive ? <DeactivateIcon/> : <ActivateIcon/>}
                                        onClick={() => handleActivate(bp)}>
                                        {bp.isActive ? 'Deactivate' : 'Activate'}
                                    </MenuItem>
                                    <MenuItem icon={<CopyIcon/>} onClick={() => handleDuplicate(bp)}>Duplicate</MenuItem>
                                    <MenuItem icon={<DeleteIcon/>} onClick={() => handleDelete(bp)}>Delete</MenuItem>
                                </MenuList>
                            </MenuPopover>
                        </Menu>
                    </div>
                ))}
            </div>

            <BlueprintEditorDialog
                open={editorOpen}
                onClose={() => setEditorOpen(false)}
                onSaved={() => { setEditorOpen(false); loadBlueprints(); }}
                blueprint={editingBlueprint}
                scope="ORG"
            />
        </>
    );
};

export default OrganizationBlueprintsTab;
