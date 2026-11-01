import {
    Dialog,
    DialogBody,
    DialogContent,
    DialogSurface,
    DialogTitle,
    Field,
    Textarea,
} from '@fluentui/react-components';
import {DocumentLibraryEntrySummaryDto, DocumentLibraryScope} from '../../../models/models.tsx';
import DocumentLibraryDialogActions from '../document-library-dialog-actions/DocumentLibraryDialogActions.tsx';
import DocumentLibraryTitleField from './document-library-title-field/DocumentLibraryTitleField.tsx';
import DocumentLibraryRestrictionFields from './document-library-restriction-fields/DocumentLibraryRestrictionFields.tsx';
import DocumentLibraryTagEditor from './document-library-tag-editor/DocumentLibraryTagEditor.tsx';
import {useDocumentLibraryEditorForm} from './useDocumentLibraryEditorForm.ts';
import {useDocumentLibraryEditorDialogStyles} from './DocumentLibraryEditorDialogStyles.tsx';

interface Props
{
    open: boolean;
    onClose: () => void;
    onSaved: () => void;
    entry?: DocumentLibraryEntrySummaryDto;
    scope: DocumentLibraryScope;
    enforcedScope?: DocumentLibraryScope;
}

const actionIds = {
    actions: 'document-library-editor-dialog-dialog-actions-1',
    container: 'document-library-editor-dialog-div-4',
    confirm: 'doc-editor-save-btn',
    cancel: 'doc-editor-cancel-btn',
    spinner: 'document-library-editor-dialog-spinner-1',
};

const DocumentLibraryEditorDialog = ({open, onClose, onSaved, entry, scope, enforcedScope}: Props) =>
{
    const styles = useDocumentLibraryEditorDialogStyles();
    const form = useDocumentLibraryEditorForm(open, {entry, scope, enforcedScope}, onSaved);

    return (
        <Dialog open={open}
                onOpenChange={(_, d) =>
                {
                    if (!d.open) onClose();
                }}>
            <DialogSurface id={"document-library-editor-dialog-dialog-surface-1"}>
                <DialogBody id={"document-library-editor-dialog-dialog-body-1"}>
                    <DialogTitle id={"document-library-editor-dialog-dialog-title-1"}>{entry ? 'Edit Document' : 'New Document'}</DialogTitle>
                    <DialogContent id={"document-library-editor-dialog-dialog-content-1"}>
                        <div id="doc-editor-dialog-body"
                             className={styles.dialogBody}>
                            <DocumentLibraryTitleField title={form.title}
                                                       onChange={form.setTitle}
                                                       availableVariables={form.availableVariables}
                                                       error={form.error}/>
                            <Field id={"document-library-editor-dialog-field-2"}
                                   label="Description"
                                   hint="For library reference only, not shown during Exchange creation">
                                <Textarea id="doc-editor-description"
                                          value={form.description}
                                          onChange={(_, d) => form.setDescription(d.value)}
                                          placeholder="Describe this document entry for library purposes"
                                          rows={3}/>
                            </Field>
                            <DocumentLibraryRestrictionFields restrictType={form.restrictType}
                                                              restrictedType={form.restrictedType}
                                                              required={form.required}
                                                              onRestrictTypeChange={form.setRestrictType}
                                                              onRestrictedTypeChange={form.setRestrictedType}
                                                              onRequiredChange={form.setRequired}/>
                            <DocumentLibraryTagEditor tags={form.tags}
                                                      tagInput={form.tagInput}
                                                      onTagInputChange={form.setTagInput}
                                                      onAddTag={form.addTag}
                                                      onRemoveTag={form.removeTag}/>
                            {form.error && form.title.trim() && (
                                <span id="doc-editor-error"
                                      className={styles.errorText}>
                                    {form.error}
                                </span>
                            )}
                        </div>
                    </DialogContent>
                    <DocumentLibraryDialogActions ids={actionIds}
                                                  confirmLabel="Save"
                                                  pendingLabel="Saving..."
                                                  pending={form.saving}
                                                  onConfirm={form.handleSave}
                                                  onCancel={onClose}/>
                </DialogBody>
            </DialogSurface>
        </Dialog>
    );
};

export default DocumentLibraryEditorDialog;
