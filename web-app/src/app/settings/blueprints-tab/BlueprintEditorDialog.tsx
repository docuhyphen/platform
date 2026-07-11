import React, {useEffect, useState} from 'react';
import {
    Accordion,
    AccordionHeader,
    AccordionItem,
    AccordionPanel,
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
    Textarea,
} from '@fluentui/react-components';
import {
    AvailableVariablesDto,
    BlueprintConfig,
    BlueprintDefinitionDto,
    BlueprintDefinitionSummaryDto,
    BlueprintDocumentConfig,
    BlueprintFieldDefaultConfig,
    BlueprintScope,
    CreateBlueprintRequest,
    DocumentLibraryEntrySummaryDto,
    DocumentType,
    ImageType,
    UpdateBlueprintRequest,
} from '../../models/models.tsx';
import {createBlueprint, updateBlueprint} from '../../../services/blueprintService.ts';
import {
    AddIcon,
    DeleteIcon,
    DocumentAddIcon,
    LinkDismissIcon,
    PickFromLibraryIcon
} from '../../components/IconBundles.tsx';
import {useExchangeInitiationStyles} from '../../exchange-initiation/ExchangeInitiationStyles.tsx';
import {useBlueprintEditorStyles} from './BlueprintsTabStyles.tsx';
import VariableTokenInput from '../../../components/variable-token-input/VariableTokenInput.tsx';
import {getAvailableVariables} from '../../../services/variableService.ts';
import DocumentLibraryPicker from '../../../app/exchange-initiation/components/document-library-picker/DocumentLibraryPicker.tsx';
import BlueprintBusinessFieldsTab from './blueprint-business-fields-tab/BlueprintBusinessFieldsTab.tsx';

interface BlueprintEditorDialogProps
{
    open: boolean;
    onClose: () => void;
    onSaved: () => void;
    blueprint?: BlueprintDefinitionSummaryDto;
    scope: BlueprintScope;
}

type EditorTab = 'details' | 'documents' | 'permissions' | 'fields';

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
    const [schemaDefinitionId, setSchemaDefinitionId] = useState<string | undefined>(undefined);
    const [fieldDefaults, setFieldDefaults] = useState<BlueprintFieldDefaultConfig[]>([]);
    const [saving, setSaving] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [availableVariables, setAvailableVariables] = useState<AvailableVariablesDto | null>(null);
    const [pickerOpen, setPickerOpen] = useState(false);
    const styles = useExchangeInitiationStyles();
    const editorStyles = useBlueprintEditorStyles();

    useEffect(() =>
    {
        if (!open) return;
        getAvailableVariables().then(setAvailableVariables).catch(() => null);
        if (blueprint)
        {
            setName(blueprint.name);
            setSummary(blueprint.summary ?? '');
            setDescription((blueprint as BlueprintDefinitionDto).description ?? '');
            setTags(blueprint.generalTags);
            try { setConfig(JSON.parse(blueprint.configJson)); }
            catch { setConfig(emptyConfig()); }
            setDocuments(blueprint.exchangeDocuments ?? []);
            setSchemaDefinitionId(blueprint.schemaDefinitionId);
            setFieldDefaults(blueprint.fieldDefaults ?? []);
        }
        else
        {
            setName('');
            setSummary('');
            setDescription('');
            setTags([]);
            setConfig(emptyConfig());
            setDocuments([]);
            setSchemaDefinitionId(undefined);
            setFieldDefaults([]);
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
                    schemaDefinitionId,
                    fieldDefaults,
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
                    schemaDefinitionId,
                    fieldDefaults: fieldDefaults.length > 0 ? fieldDefaults : undefined,
                };
                await createBlueprint(req);
            }
            onSaved();
        }
        catch (e: unknown)
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
            <DialogSurface className={editorStyles.dialogSurface}>
                <DialogBody>
                    <DialogTitle
                        action={scope !== 'PERSONAL' ? (
                            <Checkbox
                                id={"checkbox-allow-edit-on-exchange-start"}
                                label="Allow edit on Exchange start"
                                checked={config.allowEditOnExchangeStart ?? false}
                                onChange={(_, d) => setBoolConfig('allowEditOnExchangeStart', !!d.checked)}
                            />
                        ) : undefined}
                    >
                        {blueprint ? 'Edit Blueprint' : 'Create Blueprint'}
                    </DialogTitle>
                    <DialogContent>
                        <TabList
                            selectedValue={activeTab}
                            onTabSelect={(_, d) => setActiveTab(d.value as EditorTab)}
                            className={editorStyles.tabList}
                        >
                            <Tab value="details">Details</Tab>
                            <Tab value="documents">Documents</Tab>
                            <Tab value="fields">Business Fields</Tab>
                            <Tab value="permissions">Permissions</Tab>
                        </TabList>

                        {activeTab === 'details' && (
                            <Accordion multiple defaultOpenItems={['blueprint']}>
                                <AccordionItem value="blueprint">
                                    <AccordionHeader>Blueprint Details</AccordionHeader>
                                    <AccordionPanel>
                                        <div className={editorStyles.accordionPanelContent}>
                                            <Field label="Blueprint Name" required>
                                                <Input
                                                    id={"input-blueprint-name"}
                                                    value={name}
                                                    onChange={(_, d) => setName(d.value)}
                                                    placeholder="Name shown in the blueprint list"
                                                />
                                            </Field>
                                            <Field label="Blueprint Summary">
                                                <Textarea
                                                    id={"textarea-blueprint-summary"}
                                                    value={summary}
                                                    onChange={(_, d) => setSummary(d.value)}
                                                    rows={2}
                                                    placeholder="Short description shown in the blueprint list"
                                                />
                                            </Field>
                                            <Field label="Tags">
                                                <div className={styles.tagInput}>
                                                    {tags.map(tag => (
                                                        <Tag
                                                            key={tag}
                                                            size="small"
                                                            dismissible
                                                            shape={"circular"}
                                                            onClick={() => setTags(prev => prev.filter(t => t !== tag))}
                                                        >{tag}</Tag>
                                                    ))}
                                                    <Input
                                                        id={"input-blueprint-tag"}
                                                        size="small"
                                                        appearance="underline"
                                                        placeholder="Add tag, press Enter"
                                                        value={tagInput}
                                                        onChange={(_, d) => setTagInput(d.value)}
                                                        onKeyDown={e => { if (e.key === 'Enter') { e.preventDefault(); addTag(); } }}
                                                        className={editorStyles.tagInputField}
                                                    />
                                                    <Button
                                                        id={"button-blueprint-add-tag"}
                                                        shape={"circular"}
                                                        appearance="subtle"
                                                        size="medium"
                                                        icon={<AddIcon/>}
                                                        onClick={addTag}
                                                    />
                                                </div>
                                            </Field>
                                        </div>
                                    </AccordionPanel>
                                </AccordionItem>

                                <AccordionItem value="exchange">
                                    <AccordionHeader>Exchange Details</AccordionHeader>
                                    <AccordionPanel>
                                        <div className={editorStyles.accordionPanelContent}>
                                            <Field label="Exchange Name" hint="Pre-fills the exchange name on initiation">
                                                {availableVariables ? (
                                                    <VariableTokenInput
                                                        value={config.name ?? ''}
                                                        onChange={v => setConfig(prev => ({...prev, name: v}))}
                                                        availableVariables={availableVariables}
                                                        placeholder="Exchange name, type {{ to insert a variable"
                                                    />
                                                ) : (
                                                    <Input
                                                        value={config.name ?? ''}
                                                        onChange={(_, d) => setConfig(prev => ({...prev, name: d.value}))}
                                                        placeholder="Exchange name"
                                                    />
                                                )}
                                            </Field>
                                            <Field label="Exchange Description" hint="Pre-fills the exchange description on initiation">
                                                {availableVariables ? (
                                                    <VariableTokenInput
                                                        value={config.description ?? ''}
                                                        onChange={v => setConfig(prev => ({...prev, description: v}))}
                                                        availableVariables={availableVariables}
                                                        multiline
                                                        placeholder="Exchange description, type {{ to insert a variable"
                                                    />
                                                ) : (
                                                    <Textarea
                                                        value={config.description ?? ''}
                                                        onChange={(_, d) => setConfig(prev => ({...prev, description: d.value}))}
                                                        rows={3}
                                                        placeholder="Exchange description"
                                                    />
                                                )}
                                            </Field>
                                            <Field label="Initial Share Message" hint="Message shown to recipients when they open the exchange">
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
                                        </div>
                                    </AccordionPanel>
                                </AccordionItem>
                            </Accordion>
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
                                                    appearance="transparent"
                                                    shape="circular"
                                                    onClick={() => removeDocument(i)}
                                                />
                                            </div>
                                            {doc.libraryDocumentId && (
                                                <div className={editorStyles.linkedBadgeRow}>
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
                                                        icon={<LinkDismissIcon/>}
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
                                                            id={`bp-doc-restrict-type-${i}`}
                                                            label="Restrict upload type"
                                                            checked={doc.restrictType ?? false}
                                                            disabled={!!doc.libraryDocumentId}
                                                            onChange={(_, d) => updateDoc(i, {restrictType: d.checked, restrictedType: d.checked ? doc.restrictedType : undefined})}
                                                        />
                                                    </Field>
                                                    <Dropdown
                                                        id={`bp-doc-restricted-type-${i}`}
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
                                                    id={`bp-doc-required-${i}`}
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
                                onSelect={(entries: DocumentLibraryEntrySummaryDto[]) =>
                                {
                                    setDocuments(prev => [
                                        ...prev,
                                        ...entries.map(entry => ({
                                            title: entry.title,
                                            libraryDocumentId: entry.id,
                                            restrictType: entry.restrictType ?? false,
                                            restrictedType: entry.restrictedType,
                                            required: entry.required ?? false,
                                        })),
                                    ]);
                                    setPickerOpen(false);
                                }}
                                onBack={() => setPickerOpen(false)}
                            />
                        )}

                        {activeTab === 'fields' && (
                            <BlueprintBusinessFieldsTab
                                initialSchemaDefinitionId={schemaDefinitionId}
                                initialFieldDefaults={fieldDefaults}
                                onChange={(id, defaults) => { setSchemaDefinitionId(id); setFieldDefaults(defaults); }}
                            />
                        )}

                        {activeTab === 'permissions' && (
                            <div className={editorStyles.permissionsContent}>
                                <Checkbox
                                    id={"checkbox-request-recipient-sign-in"}
                                    label="Require recipient sign-in"
                                    checked={config.requestRecipientSignIn ?? false}
                                    onChange={(_, d) => setBoolConfig('requestRecipientSignIn', !!d.checked)}
                                />
                                <Checkbox
                                    id={"checkbox-allow-document-addition"}
                                    label="Allow document addition"
                                    checked={config.allowDocumentAddition ?? false}
                                    onChange={(_, d) => setBoolConfig('allowDocumentAddition', !!d.checked)}
                                />
                                <Checkbox
                                    id={"checkbox-allow-document-deletion"}
                                    label="Allow document deletion"
                                    checked={config.allowDocumentDeletion ?? false}
                                    onChange={(_, d) => setBoolConfig('allowDocumentDeletion', !!d.checked)}
                                />
                                <Checkbox
                                    id={"checkbox-allow-document-download"}
                                    label="Allow document download"
                                    checked={config.allowDocumentDownload ?? false}
                                    onChange={(_, d) => setBoolConfig('allowDocumentDownload', !!d.checked)}
                                />
                                <Checkbox
                                    id={"checkbox-allow-document-update"}
                                    label="Allow document update"
                                    checked={config.allowDocumentUpdate ?? false}
                                    onChange={(_, d) => setBoolConfig('allowDocumentUpdate', !!d.checked)}
                                />
                                <Checkbox
                                    id={"checkbox-allow-document-upload"}
                                    label="Allow document upload"
                                    checked={config.allowDocumentUpload ?? false}
                                    onChange={(_, d) => setBoolConfig('allowDocumentUpload', !!d.checked)}
                                />
                            </div>
                        )}

                        {error && (
                            <span className={editorStyles.errorSpan}>
                                {error}
                            </span>
                        )}
                    </DialogContent>
                    <DialogActions>
                        <Button
                            id={"button-blueprint-save"}
                            appearance="primary"
                            shape={"circular"}
                            onClick={handleSave}
                            disabled={saving}
                        >
                            {saving ? <><Spinner size="tiny"/> Saving…</> : (blueprint ? 'Save Changes' : 'Create Blueprint')}
                        </Button>
                        <Button
                            id={"button-blueprint-cancel"}
                            shape={"circular"}
                            onClick={onClose}
                            disabled={saving}
                        >Cancel</Button>
                    </DialogActions>
                </DialogBody>
            </DialogSurface>
        </Dialog>
    );
};

export default BlueprintEditorDialog;
