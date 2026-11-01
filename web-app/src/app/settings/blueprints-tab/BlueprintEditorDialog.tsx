import React from 'react';
import {Checkbox, Dialog, DialogBody, DialogContent, DialogSurface, DialogTitle} from '@fluentui/react-components';
import {BlueprintDefinitionSummaryDto, BlueprintScope, DocumentLibraryEntrySummaryDto} from '../../models/models.tsx';
import {useBlueprintEditorStyles} from './BlueprintsTabStyles.tsx';
import DocumentLibraryPicker
    from '../../../app/exchange-initiation/components/document-library-picker/DocumentLibraryPicker.tsx';
import BlueprintBusinessFieldsTab from './blueprint-business-fields-tab/BlueprintBusinessFieldsTab.tsx';
import BlueprintInformationRequestTab from './blueprint-information-request-tab/BlueprintInformationRequestTab.tsx';
import BlueprintEditorTabList from './blueprint-editor-tab-list/BlueprintEditorTabList.tsx';
import BlueprintDetailsTab from './blueprint-details-tab/BlueprintDetailsTab.tsx';
import BlueprintDocumentsTab from './blueprint-documents-tab/BlueprintDocumentsTab.tsx';
import BlueprintPermissionsTab from './blueprint-permissions-tab/BlueprintPermissionsTab.tsx';
import BlueprintEditorActions from './blueprint-editor-actions/BlueprintEditorActions.tsx';
import {useBlueprintEditorForm} from './useBlueprintEditorForm.ts';
import {libraryEntriesToDocuments} from './blueprintEditorRequests.ts';

interface BlueprintEditorDialogProps
{
    open: boolean;
    onClose: () => void;
    onSaved: () => void;
    blueprint?: BlueprintDefinitionSummaryDto;
    scope: BlueprintScope;
    enforcedScope?: BlueprintScope;
}

const BlueprintEditorDialog: React.FC<BlueprintEditorDialogProps> = (
    {
        open,
        onClose,
        onSaved,
        blueprint,
        scope,
        enforcedScope,
    }) =>
{
    const editorStyles = useBlueprintEditorStyles();
    const form = useBlueprintEditorForm({open, blueprint, scope, enforcedScope, onSaved});
    const {effectiveScope, activeTab, values, pickerOpen, setPickerOpen} = form;

    return (
        <Dialog open={open}
                onOpenChange={(_, {open: isOpen}) =>
                {
                    if (!isOpen) onClose();
                }}>
            <DialogSurface id={"blueprint-editor-dialog"}
                           className={editorStyles.dialogSurface}>
                <DialogBody id={"blueprint-editor-dialog-body"}>
                    <DialogTitle
                        id={"blueprint-editor-dialog-title"}
                        action={effectiveScope !== 'PERSONAL' ? (
                            <Checkbox
                                id={"checkbox-allow-edit-on-exchange-start"}
                                label="Allow edit on Exchange start"
                                checked={values.config.allowEditOnExchangeStart ?? false}
                                onChange={(_, d) => form.setBoolConfig('allowEditOnExchangeStart', !!d.checked)}
                            />
                        ) : undefined}
                    >
                        {blueprint ? 'Edit Blueprint' : 'Create Blueprint'}
                    </DialogTitle>
                    <DialogContent id={"blueprint-editor-dialog-content"}>
                        <BlueprintEditorTabList activeTab={activeTab}
                                                onTabSelect={form.setActiveTab}/>

                        {activeTab === 'details' && <BlueprintDetailsTab form={form}/>}

                        {activeTab === 'documents' && !pickerOpen && <BlueprintDocumentsTab form={form}/>}

                        {activeTab === 'documents' && pickerOpen && (
                            <DocumentLibraryPicker
                                enforcedScope={enforcedScope}
                                onSelect={(entries: DocumentLibraryEntrySummaryDto[]) =>
                                {
                                    form.appendDocuments(libraryEntriesToDocuments(entries));
                                    setPickerOpen(false);
                                }}
                                onBack={() => setPickerOpen(false)}
                            />
                        )}

                        {activeTab === 'fields' && (
                            <BlueprintBusinessFieldsTab
                                initialSchemaDefinitionId={values.schemaDefinitionId}
                                initialFieldDefaults={values.fieldDefaults}
                                enforcedScope={enforcedScope}
                                onChange={(id, defaults) => form.update({schemaDefinitionId: id, fieldDefaults: defaults})}
                            />
                        )}

                        {activeTab === 'informationRequest' && (
                            <BlueprintInformationRequestTab scope={effectiveScope}
                                                            templateVersionId={values.templateVersionId}
                                                            pinnedVersion={values.templateVersionId === blueprint?.informationRequestTemplateVersionId
                                                                ? blueprint?.informationRequestTemplateVersion
                                                                : undefined}
                                                            onChange={templateVersionId => form.update({templateVersionId})}/>
                        )}

                        {activeTab === 'permissions' && (
                            <BlueprintPermissionsTab config={values.config}
                                                     onToggle={form.setBoolConfig}/>
                        )}

                        {form.error && (
                            <span id={"blueprint-editor-error"}
                                  className={editorStyles.errorSpan}>
                                {form.error}
                            </span>
                        )}
                    </DialogContent>
                    <BlueprintEditorActions saving={form.saving}
                                            isEdit={!!blueprint}
                                            onSave={form.save}
                                            onCancel={onClose}/>
                </DialogBody>
            </DialogSurface>
        </Dialog>
    );
};

export default BlueprintEditorDialog;
