import {useState} from 'react';
import {
    BlueprintDocumentConfig,
    BlueprintFieldDefaultConfig,
    BlueprintParticipantConfig,
    BlueprintScope,
    Capability,
    CreateBlueprintRequest,
} from '../../../models/models.tsx';
import {createBlueprint, patchBlueprintPublished} from '../../../../services/blueprintService.ts';
import {useAuth} from '../../../../context/AuthContext.tsx';

export type SaveTarget = 'PERSONAL' | 'ORG';

interface UseSaveBlueprintProps
{
    onSaved: () => void;
    initialName: string;
    configJson: string;
    exchangeDocuments: BlueprintDocumentConfig[];
    participants: BlueprintParticipantConfig[];
    schemaDefinitionId?: string;
    fieldDefaults?: BlueprintFieldDefaultConfig[];
}

export const useSaveBlueprint = (
    {
        onSaved,
        initialName,
        configJson,
        exchangeDocuments,
        participants,
        schemaDefinitionId,
        fieldDefaults,
    }: UseSaveBlueprintProps,
) =>
{
    const {appUserPersonOrganization, hasCapability} = useAuth();
    const isAdmin =
        appUserPersonOrganization?.isActive &&
        hasCapability(Capability.ORG_POLICY_MANAGE);

    const [name, setName] = useState(initialName || '');
    const [summary, setSummary] = useState('');
    const [tags, setTags] = useState<string[]>([]);
    const [saveTarget, setSaveTarget] = useState<SaveTarget>('PERSONAL');
    const [saving, setSaving] = useState(false);
    const [error, setError] = useState<string | null>(null);

    const addTag = (tag: string) =>
    {
        const trimmed = tag.trim();
        if (trimmed && !tags.includes(trimmed))
        {
            setTags(prev => [...prev, trimmed]);
        }
    };

    const removeTag = (tag: string) => setTags(prev => prev.filter(t => t !== tag));

    const save = async () =>
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
                schemaDefinitionId,
                fieldDefaults: fieldDefaults && fieldDefaults.length > 0 ? fieldDefaults : undefined,
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
        catch (e: unknown)
        {
            setError(typeof e === 'string' ? e : 'Failed to save blueprint');
        }
        finally
        {
            setSaving(false);
        }
    };

    return {
        isAdmin,
        name,
        setName,
        summary,
        setSummary,
        tags,
        addTag,
        removeTag,
        saveTarget,
        setSaveTarget,
        saving,
        error,
        save,
    };
};
