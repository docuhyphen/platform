import {useEffect, useState} from 'react';
import {FieldScopeKind} from '../../../models/models';
import {createFieldDefinition} from '../../../../services/fieldsService';
import {createPlatformField} from '../../../../services/platformFieldsService.ts';
import {
    createFieldFailureMessage,
    emptyFieldForm,
    FieldForm,
    fieldFormValidationError,
    toCreateFieldRequest,
} from './fieldDefinitionForm';

export interface FieldDefinitionForm
{
    form: FieldForm;
    saving: boolean;
    error: string | null;
    update: (patch: Partial<FieldForm>) => void;
    save: () => Promise<void>;
}

export const useFieldDefinitionForm = (
    open: boolean,
    enforcedScope: FieldScopeKind | undefined,
    onSaved: () => void,
): FieldDefinitionForm =>
{
    const [form, setForm] = useState<FieldForm>(emptyFieldForm());
    const [saving, setSaving] = useState(false);
    const [error, setError] = useState<string | null>(null);

    useEffect(() =>
    {
        if (open) { setForm(emptyFieldForm()); setError(null); }
    }, [open]);

    const update = (patch: Partial<FieldForm>) => setForm(prev => ({...prev, ...patch}));

    const save = async () =>
    {
        const validationError = fieldFormValidationError(form);
        if (validationError)
        {
            setError(validationError);
            return;
        }
        setSaving(true);
        setError(null);
        try
        {
            const request = toCreateFieldRequest(form);
            if (enforcedScope === FieldScopeKind.PLATFORM)
                await createPlatformField(request);
            else
                await createFieldDefinition(request);
            onSaved();
        }
        catch (e: unknown)
        {
            setError(createFieldFailureMessage(e));
        }
        finally
        {
            setSaving(false);
        }
    };

    return {form, saving, error, update, save};
};
