import React, {useState} from 'react';
import {
    Button,
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
import {useExchangeInitiationStyles} from '../../ExchangeInitiationStyles.tsx';

interface SaveBlueprintPanelProps
{
    onBack: () => void;
    onSaved: () => void;
    initialName: string;
    configJson: string;
    exchangeDocuments: BlueprintDocumentConfig[];
    participants: BlueprintParticipantConfig[];
}

type SaveTarget = 'PERSONAL' | 'ORG';

const SaveBlueprintPanel: React.FC<SaveBlueprintPanelProps> = (
    {
        onBack,
        onSaved,
        initialName,
        configJson,
        exchangeDocuments,
        participants,
    }) =>
{
    const {appUser, appUserPersonOrganization} = useAuth();
    const docStyles = useDocumentsTabStyles();
    const styles = useExchangeInitiationStyles();
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
        <div className={styles.saveBlueprintContainer}>
            {isAdmin && (
                <Field label="Save to">
                    <RadioGroup
                        id={"save-blueprint-target-group"}
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
                    id={"save-blueprint-name-input"}
                    value={name}
                    onChange={(_, d) => setName(d.value)}
                    placeholder="Blueprint name"
                    autoFocus
                />
            </Field>
            <Field label="Summary">
                <Textarea
                    id={"save-blueprint-summary-textarea"}
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
                        id={"save-blueprint-tag-input"}
                        size="small"
                        appearance="underline"
                        placeholder="Add tag, press Enter"
                        value={tagInput}
                        onChange={(_, d) => setTagInput(d.value)}
                        onKeyDown={e => { if (e.key === 'Enter') { e.preventDefault(); addTag(); } }}
                    />
                    <Button
                        id={"save-blueprint-add-tag-btn"}
                        shape="circular"
                        appearance="subtle"
                        size="medium"
                        icon={<AddIcon/>}
                        onClick={addTag}
                    />
                </div>
            </Field>
            {error && (
                <span className={styles.saveBlueprintErrorText}>
                    {error}
                </span>
            )}
            <div className={styles.saveBlueprintActions}>
                <Button
                    id={"save-blueprint-save-btn"}
                    appearance="primary"
                    shape="circular"
                    onClick={handleSave}
                    disabled={saving}
                >
                    {saving ? <><Spinner size="tiny"/> Saving...</> : 'Save Blueprint'}
                </Button>
                <Button
                    id={"save-blueprint-back-btn"}
                    shape="circular"
                    onClick={onBack}
                    disabled={saving}>Back</Button>
            </div>
        </div>
    );
};

export default SaveBlueprintPanel;
