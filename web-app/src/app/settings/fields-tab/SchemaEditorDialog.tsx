import {useEffect, useState} from 'react';
import {
    Button,
    Dialog,
    DialogActions,
    DialogBody,
    DialogContent,
    DialogSurface,
    DialogTitle,
    Field,
    Spinner,
} from '@fluentui/react-components';
import {FieldDefinitionDto, FieldScopeKind, SchemaDefinitionDto} from '../../models/models';
import {
    BindingRequest,
    createSchema,
    updateSchemaDraftBindings,
} from '../../../services/fieldsService';
import {
    createPlatformSchema,
    updatePlatformSchemaBindings,
} from '../../../services/platformFieldsService.ts';
import {useFieldsTabStyles} from './FieldsTabStyles';
import SchemaBindingsEditor, {BindingDraft} from './SchemaBindingsEditor';
import SchemaIdentityFields, {SchemaIdentity} from './SchemaIdentityFields';

interface Props
{
    open: boolean;
    onClose: () => void;
    onSaved: () => void;
    definitions: FieldDefinitionDto[];
    schema?: SchemaDefinitionDto;
    enforcedScope?: FieldScopeKind;
}

const emptyIdentity = (): SchemaIdentity =>
    ({namespace: '', schemaKey: '', displayName: '', description: ''});

const toBindingRequests = (bindings: BindingDraft[]): BindingRequest[] =>
    bindings.map((b, index) => ({
        fieldContractId: b.fieldContractId,
        displayOrder: index,
        isRequired: b.isRequired,
        isReadOnly: b.isReadOnly,
    }));

const SchemaEditorDialog = ({open, onClose, onSaved, definitions, schema, enforcedScope}: Props) =>
{
    const styles = useFieldsTabStyles();
    const isEdit = !!schema;
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
            setIdentity({
                namespace: schema.namespace,
                schemaKey: schema.schemaKey,
                displayName: schema.displayName,
                description: schema.description ?? '',
            });
            setBindings((schema.draftVersion?.bindings ?? []).map(b => ({
                fieldContractId: b.fieldContractId,
                label: b.label,
                keyLabel: `${b.namespace}:${b.fieldKey}`,
                isRequired: b.isRequired,
                isReadOnly: b.isReadOnly,
            })));
        }
        else
        {
            setIdentity(emptyIdentity());
            setBindings([]);
        }
    }, [open, schema]);

    const updateIdentity = (patch: Partial<SchemaIdentity>) =>
        setIdentity(prev => ({...prev, ...patch}));

    const handleSave = async () =>
    {
        if (!isEdit && (!identity.namespace.trim() || !identity.schemaKey.trim() || !identity.displayName.trim()))
        {
            setError('Namespace, key, and name are required');
            return;
        }
        setSaving(true);
        setError(null);
        try
        {
            if (isEdit && schema)
            {
                const requests = toBindingRequests(bindings);
                if (enforcedScope === FieldScopeKind.PLATFORM)
                    await updatePlatformSchemaBindings(schema, requests);
                else
                    await updateSchemaDraftBindings(schema.id, {bindings: requests});
            }
            else
            {
                const request = {
                    namespace: identity.namespace.trim(),
                    schemaKey: identity.schemaKey.trim(),
                    displayName: identity.displayName.trim(),
                    description: identity.description.trim() || undefined,
                    bindings: toBindingRequests(bindings),
                };
                if (enforcedScope === FieldScopeKind.PLATFORM)
                    await createPlatformSchema(request);
                else
                    await createSchema(request);
            }
            onSaved();
        }
        catch (e: unknown)
        {
            const msg = typeof e === 'object' && e !== null && 'error' in e
                ? String((e as {error: string}).error)
                : 'Failed to save schema';
            setError(msg);
        }
        finally
        {
            setSaving(false);
        }
    };

    return (
        <Dialog open={open}
                onOpenChange={(_, d) => { if (!d.open) onClose(); }}>
            <DialogSurface>
                <DialogBody>
                    <DialogTitle>{isEdit ? `Edit ${schema?.displayName}` : 'New schema'}</DialogTitle>
                    <DialogContent>
                        <div id="schema-editor-body"
                             className={styles.drawerBody}>
                            {!isEdit && (
                                <SchemaIdentityFields identity={identity}
                                                      update={updateIdentity}/>
                            )}
                            <Field label="Fields">
                                <SchemaBindingsEditor definitions={definitions}
                                                      bindings={bindings}
                                                      onChange={setBindings}/>
                            </Field>
                            {error && (
                                <span id="schema-editor-error"
                                      className={styles.errorText}>
                                    {error}
                                </span>
                            )}
                        </div>
                    </DialogContent>
                    <DialogActions>
                        <Button id="schema-editor-save"
                                appearance="primary"
                                shape="circular"
                                disabled={saving}
                                icon={saving ? <Spinner size="tiny"/> : undefined}
                                onClick={handleSave}>
                            {saving ? 'Saving...' : isEdit ? 'Save draft' : 'Create'}
                        </Button>
                        <Button id="schema-editor-cancel"
                                appearance="secondary"
                                shape="circular"
                                disabled={saving}
                                onClick={onClose}>
                            Cancel
                        </Button>
                    </DialogActions>
                </DialogBody>
            </DialogSurface>
        </Dialog>
    );
};

export default SchemaEditorDialog;
