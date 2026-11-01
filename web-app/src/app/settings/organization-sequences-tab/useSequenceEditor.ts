import {Dispatch, SetStateAction, useState} from 'react';
import {CreateSequenceRequest, SequenceDefinitionDto, UpdateSequenceRequest} from '../../models/models';
import {createSequence, updateSequence} from '../../../services/variableService';
import {defaultSequenceForm} from './sequenceForm.ts';

interface SequenceDrawerState
{
    open: boolean;
    editing?: SequenceDefinitionDto;
}

export interface SequenceEditor
{
    open: boolean;
    editing?: SequenceDefinitionDto;
    form: CreateSequenceRequest;
    setForm: Dispatch<SetStateAction<CreateSequenceRequest>>;
    saving: boolean;
    formError: string | null;
    setOpen: (open: boolean) => void;
    openCreate: () => void;
    openEdit: (seq: SequenceDefinitionDto) => void;
    close: () => void;
    save: () => Promise<void>;
}

export const useSequenceEditor = (onSaved: () => void): SequenceEditor =>
{
    const [drawer, setDrawer] = useState<SequenceDrawerState>({open: false});
    const [form, setForm] = useState<CreateSequenceRequest>(defaultSequenceForm());
    const [saving, setSaving] = useState(false);
    const [formError, setFormError] = useState<string | null>(null);

    const openCreate = () =>
    {
        setForm(defaultSequenceForm());
        setFormError(null);
        setDrawer({open: true, editing: undefined});
    };

    const openEdit = (seq: SequenceDefinitionDto) =>
    {
        setForm({
            name: seq.name,
            key: seq.key,
            padWidth: seq.padWidth,
            prefix: seq.prefix ?? '',
            suffix: seq.suffix ?? '',
            resetPeriod: seq.resetPeriod,
        });
        setFormError(null);
        setDrawer({open: true, editing: seq});
    };

    const save = async () =>
    {
        if (!form.name.trim())
        {
            setFormError('Name is required');
            return;
        }
        if (!form.key.trim())
        {
            setFormError('Key is required');
            return;
        }

        setSaving(true);
        setFormError(null);
        try
        {
            if (drawer.editing)
            {
                const update: UpdateSequenceRequest = {
                    name: form.name,
                    padWidth: form.padWidth,
                    prefix: form.prefix,
                    suffix: form.suffix,
                    resetPeriod: form.resetPeriod,
                };
                await updateSequence(drawer.editing.id, update);
            }
            else
            {
                await createSequence({...form, key: form.key.toUpperCase()});
            }
            setDrawer({open: false});
            onSaved();
        }
        catch (e: unknown)
        {
            const msg = typeof e === 'object' && e !== null && 'errorMessage' in e
                ? (e as {errorMessage: string}).errorMessage
                : 'Failed to save sequence';
            setFormError(msg);
        }
        finally
        {
            setSaving(false);
        }
    };

    return {
        open: drawer.open,
        editing: drawer.editing,
        form,
        setForm,
        saving,
        formError,
        setOpen: open => setDrawer(prev => ({...prev, open})),
        openCreate,
        openEdit,
        close: () => setDrawer({open: false}),
        save,
    };
};
