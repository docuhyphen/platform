import {useState} from 'react';
import {CreateVariableRequest, UpdateVariableRequest, VariableDefinitionDto, VariableScope} from '../../models/models';
import {createVariable, updateVariable} from '../../../services/variableService';

interface DrawerState
{
    open: boolean;
    editing?: VariableDefinitionDto;
}

export interface VariableEditor
{
    open: boolean;
    editing?: VariableDefinitionDto;
    formKey: string;
    formValue: string;
    formError: string | null;
    saving: boolean;
    setFormKey: (key: string) => void;
    setFormValue: (value: string) => void;
    setOpen: (open: boolean) => void;
    openCreate: () => void;
    openEdit: (v: VariableDefinitionDto) => void;
    close: () => void;
    save: () => Promise<void>;
}

export const useVariableEditor = (scope: VariableScope, onSaved: () => void): VariableEditor =>
{
    const [drawer, setDrawer] = useState<DrawerState>({open: false});
    const [formKey, setFormKey] = useState('');
    const [formValue, setFormValue] = useState('');
    const [formError, setFormError] = useState<string | null>(null);
    const [saving, setSaving] = useState(false);

    const openCreate = () =>
    {
        setFormKey('');
        setFormValue('');
        setFormError(null);
        setDrawer({open: true});
    };

    const openEdit = (v: VariableDefinitionDto) =>
    {
        setFormKey(v.key);
        setFormValue(v.defaultValue ?? '');
        setFormError(null);
        setDrawer({open: true, editing: v});
    };

    const save = async () =>
    {
        if (!formKey.trim())
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
                const req: UpdateVariableRequest = {defaultValue: formValue};
                await updateVariable(drawer.editing.id, req);
            }
            else
            {
                const req: CreateVariableRequest = {key: formKey.toUpperCase(), defaultValue: formValue, scope};
                await createVariable(req);
            }
            setDrawer({open: false});
            onSaved();
        }
        catch (e: unknown)
        {
            const msg = typeof e === 'object' && e !== null && 'errorMessage' in e
                ? (e as {errorMessage: string}).errorMessage
                : 'Failed to save variable';
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
        formKey,
        formValue,
        formError,
        saving,
        setFormKey,
        setFormValue,
        setOpen: open => setDrawer(prev => ({...prev, open})),
        openCreate,
        openEdit,
        close: () => setDrawer({open: false}),
        save,
    };
};
