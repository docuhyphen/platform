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
} from '@fluentui/react-components';
import {DeleteRegular, EditRegular, MoreVerticalRegular} from '@fluentui/react-icons';
import {CreateVariableRequest, UpdateVariableRequest, VariableDefinitionDto, ViewMode} from '../../models/models';
import {Capability} from '../../models/models';
import {createVariable, deleteVariable, listVariables, updateVariable} from '../../../services/variableService';
import {useOrganizationVariablesTabStyles} from './OrganizationVariablesTabStyles';

interface DrawerState
{
    open: boolean;
    editing?: VariableDefinitionDto;
}

export interface OrganizationVariablesTabHandle
{
    openCreate: () => void;
}

interface OrganizationVariablesTabProps
{
    viewMode?: ViewMode;
}

const OrganizationVariablesTab = forwardRef<OrganizationVariablesTabHandle, OrganizationVariablesTabProps>(({viewMode = 'cards'}, ref) =>
{
    const styles = useOrganizationVariablesTabStyles();
    const {appUserPersonOrganization, hasCapability} = useAuth();
    const canManage =
        appUserPersonOrganization?.isActive &&
        (hasCapability(Capability.APP_ADMIN) || hasCapability(Capability.ORG_POLICY_MANAGE));

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
            <div className={styles.container}>
                <Text
                    size={300}
                    className={styles.descriptionText}
                >
                    Reusable key-value pairs shared across your organization. Users can override them per exchange.
                </Text>

                {loading && <Spinner size="small" label="Loading…"/>}
                {!loading && error && (
                    <Text className={styles.errorText}>{error}</Text>
                )}
                {!loading && !error && variables.length === 0 && (
                    <Text className={styles.emptyText}>No org variables yet.</Text>
                )}
                {!loading && !error && variables.length > 0 && viewMode === 'cards' && variables.map(v => (
                    <div
                        key={v.id}
                        className={styles.variableRow}
                    >
                        <div className={styles.variableRowInner}>
                            <code className={styles.codeKey}>{`{{${v.key}}}`}</code>
                            <Text
                                size={200}
                                className={styles.defaultValueText}
                            >
                                {v.defaultValue ? `Default: "${v.defaultValue}"` : <em>no default</em>}
                            </Text>
                            {!v.isActive && (
                                <Badge
                                    appearance="tint"
                                    color="severe"
                                    size="small"
                                >
                                    Inactive
                                </Badge>
                            )}
                        </div>
                        {canManage && (
                            <Menu>
                                <MenuTrigger disableButtonEnhancement>
                                    <Button
                                        id={`button-org-var-menu-${v.id}`}
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
                                            onClick={() => openEdit(v)}
                                        >
                                            Edit
                                        </MenuItem>
                                        <MenuItem
                                            icon={<DeleteRegular/>}
                                            onClick={() => handleDelete(v)}
                                        >
                                            Delete
                                        </MenuItem>
                                    </MenuList>
                                </MenuPopover>
                            </Menu>
                        )}
                    </div>
                ))}
                {!loading && !error && variables.length > 0 && viewMode === 'table' && (
                    <table className={styles.table}>
                        <thead>
                            <tr>
                                <th className={styles.th}>Token</th>
                                <th className={styles.th}>Default Value</th>
                                <th className={styles.th}>Active</th>
                                {canManage && <th className={styles.th}/>}
                            </tr>
                        </thead>
                        <tbody>
                            {variables.map(v => (
                                <tr key={v.id} className={styles.tr}>
                                    <td className={styles.td}><code className={styles.codeKey}>{`{{${v.key}}}`}</code></td>
                                    <td className={styles.td}><Text size={200}>{v.defaultValue || <em>no default</em>}</Text></td>
                                    <td className={styles.td}><Text size={200}>{v.isActive ? 'Active' : 'Inactive'}</Text></td>
                                    {canManage && (
                                        <td className={styles.td}>
                                            <Menu>
                                                <MenuTrigger disableButtonEnhancement>
                                                    <Button size="small" appearance="subtle" shape="circular" icon={<MoreVerticalRegular/>}/>
                                                </MenuTrigger>
                                                <MenuPopover>
                                                    <MenuList>
                                                        <MenuItem icon={<EditRegular/>} onClick={() => openEdit(v)}>Edit</MenuItem>
                                                        <MenuItem icon={<DeleteRegular/>} onClick={() => handleDelete(v)}>Delete</MenuItem>
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
                    <DrawerHeaderTitle>{drawer.editing ? 'Edit Variable' : 'New Org Variable'}</DrawerHeaderTitle>
                </DrawerHeader>
                <DrawerBody className={styles.drawerBody}>
                    <Field
                        label="Key"
                        required
                        hint="Uppercase alphanumeric. Used as {{KEY}} in templates."
                    >
                        <Input
                            id={"input-org-var-key"}
                            value={formKey}
                            onChange={(_, d) => setFormKey(d.value.toUpperCase())}
                            placeholder="e.g. CLIENT_NAME"
                            disabled={!!drawer.editing}
                        />
                    </Field>
                    <Field label="Default Value">
                        <Input
                            id={"input-org-var-value"}
                            value={formValue}
                            onChange={(_, d) => setFormValue(d.value)}
                            placeholder="e.g. Acme Corp"
                        />
                    </Field>
                    {formError && (
                        <Text className={styles.errorText}>{formError}</Text>
                    )}
                    <div className={styles.buttonRow}>
                        <Button
                            id={"button-org-var-cancel"}
                            appearance="secondary"
                            shape={"circular"}
                            onClick={() => setDrawer({open: false})}
                        >
                            Cancel
                        </Button>
                        <Button
                            id={"button-org-var-save"}
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
});

export default OrganizationVariablesTab;
