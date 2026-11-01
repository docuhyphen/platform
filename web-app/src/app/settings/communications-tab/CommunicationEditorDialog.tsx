import React from 'react';
import {
    Dialog,
    DialogBody,
    DialogContent,
    DialogSurface,
    DialogTitle,
} from '@fluentui/react-components';
import {CommunicationDto, CommunicationScope, CommunicationSummaryDto} from '../../models/models';
import {useCommunicationEditorDialogStyles} from './CommunicationEditorDialogStyles';
import {useCommunicationEditorForm} from './useCommunicationEditorForm';
import CommunicationEditorTabList from './communication-editor-tab-list/CommunicationEditorTabList';
import CommunicationDetailsTab from './communication-details-tab/CommunicationDetailsTab';
import CommunicationPreviewTab from './communication-preview-tab/CommunicationPreviewTab';
import CommunicationEditorActions from './communication-editor-actions/CommunicationEditorActions';

interface Props
{
    open: boolean;
    onClose: () => void;
    onSaved: () => void;
    communication?: CommunicationSummaryDto | CommunicationDto;
    scope: CommunicationScope;
    enforcedScope?: CommunicationScope;
    createAsTemplate?: boolean;
}

const CommunicationEditorDialog: React.FC<Props> = (
    {
        open,
        onClose,
        onSaved,
        communication,
        scope,
        enforcedScope,
        createAsTemplate = false,
    },
) =>
{
    const styles = useCommunicationEditorDialogStyles();
    const form = useCommunicationEditorForm({open, communication, scope, enforcedScope, createAsTemplate, onSaved});
    const {preview} = form;

    return (
        <Dialog open={open}
                onOpenChange={(_, {open: isOpen}) => { if (!isOpen) onClose(); }}>
            <DialogSurface id={"communication-editor-dialog-dialog-surface-1"}
                           className={styles.dialogSurface}>
                <DialogBody id={"communication-editor-dialog-dialog-body-1"}>
                    <DialogTitle id={"communication-editor-dialog-dialog-title-1"}>{communication ? 'Edit Communication' : 'Create Communication'}</DialogTitle>
                    <DialogContent id={"communication-editor-dialog-dialog-content-1"}>
                        <CommunicationEditorTabList
                            activeTab={form.activeTab}
                            previewEnabled={!!communication?.id}
                            onTabChange={form.setActiveTab}
                        />

                        {form.activeTab === 'details' && (
                            <CommunicationDetailsTab
                                values={form.values}
                                onValueChange={form.updateValue}
                                availableVariables={form.availableVariables}
                                variableHints={form.variableHints}
                                onInsertHint={form.appendToBody}
                                tagInput={form.tagInput}
                                onTagInputChange={form.setTagInput}
                                onAddTag={form.addTag}
                                onRemoveTag={form.removeTag}
                            />
                        )}

                        {form.activeTab === 'preview' && (
                            <CommunicationPreviewTab
                                previewVars={preview.previewVars}
                                onPreviewVarsChange={preview.setPreviewVars}
                                previewing={preview.previewing}
                                previewError={preview.previewError}
                                previewResult={preview.previewResult}
                                onRender={preview.renderPreview}
                            />
                        )}

                        {form.error && (
                            <span id={"communication-editor-dialog-span-1"}
                                  className={styles.errorSpan}>
                                {form.error}
                            </span>
                        )}
                    </DialogContent>
                    <CommunicationEditorActions
                        showSave={form.activeTab === 'details'}
                        saving={form.saving}
                        isEdit={!!communication}
                        onSave={form.save}
                        onCancel={onClose}
                    />
                </DialogBody>
            </DialogSurface>
        </Dialog>
    );
};

export default CommunicationEditorDialog;
