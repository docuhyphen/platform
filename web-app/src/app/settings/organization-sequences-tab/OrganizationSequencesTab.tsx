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
import {CreateSequenceRequest, SequenceDefinitionDto, SequenceResetPeriod, UpdateSequenceRequest, ViewMode} from '../../models/models';
import {Capability} from '../../models/models';
import {
    createSequence,
    deleteSequence,
    listSequences,
    resetSequenceCounter,
    updateSequence,
} from '../../../services/variableService';
import {useOrganizationSequencesTabStyles} from './OrganizationSequencesTabStyles';
import ViewModeToggle from '../../components/ViewModeToggle.tsx';
import {updateAppUserSettings} from '../../../services/appUserApi';

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
    const styles = useOrganizationSequencesTabStyles();
    const {appUser, setAppUser, token, appUserPersonOrganization, hasCapability} = useAuth();
    const canManage =
        appUserPersonOrganization?.isActive &&
        hasCapability(Capability.ORG_POLICY_MANAGE);

    const [viewMode, setViewMode] = useState<ViewMode>(appUser?.settings?.sequencesView ?? 'cards');

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

    const handleViewModeChange = async (mode: ViewMode) => {
        setViewMode(mode);
        if (!appUser?.settings) return;
        const updated = {...appUser.settings, sequencesView: mode};
        try { await updateAppUserSettings(updated, token); if (appUser) setAppUser({...appUser, settings: updated}); }
        catch { /* non-critical */ }
    };

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
            <div className={styles.container}>
                <div className={styles.header}>
                    <Text
                        size={500}
                        weight="semibold"
                    >
                        Sequences
                    </Text>
                    {canManage && (
                        <Button
                            id={"button-create-sequence"}
                            appearance="subtle"
                            shape={"circular"}
                            icon={<AddRegular/>}
                            onClick={openCreate}
                        >
                            Create Sequence
                        </Button>
                    )}
                </div>
                <Text
                    size={300}
                    className={styles.descriptionText}
                >
                    Auto-incrementing counters. Use <code>{'{{SEQ:KEY}}'}</code> in blueprint and exchange names.
                </Text>

                <div className={styles.toolbar}>
                    <ViewModeToggle value={viewMode} onChange={handleViewModeChange}/>
                </div>

                {loading && <Spinner size="small" label="Loading…"/>}
                {!loading && error && (
                    <Text className={styles.errorText}>{error}</Text>
                )}
                {!loading && !error && sequences.length === 0 && (
                    <Text className={styles.emptyText}>No sequences yet.</Text>
                )}
                {!loading && !error && sequences.length > 0 && viewMode === 'cards' && sequences.map(seq => (
                    <div
                        key={seq.id}
                        className={styles.sequenceCard}
                    >
                        <div className={styles.sequenceCardInner}>
                            <Text
                                weight="semibold"
                                size={400}
                            >
                                {seq.name}
                            </Text>
                            <code className={styles.codeToken}>{`{{SEQ:${seq.key}}}`}</code>
                            <div className={styles.badgeRow}>
                                <Badge
                                    appearance="tint"
                                    color={seq.isActive ? 'success' : 'severe'}
                                    size="small"
                                >
                                    {seq.isActive ? 'Active' : 'Inactive'}
                                </Badge>
                                <Badge
                                    appearance="tint"
                                    color="informative"
                                    size="small"
                                >
                                    Counter: {seq.currentValue}
                                </Badge>
                                <Badge
                                    appearance="tint"
                                    size="small"
                                >
                                    Resets: {seq.resetPeriod}
                                </Badge>
                                <Text
                                    size={200}
                                    className={styles.nextText}
                                >
                                    Next: <strong>{seq.previewValue}</strong>
                                </Text>
                            </div>
                        </div>
                        {canManage && (
                            <Menu>
                                <MenuTrigger disableButtonEnhancement>
                                    <Button
                                        id={`button-seq-menu-${seq.id}`}
                                        size="small"
                                        appearance="subtle"
                                        shape={"circular"}
                                        icon={<MoreVerticalRegular/>}
                                    />
                                </MenuTrigger>
                                <MenuPopover>
                                    <MenuList>
                                        <MenuItem
                                            icon={<EditRegular/>}
                                            onClick={() => openEdit(seq)}
                                        >
                                            Edit
                                        </MenuItem>
                                        <MenuItem
                                            icon={<ArrowCounterclockwiseRegular/>}
                                            onClick={() => handleReset(seq)}
                                        >
                                            Reset Counter
                                        </MenuItem>
                                        <MenuItem
                                            icon={<DeleteRegular/>}
                                            onClick={() => handleDelete(seq)}
                                        >
                                            Delete
                                        </MenuItem>
                                    </MenuList>
                                </MenuPopover>
                            </Menu>
                        )}
                    </div>
                ))}
                {!loading && !error && sequences.length > 0 && viewMode === 'table' && (
                    <table className={styles.table}>
                        <thead>
                            <tr>
                                <th className={styles.th}>Name</th>
                                <th className={styles.th}>Token</th>
                                <th className={styles.th}>Counter</th>
                                <th className={styles.th}>Reset Period</th>
                                <th className={styles.th}>Active</th>
                                {canManage && <th className={styles.th}/>}
                            </tr>
                        </thead>
                        <tbody>
                            {sequences.map(seq => (
                                <tr key={seq.id} className={styles.tr}>
                                    <td className={styles.td}><Text weight="semibold">{seq.name}</Text></td>
                                    <td className={styles.td}><code className={styles.codeToken}>{`{{SEQ:${seq.key}}}`}</code></td>
                                    <td className={styles.td}><Text size={200}>{seq.currentValue} (next: {seq.previewValue})</Text></td>
                                    <td className={styles.td}><Text size={200}>{seq.resetPeriod}</Text></td>
                                    <td className={styles.td}>
                                        <Badge appearance="tint" color={seq.isActive ? 'success' : 'severe'} size="small">
                                            {seq.isActive ? 'Active' : 'Inactive'}
                                        </Badge>
                                    </td>
                                    {canManage && (
                                        <td className={styles.td}>
                                            <Menu>
                                                <MenuTrigger disableButtonEnhancement>
                                                    <Button
                                                        id={`button-seq-menu-table-${seq.id}`}
                                                        size="small"
                                                        appearance="subtle"
                                                        shape="circular"
                                                        icon={<MoreVerticalRegular/>}
                                                    />
                                                </MenuTrigger>
                                                <MenuPopover>
                                                    <MenuList>
                                                        <MenuItem icon={<EditRegular/>} onClick={() => openEdit(seq)}>Edit</MenuItem>
                                                        <MenuItem icon={<ArrowCounterclockwiseRegular/>} onClick={() => handleReset(seq)}>Reset Counter</MenuItem>
                                                        <MenuItem icon={<DeleteRegular/>} onClick={() => handleDelete(seq)}>Delete</MenuItem>
                                                    </MenuList>
                                                </MenuPopover>
                                            </Menu>
                                        </td>
                                    )}
                                </tr>
                            ))}
                        </tbody>
                    </table>
                )}
            </div>

            <Drawer
                open={drawer.open}
                onOpenChange={(_, d) => setDrawer(prev => ({...prev, open: d.open}))}
                position="end"
                size="small"
            >
                <DrawerHeader>
                    <DrawerHeaderTitle>{drawer.editing ? 'Edit Sequence' : 'New Sequence'}</DrawerHeaderTitle>
                </DrawerHeader>
                <DrawerBody className={styles.drawerBody}>
                    <Field
                        label="Name"
                        required
                        validationMessage={!form.name.trim() && formError ? formError : undefined}
                    >
                        <Input
                            id={"input-seq-name"}
                            value={form.name}
                            onChange={(_, d) => setForm(f => ({...f, name: d.value}))}
                            placeholder="e.g. Invoice Number"
                        />
                    </Field>
                    <Field
                        label="Key (uppercase)"
                        required
                        hint="Used in {{SEQ:KEY}} token"
                    >
                        <Input
                            id={"input-seq-key"}
                            value={form.key}
                            onChange={(_, d) => setForm(f => ({...f, key: d.value.toUpperCase()}))}
                            placeholder="e.g. INV"
                            disabled={!!drawer.editing}
                        />
                    </Field>
                    <Field
                        label="Pad Width"
                        hint="0 = no padding, 3  to  007"
                    >
                        <Input
                            id={"input-seq-pad-width"}
                            type="number"
                            value={String(form.padWidth ?? 0)}
                            onChange={(_, d) => setForm(f => ({...f, padWidth: parseInt(d.value) || 0}))}
                        />
                    </Field>
                    <Field label="Prefix">
                        <Input
                            id={"input-seq-prefix"}
                            value={form.prefix ?? ''}
                            onChange={(_, d) => setForm(f => ({...f, prefix: d.value}))}
                            placeholder="e.g. INV-"
                        />
                    </Field>
                    <Field label="Suffix">
                        <Input
                            id={"input-seq-suffix"}
                            value={form.suffix ?? ''}
                            onChange={(_, d) => setForm(f => ({...f, suffix: d.value}))}
                            placeholder="e.g. /2026"
                        />
                    </Field>
                    <Field label="Reset Period">
                        <Select
                            id={"select-seq-reset-period"}
                            value={form.resetPeriod ?? 'NEVER'}
                            onChange={(_, d) => setForm(f => ({...f, resetPeriod: d.value as SequenceResetPeriod}))}
                        >
                            {RESET_PERIODS.map(p => <option key={p} value={p}>{p}</option>)}
                        </Select>
                    </Field>
                    <div className={styles.previewBox}>
                        <Text
                            size={200}
                            className={styles.previewLabel}
                        >
                            Preview:{' '}
                        </Text>
                        <code>{formatPreview(form, drawer.editing?.currentValue ?? 0)}</code>
                    </div>
                    {formError && (
                        <Text className={styles.formError}>{formError}</Text>
                    )}
                    <div className={styles.buttonRow}>
                        <Button
                            id={"button-seq-cancel"}
                            appearance="secondary"
                            shape={"circular"}
                            onClick={() => setDrawer({open: false})}
                        >
                            Cancel
                        </Button>
                        <Button
                            id={"button-seq-save"}
                            appearance="primary"
                            shape={"circular"}
                            onClick={handleSave}
                            disabled={saving}
                        >
                            {saving ? 'Saving…' : 'Save'}
                        </Button>
                    </div>
                </DrawerBody>
            </Drawer>
        </>
    );
};

export default OrganizationSequencesTab;
