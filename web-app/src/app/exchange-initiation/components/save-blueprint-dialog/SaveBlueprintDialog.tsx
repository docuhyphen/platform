import React, {useState} from 'react';
import {
    Button,
    Dialog,
    DialogActions,
    DialogBody,
    DialogContent,
    DialogSurface,
    DialogTitle,
    Field,
    Input,
    Radio,
    RadioGroup,
    Spinner,
    Tag,
    Textarea,
} from '@fluentui/react-components';
import {
    AppUserRole,
    BlueprintDocumentConfig,
    BlueprintParticipantConfig,
    BlueprintScope,
    CreateBlueprintRequest,
} from '../../../models/models.tsx';
import {createBlueprint, patchBlueprintPublished} from '../../../../services/blueprintService.ts';
import {useAuth} from '../../../../context/AuthContext.tsx';
import {useDocumentsTabStyles} from '../../../settings/document-library-tab/DocumentLibraryTabStyles.tsx';
import {AddIcon} from '../../../components/IconBundles.tsx';

interface SaveBlueprintDialogProps
{
    open: boolean;
    onClose: () => void;
    onSaved: () => void;
    initialName: string;
    configJson: string;
    exchangeDocuments: BlueprintDocumentConfig[];
    participants: BlueprintParticipantConfig[];
}

type SaveTarget = 'PERSONAL' | 'ORG';

const SaveBlueprintDialog: React.FC<SaveBlueprintDialogProps> = (
    {
        open,
        onClose,
        onSaved,
        initialName,
        configJson,
        exchangeDocuments,
        participants,
    }) =>
{
    const {appUser, appUserPersonOrganization} = useAuth();
    const docStyles = useDocumentsTabStyles();
    const roleValue = `${appUser?.role ?? ''}`;
    const isAdmin =
        appUserPersonOrganization?.isActive &&
        (roleValue === AppUserRole.ORG_ADMIN || roleValue === 'APP_ADMIN');

    const [name, setName] = useState(initialName || '');
    const [summary, setSummary] = useState('');
    const [tagInput, setTagInput] = useState('');
    const [tags, setTags] = useState<string[]>([]);
    const [saveTarget, setSaveTarget] = useState<SaveTarget>('PERSONAL');
    const [saving, setSaving] = useState(false);
    const [error, setError] = useState<string | null>(null);

    const addTag = () =>
    {
        const trimmed = tagInput.trim();
        if (trimmed && !tags.includes(trimmed))
        {
            setTags(prev => [...prev, trimmed]);
        }
        setTagInput('');
    };

    const removeTag = (tag: string) => setTags(prev => prev.filter(t => t !== tag));

    const handleSave = async () =>
    {
        if (!name.trim())
        {
            setError('Name is required');
            return;
        }
        setSaving(true);
        setError(null);
        try
        {
            const scope: BlueprintScope = saveTarget === 'ORG' ? 'ORG' : 'PERSONAL';
            const request: CreateBlueprintRequest = {
                name: name.trim(),
                summary: summary.trim() || undefined,
                configJson,
                exchangeDocuments,
                participants,
                generalTags: tags,
                scope,
                isActive: true,
                isTemplate: false,
            };
            const created = await createBlueprint(request);
            if (saveTarget === 'ORG')
            {
                await patchBlueprintPublished(created.id, {isPublished: true});
            }
            onSaved();
        }
        catch (e: any)
        {
            setError(typeof e === 'string' ? e : 'Failed to save blueprint');
        }
        finally
        {
            setSaving(false);
        }
    };

    return (
        <Dialog open={open} onOpenChange={(_, {open: isOpen}) => { if (!isOpen) onClose(); }}>
            <DialogSurface>
                <DialogBody>
                    <DialogTitle>Save as Blueprint</DialogTitle>
                    <DialogContent>
                        <div style={{display: 'flex', flexDirection: 'column', gap: '16px'}}>
                            {isAdmin && (
                                <Field label="Save to">
                                    <RadioGroup
                                        value={saveTarget}
                                        onChange={(_, d) => setSaveTarget(d.value as SaveTarget)}
                                        layout="horizontal"
                                    >
                                        <Radio value="PERSONAL" label="My Blueprints"/>
                                        <Radio value="ORG" label="Organization"/>
                                    </RadioGroup>
                                </Field>
                            )}
                            <Field label="Name" required>
                                <Input
                                    value={name}
                                    onChange={(_, d) => setName(d.value)}
                                    placeholder="Blueprint name"
                                />
                            </Field>
                            <Field label="Summary">
                                <Textarea
                                    value={summary}
                                    onChange={(_, d) => setSummary(d.value)}
                                    placeholder="Short description of this blueprint"
                                    rows={2}
                                />
                            </Field>
                            <Field label="Tags">
                                <div className={docStyles.tagInput}>
                                    {tags.map(tag => (
                                        <Tag
                                            key={tag}
                                            size="small"
                                            dismissible
                                            onClick={() => removeTag(tag)}
                                        >
                                            {tag}
                                        </Tag>
                                    ))}
                                    <Input
                                        size="small"
                                        appearance="underline"
                                        placeholder="Add tag, press Enter"
                                        value={tagInput}
                                        onChange={(_, d) => setTagInput(d.value)}
                                        onKeyDown={e => { if (e.key === 'Enter') { e.preventDefault(); addTag(); } }}
                                        style={{border: 'none', flexGrow: 1, minWidth: '8rem'}}
                                    />
                                    <Button
                                        shape="circular"
                                        appearance="subtle"
                                        size="medium"
                                        icon={<AddIcon/>}
                                        onClick={addTag}
                                    />
                                </div>
                            </Field>
                            {error && (
                                <span style={{color: 'var(--colorPaletteRedForeground1)', fontSize: '12px'}}>
                                    {error}
                                </span>
                            )}
                        </div>
                    </DialogContent>
                    <DialogActions>
                        <Button
                            appearance="primary"
                            shape="circular"
                            onClick={handleSave}
                            disabled={saving}
                        >
                            {saving ? <><Spinner size="tiny"/> Saving…</> : 'Save Blueprint'}
                        </Button>
                        <Button shape="circular" onClick={onClose} disabled={saving}>Cancel</Button>
                    </DialogActions>
                </DialogBody>
            </DialogSurface>
        </Dialog>
    );
};

export default SaveBlueprintDialog;
