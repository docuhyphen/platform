import {useEffect, useState} from 'react';
import {useAuth} from '../../../context/AuthContext';
import {
    Badge,
    Button,
    Drawer,
    DrawerBody,
    DrawerHeader,
    DrawerHeaderTitle,
    Field,
    Input,
    Menu,
    MenuList,
    MenuItem,
    MenuPopover,
    MenuTrigger,
    Select,
    Spinner,
    Text,
} from '@fluentui/react-components';
import {AddRegular, ArrowCounterclockwiseRegular, DeleteRegular, EditRegular, MoreVerticalRegular} from '@fluentui/react-icons';
import {AppUserRole, CreateSequenceRequest, SequenceDefinitionDto, SequenceResetPeriod, UpdateSequenceRequest} from '../../models/models';
import {
    createSequence,
    deleteSequence,
    listSequences,
    resetSequenceCounter,
    updateSequence,
} from '../../../services/variableService';

const RESET_PERIODS: SequenceResetPeriod[] = ['NEVER', 'YEARLY', 'MONTHLY'];

interface SequenceDrawerState
{
    open: boolean;
    editing?: SequenceDefinitionDto;
}

const defaultForm = (): CreateSequenceRequest => ({
    name: '',
    key: '',
    padWidth: 0,
    prefix: '',
    suffix: '',
    resetPeriod: 'NEVER',
});

const formatPreview = (form: CreateSequenceRequest | UpdateSequenceRequest, currentValue = 0): string =>
{
    const next = currentValue + 1;
    const padWidth = (form as CreateSequenceRequest).padWidth ?? 0;
    const padded = padWidth > 0 ? String(next).padStart(padWidth, '0') : String(next);
    return `${(form as CreateSequenceRequest).prefix ?? ''}${padded}${(form as CreateSequenceRequest).suffix ?? ''}`;
};

const OrganizationSequencesTab = () =>
{
    const {appUser, appUserPersonOrganization} = useAuth();
    const roleValue = `${appUser?.role ?? ''}`;
    const canManage =
        appUserPersonOrganization?.isActive &&
        (roleValue === AppUserRole.ORG_ADMIN || roleValue === 'APP_ADMIN');

    const [sequences, setSequences] = useState<SequenceDefinitionDto[]>([]);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [drawer, setDrawer] = useState<SequenceDrawerState>({open: false});
    const [form, setForm] = useState<CreateSequenceRequest>(defaultForm());
    const [saving, setSaving] = useState(false);
    const [formError, setFormError] = useState<string | null>(null);

    const load = () =>
    {
        setLoading(true);
        setError(null);
        listSequences()
            .then(setSequences)
            .catch(() => setError('Failed to load sequences'))
            .finally(() => setLoading(false));
    };

    useEffect(() => { load(); }, []);

    const openCreate = () =>
    {
        setForm(defaultForm());
        setFormError(null);
        setDrawer({open: true, editing: undefined});
    };

    const openEdit = (seq: SequenceDefinitionDto) =>
    {
        setForm({
            name: seq.name,
            key: seq.key,
            padWidth: seq.padWidth,
            prefix: seq.prefix ?? '',
            suffix: seq.suffix ?? '',
            resetPeriod: seq.resetPeriod,
        });
        setFormError(null);
        setDrawer({open: true, editing: seq});
    };

    const handleSave = async () =>
    {
        if (!form.name.trim()) { setFormError('Name is required'); return; }
        if (!form.key.trim()) { setFormError('Key is required'); return; }

        setSaving(true);
        setFormError(null);
        try
        {
            if (drawer.editing)
            {
                const update: UpdateSequenceRequest = {
                    name: form.name,
                    padWidth: form.padWidth,
                    prefix: form.prefix,
                    suffix: form.suffix,
                    resetPeriod: form.resetPeriod,
                };
                await updateSequence(drawer.editing.id, update);
            }
            else
            {
                await createSequence({...form, key: form.key.toUpperCase()});
            }
            setDrawer({open: false});
            load();
        }
        catch (e: unknown)
        {
            const msg = typeof e === 'object' && e !== null && 'errorMessage' in e
                ? (e as {errorMessage: string}).errorMessage
                : 'Failed to save sequence';
            setFormError(msg);
        }
        finally
        {
            setSaving(false);
        }
    };

    const handleDelete = async (seq: SequenceDefinitionDto) =>
    {
        await deleteSequence(seq.id).catch(() => null);
        load();
    };

    const handleReset = async (seq: SequenceDefinitionDto) =>
    {
        await resetSequenceCounter(seq.id).catch(() => null);
        load();
    };

    return (
        <>
            <div style={{display: 'flex', flexDirection: 'column', gap: '16px', padding: '0 4px'}}>
                <div style={{display: 'flex', justifyContent: 'space-between', alignItems: 'center'}}>
                    <Text size={500} weight="semibold">Sequences</Text>
                    {canManage && (
                        <Button appearance="subtle" shape="circular" icon={<AddRegular/>} onClick={openCreate}>
                            Create Sequence
                        </Button>
                    )}
                </div>
                <Text size={300} style={{color: 'var(--colorNeutralForeground3)'}}>
                    Auto-incrementing counters. Use <code style={{fontFamily: 'monospace'}}>{'{{SEQ:KEY}}'}</code> in blueprint and exchange names.
                </Text>

                {loading && <Spinner size="medium" label="Loading…"/>}
                {!loading && error && <Text style={{color: 'var(--colorPaletteRedForeground1)'}}>{error}</Text>}
                {!loading && !error && sequences.length === 0 && (
                    <Text style={{color: 'var(--colorNeutralForeground3)'}}>No sequences yet.</Text>
                )}
                {!loading && sequences.map(seq => (
                    <div key={seq.id} style={{
                        border: '1px solid var(--colorNeutralStroke1)',
                        borderRadius: '8px',
                        padding: '12px 16px',
                        display: 'flex',
                        justifyContent: 'space-between',
                        alignItems: 'flex-start',
                        gap: '8px',
                    }}>
                        <div style={{display: 'flex', flexDirection: 'column', gap: '4px', flex: 1, minWidth: 0}}>
                            <Text weight="semibold" size={400}>{seq.name}</Text>
                            <code style={{fontFamily: 'monospace', fontSize: '12px', color: 'var(--colorNeutralForeground2)'}}>{`{{SEQ:${seq.key}}}`}</code>
                            <div style={{display: 'flex', gap: '6px', flexWrap: 'wrap', alignItems: 'center'}}>
                                <Badge appearance="tint" color={seq.isActive ? 'success' : 'severe'} size="small">
                                    {seq.isActive ? 'Active' : 'Inactive'}
                                </Badge>
                                <Badge appearance="tint" color="informative" size="small">
                                    Counter: {seq.currentValue}
                                </Badge>
                                <Badge appearance="tint" size="small">
                                    Resets: {seq.resetPeriod}
                                </Badge>
                                <Text size={200} style={{color: 'var(--colorNeutralForeground3)'}}>
                                    Next: <strong>{seq.previewValue}</strong>
                                </Text>
                            </div>
                        </div>
                        {canManage && (
                            <Menu>
                                <MenuTrigger disableButtonEnhancement>
                                    <Button size="small" appearance="subtle" icon={<MoreVerticalRegular/>}/>
                                </MenuTrigger>
                                <MenuPopover>
                                    <MenuList>
                                        <MenuItem icon={<EditRegular/>} onClick={() => openEdit(seq)}>Edit</MenuItem>
                                        <MenuItem icon={<ArrowCounterclockwiseRegular/>} onClick={() => handleReset(seq)}>Reset Counter</MenuItem>
                                        <MenuItem icon={<DeleteRegular/>} onClick={() => handleDelete(seq)}>Delete</MenuItem>
                                    </MenuList>
                                </MenuPopover>
                            </Menu>
                        )}
                    </div>
                ))}
            </div>

            <Drawer open={drawer.open} onOpenChange={(_, d) => setDrawer(prev => ({...prev, open: d.open}))} position="end" size="small">
                <DrawerHeader>
                    <DrawerHeaderTitle>{drawer.editing ? 'Edit Sequence' : 'New Sequence'}</DrawerHeaderTitle>
                </DrawerHeader>
                <DrawerBody style={{display: 'flex', flexDirection: 'column', gap: '16px', paddingTop: '16px'}}>
                    <Field label="Name" required validationMessage={!form.name.trim() && formError ? formError : undefined}>
                        <Input value={form.name} onChange={(_, d) => setForm(f => ({...f, name: d.value}))} placeholder="e.g. Invoice Number"/>
                    </Field>
                    <Field label="Key (uppercase)" required hint="Used in {{SEQ:KEY}} token">
                        <Input
                            value={form.key}
                            onChange={(_, d) => setForm(f => ({...f, key: d.value.toUpperCase()}))}
                            placeholder="e.g. INV"
                            disabled={!!drawer.editing}
                        />
                    </Field>
                    <Field label="Pad Width" hint="0 = no padding, 3 → 007">
                        <Input
                            type="number"
                            value={String(form.padWidth ?? 0)}
                            onChange={(_, d) => setForm(f => ({...f, padWidth: parseInt(d.value) || 0}))}
                        />
                    </Field>
                    <Field label="Prefix">
                        <Input value={form.prefix ?? ''} onChange={(_, d) => setForm(f => ({...f, prefix: d.value}))} placeholder="e.g. INV-"/>
                    </Field>
                    <Field label="Suffix">
                        <Input value={form.suffix ?? ''} onChange={(_, d) => setForm(f => ({...f, suffix: d.value}))} placeholder="e.g. /2026"/>
                    </Field>
                    <Field label="Reset Period">
                        <Select value={form.resetPeriod ?? 'NEVER'} onChange={(_, d) => setForm(f => ({...f, resetPeriod: d.value as SequenceResetPeriod}))}>
                            {RESET_PERIODS.map(p => <option key={p} value={p}>{p}</option>)}
                        </Select>
                    </Field>
                    <div style={{padding: '8px 12px', background: 'var(--colorNeutralBackground2)', borderRadius: '6px'}}>
                        <Text size={200} style={{color: 'var(--colorNeutralForeground3)'}}>Preview: </Text>
                        <code style={{fontFamily: 'monospace'}}>{formatPreview(form, drawer.editing?.currentValue ?? 0)}</code>
                    </div>
                    {formError && <Text style={{color: 'var(--colorPaletteRedForeground1)'}}>{formError}</Text>}
                    <div style={{display: 'flex', gap: '8px', justifyContent: 'flex-end'}}>
                        <Button appearance="secondary" onClick={() => setDrawer({open: false})}>Cancel</Button>
                        <Button appearance="primary" onClick={handleSave} disabled={saving}>
                            {saving ? 'Saving…' : 'Save'}
                        </Button>
                    </div>
                </DrawerBody>
            </Drawer>
        </>
    );
};

export default OrganizationSequencesTab;
