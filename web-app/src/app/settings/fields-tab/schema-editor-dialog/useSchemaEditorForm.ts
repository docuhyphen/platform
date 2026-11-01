import {useEffect, useState} from 'react';
import {FieldScopeKind, ResponseError, SchemaDefinitionDto, SchemaTargetResourceType} from '../../../models/models';
import {BindingDraft, toBindingDrafts} from '../schemaBindingDrafts';
import {SchemaIdentity} from '../schema-identity-fields/SchemaIdentityFields';
import {schemaTargetOf} from '../schemaTarget';
import {saveSchema} from './schemaEditorSave';

export interface SchemaEditorForm
{
    identity: SchemaIdentity;
    bindings: BindingDraft[];
    saving: boolean;
    error: string | null;
    updateIdentity: (patch: Partial<SchemaIdentity>) => void;
    setBindings: (bindings: BindingDraft[]) => void;
    save: () => Promise<void>;
}

const emptyIdentity = (): SchemaIdentity =>
    ({
        namespace: '',
        schemaKey: '',
        displayName: '',
        description: '',
        targetResourceType: SchemaTargetResourceType.EXCHANGE
    });

const identityOf = (schema: SchemaDefinitionDto): SchemaIdentity =>
    ({
        namespace: schema.namespace,
        schemaKey: schema.schemaKey,
        displayName: schema.displayName,
        description: schema.description ?? '',
        targetResourceType: schemaTargetOf(schema.targetResourceType),
    });

export const useSchemaEditorForm = (
    open: boolean,
    schema: SchemaDefinitionDto | undefined,
    enforcedScope: FieldScopeKind | undefined,
    onSaved: () => void,
): SchemaEditorForm =>
{
    const [identity, setIdentity] = useState<SchemaIdentity>(emptyIdentity());
    const [bindings, setBindings] = useState<BindingDraft[]>([]);
    const [saving, setSaving] = useState(false);
    const [error, setError] = useState<string | null>(null);

    useEffect(() =>
    {
        if (!open) return;
        setError(null);
        if (schema)
        {
            setIdentity(identityOf(schema));
            setBindings(toBindingDrafts(schema.draftVersion?.bindings ?? []));
        }
        else
        {
            setIdentity(emptyIdentity());
            setBindings([]);
        }
    }, [open, schema]);

    const updateIdentity = (patch: Partial<SchemaIdentity>) =>
        setIdentity(prev => ({...prev, ...patch}));

    const save = async () =>
    {
        if (!schema && (!identity.namespace.trim() || !identity.schemaKey.trim() || !identity.displayName.trim()))
        {
            setError('Namespace, key, and name are required');
            return;
        }
        setSaving(true);
        setError(null);
        try
        {
            await saveSchema(identity, bindings, schema, enforcedScope);
            onSaved();
        } catch (refusal: unknown)
        {
            setError((refusal as ResponseError | null)?.errorMessage?.trim() || 'Failed to save schema');
        }
        finally
        {
            setSaving(false);
        }
    };

    return {identity, bindings, saving, error, updateIdentity, setBindings, save};
};
