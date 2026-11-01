import {useEffect, useState} from 'react';
import {AvailableVariablesDto} from '../../../models/models.tsx';
import {getAvailableVariables} from '../../../../services/variableService.ts';
import {DocumentLibraryEditorTarget, saveDocumentLibraryEntry} from './documentLibraryEditorRequests.ts';

export const useDocumentLibraryEditorForm = (open: boolean, target: DocumentLibraryEditorTarget, onSaved: () => void) =>
{
    const {entry, enforcedScope} = target;
    const [title, setTitle] = useState('');
    const [description, setDescription] = useState('');
    const [tagInput, setTagInput] = useState('');
    const [tags, setTags] = useState<string[]>([]);
    const [restrictType, setRestrictTypeState] = useState(false);
    const [restrictedType, setRestrictedType] = useState<string | undefined>(undefined);
    const [required, setRequired] = useState(false);
    const [availableVariables, setAvailableVariables] = useState<AvailableVariablesDto | null>(null);
    const [saving, setSaving] = useState(false);
    const [error, setError] = useState<string | null>(null);

    useEffect(() =>
    {
        if (!open) return;
        setTitle(entry?.title ?? '');
        setDescription(entry?.description ?? '');
        setTags(entry?.generalTags ?? []);
        setTagInput('');
        setRestrictTypeState(entry?.restrictType ?? false);
        setRestrictedType(entry?.restrictedType);
        setRequired(entry?.required ?? false);
        setError(null);
        setAvailableVariables(null);
        if (enforcedScope !== 'APP')
            getAvailableVariables().then(setAvailableVariables).catch(() => setAvailableVariables(null));
    }, [open, entry, enforcedScope]);

    const setRestrictType = (checked: boolean) =>
    {
        setRestrictTypeState(checked);
        if (!checked) setRestrictedType(undefined);
    };

    const addTag = () =>
    {
        const trimmed = tagInput.trim();
        if (trimmed && !tags.includes(trimmed)) setTags(prev => [...prev, trimmed]);
        setTagInput('');
    };

    const removeTag = (tag: string) => setTags(prev => prev.filter(t => t !== tag));

    const handleSave = async () =>
    {
        if (!title.trim())
        {
            setError('Title is required');
            return;
        }
        setSaving(true);
        setError(null);
        try
        {
            await saveDocumentLibraryEntry(target, {title, description, tags, restrictType, restrictedType, required});
            onSaved();
        }
        catch
        {
            setError('Failed to save. Please try again.');
        }
        finally
        {
            setSaving(false);
        }
    };

    return {
        title,
        setTitle,
        description,
        setDescription,
        tagInput,
        setTagInput,
        tags,
        addTag,
        removeTag,
        restrictType,
        setRestrictType,
        restrictedType,
        setRestrictedType,
        required,
        setRequired,
        availableVariables,
        saving,
        error,
        handleSave,
    };
};
