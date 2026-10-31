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
import {
    FieldDefinitionDto,
    FieldScopeKind,
    ResponseError,
    SchemaDefinitionDto,
    SchemaTargetResourceType,
} from '../../models/models';
import {useFieldsTabStyles} from './FieldsTabStyles';
import SchemaBindingsEditor from './SchemaBindingsEditor';
import {BindingDraft, toBindingDrafts} from './schemaBindingDrafts';
import {saveSchema} from './schemaEditorSave';
import SchemaIdentityFields, {SchemaIdentity} from './SchemaIdentityFields';
import {schemaTargetOf} from './schemaTarget';

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
    ({
        namespace: '',
        schemaKey: '',
        displayName: '',
        description: '',
        targetResourceType: SchemaTargetResourceType.EXCHANGE
    });

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
                targetResourceType: schemaTargetOf(schema.targetResourceType),
            });
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

    return (
        <Dialog open={open}
                onOpenChange={(_, d) => {
                    if (!d.open) onClose();
                }}>
            <DialogSurface id={"schema-editor-dialog-surface"}>
                <DialogBody id={"schema-editor-dialog-body"}>
                    <DialogTitle
                        id={"schema-editor-schema-dialog-title"}>{isEdit ? `Edit ${schema?.displayName}` : 'New schema'}</DialogTitle>
                    <DialogContent id={"schema-editor-dialog-content"}>
                        <div id="schema-editor-body"
                             className={styles.drawerBody}>
                            {!isEdit && (
                                <SchemaIdentityFields identity={identity}
                                                      update={updateIdentity}/>
                            )}
                            <Field id={"schema-editor-fields-field"}
                                   label="Fields">
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
                    <DialogActions id={"schema-editor-dialog-actions"}>
                        <Button id="schema-editor-save"
                                appearance="primary"
                                shape="circular"
                                disabled={saving}
                                icon={saving ? <Spinner id={"schema-editor-spinner"}
                                                        size="tiny"/> : undefined}
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
