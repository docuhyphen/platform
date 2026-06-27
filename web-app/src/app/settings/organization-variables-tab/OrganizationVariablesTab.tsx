import {forwardRef, useEffect, useImperativeHandle, useState} from 'react';
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
    Spinner,
    Text,
    tokens,
} from '@fluentui/react-components';
import {DeleteRegular, EditRegular, MoreVerticalRegular} from '@fluentui/react-icons';
import {AppUserRole, CreateVariableRequest, UpdateVariableRequest, VariableDefinitionDto} from '../../models/models';
import {createVariable, deleteVariable, listVariables, updateVariable} from '../../../services/variableService';

interface DrawerState
{
    open: boolean;
    editing?: VariableDefinitionDto;
}

export interface OrganizationVariablesTabHandle
{
    openCreate: () => void;
}

const OrganizationVariablesTab = forwardRef<OrganizationVariablesTabHandle>((_, ref) =>
{
    const {appUser, appUserPersonOrganization} = useAuth();
    const roleValue = `${appUser?.role ?? ''}`;
    const canManage =
        appUserPersonOrganization?.isActive &&
        (roleValue === AppUserRole.ORG_ADMIN || roleValue === 'APP_ADMIN');

    const [variables, setVariables] = useState<VariableDefinitionDto[]>([]);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [drawer, setDrawer] = useState<DrawerState>({open: false});
    const [formKey, setFormKey] = useState('');
    const [formValue, setFormValue] = useState('');
    const [formError, setFormError] = useState<string | null>(null);
    const [saving, setSaving] = useState(false);

    const load = () =>
    {
        setLoading(true);
        setError(null);
        listVariables('ORG')
            .then(setVariables)
            .catch(() => setError('Failed to load variables'))
            .finally(() => setLoading(false));
    };

    useEffect(() => { load(); }, []);

    const openCreate = () =>
    {
        setFormKey('');
        setFormValue('');
        setFormError(null);
        setDrawer({open: true});
    };

    useImperativeHandle(ref, () => ({openCreate}));

    const openEdit = (v: VariableDefinitionDto) =>
    {
        setFormKey(v.key);
        setFormValue(v.defaultValue ?? '');
        setFormError(null);
        setDrawer({open: true, editing: v});
    };

    const handleSave = async () =>
    {
        if (!formKey.trim()) { setFormError('Key is required'); return; }
        setSaving(true);
        setFormError(null);
        try
        {
            if (drawer.editing)
            {
                const req: UpdateVariableRequest = {defaultValue: formValue};
                await updateVariable(drawer.editing.id, req);
            }
            else
            {
                const req: CreateVariableRequest = {key: formKey.toUpperCase(), defaultValue: formValue, scope: 'ORG'};
                await createVariable(req);
            }
            setDrawer({open: false});
            load();
        }
        catch (e: unknown)
        {
            const msg = typeof e === 'object' && e !== null && 'errorMessage' in e
                ? (e as {errorMessage: string}).errorMessage
                : 'Failed to save variable';
            setFormError(msg);
        }
        finally
        {
            setSaving(false);
        }
    };

    const handleDelete = async (v: VariableDefinitionDto) =>
    {
        await deleteVariable(v.id).catch(() => null);
        load();
    };

    return (
        <>
            <div style={{display: 'flex', flexDirection: 'column', gap: '12px', padding: '0 4px'}}>
                <Text size={300} style={{color: 'var(--colorNeutralForeground3)'}}>
                    Reusable key-value pairs shared across your organization. Users can override them per exchange.
                </Text>

                {loading && <Spinner size="small" label="Loading…"/>}
                {!loading && error && <Text style={{color: 'var(--colorPaletteRedForeground1)'}}>{error}</Text>}
                {!loading && !error && variables.length === 0 && (
                    <Text style={{color: 'var(--colorNeutralForeground3)'}}>No org variables yet.</Text>
                )}
                {!loading && variables.map(v => (
                    <div key={v.id} style={{
                        border: '1px solid var(--colorNeutralStroke1)',
                        borderRadius: tokens.borderRadiusXLarge,
                        padding: '10px 16px',
                        display: 'flex',
                        justifyContent: 'space-between',
                        alignItems: 'center',
                        gap: '8px',
                    }}>
                        <div style={{display: 'flex', gap: '12px', alignItems: 'center', flex: 1, minWidth: 0}}>
                            <code style={{fontFamily: 'monospace', fontWeight: 600}}>{`{{${v.key}}}`}</code>
                            <Text size={200} style={{color: 'var(--colorNeutralForeground2)'}}>
                                {v.defaultValue ? `Default: "${v.defaultValue}"` : <em>no default</em>}
                            </Text>
                            {!v.isActive && <Badge appearance="tint" color="severe" size="small">Inactive</Badge>}
                        </div>
                        {canManage && (
                            <Menu>
                                <MenuTrigger disableButtonEnhancement>
                                    <Button size="small" appearance="subtle" icon={<MoreVerticalRegular/>}/>
                                </MenuTrigger>
                                <MenuPopover>
                                    <MenuList>
                                        <MenuItem icon={<EditRegular/>} onClick={() => openEdit(v)}>Edit</MenuItem>
                                        <MenuItem icon={<DeleteRegular/>} onClick={() => handleDelete(v)}>Delete</MenuItem>
                                    </MenuList>
                                </MenuPopover>
                            </Menu>
                        )}
                    </div>
                ))}
            </div>

            <Drawer open={drawer.open} onOpenChange={(_, d) => setDrawer(prev => ({...prev, open: d.open}))} position="end" size="small">
                <DrawerHeader>
                    <DrawerHeaderTitle>{drawer.editing ? 'Edit Variable' : 'New Org Variable'}</DrawerHeaderTitle>
                </DrawerHeader>
                <DrawerBody style={{display: 'flex', flexDirection: 'column', gap: '16px', paddingTop: '16px'}}>
                    <Field label="Key" required hint="Uppercase alphanumeric. Used as {{KEY}} in templates.">
                        <Input
                            value={formKey}
                            onChange={(_, d) => setFormKey(d.value.toUpperCase())}
                            placeholder="e.g. CLIENT_NAME"
                            disabled={!!drawer.editing}
                        />
                    </Field>
                    <Field label="Default Value">
                        <Input value={formValue} onChange={(_, d) => setFormValue(d.value)} placeholder="e.g. Acme Corp"/>
                    </Field>
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
});

export default OrganizationVariablesTab;
