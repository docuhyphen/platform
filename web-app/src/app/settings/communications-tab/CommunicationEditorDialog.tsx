import React, {useEffect, useState} from 'react';
import {
    Badge,
    Button,
    Dialog,
    DialogActions,
    DialogBody,
    DialogContent,
    DialogSurface,
    DialogTitle,
    Field,
    Input,
    Spinner,
    Tab,
    TabList,
    Tag,
    Text,
    Textarea,
} from '@fluentui/react-components';
import {
    CreateCommunicationRequest,
    CommunicationDto,
    CommunicationSummaryDto,
    CommunicationScope,
    RenderedCommunication,
    UpdateCommunicationRequest,
} from '../../models/models';
import {
    createCommunication,
    previewCommunication,
    updateCommunication,
} from '../../../services/communicationService';
import VariableTokenInput from '../../../components/variable-token-input/VariableTokenInput';
import {getAvailableVariables} from '../../../services/variableService';
import {AvailableVariablesDto} from '../../models/models';
import {AddIcon} from "../../components/IconBundles.tsx";
import {useCommunicationEditorStyles} from './CommunicationsTabStyles.tsx';

interface Props
{
    open: boolean;
    onClose: () => void;
    onSaved: () => void;
    communication?: CommunicationSummaryDto | CommunicationDto;
    scope: CommunicationScope;
}

type EditorTab = 'details' | 'preview';

const CommunicationEditorDialog: React.FC<Props> = ({open, onClose, onSaved, communication, scope}) =>
{
    const styles = useCommunicationEditorStyles();
    const [activeTab, setActiveTab] = useState<EditorTab>('details');
    const [name, setName] = useState('');
    const [summary, setSummary] = useState('');
    const [description, setDescription] = useState('');
    const [subject, setSubject] = useState('');
    const [body, setBody] = useState('');
    const [tagInput, setTagInput] = useState('');
    const [tags, setTags] = useState<string[]>([]);
    const [saving, setSaving] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [availableVariables, setAvailableVariables] = useState<AvailableVariablesDto | null>(null);

    const [previewVars, setPreviewVars] = useState('');
    const [previewing, setPreviewing] = useState(false);
    const [previewResult, setPreviewResult] = useState<RenderedCommunication | null>(null);
    const [previewError, setPreviewError] = useState<string | null>(null);

    useEffect(() =>
    {
        if (!open) return;
        getAvailableVariables().then(setAvailableVariables).catch(() => null);
        if (communication)
        {
            setName(communication.name);
            setSummary(communication.summary ?? '');
            setDescription((communication as CommunicationDto).description ?? '');
            setSubject(communication.subject);
            setBody((communication as CommunicationDto).body ?? '');
            setTags(communication.generalTags ?? []);
        }
        else
        {
            setName('');
            setSummary('');
            setDescription('');
            setSubject('');
            setBody('');
            setTags([]);
        }
        setActiveTab('details');
        setError(null);
        setPreviewResult(null);
        setPreviewVars('');
        setPreviewError(null);
    }, [open, communication]);

    const addTag = () =>
    {
        const t = tagInput.trim();
        if (t && !tags.includes(t)) setTags(prev => [...prev, t]);
        setTagInput('');
    };

    const handleSave = async () =>
    {
        if (!name.trim()) { setError('Name is required'); return; }
        if (!subject.trim()) { setError('Subject is required'); return; }
        if (!body.trim()) { setError('Body is required'); return; }
        setSaving(true);
        setError(null);
        try
        {
            if (communication)
            {
                const req: UpdateCommunicationRequest = {
                    name: name.trim(),
                    summary: summary.trim() || undefined,
                    description: description.trim() || undefined,
                    subject: subject.trim(),
                    body: body.trim(),
                    generalTags: tags,
                };
                await updateCommunication(communication.id, req);
            }
            else
            {
                const req: CreateCommunicationRequest = {
                    name: name.trim(),
                    summary: summary.trim() || undefined,
                    description: description.trim() || undefined,
                    subject: subject.trim(),
                    body: body.trim(),
                    generalTags: tags,
                    scope,
                    isActive: true,
                };
                await createCommunication(req);
            }
            onSaved();
        }
        catch (e: any)
        {
            setError(typeof e === 'string' ? e : 'Failed to save communication');
        }
        finally
        {
            setSaving(false);
        }
    };

    const handlePreview = async () =>
    {
        if (!communication?.id) return;
        setPreviewing(true);
        setPreviewError(null);
        setPreviewResult(null);
        try
        {
            let sampleVariables: Record<string, string> = {};
            if (previewVars.trim())
            {
                try { sampleVariables = JSON.parse(previewVars); }
                catch { setPreviewError('Sample variables must be valid JSON'); setPreviewing(false); return; }
            }
            const result = await previewCommunication(communication.id, {sampleVariables});
            setPreviewResult(result);
        }
        catch (e: any)
        {
            setPreviewError(typeof e === 'string' ? e : 'Preview failed');
        }
        finally
        {
            setPreviewing(false);
        }
    };

    const variableHints = availableVariables
        ? [
            ...availableVariables.system.map(v => `{{${v.token}}}`),
            ...availableVariables.org.map(v => `{{${v.key}}}`),
            ...availableVariables.personal.map(v => `{{${v.key}}}`),
            ...availableVariables.sequences.map(v => `{{SEQ:${v.key}}}`),
        ]
        : [];

    return (
        <Dialog open={open} onOpenChange={(_, {open: isOpen}) => { if (!isOpen) onClose(); }}>
            <DialogSurface style={{maxWidth: '680px', width: '100%'}}>
                <DialogBody>
                    <DialogTitle>{communication ? 'Edit Communication' : 'Create Communication'}</DialogTitle>
                    <DialogContent>
                        <TabList
                            selectedValue={activeTab}
                            onTabSelect={(_, d) => setActiveTab(d.value as EditorTab)}
                            style={{marginBottom: '16px'}}
                        >
                            <Tab value="details">Details</Tab>
                            <Tab value="preview" disabled={!communication?.id}>Preview</Tab>
                        </TabList>

                        {activeTab === 'details' && (
                            <div style={{display: 'flex', flexDirection: 'column', gap: '12px'}}>
                                <Field label="Name" required>
                                    <Input
                                        value={name}
                                        onChange={(_, d) => setName(d.value)}
                                        placeholder="Communication name"
                                    />
                                </Field>
                                <Field label="Summary">
                                    <Input
                                        value={summary}
                                        onChange={(_, d) => setSummary(d.value)}
                                        placeholder="Short description"
                                    />
                                </Field>
                                <Field label="Subject" required hint="Supports {{variable}} tokens">
                                    {availableVariables ? (
                                        <VariableTokenInput
                                            value={subject}
                                            onChange={setSubject}
                                            availableVariables={availableVariables}
                                            placeholder="Email subject line, type {{ to insert a variable"
                                        />
                                    ) : (
                                        <Input
                                            value={subject}
                                            onChange={(_, d) => setSubject(d.value)}
                                            placeholder="Email subject line"
                                        />
                                    )}
                                </Field>
                                <Field
                                    label="Body"
                                    required
                                    hint="Supports Markdown and {{variable}} tokens. Used for email body and in-app notifications."
                                >
                                    {availableVariables ? (
                                        <VariableTokenInput
                                            value={body}
                                            onChange={setBody}
                                            availableVariables={availableVariables}
                                            multiline
                                            placeholder="Message body in Markdown. Type {{ to insert a variable."
                                        />
                                    ) : (
                                        <Textarea
                                            value={body}
                                            onChange={(_, d) => setBody(d.value)}
                                            rows={6}
                                            placeholder="Message body in Markdown"
                                        />
                                    )}
                                </Field>
                                {variableHints.length > 0 && (
                                    <div>
                                        <Text size={200} style={{color: 'var(--colorNeutralForeground3)', display: 'block', marginBottom: '6px'}}>
                                            Available tokens:
                                        </Text>
                                        <div style={{display: 'flex', flexWrap: 'wrap', gap: '4px'}}>
                                            {variableHints.slice(0, 12).map(hint => (
                                                <Badge key={hint} appearance="tint" size="small" color="informative"
                                                       style={{fontFamily: 'monospace', cursor: 'pointer'}}
                                                       onClick={() => setBody(b => b + hint)}>
                                                    {hint}
                                                </Badge>
                                            ))}
                                            {variableHints.length > 12 && (
                                                <Text size={200} style={{color: 'var(--colorNeutralForeground3)'}}>
                                                    +{variableHints.length - 12} more
                                                </Text>
                                            )}
                                        </div>
                                    </div>
                                )}
                                <Field label="Tags">
                                    <div className={styles.tagInput}>
                                        {tags.map(tag => (
                                            <Tag key={tag} size="small" dismissible
                                                 onClick={() => setTags(prev => prev.filter(t => t !== tag))}>{tag}</Tag>
                                        ))}
                                        <Input
                                            size="small"
                                            appearance="underline"
                                            placeholder="Add tag, press Enter"
                                            value={tagInput}
                                            onChange={(_, d) => setTagInput(d.value)}
                                            onKeyDown={e => { if (e.key === 'Enter') { e.preventDefault(); addTag(); } }}
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
                            </div>
                        )}

                        {activeTab === 'preview' && (
                            <div style={{display: 'flex', flexDirection: 'column', gap: '12px'}}>
                                <Field
                                    label="Sample variables (JSON)"
                                    hint='Override tokens for preview, e.g. {"exchangeName": "Contract Review"}'
                                >
                                    <Textarea
                                        value={previewVars}
                                        onChange={(_, d) => setPreviewVars(d.value)}
                                        rows={3}
                                        placeholder='{"exchangeName": "My Exchange", "initiatorName": "Jane Smith"}'
                                    />
                                </Field>
                                <Button
                                    appearance="secondary"
                                    shape="circular"
                                    onClick={handlePreview}
                                    disabled={previewing}
                                    style={{alignSelf: 'flex-start'}}
                                >
                                    {previewing ? <><Spinner size="tiny"/> Rendering…</> : 'Render Preview'}
                                </Button>
                                {previewError && (
                                    <Text style={{color: 'var(--colorPaletteRedForeground1)'}}>{previewError}</Text>
                                )}
                                {previewResult && (
                                    <div style={{display: 'flex', flexDirection: 'column', gap: '12px'}}>
                                        <div style={{
                                            border: '1px solid var(--colorNeutralStroke1)',
                                            borderRadius: '8px',
                                            padding: '12px',
                                            background: 'var(--colorNeutralBackground2)',
                                        }}>
                                            <Text size={200} weight="semibold" style={{display: 'block', marginBottom: '4px', color: 'var(--colorNeutralForeground3)'}}>
                                                Subject
                                            </Text>
                                            <Text size={400} weight="semibold">{previewResult.subject}</Text>
                                        </div>
                                        <div style={{
                                            border: '1px solid var(--colorNeutralStroke1)',
                                            borderRadius: '8px',
                                            padding: '12px',
                                            background: 'var(--colorNeutralBackground2)',
                                        }}>
                                            <Text size={200} weight="semibold" style={{display: 'block', marginBottom: '8px', color: 'var(--colorNeutralForeground3)'}}>
                                                Body (Markdown)
                                            </Text>
                                            <pre style={{
                                                fontFamily: 'inherit',
                                                whiteSpace: 'pre-wrap',
                                                wordBreak: 'break-word',
                                                margin: 0,
                                            }}>
                                                {previewResult.body}
                                            </pre>
                                        </div>
                                    </div>
                                )}
                            </div>
                        )}

                        {error && (
                            <span style={{color: 'var(--colorPaletteRedForeground1)', fontSize: '12px', marginTop: '8px', display: 'block'}}>
                                {error}
                            </span>
                        )}
                    </DialogContent>
                    <DialogActions>
                        {activeTab === 'details' && (
                            <Button appearance="primary" shape="circular" onClick={handleSave} disabled={saving}>
                                {saving ? <><Spinner size="tiny"/> Saving…</> : (communication ? 'Save Changes' : 'Create Communication')}
                            </Button>
                        )}
                        <Button shape="circular" onClick={onClose} disabled={saving}>Cancel</Button>
                    </DialogActions>
                </DialogBody>
            </DialogSurface>
        </Dialog>
    );
};

export default CommunicationEditorDialog;
