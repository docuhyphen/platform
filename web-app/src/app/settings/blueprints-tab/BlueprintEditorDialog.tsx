import React, {useEffect, useState} from 'react';
import {
    Badge,
    Button,
    Card,
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
    Tab,
    TabList,
    Tag,
    TagGroup,
    Textarea,
} from '@fluentui/react-components';
import {
    AvailableVariablesDto,
    BlueprintConfig,
    BlueprintDefinitionSummaryDto,
    BlueprintDocumentConfig,
    BlueprintScope,
    CreateBlueprintRequest,
    DocumentLibraryEntrySummaryDto,
    DocumentType,
    ImageType,
    UpdateBlueprintRequest,
} from '../../models/models.tsx';
import {createBlueprint, updateBlueprint} from '../../../services/blueprintService.ts';
import {AddIcon, DeleteIcon, DocumentAddIcon, PickFromLibraryIcon} from '../../components/IconBundles.tsx';
import {useExchangeInitiationStyles} from '../../exchange-initiation/ExchangeInitiationStyles.tsx';
import VariableTokenInput from '../../../components/variable-token-input/VariableTokenInput.tsx';
import {getAvailableVariables} from '../../../services/variableService.ts';
import DocumentLibraryPicker from '../../../app/exchange-initiation/components/document-library-picker/DocumentLibraryPicker.tsx';

interface BlueprintEditorDialogProps
{
    open: boolean;
    onClose: () => void;
    onSaved: () => void;
    blueprint?: BlueprintDefinitionSummaryDto;
    scope: BlueprintScope;
}

type EditorTab = 'details' | 'documents' | 'permissions';

const emptyConfig = (): BlueprintConfig => ({
    requestRecipientSignIn: true,
    allowDocumentAddition: false,
    allowDocumentDeletion: false,
    allowDocumentDownload: false,
    allowDocumentUpdate: false,
    allowDocumentUpload: false,
});

const BlueprintEditorDialog: React.FC<BlueprintEditorDialogProps> = (
    {
        open,
        onClose,
        onSaved,
        blueprint,
        scope,
    }) =>
{
    const [activeTab, setActiveTab] = useState<EditorTab>('details');
    const [name, setName] = useState('');
    const [summary, setSummary] = useState('');
    const [description, setDescription] = useState('');
    const [tagInput, setTagInput] = useState('');
    const [tags, setTags] = useState<string[]>([]);
    const [config, setConfig] = useState<BlueprintConfig>(emptyConfig());
    const [documents, setDocuments] = useState<BlueprintDocumentConfig[]>([]);
    const [saving, setSaving] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [availableVariables, setAvailableVariables] = useState<AvailableVariablesDto | null>(null);
    const [pickerOpen, setPickerOpen] = useState(false);
    const styles = useExchangeInitiationStyles();

    useEffect(() =>
    {
        if (!open) return;
        getAvailableVariables().then(setAvailableVariables).catch(() => null);
        if (blueprint)
        {
            setName(blueprint.name);
            setSummary(blueprint.summary ?? '');
            setDescription((blueprint as any).description ?? '');
            setTags(blueprint.generalTags);
            try { setConfig(JSON.parse(blueprint.configJson)); }
            catch { setConfig(emptyConfig()); }
            setDocuments(blueprint.exchangeDocuments ?? []);
        }
        else
        {
            setName('');
            setSummary('');
            setDescription('');
            setTags([]);
            setConfig(emptyConfig());
            setDocuments([]);
        }
        setActiveTab('details');
        setPickerOpen(false);
        setError(null);
    }, [open, blueprint]);

    const addTag = () =>
    {
        const t = tagInput.trim();
        if (t && !tags.includes(t)) setTags(prev => [...prev, t]);
        setTagInput('');
    };

    const addDocument = () =>
        setDocuments(prev => [...prev, {title: ''}]);

    const removeDocument = (index: number) =>
        setDocuments(prev => prev.filter((_, i) => i !== index));

    const updateDoc = (index: number, patch: Partial<BlueprintDocumentConfig>) =>
        setDocuments(prev => prev.map((d, i) => i === index ? {...d, ...patch} : d));

    const setBoolConfig = (key: keyof BlueprintConfig, value: boolean) =>
        setConfig(prev => ({...prev, [key]: value}));

    const handleSave = async () =>
    {
        if (!name.trim())
        {
            setError('Name is required');
            return;
        }
        setSaving(true);
        setError(null);
        try
        {
            const configJson = JSON.stringify(config);
            if (blueprint)
            {
                const req: UpdateBlueprintRequest = {
                    name: name.trim(),
                    summary: summary.trim() || undefined,
                    description: description.trim() || undefined,
                    configJson,
                    exchangeDocuments: documents,
                    generalTags: tags,
                };
                await updateBlueprint(blueprint.id, req);
            }
            else
            {
                const req: CreateBlueprintRequest = {
                    name: name.trim(),
                    summary: summary.trim() || undefined,
                    description: description.trim() || undefined,
                    configJson,
                    exchangeDocuments: documents,
                    generalTags: tags,
                    scope,
                    isActive: true,
                };
                await createBlueprint(req);
            }
            onSaved();
        }
        catch (e: any)
        {
            setError(typeof e === 'string' ? e : 'Failed to save blueprint');
        }
        finally
        {
            setSaving(false);
        }
    };

    return (
        <Dialog open={open} onOpenChange={(_, {open: isOpen}) => { if (!isOpen) onClose(); }}>
            <DialogSurface style={{maxWidth: '600px', width: '100%'}}>
                <DialogBody>
                    <DialogTitle>{blueprint ? 'Edit Blueprint' : 'Create Blueprint'}</DialogTitle>
                    <DialogContent>
                        <TabList
                            selectedValue={activeTab}
                            onTabSelect={(_, d) => setActiveTab(d.value as EditorTab)}
                            style={{marginBottom: '16px'}}
                        >
                            <Tab value="details">Details</Tab>
                            <Tab value="documents">Documents</Tab>
                            <Tab value="permissions">Permissions</Tab>
                        </TabList>

                        {activeTab === 'details' && (
                            <div style={{display: 'flex', flexDirection: 'column', gap: '12px'}}>
                                <Field label="Name" required>
                                    {availableVariables ? (
                                        <VariableTokenInput
                                            value={name}
                                            onChange={setName}
                                            availableVariables={availableVariables}
                                            placeholder="Blueprint name, type {{ to insert a variable"
                                        />
                                    ) : (
                                        <Input value={name} onChange={(_, d) => setName(d.value)} placeholder="Blueprint name"/>
                                    )}
                                </Field>
                                <Field label="Summary">
                                    <Textarea value={summary} onChange={(_, d) => setSummary(d.value)} rows={2} placeholder="Short description"/>
                                </Field>
                                <Field label="Description">
                                    {availableVariables ? (
                                        <VariableTokenInput
                                            value={description}
                                            onChange={setDescription}
                                            availableVariables={availableVariables}
                                            multiline
                                            placeholder="Detailed description"
                                        />
                                    ) : (
                                        <Textarea value={description} onChange={(_, d) => setDescription(d.value)} rows={3} placeholder="Detailed description"/>
                                    )}
                                </Field>
                                <Field label="Initial Share Message">
                                    {availableVariables ? (
                                        <VariableTokenInput
                                            value={config.initialShareMessage ?? ''}
                                            onChange={v => setConfig(prev => ({...prev, initialShareMessage: v}))}
                                            availableVariables={availableVariables}
                                            multiline
                                            placeholder="Message shown to recipient when they open the exchange"
                                        />
                                    ) : (
                                        <Textarea
                                            value={config.initialShareMessage ?? ''}
                                            onChange={(_, d) => setConfig(prev => ({...prev, initialShareMessage: d.value}))}
                                            rows={2}
                                            placeholder="Message shown to recipient"
                                        />
                                    )}
                                </Field>
                                <Field label="Tags">
                                    <div style={{display: 'flex', gap: '8px', alignItems: 'center'}}>
                                        <Input
                                            value={tagInput}
                                            onChange={(_, d) => setTagInput(d.value)}
                                            placeholder="Add tag"
                                            onKeyDown={e => { if (e.key === 'Enter') { e.preventDefault(); addTag(); } }}
                                        />

                                        <Button
                                            id="doc-lib-add-tag-btn"
                                            shape="circular"
                                            appearance="subtle"
                                            size={"medium"}
                                            icon={<AddIcon/>}
                                            onClick={addTag}
                                        />
                                    </div>
                                    {tags.length > 0 && (
                                        <TagGroup onDismiss={(_, {value}) => setTags(prev => prev.filter(t => t !== value))} style={{marginTop: '8px'}}>
                                            {tags.map(tag => (
                                                <Tag key={tag} value={tag} dismissible>{tag}</Tag>
                                            ))}
                                        </TagGroup>
                                    )}
                                </Field>
                            </div>
                        )}

                        {activeTab === 'documents' && !pickerOpen && (
                            <div className={styles.exchangeDocumentsTabContent}>
                                {documents.map((doc, i) => (
                                    <Card
                                        key={i}
                                        className={styles.shadingExchangeDocumentCard}
                                    >
                                        <div>
                                            <div className={styles.dialogTitle1}>
                                                <Field className={styles.sharingDetailsInput}>
                                                    {availableVariables ? (
                                                        <VariableTokenInput
                                                            value={doc.title}
                                                            onChange={v => updateDoc(i, {title: v})}
                                                            availableVariables={availableVariables}
                                                            placeholder="Document name, type {{ to insert a variable"
                                                            disabled={!!doc.libraryDocumentId}
                                                        />
                                                    ) : (
                                                        <Input
                                                            id={`bp-doc-title-${i}`}
                                                            type="text"
                                                            size="small"
                                                            value={doc.title}
                                                            onChange={(_, d) => updateDoc(i, {title: d.value})}
                                                            placeholder="Document name"
                                                            disabled={!!doc.libraryDocumentId}
                                                        />
                                                    )}
                                                </Field>
                                                <Button
                                                    id={`bp-doc-delete-${i}`}
                                                    icon={<DeleteIcon className={styles.iconDeleteFilled}/>}
                                                    appearance="subtle"
                                                    shape="circular"
                                                    onClick={() => removeDocument(i)}
                                                />
                                            </div>
                                            {doc.libraryDocumentId && (
                                                <div style={{display: 'flex', alignItems: 'center', gap: '8px', marginTop: '6px'}}>
                                                    <Badge
                                                        id={`bp-doc-linked-badge-${i}`}
                                                        appearance="tint"
                                                        color="success"
                                                        size="small"
                                                    >
                                                        Linked from library
                                                    </Badge>
                                                    <Button
                                                        id={`bp-doc-unlink-${i}`}
                                                        size="small"
                                                        appearance="subtle"
                                                        shape="circular"
                                                        onClick={() => updateDoc(i, {libraryDocumentId: undefined})}
                                                    >
                                                        Unlink
                                                    </Button>
                                                </div>
                                            )}
                                            <div className={styles.exchangeDocumentsRestriction}>
                                                <div className={styles.exchangeDocumentsRestrictionField}>
                                                    <Field label="">
                                                        <Switch
                                                            label="Restrict upload type"
                                                            checked={doc.restrictType ?? false}
                                                            disabled={!!doc.libraryDocumentId}
                                                            onChange={(_, d) => updateDoc(i, {restrictType: d.checked, restrictedType: d.checked ? doc.restrictedType : undefined})}
                                                        />
                                                    </Field>
                                                    <Dropdown
                                                        className={styles.exchangeDocumentsDropdown}
                                                        disabled={!doc.restrictType || !!doc.libraryDocumentId}
                                                        appearance="underline"
                                                        value={doc.restrictedType ?? ''}
                                                        size="small"
                                                        placeholder="Select allowed upload type"
                                                        onOptionSelect={(_, d) => updateDoc(i, {restrictedType: d.optionValue as string})}
                                                    >
                                                        <OptionGroup label="Documents">
                                                            {Object.values(DocumentType).map(opt => (
                                                                <Option
                                                                    key={opt}
                                                                    value={opt}
                                                                >
                                                                    {opt}
                                                                </Option>
                                                            ))}
                                                        </OptionGroup>
                                                        <OptionGroup label="Images">
                                                            {Object.values(ImageType).map(opt => (
                                                                <Option
                                                                    key={opt}
                                                                    value={opt}
                                                                >
                                                                    {opt}
                                                                </Option>
                                                            ))}
                                                        </OptionGroup>
                                                    </Dropdown>
                                                </div>
                                                <Checkbox
                                                    label="Required"
                                                    checked={doc.required ?? false}
                                                    disabled={!!doc.libraryDocumentId}
                                                    onChange={(_, d) => updateDoc(i, {required: !!d.checked})}
                                                />
                                            </div>
                                        </div>
                                    </Card>
                                ))}
                                <div className={styles.addDocumentButtonContainer}>
                                    <Button
                                        id="bp-doc-add-btn"
                                        onClick={addDocument}
                                        shape="circular"
                                        icon={<DocumentAddIcon/>}
                                        appearance="subtle"
                                    >
                                        Add Document
                                    </Button>
                                    <Button
                                        id="bp-doc-pick-library-btn"
                                        onClick={() => setPickerOpen(true)}
                                        shape="circular"
                                        icon={<PickFromLibraryIcon/>}
                                        appearance="subtle"
                                    >
                                        Pick from Library
                                    </Button>
                                </div>
                            </div>
                        )}

                        {activeTab === 'documents' && pickerOpen && (
                            <DocumentLibraryPicker
                                onSelect={(entry: DocumentLibraryEntrySummaryDto) =>
                                {
                                    setDocuments(prev => [
                                        ...prev,
                                        {
                                            title: entry.title,
                                            libraryDocumentId: entry.id,
                                            restrictType: entry.restrictType ?? false,
                                            restrictedType: entry.restrictedType,
                                            required: entry.required ?? false,
                                        },
                                    ]);
                                    setPickerOpen(false);
                                }}
                                onCancel={() => setPickerOpen(false)}
                            />
                        )}

                        {activeTab === 'permissions' && (
                            <div style={{display: 'flex', flexDirection: 'column', gap: '8px'}}>
                                <Checkbox
                                    label="Require recipient sign-in"
                                    checked={config.requestRecipientSignIn ?? false}
                                    onChange={(_, d) => setBoolConfig('requestRecipientSignIn', !!d.checked)}
                                />
                                <Checkbox
                                    label="Allow document addition"
                                    checked={config.allowDocumentAddition ?? false}
                                    onChange={(_, d) => setBoolConfig('allowDocumentAddition', !!d.checked)}
                                />
                                <Checkbox
                                    label="Allow document deletion"
                                    checked={config.allowDocumentDeletion ?? false}
                                    onChange={(_, d) => setBoolConfig('allowDocumentDeletion', !!d.checked)}
                                />
                                <Checkbox
                                    label="Allow document download"
                                    checked={config.allowDocumentDownload ?? false}
                                    onChange={(_, d) => setBoolConfig('allowDocumentDownload', !!d.checked)}
                                />
                                <Checkbox
                                    label="Allow document update"
                                    checked={config.allowDocumentUpdate ?? false}
                                    onChange={(_, d) => setBoolConfig('allowDocumentUpdate', !!d.checked)}
                                />
                                <Checkbox
                                    label="Allow document upload"
                                    checked={config.allowDocumentUpload ?? false}
                                    onChange={(_, d) => setBoolConfig('allowDocumentUpload', !!d.checked)}
                                />
                            </div>
                        )}

                        {error && (
                            <span style={{color: 'var(--colorPaletteRedForeground1)', fontSize: '12px', marginTop: '8px', display: 'block'}}>
                                {error}
                            </span>
                        )}
                    </DialogContent>
                    <DialogActions>
                        <Button appearance="primary" shape="circular" onClick={handleSave} disabled={saving}>
                            {saving ? <><Spinner size="tiny"/> Saving…</> : (blueprint ? 'Save Changes' : 'Create Blueprint')}
                        </Button>
                        <Button shape="circular" onClick={onClose} disabled={saving}>Cancel</Button>
                    </DialogActions>
                </DialogBody>
            </DialogSurface>
        </Dialog>
    );
};

export default BlueprintEditorDialog;
