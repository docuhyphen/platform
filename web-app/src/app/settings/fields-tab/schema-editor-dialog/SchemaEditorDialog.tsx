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
import {FieldDefinitionDto, FieldScopeKind, SchemaDefinitionDto} from '../../../models/models';
import SchemaBindingsEditor from '../schema-bindings-editor/SchemaBindingsEditor';
import SchemaIdentityFields from '../schema-identity-fields/SchemaIdentityFields';
import {useSchemaEditorForm} from './useSchemaEditorForm';
import {useSchemaEditorDialogStyles} from './SchemaEditorDialogStyles';

interface Props
{
    open: boolean;
    onClose: () => void;
    onSaved: () => void;
    definitions: FieldDefinitionDto[];
    schema?: SchemaDefinitionDto;
    enforcedScope?: FieldScopeKind;
}

const SchemaEditorDialog = ({open, onClose, onSaved, definitions, schema, enforcedScope}: Props) =>
{
    const styles = useSchemaEditorDialogStyles();
    const isEdit = !!schema;
    const form = useSchemaEditorForm(open, schema, enforcedScope, onSaved);

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
                                <SchemaIdentityFields identity={form.identity}
                                                      update={form.updateIdentity}/>
                            )}
                            <Field id={"schema-editor-fields-field"}
                                   label="Fields">
                                <SchemaBindingsEditor definitions={definitions}
                                                      bindings={form.bindings}
                                                      onChange={form.setBindings}/>
                            </Field>
                            {form.error && (
                                <span id="schema-editor-error"
                                      className={styles.errorText}>
                                    {form.error}
                                </span>
                            )}
                        </div>
                    </DialogContent>
                    <DialogActions id={"schema-editor-dialog-actions"}>
                        <Button id="schema-editor-save"
                                appearance="primary"
                                shape="circular"
                                disabled={form.saving}
                                icon={form.saving ? <Spinner id={"schema-editor-spinner"}
                                                             size="tiny"/> : undefined}
                                onClick={form.save}>
                            {form.saving ? 'Saving...' : isEdit ? 'Save draft' : 'Create'}
                        </Button>
                        <Button id="schema-editor-cancel"
                                appearance="secondary"
                                shape="circular"
                                disabled={form.saving}
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
