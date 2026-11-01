import {useEffect, useState} from 'react';
import {
    AvailableVariablesDto,
    CommunicationDto,
    CommunicationScope,
    CommunicationSummaryDto,
} from '../../models/models';
import {createCommunication, updateCommunication} from '../../../services/communicationService';
import {getAvailableVariables} from '../../../services/variableService';
import {CommunicationEditorTab, CommunicationEditorValues} from './communicationEditorTypes';
import {
    buildCreateCommunicationRequest,
    buildUpdateCommunicationRequest,
    communicationValuesFrom,
    communicationVariableHints,
    emptyCommunicationValues,
    validateCommunicationValues,
} from './communicationEditorValues';
import {useCommunicationPreview} from './useCommunicationPreview';

interface Options
{
    open: boolean;
    communication?: CommunicationSummaryDto | CommunicationDto;
    scope: CommunicationScope;
    enforcedScope?: CommunicationScope;
    createAsTemplate: boolean;
    onSaved: () => void;
}

export const useCommunicationEditorForm = ({open, communication, scope, enforcedScope, createAsTemplate, onSaved}: Options) =>
{
    const [activeTab, setActiveTab] = useState<CommunicationEditorTab>('details');
    const [values, setValues] = useState<CommunicationEditorValues>(emptyCommunicationValues);
    const [tagInput, setTagInput] = useState('');
    const [saving, setSaving] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [availableVariables, setAvailableVariables] = useState<AvailableVariablesDto | null>(null);
    const preview = useCommunicationPreview(communication?.id);
    const {resetPreview} = preview;

    useEffect(() =>
    {
        if (!open) return;
        if (enforcedScope === 'PLATFORM')
        {
            setAvailableVariables(null);
        }
        else
        {
            getAvailableVariables().then(setAvailableVariables).catch(() => null);
        }
        setValues(communicationValuesFrom(communication));
        setActiveTab('details');
        setError(null);
        resetPreview();
    }, [open, communication, enforcedScope, resetPreview]);

    const updateValue = <K extends keyof CommunicationEditorValues>(key: K, value: CommunicationEditorValues[K]) =>
        setValues(previous => ({...previous, [key]: value}));

    const appendToBody = (text: string) =>
        setValues(previous => ({...previous, body: previous.body + text}));

    const addTag = () =>
    {
        const tag = tagInput.trim();
        if (tag && !values.tags.includes(tag))
        {
            setValues(previous => ({...previous, tags: [...previous.tags, tag]}));
        }
        setTagInput('');
    };

    const removeTag = (tag: string) =>
        setValues(previous => ({...previous, tags: previous.tags.filter(existing => existing !== tag)}));

    const save = async () =>
    {
        if (enforcedScope && communication && communication.scope !== enforcedScope)
        {
            setError('This communication is outside the Platform Administration scope.');
            return;
        }
        const validationError = validateCommunicationValues(values);
        if (validationError)
        {
            setError(validationError);
            return;
        }
        setSaving(true);
        setError(null);
        try
        {
            if (communication)
            {
                await updateCommunication(communication.id, buildUpdateCommunicationRequest(values));
            }
            else
            {
                await createCommunication(buildCreateCommunicationRequest(values, enforcedScope ?? scope, createAsTemplate));
            }
            onSaved();
        }
        catch (e: unknown)
        {
            setError(typeof e === 'string' ? e : 'Failed to save communication');
        }
        finally
        {
            setSaving(false);
        }
    };

    return {
        activeTab,
        setActiveTab,
        values,
        updateValue,
        appendToBody,
        tagInput,
        setTagInput,
        addTag,
        removeTag,
        saving,
        error,
        availableVariables,
        variableHints: communicationVariableHints(availableVariables),
        preview,
        save,
    };
};
