import React, {useEffect, useState} from 'react';
import {
    Button,
    Checkbox,
    Dialog,
    DialogActions,
    DialogBody,
    DialogContent,
    DialogSurface,
    DialogTitle,
    Dropdown,
    Field,
    Input,
    Option,
    OptionGroup,
    Spinner,
    Switch,
    Tag,
    Textarea,
} from '@fluentui/react-components';
import {useDocumentsTabStyles} from './DocumentLibraryTabStyles.tsx';
import {useExchangeInitiationStyles} from '../../exchange-initiation/ExchangeInitiationStyles.tsx';
import {
    AvailableVariablesDto,
    CreateDocumentLibraryEntryRequest,
    DocumentLibraryEntrySummaryDto,
    DocumentLibraryScope,
} from '../../models/models.tsx';
import {
    createDocumentLibraryEntry,
    updateDocumentLibraryEntry,
} from '../../../services/documentLibraryService.ts';
import VariableTokenInput from '../../../components/variable-token-input/VariableTokenInput.tsx';
import {getAvailableVariables} from '../../../services/variableService.ts';
import {AddIcon} from "../../components/IconBundles.tsx";

interface Props
{
    open: boolean;
    onClose: () => void;
    onSaved: () => void;
    entry?: DocumentLibraryEntrySummaryDto;
    scope: DocumentLibraryScope;
}

const DocumentLibraryEditorDialog = ({open, onClose, onSaved, entry, scope}: Props) =>
{
    const styles = useDocumentsTabStyles();
    const exchangeStyles = useExchangeInitiationStyles();
    const [title, setTitle] = useState('');
    const [description, setDescription] = useState('');
    const [tagInput, setTagInput] = useState('');
    const [tags, setTags] = useState<string[]>([]);
    const [restrictType, setRestrictType] = useState(false);
    const [restrictedType, setRestrictedType] = useState<string | undefined>(undefined);
    const [required, setRequired] = useState(false);
    const [availableVariables, setAvailableVariables] = useState<AvailableVariablesDto | null>(null);
    const [saving, setSaving] = useState(false);
    const [error, setError] = useState<string | null>(null);

    useEffect(() =>
    {
        if (open)
        {
            setTitle(entry?.title ?? '');
            setDescription(entry?.description ?? '');
            setTags(entry?.generalTags ?? []);
            setTagInput('');
            setRestrictType(entry?.restrictType ?? false);
            setRestrictedType(entry?.restrictedType);
            setRequired(entry?.required ?? false);
            setError(null);
            getAvailableVariables().then(setAvailableVariables).catch(() => setAvailableVariables(null));
        }
    }, [open, entry]);

    const addTag = () =>
    {
        const trimmed = tagInput.trim();
        if (trimmed && !tags.includes(trimmed)) setTags(prev => [...prev, trimmed]);
        setTagInput('');
    };

    const handleSave = async () =>
    {
        if (!title.trim())
        {
            setError('Title is required');
            return;
        }
        setSaving(true);
        setError(null);
        try
        {
            if (entry)
            {
                await updateDocumentLibraryEntry(entry.id, {
                    title: title.trim(),
                    description: description.trim() || undefined,
                    generalTags: tags,
                    restrictType,
                    restrictedType: restrictType ? restrictedType : undefined,
                    required,
                });
            }
            else
            {
                const request: CreateDocumentLibraryEntryRequest = {
                    title: title.trim(),
                    description: description.trim() || undefined,
                    generalTags: tags,
                    scope,
                    restrictType,
                    restrictedType: restrictType ? restrictedType : undefined,
                    required,
                };
                await createDocumentLibraryEntry(request);
            }
            onSaved();
        }
        catch
        {
            setError('Failed to save. Please try again.');
        }
        finally
        {
            setSaving(false);
        }
    };

    return (
        <Dialog
            open={open}
            onOpenChange={(_, d) => { if (!d.open) onClose(); }}
        >
            <DialogSurface>
                <DialogBody>
                    <DialogTitle>{entry ? 'Edit Document' : 'New Document'}</DialogTitle>
                    <DialogContent>
                        <div
                            id="doc-editor-dialog-body"
                            className={styles.dialogBody}
                        >
                            <Field
                                label="Title"
                                required
                                validationState={error && !title.trim() ? 'error' : 'none'}
                                validationMessage={error && !title.trim() ? error : undefined}
                            >
                                {availableVariables !== null ? (
                                    <VariableTokenInput
                                        value={title}
                                        onChange={setTitle}
                                        availableVariables={availableVariables}
                                        placeholder="Document title"
                                    />
                                ) : (
                                    <Input
                                        id="doc-editor-title"
                                        value={title}
                                        onChange={(_, d) => setTitle(d.value)}
                                        placeholder="Document title"
                                    />
                                )}
                            </Field>
                            <Field
                                label="Description"
                                hint="For library reference only, not shown during exchange creation"
                            >
                                <Textarea
                                    id="doc-editor-description"
                                    value={description}
                                    onChange={(_, d) => setDescription(d.value)}
                                    placeholder="Describe this document entry for library purposes"
                                    rows={3}
                                />
                            </Field>
                            <div className={exchangeStyles.exchangeDocumentsRestriction}>
                                <div className={exchangeStyles.exchangeDocumentsRestrictionField}>
                                    <Field label="">
                                        <Switch
                                            id="doc-lib-restrict-type-switch"
                                            checked={restrictType}
                                            label="Restrict upload type"
                                            onChange={(_, d) => { setRestrictType(d.checked); if (!d.checked) setRestrictedType(undefined); }}
                                        />
                                    </Field>
                                    <Dropdown
                                        id="doc-lib-restricted-type-dropdown"
                                        className={exchangeStyles.exchangeDocumentsDropdown}
                                        disabled={!restrictType}
                                        value={restrictedType ?? ''}
                                        selectedOptions={restrictedType ? [restrictedType] : []}
                                        onOptionSelect={(_, d) => setRestrictedType(d.optionValue as string)}
                                        placeholder="Select allowed type..."
                                    >
                                        <OptionGroup label="Documents">
                                            <Option value="PDF">PDF</Option>
                                            <Option value="DOCX">DOCX</Option>
                                            <Option value="DOC">DOC</Option>
                                            <Option value="XLSX">XLSX</Option>
                                            <Option value="XLS">XLS</Option>
                                            <Option value="PPTX">PPTX</Option>
                                            <Option value="PPT">PPT</Option>
                                        </OptionGroup>
                                        <OptionGroup label="Images">
                                            <Option value="PNG">PNG</Option>
                                            <Option value="JPG">JPG</Option>
                                        </OptionGroup>
                                    </Dropdown>
                                </div>
                                <Checkbox
                                    id="doc-lib-required-checkbox"
                                    checked={required}
                                    onChange={(_, d) => setRequired(!!d.checked)}
                                    label="Required"
                                />
                            </div>
                            <Field label="Tags">
                                <div className={styles.tagInput}>
                                    {tags.map(tag => (
                                        <Tag key={tag}
                                             size="small"
                                             dismissible
                                             shape={"circular"}
                                             onClick={() => setTags(prev => prev.filter(t => t !== tag))}>{tag}</Tag>
                                    ))}
                                    <Input
                                        size="small"
                                        appearance="underline"
                                        placeholder="Add tag, press Enter"
                                        value={tagInput}
                                        onChange={(_, d) => setTagInput(d.value)}
                                        onKeyDown={(e) => { if (e.key === 'Enter') { e.preventDefault(); addTag(); } }}
                                        style={{border: 'none', flexGrow: 1, minWidth: '8rem'}}
                                    />
                                    <Button
                                        shape="circular"
                                        appearance="subtle"
                                        size="medium"
                                        icon={<AddIcon/>}
                                        onClick={addTag}
                                    />
                                </div>
                            </Field>
                            {error && title.trim() && (
                                <span
                                    id="doc-editor-error"
                                    style={{color: 'var(--colorPaletteRedForeground1)'}}
                                >
                                    {error}
                                </span>
                            )}
                        </div>
                    </DialogContent>
                    <DialogActions>
                        <div className={styles.dialogActions}>
                            <Button
                                id="doc-editor-cancel-btn"
                                appearance="secondary"
                                shape="circular"
                                onClick={onClose}
                                disabled={saving}
                            >
                                Cancel
                            </Button>
                            <Button
                                id="doc-editor-save-btn"
                                appearance="primary"
                                shape="circular"
                                onClick={handleSave}
                                disabled={saving}
                                icon={saving ? <Spinner size="tiny"/> : undefined}
                            >
                                {saving ? 'Saving...' : 'Save'}
                            </Button>
                        </div>
                    </DialogActions>
                </DialogBody>
            </DialogSurface>
        </Dialog>
    );
};

export default DocumentLibraryEditorDialog;
