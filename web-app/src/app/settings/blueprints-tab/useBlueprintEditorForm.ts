import {useEffect, useState} from 'react';
import {
    AvailableVariablesDto,
    BlueprintConfig,
    BlueprintDefinitionSummaryDto,
    BlueprintDocumentConfig,
    BlueprintScope,
} from '../../models/models.tsx';
import {createBlueprint, updateBlueprint} from '../../../services/blueprintService.ts';
import {getAvailableVariables} from '../../../services/variableService.ts';
import {normalizeApiError} from '../../../utils/apiErrorUtils.ts';
import {BlueprintBooleanConfigKey, BlueprintEditorTab, BlueprintEditorValues} from './blueprintEditorTypes.ts';
import {blueprintEditorValuesFrom, emptyBlueprintEditorValues} from './blueprintEditorValues.ts';
import {buildCreateBlueprintRequest, buildUpdateBlueprintRequest} from './blueprintEditorRequests.ts';

interface BlueprintEditorFormArgs
{
    open: boolean;
    blueprint?: BlueprintDefinitionSummaryDto;
    scope: BlueprintScope;
    enforcedScope?: BlueprintScope;
    onSaved: () => void;
}

export interface BlueprintEditorForm
{
    effectiveScope: BlueprintScope;
    activeTab: BlueprintEditorTab;
    setActiveTab: (tab: BlueprintEditorTab) => void;
    values: BlueprintEditorValues;
    update: (patch: Partial<BlueprintEditorValues>) => void;
    updateConfig: (patch: Partial<BlueprintConfig>) => void;
    setBoolConfig: (key: BlueprintBooleanConfigKey, value: boolean) => void;
    tagInput: string;
    setTagInput: (value: string) => void;
    addTag: () => void;
    removeTag: (tag: string) => void;
    addDocument: () => void;
    appendDocuments: (documents: BlueprintDocumentConfig[]) => void;
    removeDocument: (index: number) => void;
    updateDocument: (index: number, patch: Partial<BlueprintDocumentConfig>) => void;
    availableVariables: AvailableVariablesDto | null;
    pickerOpen: boolean;
    setPickerOpen: (open: boolean) => void;
    saving: boolean;
    error: string | null;
    save: () => Promise<void>;
}

export const useBlueprintEditorForm = (
    {open, blueprint, scope, enforcedScope, onSaved}: BlueprintEditorFormArgs,
): BlueprintEditorForm =>
{
    const effectiveScope = enforcedScope ?? scope;
    const [activeTab, setActiveTab] = useState<BlueprintEditorTab>('details');
    const [values, setValues] = useState<BlueprintEditorValues>(emptyBlueprintEditorValues);
    const [tagInput, setTagInput] = useState('');
    const [saving, setSaving] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [availableVariables, setAvailableVariables] = useState<AvailableVariablesDto | null>(null);
    const [pickerOpen, setPickerOpen] = useState(false);

    useEffect(() =>
    {
        if (!open) return;
        if (enforcedScope === 'APP')
            setAvailableVariables(null);
        else
            getAvailableVariables().then(setAvailableVariables).catch((): null => null);
        setValues(blueprint ? blueprintEditorValuesFrom(blueprint) : emptyBlueprintEditorValues());
        setActiveTab('details');
        setPickerOpen(false);
        setError(null);
    }, [open, blueprint, enforcedScope]);

    const update = (patch: Partial<BlueprintEditorValues>) =>
        setValues(prev => ({...prev, ...patch}));

    const updateConfig = (patch: Partial<BlueprintConfig>) =>
        setValues(prev => ({...prev, config: {...prev.config, ...patch}}));

    const updateDocuments = (change: (documents: BlueprintDocumentConfig[]) => BlueprintDocumentConfig[]) =>
        setValues(prev => ({...prev, documents: change(prev.documents)}));

    const addTag = () =>
    {
        const t = tagInput.trim();
        if (t && !values.tags.includes(t)) setValues(prev => ({...prev, tags: [...prev.tags, t]}));
        setTagInput('');
    };

    const save = async () =>
    {
        if (blueprint && enforcedScope && blueprint.scope !== enforcedScope)
        {
            setError('The selected Blueprint is outside the enforced editor scope');
            return;
        }
        if (!values.name.trim())
        {
            setError('Name is required');
            return;
        }
        setSaving(true);
        setError(null);
        try
        {
            if (blueprint)
                await updateBlueprint(blueprint.id, buildUpdateBlueprintRequest(values, blueprint.informationRequestTemplateVersionId));
            else
                await createBlueprint(buildCreateBlueprintRequest(values, effectiveScope));
            onSaved();
        }
        catch (e: unknown)
        {
            setError(normalizeApiError(e, 'Failed to save blueprint').message);
        }
        finally
        {
            setSaving(false);
        }
    };

    return {
        effectiveScope,
        activeTab,
        setActiveTab,
        values,
        update,
        updateConfig,
        setBoolConfig: (key, value) => setValues(prev => ({...prev, config: {...prev.config, [key]: value}})),
        tagInput,
        setTagInput,
        addTag,
        removeTag: tag => setValues(prev => ({...prev, tags: prev.tags.filter(t => t !== tag)})),
        addDocument: () => updateDocuments(documents => [...documents, {title: ''}]),
        appendDocuments: added => updateDocuments(documents => [...documents, ...added]),
        removeDocument: index => updateDocuments(documents => documents.filter((_, i) => i !== index)),
        updateDocument: (index, patch) =>
            updateDocuments(documents => documents.map((d, i) => i === index ? {...d, ...patch} : d)),
        availableVariables,
        pickerOpen,
        setPickerOpen,
        saving,
        error,
        save,
    };
};
