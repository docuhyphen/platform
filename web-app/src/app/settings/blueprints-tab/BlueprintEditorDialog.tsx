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
import DocumentLibraryPicker
    from '../../../app/exchange-initiation/components/document-library-picker/DocumentLibraryPicker.tsx';
import BlueprintBusinessFieldsTab from './blueprint-business-fields-tab/BlueprintBusinessFieldsTab.tsx';
import BlueprintInformationRequestTab from './blueprint-information-request-tab/BlueprintInformationRequestTab.tsx';
import {templateVersionChange} from './blueprint-information-request-tab/blueprintTemplateVersionChange.ts';
import {normalizeApiError} from '../../../utils/apiErrorUtils.ts';

interface BlueprintEditorDialogProps
{
    open: boolean;
    onClose: () => void;
    onSaved: () => void;
    blueprint?: BlueprintDefinitionSummaryDto;
    scope: BlueprintScope;
    enforcedScope?: BlueprintScope;
}

type EditorTab = 'details' | 'documents' | 'permissions' | 'fields' | 'informationRequest';

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
        enforcedScope,
    }) =>
{
    const effectiveScope = enforcedScope ?? scope;
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
    const [templateVersionId, setTemplateVersionId] = useState<string | undefined>(undefined);
    const [saving, setSaving] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [availableVariables, setAvailableVariables] = useState<AvailableVariablesDto | null>(null);
    const [pickerOpen, setPickerOpen] = useState(false);
    const styles = useExchangeInitiationStyles();
    const editorStyles = useBlueprintEditorStyles();

    useEffect(() =>
    {
        if (!open) return;
        if (enforcedScope === 'APP')
            setAvailableVariables(null);
        else
            getAvailableVariables().then(setAvailableVariables).catch(() => null);
        if (blueprint)
        {
            setName(blueprint.name);
            setSummary(blueprint.summary ?? '');
            setDescription((blueprint as BlueprintDefinitionDto).description ?? '');
            setTags(blueprint.generalTags);
            try {
                setConfig(JSON.parse(blueprint.configJson));
            } catch {
                setConfig(emptyConfig());
            }
            setDocuments(blueprint.exchangeDocuments ?? []);
            setSchemaDefinitionId(blueprint.schemaDefinitionId);
            setFieldDefaults(blueprint.fieldDefaults ?? []);
            setTemplateVersionId(blueprint.informationRequestTemplateVersionId);
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
            setTemplateVersionId(undefined);
        }
        setActiveTab('details');
        setPickerOpen(false);
        setError(null);
    }, [open, blueprint, enforcedScope]);

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
        if (blueprint && enforcedScope && blueprint.scope !== enforcedScope)
        {
            setError('The selected Blueprint is outside the enforced editor scope');
            return;
        }
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
                    ...templateVersionChange(blueprint.informationRequestTemplateVersionId, templateVersionId),
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
                    scope: effectiveScope,
                    isActive: true,
                    schemaDefinitionId,
                    fieldDefaults: fieldDefaults.length > 0 ? fieldDefaults : undefined,
                    informationRequestTemplateVersionId: templateVersionId,
                };
                await createBlueprint(req);
            }
            onSaved();
        }
        catch (e: unknown)
        {
            setError(normalizeApiError(e, 'Failed to save blueprint').message);
        }
        finally
        {
            setSaving(false);
        }
    };

    return (
        <Dialog open={open}
                onOpenChange={(_, {open: isOpen}) => {
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
                                checked={config.allowEditOnExchangeStart ?? false}
                                onChange={(_, d) => setBoolConfig('allowEditOnExchangeStart', !!d.checked)}
                            />
                        ) : undefined}
                    >
                        {blueprint ? 'Edit Blueprint' : 'Create Blueprint'}
                    </DialogTitle>
                    <DialogContent id={"blueprint-editor-dialog-content"}>
                        <TabList
                            id={"blueprint-editor-tabs"}
                            selectedValue={activeTab}
                            onTabSelect={(_, d) => setActiveTab(d.value as EditorTab)}
                            className={editorStyles.tabList}
                        >
                            <Tab id={"blueprint-editor-details-tab"}
                                 value="details">
                                Details
                            </Tab>
                            <Tab id={"blueprint-editor-documents-tab"}
                                 value="documents">
                                Documents
                            </Tab>
                            <Tab id={"blueprint-editor-fields-tab"}
                                 value="fields">
                                Business Fields
                            </Tab>
                            <Tab id={"blueprint-editor-information-request-tab"}
                                 value="informationRequest">
                                Information Request
                            </Tab>
                            <Tab id={"blueprint-editor-permissions-tab"}
                                 value="permissions">
                                Permissions
                            </Tab>
                        </TabList>

                        {activeTab === 'details' && (
                            <Accordion id={"blueprint-editor-accordion"}
                                       multiple
                                       defaultOpenItems={['blueprint']}>
                                <AccordionItem id={"blueprint-editor-accordion-item"}
                                               value="blueprint">
                                    <AccordionHeader id={"blueprint-editor-blueprint-details-accordion-header"}>Blueprint
                                        Details</AccordionHeader>
                                    <AccordionPanel id={"blueprint-editor-accordion-panel"}>
                                        <div id={"blueprint-editor-div"}
                                             className={editorStyles.accordionPanelContent}>
                                            <Field id={"blueprint-editor-blueprint-name-field"}
                                                   label="Blueprint Name"
                                                   required>
                                                <Input
                                                    id={"input-blueprint-name"}
                                                    value={name}
                                                    onChange={(_, d) => setName(d.value)}
                                                    placeholder="Name shown in the blueprint list"
                                                />
                                            </Field>
                                            <Field id={"blueprint-editor-blueprint-summary-field"}
                                                   label="Blueprint Summary">
                                                <Textarea
                                                    id={"textarea-blueprint-summary"}
                                                    value={summary}
                                                    onChange={(_, d) => setSummary(d.value)}
                                                    rows={2}
                                                    placeholder="Short description shown in the blueprint list"
                                                />
                                            </Field>
                                            <Field id={"blueprint-editor-tags-field"}
                                                   label="Tags">
                                                <div id={"blueprint-editor-tag-input"}
                                                     className={styles.tagInput}>
                                                    {tags.map(tag => (
                                                        <Tag
                                                            id={`blueprint-editor-tag-${tag}`}
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
                                                        onKeyDown={e => {
                                                            if (e.key === 'Enter') {
                                                                e.preventDefault();
                                                                addTag();
                                                            }
                                                        }}
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

                                <AccordionItem id={"blueprint-editor-accordion-item-2"}
                                               value="exchange">
                                    <AccordionHeader id={"blueprint-editor-exchange-details-accordion-header"}>Exchange
                                        Details</AccordionHeader>
                                    <AccordionPanel id={"blueprint-editor-accordion-panel-2"}>
                                        <div id={"blueprint-editor-div-2"}
                                             className={editorStyles.accordionPanelContent}>
                                            <Field id={"blueprint-editor-exchange-name-field"}
                                                   label="Exchange Name"
                                                   hint="Pre-fills the Exchange name on initiation">
                                                {availableVariables ? (
                                                    <VariableTokenInput
                                                        value={config.name ?? ''}
                                                        onChange={v => setConfig(prev => ({...prev, name: v}))}
                                                        availableVariables={availableVariables}
                                                        placeholder="Exchange name, type {{ to insert a variable"
                                                    />
                                                ) : (
                                                    <Input
                                                        id={"blueprint-editor-input"}
                                                        value={config.name ?? ''}
                                                        onChange={(_, d) => setConfig(prev => ({
                                                            ...prev,
                                                            name: d.value
                                                        }))}
                                                        placeholder="Exchange name"
                                                    />
                                                )}
                                            </Field>
                                            <Field id={"blueprint-editor-exchange-description-field"}
                                                   label="Exchange Description"
                                                   hint="Pre-fills the Exchange description on initiation">
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
                                                        id={"blueprint-editor-textarea"}
                                                        value={config.description ?? ''}
                                                        onChange={(_, d) => setConfig(prev => ({
                                                            ...prev,
                                                            description: d.value
                                                        }))}
                                                        rows={3}
                                                        placeholder="Exchange description"
                                                    />
                                                )}
                                            </Field>
                                            <Field id={"blueprint-editor-initial-share-message-field"}
                                                   label="Initial Share Message"
                                                   hint="Message shown to recipients when they open the Exchange">
                                                {availableVariables ? (
                                                    <VariableTokenInput
                                                        value={config.initialShareMessage ?? ''}
                                                        onChange={v => setConfig(prev => ({
                                                            ...prev,
                                                            initialShareMessage: v
                                                        }))}
                                                        availableVariables={availableVariables}
                                                        multiline
                                                        placeholder="Message shown to recipient when they open the Exchange"
                                                    />
                                                ) : (
                                                    <Textarea
                                                        id={"blueprint-editor-textarea-2"}
                                                        value={config.initialShareMessage ?? ''}
                                                        onChange={(_, d) => setConfig(prev => ({
                                                            ...prev,
                                                            initialShareMessage: d.value
                                                        }))}
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
                            <div id={"blueprint-editor-exchange-documents-tab-content"}
                                 className={styles.exchangeDocumentsTabContent}>
                                {documents.map((doc, i) => (
                                    <Card
                                        id={`blueprint-editor-shading-exchange-document-card-${i}`}
                                        key={i}
                                        className={styles.shadingExchangeDocumentCard}
                                    >
                                        <div id={`blueprint-editor-div-3-${i}`}>
                                            <div id={`blueprint-editor-dialog-title1-${i}`}
                                                 className={styles.dialogTitle1}>
                                                <Field id={`blueprint-editor-sharing-details-input-${i}`}
                                                       className={styles.sharingDetailsInput}>
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
                                                <div id={`blueprint-editor-shading-exchange-document-card-div-${i}`}
                                                     className={editorStyles.linkedBadgeRow}>
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
                                            <div id={`blueprint-editor-exchange-documents-restriction-${i}`}
                                                 className={styles.exchangeDocumentsRestriction}>
                                                <div id={`blueprint-editor-exchange-documents-restriction-field-${i}`}
                                                     className={styles.exchangeDocumentsRestrictionField}>
                                                    <Field
                                                        id={`blueprint-editor-exchange-documents-restriction-field-field-${i}`}
                                                        label="">
                                                        <Switch
                                                            id={`bp-doc-restrict-type-${i}`}
                                                            label="Restrict upload type"
                                                            checked={doc.restrictType ?? false}
                                                            disabled={!!doc.libraryDocumentId}
                                                            onChange={(_, d) => updateDoc(i, {
                                                                restrictType: d.checked,
                                                                restrictedType: d.checked ? doc.restrictedType : undefined
                                                            })}
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
                                                        <OptionGroup id={`blueprint-editor-documents-option-group-${i}`}
                                                                     label="Documents">
                                                            {Object.values(DocumentType).map(opt => (
                                                                <Option
                                                                    id={`blueprint-editor-opt-option-${i}-${opt}`}
                                                                    key={opt}
                                                                    value={opt}
                                                                >
                                                                    {opt}
                                                                </Option>
                                                            ))}
                                                        </OptionGroup>
                                                        <OptionGroup id={`blueprint-editor-images-option-group-${i}`}
                                                                     label="Images">
                                                            {Object.values(ImageType).map(opt => (
                                                                <Option
                                                                    id={`blueprint-editor-opt-option-2-${i}-${opt}`}
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
                                <div id={"blueprint-editor-add-document-button-container"}
                                     className={styles.addDocumentButtonContainer}>
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
                                enforcedScope={enforcedScope}
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
                                enforcedScope={enforcedScope}
                                onChange={(id, defaults) => {
                                    setSchemaDefinitionId(id);
                                    setFieldDefaults(defaults);
                                }}
                            />
                        )}

                        {activeTab === 'informationRequest' && (
                            <BlueprintInformationRequestTab scope={effectiveScope}
                                                            templateVersionId={templateVersionId}
                                                            pinnedVersion={templateVersionId === blueprint?.informationRequestTemplateVersionId
                                                                ? blueprint?.informationRequestTemplateVersion
                                                                : undefined}
                                                            onChange={setTemplateVersionId}/>
                        )}

                        {activeTab === 'permissions' && (
                            <div id={"blueprint-editor-div-4"}
                                 className={editorStyles.permissionsContent}>
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
                            <span id={"blueprint-editor-error"}
                                  className={editorStyles.errorSpan}>
                                {error}
                            </span>
                        )}
                    </DialogContent>
                    <DialogActions id={"blueprint-editor-dialog-actions"}>
                        <Button
                            id={"button-blueprint-save"}
                            appearance="primary"
                            shape={"circular"}
                            onClick={handleSave}
                            disabled={saving}
                        >
                            {saving ? <><Spinner
                                id={"blueprint-editor-spinner"}
                                size="tiny"/> Saving...</> : (blueprint ? 'Save Changes' : 'Create Blueprint')}
                        </Button>
                        <Button
                            id={"button-blueprint-cancel"}
                            appearance="secondary"
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
